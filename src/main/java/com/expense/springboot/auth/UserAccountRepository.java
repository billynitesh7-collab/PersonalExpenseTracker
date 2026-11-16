package com.expense.springboot.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Optional;

@Repository
public class UserAccountRepository {
    private final JdbcTemplate jdbc;

    public UserAccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UserAccount create(String email, String passwordHash) {
        return jdbc.queryForObject("INSERT INTO users (email, password_hash) VALUES (?, ?) " +
                        "RETURNING id, email, password_hash, created_at",
                (row, index) -> new UserAccount(row.getLong("id"), row.getString("email"),
                        row.getString("password_hash"), row.getTimestamp("created_at").toInstant()),
                email.toLowerCase(Locale.ROOT), passwordHash);
    }

    public Optional<UserAccount> findByEmail(String email) {
        return jdbc.query("SELECT id, email, password_hash, created_at FROM users WHERE email = ?",
                (row, index) -> new UserAccount(row.getLong("id"), row.getString("email"),
                        row.getString("password_hash"), row.getTimestamp("created_at").toInstant()),
                email.toLowerCase(Locale.ROOT)).stream().findFirst();
    }
}
