-- Veltrix Hom vNext Backend Part 3 final depth migration.
-- Additive only; accepted Part 1/2 and V007 state remains authoritative.

CREATE TABLE IF NOT EXISTS learning_mode_definition (
  mode_id text NOT NULL,
  version integer NOT NULL CHECK(version > 0),
  state text NOT NULL CHECK(state IN ('DRAFT','SCHEDULED','ACTIVE','RETIRED')),
  answer_depth text NOT NULL,
  guiding_questions boolean NOT NULL DEFAULT false,
  reveal_answers_immediately boolean NOT NULL DEFAULT true,
  citation_preference text NOT NULL DEFAULT 'WHEN_USEFUL',
  correction_style text NOT NULL DEFAULT 'DIRECT',
  prompt_policy jsonb NOT NULL DEFAULT '{}'::jsonb,
  tool_policy jsonb NOT NULL DEFAULT '{}'::jsonb,
  assessment_policy jsonb NOT NULL DEFAULT '{}'::jsonb,
  activated_at timestamptz,
  retired_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(mode_id,version)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_learning_mode_one_active ON learning_mode_definition(mode_id) WHERE state='ACTIVE';

INSERT INTO learning_mode_definition(mode_id,version,state,answer_depth,guiding_questions,reveal_answers_immediately,citation_preference,correction_style,prompt_policy,tool_policy,assessment_policy,activated_at) VALUES
 ('DEFAULT',1,'ACTIVE','BALANCED',false,true,'WHEN_USEFUL','DIRECT','{"behavior":"balanced"}','{"allow":["calculator","translate","search"]}','{}',now()),
 ('TUTOR',1,'ACTIVE','ADAPTIVE',true,false,'WHEN_USEFUL','TEACHING','{"behavior":"scaffold_then_explain"}','{"allow":["calculator","translate","search"]}','{"hints":"progressive"}',now()),
 ('SOCRATIC',1,'ACTIVE','GUIDED',true,false,'WHEN_USEFUL','QUESTIONS_FIRST','{"behavior":"questions_before_answer"}','{"allow":["calculator","search"]}','{"hints":"questions"}',now()),
 ('EXPLAIN',1,'ACTIVE','SIMPLE',false,true,'WHEN_USEFUL','CLEAR','{"behavior":"explain"}','{"allow":["calculator","translate","search"]}','{}',now()),
 ('PRACTICE_COACH',1,'ACTIVE','CONCISE',true,false,'LOW','COACH','{"behavior":"practice_coach"}','{"allow":["calculator"]}','{"hints":"on_request","reveal":"after_check"}',now()),
 ('EXAM',1,'ACTIVE','EXAM',false,false,'LOW','EXAM','{"behavior":"exam_conditions"}','{"allow":["calculator"]}','{"hints":"disabled","reveal":"after_submit"}',now()),
 ('REVIEW',1,'ACTIVE','CONCISE',true,true,'HIGH','REVIEW','{"behavior":"review_and_recall"}','{"allow":["search"]}','{"hints":"limited"}',now()),
 ('CONCISE',1,'ACTIVE','CONCISE',false,true,'WHEN_USEFUL','DIRECT','{"behavior":"concise"}','{"allow":["calculator","translate","search"]}','{}',now()),
 ('DEEP_DIVE',1,'ACTIVE','DEEP',false,true,'HIGH','DETAILED','{"behavior":"deep_dive"}','{"allow":["calculator","translate","search"]}','{}',now())
ON CONFLICT(mode_id,version) DO NOTHING;

CREATE TABLE IF NOT EXISTS goal_dependency (
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  goal_id uuid NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
  depends_on_goal_id uuid NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(account_id,goal_id,depends_on_goal_id),
  CHECK(goal_id <> depends_on_goal_id)
);
CREATE INDEX IF NOT EXISTS idx_goal_dependency_project ON goal_dependency(account_id,project_id,goal_id);

CREATE TABLE IF NOT EXISTS goal_link (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  goal_id uuid NOT NULL REFERENCES goal(id) ON DELETE CASCADE,
  object_type text NOT NULL CHECK(object_type IN ('SOURCE','NOTE','CONVERSATION','TEST','QUIZ','PRACTICE','FLASHCARD_DECK','MISTAKE')),
  object_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(account_id,goal_id,object_type,object_id)
);
CREATE INDEX IF NOT EXISTS idx_goal_link_project ON goal_link(account_id,project_id,goal_id);

CREATE TABLE IF NOT EXISTS goal_suggestion (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  project_id uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  parent_goal_id uuid REFERENCES goal(id) ON DELETE CASCADE,
  title text NOT NULL,
  description text,
  provenance jsonb NOT NULL DEFAULT '{}'::jsonb,
  state text NOT NULL DEFAULT 'PROPOSED' CHECK(state IN ('PROPOSED','ACCEPTED','REJECTED','EXPIRED')),
  accepted_goal_id uuid REFERENCES goal(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revision bigint NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_goal_suggestion_project ON goal_suggestion(account_id,project_id,state,created_at DESC);

ALTER TABLE assessment_attempt ADD COLUMN IF NOT EXISTS deadline_at timestamptz;
ALTER TABLE assessment_attempt ADD COLUMN IF NOT EXISTS duration_seconds integer CHECK(duration_seconds IS NULL OR duration_seconds > 0);
ALTER TABLE assessment_attempt ADD COLUMN IF NOT EXISTS retest_of_attempt_id uuid REFERENCES assessment_attempt(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_assessment_attempt_history ON assessment_attempt(account_id,assessment_id,submitted_at DESC NULLS LAST,created_at DESC);

CREATE TABLE IF NOT EXISTS assessment_comparison (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  assessment_id uuid NOT NULL REFERENCES assessment(id) ON DELETE CASCADE,
  earlier_attempt_id uuid NOT NULL REFERENCES assessment_attempt(id) ON DELETE CASCADE,
  later_attempt_id uuid NOT NULL REFERENCES assessment_attempt(id) ON DELETE CASCADE,
  earlier_score numeric(7,4),
  later_score numeric(7,4),
  score_delta numeric(7,4),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(account_id,earlier_attempt_id,later_attempt_id),
  CHECK(earlier_attempt_id <> later_attempt_id)
);

CREATE OR REPLACE FUNCTION part3_capture_assessment_comparison() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE prior assessment_attempt%ROWTYPE;
BEGIN
  IF NEW.state='GRADED' AND OLD.state IS DISTINCT FROM 'GRADED' THEN
    SELECT * INTO prior FROM assessment_attempt
    WHERE account_id=NEW.account_id AND assessment_id=NEW.assessment_id AND id<>NEW.id AND state='GRADED' AND score IS NOT NULL
    ORDER BY submitted_at DESC NULLS LAST,created_at DESC LIMIT 1;
    IF prior.id IS NOT NULL AND NEW.score IS NOT NULL THEN
      INSERT INTO assessment_comparison(account_id,assessment_id,earlier_attempt_id,later_attempt_id,earlier_score,later_score,score_delta)
      VALUES(NEW.account_id,NEW.assessment_id,prior.id,NEW.id,prior.score,NEW.score,NEW.score-prior.score)
      ON CONFLICT(account_id,earlier_attempt_id,later_attempt_id) DO NOTHING;
    END IF;
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_part3_assessment_comparison ON assessment_attempt;
CREATE TRIGGER trg_part3_assessment_comparison AFTER UPDATE OF state ON assessment_attempt
FOR EACH ROW EXECUTE FUNCTION part3_capture_assessment_comparison();

CREATE TABLE IF NOT EXISTS note_version (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  note_id uuid NOT NULL REFERENCES note(id) ON DELETE CASCADE,
  source_revision bigint NOT NULL,
  title text NOT NULL,
  body text NOT NULL,
  snapshot_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(account_id,note_id,source_revision)
);
CREATE INDEX IF NOT EXISTS idx_note_version_note ON note_version(account_id,note_id,source_revision DESC);

CREATE OR REPLACE FUNCTION part3_capture_note_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.title IS DISTINCT FROM OLD.title OR NEW.body IS DISTINCT FROM OLD.body THEN
    INSERT INTO note_version(account_id,note_id,source_revision,title,body)
    VALUES(OLD.account_id,OLD.id,OLD.revision,OLD.title,OLD.body)
    ON CONFLICT(account_id,note_id,source_revision) DO NOTHING;
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_part3_capture_note_version ON note;
CREATE TRIGGER trg_part3_capture_note_version BEFORE UPDATE OF title,body ON note
FOR EACH ROW EXECUTE FUNCTION part3_capture_note_version();

CREATE TABLE IF NOT EXISTS account_deletion_lifecycle (
  request_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid,
  account_ref_hash text NOT NULL,
  state text NOT NULL CHECK(state IN ('PURGE_PENDING','PURGING','PURGED','FAILED_RETRYABLE')),
  requested_at timestamptz NOT NULL DEFAULT now(),
  purge_after timestamptz NOT NULL DEFAULT now(),
  started_at timestamptz,
  completed_at timestamptz,
  last_error_code text,
  retry_count integer NOT NULL DEFAULT 0 CHECK(retry_count >= 0),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_account_deletion_pending ON account_deletion_lifecycle(account_id) WHERE account_id IS NOT NULL AND state IN ('PURGE_PENDING','PURGING','FAILED_RETRYABLE');
CREATE INDEX IF NOT EXISTS idx_account_deletion_due ON account_deletion_lifecycle(state,purge_after) WHERE state IN ('PURGE_PENDING','FAILED_RETRYABLE');

CREATE TABLE IF NOT EXISTS account_export_audit (
  export_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  generated_at timestamptz NOT NULL DEFAULT now(),
  schema_version integer NOT NULL DEFAULT 3,
  entity_counts jsonb NOT NULL,
  payload_sha256 text NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_account_export_audit_account ON account_export_audit(account_id,generated_at DESC);

CREATE INDEX IF NOT EXISTS idx_student_signal_personalization ON student_signal(account_id,project_id,status,confidence DESC,updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_frontend_semantic_event_feed ON frontend_semantic_event(account_id,occurred_at DESC,event_id DESC);
CREATE INDEX IF NOT EXISTS idx_context_carry_account_revision ON context_carry_state(account_id,context_revision DESC);
