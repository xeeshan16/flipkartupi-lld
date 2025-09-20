package service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Simple in-memory registry. Good for demos/tests.
 */
public class InMemoryBankHealthRegistry implements BankHealthRegistry {
    private final ConcurrentMap<UUID, Boolean> map = new ConcurrentHashMap<>();

    @Override
    public boolean isDown(UUID bankId) {
        if (bankId == null) return false;
        return map.getOrDefault(bankId, false);
    }

    @Override
    public void markDown(UUID bankId) {
        if (bankId != null) map.put(bankId, true);
    }

    @Override
    public void markUp(UUID bankId) {
        if (bankId != null) map.put(bankId, false);
    }
}
