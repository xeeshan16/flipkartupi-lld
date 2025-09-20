package repositoryimpl;

import enums.UserStatus;
import models.User;
import repository.UserRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryUserRepository implements UserRepository {
    private final ConcurrentMap<UUID, User> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> phoneToId = new ConcurrentHashMap<>();

    public User createUser(String name, String phone) {
        Objects.requireNonNull(phone);
        // ensure phone uniqueness: use compute
        UUID existing = phoneToId.compute(phone, (k, v) -> {
            if (v != null) return v;
            UUID newId = UUID.randomUUID();
            return newId;
        });
        UUID userId = phoneToId.get(phone);
        // if existed already, return existing user
        User u = byId.computeIfAbsent(userId, id -> new User(id, name, phone));
        return u;
    }

    public Optional<User> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
    public Optional<User> findByPhone(String phone) {
        UUID id = phoneToId.get(phone);
        return id == null ? Optional.empty() : findById(id);
    }
    public List<User> listAll() { return new ArrayList<>(byId.values()); }
    public void deactivateUser(UUID id) {
        User u = byId.get(id);
        if (u != null) u.status = UserStatus.DEACTIVATED;
    }
}
