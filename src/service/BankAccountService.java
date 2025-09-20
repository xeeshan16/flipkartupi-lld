package service;

import enums.AccountStatus;
import enums.UserStatus;
import lock.LockManager;
import models.BankAccount;
import models.User;
import repository.BankAccountRepository;
import repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

public class BankAccountService {

    private final BankAccountRepository acctRepo;
    private final UserRepository userRepo;
    private final LockManager lockManager;

    public BankAccountService(BankAccountRepository acctRepo, UserRepository userRepo, LockManager lockManager) {
        this.acctRepo = acctRepo; this.userRepo = userRepo; this.lockManager = lockManager;
    }

    public BankAccount linkBankAccount(UUID userId, UUID bankId, String accountNumber, BigDecimal initialBalance) {
        // validate user exists and status active
        User u = userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("user not found"));
        if (u.status != UserStatus.ACTIVE) throw new IllegalStateException("user is not active");
        BankAccount acct = acctRepo.createAccount(userId, bankId, accountNumber, initialBalance);
        return acct;
    }

    public void setPrimaryAccount(UUID userId, UUID accountId) {
        acctRepo.setPrimary(userId, accountId);
    }

    public void validateActive(UUID accountId) {
        BankAccount acct = acctRepo.findById(accountId).orElseThrow(() -> new IllegalArgumentException("account not found"));
        if (acct.status != AccountStatus.ACTIVE) throw new IllegalStateException("account is not active");
    }

    // Reserve amount with account-level lock
    public void reserve(UUID accountId, BigDecimal amount) {
        Lock lock = lockManager.getLockForAccount(accountId);
        lock.lock();
        try {
            BankAccount acct = acctRepo.findById(accountId).orElseThrow(() -> new IllegalArgumentException("account not found"));
            acct.reserve(amount);
            acctRepo.update(acct);
        } finally {
            lock.unlock();
        }
    }

    public void release(UUID accountId, BigDecimal amount) {
        Lock lock = lockManager.getLockForAccount(accountId);
        lock.lock();
        try {
            BankAccount acct = acctRepo.findById(accountId).orElseThrow(() -> new IllegalArgumentException("account not found"));
            acct.releaseReserved(amount);
            acctRepo.update(acct);
        } finally {
            lock.unlock();
        }
    }

    public void settle(UUID accountId, BigDecimal amount) {
        Lock lock = lockManager.getLockForAccount(accountId);
        lock.lock();
        try {
            BankAccount acct = acctRepo.findById(accountId).orElseThrow(() -> new IllegalArgumentException("account not found"));
            acct.settle(amount);
            acctRepo.update(acct);
        } finally {
            lock.unlock();
        }
    }

    // credit a destination account (internal)
    public void credit(UUID accountId, BigDecimal amount) {
        Lock lock = lockManager.getLockForAccount(accountId);
        lock.lock();
        try {
            BankAccount acct = acctRepo.findById(accountId).orElseThrow(() -> new IllegalArgumentException("account not found"));
            acct.credit(amount);
            acctRepo.update(acct);
        } finally {
            lock.unlock();
        }
    }

    public List<BankAccount> getAccountsForUser(UUID userId) { return acctRepo.findByUserId(userId); }
}
