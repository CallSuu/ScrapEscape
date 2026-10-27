# Frontend First Screen Implementation Plan

Goal: first guided frontend lesson, actual start screen/new-game/HUD with existing API. This is an incremental learning step, not all A frontend work.
Spec: docs/frontend/Scrap_Escape_본인담당_개발명세서.md (common contractv1.0). Keep B files untouched.
Approach: same-origin HTML/CSS/ES modules; app host owns client/Context, main module renders HUD. Original code-native SVG scenery as replaceable placeholder. No framework/dependency/backend change. Work in current checkout on named frontend branch; preserve uncommitted handoff docs. No commit/push without request.

- [x] RED host tests: no autoPOST, command mapping, snapshot authoritative, pending flags, new-game retry, one-time terminal notification, late navigation cancellation, close behavior. Browser smoke pins real new-game HUD.
- [x] Build index.html, common.css/main.css, app.mjs/main.mjs and assets/main/world.svg. New game only on click; context contracts stable; future panels use optional dynamic imports, clear preparation message when absent (current step).
- [x] Verify all existing Java/MySQL and JS tests, JAR, actual HTTP/browser layout at1280x720/1440x900/1920x1080. Use test DB only. Capture screenshots and cleanup own game fixtures/server.
- [x] Add beginner walkthrough and progress evidence, update index/README. Explain HTML/CSS/JavaScript and next step. Existing API/docs contract unchanged; UI modules beyond main deliberately next lessons.

Checks: double click/network uncertainty must not create second game; onSnapshot can precede busy=false; unsafe text stays textContent; missing B panels must not create B stubs; X never cancels action and does not reopen late navigation. No local save/timer/optimistic health changes.

Evidence: work/frontend-java.log (115 MySQL tests), work/frontend-final-js.log (22 JS tests), work/frontend-browser-result.json (three viewport/HUD/no-autoPOST/double-click/focus), work/frontend-screenshots/*.png. Known focus-return bug reproduced in browser and corrected with after-render microtask. Independent read-only review no other required issue. No commit/push.
