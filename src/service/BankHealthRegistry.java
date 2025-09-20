package service;

import java.util.UUID;

/**
 * Small registry to check if a bank is up/down. Implementations may query real health checks,
 * use monitoring data, or be an in-memory stub for testing.
 */
public interface BankHealthRegistry {
    /**
     * Returns true if the bank is currently considered down/unavailable.
     */
    boolean isDown(UUID bankId);

    /**
     * Mark bank as down (for testing/admin).
     */
    void markDown(UUID bankId);

    /**
     * Mark bank as up (available).
     */
    void markUp(UUID bankId);
}
