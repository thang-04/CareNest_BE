package com.carenest.service.child.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.carenest.exception.GlobalException;
import com.carenest.integration.storage.FileStorage;
import com.carenest.security.CurrentUser;
import com.carenest.security.TestPermissions;
import com.carenest.service.child.ChildPhotoService;
import com.carenest.utils.ApiCode;
import com.carenest.utils.enums.CareNestRole;
import com.carenest.utils.enums.FileCategory;
import jakarta.persistence.EntityManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Transactional
@Testcontainers(disabledWithoutDocker = true)
class ChildPhotoReplaceIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");

    @Autowired
    ChildPhotoService photos;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entities;

    @MockitoBean
    FileStorage storage;

    CurrentUser principal;
    long childId;
    long guardianId;
    MockMultipartFile file;

    @BeforeEach
    void setup() throws Exception {
        long userId = jdbc.queryForObject("""
                INSERT INTO user_accounts (keycloak_user_id, username, full_name, primary_role)
                VALUES ('kc-photo', 'ht.photo', 'Kiểm thử', 'PRINCIPAL') RETURNING id
                """, Long.class);
        principal = TestPermissions.userWithDefaults(userId, CareNestRole.PRINCIPAL);
        jdbc.update("INSERT INTO staff_assignments (user_id, staff_role, valid_from) VALUES (?, 'PRINCIPAL', '2020-01-01')", userId);
        long campusId = jdbc.queryForObject(
                "INSERT INTO campuses (code, name) VALUES ('PHOTO', 'Điểm trường giả') RETURNING id", Long.class);
        childId = jdbc.queryForObject("""
                INSERT INTO children (code, full_name, date_of_birth, gender, status, enrolled_on, intake_campus_id)
                VALUES ('PHOTOTEST', 'Trẻ giả', '2023-01-02', 'MALE', 'PENDING_PLACEMENT', '2026-09-01', ?) RETURNING id
                """, Long.class, campusId);
        guardianId = jdbc.queryForObject("""
                INSERT INTO child_guardians (child_id, position, full_name, relationship, phone, citizen_id)
                VALUES (?, 0, 'Phụ huynh giả', 'MOTHER', '0900000000', '001000000001') RETURNING id
                """, Long.class, childId);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        file = new MockMultipartFile("photo", "test.png", "image/png", bytes.toByteArray());
    }

    @Test
    void replacesGuardianAndRecordsLabel() {
        when(storage.maxBytes(FileCategory.CHILD_AVATAR, null)).thenReturn(2097152L);
        when(storage.store(eq(FileCategory.CHILD_AVATAR), eq(childId), any(byte[].class)))
                .thenReturn(new FileStorage.StoredFile("new-photo", "image/png", 70));
        var profile = photos.replaceGuardianPhoto(principal, childId, guardianId, file, "Cập nhật ảnh");
        assertThat(profile.guardians().getFirst().photoUrl()).endsWith("/guardian-photos/" + guardianId);
        entities.flush();
        assertThat(jdbc.queryForObject("SELECT photo_key FROM child_guardians WHERE id=?", String.class, guardianId))
                .isEqualTo("new-photo");
        assertThat(jdbc.queryForObject("SELECT changes->0->>'label' FROM child_history WHERE child_id=? AND action='UPDATED'", String.class, childId))
                .isEqualTo("Ảnh phụ huynh 1");
    }

    @Test
    void rejectsUnrelatedGuardian() {
        assertCode(() -> photos.replaceGuardianPhoto(principal, childId, -1L, file, "Lý do"), ApiCode.CHILD_GUARDIAN_NOT_FOUND);
    }

    @Test
    void rejectsLeftChild() {
        jdbc.update("UPDATE children SET status='LEFT', left_on='2026-10-10', left_reason='Chuyển nhà' WHERE id=?", childId);
        assertCode(() -> photos.replaceChildPhoto(principal, childId, file, "Lý do"), ApiCode.CHILD_ALREADY_LEFT);
    }

    @Test
    void rejectsOutOfScopeCampus() {
        CurrentUser vp = TestPermissions.userWithDefaults(-1L, CareNestRole.VICE_PRINCIPAL);
        assertCode(() -> photos.replaceChildPhoto(vp, childId, file, "Lý do"), ApiCode.CHILD_ACCESS_DENIED);
    }

    @Test
    void teacherCannotReplacePhotoDirectly() {
        CurrentUser teacher = TestPermissions.userWithDefaults(-1L, CareNestRole.TEACHER);
        assertCode(() -> photos.replaceChildPhoto(teacher, childId, file, "Lý do"), ApiCode.FORBIDDEN);
    }

    private static void assertCode(Runnable operation, ApiCode expected) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(GlobalException.class,
                ex -> assertThat(ex.getApiCode()).isEqualTo(expected));
    }
}
