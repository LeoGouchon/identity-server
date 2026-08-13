ALTER TABLE invitation_token ADD COLUMN client_id VARCHAR(100);

UPDATE invitation_token SET client_id = 'default-api' WHERE client_id IS NULL;

ALTER TABLE invitation_token ALTER COLUMN client_id SET NOT NULL;
CREATE INDEX idx_invitation_token_client ON invitation_token(client_id);
