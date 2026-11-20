package com.carenest;

import com.carenest.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Khởi động toàn bộ context với PostgreSQL thật (Testcontainers). Cần Docker đang chạy. */
@Import(PostgresTestcontainer.class)
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CareNestApplicationTests {

    @Test
    void contextLoads() {}
}
