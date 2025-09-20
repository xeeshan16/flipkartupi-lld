package repository;

import enums.TransactionStatus;
import exception.IdempotencyException;
import models.Transaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public interface TransactionRepository {

    Transaction saveNew(Transaction txn) throws IdempotencyException;
    Optional<Transaction> findById(UUID id);
    Optional<Transaction> findByIdempotencyKey(String key);
    List<Transaction> findByFromOrTo(UUID from, UUID to);
    List<Transaction> findByStatus(TransactionStatus status);
    List<Transaction> search(Predicate<Transaction> filter);
    void update(Transaction txn);
}
