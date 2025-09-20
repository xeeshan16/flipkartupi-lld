package models;

import enums.AccountStatus;

import java.math.BigDecimal;
import java.util.UUID;

public class BankAccount {
    public final UUID id;
    public final UUID userId;
    public final UUID bankId;
    public final String accountNumber;
    public String maskedAccount;
    private BigDecimal balance;        // available balance (not including reserved)
    private BigDecimal reserved;       // reserved amount for pending transactions
    public boolean isPrimary;
    public AccountStatus status;
    long version;

    public BankAccount(UUID id, UUID userId, UUID bankId, String accountNumber, BigDecimal initialBalance) {
        this.id = id;
        this.userId = userId;
        this.bankId = bankId;
        this.accountNumber = accountNumber;
        this.maskedAccount = mask(accountNumber);
        this.balance = initialBalance == null ? BigDecimal.ZERO : initialBalance;
        this.reserved = BigDecimal.ZERO;
        this.isPrimary = false;
        this.status = AccountStatus.ACTIVE;
        this.version = 0L;
    }

    private String mask(String acct) {
        if (acct == null) return null;
        int n = acct.length();
        if (n <= 4) return "****";
        return "****" + acct.substring(n - 4);
    }

    public synchronized BigDecimal getAvailable() {
        return balance.subtract(reserved);
    }

    public synchronized BigDecimal getBalance() { return balance; }
    public synchronized BigDecimal getReserved() { return reserved; }

    // Reserve amount - returns true if reserved
    public synchronized void reserve(BigDecimal amt) {
        // assumes caller validated status and amounts
        if (this.status != AccountStatus.ACTIVE) throw new IllegalStateException("Account not active");
        if (amt.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Reserve amount must be > 0");
        if (getAvailable().compareTo(amt) < 0) throw new IllegalStateException("Insufficient available balance");
        reserved = reserved.add(amt);
        version++;
    }

    public synchronized void releaseReserved(BigDecimal amt) {
        if (amt.compareTo(BigDecimal.ZERO) <= 0) return;
        reserved = reserved.subtract(amt);
        if (reserved.compareTo(BigDecimal.ZERO) < 0) reserved = BigDecimal.ZERO;
        version++;
    }

    // Settle: reduce reserved and reduce balance (finalize)
    public synchronized void settle(BigDecimal amt) {
        if (amt.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("amount must be >0");
        if (reserved.compareTo(amt) < 0) throw new IllegalStateException("not enough reserved to settle");
        reserved = reserved.subtract(amt);
        balance = balance.subtract(amt);
        version++;
    }

    // Credit account (for internal transfers)
    public synchronized void credit(BigDecimal amt) {
        if (amt.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("credit amount must be >0");
        balance = balance.add(amt);
        version++;
    }

    @Override public String toString() {
        return "BankAccount{" + id + ",user=" + userId + ",bank=" + bankId + ",acct=" + accountNumber +
                ",bal=" + balance + ",reserved=" + reserved + ",primary=" + isPrimary + ",status=" + status + "}";
    }
}