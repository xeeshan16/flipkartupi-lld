package service;

import enums.TransactionStatus;
import exception.BankDownException;
import exception.IdempotencyException;
import lock.LockManager;
import models.BankAccount;
import models.Transaction;
import psp.PspClient;
import repository.BankAccountRepository;
import repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class TransactionService {

    private final TransactionRepository txnRepo;
    private final BankAccountRepository acctRepo;
    private final BankAccountService acctService;
    private final PspClient pspClient;
    private final ExecutorService asyncExecutor;
    private final LockManager lockManager;
    private final BankHealthRegistry bankHealthRegistry; // NEW

    // config
    private final int maxPspInitiateRetries = 3;

    public TransactionService(TransactionRepository txnRepo,
                              BankAccountRepository acctRepo,
                              BankAccountService acctService,
                              PspClient pspClient,
                              ExecutorService asyncExecutor,
                              LockManager lockManager,
                              BankHealthRegistry bankHealthRegistry) { // NEW param
        this.txnRepo = txnRepo;
        this.acctRepo = acctRepo;
        this.acctService = acctService;
        this.pspClient = pspClient;
        this.asyncExecutor = asyncExecutor;
        this.lockManager = lockManager;
        this.bankHealthRegistry = bankHealthRegistry;
    }

    // DTO style request
    public Transaction createPayment(String idempotencyKey,
                                     UUID fromAccountId,
                                     // optional internal destination
                                     UUID toAccountId,
                                     String toIdentifier,
                                     BigDecimal amount) throws IdempotencyException {
        // 1. validate amount
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("amount must be > 0");

        // 2. idempotency check
        Optional<Transaction> existing = txnRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();

        // 2.5 fetch from account early so we can check bank health before reserving funds
        BankAccount fromAcct = acctRepo.findById(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("from account not found"));

        // 2.6 check source bank health
        UUID fromBankId = fromAcct.bankId; // assumes public or getter
        if (bankHealthRegistry != null && bankHealthRegistry.isDown(fromBankId)) {
            throw new BankDownException("Source bank (" + fromBankId + ") is currently unavailable");
        }

        // 2.7 if internal destination provided, check its bank as well
        if (toAccountId != null) {
            BankAccount toAcct = acctRepo.findById(toAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("to account not found"));
            UUID toBankId = toAcct.bankId;
            if (bankHealthRegistry != null && bankHealthRegistry.isDown(toBankId)) {
                throw new BankDownException("Destination bank (" + toBankId + ") is currently unavailable");
            }
        }

        // 3. reserve funds on fromAccount (after bank health checks)
        acctService.reserve(fromAccountId, amount);

        // 4. create transaction (PENDING)
        Transaction txn = new Transaction(UUID.randomUUID(), idempotencyKey, fromAccountId, toAccountId, toIdentifier, amount);
        try {
            txnRepo.saveNew(txn);
        } catch (IdempotencyException e) {
            // If saving failed due to idempotency (rare race), release reserved and return existing
            acctService.release(fromAccountId, amount);
            throw e;
        }

        // 5. async call to PSP to perform transfer
        asyncExecutor.submit(() -> {
            callPspAndHandle(txn.id);
        });

        return txn;
    }

    private void callPspAndHandle(UUID txnId) {
        Transaction txn = txnRepo.findById(txnId).orElseThrow();
        // create transfer request using masked account
        BankAccount fromAcct = acctRepo.findById(txn.fromAccountId).orElseThrow();
        PspClient.PspResponse resp = null;
        try {
            resp = pspClient.initiateTransfer(fromAcct.maskedAccount, txn.toIdentifier, txn.amount);
        } catch (Throwable t) {
            // network or other error -> leave pending for reconciler
            // keep txn PENDING and increment attempts
            txn.reconciliationAttempts++;
            txn.updatedAt = Instant.now();
            txnRepo.update(txn);
            return;
        }

        // handle response
        if (resp.status == PspClient.PspStatus.SUCCESS) {
            // settle reserved funds
            acctService.settle(txn.fromAccountId, txn.amount);
            // credit internal destination if applicable
            if (txn.toAccountId != null) acctService.credit(txn.toAccountId, txn.amount);
            txn.markSuccess(resp.pspTxnId);
            txnRepo.update(txn);
        } else if (resp.status == PspClient.PspStatus.PENDING) {
            // store the PSP id and keep PENDING - reconciler will pick it up
            txn.markPending(resp.pspTxnId);
            txnRepo.update(txn);
        } else {
            // failure -> release reserved amount and mark FAILED
            acctService.release(txn.fromAccountId, txn.amount);
            txn.markFailed(resp.errorCode);
            txnRepo.update(txn);
        }
    }

    // Expose ability for ReconciliationService to try reprocessing a pending txn:
    public void reconcileOnce(Transaction txn, Duration maxPendingDuration, int maxAttempts) {
        // basic checks/timeouts
        if (txn.status != TransactionStatus.PENDING) return;
        if (txn.pspTxnId == null) {
            // No PSP id assigned - re-initiate PSP call
            txn.reconciliationAttempts++;
            txn.updatedAt = Instant.now();
            txnRepo.update(txn);
            asyncExecutor.submit(() -> callPspAndHandle(txn.id));
            return;
        }
        // Query PSP for status
        PspClient.PspResponse resp = pspClient.queryStatus(txn.pspTxnId);
        if (resp.status == PspClient.PspStatus.SUCCESS) {
            // settle
            acctService.settle(txn.fromAccountId, txn.amount);
            if (txn.toAccountId != null) acctService.credit(txn.toAccountId, txn.amount);
            txn.markSuccess(resp.pspTxnId);
            txnRepo.update(txn);
        } else if (resp.status == PspClient.PspStatus.FAILED) {
            acctService.release(txn.fromAccountId, txn.amount);
            txn.markFailed(resp.errorCode);
            txnRepo.update(txn);
        } else {
            // still pending - increment attempts and maybe timeout
            txn.reconciliationAttempts++;
            txn.updatedAt = Instant.now();
            txnRepo.update(txn);
            if (txn.reconciliationAttempts > maxAttempts || Duration.between(txn.createdAt, Instant.now()).compareTo(maxPendingDuration) > 0) {
                // give up -> fail and release
                acctService.release(txn.fromAccountId, txn.amount);
                txn.markFailed("RECONCILE_TIMEOUT");
                txnRepo.update(txn);
            }
        }
    }

    // query and search operations:
    public Optional<Transaction> getTransaction(UUID id) { return txnRepo.findById(id); }
    public List<Transaction> searchByPayerOrPayee(UUID payer, String payeeIdentifier) {
        return txnRepo.search(t ->
                (payer != null && t.fromAccountId != null && t.fromAccountId.equals(payer)) ||
                        (payeeIdentifier != null && payeeIdentifier.equals(t.toIdentifier))
        );
    }

    public List<Transaction> listByUserAccount(UUID userAccountId) {
        return txnRepo.findByFromOrTo(userAccountId, userAccountId);
    }

    public List<Transaction> findPending() { return txnRepo.findByStatus(TransactionStatus.PENDING); }
}
