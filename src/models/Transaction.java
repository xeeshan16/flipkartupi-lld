package models;


import enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Transaction {
    public final UUID id;
    public final String idempotencyKey; // may be null
    public final UUID fromAccountId;
    public final UUID toAccountId;     // nullable (if external)
    public final String toIdentifier;  // phone/vpa/external
    public final BigDecimal amount;
    public volatile TransactionStatus status;
    public volatile String pspTxnId;
    volatile String errorCode;
    public volatile Instant createdAt;
    public volatile Instant updatedAt;
    public volatile int reconciliationAttempts;

    public Transaction(UUID id, String idempotencyKey, UUID fromAccountId,
                       UUID toAccountId, String toIdentifier, BigDecimal amount) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.toIdentifier = toIdentifier;
        this.amount = amount;
        this.status = TransactionStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.reconciliationAttempts = 0;
    }

    public synchronized void markSuccess(String pspTxnId) {
        this.status = TransactionStatus.SUCCESS;
        this.pspTxnId = pspTxnId;
        this.errorCode = null;
        this.updatedAt = Instant.now();
    }

    public synchronized void markFailed(String error) {
        this.status = TransactionStatus.FAILED;
        this.errorCode = error;
        this.updatedAt = Instant.now();
    }

    public synchronized void markPending(String pspTxnId) {
        this.status = TransactionStatus.PENDING;
        this.pspTxnId = pspTxnId;
        this.updatedAt = Instant.now();
    }

    @Override public String toString() {
        return "Transaction{" + id + ",from=" + fromAccountId + ",to=" + toAccountId + ",toIdent=" + toIdentifier +
                ",amt=" + amount + ",status=" + status + ",psp=" + pspTxnId + ",idempotency=" + idempotencyKey + "}";
    }
}