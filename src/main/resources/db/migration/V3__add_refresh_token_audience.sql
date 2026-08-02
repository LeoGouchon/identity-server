ALTER TABLE refresh_token_session
    ADD COLUMN audience VARCHAR(255) NOT NULL DEFAULT 'hubscore-api';
