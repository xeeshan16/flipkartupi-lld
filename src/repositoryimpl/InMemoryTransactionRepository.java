package repositoryimpl;

import enums.TransactionStatus;
import exception.IdempotencyException;
import models.Transaction;
import repository.TransactionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class InMemoryTransactionRepository implements TransactionRepository {
    private final ConcurrentMap<UUID, Transaction> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> idempotencyIndex = new ConcurrentHashMap<>();

    public Transaction saveNew(Transaction txn) throws IdempotencyException {
        if (txn.idempotencyKey != null) {
            UUID existing = idempotencyIndex.putIfAbsent(txn.idempotencyKey, txn.id);
            if (existing != null) {
                // return existing transaction (prevent duplicate)
                throw new IdempotencyException("Idempotency key already exists: " + txn.idempotencyKey);
            }
        }
        byId.put(txn.id, txn);
        return txn;
    }

    public Optional<Transaction> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public Optional<Transaction> findByIdempotencyKey(String key) {
        if (key == null) return Optional.empty();
        UUID id = idempotencyIndex.get(key);
        return id == null ? Optional.empty() : findById(id);
    }
    public List<Transaction> findByFromOrTo(UUID from, UUID to) {
        return byId.values().stream()
                .filter(t -> (t.fromAccountId != null && t.fromAccountId.equals(from)) ||
                        (t.toAccountId != null && t.toAccountId.equals(to)))
                .collect(Collectors.toList());
    }
    public List<Transaction> findByStatus(TransactionStatus status) {
        return byId.values().stream().filter(t -> t.status == status).collect(Collectors.toList());
    }
    public List<Transaction> search(Predicate<Transaction> filter) {
        return byId.values().stream().filter(filter).collect(Collectors.toList());
    }
    public void update(Transaction txn) { byId.put(txn.id, txn); }
}