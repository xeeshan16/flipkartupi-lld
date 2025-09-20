package lock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class LockManager {
    // per account lock
    private final ConcurrentMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public Lock getLockForAccount(UUID accountId) {
        return locks.computeIfAbsent(accountId, id -> new ReentrantLock());
    }

    // Acquire multiple locks in stable order to avoid deadlocks
    public List<Lock> acquireLocksOrdered(Collection<UUID> accountIds) {
        List<UUID> sorted = accountIds.stream().sorted().collect(Collectors.toList());
        List<Lock> acquired = new ArrayList<>();
        for (UUID id : sorted) {
            Lock l = getLockForAccount(id);
            l.lock();
            acquired.add(l);
        }
        return acquired;
    }
}