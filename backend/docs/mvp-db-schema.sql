-- MVP DB Schema (PostgreSQL 15+)
-- Domain: profile + products + rules + recommendation simulation

create extension if not exists "pgcrypto";

-- =========================
-- 1) User Profile
-- =========================
create table if not exists user_profile (
  id uuid primary key default gen_random_uuid(),
  external_user_key varchar(100) unique,
  age smallint not null check (age between 19 and 100),
  monthly_income integer not null check (monthly_income >= 0),
  monthly_card_spend integer not null check (monthly_card_spend >= 0),
  priority varchar(20) not null check (priority in ('CASHBACK','SAVINGS','TRAVEL','STARTER')),
  salary_transfer boolean not null,
  travel_level varchar(20) not null check (travel_level in ('NONE','SOMETIMES','OFTEN')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists user_profile_category (
  id bigserial primary key,
  profile_id uuid not null references user_profile(id) on delete cascade,
  category_code varchar(30) not null,
  created_at timestamptz not null default now(),
  unique(profile_id, category_code)
);

create index if not exists idx_profile_category_profile on user_profile_category(profile_id);

-- =========================
-- 2) Products
-- =========================
create table if not exists card_product (
  id uuid primary key default gen_random_uuid(),
  provider_name varchar(60) not null,
  product_name varchar(80) not null,
  annual_fee integer not null check (annual_fee >= 0),
  summary text not null,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists card_product_tag (
  id bigserial primary key,
  card_id uuid not null references card_product(id) on delete cascade,
  tag_code varchar(30) not null,
  unique(card_id, tag_code)
);

create table if not exists card_product_category (
  id bigserial primary key,
  card_id uuid not null references card_product(id) on delete cascade,
  category_code varchar(30) not null,
  unique(card_id, category_code)
);

create index if not exists idx_card_active on card_product(is_active);
create index if not exists idx_card_tag_card on card_product_tag(card_id);
create index if not exists idx_card_cat_card on card_product_category(card_id);

create table if not exists account_product (
  id uuid primary key default gen_random_uuid(),
  provider_name varchar(60) not null,
  product_name varchar(80) not null,
  account_type varchar(20) not null check (account_type in ('DEMAND','SAVINGS','FX')),
  summary text not null,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists account_product_tag (
  id bigserial primary key,
  account_id uuid not null references account_product(id) on delete cascade,
  tag_code varchar(30) not null,
  unique(account_id, tag_code)
);

create index if not exists idx_account_active on account_product(is_active);
create index if not exists idx_account_tag_account on account_product_tag(account_id);

-- =========================
-- 3) Rule Snapshot (for reproducible simulation)
-- =========================
create table if not exists rule_snapshot (
  id uuid primary key default gen_random_uuid(),
  rule_version varchar(40) not null,
  payload_json jsonb not null,
  created_at timestamptz not null default now(),
  unique(rule_version)
);

-- =========================
-- 4) Recommendation Simulation Result
-- =========================
create table if not exists recommendation_run (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references user_profile(id) on delete cascade,
  rule_snapshot_id uuid not null references rule_snapshot(id),
  expected_monthly_benefit integer not null,
  expected_monthly_interest integer not null,
  expected_monthly_cost integer not null,
  expected_net_monthly_profit integer not null,
  final_score numeric(8,2) not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_reco_profile_created on recommendation_run(profile_id, created_at desc);

create table if not exists recommendation_bundle_item (
  id bigserial primary key,
  recommendation_run_id uuid not null references recommendation_run(id) on delete cascade,
  bundle_rank smallint not null check (bundle_rank between 1 and 3),
  card_id uuid references card_product(id),
  account_id uuid references account_product(id),
  role_code varchar(20) not null check (role_code in ('MAIN_CARD','SUB_CARD','PRIMARY_ACCOUNT','SAVINGS_ACCOUNT')),
  expected_value integer not null,
  reason_text varchar(240) not null
);

create index if not exists idx_bundle_run on recommendation_bundle_item(recommendation_run_id);

-- =========================
-- 5) Action Checklist
-- =========================
create table if not exists action_checklist (
  id uuid primary key default gen_random_uuid(),
  recommendation_run_id uuid not null references recommendation_run(id) on delete cascade,
  title varchar(120) not null,
  status varchar(20) not null check (status in ('TODO','IN_PROGRESS','DONE')),
  due_date date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_checklist_run on action_checklist(recommendation_run_id);

-- =========================
-- 6) Auth (Google Login + Auto Login)
-- =========================
create table if not exists app_user (
  id uuid primary key default gen_random_uuid(),
  email varchar(255) not null unique,
  name varchar(100),
  profile_image_url text,
  role varchar(20) not null default 'USER',
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists user_auth_provider (
  id bigserial primary key,
  user_id uuid not null references app_user(id) on delete cascade,
  provider varchar(20) not null check (provider in ('GOOGLE')),
  provider_user_id varchar(200) not null,
  created_at timestamptz not null default now(),
  unique(provider, provider_user_id),
  unique(user_id, provider)
);

create table if not exists refresh_token_session (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references app_user(id) on delete cascade,
  refresh_token_hash varchar(255) not null,
  user_agent varchar(255),
  ip_address varchar(64),
  expires_at timestamptz not null,
  revoked_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists idx_refresh_token_user on refresh_token_session(user_id);
create index if not exists idx_refresh_token_exp on refresh_token_session(expires_at);

-- =========================
-- 7) Deposit/Rate Condition (수신 특화)
-- =========================
create table if not exists account_rate_condition (
  id bigserial primary key,
  account_id uuid not null references account_product(id) on delete cascade,
  condition_code varchar(40) not null,
  condition_name varchar(120) not null,
  bonus_rate_bp integer not null,
  is_required boolean not null default false,
  unique(account_id, condition_code)
);

create index if not exists idx_rate_condition_account on account_rate_condition(account_id);

-- =========================
-- 8) Cross-selling Package Offer
-- =========================
create table if not exists package_offer (
  id uuid primary key default gen_random_uuid(),
  offer_name varchar(120) not null,
  description text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists package_offer_item (
  id bigserial primary key,
  package_offer_id uuid not null references package_offer(id) on delete cascade,
  card_id uuid references card_product(id),
  account_id uuid references account_product(id),
  role_code varchar(30) not null check (role_code in ('MAIN_CARD','SUB_CARD','MAIN_ACCOUNT','SAVINGS_ACCOUNT','AUTO_TRANSFER')),
  extra_benefit_value integer not null default 0
);

create index if not exists idx_offer_item_offer on package_offer_item(package_offer_id);

-- =========================
-- 9) Non-face-to-face Onboarding
-- =========================
create table if not exists onboarding_session (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references app_user(id) on delete cascade,
  status varchar(20) not null check (status in ('STARTED','IN_PROGRESS','COMPLETED','FAILED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists onboarding_step_log (
  id bigserial primary key,
  session_id uuid not null references onboarding_session(id) on delete cascade,
  step_code varchar(30) not null check (step_code in ('OCR_VERIFY','MICRO_DEPOSIT_VERIFY','TERMS_AGREE')),
  result_code varchar(20) not null check (result_code in ('PASS','FAIL')),
  detail_message varchar(255),
  created_at timestamptz not null default now()
);

create index if not exists idx_onboarding_step_session on onboarding_step_log(session_id);

-- =========================
-- 10) Backtesting / Simulation History
-- =========================
create table if not exists recommendation_backtest (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references user_profile(id) on delete cascade,
  base_date date not null,
  horizon_months smallint not null check (horizon_months between 1 and 36),
  simulated_net_profit integer not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_backtest_profile on recommendation_backtest(profile_id);

-- =========================
-- 11) Redirect Tracking (추천 -> 공식 사이트 이동)
-- =========================
create table if not exists partner_site (
  id uuid primary key default gen_random_uuid(),
  provider_name varchar(80) not null,
  product_type varchar(20) not null check (product_type in ('CARD','ACCOUNT')),
  product_external_key varchar(120) not null,
  official_url text not null,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_partner_site_active on partner_site(is_active);

create table if not exists recommendation_redirect_event (
  id uuid primary key default gen_random_uuid(),
  recommendation_run_id uuid not null references recommendation_run(id) on delete cascade,
  user_id uuid references app_user(id),
  partner_site_id uuid not null references partner_site(id),
  clicked_at timestamptz not null default now(),
  user_agent varchar(255),
  ip_address varchar(64),
  referrer varchar(255)
);

create index if not exists idx_redirect_run on recommendation_redirect_event(recommendation_run_id);
create index if not exists idx_redirect_clicked_at on recommendation_redirect_event(clicked_at desc);
