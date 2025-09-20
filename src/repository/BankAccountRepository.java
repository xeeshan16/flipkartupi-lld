package repository;

import models.BankAccount;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository {

    BankAccount createAccount(UUID userId, UUID bankId, String accountNumber, BigDecimal initialBalance);
    Optional<BankAccount> findById(UUID id);
    Optional<BankAccount> findByBankAndAccountNumber(UUID bankId, String accountNumber);
    List<BankAccount> findByUserId(UUID userId);
    List<BankAccount> listAll();
    void update(BankAccount acct);
    void setPrimary(UUID userId, UUID accountId); // ensures only one primary
}
