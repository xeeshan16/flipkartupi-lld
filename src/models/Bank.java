package models;

import java.util.UUID;

public class Bank {
    public final UUID id;
    final String name;
    final String code;

    public Bank(UUID id, String name, String code) {
        this.id = id; this.name = name; this.code = code;
    }
}
