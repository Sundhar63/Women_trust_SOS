# AGENTS.md

Two independent subprojects, no root build or package manifest:

- `WomenTrust/` — Android app (Java, Gradle Kotlin DSL)
- `python_backend/` — FastAPI + SQLAlchemy + PostgreSQL backend
- `__MACOSX/` — archive junk, ignore it

There is no README, CI, lint config, or `.env.example`.

## Android app (`WomenTrust/`)

Run Gradle from `WomenTrust/`, not the repo root:

```bat
cd WomenTrust
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleRelease
```

- Java only (no Kotlin, no Compose); `viewBinding = true`.
- `local.properties` is gitignored and absent. Set `sdk.dir` or `ANDROID_HOME` before building.
- `namespace` is `com.foxsite.womentrust` but `applicationId` is `com.foxsite.womentrust.v2`. Register external SDKs/console apps against the **applicationId**.
- Release signing uses the committed `app/womentrust.jks` with plaintext credentials in `app/build.gradle.kts`. Do not rotate or "clean up" without asking.
- `compileSdk`/`targetSdk` 35, `minSdk` 24, Java 11.
- UI flow: `SplashScreen` → `LanguageSelectActivity` → `LoginActivity` / `RegisterActivity` → `MainActivity` → `AbuseActivity`. Activities call Retrofit directly; there is no DI, ViewModel, or repository layer.
- Session state lives in SharedPreferences `WomenTrust_Shared`; keys come from string resources (`userId`, `username`, `role`, `login_status`) plus a raw `jwt_token` key.
- API base URL is hardcoded in `api_services/RetrofitClient.java`. `res/xml/network_security_config.xml` lists different/stale Vercel hosts, and cleartext traffic is globally allowed.
- Bilingual UI: `values/strings.xml` + `values-ta/strings.xml`. Use `LanguageHelper.getLanguageCode()` and send `ln` (`en`/`ta`) with requests.
- Tests are only the generated `ExampleUnitTest`; there is no meaningful test suite.

## Backend (`python_backend/`)

```bat
cd python_backend
docker compose up --build          # Postgres on host 5433, API on 8000
uvicorn main:app --reload --port 8000
python seed_db.py                  # seeds violence master data
```

- Entry point is `main:app`; `app.py` is a Vercel re-export (`from main import app`).
- `database.py` reads `DB_CONNECTION_STRING` at import and crashes if missing. Required env: `DB_CONNECTION_STRING`, `JWT_SECRET_KEY`, `JWT_ALGORITHM`, `SPEECHMATICS_API_KEY`, `GROQ_API_KEY`, `OPENROUTER_API_KEY`.
- Tables are created by `Base.metadata.create_all()` in `main.py`. There is no Alembic/migrations — model changes require a manual migration or dropping the DB.
- The API is RPC-style, not REST: every action posts to the same `POST /api/API/` with an `Actions` discriminator in the body. Add a new action as an `elif event.Actions == "..."` branch in `routers/api.py` and any new input fields to `schemas.Event`.
- Responses are a JSON **array** with one object, e.g. `[{"Status": "Success", ...}]`. Response keys intentionally preserve odd casing such as `LOGIN_MASTERs`, `vIOLENCE_MASTERs`, `aBUSE_CONTACT_DETAILS_s`.
- Only specific fields are translated server-side: when `event.ln == "ta"`, `VIOLENCE`, `SUB_VIOLENCE`, `CATEGORY`, and `SUB_CATEGORY` are swapped for their `TAMIL_*` columns.
- Audio is exposed twice: `/api/API/ProcessAudio` and `/api/Voice/ProcessAudio`. `python_backend/voice.py` is a dead duplicate of `routers/voice.py`; only the router copy is imported.
- `test_speech.py` is a manual Speechmatics script needing a key and `sample.wav`, not pytest.
- `translate_db.py`, `fix_tamil.py`, and `fix_tamil_labels.py` mutate the database. Run them deliberately.

## Security / auth gotchas

- Login issues a JWT, but `POST /api/API/` never verifies it. `AuthInterceptor.java` is unused; the app calls `getApiService("")` everywhere. Do not assume endpoints are protected.
- `AADHAR_NO` is stored and returned in plaintext. Registration only checks length >= 12. Any Aadhaar/KYC work must add server-side validation and avoid echoing the number back.
- `LoginMaster` rows are inserted with `MASTER_STATUS="Active"` regardless of the client-sent status.
- CORS is `allow_origins=["*"]` with credentials enabled.
