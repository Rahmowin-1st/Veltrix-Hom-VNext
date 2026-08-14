-- Veltrix Hom vNext Part 2 completion hardening.
-- Additive only: refunds/reversals support, seasonal derived statistics,
-- deterministic derived achievements, and non-manipulative game notifications.

CREATE TABLE IF NOT EXISTS store_refund (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  purchase_id uuid NOT NULL REFERENCES store_purchase(id) ON DELETE CASCADE,
  amount bigint NOT NULL CHECK(amount > 0),
  idempotency_key text NOT NULL,
  coin_ledger_id uuid REFERENCES coin_ledger(id) ON DELETE SET NULL,
  inventory_removed boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(account_id,purchase_id),
  UNIQUE(account_id,idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_store_refund_account_time ON store_refund(account_id,created_at DESC);

ALTER TABLE season_progress ADD COLUMN IF NOT EXISTS participated boolean NOT NULL DEFAULT false;

-- V005 shipped an inert unitsCompleted achievement that the event-count engine could not advance.
-- Retire only that version and replace it with explicit derived-state achievements below.
UPDATE achievement_definition SET active=false
WHERE achievement_id='map-pathfinder-1' AND version=1;

INSERT INTO achievement_definition(achievement_id,version,category,criteria,progress_model,reward_definition,active) VALUES
 ('consistency-7',1,'CONSISTENCY','{"longestConsistency":7}','DERIVED','{}',true),
 ('map-pathfinder-2',1,'MAP','{"unitsCompleted":3}','DERIVED','{}',true),
 ('season-first',1,'SEASON','{"participated":true}','DERIVED','{}',true)
ON CONFLICT(achievement_id,version) DO UPDATE SET active=EXCLUDED.active;

CREATE OR REPLACE FUNCTION part2_touch_active_season(p_account uuid, p_at timestamptz)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  s_id text;
  s_ver integer;
  changed integer;
BEGIN
  SELECT season_id,version INTO s_id,s_ver
  FROM season_definition
  WHERE state='ACTIVE' AND start_at <= p_at AND end_at > p_at
  ORDER BY start_at DESC,version DESC LIMIT 1;
  IF s_id IS NULL THEN RETURN; END IF;

  INSERT INTO season_progress(account_id,season_id,season_version,participated,state)
  VALUES (p_account,s_id,s_ver,true,'ACTIVE')
  ON CONFLICT(account_id,season_id,season_version) DO NOTHING;
  GET DIAGNOSTICS changed = ROW_COUNT;
  IF changed = 0 THEN
    UPDATE season_progress SET participated=true,revision=revision+1,updated_at=now()
    WHERE account_id=p_account AND season_id=s_id AND season_version=s_ver AND participated=false;
    GET DIAGNOSTICS changed = ROW_COUNT;
  END IF;
  IF changed > 0 THEN
    INSERT INTO gaming_statistics(account_id,seasons_participated)
    VALUES (p_account,1)
    ON CONFLICT(account_id) DO UPDATE SET
      seasons_participated=gaming_statistics.seasons_participated+1,
      revision=gaming_statistics.revision+1,
      updated_at=now();
  END IF;
END $$;

CREATE OR REPLACE FUNCTION part2_active_season_xp() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s_id text; s_ver integer;
BEGIN
  IF NEW.amount <= 0 OR NEW.entry_type <> 'GRANT' THEN RETURN NEW; END IF;
  PERFORM part2_touch_active_season(NEW.account_id,NEW.created_at);
  SELECT season_id,version INTO s_id,s_ver FROM season_definition
  WHERE state='ACTIVE' AND start_at <= NEW.created_at AND end_at > NEW.created_at
  ORDER BY start_at DESC,version DESC LIMIT 1;
  IF s_id IS NOT NULL THEN
    UPDATE season_progress SET xp_earned=xp_earned+NEW.amount,revision=revision+1,updated_at=now()
    WHERE account_id=NEW.account_id AND season_id=s_id AND season_version=s_ver;
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_part2_season_xp ON xp_ledger;
CREATE TRIGGER trg_part2_season_xp AFTER INSERT ON xp_ledger
FOR EACH ROW EXECUTE FUNCTION part2_active_season_xp();

CREATE OR REPLACE FUNCTION part2_active_season_coins() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s_id text; s_ver integer;
BEGIN
  IF NEW.amount <= 0 OR NEW.entry_type <> 'GRANT' THEN RETURN NEW; END IF;
  PERFORM part2_touch_active_season(NEW.account_id,NEW.created_at);
  SELECT season_id,version INTO s_id,s_ver FROM season_definition
  WHERE state='ACTIVE' AND start_at <= NEW.created_at AND end_at > NEW.created_at
  ORDER BY start_at DESC,version DESC LIMIT 1;
  IF s_id IS NOT NULL THEN
    UPDATE season_progress SET coins_earned=coins_earned+NEW.amount,revision=revision+1,updated_at=now()
    WHERE account_id=NEW.account_id AND season_id=s_id AND season_version=s_ver;
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_part2_season_coins ON coin_ledger;
CREATE TRIGGER trg_part2_season_coins AFTER INSERT ON coin_ledger
FOR EACH ROW EXECUTE FUNCTION part2_active_season_coins();

CREATE OR REPLACE FUNCTION part2_claim_derived_achievement(
  p_account uuid,
  p_achievement text,
  p_progress bigint,
  p_target bigint
) RETURNS boolean LANGUAGE plpgsql AS $$
DECLARE
  v_version integer;
  changed integer;
  s_id text;
  s_ver integer;
  v_revision bigint;
BEGIN
  SELECT version INTO v_version FROM achievement_definition
  WHERE achievement_id=p_achievement AND active=true
  ORDER BY version DESC LIMIT 1;
  IF v_version IS NULL THEN RETURN false; END IF;

  INSERT INTO achievement_progress(account_id,achievement_id,definition_version,progress,state)
  VALUES (p_account,p_achievement,v_version,GREATEST(p_progress,0),CASE WHEN p_progress>0 THEN 'IN_PROGRESS' ELSE 'LOCKED' END)
  ON CONFLICT(account_id,achievement_id,definition_version) DO UPDATE
  SET progress=GREATEST(achievement_progress.progress,EXCLUDED.progress),
      state=CASE WHEN achievement_progress.state='LOCKED' AND EXCLUDED.progress>0 THEN 'IN_PROGRESS' ELSE achievement_progress.state END,
      revision=CASE WHEN EXCLUDED.progress>achievement_progress.progress THEN achievement_progress.revision+1 ELSE achievement_progress.revision END,
      updated_at=CASE WHEN EXCLUDED.progress>achievement_progress.progress THEN now() ELSE achievement_progress.updated_at END;

  IF p_progress < p_target THEN RETURN false; END IF;

  UPDATE achievement_progress SET
    progress=GREATEST(progress,p_target),state='CLAIMED',unlocked_at=COALESCE(unlocked_at,now()),
    revision=revision+1,updated_at=now()
  WHERE account_id=p_account AND achievement_id=p_achievement AND definition_version=v_version
    AND state NOT IN ('UNLOCKED','CLAIMED')
  RETURNING revision INTO v_revision;
  GET DIAGNOSTICS changed = ROW_COUNT;
  IF changed = 0 THEN RETURN false; END IF;

  INSERT INTO gaming_statistics(account_id,achievements_unlocked)
  VALUES(p_account,1)
  ON CONFLICT(account_id) DO UPDATE SET achievements_unlocked=gaming_statistics.achievements_unlocked+1,
    revision=gaming_statistics.revision+1,updated_at=now();

  SELECT season_id,version INTO s_id,s_ver FROM season_definition
  WHERE state='ACTIVE' AND start_at <= now() AND end_at > now()
  ORDER BY start_at DESC,version DESC LIMIT 1;
  IF s_id IS NOT NULL THEN
    UPDATE season_progress SET achievements_unlocked=achievements_unlocked+1,revision=revision+1,updated_at=now()
    WHERE account_id=p_account AND season_id=s_id AND season_version=s_ver;
  END IF;

  INSERT INTO game_state_event(account_id,event_type,causation_id,correlation_id,resulting_revision,payload,idempotency_key)
  VALUES(p_account,'ACHIEVEMENT_UNLOCKED',p_achievement,p_achievement,v_revision,
    jsonb_build_object('achievementId',p_achievement,'derived',true),
    'derived-achievement:'||p_achievement||':'||v_version)
  ON CONFLICT(account_id,idempotency_key) DO NOTHING;
  RETURN true;
END $$;

CREATE OR REPLACE FUNCTION part2_refresh_derived_achievements(p_account uuid)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE v_longest bigint; v_units bigint; v_seasons bigint;
BEGIN
  SELECT COALESCE(longest_consistency,0) INTO v_longest FROM consistency_state WHERE account_id=p_account;
  SELECT count(*) INTO v_units FROM map_unit_progress WHERE account_id=p_account AND state IN ('COMPLETED','REWARD_GRANTED');
  SELECT count(*) INTO v_seasons FROM season_progress WHERE account_id=p_account AND participated=true;
  PERFORM part2_claim_derived_achievement(p_account,'consistency-7',COALESCE(v_longest,0),7);
  PERFORM part2_claim_derived_achievement(p_account,'map-pathfinder-2',COALESCE(v_units,0),3);
  PERFORM part2_claim_derived_achievement(p_account,'season-first',COALESCE(v_seasons,0),1);
END $$;

CREATE OR REPLACE FUNCTION part2_consistency_derived_trigger() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  PERFORM part2_refresh_derived_achievements(NEW.account_id);
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_part2_consistency_derived ON consistency_state;
CREATE TRIGGER trg_part2_consistency_derived AFTER INSERT OR UPDATE OF longest_consistency ON consistency_state
FOR EACH ROW EXECUTE FUNCTION part2_consistency_derived_trigger();

CREATE OR REPLACE FUNCTION part2_unit_completion_trigger() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s_id text; s_ver integer;
BEGIN
  IF NEW.state='COMPLETED' AND OLD.state NOT IN ('COMPLETED','REWARD_GRANTED') THEN
    PERFORM part2_touch_active_season(NEW.account_id,now());
    SELECT season_id,version INTO s_id,s_ver FROM season_definition
    WHERE state='ACTIVE' AND start_at<=now() AND end_at>now() ORDER BY start_at DESC,version DESC LIMIT 1;
    IF s_id IS NOT NULL THEN
      UPDATE season_progress SET units_completed=units_completed+1,revision=revision+1,updated_at=now()
      WHERE account_id=NEW.account_id AND season_id=s_id AND season_version=s_ver;
    END IF;
    PERFORM part2_refresh_derived_achievements(NEW.account_id);
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_part2_unit_completion ON map_unit_progress;
CREATE TRIGGER trg_part2_unit_completion AFTER UPDATE OF state ON map_unit_progress
FOR EACH ROW EXECUTE FUNCTION part2_unit_completion_trigger();

CREATE OR REPLACE FUNCTION part2_achievement_season_trigger() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s_id text; s_ver integer;
BEGIN
  IF NEW.state='UNLOCKED' AND OLD.state NOT IN ('UNLOCKED','CLAIMED') THEN
    PERFORM part2_touch_active_season(NEW.account_id,now());
    SELECT season_id,version INTO s_id,s_ver FROM season_definition
    WHERE state='ACTIVE' AND start_at<=now() AND end_at>now() ORDER BY start_at DESC,version DESC LIMIT 1;
    IF s_id IS NOT NULL THEN
      UPDATE season_progress SET achievements_unlocked=achievements_unlocked+1,revision=revision+1,updated_at=now()
      WHERE account_id=NEW.account_id AND season_id=s_id AND season_version=s_ver;
    END IF;
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_part2_achievement_season ON achievement_progress;
CREATE TRIGGER trg_part2_achievement_season AFTER UPDATE OF state ON achievement_progress
FOR EACH ROW EXECUTE FUNCTION part2_achievement_season_trigger();

CREATE OR REPLACE FUNCTION part2_season_participation_derived_trigger() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.participated THEN PERFORM part2_refresh_derived_achievements(NEW.account_id); END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_part2_season_participation_derived ON season_progress;
CREATE TRIGGER trg_part2_season_participation_derived AFTER INSERT OR UPDATE OF participated ON season_progress
FOR EACH ROW EXECUTE FUNCTION part2_season_participation_derived_trigger();

-- Product notifications are state-change notices only. No streak-preservation pressure is generated.
CREATE OR REPLACE FUNCTION part2_game_notification_trigger() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.event_type IN ('LEVEL_UP','ACHIEVEMENT_UNLOCKED','MAP_UNLOCKED','UNIT_COMPLETED','ITEM_ACQUIRED') THEN
    INSERT INTO notification_intent(account_id,category,payload,scheduled_for,status,idempotency_key)
    VALUES(NEW.account_id,'SYSTEM_NOTICE',
      jsonb_build_object('kind','GAME_STATE','eventType',NEW.event_type,'eventId',NEW.event_id,'revision',NEW.resulting_revision),
      NULL,'PENDING','game-state:'||NEW.event_id)
    ON CONFLICT(account_id,idempotency_key) DO NOTHING;
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_part2_game_notification ON game_state_event;
CREATE TRIGGER trg_part2_game_notification AFTER INSERT ON game_state_event
FOR EACH ROW EXECUTE FUNCTION part2_game_notification_trigger();
