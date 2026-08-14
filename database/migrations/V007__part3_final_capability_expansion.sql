-- Backend Part 3 additive capability expansion from Manager-accepted Part 2.
ALTER TABLE project ADD COLUMN IF NOT EXISTS description text;
ALTER TABLE project ADD COLUMN IF NOT EXISTS icon_key text;
ALTER TABLE project ADD COLUMN IF NOT EXISTS cover_asset_key text;
ALTER TABLE project ADD COLUMN IF NOT EXISTS accent_token text;
ALTER TABLE project ADD COLUMN IF NOT EXISTS subject_type text;
ALTER TABLE project ADD COLUMN IF NOT EXISTS default_source_policy jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE project ADD COLUMN IF NOT EXISTS module_enablement jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE project ADD COLUMN IF NOT EXISTS module_order jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE project ADD COLUMN IF NOT EXISTS layout_priority jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE project ADD COLUMN IF NOT EXISTS pinned_modules jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE project ADD COLUMN IF NOT EXISTS custom_quick_actions jsonb NOT NULL DEFAULT '[]'::jsonb;

CREATE TABLE project_template_definition(
 template_id text NOT NULL,version int NOT NULL CHECK(version>0),title_key text NOT NULL,default_learning_mode text NOT NULL DEFAULT 'DEFAULT',
 module_seed jsonb NOT NULL DEFAULT '[]',goal_suggestions jsonb NOT NULL DEFAULT '[]',source_policy jsonb NOT NULL DEFAULT '{}',analytics_config jsonb NOT NULL DEFAULT '{}',
 state text NOT NULL CHECK(state IN('DRAFT','ACTIVE','RETIRED')),created_at timestamptz NOT NULL DEFAULT now(),activated_at timestamptz,retired_at timestamptz,
 PRIMARY KEY(template_id,version));
CREATE UNIQUE INDEX uq_project_template_active ON project_template_definition(template_id) WHERE state='ACTIVE';

CREATE TABLE student_signal(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,project_id uuid REFERENCES project(id) ON DELETE CASCADE,
 signal_type text NOT NULL CHECK(signal_type IN('IDENTITY','PREFERENCE','LEARNING','FRICTION','PROJECT','PERFORMANCE','INTEREST','MISTAKE','GOAL','RECENT_CONTEXT')),
 structured_value jsonb NOT NULL,confidence numeric(5,4) NOT NULL CHECK(confidence BETWEEN 0 AND 1),source text NOT NULL,
 status text NOT NULL DEFAULT 'ACTIVE' CHECK(status IN('ACTIVE','CONFIRMED','REJECTED','ARCHIVED','SUPERSEDED')),last_confirmed_at timestamptz,review_after timestamptz,
 supersedes uuid REFERENCES student_signal(id) ON DELETE SET NULL,superseded_by uuid REFERENCES student_signal(id) ON DELETE SET NULL,revision bigint NOT NULL DEFAULT 1,
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),deleted_at timestamptz);
CREATE INDEX idx_student_signal_account_scope ON student_signal(account_id,project_id,signal_type,status,updated_at DESC) WHERE deleted_at IS NULL;
CREATE TABLE student_signal_evidence(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),signal_id uuid NOT NULL REFERENCES student_signal(id) ON DELETE CASCADE,account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
 evidence_kind text NOT NULL,object_id text NOT NULL,observed_at timestamptz NOT NULL DEFAULT now(),metadata jsonb NOT NULL DEFAULT '{}');
CREATE INDEX idx_student_signal_evidence_signal ON student_signal_evidence(signal_id,observed_at DESC);

CREATE TABLE personalization_recommendation(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,project_id uuid REFERENCES project(id) ON DELETE CASCADE,
 action_key text NOT NULL,target_type text,target_id text,reason_category text NOT NULL,evidence_refs jsonb NOT NULL DEFAULT '[]',confidence numeric(5,4) NOT NULL CHECK(confidence BETWEEN 0 AND 1),
 generated_at timestamptz NOT NULL DEFAULT now(),expires_at timestamptz NOT NULL,state text NOT NULL DEFAULT 'ACTIVE' CHECK(state IN('ACTIVE','DISMISSED','EXPIRED','CONSUMED')),revision bigint NOT NULL DEFAULT 1);
CREATE INDEX idx_personalization_active ON personalization_recommendation(account_id,project_id,expires_at DESC) WHERE state='ACTIVE';

CREATE TABLE context_carry_state(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,project_id uuid REFERENCES project(id) ON DELETE CASCADE,
 source_ids jsonb NOT NULL DEFAULT '[]',conversation_id uuid REFERENCES conversation(id) ON DELETE SET NULL,assessment_id uuid REFERENCES assessment(id) ON DELETE SET NULL,
 topic text,learning_mode text NOT NULL DEFAULT 'DEFAULT',origin text,return_destination text,context_revision bigint NOT NULL DEFAULT 1,
 state text NOT NULL DEFAULT 'ACTIVE' CHECK(state IN('ACTIVE','CONSUMED','ARCHIVED')),created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX idx_context_carry_account_recent ON context_carry_state(account_id,state,updated_at DESC);
CREATE UNIQUE INDEX uq_context_carry_one_active ON context_carry_state(account_id) WHERE state='ACTIVE';

CREATE TABLE source_relationship(
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,from_source_id uuid NOT NULL REFERENCES source(id) ON DELETE CASCADE,to_source_id uuid NOT NULL REFERENCES source(id) ON DELETE CASCADE,
 relationship_type text NOT NULL CHECK(relationship_type IN('RELATED','DERIVED_FROM','SUPPLEMENTS','CONTRADICTS','SAME_TOPIC','PROJECT_REFERENCE','USER_DEFINED')),
 created_by text NOT NULL CHECK(created_by IN('USER','AI_SUGGESTION','SYSTEM')),accepted boolean NOT NULL DEFAULT true,metadata jsonb NOT NULL DEFAULT '{}',revision bigint NOT NULL DEFAULT 1,
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),CHECK(from_source_id<>to_source_id),UNIQUE(account_id,from_source_id,to_source_id,relationship_type));
CREATE INDEX idx_source_relationship_from ON source_relationship(account_id,from_source_id,relationship_type);

CREATE TABLE live_content_config(
 config_key text NOT NULL,version int NOT NULL CHECK(version>0),config_type text NOT NULL CHECK(config_type IN('REWARD_POLICY','STORE_CATALOG','ACHIEVEMENTS','SEASON','MAP','DAILY_REWARD','AVATAR_UNLOCK','FEATURE_ROLLOUT')),
 payload jsonb NOT NULL,state text NOT NULL CHECK(state IN('DRAFT','SCHEDULED','ACTIVE','RETIRED')),scheduled_at timestamptz,activated_at timestamptz,retired_at timestamptz,
 validation_hash text NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(config_key,version));
CREATE UNIQUE INDEX uq_live_content_active ON live_content_config(config_key) WHERE state='ACTIVE';
ALTER TABLE season_definition ADD COLUMN IF NOT EXISTS season_name text;
ALTER TABLE season_definition ADD COLUMN IF NOT EXISTS story_concept text;
ALTER TABLE season_definition ADD COLUMN IF NOT EXISTS world_theme_key text;
ALTER TABLE season_definition ADD COLUMN IF NOT EXISTS map_theme_key text;
ALTER TABLE season_definition ADD COLUMN IF NOT EXISTS limited_cosmetic_set jsonb NOT NULL DEFAULT '[]';
ALTER TABLE season_definition ADD COLUMN IF NOT EXISTS profile_badge_key text;

CREATE TABLE frontend_semantic_event(
 event_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
 event_type text NOT NULL CHECK(event_type IN('LEVEL_UP','XP_GRANTED','COINS_GRANTED','COINS_SPENT','REWARD_CLAIMED','ACHIEVEMENT_UNLOCKED','ITEM_ACQUIRED','AVATAR_UNLOCKED','AVATAR_EQUIPPED','PROJECT_PROGRESS_CHANGED','GOAL_COMPLETED','SOURCE_READY','TEST_COMPLETED','QUIZ_COMPLETED','FLASHCARD_SESSION_COMPLETED','MISTAKE_RESOLVED','MEMORY_UPDATED','MAP_UNLOCKED','UNIT_REVEALED','UNIT_COMPLETED','SEASON_STARTED','SEASON_COMPLETED')),
 entity_id text,causation_id text,correlation_id text,payload jsonb NOT NULL DEFAULT '{}',occurred_at timestamptz NOT NULL DEFAULT now(),revision bigint NOT NULL,idempotency_key text NOT NULL,UNIQUE(account_id,idempotency_key));
CREATE INDEX idx_frontend_semantic_event_account ON frontend_semantic_event(account_id,occurred_at DESC,event_id DESC);

CREATE TABLE qualified_active_day(account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,local_date date NOT NULL,timezone_used text NOT NULL,source_event_id uuid REFERENCES activity_event(event_id) ON DELETE SET NULL,qualified_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(account_id,local_date));
ALTER TABLE progression_profile ADD COLUMN IF NOT EXISTS qualified_active_days int NOT NULL DEFAULT 0 CHECK(qualified_active_days>=0);
ALTER TABLE progression_profile ADD COLUMN IF NOT EXISTS effective_level smallint NOT NULL DEFAULT 1 CHECK(effective_level BETWEEN 1 AND 50);
ALTER TABLE progression_profile ADD COLUMN IF NOT EXISTS level_gate_version text NOT NULL DEFAULT 'long-term-level-gate-v1';
CREATE FUNCTION part3_max_level_for_days(p_days int) RETURNS smallint LANGUAGE sql IMMUTABLE AS $$
 SELECT CASE WHEN p_days>=90 THEN 50 ELSE GREATEST(1,LEAST(49,1+floor(GREATEST(p_days,0)::numeric*49/90)::int)) END::smallint $$;
CREATE FUNCTION part3_refresh_effective_level(p_account uuid) RETURNS void LANGUAGE plpgsql AS $$
DECLARE v_xp int;v_days int;v_eff int;BEGIN
 SELECT level,qualified_active_days INTO v_xp,v_days FROM progression_profile WHERE account_id=p_account FOR UPDATE;
 IF v_xp IS NULL THEN RETURN;END IF;v_eff:=LEAST(v_xp,part3_max_level_for_days(v_days));
 UPDATE progression_profile SET effective_level=v_eff,level_gate_version='long-term-level-gate-v1',updated_at=now() WHERE account_id=p_account AND effective_level IS DISTINCT FROM v_eff;
END $$;
CREATE FUNCTION part3_qualified_day_from_consistency() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF NEW.qualified THEN
 INSERT INTO qualified_active_day(account_id,local_date,timezone_used,source_event_id) VALUES(NEW.account_id,NEW.local_date,NEW.timezone_used,NEW.source_event_id) ON CONFLICT DO NOTHING;
 UPDATE progression_profile SET qualified_active_days=(SELECT count(*) FROM qualified_active_day q WHERE q.account_id=NEW.account_id) WHERE account_id=NEW.account_id;
 PERFORM part3_refresh_effective_level(NEW.account_id);END IF;RETURN NEW;END $$;
CREATE TRIGGER trg_part3_qualified_day_consistency AFTER INSERT OR UPDATE OF qualified ON consistency_history FOR EACH ROW EXECUTE FUNCTION part3_qualified_day_from_consistency();
INSERT INTO qualified_active_day(account_id,local_date,timezone_used,source_event_id) SELECT account_id,local_date,timezone_used,source_event_id FROM consistency_history WHERE qualified ON CONFLICT DO NOTHING;
UPDATE progression_profile p SET qualified_active_days=(SELECT count(*) FROM qualified_active_day q WHERE q.account_id=p.account_id);
DO $$DECLARE r record;BEGIN FOR r IN SELECT account_id FROM progression_profile LOOP PERFORM part3_refresh_effective_level(r.account_id);END LOOP;END$$;

CREATE TABLE map_unit_stage(
 map_definition_id text NOT NULL,map_version int NOT NULL,unit_id text NOT NULL,stage text NOT NULL CHECK(stage IN('MISSION','PRACTICE','CHALLENGE','FINAL_CHECK')),ordinal int NOT NULL CHECK(ordinal BETWEEN 1 AND 4),
 completion_criteria jsonb NOT NULL DEFAULT '{}',content_reference jsonb NOT NULL DEFAULT '{}',PRIMARY KEY(map_definition_id,map_version,unit_id,stage),UNIQUE(map_definition_id,map_version,unit_id,ordinal),
 FOREIGN KEY(unit_id,map_definition_id,map_version) REFERENCES map_unit(unit_id,map_definition_id,map_version) ON DELETE CASCADE);
CREATE TABLE map_checkpoint_reward(account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,completed_unit_checkpoint int NOT NULL CHECK(completed_unit_checkpoint>0),reward_tier text NOT NULL CHECK(reward_tier IN('NOOB','PRO')),avatar_id text NOT NULL REFERENCES avatar_catalog(avatar_id),granted_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(account_id,completed_unit_checkpoint));

ALTER TABLE avatar_catalog ADD COLUMN IF NOT EXISTS permanent_name text;
ALTER TABLE avatar_catalog ADD COLUMN IF NOT EXISTS rarity_order int NOT NULL DEFAULT 0;
ALTER TABLE avatar_catalog ADD COLUMN IF NOT EXISTS identity_metadata jsonb NOT NULL DEFAULT '{}';
ALTER TABLE avatar_catalog ADD COLUMN IF NOT EXISTS animation_capabilities jsonb NOT NULL DEFAULT '[]';
ALTER TABLE avatar_catalog ADD COLUMN IF NOT EXISTS behavior_capabilities jsonb NOT NULL DEFAULT '[]';
ALTER TABLE avatar_catalog ADD COLUMN IF NOT EXISTS preview_capabilities jsonb NOT NULL DEFAULT '["PREVIEWABLE"]';
UPDATE avatar_catalog SET permanent_name=CASE avatar_id WHEN 'avatar-noob-default' THEN 'Noob Default' WHEN 'avatar-pro-focus' THEN 'Pro Focus' WHEN 'avatar-elite-scholar' THEN 'Elite Scholar' WHEN 'avatar-super-builder' THEN 'Super Builder' WHEN 'avatar-ultra-orbit' THEN 'Ultra Orbit' WHEN 'avatar-max-core' THEN 'Max Core' WHEN 'avatar-hyperpro-nova' THEN 'HyperPro Nova' WHEN 'avatar-legendary-apex' THEN 'Legendary Apex' ELSE avatar_id END WHERE permanent_name IS NULL;
UPDATE avatar_catalog SET rarity_order=CASE tier WHEN 'NOOB' THEN 1 WHEN 'PRO' THEN 2 WHEN 'ELITE' THEN 3 WHEN 'SUPER' THEN 4 WHEN 'ULTRA' THEN 5 WHEN 'MAX' THEN 6 WHEN 'HYPERPRO' THEN 7 ELSE 8 END;
UPDATE avatar_catalog SET animation_capabilities=CASE WHEN tier='LEGENDARY' THEN '["ENTRANCE","IDLE","REACTION","CELEBRATION","PROFILE_INTERACTION","SPECIAL"]'::jsonb ELSE '["IDLE","REACTION","CELEBRATION"]'::jsonb END,
 behavior_capabilities='["LOOK_DIRECTION","TAP_REACTION"]',preview_capabilities='["PREVIEWABLE","ROTATABLE","PANNABLE","ZOOMABLE","INTERACTION_HOTSPOTS"]';
DO $$DECLARE r record;i int;v_id text;v_name text;v_anim jsonb;v_start int;BEGIN
 FOR r IN SELECT * FROM(VALUES('NOOB',40),('PRO',30),('ELITE',20),('SUPER',15),('ULTRA',12),('MAX',10),('HYPERPRO',5),('LEGENDARY',3))t(tier_name,target_count) LOOP
  SELECT count(*)+1 INTO v_start FROM avatar_catalog WHERE tier=r.tier_name;
  IF v_start<=r.target_count THEN FOR i IN v_start..r.target_count LOOP
   v_id:='avatar-'||lower(r.tier_name)||'-'||lpad(i::text,3,'0');v_name:=initcap(lower(r.tier_name))||' '||lpad(i::text,3,'0');
   v_anim:=CASE WHEN r.tier_name='LEGENDARY' THEN '["ENTRANCE","IDLE","REACTION","CELEBRATION","PROFILE_INTERACTION","SPECIAL"]'::jsonb ELSE '["IDLE","REACTION","CELEBRATION"]'::jsonb END;
   INSERT INTO inventory_catalog(item_id,item_type,catalog_version,unique_ownership,metadata,active) VALUES(v_id,'AVATAR','avatar-v2',true,jsonb_build_object('tier',r.tier_name,'permanentName',v_name),true) ON CONFLICT DO NOTHING;
   INSERT INTO avatar_catalog(avatar_id,asset_key,tier,unlock_rule,store_price,catalog_version,active,permanent_name,rarity_order,identity_metadata,animation_capabilities,behavior_capabilities,preview_capabilities)
   VALUES(v_id,'avatar/'||lower(r.tier_name)||'/'||lpad(i::text,3,'0'),r.tier_name,jsonb_build_object('type','CATALOG','tier',r.tier_name,'ordinal',i),NULL,'avatar-v2',true,v_name,
   CASE r.tier_name WHEN 'NOOB' THEN 1 WHEN 'PRO' THEN 2 WHEN 'ELITE' THEN 3 WHEN 'SUPER' THEN 4 WHEN 'ULTRA' THEN 5 WHEN 'MAX' THEN 6 WHEN 'HYPERPRO' THEN 7 ELSE 8 END,
   jsonb_build_object('identityKey',v_id),v_anim,'["LOOK_DIRECTION","TAP_REACTION"]','["PREVIEWABLE","ROTATABLE","PANNABLE","ZOOMABLE","INTERACTION_HOTSPOTS"]') ON CONFLICT DO NOTHING;
  END LOOP;END IF;
 END LOOP;END$$;
ALTER TABLE avatar_catalog ALTER COLUMN permanent_name SET NOT NULL;
CREATE UNIQUE INDEX uq_avatar_permanent_name ON avatar_catalog(permanent_name);

ALTER TABLE store_item ADD COLUMN IF NOT EXISTS store_category text NOT NULL DEFAULT 'AVATARS';
ALTER TABLE store_item ADD COLUMN IF NOT EXISTS preview_capabilities jsonb NOT NULL DEFAULT '[]';
ALTER TABLE store_item ADD COLUMN IF NOT EXISTS acquisition_rule jsonb NOT NULL DEFAULT '{"type":"EXACT_ITEM"}';
ALTER TABLE store_item ADD CONSTRAINT store_item_store_category_check CHECK(store_category IN('AVATARS','AVATAR_EFFECTS','FRAMES','PROFILE_BACKGROUNDS','PROFILE_THEMES','CHAT_ENVIRONMENTS','PROJECT_THEMES','MAP_COSMETICS','ANIMATIONS','EFFECTS','BADGES','NAMEPLATES','SOUND_PACKS','REACTION_PACKS'));
ALTER TABLE store_item ADD CONSTRAINT store_item_no_randomized_purchase CHECK(lower(acquisition_rule::text) NOT LIKE '%loot%' AND lower(acquisition_rule::text) NOT LIKE '%roulette%' AND lower(acquisition_rule::text) NOT LIKE '%mystery%' AND lower(acquisition_rule::text) NOT LIKE '%random%');

INSERT INTO project_template_definition(template_id,version,title_key,default_learning_mode,module_seed,goal_suggestions,source_policy,analytics_config,state,activated_at) VALUES
('LANGUAGE_EXAM',1,'project.template.language_exam','EXAM_PREP','["CHAT","SOURCES","TESTS","QUIZZES","PRACTICE","FLASHCARDS","MISTAKES"]','["Set target level","Complete baseline assessment"]','{"preferGroundedSources":true}','{"skillBreakdown":true}','ACTIVE',now()),
('SCHOOL_SUBJECT',1,'project.template.school_subject','TUTOR','["SOURCES","NOTES","TESTS","QUIZZES","PRACTICE","FLASHCARDS","MISTAKES"]','["Add syllabus or textbook","Complete baseline test"]','{"preferGroundedSources":true}','{"topicBreakdown":true}','ACTIVE',now()),
('RESEARCH',1,'project.template.research','RESEARCH','["SOURCES","CHAT","NOTES","GOALS"]','["Define research question","Collect primary sources"]','{"preferPrimarySources":true}','{"citationCoverage":true}','ACTIVE',now()),
('EXAM_PREPARATION',1,'project.template.exam_prep','EXAM_PREP','["TESTS","QUIZZES","MISTAKES","PRACTICE","FLASHCARDS"]','["Set exam date","Complete diagnostic test"]','{}','{"progressComparison":true}','ACTIVE',now()),
('PERSONAL_SKILL',1,'project.template.personal_skill','PRACTICE_COACH','["GOALS","PRACTICE","NOTES","CHAT"]','["Define observable skill outcome"]','{}','{"practiceConsistency":true}','ACTIVE',now()),
('COMPETITION',1,'project.template.competition','EXAM_PREP','["GOALS","TESTS","PRACTICE","MISTAKES"]','["Define competition target","Run baseline assessment"]','{}','{"timedPerformance":true}','ACTIVE',now()),
('CUSTOM',1,'project.template.custom','DEFAULT','["CHAT","SOURCES","NOTES","GOALS"]','[]','{}','{}','ACTIVE',now());

UPDATE map_definition SET active=false WHERE map_definition_id='foundation-map' AND version<2;
INSERT INTO map_definition(map_definition_id,version,semantic_key,generation_version,metadata,active) VALUES('foundation-map',2,'FOUNDATION_PERSONAL_MAP','map-gen-v2','{"unitCount":10,"personalizationInputs":["MEMORY","MISTAKES","INTERESTS","PROJECTS","PERFORMANCE","FRICTION","GOALS"],"futureUnitsHidden":true}',true);
INSERT INTO map_unit(unit_id,map_definition_id,map_version,ordinal,semantic_key,title_key,learning_metadata,completion_criteria,reward_definition,content_reference)
SELECT 'foundation-v2-u'||i,'foundation-map',2,i,'FOUNDATION_'||i,'map.unit.'||lpad(i::text,2,'0'),jsonb_build_object('arcVersion',1),'{"finalCheckPass":true}',jsonb_build_object('xp',70+i*10,'coins',10+i*3),'{}' FROM generate_series(1,10)i;
INSERT INTO map_unit_dependency(map_definition_id,map_version,unit_id,prerequisite_unit_id) SELECT 'foundation-map',2,'foundation-v2-u'||i,'foundation-v2-u'||(i-1) FROM generate_series(2,10)i;
INSERT INTO map_unit_stage(map_definition_id,map_version,unit_id,stage,ordinal,completion_criteria,content_reference)
SELECT 'foundation-map',2,'foundation-v2-u'||u,s.stage,s.ord,CASE s.stage WHEN 'FINAL_CHECK' THEN '{"deterministicPassRequired":true}'::jsonb ELSE '{"completed":true}'::jsonb END,jsonb_build_object('semanticKey','foundation-v2-u'||u||':'||lower(s.stage))
FROM generate_series(1,10)u CROSS JOIN(VALUES('MISSION',1),('PRACTICE',2),('CHALLENGE',3),('FINAL_CHECK',4))s(stage,ord);

CREATE FUNCTION part3_map_checkpoint_reward() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE n int;t text;a text;BEGIN
 IF NEW.state IN('COMPLETED','REWARD_GRANTED') AND OLD.state NOT IN('COMPLETED','REWARD_GRANTED') THEN
  SELECT count(*) INTO n FROM map_unit_progress WHERE account_id=NEW.account_id AND state IN('COMPLETED','REWARD_GRANTED');
  IF n>0 AND n%20=0 THEN t:='PRO';ELSIF n>0 AND n%5=0 THEN t:='NOOB';ELSE RETURN NEW;END IF;
  IF EXISTS(SELECT 1 FROM map_checkpoint_reward WHERE account_id=NEW.account_id AND completed_unit_checkpoint=n) THEN RETURN NEW;END IF;
  SELECT x.avatar_id INTO a FROM avatar_catalog x LEFT JOIN inventory_ownership o ON o.account_id=NEW.account_id AND o.item_id=x.avatar_id WHERE x.tier=t AND x.active AND o.item_id IS NULL ORDER BY x.rarity_order,x.avatar_id LIMIT 1;
  IF a IS NULL THEN RETURN NEW;END IF;
  INSERT INTO map_checkpoint_reward VALUES(NEW.account_id,n,t,a,now()) ON CONFLICT DO NOTHING;
  INSERT INTO inventory_ownership(account_id,item_id,ownership_source,metadata) VALUES(NEW.account_id,a,'MAP_UNIT',jsonb_build_object('completedUnitCheckpoint',n,'tier',t)) ON CONFLICT DO NOTHING;
  INSERT INTO game_state_event(account_id,event_type,causation_id,correlation_id,resulting_revision,payload,idempotency_key) VALUES(NEW.account_id,'AVATAR_UNLOCKED','map-checkpoint:'||n,'map-checkpoint:'||n,NEW.revision,jsonb_build_object('avatarId',a,'tier',t,'completedUnits',n),'part3-map-avatar:'||n) ON CONFLICT(account_id,idempotency_key) DO NOTHING;
 END IF;RETURN NEW;END$$;
CREATE TRIGGER trg_part3_map_checkpoint_reward AFTER UPDATE OF state ON map_unit_progress FOR EACH ROW EXECUTE FUNCTION part3_map_checkpoint_reward();

CREATE FUNCTION part3_frontend_event_from_game() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF NEW.event_type IN('LEVEL_UP','XP_GRANTED','COINS_GRANTED','COINS_SPENT','ACHIEVEMENT_UNLOCKED','ITEM_ACQUIRED','AVATAR_UNLOCKED','AVATAR_EQUIPPED','MAP_UNLOCKED','UNIT_COMPLETED','SEASON_STARTED','SEASON_COMPLETED') THEN
 INSERT INTO frontend_semantic_event(account_id,event_type,entity_id,causation_id,correlation_id,payload,revision,idempotency_key) VALUES(NEW.account_id,NEW.event_type,COALESCE(NEW.causation_id,NEW.correlation_id),NEW.causation_id,NEW.correlation_id,NEW.payload,NEW.resulting_revision,'game:'||NEW.event_id) ON CONFLICT(account_id,idempotency_key) DO NOTHING;
 END IF;RETURN NEW;END$$;
CREATE TRIGGER trg_part3_frontend_event_from_game AFTER INSERT ON game_state_event FOR EACH ROW EXECUTE FUNCTION part3_frontend_event_from_game();
CREATE FUNCTION part3_frontend_event_from_activity() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE t text;BEGIN t:=CASE NEW.event_type WHEN 'GOAL_COMPLETED' THEN 'GOAL_COMPLETED' WHEN 'TEST_COMPLETED' THEN 'TEST_COMPLETED' WHEN 'QUIZ_COMPLETED' THEN 'QUIZ_COMPLETED' WHEN 'FLASHCARD_REVIEW_COMPLETED' THEN 'FLASHCARD_SESSION_COMPLETED' WHEN 'MISTAKE_RESOLVED' THEN 'MISTAKE_RESOLVED' ELSE NULL END;
 IF t IS NOT NULL THEN INSERT INTO frontend_semantic_event(account_id,event_type,entity_id,causation_id,correlation_id,payload,revision,idempotency_key) VALUES(NEW.account_id,t,NEW.object_id,NEW.event_id::text,NEW.project_id::text,jsonb_build_object('activityEventId',NEW.event_id,'projectId',NEW.project_id,'objectId',NEW.object_id),1,'activity:'||NEW.event_id) ON CONFLICT(account_id,idempotency_key) DO NOTHING;END IF;RETURN NEW;END$$;
CREATE TRIGGER trg_part3_frontend_event_from_activity AFTER INSERT ON activity_event FOR EACH ROW EXECUTE FUNCTION part3_frontend_event_from_activity();

CREATE INDEX idx_project_home_priority ON project(account_id,status,priority DESC,last_active_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_goal_home_active ON goal(account_id,project_id,status,priority DESC,target_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_note_global_search ON note(account_id,updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_assessment_project_recent ON assessment(account_id,project_id,updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_mistake_account_recent ON mistake(account_id,last_seen_at DESC) WHERE deleted_at IS NULL;
