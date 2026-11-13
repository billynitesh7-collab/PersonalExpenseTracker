package com.expense.storage;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Creates the local data files once, without replacing any file that already exists. */
public final class DataInitializer {
    private DataInitializer() {
    }

    public static void initialize(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        createEmptyArrayIfMissing(dataDirectory.resolve("transactions.json"));
        createEmptyArrayIfMissing(dataDirectory.resolve("budgets.json"));
    }

    private static void createEmptyArrayIfMissing(Path file) throws IOException {
        try {
            // CREATE_NEW makes this safe even if another startup creates the file at the same time.
            Files.writeString(file, "[]\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException ignored) {
            // Existing user data must never be overwritten during initialization.
        }
    }
}
