import enums.AccountStatus;
import enums.TransactionStatus;
import exception.IdempotencyException;
import lock.LockManager;
import models.Bank;
import models.BankAccount;
import models.Transaction;
import models.User;
import psp.MockPspClient;
import repositoryimpl.InMemoryBankAccountRepository;
import repositoryimpl.InMemoryBankRepository;
import repositoryimpl.InMemoryTransactionRepository;
import repositoryimpl.InMemoryUserRepository;
import service.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Demo / test harness that runs multiple scenarios against the no-framework
 * Flipkart UPI implementation.
 */
public class FlipkartUPI {
    public static void main(String[] args) throws Exception {
        // ---------- Setup infra ----------
        LockManager lockManager = new LockManager();
        InMemoryUserRepository userRepo = new InMemoryUserRepository();
        InMemoryBankRepository bankRepo = new InMemoryBankRepository();
        InMemoryBankAccountRepository acctRepo = new InMemoryBankAccountRepository(userRepo, lockManager);
        InMemoryTransactionRepository txnRepo = new InMemoryTransactionRepository();

        // PSP clients with different behaviors
        MockPspClient pspDefault = new MockPspClient(0.6, 0.3);   // normal-ish
        MockPspClient pspAlwaysPending = new MockPspClient(0.0, 1.0); // always pending
        MockPspClient pspAlwaysFail = new MockPspClient(0.0, 0.0);    // always fail

        // Bank health registry
        BankHealthRegistry bankHealthRegistry = new InMemoryBankHealthRegistry();

        // services
        UserService userService = new UserService(userRepo);
        BankService bankService = new BankService(bankRepo);
        BankAccountService acctService = new BankAccountService(acctRepo, userRepo, lockManager);

        // Shared executor for async PSP calls across services
        ExecutorService asyncExecutor = Executors.newFixedThreadPool(8);

        // Create multiple TransactionService instances using different PSP clients
        TransactionService txnServiceDefault = new TransactionService(
                txnRepo, acctRepo, acctService, pspDefault, asyncExecutor, lockManager, bankHealthRegistry);

        TransactionService txnServicePending = new TransactionService(
                txnRepo, acctRepo, acctService, pspAlwaysPending, asyncExecutor, lockManager, bankHealthRegistry);

        TransactionService txnServiceFail = new TransactionService(
                txnRepo, acctRepo, acctService, pspAlwaysFail, asyncExecutor, lockManager, bankHealthRegistry);

        // Reconcilers (one main reconciler that uses txnServiceDefault and another for pending service)
        ReconciliationService reconcilerDefault = new ReconciliationService(txnServiceDefault, txnRepo);
        ReconciliationService reconcilerPending = new ReconciliationService(txnServicePending, txnRepo);
        ReconciliationService reconcilerFail = new ReconciliationService(txnServiceFail, txnRepo);

        reconcilerDefault.start();
        reconcilerPending.start();
        reconcilerFail.start();

        // ---------- Setup initial data ----------
        User alice = userService.onboardUser("Alice", "99990001");
        User bob = userService.onboardUser("Bob", "99990002");
        User carol = userService.onboardUser("Carol", "99990003");

        Bank bankA = bankService.registerBank("Bank A", "BKA");
        Bank bankB = bankService.registerBank("Bank B", "BKB");

        BankAccount aliceAcct = acctService.linkBankAccount(alice.id, bankA.id, "100200300", new BigDecimal("1000.00"));
        BankAccount bobAcct = acctService.linkBankAccount(bob.id, bankB.id, "200300400", new BigDecimal("100.00"));
        BankAccount carolAcct = acctService.linkBankAccount(carol.id, bankA.id, "300400500", new BigDecimal("500.00"));

        acctService.setPrimaryAccount(alice.id, aliceAcct.id);

        System.out.println("=== Initial State ===");
        printAccounts(acctRepo);
        System.out.println();

        RecipientResolver recipientResolver = new RecipientResolver(userRepo,acctRepo);

        Optional<UUID> resolved = recipientResolver.resolveToAccountId(bob.phone);

        // Call createPayment but pass only the phone as toIdentifier; resolve first like production would
        UUID toAccountId = resolved.orElse(null);

        // ---------- Scenario 1: Happy path internal transfer (Alice -> Bob) ----------
        System.out.println("SCENARIO 1: Happy path (Alice -> Bob 50.00)");
        try {
            Transaction t1 = txnServiceDefault.createPayment("idem-happy-1", aliceAcct.id,toAccountId, bob.phone, new BigDecimal("50.00"));
            System.out.println("Created txn: " + t1.id + " initial status: " + t1.status);
        } catch (Exception ex) {
            System.out.println("Unexpected error: " + ex.getMessage());
        }

        // allow background async work to run
        Thread.sleep(2500);
        System.out.println("After processing:");
        printAccounts(acctRepo);
        printTxnSummary(txnRepo);



        // ---------- Scenario 2: PSP returns PENDING -> reconciler resolves it ----------
        System.out.println("\nSCENARIO 2: PSP returns PENDING (forced) -> reconciler resolves");
        try {
            Transaction t2 = txnServicePending.createPayment("idem-pending-1", aliceAcct.id, null, "external-pend", new BigDecimal("20.00"));
            System.out.println("Created pending txn: " + t2.id + " status: " + t2.status + " pspId: " + t2.pspTxnId);
        } catch (Exception ex) {
            System.out.println("Error creating pending txn: " + ex.getMessage());
        }

        // wait longer — reconciler will query PSP and resolve
        Thread.sleep(8000);
        System.out.println("After reconciliation attempt:");
        printAccounts(acctRepo);
        printTxnSummary(txnRepo);

        // ---------- Scenario 3: Bank down - block payment ----------
        System.out.println("\nSCENARIO 3: Bank down (mark Bank A down) -> payments from Bank A blocked");
        bankHealthRegistry.markDown(bankA.id);
        System.out.println("Marked Bank A down. Attempt payment from Alice (Bank A) -> Bob (Bank B)");
        try {
            txnServiceDefault.createPayment("idem-bankdown-1", aliceAcct.id, bobAcct.id, bob.phone, new BigDecimal("10.00"));
        } catch (Exception ex) {
            System.out.println("Expected failure due to bank down: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        }

        // bring bank A back up for next scenarios
        bankHealthRegistry.markUp(bankA.id);
        System.out.println("Marked Bank A up again.");

        // ---------- Scenario 4: Insufficient funds ----------
        System.out.println("\nSCENARIO 4: Insufficient funds (Bob has 100)");
        try {
            txnServiceDefault.createPayment("idem-insuf-1", bobAcct.id, null, "external", new BigDecimal("200.00"));
        } catch (Exception ex) {
            System.out.println("Expected insufficient funds: " + ex.getMessage());
        }

        // ---------- Scenario 5: Idempotency - repeat request with same key ----------
        System.out.println("\nSCENARIO 5: Idempotency (repeat same idempotency key)");
        try {
            Transaction a = txnServiceDefault.createPayment("idem-repeat-1", carolAcct.id, null, "ext-1", new BigDecimal("30.00"));
            Transaction b = txnServiceDefault.createPayment("idem-repeat-1", carolAcct.id, null, "ext-1", new BigDecimal("30.00"));
            System.out.println("First txn id: " + a.id + " second returned txn id: " + b.id + " (should be same)");
        } catch (Exception ex) {
            System.out.println("Idempotency test error: " + ex.getMessage());
        }
        Thread.sleep(2500);

        // ---------- Scenario 6: Concurrent payments causing contention ----------
        System.out.println("\nSCENARIO 6: Concurrent payments (Carol account 500) trying two 400 transfers");
        Runnable rtask = () -> {
            try {
                String idemp = "idem-conc-" + UUID.randomUUID();
                Transaction t = txnServiceDefault.createPayment(idemp, carolAcct.id, null, "ext-conc", new BigDecimal("400.00"));
                System.out.println(Thread.currentThread().getName() + " created txn " + t.id + " status:" + t.status);
            } catch (Exception ex) {
                System.out.println(Thread.currentThread().getName() + " failed to create txn: " + ex.getMessage());
            }
        };
        ExecutorService concExec = Executors.newFixedThreadPool(2);
        concExec.submit(rtask);
        concExec.submit(rtask);
        concExec.shutdown();
        concExec.awaitTermination(10, TimeUnit.SECONDS);
        // allow background tasks and reconciler
        Thread.sleep(6000);
        printAccounts(acctRepo);
        printTxnSummary(txnRepo);

        // ---------- Scenario 7: Payment to inactive account ----------
        System.out.println("\nSCENARIO 7: Payment to an inactive destination account (mark Bob's account inactive)");
        // set Bob account inactive
        BankAccount bobAcctObj = acctRepo.findById(bobAcct.id).orElseThrow();
        bobAcctObj.status = AccountStatus.INACTIVE;
        acctRepo.update(bobAcctObj);
        try {
            txnServiceDefault.createPayment("idem-inactive-1", aliceAcct.id, bobAcct.id, bob.phone, new BigDecimal("5.00"));
        } catch (Exception ex) {
            System.out.println("Expected failure (destination inactive): " + ex.getMessage());
        }
        // reactivate for cleanup
        bobAcctObj.status = AccountStatus.ACTIVE;
        acctRepo.update(bobAcctObj);

        // ---------- Scenario 8: PSP failure -> should release reserved funds and mark txn FAILED ----------
        System.out.println("\nSCENARIO 8: PSP failure (force) -> txn should fail and reserved funds released");
        try {
            Transaction tfail = txnServiceFail.createPayment("idem-pspfail-1", aliceAcct.id, null, "ext-fail", new BigDecimal("10.00"));
            System.out.println("Created txn (psp fail scenario): " + tfail.id);
        } catch (Exception ex) {
            System.out.println("Error creating txn in PSP-fail scenario: " + ex.getMessage());
        }
        Thread.sleep(3000);
        printAccounts(acctRepo);
        printTxnSummary(txnRepo);

        // ---------- Final summary ----------
        System.out.println("\n=== FINAL ACCOUNTS & TRANSACTIONS ===");
        printAccounts(acctRepo);
        printTxnSummary(txnRepo);

        // shutdown
        reconcilerDefault.stop();
        reconcilerPending.stop();
        reconcilerFail.stop();
        asyncExecutor.shutdown();
        asyncExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void printAccounts(InMemoryBankAccountRepository acctRepo) {
        System.out.println("Accounts:");
        acctRepo.listAll().forEach(a ->
                System.out.println(String.format("  acct=%s user=%s bank=%s balance=%s reserved=%s status=%s",
                        a.id, a.userId, a.bankId, a.getBalance(), a.getReserved(), a.status))
        );
    }

    private static void printTxnSummary(InMemoryTransactionRepository txnRepo) {
        List<Transaction> pending = txnRepo.findByStatus(TransactionStatus.PENDING);
        List<Transaction> success = txnRepo.findByStatus(TransactionStatus.SUCCESS);
        List<Transaction> failed = txnRepo.findByStatus(TransactionStatus.FAILED);
        System.out.println("Transactions summary: PENDING=" + pending.size() + " SUCCESS=" + success.size() + " FAILED=" + failed.size());
        if (!pending.isEmpty()) {
            System.out.println("  Pending: ");
            pending.forEach(t -> System.out.println("    " + t));
        }
        if (!success.isEmpty()) {
            System.out.println("  Success: ");
            success.forEach(t -> System.out.println("    " + t));
        }
        if (!failed.isEmpty()) {
            System.out.println("  Failed: ");
            failed.forEach(t -> System.out.println("    " + t));
        }
    }
}
