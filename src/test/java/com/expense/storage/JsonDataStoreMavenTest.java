package com.expense.storage;

import org.junit.jupiter.api.Test;

/** Runs the existing standalone test program as part of the Maven test lifecycle. */
class JsonDataStoreMavenTest {
    @Test
    void existingMainBasedTestPasses() throws Exception {
        JsonDataStoreTest.main(new String[0]);
    }
}
