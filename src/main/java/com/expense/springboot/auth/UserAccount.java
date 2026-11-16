package com.expense.springboot.auth;

import java.time.Instant;

/** Internal account row. Password hashes are never returned by the API. */
public record UserAccount(long id, String email, String passwordHash, Instant createdAt) { }
