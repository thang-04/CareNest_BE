-- ADR-0015: phiên Web nằm trong cookie HttpOnly mã hóa, Keycloak giữ phiên; bảng phiên DB không còn dùng.
DROP TABLE web_auth_sessions;
-- Không query nào tìm challenge theo lookup_hash; cột vẫn giữ để tính bucket rate khi resend.
DROP INDEX idx_web_auth_challenges_lookup;
