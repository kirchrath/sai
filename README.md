# SAI – Agentensystem

Agentensystem auf Basis von **Spring Boot 4.2 / Spring AI 2.1 (Java 25)** mit einem **React-Frontend (nur Bulma)**,
**Keycloak** für die Anmeldung und **PostgreSQL/pgvector** als Datenbank. Läuft komplett per Docker Compose.

## Funktionen

- **Agenten per YAML** (`config/agents.yml`): Provider (Anthropic oder OpenAI), Modell, System-Prompt, Tools, RAG
- **Chat mit Streaming** (Server-Sent Events), Verlauf pro Benutzer in Postgres, Unterhaltungen umbenennen/löschen
- **Tools**: Datum/Uhrzeit, Rechner (eigener Parser), Web-Abruf (mit SSRF-Schutz), Wissenssuche
- **RAG pro Agent**: Dokumente (PDF, DOCX, TXT, MD, HTML …) per Tika einlesen, lokale ONNX-Embeddings
  (all-MiniLM-L6-v2, 384 Dimensionen), Speicherung in pgvector. Hochladen/Löschen nur mit Rolle `admin`
- **MCP-Server** (Streamable HTTP unter `/mcp`), abgesichert per Keycloak-Token
- **Keycloak** (OIDC, PKCE) mit vorkonfiguriertem Realm `sai`

## Architektur

```
Browser / MCP-Client
        │  http://<host>:8080
        ▼
┌──────────────── frontend (nginx) ────────────────┐
│  /          React-SPA                            │
│  /api, /mcp → backend:8080   (Spring Boot)       │
│  /auth      → keycloak:8080  (Keycloak)          │
└──────────────────────────────────────────────────┘
        backend ──► postgres (DB "sai": Daten, ChatMemory, pgvector)
        keycloak ─► postgres (DB "keycloak")
        backend ──► Anthropic / OpenAI API
```

| Verzeichnis        | Inhalt                                                        |
|--------------------|---------------------------------------------------------------|
| `backend/`         | Spring-Boot-Anwendung (Maven)                                 |
| `frontend/`        | React + TypeScript + Vite, nginx-Konfiguration                |
| `config/agents.yml`| Agenten-Definitionen (wird ins Backend gemountet)             |
| `keycloak/`        | Realm-Import (Clients, Rollen, Startbenutzer)                 |
| `docker/postgres/` | Init-Skript (legt die Keycloak-Datenbank an)                  |

## Inbetriebnahme

```bash
cp .env.example .env
# .env anpassen: SAI_PUBLIC_URL, alle Passwörter, mindestens ein API-Key
docker compose up -d --build
```

Danach ist die Anwendung unter `SAI_PUBLIC_URL` erreichbar (Standard `http://localhost:8080`).

- Anmeldung: Benutzer `admin` (Rolle admin) bzw. `user`, Passwörter aus `.env`
- Keycloak-Admin-Konsole: `SAI_PUBLIC_URL/auth/admin` (Zugang `KEYCLOAK_ADMIN_USER` / `KEYCLOAK_ADMIN_PASSWORD`)

Hinweise:

- `SAI_PUBLIC_URL` muss exakt die URL sein, die im Browser verwendet wird – sie ist Issuer der Tokens
  und Redirect-URI. Bei Änderung nach dem ersten Start die Redirect-URIs des Clients `sai-frontend`
  in Keycloak anpassen (der Realm wird nur beim ersten Start importiert).
- Der Docker-Build lädt einmalig das Embedding-Modell und die DJL-PyTorch-Bibliotheken
  (für die Architektur des Build-Hosts, ca. 300 MB) ins Image. Zur Laufzeit sind keine Downloads nötig.
- Keycloak verlangt mit `SAI_KEYCLOAK_SSL_REQUIRED=external` HTTPS für Zugriffe über öffentliche IP-Adressen.
  Für den Betrieb im Internet einen TLS-Reverse-Proxy vor Port 8080 setzen.

## Agenten konfigurieren

Siehe Kommentare in `config/agents.yml`. Nach Änderungen: `docker compose restart backend`.

```yaml
sai:
  agents:
    - id: wissen
      name: Wissensassistent
      provider: anthropic          # anthropic | openai
      model: claude-sonnet-5-5
      system-prompt: Antworte nur auf Basis der Wissensbasis.
      tools: [knowledge]           # datetime, calculator, web-fetch, knowledge
      rag: true                    # Kontext automatisch beifügen
```

## MCP-Endpoint

Externe MCP-Clients verbinden sich per Streamable HTTP mit `SAI_PUBLIC_URL/mcp` und senden ein Bearer-Token.
Für Maschinen-Clients gibt es den Keycloak-Client `sai-mcp` (Client-Credentials):

```bash
TOKEN=$(curl -s -d grant_type=client_credentials -d client_id=sai-mcp \
  -d client_secret="$SAI_MCP_CLIENT_SECRET" \
  "$SAI_PUBLIC_URL/auth/realms/sai/protocol/openid-connect/token" | jq -r .access_token)
```

Angebotene Tools: `current_datetime`, `calculate`, `web_fetch`, `list_agents`, `search_agent_knowledge`.

## Entwicklung

```bash
# Backend (JDK 25, Postgres mit pgvector auf localhost:5432 erwartet)
cd backend && mvn test && mvn spring-boot:run

# Frontend (leitet /api und /auth an den laufenden Compose-Stack weiter)
cd frontend && npm install && npm run dev   # http://localhost:5173
```

## Tests und Testabdeckung

```bash
cd backend && mvn verify
```

- Unit-Tests (JUnit 5, Mockito, Reactor `StepVerifier`) und Persistenz-Integrationstests mit
  **Testcontainers** (`pgvector/pgvector:pg17`, Flyway) – für `mvn verify` wird daher Docker benötigt.
- **JaCoCo** erzwingt **100 %** Instruction-, Branch-, Line- und Method-Coverage; der Build bricht sonst ab.
  Report: `backend/target/site/jacoco/index.html`.
- Von der Messung ausgenommen sind Controller (inkl. darin definierter Request-/View-Records), DTOs (`*Dto`),
  Konfiguration (`config`-Paket, `SaiApplication`) und Entities.

## Persistenz

Eigene Tabellen werden mit **Spring Data JDBC** angesprochen. Entities sind Java-Records mit
UUID-ID, `@Version` (optimistisches Locking) sowie `@CreatedDate`/`@LastModifiedDate` (`created_at`, `modified_at`).
Die Tabellen von Spring AI (`spring_ai_chat_memory`, `vector_store`) verwaltet Spring AI selbst.

## REST-API (Auszug)

| Methode | Pfad                                         | Beschreibung                         |
|---------|----------------------------------------------|--------------------------------------|
| GET     | `/api/agents`                                | Konfigurierte Agenten                |
| GET/POST| `/api/conversations`                         | Eigene Unterhaltungen / neue anlegen |
| PATCH/DELETE | `/api/conversations/{id}`               | Umbenennen / löschen                 |
| GET     | `/api/conversations/{id}/messages`           | Verlauf                              |
| POST    | `/api/conversations/{id}/messages`           | Nachricht senden, Antwort als SSE (`token`, `done`, `error`) |
| GET/POST| `/api/agents/{agentId}/documents`            | Wissensdokumente (Upload nur admin)  |
| DELETE  | `/api/agents/{agentId}/documents/{docId}`    | Dokument löschen (nur admin)         |
