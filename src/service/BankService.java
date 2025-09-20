package service;

import models.Bank;
import repository.BankRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BankService {
    private final BankRepository bankRepo;
    public BankService(BankRepository repo) { this.bankRepo = repo; }
    public Bank registerBank(String name, String code) { return bankRepo.createBank(name, code); }
    public List<Bank> listBanks() { return bankRepo.listAll(); }
    public Optional<Bank> find(UUID id) { return bankRepo.findById(id); }
}
