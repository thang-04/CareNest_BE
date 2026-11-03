package com.carenest.repository.meals;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class AgeGroupNormsInGramsMigrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void percentNormsAreConvertedToGramsAndAnimalShareColumnsDropped() {
        var config = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        config.target("57").load().migrate();
        var jdbc = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        long ag2 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-2'", Long.class);
        long ag3 = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        // kcal 600–800 (giữa 700), P 13–20 %, L 25–35 %, G 47–60 %, đạm ĐV 50–60 %, béo ĐV chưa nhập
        long full = jdbc.queryForObject("""
                INSERT INTO age_group_meal_configs (age_group_id, effective_from, meal_price, kcal_min, kcal_max,
                    protein_pct_min, protein_pct_max, lipid_pct_min, lipid_pct_max, glucid_pct_min, glucid_pct_max,
                    animal_protein_pct_min, animal_protein_pct_max)
                VALUES (?, '2026-09-01', 30000, 600, 800, 13, 20, 25, 35, 47, 60, 50, 60) RETURNING id
                """, Long.class, ag2);
        long priceOnly = jdbc.queryForObject(
                "INSERT INTO age_group_meal_configs (age_group_id, effective_from, meal_price)"
                        + " VALUES (?, '2026-09-01', 25000) RETURNING id",
                Long.class,
                ag3);

        config.target("58").load().migrate();

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM age_group_meal_configs WHERE id = ?", full);
        // Tổng đạm 700 × 13 % ÷ 4 = 22,75 g – 700 × 20 % ÷ 4 = 35 g; ĐV = tổng × 50–60 %, TV = tổng × 40–50 %
        assertThat((BigDecimal) row.get("animal_protein_g_min")).isEqualByComparingTo("11.4");
        assertThat((BigDecimal) row.get("animal_protein_g_max")).isEqualByComparingTo("21");
        assertThat((BigDecimal) row.get("plant_protein_g_min")).isEqualByComparingTo("9.1");
        assertThat((BigDecimal) row.get("plant_protein_g_max")).isEqualByComparingTo("17.5");
        assertThat(row.get("animal_lipid_g_min")).isNull();
        assertThat(row.get("plant_lipid_g_max")).isNull();
        assertThat((BigDecimal) row.get("glucid_g_min")).isEqualByComparingTo("82.3");
        assertThat((BigDecimal) row.get("glucid_g_max")).isEqualByComparingTo("105");
        assertThat((BigDecimal) row.get("protein_pct_min")).isEqualByComparingTo("13");
        assertThat(row.get("lunch_kcal_pct_min")).isNull();
        assertThat(row).doesNotContainKeys("animal_protein_pct_min", "animal_lipid_pct_max");

        Map<String, Object> empty = jdbc.queryForMap("SELECT * FROM age_group_meal_configs WHERE id = ?", priceOnly);
        assertThat(empty.get("glucid_g_min")).isNull();
        assertThat(empty.get("animal_protein_g_min")).isNull();
    }
}
