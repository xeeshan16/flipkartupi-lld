package repositoryimpl;

import lock.LockManager;
import models.BankAccount;
import repository.BankAccountRepository;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

public class InMemoryBankAccountRepository implements BankAccountRepository {
    private final ConcurrentMap<UUID, BankAccount> byId = new ConcurrentHashMap<>();
    // uniqueness for bankId+accountNumber
    private final ConcurrentMap<String, UUID> bankAcctIndex = new ConcurrentHashMap<>();
    private final InMemoryUserRepository userRepo;
    private final LockManager lockManager;

    public InMemoryBankAccountRepository(InMemoryUserRepository userRepo, LockManager lockManager) {
        this.userRepo = userRepo;
        this.lockManager = lockManager;
    }

    private String key(UUID bankId, String acct) {
        return bankId.toString() + ":" + acct; }

    public BankAccount createAccount(UUID userId, UUID bankId, String accountNumber, BigDecimal initialBalance) {
        Objects.requireNonNull(userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("user not found")));
        String k = key(bankId, accountNumber);
        UUID id = bankAcctIndex.compute(k, (kk, old) -> {
            if (old != null) return old; // existing account -> reuse id
            return UUID.randomUUID();
        });
        BankAccount acct = byId.computeIfAbsent(id, uuid -> new BankAccount(uuid, userId, bankId, accountNumber, initialBalance));
        return acct;
    }

    public Optional<BankAccount> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public Optional<BankAccount> findByBankAndAccountNumber(UUID bankId, String accountNumber) {
        UUID id = bankAcctIndex.get(key(bankId, accountNumber));
        return id == null ? Optional.empty() : findById(id);
    }
    public List<BankAccount> findByUserId(UUID userId) {
        return byId.values().stream().filter(a -> a.userId.equals(userId)).collect(Collectors.toList());
    }
    public List<BankAccount> listAll() { return new ArrayList<>(byId.values()); }
    public void update(BankAccount acct)
    { byId.put(acct.id, acct);
    }

    public void setPrimary(UUID userId, UUID accountId) {
        // Acquire locks for all user's accounts to avoid race; lock ordering by account id string ensures no deadlocks
        List<BankAccount> accounts = findByUserId(userId);
        List<UUID> ids = accounts.stream().map(a -> a.id).sorted().collect(Collectors.toList());
        List<Lock> locks = new ArrayList<>();
        try {
            // lock all
            for (UUID id: ids) {
                Lock l = lockManager.getLockForAccount(id);
                l.lock();
                locks.add(l);
            }
            // unset other primaries, set desired
            for (BankAccount a: accounts) {
                if (a.id.equals(accountId)) {
                    a.isPrimary = true;
                } else {
                    a.isPrimary = false;
                }
                update(a);
            }
        } finally {
            for (Lock l: locks) l.unlock();
        }
    }
}