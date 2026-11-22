package com.carenest.support;

import org.springframework.jdbc.core.JdbcTemplate;

public final class YearAgeGroupFixture {
    private YearAgeGroupFixture() {}

    public static void apply(JdbcTemplate jdbc, long yearId) {
        jdbc.update(
                "INSERT INTO school_year_age_group_members (school_year_id, age_group_id, name, short_name,"
                        + " age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note)"
                        + " SELECT ?, id, name, short_name, age_from_months, age_to_months, meal_sessions, max_class_size, nutrition_note"
                        + " FROM age_groups ON CONFLICT (school_year_id, age_group_id) DO NOTHING",
                yearId);
    }
}
