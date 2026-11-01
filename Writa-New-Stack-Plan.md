# Writa: Rewrite in Kotlin + Jetpack Compose (greenfield; the Java prototype is the reference)

Status: proposal, 2026-11-01. **Supersedes** the earlier incremental in-place migration plan, which assumed upgrade paths. Because the Java prototype has not been released, none of that is needed.

## 0. What changes because nothing has shipped

Dropped (the old plan spent real effort on these):
- Upgrade-in-place testing, "don't sign users out", Room migration chains from old versions. The new app starts at **Room schema version 1** with exported schemas. (Migrations are required from the first *released* version onward, so keep the rule from then.)
- Legacy handling: `security-crypto` prefs/master-key cleanup in `SecureStorage`, `Theme.fromStored` for old display-name values, keeping the `supabase_session_secure` exclusion entry, and any compatibility shims.
- Per-class Java/Kotlin interop (`@JvmStatic`, mixed builds), and the characterization-test phase for code that will not be ported.
- Big-bang vs. incremental risk: the old app keeps working untouched as the reference until the new one reaches parity.

Still binding:
- **The server contract.** `schema.sql`, PostgREST and GoTrue behavior, RLS, optional columns (`updated_at`, `pinned`, `deleted_at`, `tags`, `archived`, `client_id`), and users running older copies of the schema. This is the real compatibility surface, not the app.
- **Backup file format** (`version: 1`, optional `tags`/`pinned`/`archived`). Cheap to keep and it lets users move between builds.
- The security rules and the hard-won behaviors in the old CLAUDE.md "Gotchas" (Section 7 lists which to carry over).

## 0.5 Decided: this is a clean v1.0 (added 2026-11-01)

The three open decisions are resolved, and the new app ships as **1.0.0 (versionCode 1)** with no legacy anywhere:
- **DI:** Hilt.
- **Schema:** the app *requires* the full v1.0 `schema.sql` (all columns, including `client_id`). There is no `ServerFeatures`, no old-server fallback, no per-feature hiding. A missing column shows a single "run schema.sql" screen. This deletes most of the feature-detection branching and the direct-to-server edit path; every edit is local-first.
- **Remote sync:** full paged refresh in 1.0; incremental sync is post-1.0.
- **Room:** version 1, schema exported; migrations start with the first release after 1.0.
- **Repo name:** `Writa` (the old repo is tagged `java-legacy`).
- **Backup read/write:** stream both ways with no whole-file parsing. Writing uses `android.util.JsonWriter`; reading uses `android.util.JsonReader`, which accepts any key order, so `version` may come after `notes`. Validation (skip entries without a string title, reject `version` > 1 or a missing `notes` array, batches of 200) lives in pure Kotlin that takes already-read values, so it is tested on the JVM; the stream I/O is covered by instrumented round-trip tests (as the prototype did). kotlinx.serialization stays for network JSON only.
- **Contrast levels:** low (= Material standard), medium and high are complete Material 3 color schemes for light and dark, 46 roles each, six in total. They are converted from the prototype's Theme Builder output into `ColorSchemes.kt` (same folder), exposed as `colorSchemeFor(contrast, darkTheme)`. Dynamic color, when enabled on Android 12+, overrides them. The `Contrast` keys stay `contrast_low|medium|high`. Check text contrast on each scheme in the screenshot review.
- The new `CLAUDE.md` for the project is `CLAUDE.md` (same folder). Copy it to the new repo root as `CLAUDE.md`.
- Phase D therefore drops the "fallback if no `client_id` column" item, and Phase C drops `ServerFeatures` in favor of a schema check at first load.

## 1. Decisions to make first (with recommendations)

| Decision | Recommendation | Why |
|---|---|---|
| Where to build | New directory/repo, `Writa`, history starting fresh; tag the old repo `java-legacy` and keep it read-only | Clean Gradle setup, no half-migrated state; easy side-by-side reading |
| applicationId | New `com.mowtiie.writa` (+ `.debug`) | App renamed to Writa and nothing released, so no clash with the prototype; keep debug/release side by side |
| Structure | Single `:app` module, packages by feature (`feature/notes`, `feature/auth`, ...) plus `core/` (network, database, security, ui) | The app is small; multi-module is overhead now. Keep package boundaries clean so splitting later is cheap |
| UI | Compose, Material 3, **single Activity** + Navigation Compose (type-safe routes) | No legacy activities to preserve. `SetupActivity` no longer needs to be a separate exported launcher: one exported `MainActivity` |
| State | `ViewModel` + `StateFlow<UiState>` per screen, one-shot events via `Channel` | Standard, testable |
| Async | Coroutines + Flow end to end | No Handlers/Executors |
| DI | **Hilt** (KSP) | Decided. The prototype had no DI by choice; with ViewModels, coroutines and testing, compile-time-checked DI pays off now |
| Networking | Keep **OkHttp** (no Supabase SDK, same principle: tiny trusted surface) | The auth/redirect/token rules are your own and well tested |
| JSON | **kotlinx.serialization** (replaces `org.json`) with explicit nullable fields and `ignoreUnknownKeys` | Removes the `optString("null")` bug class; also testable on the JVM with no Android stubs. Keep R8 rules minimal (plugin generates serializers) |
| Local DB | Room (KSP) with `Flow` DAOs, as source of truth for the notes list | Previously just a cache; now the UI observes the DB |
| Preferences | **DataStore (Preferences)** for theme/contrast/sort/privacy; keep `ConnectionManager` values in a small encrypted or plain store (see 4.3) | Flow-based settings suit Compose; no SharedPreferences main-thread pitfalls |
| Background sync | Keep out of v1 (as before); design the sync engine so **WorkManager** can drive it later with no rewrite | Matches the old "out of scope" list; leaves an obvious extension |
| Min/target SDK | minSdk 29, targetSdk latest installed (36 at present) | Unchanged |
| Tests | JUnit4 + kotlinx-coroutines-test + Turbine + MockWebServer + Robolectric (only where needed); Compose UI tests; instrumented tests for Keystore/Room | ViewModels and UI are now testable by design |
| Language/tooling | Kotlin (current stable), AGP version-catalog managed, KSP, ktlint/Spotless, CRLF handling via `.editorconfig`/`.gitattributes` (old repo used CRLF) | |

Navigation is Navigation Compose with type-safe routes; DI is Hilt.

## 2. Target architecture

```
UI (Compose screens, stateless composables)
  -> ViewModel (StateFlow<UiState>, events)
    -> Repository (single source of truth: Room)
        -> NoteDao / Room (encrypted payload, same design as old cache)
        -> RemoteDataSource (OkHttp: PostgREST + GoTrue)
        -> SyncEngine (uploads dirty rows, merges server pages)
core: SessionStore, SecureStorage, ConnectionStore, NetworkMonitor, TokenAuthenticator
```

Key shift from the old app: **Room is the single source of truth.** The old design was "server list on screen + saved copy + patch from responses". The new design is "UI observes Room; refresh and sync write to Room". That removes `NoteLoader`'s special ordering, `loadGeneration`, "stale" banner plumbing (becomes a derived state), the `changeLocally/createLocal/deleteLocal` vs. direct-server split, and `skipNextResumeReload`. All of those collapse into: write locally, mark dirty, sync, observe.

### 2.1 Packages
```
com.mowtiie.writa
  WritaApp, MainActivity, WritaNavHost
  core/
    network/   HttpClients, TokenAuthenticator, ApiException, NetworkErrors, SchemaCheck
    database/  AppDatabase, NoteEntity, NoteDao, PendingChange, converters
    security/  SecureStorage, KeystoreNoteCrypto, SessionStore
    datastore/ SettingsStore, ConnectionStore
    ui/        theme (WritaTheme, color schemes), components, ImeActions
  feature/
    setup/     SetupScreen, SetupViewModel
    auth/      LoginScreen, AuthViewModel, AuthRepository
    notes/     NoteListScreen, NoteViewModel, NoteRepository, SyncEngine, NoteLoader-equivalent
    editor/    EditorScreen, EditorViewModel
    archive/   ArchiveScreen
    trash/     TrashScreen
    settings/  SettingsScreen, BackupFormat, export/import
    about/     AboutScreen
    crash/     CrashHandler, CrashReporter, CrashDialog
```

## 3. Phases

### Phase A: Spec extraction from the old project (2-3 days)
Turn the old code into a requirements list so the rewrite is not a line-by-line copy.
- Export the old CLAUDE.md to a "behavior spec" checklist (Section 7 does this already).
- From the old tests, list scenarios as the acceptance specs to re-implement: `NoteLoaderTest` (10), `NoteRepositoryTest` (27), `AuthRepositoryTest` (12), `TokenAuthenticatorTest`, `SyncEngineTest`, `RoomNoteCacheTest` (16) + offline test, `BackupFormatTest`, `SecureStorageTest`, plus the small ones.
- Capture UI references: screenshots of every screen in light/dark/high contrast, with and without notes, with banners and dialogs (so Compose parity is reviewable).
- Copy as data, not code: `schema.sql`, string resources (`strings.xml`, plurals, translations), icons/adaptive icons, launcher placeholders, `proguard` ideas, `data_extraction_rules.xml`.
- Re-run `schema.sql` review. Since this is a fresh start, it is the cheapest moment to fix schema warts, but anything changed must stay compatible with the old client only if you care about running both. Not verified against a live Postgres in the old project: verify now (run it against a scratch Supabase project).

### Phase B: Skeleton and foundations (1 week)
- New project: version catalog, Kotlin, KSP, Compose BOM, Hilt/Koin, serialization, coroutines, Room (schema export on), DataStore, OkHttp, test libs.
- `build.gradle.kts` parity: debug type with `BuildConfig.SUPABASE_URL/KEY` pre-fill from git-ignored `local.properties` (debug only; release empty), `keystore.properties` optional signing, different icons per build type via manifest placeholders, R8 + resource shrinking on release.
- Manifest: `allowBackup=false`, `fullBackupContent=false`, `data_extraction_rules.xml` (exclude session, connection, DB and DataStore files), no cleartext, single exported launcher activity.
- `WritaTheme`: Material 3 with system/battery/light/dark, three contrast levels, dynamic color; pass-through to `FLAG_SECURE` and Recents-thumbnail handling in `MainActivity` (observing the privacy setting; re-apply on `ON_RESUME` via lifecycle observer).
- Core components with tests: `SettingsStore`, `Validators`, `Tags`, `NetworkErrors`, `ImeActions` (keyboard Done and hardware Enter), `ApiException` (short JSON message, max 200 chars, never raw bodies).
- CI-style scripts: `testDebugUnitTest`, `lintDebug`, `assembleRelease`.

### Phase C: Security and network core (1-1.5 weeks)
Build and test before any UI:
- `SecureStorage` (AES-256-GCM, non-exportable Keystore key, per-entry AAD, undecryptable reads as absent) and `SessionStore` (in-memory cache as source of truth after first open, warm-up on a background dispatcher, recreate key once then memory-only fallback, never crash). Drop the legacy `security-crypto` cleanup.
- `HttpClients`: plain client and authed client, **both redirects disabled**. `TokenAuthenticator` (blocking, synchronized, give up after 2 responses, retry if stored token differs, only 400/401 on refresh clears the session, everything else `OfflineException`).
- `ConnectionStore`: base URL (trim, strip one trailing `/`, reject empty), anon key.
- `AuthRepository` (suspend): signUp/signIn/signOut (`/auth/v1/...`, plain client; signOut clears locally then best-effort logout), "Confirm email on" handling (no `access_token`).
- `NoteRemoteDataSource` (suspend, authed client): paged read `order=created_at.desc,id.desc&limit=200&offset=N`, create/patch/delete, `getNote(id)`, batched `bulkInsert` with `Prefer: return=minimal`, headers `apikey` + `Authorization: Bearer <access token>`. Never send `user_id`. IDs are `Long`.
- `SchemaCheck`: on the first read, a PostgREST error or a missing key in a non-empty `select=*` result means `schema.sql` was not run; surface it as a single "Update your database" state. There is no per-feature detection.
- Tests: port every scenario from the old repository/authenticator tests, now on `runTest` + MockWebServer. Instrumented: Keystore + session.

### Phase D: Local database and sync (1.5-2 weeks)
- `NoteEntity`: plain `id`, timestamps, flags (`pinned`, `archived`, `deleted_at`), `position`; encrypted `payload` (title, content, tags) via `KeystoreNoteCrypto` with the row id as AAD; offline fields `dirty_mask`, `pending_delete`, `sync_failed`, `base_updated_at`, `client_id`, `version`. Offline-created ids <= -2 (never -1). Cache owner = SHA-256(base URL + email); owner switch wipes before any read or write; sign out / change server clears.
- Decide whether the payload encryption stays. It does (the privacy design is a feature); the new twist is that title/content/tags are not queryable in SQL, so **search stays in memory** (pre-lowercased index built in the repository/ViewModel). Document this trade-off.
- `NoteDao`: `Flow<List<NoteEntity>>` for list, archive and trash views; transactions for merge, local edit, sync success (`version` compare), id alias table.
- `NoteRepository`: exposes `Flow<List<Note>>` (decrypt in a mapping step on `Dispatchers.Default`), `refresh()` (pages -> merge in one transaction: replace on last page, keep rows with local changes and local-only rows), and local mutations (create/update/pin/archive/trash/restore/delete) that write Room first.
- `SyncEngine`: single worker (one coroutine consuming a conflated trigger channel), triggered after each edit, on refresh, and on network return (`NetworkMonitor` flow). Preserve semantics: state-based (`dirty_mask` TEXT|TAGS|PINNED|ARCHIVED|TRASH); POST with `client_id` and 409 => lookup by `client_id`; text PATCH conditional on `updated_at=eq.<base>`, no match => conflict copy titled `... (conflict copy)` and server version taken for the original (deleted elsewhere => recreate); non-text changes win; transient errors (network, 5xx, 408, 429, 401) stop the pass; other 4xx mark `FAILED` with retry/discard. Every edit is local-first; the full v1.0 schema (including `client_id`) is required, so there is no direct-to-server path.
- Tests: port `NoteLoaderTest` scenarios as repository tests (saved data shown first, failure reported only after local data delivered, etc.), `SyncEngineTest`, `RoomNoteCacheTest` (in-memory Room + real Keystore, owner switch, ordering, encryption, AAD binding).

### Phase E: Compose UI (3-4 weeks)
Build in this order, each screen with ViewModel tests and a Compose UI test:
1. **Navigation shell + Setup**: URL (https, parseable) and key (visible ASCII) validation, probe `GET /auth/v1/settings` (2xx; 401/403 = bad key), errors on the field (`isError` + `supportingText`), Done/hardware Enter submit, obscured-touch filtering on the button, debug pre-fill. Start destination chosen from state: unconfigured => Setup, configured and logged out => Login, else Notes.
2. **Login**: sign in/up, lenient email shape check, password untrimmed, `Server: <host>`, Change server (clears connection and cache), title auto-sizing on one line, no mid-word breaks at 2.0x font scale.
3. **Note list**: LazyColumn with stable keys (long ids; offline ids; alias handling), pull-to-refresh, search with expand/collapse state in the ViewModel, sort (persist the enum name), tag filter bar (any-of, survives rotation), pinned first, "Showing saved notes" banner derived from sync/refresh state, swipe-to-delete with deferred commit and Undo (6 s), long-press menu with confirmation, TalkBack custom actions, scroll-to-top rule when the user was at the top, crash-report dialog on cold start.
4. **Editor**: title error on the field, tags with suggestions (`knownTags`, derived from the DB), overflow actions (Archive/Unarchive, Trash), save/delete **finish only after success** (now: after the local write succeeds; the upload is background), autofill and personalized learning disabled on fields, unsaved-changes back behavior as in the old app.
5. **Archive** and **Trash**: unarchive/restore/permanent delete, observe the DB (no reload-on-resume hack).
6. **Settings**: theme, contrast, dynamic color, screen privacy, export/import with SAF launchers (streaming; `kotlinx.serialization` streaming API or `JsonWriter`-style manual writer for low memory; import merges, batches of 200, `PartialImportException`), sign out / Change server with pending-changes warning (`pendingChangeCount`).
7. **About**, **Crash handler/reporter** (`files/last_crash.txt`, email/save).

Per-screen definition of done: matches the Phase A screenshots (or improves with the maintainer's OK); TalkBack pass; 2.0x font scale; dark and high contrast; landscape; RTL smoke; process-death restore of search/filter/editor draft (`rememberSaveable`/`SavedStateHandle`).

### Phase F: Hardening and release (1-1.5 weeks)
- R8 release build tested end to end (login, offline edit then reconnect, conflict copy, import/export, crash dialog, rotation). Minimal keep rules; `-dontwarn` okhttp/okio/conscrypt/bouncycastle/openjsse; strip `Log.d/v/i`; keep `SourceFile,LineNumberTable`; produce `mapping-<version>.txt`.
- Macrobenchmark/startup check on the light emulator (API 29 AOSP x86_64, ~1.5 GB RAM, 2 cores, 720x1560); baseline profile if startup is slow. Large-list scroll test (thousands of notes).
- Lint clean; security review pass with the rules in Section 6.
- README, new CLAUDE.md (rewrite it for the new stack, based on Section 6-7 of this plan), contributor docs, screenshots.
- First release as 1.0 (the Java app never shipped, so there is no versionCode continuity to preserve; still pick a `versionCode` and keystore plan once and never change the signing key).

## 4. Improvements the rewrite makes possible (optional, decide per item)

1. **Server-side search/sort/paging** instead of downloading the whole table: not possible with encrypted-at-rest payloads in the local DB for local search, but the *remote* refresh could use `updated_at` incremental sync (`updated_at=gt.<last sync>`) instead of re-downloading everything. Needs a tombstone strategy for deletes (trash already acts as one; permanent deletes would need a `deleted` log or periodic full refresh). Design this in Phase D or defer to v1.1.
2. **WorkManager** background upload (battery/network constraints) driven by the same `SyncEngine`. The old CLAUDE.md lists background sync as absent by intent: confirm you want it.
3. **Search index**: build the lowercase index incrementally from the Room Flow rather than rebuilding each emission.
4. **Single-activity "Setup" removal of the exported launcher activity** (smaller attack surface: only `MainActivity` is exported).
5. **Shared-element/predictive-back animations**, adaptive layouts for tablets/foldables (list-detail with `NavigableListDetailPaneScaffold`).
6. **Baseline profiles** and strong skipping tuned for the list.
Each is a separate, deferred decision. None is required for parity.

## 5. Parity checklist (done when all are ticked)

Auth and networking: setup probe, sign up/in/out, "check your email", token refresh rules, redirects disabled on both clients, no `Authenticator` on the plain client, never use the plain client for notes.
Data: tags rules, pinned, archived, trash (set `deleted_at`) with explicit permanent delete, edited indicator (updated >= created + 1 s), paging, export (skips trashed; includes archived), import merge, backups plaintext with a warning.
Offline: edits work offline, uploads on reconnect, conflict copies, FAILED notes with retry/discard, pending-change warnings on sign out / Change server, ids <= -2, `client_id` dedupe.
Privacy: encrypted DB payload, encrypted session, `FLAG_SECURE` option re-applied on resume, Recents thumbnail hidden on 13+, fields with autofill and personalization disabled, no sensitive logging, backups excluded.
UX: theme/contrast/dynamic color, sort persistence, search + tag filter, swipe undo (6 s), TalkBack actions, large-text safe titles, edge-to-edge with IME insets.

## 6. Rules to copy into the new CLAUDE.md (condensed from the old one)

- No Supabase SDK; plain OkHttp. Two clients, redirects off, `Authenticator` only on the authed client.
- Only the anon/publishable key client-side; never `service_role`. Never send `user_id`.
- Only HTTP 400/401 from refresh clears the session; other failures keep it.
- Never log tokens/passwords/full responses; never put raw bodies in exceptions/toasts; user-facing errors never show exception text (map to string resources).
- IDs are `Long`. Offline ids <= -2.
- `allowBackup=false`; sensitive stores excluded in `data_extraction_rules.xml`; cleartext off; setup requires https.
- Room: from the first release, every version bump needs a real Migration; no destructive fallback (the DB holds unsent user data).
- Strings in `strings.xml` (plurals for counts), no hardcoded user text; escape apostrophes/quotes.
- Schema changes go in `schema.sql` (idempotent) and the client must degrade gracefully.
- Release: R8 + shrinking, test release builds after adding libraries; publish the mapping file.

## 7. Old-project gotchas: which still apply

| Old gotcha | In the rewrite |
|---|---|
| `skipNextResumeReload` (double load on start) | Gone: UI observes Room, refresh triggered once by the ViewModel |
| `loadGeneration` drops stale loads | Replace with a cancelled refresh `Job` / `collectLatest`; keep a test |
| Deferred swipe-delete commit (stop, sign-out, another swipe, Undo) | **Keep** the behavior; implement in the ViewModel with a pending-delete state and timeout |
| Editor finishes only after success | Keep (success = local write) |
| `ConnectionManager.isConfigured()` rejects empty strings | Keep, with a test |
| Search expansion after rotation | Gone with Compose; state in ViewModel |
| Scroll-to-top after submitList | Re-implement in LazyColumn state |
| `optString` returns "null" | Gone with kotlinx.serialization (null fields explicit) |
| First Keystore read is slow; warm up in background | **Keep** |
| Form errors on the field, not `EditText.setError` | Equivalent: `supportingText` |
| Login title auto-size at 2.0x | **Keep** (test it) |
| Debug pre-fill via `BuildConfig` only in Setup | **Keep** |
| Session can be cleared by the authenticator while Main is open | Observe `SessionStore` as a Flow and navigate to Login when it becomes empty (cleaner) |
| `getApplication()` before attach | N/A with DI/constructor injection |
| `SetupActivity` only exported activity | Single exported `MainActivity` |

## 8. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Rewriting subtle sync/auth logic loses hard-won edge cases | Port the old tests as acceptance specs first (Phase A), then implement until green; review each against the old code once |
| Scope creep (improvements in Section 4) | Parity first; improvements only after the Section 5 checklist is complete |
| Two codebases to maintain meanwhile | Freeze the Java app except for critical bugs; if the schema changes, change it in `schema.sql` once and in the new app only |
| Compose a11y/large-text regressions | Per-screen checklist, TalkBack, 2.0x font scale, tests with semantics |
| Encrypted payload makes DB queries limited | Accept: search in memory; test with thousands of notes; keep decrypt off the main thread |
| Tooling churn (AGP, KSP, Compose compiler, Hilt) | Pin versions via the catalog; upgrade one at a time |
| Motivation drop in a long rewrite | Milestones that run end to end early: after Phase C+D with a bare-bones list screen, you can already sign in and sync |

## 9. Estimate (one developer, part-time, rough)

| Phase | Time |
|---|---|
| A Spec extraction | 2-3 days |
| B Skeleton | ~1 week |
| C Security + network | 1-1.5 weeks |
| D Database + sync | 1.5-2 weeks |
| E Compose UI | 3-4 weeks |
| F Hardening + release | 1-1.5 weeks |
| **Total** | **~8-11 weeks** |

Similar total to the incremental plan, but with a cleaner result (no legacy shims, Room as source of truth, testable ViewModels from day one) and zero upgrade-path work.

## 10. First actions (week 1)

1. Tag the old repo (`java-legacy`), create the new project, commit the Gradle/catalog setup and an empty themed Compose app that builds in debug and release.
2. Write the Phase A behavior spec + test-scenario list from the old tests; take the reference screenshots.
3. Implement `SettingsStore`, `Validators`, `Tags`, `ApiException`, `NetworkErrors` with tests (all pure Kotlin, fast wins).
4. Implement `SecureStorage` + `SessionStore` with instrumented tests (the riskiest low-level piece; get it done early).
5. Decisions already made (Section 0.5): Hilt, required full schema, full paged refresh in 1.0 (incremental sync after 1.0). Nothing is left to decide before starting.
