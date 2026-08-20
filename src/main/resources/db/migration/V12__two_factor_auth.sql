-- ==========================================
-- Email-based 2FA login: ADMIN/OPERATOR logins issue a short-lived OTP
-- challenge instead of a JWT directly. Each row is a single OTP attempt,
-- correlated to the client via an opaque temp_token (never a JWT, so
-- JwtAuthFilter can never mistake a pending challenge for a real session).
-- ==========================================
CREATE TABLE user_otp_verifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    otp_code VARCHAR(6) NOT NULL,
    temp_token VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_otp_verifications_temp_token ON user_otp_verifications(temp_token);
CREATE INDEX idx_user_otp_verifications_user_id ON user_otp_verifications(user_id);
