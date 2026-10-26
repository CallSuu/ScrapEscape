# Screen Contract Implementation Plan

Goal: implement the three approved preparations: screen/API mapping, server previews, shared JavaScript request handling.
Architecture: GET /api/games/{id}/screen returns State, existing location cards and deterministic safe-action previews in one read-only transaction. Existing mutation paths and game rules stay compatible. Plain browser ES module served by Spring Boot; no framework/dependency or full screen design.

Decisions: deadlines warn but do not block; resource/status restrictions disable buttons with readable reason. Random exploration/room outcomes remain probabilities, not predictions. No automatic mutation retry. Uncertain requests retain UUID in memory, block new mutations and expose manual retry; successful responses refresh the screen snapshot. X can always close a panel but does not cancel a committed server action. Result storage/ranking deferred. Page reload loses in-memory retry context; no save feature introduced.

- [x] RED: MySQL HTTP tests for previews, deadlines, expiry, resource restrictions, clean merging and read-only behavior; JS tests for response loss/retry, duplicate click, refresh failure and HTTP errors.
- [x] Implement GameModels screen DTOs, GameService.screen, GameController GET, GamePreviews helper. Add plain game-api.mjs client and button availability helper.
- [x] Run complete MySQL suite and JS tests, build JAR, collect actual screen HTTP examples and test served module.
- [x] Update Markdown/OpenAPI/examples, screen mapping, implementation status and verification record. Independent review before completion.

Review focus: treatment completing at disease deadline must not consume kit; dirty material being cleaned protected at48h; full destination can merge CLEAN; read never expires rows or invalidates immediate room; uncertain retry must reuse exact serialized payload and key and refresh before another action.

Verification: MySQL115/115 and JS11/11; actual HTTP screen/projection and module/client integration PASS; independent read-only review no actionable findings. No commit/push requested; preserve existing uncommitted branch work.
