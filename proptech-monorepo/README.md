# NaijaProptech

A property marketplace prototype for Northern Nigeria: AI-assisted duplicate-listing detection,
USSD access for agents on basic phones, Hausa-aware NIN/KYC checks, and an OTP-gated escrow that
splits funds automatically at physical key hand-over.

```
proptech-monorepo/
├── ai-service/     Python FastAPI microservice - photo -> pHash + 512-dim "room layout" vector
├── backend/        Spring Boot 3 API - JWT auth, geo hierarchy, properties, USSD, KYC, escrow
├── database/       Single idempotent init.sql - schema + pgvector/PostGIS extensions + demo seed data
├── frontend/       Next.js 14 (App Router) - Tenant Discovery, Agent Onboarding, Checkout, etc.
└── docker/         Reserved for an optional future docker-compose setup (not required to run locally)
```

Everything below assumes you already have **WAMP/XAMPP** (or MAMP) running for a *different*
project - Apache on 80/443 and MySQL on 3306 are left completely alone. This stack adds its own
PostgreSQL instance on port **5432**, which does not conflict with any of that.

---

## 1. Prerequisites

| Tool | Version used in this prototype | Notes |
|---|---|---|
| PostgreSQL | 16, with `pgvector` and `postgis` extensions | Runs standalone, alongside WAMP/XAMPP's MySQL |
| Java | 17+ | Spring Boot 3.3 requires 17 minimum |
| Maven | 3.9+ | For `backend/` |
| Python | 3.11+ | For `ai-service/` |
| Node.js | 20+ | For `frontend/` |

### Installing PostgreSQL 16 + pgvector + PostGIS on Windows (for WAMP/XAMPP users)

1. Download the PostgreSQL 16 installer from https://www.postgresql.org/download/windows/ and run it.
   Keep the default port **5432** - this is separate from XAMPP's MySQL on 3306, so there is no clash.
2. During install, also open **Stack Builder** (offered at the end of the installer) and install:
   - **pgvector** is not in Stack Builder by default on Windows; the easiest path is to install it via
     the prebuilt binaries at https://github.com/pgvector/pgvector (or `pgvector` is bundled if you
     use the "PostgreSQL with extensions" builds some vendors ship). If building from source, you will
     need the Visual Studio Build Tools with the "Desktop development with C++" workload.
   - **PostGIS** *is* available directly in Stack Builder under "Spatial Extensions" - select it there.
3. On macOS/Linux, both extensions are one line each:
   ```bash
   # macOS (Homebrew)
   brew install postgresql@16 pgvector postgis

   # Ubuntu/Debian
   sudo apt install postgresql-16 postgresql-16-pgvector postgresql-16-postgis-3
   ```

### Run the database bootstrap script

`database/init.sql` is fully idempotent - safe to re-run any time - and does everything in one pass:
creates the `proptech_app` role and `proptech` database, enables `uuid-ossp`, `vector`, and `postgis`,
builds the entire schema (5-tier geo-hierarchy, users, properties, listings, escrow, wallets, ledger,
KYC), and seeds demo data (3 states, 12 LGAs, 21 settlements, 8 users, 3 verified properties with
Hausa-context landmark descriptions, and co-listing examples).

```bash
# Run as the postgres superuser
psql -U postgres -h 127.0.0.1 -f database/init.sql
```

> **Change `proptech_dev_pw`** (the default password for `proptech_app`, set inside `init.sql`)
> before using this anywhere beyond your own machine.

All demo user passwords are `Password123!`. A few notable seed accounts for testing:
- `agent1@example.com` / `agent2@example.com` / `agent3@example.com` - three competing agents on the same Hotoro property (co-listing demo)
- One agent has **no NIN profile on file** and one has a **SIM/NIN name mismatch** - both are there specifically to exercise the USSD KYC rejection paths in Hausa.

---

## 2. Run the AI microservice

```bash
cd ai-service
python -m venv venv && source venv/bin/activate   # venv\Scripts\activate on Windows
pip install -r requirements.txt
uvicorn main:app --host 127.0.0.1 --port 8000
```

Health check: `GET http://127.0.0.1:8000/health`. By default it only accepts image URLs from
`127.0.0.1`/`localhost` (SSRF guard) - widen `ALLOWED_IMAGE_HOSTS` if your uploaded photos are served
from elsewhere.

---

## 3. Run the Spring Boot backend

`backend/src/main/resources/application.properties` already points at `127.0.0.1:5432` with the
`proptech_app` credentials above, and at the AI service on `127.0.0.1:8000` - no edits needed for a
first run.

```bash
cd backend
mvn spring-boot:run
```

The API comes up on `http://127.0.0.1:8080`. Key environment variables (all have local-dev defaults
baked into `application.properties`; **none** have defaults in `application-prod.properties`, so
production deployments must set every one explicitly):

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | HS256 signing key, **must be 32+ bytes** or the app refuses to start |
| `OTP_PEPPER`, `NIN_PEPPER` | HMAC peppers for OTP/NIN hashing - never store these in the DB |
| `AI_BASE_URL` | Where the FastAPI service is reachable |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list, e.g. your Vercel domain |
| `PUBLIC_BASE_URL` | Used to build public `/media/**` URLs for uploaded photos |

Health check: `GET http://127.0.0.1:8080/actuator/health`.

### Trying the USSD flow without a real telecom account

The webhook is public and speaks the Africa's Talking contract (`POST /api/ussd/callback`,
form-urlencoded `sessionId`, `phoneNumber`, `text`). Simulate a session from the command line:

```bash
# Step 1: open a session (empty text) - requires the phone number to already have a verified
# KYC profile in kyc_profiles that matches sim_registry_mock, or you'll get the Hausa rejection message.
curl -s -X POST http://127.0.0.1:8080/api/ussd/callback \
  -d "sessionId=demo1" -d "phoneNumber=+2348031110001" -d "text="

# Each subsequent call resends the FULL star-separated history so far, e.g. after picking
# state 1 then LGA 2:
curl -s -X POST http://127.0.0.1:8080/api/ussd/callback \
  -d "sessionId=demo1" -d "phoneNumber=+2348031110001" -d "text=1*2"
```

---

## 4. Run the frontend

```bash
cd frontend
cp .env.example .env.local     # adjust NEXT_PUBLIC_API_URL / API_ORIGIN if your backend isn't on :8080
npm install
npm run dev
```

Open `http://localhost:3000`. The four core screens:
- `/discover` - progressive State → LGA → Settlement dropdowns over the public property search API
- `/onboarding` - agent account creation + NIN submission (English/Hausa toggle in the header)
- `/properties/[id]` - a single property with its photo gallery and the comparative agent-offer grid
- `/checkout/[listingId]` - reserve → pay (simulated) → 4-digit key-handover OTP → release funds

`npm run build` requires outbound access to `fonts.googleapis.com` (Next.js fetches `Fraunces` and
`IBM Plex Sans` at build time) - this is blocked in some locked-down CI/sandbox networks but works
on a normal developer machine or in Vercel's build environment.

---

## 5. Deploying

- **Frontend → Vercel**: push `frontend/` as the project root (or set it as the Vercel "Root
  Directory"). Set `API_ORIGIN` (and optionally `NEXT_PUBLIC_API_URL`) in the Vercel project's
  Environment Variables to your deployed backend's URL. `vercel.json` and `next.config.js` are
  already wired so the browser only ever calls same-origin `/api/*` and `/media/*` - the backend URL
  never needs CORS configuration changes beyond adding the Vercel domain to `CORS_ALLOWED_ORIGINS`
  (belt-and-braces, since the same-origin proxy means the browser rarely calls it cross-origin
  directly).
- **Backend → Render/AWS**: run with `SPRING_PROFILES_ACTIVE=prod` and set every environment
  variable listed above - `application-prod.properties` intentionally has no defaults, so a missing
  variable fails startup loudly instead of silently running with a dev secret in production.
- **AI microservice**: deploy anywhere that can run a small FastAPI app (Render, Fly.io, a small EC2
  instance). Set `ALLOWED_IMAGE_HOSTS` to wherever your production `/media/**` photos are actually
  served from, and set `FALLBACK_ON_FETCH_ERROR=false` so a broken image URL fails loudly rather than
  silently generating a placeholder vector.
- **Database**: any managed PostgreSQL 16+ with the `vector` and `postgis` extensions available
  (Render Postgres, AWS RDS with the right parameter group, Supabase, Neon, etc.). Run `init.sql`
  once against it the same way as local.

---

## 6. Known limitations of this prototype

- The payment gateway (`MockPaymentGatewayService`) and the SMS/USSD carrier are both simulated -
  swap them for Paystack/Flutterwave and Africa's Talking's real APIs respectively before going live.
- There's no field-officer "audit" UI yet - properties are created `PENDING_AUDIT` and the escrow
  checkout step falls back to *any* seeded field officer if a property hasn't been formally assigned
  one. A real deployment needs an audit workflow/UI before this is production-ready.
- NIN verification is checked against a mock `sim_registry_mock` table rather than a real telco
  partner API - this is clearly the piece to replace first in a real integration.
