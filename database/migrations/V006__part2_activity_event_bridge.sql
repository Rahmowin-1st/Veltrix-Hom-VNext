-- Veltrix Hom vNext Part 2: semantic Part 1 -> Part 2 activity bridge.
-- This migration does not calculate rewards. It only emits auditable, idempotent
-- server-authoritative ActivityEvents; the versioned Part 2 RewardPolicy remains the
-- sole reward decision authority.

CREATE OR REPLACE FUNCTION part2_enrich_activity_evidence()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_evidence jsonb;
BEGIN
  IF COALESCE(NEW.evidence, '{}'::jsonb) <> '{}'::jsonb THEN
    RETURN NEW;
  END IF;

  CASE NEW.event_type
    WHEN 'PROJECT_CREATED' THEN
      SELECT jsonb_build_object(
        'objectType','project','revision',p.revision,'status',p.status::text,
        'purposeChars',length(trim(COALESCE(p.purpose,''))))
      INTO v_evidence
      FROM project p
      WHERE p.id::text=NEW.object_id AND p.account_id=NEW.account_id AND p.deleted_at IS NULL;

    WHEN 'GOAL_COMPLETED' THEN
      SELECT jsonb_build_object(
        'objectType','goal','revision',g.revision,'status',g.status::text,
        'completedAt',g.completed_at)
      INTO v_evidence
      FROM goal g
      WHERE g.id::text=NEW.object_id AND g.account_id=NEW.account_id AND g.deleted_at IS NULL;

    WHEN 'SOURCE_ADDED' THEN
      SELECT jsonb_build_object(
        'objectType','source','revision',s.revision,'state',s.state::text,
        'chunkCount',(SELECT count(*) FROM source_chunk sc WHERE sc.source_id=s.id AND sc.account_id=s.account_id))
      INTO v_evidence
      FROM source s
      WHERE s.id::text=NEW.object_id AND s.account_id=NEW.account_id AND s.deleted_at IS NULL;

    WHEN 'TEST_COMPLETED', 'QUIZ_COMPLETED' THEN
      SELECT jsonb_build_object(
        'objectType','assessmentAttempt','revision',aa.revision,'state',aa.state::text,
        'score',aa.score,'kind',a.kind,
        'questionCount',(SELECT count(*) FROM assessment_question q WHERE q.assessment_id=a.id AND q.account_id=a.account_id))
      INTO v_evidence
      FROM assessment_attempt aa
      JOIN assessment a ON a.id=aa.assessment_id AND a.account_id=aa.account_id
      WHERE aa.id::text=NEW.object_id AND aa.account_id=NEW.account_id;

    WHEN 'PRACTICE_COMPLETED' THEN
      SELECT jsonb_build_object(
        'objectType','practiceSession','revision',ps.revision,'state',ps.state,
        'completedItems',(SELECT count(*) FROM practice_item pi WHERE pi.session_id=ps.id AND pi.account_id=ps.account_id AND pi.state='COMPLETED'))
      INTO v_evidence
      FROM practice_session ps
      WHERE ps.id::text=NEW.object_id AND ps.account_id=NEW.account_id;

    WHEN 'FLASHCARD_REVIEW_COMPLETED' THEN
      SELECT jsonb_build_object(
        'objectType','flashcardReview','cardId',fr.card_id,'rating',fr.rating,'reviewedAt',fr.reviewed_at)
      INTO v_evidence
      FROM flashcard_review fr
      WHERE fr.card_id::text=NEW.object_id AND fr.account_id=NEW.account_id
      ORDER BY fr.reviewed_at DESC,fr.id DESC
      LIMIT 1;

    WHEN 'MISTAKE_RESOLVED' THEN
      SELECT jsonb_build_object(
        'objectType','mistake','revision',m.revision,'status',m.status,'occurrenceCount',m.occurrence_count)
      INTO v_evidence
      FROM mistake m
      WHERE m.id::text=NEW.object_id AND m.account_id=NEW.account_id AND m.deleted_at IS NULL;

    WHEN 'NOTE_CREATED' THEN
      SELECT jsonb_build_object(
        'objectType','note','revision',n.revision,'bodyChars',length(trim(n.body)),
        'hasProject',(n.project_id IS NOT NULL),'hasSource',(n.source_id IS NOT NULL))
      INTO v_evidence
      FROM note n
      WHERE n.id::text=NEW.object_id AND n.account_id=NEW.account_id AND n.deleted_at IS NULL;

    WHEN 'MEANINGFUL_CHAT_SESSION' THEN
      SELECT jsonb_build_object(
        'objectType','chatExchange','assistantRevision',a.revision,'assistantState',a.state,
        'assistantChars',length(trim(a.content)),'userChars',length(trim(u.content)),
        'conversationId',a.conversation_id)
      INTO v_evidence
      FROM conversation_message a
      JOIN conversation_message u ON u.id=a.parent_message_id AND u.account_id=a.account_id
      WHERE a.id::text=NEW.object_id AND a.account_id=NEW.account_id
        AND a.role='ASSISTANT' AND u.role='USER';

    ELSE
      v_evidence := '{}'::jsonb;
  END CASE;

  NEW.evidence := COALESCE(v_evidence, '{}'::jsonb)
    || jsonb_build_object('provenance','SERVER_AUTHORITATIVE','evidenceSchemaVersion',1);
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_part2_enrich_activity_evidence ON activity_event;
CREATE TRIGGER trg_part2_enrich_activity_evidence
BEFORE INSERT ON activity_event
FOR EACH ROW EXECUTE FUNCTION part2_enrich_activity_evidence();

CREATE OR REPLACE FUNCTION part2_emit_project_created()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO activity_event(account_id,event_type,project_id,object_id,metadata,idempotency_key,meaningful)
  VALUES (NEW.account_id,'PROJECT_CREATED',NEW.id,NEW.id::text,
          jsonb_build_object('origin','PART1_DB_BRIDGE','schemaVersion',1),
          'part1-project-created:'||NEW.id::text,true)
  ON CONFLICT(account_id,idempotency_key) DO NOTHING;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_part2_project_created ON project;
CREATE TRIGGER trg_part2_project_created
AFTER INSERT ON project
FOR EACH ROW EXECUTE FUNCTION part2_emit_project_created();

CREATE OR REPLACE FUNCTION part2_emit_goal_completed()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF OLD.status IS DISTINCT FROM NEW.status AND NEW.status='COMPLETED' THEN
    INSERT INTO activity_event(account_id,event_type,project_id,object_id,metadata,idempotency_key,meaningful)
    VALUES (NEW.account_id,'GOAL_COMPLETED',NEW.project_id,NEW.id::text,
            jsonb_build_object('origin','PART1_DB_BRIDGE','schemaVersion',1,'goalRevision',NEW.revision),
            'part1-goal-completed:'||NEW.id::text,true)
    ON CONFLICT(account_id,idempotency_key) DO NOTHING;
  END IF;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_part2_goal_completed ON goal;
CREATE TRIGGER trg_part2_goal_completed
AFTER UPDATE OF status ON goal
FOR EACH ROW EXECUTE FUNCTION part2_emit_goal_completed();

CREATE OR REPLACE FUNCTION part2_emit_source_added()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO activity_event(account_id,event_type,project_id,object_id,metadata,idempotency_key,meaningful)
  VALUES (NEW.account_id,'SOURCE_ADDED',NULL,NEW.source_id::text,
          jsonb_build_object('origin','PART1_DB_BRIDGE','schemaVersion',1,'sourceVersion',NEW.source_version),
          'part1-source-added:'||NEW.source_id::text,true)
  ON CONFLICT(account_id,idempotency_key) DO NOTHING;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_part2_source_added ON source_chunk;
CREATE TRIGGER trg_part2_source_added
AFTER INSERT ON source_chunk
FOR EACH ROW EXECUTE FUNCTION part2_emit_source_added();

CREATE OR REPLACE FUNCTION part2_emit_note_created()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO activity_event(account_id,event_type,project_id,object_id,metadata,idempotency_key,meaningful)
  VALUES (NEW.account_id,'NOTE_CREATED',NEW.project_id,NEW.id::text,
          jsonb_build_object('origin','PART1_DB_BRIDGE','schemaVersion',1),
          'part1-note-created:'||NEW.id::text,true)
  ON CONFLICT(account_id,idempotency_key) DO NOTHING;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_part2_note_created ON note;
CREATE TRIGGER trg_part2_note_created
AFTER INSERT ON note
FOR EACH ROW EXECUTE FUNCTION part2_emit_note_created();

CREATE OR REPLACE FUNCTION part2_emit_meaningful_chat_session()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_user_content text;
  v_project_id uuid;
BEGIN
  IF NEW.role='ASSISTANT'
     AND OLD.state IS DISTINCT FROM NEW.state
     AND NEW.state='COMPLETED'
     AND length(trim(COALESCE(NEW.content,''))) >= 40
     AND NEW.parent_message_id IS NOT NULL THEN
    SELECT u.content,c.project_id
      INTO v_user_content,v_project_id
    FROM conversation_message u
    JOIN conversation c ON c.id=NEW.conversation_id AND c.account_id=NEW.account_id
    WHERE u.id=NEW.parent_message_id AND u.account_id=NEW.account_id AND u.role='USER';

    IF length(trim(COALESCE(v_user_content,''))) >= 10 THEN
      INSERT INTO activity_event(account_id,event_type,project_id,object_id,metadata,idempotency_key,meaningful)
      VALUES (NEW.account_id,'MEANINGFUL_CHAT_SESSION',v_project_id,NEW.id::text,
              jsonb_build_object('origin','PART1_DB_BRIDGE','schemaVersion',1,'conversationId',NEW.conversation_id),
              'part1-meaningful-chat:'||NEW.id::text,true)
      ON CONFLICT(account_id,idempotency_key) DO NOTHING;
    END IF;
  END IF;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_part2_meaningful_chat_session ON conversation_message;
CREATE TRIGGER trg_part2_meaningful_chat_session
AFTER UPDATE OF state ON conversation_message
FOR EACH ROW EXECUTE FUNCTION part2_emit_meaningful_chat_session();
