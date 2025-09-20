package service;

import enums.TransactionStatus;
import models.Transaction;
import repository.TransactionRepository;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReconciliationService {

    private final TransactionService txnService;
    private final TransactionRepository txnRepo;
    private final ScheduledExecutorService scheduler;
    private final Duration maxPendingDuration = Duration.ofSeconds(120); // spec: PSP will update within 120s; configurable
    private final int maxAttempts = 5;

    public ReconciliationService(TransactionService txnService, TransactionRepository txnRepo) {
        this.txnService = txnService;
        this.txnRepo = txnRepo;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        // run every 10 seconds
        scheduler.scheduleWithFixedDelay(this::reconcileRun, 5, 10, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void reconcileRun() {
        try {
            List<Transaction> pending = txnRepo.findByStatus(TransactionStatus.PENDING);
            for (Transaction t : pending) {
                txnService.reconcileOnce(t, maxPendingDuration, maxAttempts);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}

