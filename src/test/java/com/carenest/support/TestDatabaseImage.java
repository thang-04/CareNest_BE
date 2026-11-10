package com.carenest.support;

import org.testcontainers.utility.DockerImageName;

/** Image PostgreSQL dùng cho Testcontainers; khớp compose.yaml (PostgreSQL 17 kèm pgvector cho RAG). */
public final class TestDatabaseImage {

    public static final DockerImageName POSTGRES =
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");

    private TestDatabaseImage() {}
}
