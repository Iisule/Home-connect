-- =============================================================================
-- NaijaProptech - PostgreSQL bootstrap (role + database + extensions + schema + seed)
-- =============================================================================
-- Run ONCE as a PostgreSQL superuser, side-by-side with WAMP/XAMPP (which use
-- MySQL on 3306 / Apache on 80,443 and therefore never touch PostgreSQL on 5432):
--
--   Windows (cmd):  "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -h 127.0.0.1 -p 5432 -f database\init.sql
--   macOS/Linux :   psql -U postgres -h 127.0.0.1 -p 5432 -f database/init.sql
--
-- The script is idempotent for role/database/extensions/tables. The seed block at
-- the bottom uses ON CONFLICT DO NOTHING so re-running will not duplicate rows.
--
-- PREREQUISITES (server-side packages, not installable from SQL):
--   * pgvector  -> provides the "vector" extension and the <=> cosine operator
--   * PostGIS   -> required by Hibernate Spatial for geometry(Point,4326)
--   * uuid-ossp -> ships with PostgreSQL "contrib" (included in the EDB installer)
-- See README.md section 2 for Windows install steps, or use the Docker image in ./docker.
-- =============================================================================

\set ON_ERROR_STOP on

-- 1) Application role (CHANGE THE PASSWORD outside local development) ----------
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'proptech_app') THEN
        CREATE ROLE proptech_app LOGIN PASSWORD 'proptech_dev_pw';
    END IF;
END
$$;

-- 2) Database (CREATE DATABASE cannot run inside a transaction/DO block, so we use \gexec)
SELECT 'CREATE DATABASE proptech OWNER proptech_app TEMPLATE template0 ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'proptech')
\gexec

\connect proptech

-- 3) Extensions (need superuser; that is why this file is run as "postgres") ----
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";   -- uuid_generate_v4()
CREATE EXTENSION IF NOT EXISTS vector;        -- pgvector: vector(n), <=> cosine distance, HNSW/IVFFlat indexes
CREATE EXTENSION IF NOT EXISTS postgis;       -- geometry(Point,4326) for Hibernate Spatial

-- PostgreSQL 15+ no longer lets ordinary roles create objects in "public" by default.
GRANT USAGE, CREATE ON SCHEMA public TO proptech_app;

-- =============================================================================
-- 4) SCHEMA
-- =============================================================================

-- ---- 5-tier geo-hierarchy: State > LGA > Settlement/Village > Estate > Property
CREATE TABLE IF NOT EXISTS states (
    id    uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    name  varchar(80) NOT NULL UNIQUE,
    code  varchar(4)  NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS lgas (
    id        uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    state_id  uuid NOT NULL REFERENCES states(id) ON DELETE RESTRICT,
    name      varchar(120) NOT NULL,
    is_urban  boolean NOT NULL DEFAULT false,   -- urban LGAs get street-address flows; rural ones lean on landmarks
    UNIQUE (state_id, name)
);

CREATE TABLE IF NOT EXISTS settlement_villages (
    id      uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    lga_id  uuid NOT NULL REFERENCES lgas(id) ON DELETE RESTRICT,
    name    varchar(120) NOT NULL,
    UNIQUE (lga_id, name)
);

CREATE TABLE IF NOT EXISTS estate_neighborhoods (
    id             uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    settlement_id  uuid NOT NULL REFERENCES settlement_villages(id) ON DELETE RESTRICT,
    name           varchar(140) NOT NULL,
    is_gated       boolean NOT NULL DEFAULT false,
    UNIQUE (settlement_id, name)
);

-- ---- People -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_users (
    id                     uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    full_name              varchar(160) NOT NULL,
    email                  varchar(190) NOT NULL UNIQUE,
    phone_number           varchar(20)  NOT NULL UNIQUE,          -- E.164, e.g. +2348031110001
    password_hash          varchar(100) NOT NULL,                 -- BCrypt
    role                   varchar(20)  NOT NULL CHECK (role IN ('TENANT','AGENT','FIELD_OFFICER','ADMIN')),
    nin_verified           boolean NOT NULL DEFAULT false,
    preferred_language     varchar(5)   NOT NULL DEFAULT 'en',
    bank_name              varchar(80),
    bank_code              varchar(6),
    bank_account_number    varchar(10),
    bank_account_name      varchar(160),
    rating                 numeric(3,2) NOT NULL DEFAULT 0,
    deals_closed           integer NOT NULL DEFAULT 0,
    avg_response_minutes   integer NOT NULL DEFAULT 0,
    created_at             timestamptz NOT NULL DEFAULT now()
);

-- ---- Property (the physical home; agents attach listings to it) -------------
CREATE TABLE IF NOT EXISTS properties (
    id                    uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    lga_id                uuid NOT NULL REFERENCES lgas(id),        -- kept denormalised: the duplicate check is scoped by it
    settlement_id         uuid NOT NULL REFERENCES settlement_villages(id),
    estate_id             uuid REFERENCES estate_neighborhoods(id),
    title                 varchar(200) NOT NULL,
    property_type         varchar(32) NOT NULL CHECK (property_type IN
                            ('SELF_CONTAIN','ROOM_AND_PARLOUR','TWO_BEDROOM','THREE_BEDROOM','DUPLEX','SHOP')),
    landmark_description  text NOT NULL,                            -- offline routing: "3rd gate after the red water tank..."
    location              geometry(Point,4326),                     -- optional GPS pin (lng/lat)
    primary_image_url     text,
    phash                 varchar(64),                              -- 64-bit perceptual hash from the AI service
    embedding             vector(512),                              -- room-layout vector from the AI service
    ai_flagged            boolean NOT NULL DEFAULT false,           -- true => structural duplicate of another home in the same LGA
    duplicate_of_id       uuid REFERENCES properties(id),
    status                varchar(20) NOT NULL DEFAULT 'PENDING_AUDIT' CHECK (status IN
                            ('PENDING_AUDIT','VERIFIED','PENDING_MERGE','MERGED','RESERVED','RENTED','REJECTED')),
    created_by            uuid NOT NULL REFERENCES app_users(id),
    audited_by            uuid REFERENCES app_users(id),
    audited_at            timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_properties_lga         ON properties (lga_id);
CREATE INDEX IF NOT EXISTS idx_properties_settlement  ON properties (settlement_id);
CREATE INDEX IF NOT EXISTS idx_properties_status      ON properties (status);
CREATE INDEX IF NOT EXISTS idx_properties_location    ON properties USING gist (location);
-- Approximate nearest-neighbour index for cosine distance (needs pgvector >= 0.5.0).
-- The duplicate query also filters on lga_id, so on very large tables consider
-- per-LGA partitioning or "SET hnsw.iterative_scan = relaxed_order" (pgvector >= 0.8).
CREATE INDEX IF NOT EXISTS idx_properties_embedding_hnsw
    ON properties USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS property_photos (
    property_id  uuid NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    photo_url    text NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_property_photos_property ON property_photos (property_id);

-- ---- Agent offers on a property (co-listing) ---------------------------------
CREATE TABLE IF NOT EXISTS agent_listings (
    id            uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    property_id   uuid NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    agent_id      uuid NOT NULL REFERENCES app_users(id),
    annual_rent   numeric(14,2) NOT NULL CHECK (annual_rent > 0),
    agency_fee    numeric(14,2) NOT NULL DEFAULT 0 CHECK (agency_fee >= 0),
    status        varchar(20) NOT NULL DEFAULT 'PENDING_AUDIT' CHECK (status IN
                    ('PENDING_AUDIT','ACTIVE','RESERVED','CLOSED')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (property_id, agent_id)
);
CREATE INDEX IF NOT EXISTS idx_listings_property ON agent_listings (property_id);

-- ---- Fintech: wallets, escrow, ledger ----------------------------------------
CREATE TABLE IF NOT EXISTS wallets (
    id             uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_type     varchar(20) NOT NULL CHECK (owner_type IN ('CORPORATE','USER')),
    owner_user_id  uuid UNIQUE REFERENCES app_users(id),
    balance        numeric(16,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at     timestamptz NOT NULL DEFAULT now()
);
-- exactly one corporate revenue wallet
CREATE UNIQUE INDEX IF NOT EXISTS uq_wallet_corporate ON wallets (owner_type) WHERE owner_type = 'CORPORATE';

CREATE TABLE IF NOT EXISTS escrow_transactions (
    id                 uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    listing_id         uuid NOT NULL REFERENCES agent_listings(id),
    tenant_id          uuid NOT NULL REFERENCES app_users(id),
    agent_id           uuid NOT NULL REFERENCES app_users(id),
    field_officer_id   uuid NOT NULL REFERENCES app_users(id),
    annual_rent        numeric(14,2) NOT NULL,
    agency_fee         numeric(14,2) NOT NULL,
    total_amount       numeric(14,2) NOT NULL,     -- what the tenant pays
    platform_fee       numeric(14,2) NOT NULL,     -- 3% of annual rent -> corporate wallet
    logistics_fee      numeric(14,2) NOT NULL,     -- flat NGN 5,000    -> field officer wallet
    net_to_agent       numeric(14,2) NOT NULL,     -- remainder         -> agent bank account
    status             varchar(20) NOT NULL DEFAULT 'PENDING_PAYMENT' CHECK (status IN
                         ('PENDING_PAYMENT','FUNDED','RELEASED','LOCKED','REFUNDED')),
    otp_hash           varchar(64),                -- HMAC of the 4-digit voucher; the plain OTP is never stored
    otp_attempts       integer NOT NULL DEFAULT 0,
    otp_expires_at     timestamptz,
    payment_reference  varchar(80),
    payout_reference   varchar(80),
    created_at         timestamptz NOT NULL DEFAULT now(),
    funded_at          timestamptz,
    released_at        timestamptz
);
CREATE INDEX IF NOT EXISTS idx_escrow_tenant ON escrow_transactions (tenant_id);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id           uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    escrow_id    uuid NOT NULL REFERENCES escrow_transactions(id),
    wallet_id    uuid REFERENCES wallets(id),          -- NULL for bank payouts (money leaves the platform)
    entry_type   varchar(20) NOT NULL CHECK (entry_type IN ('PLATFORM_FEE','LOGISTICS_FEE','AGENT_PAYOUT')),
    amount       numeric(14,2) NOT NULL CHECK (amount > 0),
    reference    varchar(80),
    description  varchar(200),
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ledger_escrow ON ledger_entries (escrow_id);

-- ---- KYC simulation tables (replace with NIMC / telco APIs in production) -----
CREATE TABLE IF NOT EXISTS kyc_profiles (
    msisdn         varchar(20) PRIMARY KEY,               -- normalised +234...
    nin_hash       varchar(64) NOT NULL,                  -- HMAC(NIN); the raw NIN is never stored
    nin_last4      varchar(4)  NOT NULL,
    nin_full_name  varchar(160) NOT NULL,
    slip_key       varchar(120),                          -- private file key of the uploaded NIN slip
    status         varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','VERIFIED','REJECTED')),
    verified_at    timestamptz
);

CREATE TABLE IF NOT EXISTS sim_registry_mock (
    msisdn           varchar(20) PRIMARY KEY,
    registered_name  varchar(160) NOT NULL
);

-- Let the application role use everything created above.
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO proptech_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO proptech_app;

-- =============================================================================
-- 5) SEED DATA (sample data for demos - verify boundaries before real use)
-- =============================================================================

INSERT INTO states (name, code) VALUES
    ('Kano','KN'), ('Kaduna','KD'), ('Katsina','KT')
ON CONFLICT DO NOTHING;

INSERT INTO lgas (state_id, name, is_urban)
SELECT s.id, v.name, v.is_urban
FROM (VALUES
    ('KN','Nassarawa',true), ('KN','Tarauni',true), ('KN','Kumbotso',true), ('KN','Gwale',true), ('KN','Dawakin Kudu',false),
    ('KD','Kaduna North',true), ('KD','Kaduna South',true), ('KD','Chikun',false), ('KD','Kajuru',false),
    ('KT','Katsina',true), ('KT','Funtua',true), ('KT','Daura',false)
) AS v(code, name, is_urban)
JOIN states s ON s.code = v.code
ON CONFLICT DO NOTHING;

INSERT INTO settlement_villages (lga_id, name)
SELECT l.id, v.name
FROM (VALUES
    ('Nassarawa','Gama'), ('Nassarawa','Bompai'),
    ('Tarauni','Hotoro'), ('Tarauni','Unguwa Uku'),
    ('Kumbotso','Panshekara'), ('Kumbotso','Kumbotso Town'),
    ('Gwale','Dorayi'), ('Gwale','Gwale Town'),
    ('Dawakin Kudu','Dawakin Kudu Town'), ('Dawakin Kudu','Gogel'),
    ('Kaduna North','Unguwan Rimi'), ('Kaduna North','Kawo'),
    ('Kaduna South','Barnawa'), ('Kaduna South','Television'),
    ('Chikun','Sabon Tasha'), ('Chikun','Narayi'),
    ('Kajuru','Kajuru Town'),
    ('Katsina','Kofar Marusa'), ('Katsina','Ajiwa'),
    ('Funtua','Funtua Town'), ('Daura','Daura Town')
) AS v(lga, name)
JOIN lgas l ON l.name = v.lga
ON CONFLICT DO NOTHING;

INSERT INTO estate_neighborhoods (settlement_id, name, is_gated)
SELECT sv.id, v.name, v.is_gated
FROM (VALUES
    ('Hotoro','Hotoro Layout Estate',true),
    ('Hotoro','Hotoro North Cluster',false),
    ('Barnawa','Barnawa Low-Cost Estate',true),
    ('Gama','Gama Housing Layout',false),
    ('Panshekara','Panshekara Gardens',true)
) AS v(settlement, name, is_gated)
JOIN settlement_villages sv ON sv.name = v.settlement
ON CONFLICT DO NOTHING;

-- ---- Demo accounts. Every password is:  Password123!  ------------------------
INSERT INTO app_users (id, full_name, email, phone_number, password_hash, role, nin_verified, preferred_language,
                       bank_name, bank_code, bank_account_number, bank_account_name, rating, deals_closed, avg_response_minutes)
VALUES
 ('c0000000-0000-0000-0000-000000000001','Fatima Sani','tenant@demo.ng','+2348030000001','$2a$10$nS4ebBqQSaCOLSdYblgVqusJQ8e35DT5H1F2jNt/Y8MD1NhIQE/3u','TENANT',false,'ha',NULL,NULL,NULL,NULL,0,0,0),
 ('c0000000-0000-0000-0000-000000000002','Musa Ibrahim Danladi','musa@demo.ng','+2348031110001','$2a$10$nS4ebBqQSaCOLSdYblgVqusJQ8e35DT5H1F2jNt/Y8MD1NhIQE/3u','AGENT',true,'ha','Guaranty Trust Bank','058','0123456789','Musa Ibrahim Danladi',4.80,42,12),
 ('c0000000-0000-0000-0000-000000000003','Aisha Bello Yusuf','aisha@demo.ng','+2348031110002','$2a$10$nS4ebBqQSaCOLSdYblgVqusJQ8e35DT5H1F2jNt/Y8MD1NhIQE/3u','AGENT',true,'en','Zenith Bank','057','2087654321','Aisha Bello Yusuf',4.60,31,25),
 ('c0000000-0000-0000-0000-000000000004','Hauwa Lawal Abubakar','hauwa@demo.ng','+2348031110003','$2a$10$nS4ebBqQSaCOLSdYblgVqusJQ8e35DT5H1F2jNt/Y8MD1NhIQE/3u','AGENT',true,'ha','Access Bank','044','0456123789','Hauwa Lawal Abubakar',4.20,12,45),
 ('c0000000-0000-0000-0000-000000000005','Kabiru Mohammed','officer@demo.ng','+2348032220001','$2a$10$nS4ebBqQSaCOLSdYblgVqusJQ8e35DT5H1F2jNt/Y8MD1NhIQE/3u','FIELD_OFFICER',true,'en','First Bank','011','3011223344','Kabiru Mohammed',0,0,0),
 ('c0000000-0000-0000-0000-000000000006','Platform Admin','admin@demo.ng','+2348033330001','$2a$10$nS4ebBqQSaCOLSdYblgVqusJQ8e35DT5H1F2jNt/Y8MD1NhIQE/3u','ADMIN',true,'en',NULL,NULL,NULL,NULL,0,0,0),
 -- USSD exception-handling test agents (see README, section 7)
 ('c0000000-0000-0000-0000-000000000008','Bala Kabiru','bala@demo.ng','+2348031110008','$2a$10$nS4ebBqQSaCOLSdYblgVqusJQ8e35DT5H1F2jNt/Y8MD1NhIQE/3u','AGENT',false,'ha',NULL,NULL,NULL,NULL,0,0,0),
 ('c0000000-0000-0000-0000-000000000009','Sani Abdullahi Umar','sani@demo.ng','+2348031110009','$2a$10$nS4ebBqQSaCOLSdYblgVqusJQ8e35DT5H1F2jNt/Y8MD1NhIQE/3u','AGENT',false,'ha',NULL,NULL,NULL,NULL,0,0,0)
ON CONFLICT DO NOTHING;

-- SIM registration (telco side) vs NIN profile (NIMC side)
INSERT INTO sim_registry_mock (msisdn, registered_name) VALUES
    ('+2348031110001','MUSA IBRAHIM DANLADI'),
    ('+2348031110002','AISHA BELLO YUSUF'),
    ('+2348031110003','HAUWA LAWAL ABUBAKAR'),
    ('+2348031110008','BALA KABIRU'),
    ('+2348031110009','YAKUBU ADAMU LAWAL')          -- SIM belongs to somebody else => NAME_MISMATCH demo
ON CONFLICT DO NOTHING;

-- nin_hash below is a placeholder value for seed rows only; real rows are written by KycService.
INSERT INTO kyc_profiles (msisdn, nin_hash, nin_last4, nin_full_name, status, verified_at) VALUES
    ('+2348031110001', repeat('a',64), '1101', 'Musa Ibrahim Danladi',  'VERIFIED', now()),
    ('+2348031110002', repeat('b',64), '1102', 'Aisha Bello Yusuf',     'VERIFIED', now()),
    ('+2348031110003', repeat('c',64), '1103', 'Hauwa Lawal Abubakar',  'VERIFIED', now()),
    ('+2348031110009', repeat('d',64), '1109', 'Sani Abdullahi Umar',   'VERIFIED', now())
    -- +2348031110008 deliberately has NO NIN profile => NO_NIN_PROFILE demo
ON CONFLICT DO NOTHING;

-- Wallets: one corporate revenue wallet + the field officer's virtual wallet
INSERT INTO wallets (id, owner_type, owner_user_id, balance) VALUES
    ('f0000000-0000-0000-0000-000000000001','CORPORATE',NULL,0),
    ('f0000000-0000-0000-0000-000000000002','USER','c0000000-0000-0000-0000-000000000005',0)
ON CONFLICT DO NOTHING;

-- ---- Three audited demo homes ----------------------------------------------
INSERT INTO properties (id, lga_id, settlement_id, estate_id, title, property_type, landmark_description,
                        location, status, created_by, audited_by, audited_at)
SELECT 'd0000000-0000-0000-0000-000000000001', l.id, sv.id, en.id,
       'Newly built 2-bedroom flat, Hotoro', 'TWO_BEDROOM',
       'From Hotoro market junction take the tarred road behind the central mosque. The house is the third gate after the red water tank, opposite the Al-Amin pharmacy.',
       ST_SetSRID(ST_MakePoint(8.5720, 12.0080), 4326), 'VERIFIED',
       'c0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000005', now()
FROM lgas l JOIN settlement_villages sv ON sv.lga_id = l.id AND sv.name = 'Hotoro'
            JOIN estate_neighborhoods en ON en.settlement_id = sv.id AND en.name = 'Hotoro Layout Estate'
WHERE l.name = 'Tarauni'
ON CONFLICT DO NOTHING;

INSERT INTO properties (id, lga_id, settlement_id, estate_id, title, property_type, landmark_description,
                        location, status, created_by, audited_by, audited_at)
SELECT 'd0000000-0000-0000-0000-000000000002', l.id, sv.id, NULL,
       'Self-contain near Gama primary school', 'SELF_CONTAIN',
       'Opposite Gama primary school, beside the tailor shop with a blue signboard. Ask for Baba Shehu compound.',
       ST_SetSRID(ST_MakePoint(8.5560, 12.0250), 4326), 'VERIFIED',
       'c0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000005', now()
FROM lgas l JOIN settlement_villages sv ON sv.lga_id = l.id AND sv.name = 'Gama'
WHERE l.name = 'Nassarawa'
ON CONFLICT DO NOTHING;

INSERT INTO properties (id, lga_id, settlement_id, estate_id, title, property_type, landmark_description,
                        location, status, created_by, audited_by, audited_at)
SELECT 'd0000000-0000-0000-0000-000000000003', l.id, sv.id, en.id,
       '3-bedroom bungalow, Barnawa', 'THREE_BEDROOM',
       'Behind Barnawa motor park, go straight past the mechanic village. The cream bungalow with the green gate is the last house before the drainage channel.',
       ST_SetSRID(ST_MakePoint(7.4230, 10.4780), 4326), 'VERIFIED',
       'c0000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000005', now()
FROM lgas l JOIN settlement_villages sv ON sv.lga_id = l.id AND sv.name = 'Barnawa'
            JOIN estate_neighborhoods en ON en.settlement_id = sv.id AND en.name = 'Barnawa Low-Cost Estate'
WHERE l.name = 'Kaduna South'
ON CONFLICT DO NOTHING;

-- Co-listing: three verified agents compete for the SAME Hotoro home
INSERT INTO agent_listings (id, property_id, agent_id, annual_rent, agency_fee, status) VALUES
 ('e0000000-0000-0000-0000-000000000001','d0000000-0000-0000-0000-000000000001','c0000000-0000-0000-0000-000000000002', 850000, 85000,'ACTIVE'),
 ('e0000000-0000-0000-0000-000000000002','d0000000-0000-0000-0000-000000000001','c0000000-0000-0000-0000-000000000003', 820000, 90000,'ACTIVE'),
 ('e0000000-0000-0000-0000-000000000003','d0000000-0000-0000-0000-000000000001','c0000000-0000-0000-0000-000000000004', 880000, 60000,'ACTIVE'),
 ('e0000000-0000-0000-0000-000000000004','d0000000-0000-0000-0000-000000000002','c0000000-0000-0000-0000-000000000003', 250000, 25000,'ACTIVE'),
 ('e0000000-0000-0000-0000-000000000005','d0000000-0000-0000-0000-000000000002','c0000000-0000-0000-0000-000000000002', 260000, 20000,'ACTIVE'),
 ('e0000000-0000-0000-0000-000000000006','d0000000-0000-0000-0000-000000000003','c0000000-0000-0000-0000-000000000004',1200000,120000,'ACTIVE')
ON CONFLICT DO NOTHING;

-- =============================================================================
-- 6) SMOKE TESTS - you should see vector, uuid-ossp and postgis listed, and 1
-- =============================================================================
SELECT extname, extversion FROM pg_extension WHERE extname IN ('vector','uuid-ossp','postgis') ORDER BY extname;
SELECT '[1,0,0]'::vector <=> '[0,1,0]'::vector AS cosine_distance_should_be_1;
SELECT (SELECT count(*) FROM states) AS states, (SELECT count(*) FROM lgas) AS lgas,
       (SELECT count(*) FROM settlement_villages) AS settlements, (SELECT count(*) FROM properties) AS properties;
