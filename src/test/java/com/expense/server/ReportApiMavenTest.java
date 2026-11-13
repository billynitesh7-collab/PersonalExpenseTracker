package com.expense.server;

import org.junit.jupiter.api.Test;

/** Runs the existing standalone test program as part of the Maven test lifecycle. */
class ReportApiMavenTest {
    @Test
    void existingMainBasedTestPasses() throws Exception {
        ReportApiTest.main(new String[0]);
    }
}
