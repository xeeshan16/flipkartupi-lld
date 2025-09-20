// NEW file: service/RecipientResolver.java
package service;

import enums.AccountStatus;
import models.BankAccount;
import models.User;
import repository.BankAccountRepository;
import repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

public class RecipientResolver {
    private final UserRepository userRepo;
    private final BankAccountRepository acctRepo;

    public RecipientResolver(UserRepository userRepo, BankAccountRepository acctRepo) {
        this.userRepo = userRepo;
        this.acctRepo = acctRepo;
    }

    /**
     * Try to resolve toAccountId from toIdentifier:
     * - if toIdentifier is a phone and maps to a User -> return user's PRIMARY account if exists
     * - if toIdentifier is exactly a bank account number -> return matching BankAccount
     * - otherwise return Optional.empty() (external destination)
     */
    public Optional<UUID> resolveToAccountId(String toIdentifier) {
        if (toIdentifier == null) return Optional.empty();
        // 1) phone -> user -> primary account
        Optional<User> userOpt = userRepo.findByPhone(toIdentifier);
        if (userOpt.isPresent()) {
            UUID userId = userOpt.get().id;
            // pick primary account
            for (BankAccount a : acctRepo.findByUserId(userId)) {
                if (a.isPrimary && a.status == AccountStatus.ACTIVE) {
                    return Optional.of(a.id);
                }
            }
            // fallback to any active account
            for (BankAccount a : acctRepo.findByUserId(userId)) {
                if (a.status == AccountStatus.ACTIVE) {
                    return Optional.of(a.id);
                }
            }
        }
        // 2) exact bank account number -> find by bank/account (if your repo supports it)
        // This requires that the caller includes bank info or the account numbers are globally unique.
        // For demo, try linear search:
        for (BankAccount a : acctRepo.listAll()) {
            if (a.accountNumber.equals(toIdentifier)) {
                return Optional.of(a.id);
            }
        }
        // not resolved -> external
        return Optional.empty();
    }
}
