package com.carenest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Khởi động toàn bộ context với PostgreSQL thật (Testcontainers). Cần Docker đang chạy. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CareNestApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void contextLoads() {
    }
}
