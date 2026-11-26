package com.easylang.activitymonitoring.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class V5MigrationTest {

    @Test
    void migratesFilelessSprint3ReviewAndEnforcesSprint4ReviewShape() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:v5_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        Flyway.configure().dataSource(dataSource).target("4").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedOpenSprint3Review(jdbc);

        var result = Flyway.configure().dataSource(dataSource).target("5.2").load().migrate();

        assertEquals("5.2", result.targetSchemaVersion.toString());
        Map<String, Object> review = jdbc.queryForMap("""
                SELECT result, open_slot, document_storage_key, document_original_name,
                       document_content_type, document_size_bytes, document_sha256,
                       expected_checking_hours, decided_at, review_kind, week_start_date
                FROM reviews WHERE id = 1
                """);
        assertNull(review.get("result"));
        assertEquals(1, ((Number) review.get("open_slot")).intValue());
        assertNull(review.get("document_storage_key"));
        assertNull(review.get("document_original_name"));
        assertNull(review.get("document_content_type"));
        assertNull(review.get("document_size_bytes"));
        assertNull(review.get("document_sha256"));
        assertNull(review.get("expected_checking_hours"));
        assertNull(review.get("decided_at"));
        assertEquals("FINAL", review.get("review_kind"));
        assertNull(review.get("week_start_date"));

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO reviews (activity_id, chief_editor_id, cycle_number, submitted_at)
                VALUES (1, 2, 2, CURRENT_TIMESTAMP)
                """));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                UPDATE reviews SET result = 'REJECTED', open_slot = NULL WHERE id = 1
                """));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                UPDATE reviews SET document_storage_key = 'orphan-key' WHERE id = 1
                """));

        jdbc.update("""
                INSERT INTO notifications
                    (user_id, activity_id, type, period_date, dedupe_key, message, created_at)
                VALUES (1, 1, 'CORRECTION_REQUESTED', CURRENT_DATE,
                        'review:1:correction', 'Corrections requested', CURRENT_TIMESTAMP)
                """);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type = 'CORRECTION_REQUESTED'", Integer.class));
    }

    private void seedOpenSprint3Review(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO app_users (id, email, initials, full_name, password_hash, role)
                VALUES
                    (1, 'translator@example.test', 'TR', 'Test Translator', 'not-used', 'TRANSLATOR'),
                    (2, 'editor@example.test', 'ED', 'Test Editor', 'not-used', 'CHIEF_EDITOR'),
                    (3, 'manager@example.test', 'PM', 'Test Manager', 'not-used', 'PROJECT_MANAGER')
                """);
        jdbc.update("""
                INSERT INTO projects (id, project_name, project_manager_id, created_date)
                VALUES (1, 'Migration Project', 3, CURRENT_DATE)
                """);
        jdbc.update("""
                INSERT INTO activities (id, activity_number, activity_name, project_id, status, created_date)
                VALUES (1, 'EL-MIGRATION', 'Legacy review', 1, 'UNDER_CHECKING', CURRENT_DATE)
                """);
        jdbc.update("""
                INSERT INTO reviews (id, activity_id, chief_editor_id, cycle_number, submitted_at)
                VALUES (1, 1, 2, 1, CURRENT_TIMESTAMP)
                """);
    }
}
