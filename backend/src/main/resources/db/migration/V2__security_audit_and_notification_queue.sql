-- Account lockout, and token revocation: JWTs carry token_version and stop working once it is bumped.
ALTER TABLE app_users
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP,
    ADD COLUMN password_changed_at TIMESTAMP,
    ADD COLUMN token_version INT NOT NULL DEFAULT 0;

-- A slot freed by a cancellation must be bookable again, so slot_id can only be
-- unique among appointments that still hold the slot.
ALTER TABLE appointments DROP CONSTRAINT appointments_slot_id_key;
CREATE UNIQUE INDEX uk_appointments_active_slot ON appointments(slot_id) WHERE status <> 'CANCELED';
CREATE INDEX idx_appointments_slot ON appointments(slot_id);

-- Append-only audit trail. actor_* columns are denormalized so entries survive user deletion.
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_user_id BIGINT,
    actor_email VARCHAR(160),
    actor_role VARCHAR(30),
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60),
    entity_id BIGINT,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    ip_address VARCHAR(64),
    details TEXT
);
CREATE INDEX idx_audit_occurred ON audit_logs(occurred_at DESC);
CREATE INDEX idx_audit_actor ON audit_logs(actor_user_id, occurred_at DESC);
CREATE INDEX idx_audit_action ON audit_logs(action, occurred_at DESC);

-- Notifications become a Postgres-backed job queue with retries.
ALTER TABLE notifications DROP CONSTRAINT notifications_status_check;
UPDATE notifications SET status = 'SENT' WHERE status NOT IN ('PENDING', 'SENT');
ALTER TABLE notifications
    ADD COLUMN type VARCHAR(40) NOT NULL DEFAULT 'REMINDER_24H',
    ADD COLUMN attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN locked_at TIMESTAMP,
    ADD COLUMN sent_at TIMESTAMP,
    ADD COLUMN last_error TEXT,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT notifications_status_check
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'DEAD', 'SKIPPED'));

-- Duplicates from older runs would block the idempotency constraint below.
DELETE FROM notifications n USING notifications d
WHERE n.appointment_id = d.appointment_id AND n.type = d.type AND n.id > d.id;
ALTER TABLE notifications ADD CONSTRAINT uk_notification_appointment_type UNIQUE (appointment_id, type);
CREATE INDEX idx_notifications_due ON notifications(next_attempt_at) WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX idx_notifications_status ON notifications(status);
