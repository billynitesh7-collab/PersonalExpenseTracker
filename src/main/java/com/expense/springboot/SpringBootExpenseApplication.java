package com.expense.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Independent Spring Boot entry point; the legacy com.expense.Main remains unchanged. */
@SpringBootApplication
public class SpringBootExpenseApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringBootExpenseApplication.class, args);
    }
}
