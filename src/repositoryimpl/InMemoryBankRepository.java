package repositoryimpl;

import models.Bank;
import repository.BankRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryBankRepository implements BankRepository {
    private final ConcurrentMap<UUID, Bank> byId = new ConcurrentHashMap<>();

    public Bank createBank(String name, String code) {
        UUID id = UUID.randomUUID();
        Bank b = new Bank(id, name, code);
        byId.put(id, b);
        return b;
    }
    public Optional<Bank> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public List<Bank> listAll() { return new ArrayList<>(byId.values()); }
}