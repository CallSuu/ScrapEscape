# Safe Zone and Final Results Implementation Plan

**Goal:** Implement the previously assigned B product screens in the current integrated frontend, as explicitly authorized by the user.
**Architecture:** Pure ES modules createSafeZoneView/createResultView use existing Context; existing app mounts both automatically. Server screen is authoritative, no API or gameplay changes.
**Tech Stack:** Spring Boot static HTML/CSS/JavaScript, MySQL; Node built-in tests, Chrome CDP.
**Spec:** docs/frontend/Scrap_Escape_팀원담당_개발명세서.md B1–B7, shared contract v1.3.

## Constraints and execution decisions
- Continue frontend/main-exploration in place: user asked to continue both roles here. No separate B branch/merge needed, no automatic commit or push in this implementation turn.
- Keep current factories, names, Context, APIs and resource slots; no new dependencies.
- Implement independent /dev/safe-zone.html host using existing app controller rather than duplicating API mapping; no auto creation.
- Preserve host X/focus/HUD, fullpage expeditions, terminal transition and retry.

## Review focus
- Discard confirmation must refer to batchId and be cleared if that batch disappears or game changes.
- Pending actions block writes; fresh preview used at click time, not old DOM values.
- Expiring batches and failure projection warn but allowed=true remains actionable.
- CLEAN merged batches disappearing are normal; no optimistic inventory updates.
- Outcome never calculates success locally; explicit ESCAPE remains mandatory.

## Tasks
- [x] 1. Add failing tests for selection invalidation, projected warnings, actual recovery, result reasons/stats. Run RED. Implement safe-zone.mjs/result.mjs factories and scoped CSS. Run GREEN.
- [x] 2. Add independent dev host using createAppController and both Views; start, hospital exploration, six-hour rest, safe/results/close/retry. No automatic creation or production fixtures.
- [x] 3. Build and run Java/MySQL and JS suites. Real separate test DB/18080 and Chrome: start/explore/recover/move/clean/discard/treat, prepared clean materials for repair/explicit escape, failure/result/restart, three viewports, keyboard/focus, uncertainty. Clean only own game IDs.
- [x] 4. Review, fix material issues with regression tests, update matching A/B shared contract/status/guide/README. Record real verification and limits.

## Execution evidence

- RED: missing module exports; GREEN: JS53. Treatment boundary regression RED: missing treatmentSummary; GREEN: JS55. Java/MySQL115/115 skipped0; JAR success.
- Chrome actual API actions, exact fixture data in own recorded test games, 3 viewport layouts; response loss after real commit, same key retry once; selected batch disappearance and disease failure; keyboard focus.
- Final review: one P2 treatment outcome omission fixed with deadline/normal regression tests and browser assertion for kit1→1/disease persists; no other concrete findings.
- Ruling: current user instruction supersedes historical separate B branch/file owner restriction; same branch integration, no duplicate merge. Cost if changed later: separate role history would need extraction.
- No commit/push in this implementation turn. No API/backend changes.
