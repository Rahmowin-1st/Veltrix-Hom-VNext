CREATE TABLE account_external_identity (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  provider text NOT NULL CHECK(provider IN ('GOOGLE')),
  provider_subject text NOT NULL CHECK(length(provider_subject) BETWEEN 1 AND 255),
  email_snapshot text CHECK(email_snapshot IS NULL OR length(email_snapshot) BETWEEN 3 AND 320),
  email_verified boolean NOT NULL DEFAULT false,
  display_name_snapshot text CHECK(display_name_snapshot IS NULL OR length(display_name_snapshot) <= 80),
  picture_url_snapshot text CHECK(picture_url_snapshot IS NULL OR (length(picture_url_snapshot) <= 2048 AND picture_url_snapshot LIKE 'https://%')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  last_login_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(provider,provider_subject)
);
CREATE INDEX idx_account_external_identity_account ON account_external_identity(account_id,provider);


CREATE TABLE external_identity_deletion_tombstone (
  provider text NOT NULL CHECK(provider IN ('GOOGLE')),
  provider_subject_hash text NOT NULL CHECK(length(provider_subject_hash)=64),
  account_ref_hash text NOT NULL CHECK(length(account_ref_hash)=64),
  deleted_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(provider,provider_subject_hash)
);
CREATE INDEX idx_external_identity_deletion_tombstone_deleted_at ON external_identity_deletion_tombstone(deleted_at);

CREATE TABLE auth_exchange_replay (
  replay_hash text PRIMARY KEY CHECK(length(replay_hash)=64),
  provider text NOT NULL CHECK(provider IN ('GOOGLE')),
  account_id uuid REFERENCES account(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL CHECK(expires_at > created_at)
);
CREATE INDEX idx_auth_exchange_replay_expiry ON auth_exchange_replay(expires_at);

COMMENT ON TABLE account_external_identity IS 'Durable provider subject mapping. No provider token is stored.';
COMMENT ON TABLE external_identity_deletion_tombstone IS 'Hashed provider subject tombstone that survives account purge to prevent silent federated-account resurrection.';
COMMENT ON TABLE auth_exchange_replay IS 'Hashed successful federated exchange fingerprint for bounded replay protection. No raw token or nonce.';
