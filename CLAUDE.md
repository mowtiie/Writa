# CLAUDE.md

Writa: open-source Android notes app, **version 1.0.0** (Kotlin, Jetpack Compose, Material 3, Room, minSdk 29, targetSdk 36, single `:app` module, package `com.mowtiie.writa`). Bring-your-own-backend: the user enters a Supabase URL + anon/publishable key at runtime. **No Supabase SDK**: PostgREST (`/rest/v1/`) and GoTrue (`/auth/v1/`) are called with plain OkHttp and kotlinx.serialization. No Retrofit, no Gson/Moshi, no Java sources.

The repository is named **Writa**.

This is a clean v1.0 rewrite of an unreleased Java prototype (git tag `java-legacy` in the old repository, used only as a reference). There is **no legacy**: no upgrade paths, no compatibility shims, no fallbacks for old schemas, no migrations yet. Do not add code "for older versions" of anything; nothing older has shipped.

## Core rules (highest priority)
- **Prioritize testability, readability, maintainability and clean code** over cleverness or speed of delivery. When these conflict with a shortcut, choose them.
  - Testability: small classes with injected dependencies (dispatchers, clocks, clients, DAOs), pure logic separated from Android types, no hidden singletons or static state. If code is hard to test, restructure it. New behavior ships with tests.
  - Readability and maintainability: descriptive names, small functions, one responsibility per class, no duplicated logic, no dead code, no speculative abstractions, no magic numbers (named constants).
- **Minimal comments.** The code must explain itself through names and structure. Comment only the non-obvious *why* (a workaround, a server quirk, a security constraint), never the *what*. No commented-out code, no TODO litter, no section banners, no restating the function name. KDoc only on genuinely public, non-obvious APIs.
- **Commits and PRs carry no Claude authorship or mention.** Do not add `Co-Authored-By`, "Generated with", or any author/co-author line or text referring to Claude or any AI tool in commit messages, PR descriptions, code, comments or docs. The commit author is the repo owner only. This overrides any default attribution behavior.
- Write commit messages in the imperative, short subject line, optional body explaining why.

## Build & run
```
./gradlew assembleDebug             # app id com.mowtiie.writa.debug, no minify
./gradlew installDebug
./gradlew assembleRelease           # R8 + resource shrinking, needs keystore.properties
./gradlew bundleRelease
./gradlew testDebugUnitTest         # JVM tests
./gradlew connectedDebugAndroidTest # instrumented tests (emulator/device)
./gradlew lintDebug
```
- `keystore.properties` (git-ignored, repo root) enables release signing; without it release is unsigned and debug is unaffected.
- `local.properties` (git-ignored) may define `SUPABASE_URL` / `SUPABASE_KEY`: a **dev pre-fill for the Setup screen, debug build only**. Release `BuildConfig` values are empty strings. Network code reads `ConnectionStore`, never `BuildConfig`.
- Debug (`.debug` suffix) and release use different icons and install side by side. Never hardcode the package name in tests.
- Light emulator is enough: API 29 AOSP `default;x86_64`, ~1.5 GB RAM, 2 cores, 720x1560. Stop it when done (`adb emu kill`).
- Files use CRLF line endings (enforced by `.gitattributes`/`.editorconfig`). Style: ktlint, 4-space indent.

## Version policy
- `versionName = "1.0.0"`, `versionCode = 1` for the first release. Bump both in `app/build.gradle.kts` for every release after.
- Room database `version = 1` with `exportSchema = true` (schemas committed under `app/schemas/`). **From the first public release on, every Room version bump needs a real `Migration` plus a migration test; never use destructive fallback** (the DB holds unsent user data).
- The signing key is created once and never replaced.

## Architecture (MVVM, unidirectional data flow, Hilt)
`Composable screen -> ViewModel (StateFlow<UiState> + one-shot events) -> Repository -> Room (source of truth) / OkHttp`.

**Room is the single source of truth for notes.** The UI observes the database; refresh and sync write into it. Never put a server response straight into UI state.

Packages:
```
com.mowtiie.writa
  WritaApp            Hilt application; installs CrashHandler
  MainActivity           the only activity, the only exported component; hosts NavHost
  navigation/            type-safe routes, start-destination logic
  core/network/          HttpClients, TokenAuthenticator, ApiException, NetworkErrors, NetworkMonitor
  core/database/         AppDatabase, NoteEntity, NoteDao, PendingChange, id alias table
  core/security/         SecureStorage, KeystoreNoteCrypto, SessionStore
  core/datastore/        SettingsStore (DataStore Preferences), ConnectionStore
  core/ui/               WritaTheme, color schemes, shared components, ImeActions
  feature/setup|auth|notes|editor|archive|trash|settings|about|crash/
```

- **Hilt** wires everything (KSP). ViewModels use `@HiltViewModel`. Singletons: `SessionStore`, `ConnectionStore`, both OkHttp clients, `AppDatabase`, `SyncEngine`, `NetworkMonitor`.
- **Coroutines/Flow everywhere.** No `Handler`, no `Executor`, no callbacks, no `LiveData`, no `postValue`. Repositories expose `suspend` functions and `Flow`. Inject dispatchers (`@IoDispatcher`, `@DefaultDispatcher`) so tests can replace them. Blocking OkHttp calls run on IO; `TokenAuthenticator` is the one deliberately blocking class (OkHttp requires it).
- **UiState** is one immutable data class (or sealed interface) per screen, exposed as `StateFlow`; one-shot events (snackbar, navigation, "saved") go through a `Channel` collected with lifecycle awareness. Errors are state that the screen clears after display.
- Screens are stateless composables taking state + lambdas; state hoisting is the rule. Anything that must survive rotation/process death uses `SavedStateHandle` or `rememberSaveable` (search query, selected tags, editor draft).

## Auth & networking
- Two shared OkHttp clients, **both with redirects disabled** (`followRedirects(false)`, `followSslRedirects(false)`): the plain client (setup probe, auth endpoints, token refresh) and the authed client (same config + `TokenAuthenticator`; all `/rest/v1/` calls). Never put an `Authenticator` on the plain client, never use the plain client for notes, never re-enable redirects (they could replay the refresh token or `apikey` to another host or downgrade to http).
- REST calls need `apikey: <anon key>` and `Authorization: Bearer <user access token>`. Auth endpoints need only `apikey`. The anon key is **not** a bearer token.
- `TokenAuthenticator.authenticate`: gives up after 2 responses; synchronized; if the stored token already differs from the one that failed, retries with it; otherwise POSTs the `refresh_token` grant through the plain client. **Only HTTP 400/401 from the refresh call clears the session.** Network errors, non-2xx, HTML/malformed bodies, a missing `access_token`, and an unconfigured connection throw `OfflineException` and keep the session.
- API failures are `ApiException` (status code + short JSON `message`, max 200 chars). Never put raw response bodies in exceptions, toasts or logs (PostgREST `details` can echo row contents).
- User-facing network errors never show exception text: `NetworkErrors` classifies connectivity/TLS failures; ViewModels map them to string resources.
- JSON uses kotlinx.serialization with explicit nullable fields (`content: String?`, `updatedAt: String?`, ...) and `ignoreUnknownKeys = true`. No `org.json`.
- Sign-out and Change server both: clear the session locally, then best-effort `POST /auth/v1/logout`, and clear cached notes (after warning about unsent changes).
- The session store is a `Flow`; when it becomes empty (e.g. refresh rejected) the NavHost navigates to Login. No per-screen `isLoggedIn()` re-checks.

## Data model & server schema
`schema.sql` (run once by the user in their Supabase project) is the **single, complete v1.0 schema**, idempotent (`if not exists`, `drop ... if exists`) so re-running is safe:
`notes(id bigint generated always as identity, user_id uuid not null default auth.uid() references auth.users on delete cascade, title text not null, content text, tags text[] not null default '{}', pinned boolean not null default false, archived boolean not null default false, created_at timestamptz default now(), updated_at timestamptz, deleted_at timestamptz, client_id uuid)`, a unique index on `client_id`, a trigger that sets `updated_at` only when title or content change, and four per-operation RLS policies (`auth.uid() = user_id`) for `authenticated`.
- The app **requires** this schema. There is no feature detection, no `ServerFeatures`, and no "old server" code path. If the first read shows a missing column (PostgREST error or missing key in a non-empty `select=*`), show a dedicated "Update your database: run schema.sql" screen with a copy button, and do nothing else until the user re-checks.
- IDs are `bigint` -> **`Long`**, interpolated into `?id=eq.<id>`. Never `Int`.
- **Never send `user_id`**; the server fills it from the JWT. Never embed `service_role` or any secret key.
- Verify `schema.sql` against a real Supabase project before release (the old prototype never did).
- Account creation relies on the email provider; "Confirm email" on means signup returns no `access_token` (show a "check your email" message).
- Paging: `order=created_at.desc,id.desc&limit=200&offset=N`. The whole table is downloaded in pages on each refresh (incremental sync is deliberately a post-1.0 item).

### Notes, tags, pin, archive, trash
- `Note` is a Kotlin data class: `id: Long`, `title`, `content: String?`, `tags: List<String>`, `pinned`, `archived`, `createdAt`, `updatedAt: String?`, `deletedAt: String?`. `isEdited` = `updatedAt` later than `createdAt` by at least one second. `isTrashed` = `deletedAt != null`.
- Tag rules live in one place (`Tags`): trim, collapse spaces, strip leading `#`, 30 chars max, 10 per note, case-insensitive, first spelling kept. The list filters on the device: a note matches if it has ANY selected tag (selection survives rotation, not cold starts); search also matches tags. No tag-management screen on purpose: a tag disappears with its last note. Suggestions in the editor come from the tags of existing notes.
- Archived notes are hidden from the main list (filter in the repository query, so search/tags/sort ignore them) and shown in the Archive screen. Trashed notes are shown only in Trash. Moving to trash sets `deleted_at`; Trash is never purged automatically; permanent delete is explicit. Pinned notes sort first.
- Export skips trashed notes and includes archived ones.

## Local database & offline editing
- `AppDatabase` (`writa.db`): one row per note the server sent, including trashed and archived. Title, content and tags are encrypted together in one `payload` column by `KeystoreNoteCrypto` (AES-GCM, key alias `writa_cache_key`, **row id as AAD**); ids, timestamps, flags and `position` (server order) are plain. Consequence: text cannot be searched in SQL, so search runs in memory over a pre-lowercased index built off the Flow, off the main thread.
- The DB is owned by one account+server (owner = SHA-256 of base URL + email). A different owner wipes it **before any read or write**; sign-out / Change server clear it.
- Edits are written to the DB first and uploaded by the app-wide `SyncEngine` (single worker coroutine consuming a conflated trigger channel), triggered after each edit, after each refresh, and when `NetworkMonitor` reports the network back. Uploads happen only while the app process is alive (no background sync in 1.0; design the engine so WorkManager can drive it later).
- State-based, not an operation log. Each row has `dirty_mask` (`TEXT|TAGS|PINNED|ARCHIVED|TRASH`), `pending_delete`, `sync_failed`, `base_updated_at`, `client_id`, `version`.
- Notes created offline get ids <= -2 (never -1, which means "no note" in the editor) and a `client_id` uuid, so a retried POST cannot duplicate: on 409, look up by `client_id`.
- Text edits are PATCHed with `updated_at=eq.<base>`. No match = conflict: upload the user's text as a new note titled `... (conflict copy)` and take the server version for the original (original deleted elsewhere: re-create the edit). Non-text changes (pin, archive, trash) win.
- Transient problems (network, 5xx, 408, 429, 401) stop the pass; other 4xx mark that note `FAILED` (long-press: retry or discard).
- `mergeServerNotes` keeps rows with local changes and local-only rows, replaces the rest. `syncSucceeded` compares `version`, so an edit made during upload is kept, and records an `id_alias` so open screens holding a temporary id keep working.
- `WritaApp`/repository exposes `pendingChangeCount`; sign out / Change server warn about unsent changes.
- The "Showing saved notes" banner is derived state (last refresh failed or still loading while data came from the DB), not a separate code path.
- Excluded from backup and device transfer in `data_extraction_rules.xml`.

## Theming
- `WritaTheme` (material 3) picks `colorSchemeFor(contrast, darkTheme)` from `ColorSchemes.kt`: six full schemes (light/dark x low/medium/high), generated with the Material Theme Builder. Dynamic color (Android 12+) overrides them when enabled. Do not hand-edit individual colors; regenerate the set.
- `theme` values: `system|battery|light|dark`. `contrast` values: `contrast_low|contrast_medium|contrast_high` (low = standard).

## Preferences & storage
- `SettingsStore` (DataStore Preferences): `theme` (`system|battery|light|dark`, an enum with stable keys), `contrast` (`contrast_low|medium|high`), `dynamic_color`, `screen_privacy`, `sort_mode` (the `Sort` enum name). Keep keys and values stable once released.
- `ConnectionStore`: base URL and anon key; `save()` trims and strips one trailing `/`; `isConfigured()` rejects null **and** empty strings; `clear()` is used by Change server.
- `SecureStorage`: AES-256-GCM with a non-exportable Android Keystore key; each value encrypted separately and bound to its entry name (GCM associated data); undecryptable values read as absent.
- `SessionStore`: access token, refresh token, email in `SecureStorage` with an in-memory cache as the source of truth after first open. If the keystore is unusable it recreates the key once (signing the user out), then falls back to a memory-only session. Never crashes startup. The first Keystore open is slow: warm it on a background dispatcher at startup; callers wait on the same lock/mutex.

## Screens (Compose, Material 3)
Single `NavHost` in `MainActivity`; the start destination is computed from state: not configured -> Setup; configured, no session -> Login; else Notes.
- **Setup**: validates URL (https, parseable) and key (visible ASCII), probes `GET /auth/v1/settings` (must be 2xx; 401/403 = bad key), saves via `ConnectionStore`.
- **Login**: sign in / sign up; password not trimmed; lenient email shape check (`Validators.isPlausibleEmail`); shows `Server: <host>`; Change server button.
- **Notes**: list (`LazyColumn`, stable keys), pull-to-refresh, search, sort, tag filter bar, swipe-to-delete with deferred commit + Undo, long-press menu (with confirmation) with TalkBack custom actions, crash-report dialog on cold start, "Showing saved notes" banner.
- **Editor**: create/edit one note; only the note id travels in the route (never the note body). Title errors go on the field (`isError` + `supportingText`).
- **Archive**, **Trash**, **Settings** (theme/contrast/dynamic color/screen privacy, export/import, Change server, sign out), **About**.
- **Crash**: `CrashHandler` writes `files/last_crash.txt` on uncaught exception; `MainActivity` offers email/save on the next cold start.

## Export / import
- `BackupFormat` streams in both directions: `android.util.JsonWriter` to write, `android.util.JsonReader` to read (any key order, so `version` may follow `notes`). Validation and mapping are pure Kotlin on already-read values and are JVM-tested; stream I/O is covered by instrumented round-trip tests. Format: `{ "version": 1, "exportedAt": <epoch ms>, "notes": [ { "title", "content", "createdAt", "tags"?, "pinned"?, "archived"? } ] }`. No ids exported.
- Import skips entries without a string title, rejects files with `version` > 1 or no `notes` array, confirms, then inserts in batches of 200 (`Prefer: return=minimal`); a failure after some batches raises `PartialImportException`. It is a **merge**: always inserts new rows (duplicates on re-import), never deletes, ignores `createdAt`. Send pinned/tags/archived uniformly or not at all per batch. Backups are plaintext (say so in the UI). Import and export are online-only in 1.0.

## Conventions
- Kotlin official style; ktlint enforced. 4-space indent; no `m` prefix; no `!!` outside tests; prefer `val`, immutable data classes, sealed types for state/events.
- Strings live in `res/values/strings.xml` (plurals for counts); **no hardcoded user-facing text** in code or composables. Escape apostrophes (`\'`) and literal quotes (`\"`) in XML.
- Forms submit on the keyboard Done key **and** a hardware Enter key: use `ImeActions.isSubmit(actionId/event)`; do not rely on `ImeAction.Done` alone.
- Edge-to-edge: `enableEdgeToEdge()` in `MainActivity`; apply system-bar and IME insets on every screen (forms with scrolling content must include IME insets).
- Large-text safe: the Login title stays on one line with auto-size and never breaks mid-word at 2.0x font scale. Check every new headline at 2.0x.
- Accessibility: content descriptions on icons, 48 dp minimum targets, headings marked, errors announced, custom actions for gesture-only features (swipe delete).
- Note text fields disable autofill and personalized keyboard learning; setup/login buttons filter obscured touches.
- Stable list keys: use the row id (long), and respect `id_alias` so a note keeps its key and scroll position when its temporary id becomes a server id.
- Do not use `GlobalScope`; coroutines are scoped to a ViewModel, the `SyncEngine` scope, or the application scope. Cancellation must be safe: a cancelled load never half-writes (use Room transactions).

## Gotchas to preserve (learned in the prototype)
- Swipe-to-delete is deferred: the note is hidden locally and the trash/delete commits only when the Undo snackbar closes, another swipe happens, the user signs out, or the screen stops. Undo = cancel the pending delete. Undo duration is 6 s (`UNDO_DURATION_MS`); the default `SnackbarDuration.Long` is too short, so drive the timeout yourself.
- Editor save and delete close the screen **only after** the local write succeeds; a failure keeps the screen open with an error.
- Scroll to the top after a list change when the user was already at the top (notes inserted above, e.g. after import, must not leave the list anchored to an old note).
- Cache owner switch must wipe before any read or write.
- Never trust JSON null as a string: explicit nullable fields only.
- R8 + `isShrinkResources` on release: test release builds after adding reflection or libraries.

## Tests
- **JVM (`src/test`)**: `kotlinx-coroutines-test` (`runTest`), Turbine for Flows, MockWebServer for repositories/authenticator/auth, Mockito or fakes for dependencies. Required coverage before 1.0: `TokenAuthenticator` (refresh, rejection, captive-portal HTML, 5xx, redirects, race, missing token), `AuthRepository`, `NoteRemoteDataSource`/`NoteRepository` (request shapes, paging, parsing incl. nulls, errors, import batching and key matching), `SyncEngine` (offline create, 409 by `client_id`, conflict copy, deleted elsewhere, transient vs permanent errors, version race, alias), loader/refresh ordering, ViewModels (load/refresh cancellation, deferred delete, filters, search, error mapping), `Tags`, `Validators`, `ImeActions`, `NetworkErrors`, `ApiException`, `ConnectionStore`.
- **Instrumented (`src/androidTest`)**: `SecureStorage` + `SessionStore` (real Keystore), Room DAO + `KeystoreNoteCrypto` (owner switch, ordering, encryption, AAD binding), `BackupFormat` streaming, Compose UI tests for each screen (semantics-based, no hardcoded text or package names) and an end-to-end smoke flow.
- After changing `TokenAuthenticator`, `ApiException`, `ConnectionStore`, `SecureStorage`/`SessionStore`, `SyncEngine`, the DAO/crypto or `BackupFormat`, run the matching tests.
- Do not claim coverage that does not exist. Verify UI behavior by running the app when a change is not covered.

## Security rules
- Only the anon/publishable key is client-side. Never log tokens, passwords, or full auth/API responses; `Log.d/v/i` are stripped in release, `Log.e/w` are not.
- Keep `allowBackup="false"` and `fullBackupContent="false"`; `data_extraction_rules.xml` excludes the DB, the session and connection stores, and DataStore files. Add any new sensitive store there.
- Cleartext HTTP is off (no `usesCleartextTraffic`, no network security config); Setup requires `https://`. Scope any exception to a specific domain.
- Screen privacy (`FLAG_SECURE`) is an opt-in setting, re-applied on every resume; the Recents thumbnail is always hidden on Android 13+ (`setRecentsScreenshotEnabled(false)`). Do not remove either.
- Exactly one exported component: `MainActivity` (launcher).

## Out of scope for 1.0
Folders, background sync, E2EE, rich text, realtime, OAuth/magic link/password reset, incremental (delta) server sync, server-side search/sort, tablet/foldable layouts, v3 APK signing. The server is the source of truth; the device keeps an encrypted copy plus unsent changes. Do not assume these exist and do not add them without a decision.

## Release process
Bump version, run `testDebugUnitTest`, `connectedDebugAndroidTest`, `lintDebug`, build `assembleRelease` with the release keystore, smoke-test the release APK on a device (sign in, offline edit then reconnect, import/export, rotation, crash dialog). Publish `mapping-<version>.txt` from `app/build/outputs/mapping/release/` with the APK; optional detached PGP `.asc`. Details live in the README ("Verifying a download", "Building from Source", "Releasing (maintainers)"); don't duplicate them here.
