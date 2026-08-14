-- Veltrix Hom vNext Part 2: server-authoritative game account foundation.
-- Additive migration from the frozen Part 1 schema. Historical Part 1 data is preserved.

ALTER TABLE activity_event ADD COLUMN IF NOT EXISTS evidence jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE activity_event ADD COLUMN IF NOT EXISTS revision bigint NOT NULL DEFAULT 1;

CREATE TABLE reward_policy_version (
  version text PRIMARY KEY,
  active boolean NOT NULL DEFAULT false,
  config jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  retired_at timestamptz
);
CREATE UNIQUE INDEX uq_reward_policy_active ON reward_policy_version(active) WHERE active;

CREATE TABLE progression_profile (
  account_id uuid PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
  lifetime_xp bigint NOT NULL DEFAULT 0 CHECK(lifetime_xp >= 0),
  level smallint NOT NULL DEFAULT 1 CHECK(level BETWEEN 1 AND 50),
  level_curve_version text NOT NULL,
  reward_policy_version text NOT NULL REFERENCES reward_policy_version(version),
  revision bigint NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE xp_ledger (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  amount bigint NOT NULL CHECK(amount <> 0), entry_type text NOT NULL CHECK(entry_type IN ('GRANT','REVERSAL','ADJUSTMENT')),
  reward_source text NOT NULL, source_event_id uuid REFERENCES activity_event(event_id) ON DELETE SET NULL,
  idempotency_key text NOT NULL, policy_version text NOT NULL REFERENCES reward_policy_version(version), curve_version text NOT NULL,
  reason text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(account_id,idempotency_key)
);
CREATE INDEX idx_xp_ledger_account_time ON xp_ledger(account_id,created_at DESC,id DESC);
CREATE INDEX idx_xp_ledger_source_event ON xp_ledger(account_id,source_event_id) WHERE source_event_id IS NOT NULL;

CREATE TABLE coin_account_projection (
  account_id uuid PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
  balance bigint NOT NULL DEFAULT 0 CHECK(balance >= 0), total_earned bigint NOT NULL DEFAULT 0 CHECK(total_earned >= 0),
  total_spent bigint NOT NULL DEFAULT 0 CHECK(total_spent >= 0), revision bigint NOT NULL DEFAULT 1, updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE coin_ledger (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  amount bigint NOT NULL CHECK(amount <> 0), entry_type text NOT NULL CHECK(entry_type IN ('GRANT','SPEND','REFUND','REVERSAL','ADJUSTMENT')),
  reward_source text NOT NULL, source_event_id uuid REFERENCES activity_event(event_id) ON DELETE SET NULL, purchase_id uuid,
  idempotency_key text NOT NULL, policy_version text NOT NULL REFERENCES reward_policy_version(version), reason text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(account_id,idempotency_key)
);
CREATE INDEX idx_coin_ledger_account_time ON coin_ledger(account_id,created_at DESC,id DESC);

CREATE TABLE reward_grant (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  source_event_id uuid REFERENCES activity_event(event_id) ON DELETE SET NULL, reward_key text NOT NULL,
  xp_amount bigint NOT NULL DEFAULT 0 CHECK(xp_amount >= 0), coin_amount bigint NOT NULL DEFAULT 0 CHECK(coin_amount >= 0),
  policy_version text NOT NULL REFERENCES reward_policy_version(version), decision_reason text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(account_id,source_event_id,reward_key)
);
CREATE TABLE reward_decision_log (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  source_event_id uuid REFERENCES activity_event(event_id) ON DELETE SET NULL, event_type text NOT NULL, eligible boolean NOT NULL,
  decision_code text NOT NULL, policy_version text NOT NULL REFERENCES reward_policy_version(version), calculated_xp bigint NOT NULL DEFAULT 0,
  calculated_coins bigint NOT NULL DEFAULT 0, details jsonb NOT NULL DEFAULT '{}'::jsonb, created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(account_id,source_event_id,policy_version)
);
CREATE INDEX idx_reward_decision_account_time ON reward_decision_log(account_id,created_at DESC);

CREATE TABLE activity_reward_queue (
  activity_event_id uuid PRIMARY KEY REFERENCES activity_event(event_id) ON DELETE CASCADE,
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  status text NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PROCESSING','DONE','RETRY','REJECTED')),
  attempt_count integer NOT NULL DEFAULT 0 CHECK(attempt_count >= 0), available_at timestamptz NOT NULL DEFAULT now(), locked_at timestamptz,
  last_error_code text, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_activity_reward_queue_ready ON activity_reward_queue(status,available_at,created_at) WHERE status IN ('PENDING','RETRY');
CREATE OR REPLACE FUNCTION enqueue_part2_reward_event() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.meaningful THEN
    INSERT INTO activity_reward_queue(activity_event_id,account_id) VALUES (NEW.event_id,NEW.account_id)
    ON CONFLICT(activity_event_id) DO NOTHING;
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_activity_reward_enqueue ON activity_event;
CREATE TRIGGER trg_activity_reward_enqueue AFTER INSERT ON activity_event FOR EACH ROW EXECUTE FUNCTION enqueue_part2_reward_event();

CREATE TABLE daily_activity_state (
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE, local_date date NOT NULL, timezone_used text NOT NULL,
  eligible_event_count integer NOT NULL DEFAULT 0 CHECK(eligible_event_count >= 0), xp_granted bigint NOT NULL DEFAULT 0 CHECK(xp_granted >= 0),
  coins_granted bigint NOT NULL DEFAULT 0 CHECK(coins_granted >= 0), daily_bonus_granted boolean NOT NULL DEFAULT false,
  first_eligible_at timestamptz, last_eligible_at timestamptz, revision bigint NOT NULL DEFAULT 1, PRIMARY KEY(account_id,local_date)
);
CREATE TABLE consistency_state (
  account_id uuid PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE, current_consistency integer NOT NULL DEFAULT 0 CHECK(current_consistency >= 0),
  longest_consistency integer NOT NULL DEFAULT 0 CHECK(longest_consistency >= 0), last_eligible_local_date date,
  timezone_used text NOT NULL DEFAULT 'UTC', revision bigint NOT NULL DEFAULT 1, updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE consistency_history (
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE, local_date date NOT NULL, qualified boolean NOT NULL,
  timezone_used text NOT NULL, source_event_id uuid REFERENCES activity_event(event_id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(account_id,local_date)
);

CREATE TABLE achievement_definition (
  achievement_id text NOT NULL, version integer NOT NULL, category text NOT NULL, criteria jsonb NOT NULL, progress_model text NOT NULL,
  hidden boolean NOT NULL DEFAULT false, repeatable boolean NOT NULL DEFAULT false, reward_definition jsonb NOT NULL DEFAULT '{}'::jsonb,
  season_scope text, active boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(achievement_id,version)
);
CREATE TABLE achievement_progress (
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE, achievement_id text NOT NULL, definition_version integer NOT NULL,
  progress bigint NOT NULL DEFAULT 0 CHECK(progress >= 0), state text NOT NULL DEFAULT 'LOCKED' CHECK(state IN ('LOCKED','IN_PROGRESS','UNLOCKED','CLAIMED')),
  unlocked_at timestamptz, reward_grant_id uuid REFERENCES reward_grant(id) ON DELETE SET NULL, revision bigint NOT NULL DEFAULT 1,
  updated_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(account_id,achievement_id,definition_version),
  FOREIGN KEY(achievement_id,definition_version) REFERENCES achievement_definition(achievement_id,version)
);

CREATE TABLE inventory_catalog (
  item_id text PRIMARY KEY, item_type text NOT NULL, catalog_version text NOT NULL, unique_ownership boolean NOT NULL DEFAULT true,
  season_scope text, metadata jsonb NOT NULL DEFAULT '{}'::jsonb, active boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE inventory_ownership (
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE, item_id text NOT NULL REFERENCES inventory_catalog(item_id),
  quantity bigint NOT NULL DEFAULT 1 CHECK(quantity > 0),
  ownership_source text NOT NULL CHECK(ownership_source IN ('DEFAULT','LEVEL_UNLOCK','ACHIEVEMENT','MAP_UNIT','SEASON_REWARD','STORE_PURCHASE','ADMIN_MIGRATION')),
  acquired_at timestamptz NOT NULL DEFAULT now(), season_scope text, metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  revision bigint NOT NULL DEFAULT 1, PRIMARY KEY(account_id,item_id)
);
CREATE INDEX idx_inventory_account_type ON inventory_ownership(account_id,acquired_at DESC);
CREATE TABLE avatar_catalog (
  avatar_id text PRIMARY KEY REFERENCES inventory_catalog(item_id), asset_key text NOT NULL UNIQUE,
  tier text NOT NULL CHECK(tier IN ('NOOB','PRO','ELITE','SUPER','ULTRA','MAX','HYPERPRO','LEGENDARY')),
  unlock_rule jsonb NOT NULL DEFAULT '{}'::jsonb, store_price bigint CHECK(store_price IS NULL OR store_price >= 0),
  catalog_version text NOT NULL, active boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE equipped_avatar (
  account_id uuid PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE, avatar_id text NOT NULL REFERENCES avatar_catalog(avatar_id),
  revision bigint NOT NULL DEFAULT 1, updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE store_catalog (catalog_version text PRIMARY KEY, active boolean NOT NULL DEFAULT false, effective_at timestamptz NOT NULL DEFAULT now(), retired_at timestamptz);
CREATE UNIQUE INDEX uq_store_catalog_active ON store_catalog(active) WHERE active;
CREATE TABLE store_item (
  item_id text NOT NULL REFERENCES inventory_catalog(item_id), catalog_version text NOT NULL REFERENCES store_catalog(catalog_version),
  price_coins bigint NOT NULL CHECK(price_coins >= 0), availability jsonb NOT NULL DEFAULT '{}'::jsonb,
  requirements jsonb NOT NULL DEFAULT '{}'::jsonb, active boolean NOT NULL DEFAULT true, PRIMARY KEY(item_id,catalog_version)
);
CREATE TABLE store_purchase (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  item_id text NOT NULL REFERENCES inventory_catalog(item_id), catalog_version text NOT NULL REFERENCES store_catalog(catalog_version),
  authoritative_price bigint NOT NULL CHECK(authoritative_price >= 0), currency text NOT NULL DEFAULT 'COINS' CHECK(currency='COINS'),
  idempotency_key text NOT NULL, status text NOT NULL CHECK(status IN ('COMMITTED','REFUNDED','REVERSED')),
  coin_ledger_id uuid REFERENCES coin_ledger(id) ON DELETE SET NULL, entitlement_revision bigint, purchased_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(), UNIQUE(account_id,idempotency_key)
);
CREATE INDEX idx_store_purchase_account_time ON store_purchase(account_id,purchased_at DESC);
ALTER TABLE coin_ledger ADD CONSTRAINT fk_coin_ledger_purchase FOREIGN KEY(purchase_id) REFERENCES store_purchase(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE season_definition (
  season_id text NOT NULL, version integer NOT NULL, start_at timestamptz NOT NULL, end_at timestamptz NOT NULL CHECK(end_at > start_at),
  state text NOT NULL CHECK(state IN ('PLANNED','ACTIVE','CLOSED','ARCHIVED')), map_definition_id text,
  season_reward_catalog jsonb NOT NULL DEFAULT '{}'::jsonb, achievement_refs jsonb NOT NULL DEFAULT '[]'::jsonb,
  identity_metadata jsonb NOT NULL DEFAULT '{}'::jsonb, created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(season_id,version)
);
CREATE INDEX idx_season_time ON season_definition(start_at,end_at,state);
CREATE TABLE map_definition (
  map_definition_id text NOT NULL, version integer NOT NULL, season_id text, semantic_key text NOT NULL, generation_version text NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb, active boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(map_definition_id,version)
);
CREATE TABLE map_unit (
  unit_id text NOT NULL, map_definition_id text NOT NULL, map_version integer NOT NULL, ordinal integer NOT NULL CHECK(ordinal > 0),
  semantic_key text NOT NULL, title_key text NOT NULL, learning_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  completion_criteria jsonb NOT NULL, reward_definition jsonb NOT NULL DEFAULT '{}'::jsonb, content_reference jsonb NOT NULL DEFAULT '{}'::jsonb,
  season_scope text, PRIMARY KEY(unit_id,map_definition_id,map_version), UNIQUE(map_definition_id,map_version,ordinal),
  FOREIGN KEY(map_definition_id,map_version) REFERENCES map_definition(map_definition_id,version) ON DELETE CASCADE
);
CREATE TABLE map_unit_dependency (
  map_definition_id text NOT NULL, map_version integer NOT NULL, unit_id text NOT NULL, prerequisite_unit_id text NOT NULL,
  PRIMARY KEY(map_definition_id,map_version,unit_id,prerequisite_unit_id),
  FOREIGN KEY(unit_id,map_definition_id,map_version) REFERENCES map_unit(unit_id,map_definition_id,map_version) ON DELETE CASCADE,
  FOREIGN KEY(prerequisite_unit_id,map_definition_id,map_version) REFERENCES map_unit(unit_id,map_definition_id,map_version) ON DELETE CASCADE,
  CHECK(unit_id <> prerequisite_unit_id)
);
CREATE TABLE personal_map (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  map_definition_id text NOT NULL, map_version integer NOT NULL, season_id text,
  state text NOT NULL CHECK(state IN ('LOCKED','ELIGIBLE','GENERATING','ACTIVE','COMPLETED','FAILED_RETRYABLE')),
  generation_provider text NOT NULL DEFAULT 'DETERMINISTIC', generation_version text NOT NULL, revision bigint NOT NULL DEFAULT 1,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz,
  UNIQUE NULLS NOT DISTINCT (account_id,map_definition_id,map_version,season_id),
  FOREIGN KEY(map_definition_id,map_version) REFERENCES map_definition(map_definition_id,version)
);
CREATE INDEX idx_personal_map_account_state ON personal_map(account_id,state,updated_at DESC);
CREATE TABLE map_generation_record (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  personal_map_id uuid NOT NULL REFERENCES personal_map(id) ON DELETE CASCADE, provider text NOT NULL, generation_version text NOT NULL,
  input_summary jsonb NOT NULL DEFAULT '{}'::jsonb, output_payload jsonb, validation_status text NOT NULL,
  fallback_used boolean NOT NULL DEFAULT false, error_code text, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE map_unit_progress (
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE, personal_map_id uuid NOT NULL REFERENCES personal_map(id) ON DELETE CASCADE,
  unit_id text NOT NULL, state text NOT NULL CHECK(state IN ('HIDDEN','LOCKED','AVAILABLE','IN_PROGRESS','COMPLETED','REWARD_GRANTED')),
  progress bigint NOT NULL DEFAULT 0 CHECK(progress >= 0), required_progress bigint NOT NULL DEFAULT 1 CHECK(required_progress > 0),
  started_at timestamptz, completed_at timestamptz, reward_grant_id uuid REFERENCES reward_grant(id) ON DELETE SET NULL,
  revision bigint NOT NULL DEFAULT 1, updated_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(account_id,personal_map_id,unit_id)
);
CREATE INDEX idx_map_unit_progress_map_state ON map_unit_progress(personal_map_id,state,unit_id);
CREATE TABLE season_progress (
  account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE, season_id text NOT NULL, season_version integer NOT NULL,
  units_completed integer NOT NULL DEFAULT 0 CHECK(units_completed >= 0), achievements_unlocked integer NOT NULL DEFAULT 0 CHECK(achievements_unlocked >= 0),
  xp_earned bigint NOT NULL DEFAULT 0 CHECK(xp_earned >= 0), coins_earned bigint NOT NULL DEFAULT 0 CHECK(coins_earned >= 0),
  state text NOT NULL DEFAULT 'ACTIVE' CHECK(state IN ('ACTIVE','CLOSED','ARCHIVED')), revision bigint NOT NULL DEFAULT 1,
  updated_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(account_id,season_id,season_version),
  FOREIGN KEY(season_id,season_version) REFERENCES season_definition(season_id,version)
);
CREATE TABLE gaming_statistics (
  account_id uuid PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE, meaningful_activities bigint NOT NULL DEFAULT 0 CHECK(meaningful_activities >= 0),
  xp_earned bigint NOT NULL DEFAULT 0 CHECK(xp_earned >= 0), coins_earned bigint NOT NULL DEFAULT 0 CHECK(coins_earned >= 0),
  coins_spent bigint NOT NULL DEFAULT 0 CHECK(coins_spent >= 0), achievements_unlocked bigint NOT NULL DEFAULT 0 CHECK(achievements_unlocked >= 0),
  units_completed bigint NOT NULL DEFAULT 0 CHECK(units_completed >= 0), maps_completed bigint NOT NULL DEFAULT 0 CHECK(maps_completed >= 0),
  seasons_participated bigint NOT NULL DEFAULT 0 CHECK(seasons_participated >= 0), revision bigint NOT NULL DEFAULT 1, updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE game_state_event (
  event_id uuid PRIMARY KEY DEFAULT gen_random_uuid(), account_id uuid NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  event_type text NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now(), causation_id text, correlation_id text,
  source_event_id uuid REFERENCES activity_event(event_id) ON DELETE SET NULL, resulting_revision bigint NOT NULL,
  payload_schema_version integer NOT NULL DEFAULT 1, payload jsonb NOT NULL DEFAULT '{}'::jsonb, idempotency_key text NOT NULL,
  UNIQUE(account_id,idempotency_key)
);
CREATE INDEX idx_game_state_event_account_time ON game_state_event(account_id,occurred_at DESC,event_id DESC);
CREATE TABLE season_rollover_execution (
  season_id text NOT NULL, season_version integer NOT NULL, transition text NOT NULL, executed_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(season_id,season_version,transition), FOREIGN KEY(season_id,season_version) REFERENCES season_definition(season_id,version)
);

INSERT INTO reward_policy_version(version,active,config) VALUES ('reward-v1',true,'{"dailyXpHardCap":450,"dailyCoinHardCap":90,"dailyBonusXp":20,"dailyBonusCoins":5,"timezoneChangeSafetyHours":20}'::jsonb) ON CONFLICT(version) DO NOTHING;
INSERT INTO store_catalog(catalog_version,active) VALUES ('store-v1',true) ON CONFLICT(catalog_version) DO NOTHING;
INSERT INTO inventory_catalog(item_id,item_type,catalog_version,unique_ownership,metadata) VALUES
 ('avatar-noob-default','AVATAR','avatar-v1',true,'{"tier":"NOOB"}'),('avatar-pro-focus','AVATAR','avatar-v1',true,'{"tier":"PRO"}'),
 ('avatar-elite-scholar','AVATAR','avatar-v1',true,'{"tier":"ELITE"}'),('avatar-super-builder','AVATAR','avatar-v1',true,'{"tier":"SUPER"}'),
 ('avatar-ultra-orbit','AVATAR','avatar-v1',true,'{"tier":"ULTRA"}'),('avatar-max-core','AVATAR','avatar-v1',true,'{"tier":"MAX"}'),
 ('avatar-hyperpro-nova','AVATAR','avatar-v1',true,'{"tier":"HYPERPRO"}'),('avatar-legendary-apex','AVATAR','avatar-v1',true,'{"tier":"LEGENDARY"}')
ON CONFLICT(item_id) DO NOTHING;
INSERT INTO avatar_catalog(avatar_id,asset_key,tier,unlock_rule,store_price,catalog_version) VALUES
 ('avatar-noob-default','avatar/noob/default','NOOB','{"type":"DEFAULT"}',NULL,'avatar-v1'),
 ('avatar-pro-focus','avatar/pro/focus','PRO','{"type":"LEVEL","level":5}',350,'avatar-v1'),
 ('avatar-elite-scholar','avatar/elite/scholar','ELITE','{"type":"LEVEL","level":10}',700,'avatar-v1'),
 ('avatar-super-builder','avatar/super/builder','SUPER','{"type":"ACHIEVEMENT","achievementId":"project-builder-1"}',950,'avatar-v1'),
 ('avatar-ultra-orbit','avatar/ultra/orbit','ULTRA','{"type":"LEVEL","level":20}',1500,'avatar-v1'),
 ('avatar-max-core','avatar/max/core','MAX','{"type":"MAP_UNIT","unitId":"foundation-u4"}',2200,'avatar-v1'),
 ('avatar-hyperpro-nova','avatar/hyperpro/nova','HYPERPRO','{"type":"LEVEL","level":35}',3200,'avatar-v1'),
 ('avatar-legendary-apex','avatar/legendary/apex','LEGENDARY','{"type":"LEVEL","level":50}',5000,'avatar-v1')
ON CONFLICT(avatar_id) DO NOTHING;
INSERT INTO store_item(item_id,catalog_version,price_coins,requirements) VALUES
 ('avatar-pro-focus','store-v1',350,'{"minLevel":5}'),('avatar-elite-scholar','store-v1',700,'{"minLevel":10}'),
 ('avatar-ultra-orbit','store-v1',1500,'{"minLevel":20}'),('avatar-hyperpro-nova','store-v1',3200,'{"minLevel":35}')
ON CONFLICT(item_id,catalog_version) DO NOTHING;
INSERT INTO achievement_definition(achievement_id,version,category,criteria,progress_model,reward_definition) VALUES
 ('first-meaningful-step',1,'LEARNING','{"eventCount":{"meaningful":1}}','COUNT','{"xp":50,"coins":10}'),
 ('project-builder-1',1,'PROJECTS','{"eventType":"GOAL_COMPLETED","count":5}','COUNT','{"xp":120,"coins":30,"itemId":"avatar-super-builder"}'),
 ('mistake-master-1',1,'PRACTICE','{"eventType":"MISTAKE_RESOLVED","count":10}','COUNT','{"xp":180,"coins":40}'),
 ('knowledge-curator-1',1,'KNOWLEDGE','{"eventType":"SOURCE_ADDED","count":8}','COUNT','{"xp":120,"coins":25}'),
 ('map-pathfinder-1',1,'MAP','{"unitsCompleted":3}','COUNT','{"xp":150,"coins":35}')
ON CONFLICT(achievement_id,version) DO NOTHING;
INSERT INTO map_definition(map_definition_id,version,semantic_key,generation_version,metadata) VALUES ('foundation-map',1,'FOUNDATION_PERSONAL_MAP','map-gen-v1','{"providerFallback":"DETERMINISTIC"}') ON CONFLICT(map_definition_id,version) DO NOTHING;
INSERT INTO map_unit(unit_id,map_definition_id,map_version,ordinal,semantic_key,title_key,completion_criteria,reward_definition) VALUES
 ('foundation-u1','foundation-map',1,1,'START','map.unit.start','{"eventTypes":["PROJECT_CREATED","NOTE_CREATED"],"count":1}','{"xp":80,"coins":15}'),
 ('foundation-u2','foundation-map',1,2,'LEARN','map.unit.learn','{"eventTypes":["QUIZ_COMPLETED","TEST_COMPLETED"],"count":1}','{"xp":100,"coins":20}'),
 ('foundation-u3','foundation-map',1,3,'IMPROVE','map.unit.improve','{"eventTypes":["PRACTICE_COMPLETED","MISTAKE_RESOLVED"],"count":2}','{"xp":120,"coins":25}'),
 ('foundation-u4','foundation-map',1,4,'KNOWLEDGE','map.unit.knowledge','{"eventTypes":["SOURCE_ADDED","FLASHCARD_REVIEW_COMPLETED"],"count":3}','{"xp":160,"coins":35,"itemId":"avatar-max-core"}'),
 ('foundation-u5','foundation-map',1,5,'SYNTHESIZE','map.unit.synthesize','{"eventTypes":["MEANINGFUL_CHAT_SESSION","GOAL_COMPLETED"],"count":3}','{"xp":220,"coins":50}')
ON CONFLICT(unit_id,map_definition_id,map_version) DO NOTHING;
INSERT INTO map_unit_dependency(map_definition_id,map_version,unit_id,prerequisite_unit_id) VALUES
 ('foundation-map',1,'foundation-u2','foundation-u1'),('foundation-map',1,'foundation-u3','foundation-u2'),
 ('foundation-map',1,'foundation-u4','foundation-u3'),('foundation-map',1,'foundation-u5','foundation-u4') ON CONFLICT DO NOTHING;
INSERT INTO season_definition(season_id,version,start_at,end_at,state,map_definition_id,identity_metadata) VALUES
 ('foundation-2026',1,'2026-01-01T00:00:00Z','2027-01-01T00:00:00Z','ACTIVE','foundation-map','{"semanticKey":"FOUNDATION_2026"}') ON CONFLICT(season_id,version) DO NOTHING;
