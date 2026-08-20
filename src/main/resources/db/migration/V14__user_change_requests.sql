-- ==========================================
-- Profile self-service (email/password change) gated behind admin
-- approval, mirroring the existing metric-definition approval workflow
-- (same PENDING_APPROVAL/APPROVED/REJECTED shape, reusing approval_status
-- values). new_password_hash is always pre-hashed at submission time -
-- the plaintext new password is never persisted, even while pending.
-- ==========================================
CREATE TABLE user_change_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_type VARCHAR(20) NOT NULL,
    new_email VARCHAR(255),
    new_password_hash VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    reviewed_by VARCHAR(100),
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP
);

CREATE INDEX idx_user_change_requests_user ON user_change_requests(user_id);
CREATE INDEX idx_user_change_requests_status ON user_change_requests(status);
