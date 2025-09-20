package repository;

import models.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    User createUser(String name, String phone);
    Optional<User> findById(UUID id);
    Optional<User> findByPhone(String phone);
    List<User> listAll();
    void deactivateUser(UUID id);
}
