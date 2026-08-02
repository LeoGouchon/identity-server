CREATE TABLE signing_key (
    id UUID PRIMARY KEY,
    private_key TEXT NOT NULL,
    public_key TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
