package com.carenest.repository.meals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class FoodMicronutrientsMigrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Test
    @DisplayName("NUT-26: dữ liệu cũ giữ nguyên với vi chất null; vi chất âm và min lớn hơn max bị CHECK chặn")
    void existingRowsGetNullMicronutrientsAndChecksApply() {
        var config = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        config.target("62").load().migrate();
        var jdbc = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        long ageGroup = jdbc.queryForObject("SELECT id FROM age_groups WHERE code = 'ag-3'", Long.class);
        long food = jdbc.queryForObject("""
                INSERT INTO foods (code, name, food_group, unit, purchase_unit, purchase_unit_size, unit_price,
                    kcal, protein, lipid, glucid, nutrient_source, storage, origin)
                VALUES ('TP001', 'Gạo tẻ', 'GRAIN', 'g', 'kg', 1000, 18000, 344, 7.9, 1, 76.2, 'Viện Dinh dưỡng',
                    'DRY', 'PLANT') RETURNING id
                """, Long.class);
        long meal = jdbc.queryForObject(
                "INSERT INTO age_group_meal_configs (age_group_id, effective_from, meal_price)"
                        + " VALUES (?, '2026-09-01', 30000) RETURNING id",
                Long.class,
                ageGroup);

        config.target("143").load().migrate();
        jdbc.update(
                "UPDATE foods SET calcium = 43, iron = 0, vitamin_a = 840.5, nutrient_source_version = '2017' WHERE id = ?",
                food);
        config.target("173").load().migrate();

        Map<String, Object> foodRow = jdbc.queryForMap("SELECT * FROM foods WHERE id = ?", food);
        assertThat((BigDecimal) foodRow.get("calcium")).isEqualByComparingTo("43");
        assertThat((BigDecimal) foodRow.get("iron")).isEqualByComparingTo("0");
        assertThat((BigDecimal) foodRow.get("vitamin_a")).isEqualByComparingTo("840.5");
        assertThat(foodRow).doesNotContainKey("nutrient_source_version");
        assertThat(foodRow.get("vitamin_c")).isNull();
        assertThat(foodRow.get("zinc")).isNull();
        assertThat(jdbc.queryForMap("SELECT * FROM age_group_meal_configs WHERE id = ?", meal))
                .containsEntry("iron_min", null)
                .containsEntry("vitamin_pp_max", null);

        assertThat(jdbc.update(
                        "UPDATE foods SET calcium = 43, zinc = 4.5, vitamin_a = 840.5, vitamin_b1 = 0.06 WHERE id = ?",
                        food))
                .isEqualTo(1);
        assertThat((BigDecimal) jdbc.queryForObject("SELECT vitamin_a FROM foods WHERE id = ?", BigDecimal.class, food))
                .isEqualByComparingTo("840.5");
        assertThat((BigDecimal) jdbc.queryForObject("SELECT zinc FROM foods WHERE id = ?", BigDecimal.class, food))
                .isEqualByComparingTo("4.5");
        assertThatThrownBy(() -> jdbc.update("UPDATE foods SET iron = -0.1 WHERE id = ?", food))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE foods SET zinc = -0.1 WHERE id = ?", food))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForMap("SELECT * FROM age_group_meal_configs WHERE id = ?", meal))
                .containsEntry("zinc_min", null)
                .containsEntry("zinc_max", null);
        assertThat(jdbc.update("UPDATE age_group_meal_configs SET zinc_min = 3, zinc_max = NULL WHERE id = ?", meal))
                .isEqualTo(1);
        assertThatThrownBy(() ->
                        jdbc.update("UPDATE age_group_meal_configs SET zinc_min = 5, zinc_max = 3 WHERE id = ?", meal))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE age_group_meal_configs SET zinc_min = -1 WHERE id = ?", meal))
                .isInstanceOf(DataIntegrityViolationException.class);

        // max trống = chỉ cần đạt mức tối thiểu
        assertThat(jdbc.update("UPDATE age_group_meal_configs SET iron_min = 6, iron_max = NULL WHERE id = ?", meal))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE age_group_meal_configs SET calcium_min = 600, calcium_max = 350 WHERE id = ?", meal))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE age_group_meal_configs SET vitamin_c_min = -1 WHERE id = ?", meal))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
