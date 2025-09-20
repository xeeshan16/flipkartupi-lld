package repository;

import models.Bank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankRepository {
    Bank createBank(String name, String code);
    Optional<Bank> findById(UUID id);
    List<Bank> listAll();
}
