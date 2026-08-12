-- Veltrix Hom vNext Part 1 completion: semantic RAG, citations, attachments,
-- automatic memory, deep practice, generated artifacts, durable jobs and provider/storage metadata.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE source ADD COLUMN IF NOT EXISTS storage_provider text NOT NULL DEFAULT 'local';
ALTER TABLE source ADD COLUMN IF NOT EXISTS storage_etag text;
ALTER TABLE source ADD COLUMN IF NOT EXISTS object_version text;
ALTER TABLE source ADD COLUMN IF NOT EXISTS storage_metadata jsonb NOT NULL DEFAULT '{}'::jsonb;

CREATE TABLE IF NOT EXISTS source_embedding (
  chunk_id uuid PRIMARY KEY REFERENCES source_chunk(id) ON DELETE CASCADE,
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  source_id uuid NOT NULL REFERENCES source(id) ON DELETE CASCADE,
  source_version bigint NOT NULL,
  embedding_model text NOT NULL,
  embedding_version text NOT NULL DEFAULT '1',
  text_hash text NOT NULL,
  embedding vector(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(account_id, source_id, source_version, chunk_id, embedding_model)
);
CREATE INDEX IF NOT EXISTS idx_source_embedding_owner_source ON source_embedding(account_id, source_id, source_version);
CREATE INDEX IF NOT EXISTS idx_source_embedding_hnsw ON source_embedding USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS assistant_message_citation (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  message_id uuid NOT NULL REFERENCES conversation_message(id) ON DELETE CASCADE,
  citation_index integer NOT NULL,
  source_id uuid NOT NULL REFERENCES source(id) ON DELETE CASCADE,
  source_version bigint NOT NULL,
  chunk_id uuid NOT NULL REFERENCES source_chunk(id) ON DELETE CASCADE,
  page integer,
  section text,
  text_hash text NOT NULL,
  retrieval_score numeric(8,6) NOT NULL,
  claim_group text,
  excerpt_start integer,
  excerpt_end integer,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(message_id, citation_index)
);
CREATE INDEX IF NOT EXISTS idx_assistant_citation_message ON assistant_message_citation(message_id, citation_index);

CREATE TABLE IF NOT EXISTS message_attachment (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  conversation_id uuid NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
  message_id uuid REFERENCES conversation_message(id) ON DELETE CASCADE,
  attachment_type text NOT NULL CHECK (attachment_type IN ('SOURCE','UPLOAD','IMAGE','NOTE','CAMERA')),
  object_id uuid,
  state text NOT NULL CHECK (state IN ('UPLOADING','PROCESSING','READY','FAILED')),
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_message_attachment_message ON message_attachment(message_id, state);
CREATE INDEX IF NOT EXISTS idx_message_attachment_conversation ON message_attachment(conversation_id, created_at DESC);

ALTER TABLE conversation_message ADD COLUMN IF NOT EXISTS variant_of_message_id uuid REFERENCES conversation_message(id) ON DELETE SET NULL;
ALTER TABLE conversation_message ADD COLUMN IF NOT EXISTS regeneration_index integer NOT NULL DEFAULT 0;
ALTER TABLE conversation_message ADD COLUMN IF NOT EXISTS cancelled_at timestamptz;
CREATE INDEX IF NOT EXISTS idx_message_lineage ON conversation_message(variant_of_message_id, regeneration_index);

CREATE TABLE IF NOT EXISTS ai_provider_attempt (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  conversation_id uuid REFERENCES conversation(id) ON DELETE SET NULL,
  message_id uuid REFERENCES conversation_message(id) ON DELETE SET NULL,
  request_id text NOT NULL,
  operation text NOT NULL,
  provider_id text NOT NULL,
  model_id text NOT NULL,
  tier text NOT NULL,
  attempt integer NOT NULL,
  status text NOT NULL,
  latency_ms bigint,
  first_token_ms bigint,
  error_code text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ai_provider_attempt_request ON ai_provider_attempt(request_id, attempt);

CREATE TABLE IF NOT EXISTS memory_candidate (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  scope text NOT NULL,
  scope_id uuid,
  category text NOT NULL,
  statement text NOT NULL,
  canonical_statement text NOT NULL,
  origin text NOT NULL,
  confidence numeric(5,4) NOT NULL CHECK(confidence BETWEEN 0 AND 1),
  sensitivity text NOT NULL DEFAULT 'NORMAL',
  evidence_type text NOT NULL,
  evidence_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
  observed_at timestamptz NOT NULL DEFAULT now(),
  status text NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','ACCEPTED','UNCERTAIN','CONFLICT','REJECTED')),
  processed_at timestamptz,
  UNIQUE(account_id, scope, scope_id, category, canonical_statement, evidence_type)
);
CREATE INDEX IF NOT EXISTS idx_memory_candidate_pending ON memory_candidate(status, observed_at) WHERE status='PENDING';

CREATE TABLE IF NOT EXISTS post_response_job (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  conversation_id uuid NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
  user_message_id uuid NOT NULL REFERENCES conversation_message(id) ON DELETE CASCADE,
  assistant_message_id uuid NOT NULL REFERENCES conversation_message(id) ON DELETE CASCADE,
  job_type text NOT NULL,
  status text NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
  attempts integer NOT NULL DEFAULT 0,
  available_at timestamptz NOT NULL DEFAULT now(),
  last_error_code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(assistant_message_id, job_type)
);
CREATE INDEX IF NOT EXISTS idx_post_response_job_pending ON post_response_job(status, available_at) WHERE status='PENDING';

ALTER TABLE practice_session ADD COLUMN IF NOT EXISTS difficulty integer NOT NULL DEFAULT 2 CHECK(difficulty BETWEEN 1 AND 5);
ALTER TABLE practice_session ADD COLUMN IF NOT EXISTS target_item_count integer NOT NULL DEFAULT 8 CHECK(target_item_count BETWEEN 1 AND 100);
ALTER TABLE practice_session ADD COLUMN IF NOT EXISTS adaptive boolean NOT NULL DEFAULT true;
ALTER TABLE practice_session ADD COLUMN IF NOT EXISTS hint_policy text NOT NULL DEFAULT 'ON_REQUEST';
ALTER TABLE practice_session ADD COLUMN IF NOT EXISTS reveal_policy text NOT NULL DEFAULT 'AFTER_CHECK';
ALTER TABLE practice_session ADD COLUMN IF NOT EXISTS current_position integer NOT NULL DEFAULT 0;
ALTER TABLE practice_session ADD COLUMN IF NOT EXISTS completed_at timestamptz;
ALTER TABLE practice_session ADD COLUMN IF NOT EXISTS summary jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE practice_item ADD COLUMN IF NOT EXISTS item_type text NOT NULL DEFAULT 'SHORT_ANSWER';
ALTER TABLE practice_item ADD COLUMN IF NOT EXISTS expected_answer text;
ALTER TABLE practice_item ADD COLUMN IF NOT EXISTS explanation text;
ALTER TABLE practice_item ADD COLUMN IF NOT EXISTS difficulty integer NOT NULL DEFAULT 2 CHECK(difficulty BETWEEN 1 AND 5);
ALTER TABLE practice_item ADD COLUMN IF NOT EXISTS topic text;
ALTER TABLE practice_item ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE practice_item ADD COLUMN IF NOT EXISTS revision bigint NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS practice_attempt (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  session_id uuid NOT NULL REFERENCES practice_session(id) ON DELETE CASCADE,
  item_id uuid NOT NULL REFERENCES practice_item(id) ON DELETE CASCADE,
  answer text NOT NULL,
  correct boolean,
  score numeric(7,4),
  attempt_number integer NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(item_id, attempt_number)
);
CREATE INDEX IF NOT EXISTS idx_practice_attempt_session ON practice_attempt(session_id, created_at);

CREATE TABLE IF NOT EXISTS practice_hint (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  session_id uuid NOT NULL REFERENCES practice_session(id) ON DELETE CASCADE,
  item_id uuid NOT NULL REFERENCES practice_item(id) ON DELETE CASCADE,
  hint_index integer NOT NULL,
  body text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(item_id, hint_index)
);

CREATE TABLE IF NOT EXISTS practice_feedback (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  session_id uuid NOT NULL REFERENCES practice_session(id) ON DELETE CASCADE,
  item_id uuid NOT NULL REFERENCES practice_item(id) ON DELETE CASCADE,
  attempt_id uuid NOT NULL REFERENCES practice_attempt(id) ON DELETE CASCADE,
  correct boolean NOT NULL,
  score numeric(7,4) NOT NULL,
  explanation text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(attempt_id)
);

CREATE TABLE IF NOT EXISTS generated_artifact (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid REFERENCES project(id) ON DELETE SET NULL,
  conversation_id uuid REFERENCES conversation(id) ON DELETE SET NULL,
  source_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
  artifact_type text NOT NULL CHECK(artifact_type IN ('QUIZ','TEST','FLASHCARDS','PRACTICE','GOAL_SUGGESTION')),
  state text NOT NULL DEFAULT 'DRAFT' CHECK(state IN ('DRAFT','READY','ARCHIVED','FAILED')),
  title text NOT NULL,
  payload jsonb NOT NULL,
  provenance jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_by text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_generated_artifact_account ON generated_artifact(account_id, created_at DESC);

CREATE TABLE IF NOT EXISTS background_job (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid REFERENCES account(id) ON DELETE CASCADE,
  job_type text NOT NULL,
  object_id text,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
  attempts integer NOT NULL DEFAULT 0,
  available_at timestamptz NOT NULL DEFAULT now(),
  locked_at timestamptz,
  last_error_code text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  idempotency_key text NOT NULL UNIQUE
);
CREATE INDEX IF NOT EXISTS idx_background_job_ready ON background_job(status, available_at) WHERE status='PENDING';

CREATE TABLE IF NOT EXISTS object_access_audit (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  source_id uuid REFERENCES source(id) ON DELETE SET NULL,
  action text NOT NULL,
  storage_key_hash text NOT NULL,
  occurred_at timestamptz NOT NULL DEFAULT now()
);

-- Hot paths used by final snapshots/search/runtime gates.
CREATE INDEX IF NOT EXISTS idx_source_chunk_trgm ON source_chunk USING gin (chunk_text gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_practice_session_account_state ON practice_session(account_id, state, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_practice_item_session_state ON practice_item(session_id, state, position);
CREATE INDEX IF NOT EXISTS idx_message_attachment_owner_state ON message_attachment(account_id, state, updated_at DESC);
