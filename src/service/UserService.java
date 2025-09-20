package service;

import models.User;
import repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

public class UserService {

    private final UserRepository userRepo;
    public UserService(UserRepository repo) {
        this.userRepo = repo; }

    public User onboardUser(String name, String phone) {
        // validate phone format if needed
        return userRepo.createUser(name, phone);
    }

    public Optional<User> findByPhone(String phone) { return userRepo.findByPhone(phone); }
    public Optional<User> findById(UUID id) { return userRepo.findById(id); }
}
