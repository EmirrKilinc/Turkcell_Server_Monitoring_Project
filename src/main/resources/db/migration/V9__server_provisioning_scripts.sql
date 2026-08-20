-- ==========================================
-- Admin-managed, ordered list of extra root commands run during every
-- server onboarding (SshProvisioningService.provision), before the
-- hardcoded security-critical steps (restricted-user shell, directory
-- reset, agent upload) execute. No unique constraint on execution_order -
-- ties are broken by id, keeping the move-up/move-down swap logic simple.
-- ==========================================
CREATE TABLE server_provisioning_scripts (
    id               BIGSERIAL PRIMARY KEY,
    command_line     VARCHAR(1000) NOT NULL,
    description      VARCHAR(300)  NOT NULL,
    execution_order  INT           NOT NULL,
    is_enabled       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO server_provisioning_scripts (command_line, description, execution_order, is_enabled) VALUES
('useradd -m -s /bin/bash monitoring_user || true', 'Izleme kullanicisi olustur', 1, TRUE),
('mkdir -p /data01/monitoring && chown -R monitoring_user:monitoring_user /data01/monitoring', 'Veri dizini olustur ve sahiplik ata', 2, TRUE),
('setfacl -m u:monitoring_user:r /etc/ssh/sshd_config', 'SSH config okuma izni tanimla', 3, TRUE);

CREATE INDEX idx_provisioning_scripts_order ON server_provisioning_scripts (execution_order);
