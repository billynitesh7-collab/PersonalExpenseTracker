package com.expense.server;

import org.junit.jupiter.api.Test;

/** Runs the existing standalone test program as part of the Maven test lifecycle. */
class BudgetApiMavenTest {
    @Test
    void existingMainBasedTestPasses() throws Exception {
        BudgetApiTest.main(new String[0]);
    }
}
