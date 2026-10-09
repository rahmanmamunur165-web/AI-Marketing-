# ContactFlow — Public Business Email Intelligence & Gmail Outreach SaaS

ContactFlow is a production-oriented SaaS application designed to discover publicly displayed business contact information on permitted websites, organize and deduplicate contacts, export clean CSV spreadsheets with formula injection defense, and allow authenticated users to send approved email campaigns through their own connected Gmail accounts.

---

## Key Features

1. **Polite, Restricted Website Crawler**
   - Accept HTTP/HTTPS URLs only.
   - Strict SSRF (Server-Side Request Forgery) protection against private IPv4/IPv6, localhost, loopback, link-local, and cloud metadata endpoints (`169.254.169.254`).
   - Per-domain crawl limits, politeness delays, max depth of 3, and conservative default page budgets (25 pages, max 100).
   - Extracts publicly displayed business contact emails (`info@`, `sales@`, `contact@`, etc.) and mailto links.

2. **Deduplicated Contact Management**
   - Automatically normalizes emails and domains.
   - Distinguishes role business accounts from general public addresses.
   - Retains exact source URLs and discovery timestamps.
   - Search, filter by validation status, bulk selection, and deletion.

3. **Secure CSV Export**
   - Safe CSV generation with **Spreadsheet Formula Injection Defense** (CWE-1236): prefixes leading `=`, `+`, `-`, `@`, `\t`, `\r`, `%` with `'`.
   - UTF-8 BOM encoding for seamless display in Microsoft Excel and Google Sheets.
   - Direct download and mobile share integration.

4. **Google OAuth 2.0 & Gmail API Outreach**
   - Authorization Code flow requesting minimum required scopes: `https://www.googleapis.com/auth/gmail.send`, profile, and email.
   - Never requests inbox-reading or full mailbox access.
   - Tokens encrypted with AES-256-GCM.
   - Explicit user review and consent modal prior to sending.
   - Automatic suppression list and opt-out checks before dispatch.
   - Conservative rate limiting (~2 emails/sec) to avoid mailbox penalties.

5. **Cross-Platform Architectures**
   - **Android Native Applet**: Built with Kotlin, Jetpack Compose, Material Design 3, Room local database, and coroutines. Runs directly in the AI Studio streaming emulator.
   - **Full-Stack SaaS Package**: Node.js REST API (Express), PostgreSQL (Prisma ORM), Redis (BullMQ queues), and React (Vite + Tailwind CSS).

---

## Project Structure

```
├── app/                           # Android Native Application
│   ├── src/main/java/com/example/ # Kotlin Jetpack Compose Code
│   │   ├── crawler/               # SSRF Validator, Public Email Extractor, Crawler Engine
│   │   ├── data/local/            # Room Database, DAOs, Entities
│   │   ├── service/               # CSV Exporter, Gmail Outreach Service
│   │   ├── ui/                    # Material 3 UI Screens & Components
│   │   └── viewmodel/             # ContactFlow ViewModel
│   └── src/main/res/              # Android Drawables, Icons & Values
├── apps/
│   ├── api/                       # Node.js TypeScript REST API & Background Workers
│   └── web/                       # React 18, Vite, Tailwind CSS Frontend
├── prisma/                        # Database Schema & Migrations
├── docker-compose.yml             # Local Development Stack (PostgreSQL, Redis, API, Web)
├── .env.example                   # Environment configuration template
└── README.md                      # Documentation
```

---

## Google Cloud OAuth Setup

1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a project named `ContactFlow-Outreach`.
3. Navigate to **APIs & Services > Library** and enable the **Gmail API**.
4. Configure the **OAuth Consent Screen**:
   - User Type: External (or Internal for Google Workspace).
   - Scopes: Add `https://www.googleapis.com/auth/gmail.send`.
5. Under **Credentials**, create an **OAuth 2.0 Client ID**:
   - Application type: Web application.
   - Authorized Redirect URIs: `http://localhost:4000/api/v1/integrations/google/callback`.
6. Add the `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` to your environment.

---

## Running with Docker Compose

```bash
# 1. Clone repository and setup environment
cp .env.example .env

# 2. Start PostgreSQL, Redis, API, and Web containers
docker compose up -d

# 3. Access web dashboard
open http://localhost:5173
```
