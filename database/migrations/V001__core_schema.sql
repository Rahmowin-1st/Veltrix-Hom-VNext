-- Veltrix Hom vNext Part 1 core relational schema
-- PostgreSQL source-of-truth; UTC timestamps only.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE project_status AS ENUM ('ACTIVE','PAUSED','COMPLETED','ARCHIVED');
CREATE TYPE goal_status AS ENUM ('ACTIVE','COMPLETED','PAUSED','CANCELLED','ARCHIVED');
CREATE TYPE memory_status AS ENUM ('ACTIVE','UNCERTAIN','CONTRADICTED','USER_CORRECTED','ARCHIVED');
CREATE TYPE source_state AS ENUM ('UPLOADING','PROCESSING','READY','PARTIAL','FAILED','UNSUPPORTED');
CREATE TYPE attempt_state AS ENUM ('NOT_STARTED','IN_PROGRESS','SUBMITTED','GRADED','ABANDONED');

CREATE TABLE account (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1,
  deleted_at timestamptz
);

CREATE TABLE account_credential (
  account_id uuid PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
  login_normalized text NOT NULL UNIQUE,
  password_hash text NOT NULL,
  password_algorithm text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE device_session (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  refresh_token_hash text NOT NULL UNIQUE,
  device_label text,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz
);
CREATE INDEX idx_device_session_account_active ON device_session(account_id, expires_at) WHERE revoked_at IS NULL;

CREATE TABLE user_profile (
  account_id uuid PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
  display_name text NOT NULL,
  username text UNIQUE,
  preferred_language text NOT NULL DEFAULT 'en',
  education_level text,
  subjects jsonb NOT NULL DEFAULT '[]'::jsonb,
  interests jsonb NOT NULL DEFAULT '[]'::jsonb,
  timezone text NOT NULL DEFAULT 'UTC',
  default_avatar_id text NOT NULL DEFAULT 'default',
  onboarding_state jsonb NOT NULL DEFAULT '{}'::jsonb,
  onboarding_complete boolean NOT NULL DEFAULT false,
  memory_enabled boolean NOT NULL DEFAULT true,
  accessibility jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1
);

CREATE TABLE user_setting (
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  category text NOT NULL,
  setting_key text NOT NULL,
  setting_value jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1,
  PRIMARY KEY(account_id, category, setting_key)
);

CREATE TABLE project (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  title text NOT NULL,
  purpose text,
  template_type text NOT NULL DEFAULT 'CUSTOM',
  icon_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  cover_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  theme_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  priority integer NOT NULL DEFAULT 0,
  deadline timestamptz,
  status project_status NOT NULL DEFAULT 'ACTIVE',
  default_learning_mode text NOT NULL DEFAULT 'DEFAULT',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  last_active_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1,
  archived_at timestamptz,
  deleted_at timestamptz
);
CREATE INDEX idx_project_account_status_recent ON project(account_id, status, last_active_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE project_instruction (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  body text NOT NULL,
  structured jsonb NOT NULL DEFAULT '{}'::jsonb,
  version integer NOT NULL,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(project_id, version)
);
CREATE UNIQUE INDEX uq_project_one_active_instruction ON project_instruction(project_id) WHERE active;

CREATE TABLE goal (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  title text NOT NULL,
  description text,
  goal_type text NOT NULL DEFAULT 'GENERAL',
  priority integer NOT NULL DEFAULT 0,
  target_date timestamptz,
  status goal_status NOT NULL DEFAULT 'ACTIVE',
  progress numeric(7,4),
  user_created boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  revision bigint NOT NULL DEFAULT 1,
  deleted_at timestamptz
);
CREATE INDEX idx_goal_project_status_priority ON goal(project_id, status, priority DESC) WHERE deleted_at IS NULL;

CREATE TABLE conversation (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid REFERENCES project(id) ON DELETE SET NULL,
  scope text NOT NULL,
  title text NOT NULL,
  learning_mode text NOT NULL DEFAULT 'DEFAULT',
  memory_enabled boolean NOT NULL DEFAULT true,
  project_memory_enabled boolean NOT NULL DEFAULT true,
  pinned boolean NOT NULL DEFAULT false,
  archived boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1,
  deleted_at timestamptz
);
CREATE INDEX idx_conversation_account_recent ON conversation(account_id, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_conversation_project_recent ON conversation(project_id, updated_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE conversation_message (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  conversation_id uuid NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
  parent_message_id uuid REFERENCES conversation_message(id) ON DELETE SET NULL,
  branch_key text,
  role text NOT NULL,
  state text NOT NULL,
  content text NOT NULL DEFAULT '',
  idempotency_key text NOT NULL,
  provider_message_id text,
  final_marker boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1,
  UNIQUE(account_id, idempotency_key)
);
CREATE INDEX idx_message_conversation_created ON conversation_message(conversation_id, created_at, id);

CREATE TABLE source (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  title text NOT NULL,
  source_type text NOT NULL,
  mime_type text NOT NULL,
  file_name text,
  storage_key text,
  size_bytes bigint NOT NULL DEFAULT 0 CHECK(size_bytes >= 0),
  content_hash text NOT NULL,
  state source_state NOT NULL DEFAULT 'UPLOADING',
  processing_progress smallint NOT NULL DEFAULT 0 CHECK(processing_progress BETWEEN 0 AND 100),
  language text,
  favorite boolean NOT NULL DEFAULT false,
  pinned boolean NOT NULL DEFAULT false,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1,
  archived_at timestamptz,
  deleted_at timestamptz
);
CREATE INDEX idx_source_account_state_recent ON source(account_id, state, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_source_account_hash ON source(account_id, content_hash) WHERE deleted_at IS NULL;

CREATE TABLE source_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  source_id uuid NOT NULL REFERENCES source(id) ON DELETE CASCADE,
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  version bigint NOT NULL,
  content_hash text NOT NULL,
  extraction_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(source_id, version)
);

CREATE TABLE source_chunk (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  source_id uuid NOT NULL REFERENCES source(id) ON DELETE CASCADE,
  source_version bigint NOT NULL,
  page integer,
  section text,
  offset_start integer NOT NULL,
  offset_end integer NOT NULL,
  chunk_text text NOT NULL,
  text_hash text NOT NULL,
  search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(chunk_text,''))) STORED,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK(offset_start >= 0 AND offset_end >= offset_start)
);
CREATE INDEX idx_source_chunk_source_version ON source_chunk(source_id, source_version);
CREATE INDEX idx_source_chunk_search ON source_chunk USING gin(search_vector);

CREATE TABLE source_project_link (
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  source_id uuid NOT NULL REFERENCES source(id) ON DELETE CASCADE,
  project_id uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(source_id, project_id)
);

CREATE TABLE source_collection (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  parent_id uuid REFERENCES source_collection(id) ON DELETE SET NULL,
  title text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1,
  deleted_at timestamptz
);

CREATE TABLE annotation (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  source_id uuid NOT NULL REFERENCES source(id) ON DELETE CASCADE,
  source_version bigint NOT NULL,
  chunk_id uuid REFERENCES source_chunk(id) ON DELETE SET NULL,
  annotation_type text NOT NULL,
  body text,
  locator jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1,
  deleted_at timestamptz
);

CREATE TABLE note (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid REFERENCES project(id) ON DELETE SET NULL,
  source_id uuid REFERENCES source(id) ON DELETE SET NULL,
  conversation_id uuid REFERENCES conversation(id) ON DELETE SET NULL,
  title text NOT NULL,
  body text NOT NULL,
  pinned boolean NOT NULL DEFAULT false,
  archived boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1,
  deleted_at timestamptz
);
CREATE INDEX idx_note_account_updated ON note(account_id, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_note_project_updated ON note(project_id, updated_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE assessment (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid REFERENCES project(id) ON DELETE SET NULL,
  kind text NOT NULL CHECK(kind IN ('TEST','QUIZ')),
  title text NOT NULL,
  state text NOT NULL DEFAULT 'READY',
  config jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_by text NOT NULL DEFAULT 'USER',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1,
  deleted_at timestamptz
);

CREATE TABLE assessment_question (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  assessment_id uuid NOT NULL REFERENCES assessment(id) ON DELETE CASCADE,
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  position integer NOT NULL,
  question_type text NOT NULL,
  prompt text NOT NULL,
  options jsonb NOT NULL DEFAULT '[]'::jsonb,
  expected_answer jsonb NOT NULL DEFAULT '[]'::jsonb,
  validator jsonb NOT NULL DEFAULT '{}'::jsonb,
  evidence jsonb NOT NULL DEFAULT '[]'::jsonb,
  UNIQUE(assessment_id, position)
);

CREATE TABLE assessment_attempt (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  assessment_id uuid NOT NULL REFERENCES assessment(id) ON DELETE CASCADE,
  project_id uuid REFERENCES project(id) ON DELETE SET NULL,
  state attempt_state NOT NULL DEFAULT 'NOT_STARTED',
  started_at timestamptz,
  last_active_at timestamptz,
  submitted_at timestamptz,
  score numeric(7,4),
  result_summary jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1
);
CREATE INDEX idx_attempt_account_active ON assessment_attempt(account_id, state, updated_at DESC);

CREATE TABLE assessment_answer (
  attempt_id uuid NOT NULL REFERENCES assessment_attempt(id) ON DELETE CASCADE,
  question_id uuid NOT NULL REFERENCES assessment_question(id) ON DELETE CASCADE,
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  answer jsonb NOT NULL,
  is_correct boolean,
  score numeric(7,4),
  answered_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(attempt_id, question_id)
);

CREATE TABLE practice_session (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid REFERENCES project(id) ON DELETE SET NULL,
  focus_topic text,
  source_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
  target_mistake_id uuid,
  config jsonb NOT NULL DEFAULT '{}'::jsonb,
  state text NOT NULL DEFAULT 'IN_PROGRESS',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1
);

CREATE TABLE practice_item (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id uuid NOT NULL REFERENCES practice_session(id) ON DELETE CASCADE,
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  position integer NOT NULL,
  prompt text NOT NULL,
  validator jsonb NOT NULL DEFAULT '{}'::jsonb,
  user_answer text,
  state text NOT NULL DEFAULT 'PROMPT',
  evidence jsonb NOT NULL DEFAULT '[]'::jsonb,
  UNIQUE(session_id, position)
);

CREATE TABLE flashcard_deck (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid REFERENCES project(id) ON DELETE SET NULL,
  source_id uuid REFERENCES source(id) ON DELETE SET NULL,
  scope text NOT NULL,
  title text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1,
  deleted_at timestamptz
);

CREATE TABLE flashcard (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  deck_id uuid NOT NULL REFERENCES flashcard_deck(id) ON DELETE CASCADE,
  project_id uuid REFERENCES project(id) ON DELETE SET NULL,
  front text NOT NULL,
  back text NOT NULL,
  explanation text,
  citation jsonb,
  tags jsonb NOT NULL DEFAULT '[]'::jsonb,
  favorite boolean NOT NULL DEFAULT false,
  suspended boolean NOT NULL DEFAULT false,
  created_by text NOT NULL DEFAULT 'USER',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1,
  deleted_at timestamptz
);

CREATE TABLE flashcard_schedule (
  card_id uuid PRIMARY KEY REFERENCES flashcard(id) ON DELETE CASCADE,
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  interval_days integer NOT NULL DEFAULT 0,
  ease numeric(5,3) NOT NULL DEFAULT 2.5,
  repetitions integer NOT NULL DEFAULT 0,
  lapses integer NOT NULL DEFAULT 0,
  due_at timestamptz NOT NULL DEFAULT now(),
  last_reviewed_at timestamptz,
  revision bigint NOT NULL DEFAULT 1
);
CREATE INDEX idx_flashcard_due_account ON flashcard_schedule(account_id, due_at);

CREATE TABLE flashcard_review (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  card_id uuid NOT NULL REFERENCES flashcard(id) ON DELETE CASCADE,
  rating text NOT NULL,
  previous_state jsonb NOT NULL,
  next_state jsonb NOT NULL,
  reviewed_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE mistake (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid REFERENCES project(id) ON DELETE SET NULL,
  source_id uuid REFERENCES source(id) ON DELETE SET NULL,
  topic text NOT NULL,
  skill text,
  prompt text NOT NULL,
  user_answer text,
  expected_answer text,
  explanation text,
  occurrence_count integer NOT NULL DEFAULT 1,
  first_seen_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  status text NOT NULL DEFAULT 'ACTIVE',
  confidence numeric(5,4) NOT NULL DEFAULT 1.0,
  revision bigint NOT NULL DEFAULT 1,
  deleted_at timestamptz
);
CREATE INDEX idx_mistake_project_topic ON mistake(project_id, topic, last_seen_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE learning_signal (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid REFERENCES project(id) ON DELETE SET NULL,
  topic text NOT NULL,
  kind text NOT NULL,
  signal_value numeric(12,6) NOT NULL,
  confidence numeric(5,4) NOT NULL,
  evidence_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
  observed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_learning_signal_account_topic ON learning_signal(account_id, topic, observed_at DESC);

CREATE TABLE memory_item (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  scope text NOT NULL,
  scope_id uuid,
  category text NOT NULL,
  canonical_statement text NOT NULL,
  attributes jsonb NOT NULL DEFAULT '{}'::jsonb,
  origin text NOT NULL,
  confidence numeric(5,4) NOT NULL CHECK(confidence BETWEEN 0 AND 1),
  status memory_status NOT NULL DEFAULT 'ACTIVE',
  sensitivity text NOT NULL DEFAULT 'NORMAL',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  last_confirmed_at timestamptz,
  revision bigint NOT NULL DEFAULT 1,
  invalidation_metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_memory_account_scope_status ON memory_item(account_id, scope, scope_id, status, updated_at DESC);

CREATE TABLE memory_evidence (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  memory_id uuid NOT NULL REFERENCES memory_item(id) ON DELETE CASCADE,
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  kind text NOT NULL,
  object_id text NOT NULL,
  observed_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(memory_id, kind, object_id)
);

CREATE TABLE activity_event (
  event_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  event_type text NOT NULL,
  occurred_at timestamptz NOT NULL DEFAULT now(),
  project_id uuid REFERENCES project(id) ON DELETE SET NULL,
  object_id text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  idempotency_key text NOT NULL,
  meaningful boolean NOT NULL DEFAULT false,
  UNIQUE(account_id, idempotency_key)
);
CREATE INDEX idx_activity_account_time ON activity_event(account_id, occurred_at DESC);
CREATE INDEX idx_activity_project_time ON activity_event(project_id, occurred_at DESC);

CREATE TABLE notification_preference (
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  category text NOT NULL,
  enabled boolean NOT NULL DEFAULT true,
  quiet_hours jsonb NOT NULL DEFAULT '{}'::jsonb,
  timezone text NOT NULL DEFAULT 'UTC',
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1,
  PRIMARY KEY(account_id, category)
);

CREATE TABLE notification_intent (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid REFERENCES project(id) ON DELETE SET NULL,
  category text NOT NULL,
  payload jsonb NOT NULL,
  scheduled_for timestamptz,
  status text NOT NULL DEFAULT 'PENDING',
  idempotency_key text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(account_id, idempotency_key)
);

CREATE TABLE translation_record (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid REFERENCES project(id) ON DELETE SET NULL,
  source_language text,
  target_language text NOT NULL,
  source_text text NOT NULL,
  translated_text text NOT NULL,
  provider text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);

CREATE TABLE tool_invocation (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  conversation_id uuid REFERENCES conversation(id) ON DELETE SET NULL,
  project_id uuid REFERENCES project(id) ON DELETE SET NULL,
  tool_id text NOT NULL,
  input_hash text NOT NULL,
  result_summary jsonb NOT NULL,
  deterministic boolean NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ai_usage (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  conversation_id uuid REFERENCES conversation(id) ON DELETE SET NULL,
  operation text NOT NULL,
  provider text NOT NULL,
  model text NOT NULL,
  provider_attempt integer NOT NULL DEFAULT 1,
  input_units bigint,
  output_units bigint,
  latency_ms bigint,
  first_token_ms bigint,
  success boolean NOT NULL,
  error_category text,
  request_id text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE feature_state (
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  feature_key text NOT NULL,
  state jsonb NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1,
  PRIMARY KEY(account_id, feature_key)
);

CREATE TABLE sync_cursor (
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  device_session_id uuid NOT NULL REFERENCES device_session(id) ON DELETE CASCADE,
  entity_scope text NOT NULL,
  cursor_value bigint NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(account_id, device_session_id, entity_scope)
);

CREATE TABLE idempotency_record (
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  idempotency_key text NOT NULL,
  operation text NOT NULL,
  request_hash text NOT NULL,
  response_status integer,
  response_body jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  PRIMARY KEY(account_id, idempotency_key)
);
CREATE INDEX idx_idempotency_expiry ON idempotency_record(expires_at);

-- Part 2 is intentionally not functional. Only event metadata can later be consumed by progression/economy services.
