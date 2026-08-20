-- ==========================================
-- In-app notifications: the primary "something needs your attention"
-- channel now that outbound SMTP is unreliable (corporate relay rejects
-- anonymous sends). Written alongside every EmailService call, never
-- dependent on mail actually working.
-- ==========================================
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_username VARCHAR(100) NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    link VARCHAR(200),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_recipient ON notifications(recipient_username, created_at DESC);
CREATE INDEX idx_notifications_recipient_unread ON notifications(recipient_username, is_read);
