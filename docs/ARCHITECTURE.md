# NABHAS AI architecture — audit baseline and roadmap

## Audit baseline

यह योजना `main` merge commit `3ec24f6` के source review पर आधारित है। Repository एक `:app` Android module है। Compose chat/settings, OpenAI-compatible endpoint और fallback, चार built-in prompt skills, सीमित intents/Accessibility actions, one-shot voice/TTS तथा GitHub release updater मौजूद हैं। Audit source-only था; सभी device behaviours सत्यापित नहीं किए गए।

मुख्य gaps: `SharedPreferences` में API key; पहले BODY-level HTTP logger (अब Phase 1 change में हटाया गया); persistent chat/project memory का अभाव; action-shaped assistant output की स्वतः execution; skill prompts/parser का आंशिक mismatch; missing tests/README/wrapper; English hardcoded UI; खाली `OverlayService`; और Screen Reader prompt के लिए वास्तविक screen-content handoff का अभाव।

Tracked application code को सुरक्षित रखते हुए बदलाव छोटे commits में होंगे। `my-ai-project/myenv` के tracked residue को बिना अलग review/confirmation delete नहीं किया जाएगा; `.gitignore` केवल आगे generated files को रोकता है।

## Target package boundaries

शुरुआती चरण में सभी boundaries `:app` के भीतर रहेंगी। पहले testable seams स्थिर करें, उसके बाद ही Gradle modules अलग करने पर विचार करें। Dependencies एक दिशा में रहें:

```text
ui/                         Compose screens, state rendering, resources
  -> domain/                 typed models, use-cases, provider/action contracts
       <- data/api/           Retrofit implementations and API DTO mapping
       <- platform/voice/     SpeechRecognizer and TextToSpeech adapters
       <- platform/accessibility/ AccessibilityService and guarded UI actions
agent/                       built-in skill metadata, routing and action schema
update/                      release metadata, download and install state
core/                        shared configuration, validation and logging policy
```

Planned Kotlin root package: `com.gopal.projects` with `domain.model`, `domain.chat`, `data.api`, `agent`, `platform.voice`, `platform.accessibility`, `ui`, and `update` subpackages. `ChatViewModel` should orchestrate user intent/state and depend on interfaces; provider and Android-specific implementations stay outside domain. Unimplemented MCP, file, remote-skill and provider implementations must not be represented as working UI/API controls.

## `com.nabhas.ai` → `com.gopal.projects` migration

1. **Identity decision:** keep `applicationId = "com.nabhas.ai"` and signing identity unchanged while packages and namespace are refactored. Do not change the app identity as part of the initial source-package migration.
2. **Inventory:** search source declarations/imports, generated `R`/`BuildConfig` references, manifest component names, test packages, provider authorities, deep links, update metadata and workflow/release asset references. The current FileProvider authority already derives from `${applicationId}`.
3. **Namespace/package refactor:** change Gradle `namespace` and Kotlin packages/directories in small steps to `com.gopal.projects`; update imports and manifest class references; build/test/install an APK. Keep `applicationId=com.nabhas.ai` so Android can recognize an update to the existing app, subject to the same signing key and increasing version code.
4. **Compatibility verification:** install the old signed app, preserve settings/data, then update it with the new namespace build. Run updater and Accessibility component checks. A debug-only build is not proof of release signing compatibility.
5. **Optional applicationId change:** changing to `com.gopal.projects` creates a separate Android package/app identity; Android will not perform an in-place upgrade or automatically transfer app data. Only do this as a separately approved release plan with user notice, opt-in export/import, release signing, coexistence/rollback and updater/store migration. Same signing key alone does not make a changed applicationId a seamless upgrade.

## Phased roadmap

### Phase 1 — Reproducible foundation and repository hygiene
- Add a pinned Gradle wrapper; document JDK/SDK/build commands and actual feature limits.
- Run tests, lint and debug APK build on every branch push, PR and manual dispatch. Upload the debug APK each run; keep publish permissions only on a gated release job.
- Add `.gitignore` for build output, local SDK/config and Python virtualenvs without silently untracking existing files.
- Add baseline unit tests for deterministic domain policies and keep current app identity.

### Phase 2 — Architecture seams and typed contracts
- Separate provider-independent chat/settings/result models from Retrofit DTOs.
- Introduce `ChatCompletionGateway` and a concrete Retrofit adapter; keep current endpoint/model behaviour behind the interface.
- Move routing/fallback decisions into pure domain code with tests; make ViewModel construct/use a gateway through a compatible Android ViewModel entry point.
- Inventory action and voice contracts; do not claim a capability until a real implementation exists. Preserve chat/settings/voice/updater behaviour except for narrowly verified bug/security fixes.

### Phase 3 — Core AI engine and memory
- Implement provider registry/configuration for OpenAI-compatible endpoints and dedicated Gemini/Claude/Ollama adapters; secrets remain external to source.
- Add typed provider errors, bounded fallback/retry, timeouts, model capability metadata and user-selectable models.
- Add explicit conversation/project memory store, bounded context and privacy/clear/export policy.
- Create plan/tool-selection pipeline with typed intermediate results, cancellation and observable outcomes.

### Phase 4 — Skills, connectors, MCP, GitHub and files
- Define versioned skill metadata, capability requirements, install/enable/disable/remove policy; begin with trusted built-ins.
- Add universal API connector and MCP client abstractions with transport/auth/scope boundaries before concrete integrations.
- Implement GitHub repository/PR/issue/workflow adapters behind explicit permission scopes.
- Add file/document adapters for PDF, TXT, MD, JSON, CSV and code with picker/SAF permissions, bounded read/write and user-visible provenance.

### Phase 5 — Voice and safe Android automation
- Make voice lifecycle/user consent visible; preserve transcript before send and stop/release on app lifecycle changes.
- Parse only strict allowlisted action envelopes; require confirmation for impactful actions and report structured success/failure.
- Limit Accessibility scope to necessary content; separate `Intent` actions from Accessibility actions and test denied/unavailable states.

### Phase 6 — UI and localization foundation
- Add approved dashboard/chat/voice/model/API/skills/GitHub/MCP/memory/files/history/settings/security/developer destinations only as their real backends exist.
- Move user-facing strings to resources; English default plus Hindi and Punjabi locale resources.
- Expose permissions, enabled services, errors and action outcomes accessibly.

### Phase 7 — Credential and data security
- Store secrets with Android Keystore-backed encryption; define key rotation, deletion, backup/data-extraction exclusions and migration.
- Validate HTTPS/endpoint hosts; never log credentials, prompts or sensitive outputs.
- Apply input/output safeguards, least-privilege permissions and explicit data retention/export/clear policy.

### Phase 8 — Build and release integrity
- Keep CI debug artifacts separate from signed release APKs.
- Use protected signing secrets, immutable/versioned release tags, checksums/signatures and least-privilege release permissions.
- Verify updater package identity, version, artifact integrity, redirects and actual installation state.

### Phase 9 — Verification and regression coverage
- Unit tests for provider mapping, fallback, memory, routing, action parsing, persistence and error/cancellation paths.
- Compose/UI and instrumentation tests for chat, permissions, voice, Accessibility, updater and package upgrade.
- Add lint/static/dependency/security checks; test a documented Android/API/device matrix.

### Phase 10 — Staged modularization and release readiness
- Extract `:domain`, `:data`, `:platform` or feature modules only after package dependency rules are stable and extraction has measurable value.
- Run old-identity upgrade, backup/restore, rollback and release provenance checks.
- Release in stages with known limitations, support/rollback path and no unverified capability claims.

## Current Phase 1–2 code baseline

- The existing OpenAI-compatible Retrofit call is behind the provider-neutral `ChatCompletionGateway` interface; ViewModel accepts an injectable gateway while retaining its Android factory constructor.
- Shared chat/settings/result models and deterministic model fallback policy live under `domain/`.
- Unredacted HTTP BODY logging is removed; no request headers or chat bodies are logged by this client.
- Invalid/empty send attempts do not clear the user’s draft; acceptance is reported by `sendMessage`.
- The existing fallback ordering and default endpoint/model remain compatible. This does **not** yet implement multiple providers, persistent memory, MCP, files, a skill installer or action confirmation.
