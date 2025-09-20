package models;

import enums.UserStatus;

import java.util.UUID;

public class User {
    public final UUID id;
    final String name;
    public final String phone;
    public volatile UserStatus status;

    public User(UUID id, String name, String phone) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.status = UserStatus.ACTIVE;
    }

    @Override public String toString() {
        return "User{" + id + "," + name + "," + phone + "," + status + "}";
    }
}

