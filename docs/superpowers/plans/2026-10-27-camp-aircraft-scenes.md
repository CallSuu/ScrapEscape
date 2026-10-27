# Camp and Aircraft Scene Implementation Plan

Goal: safe-zone becomes a forest crash-camp full-page scene with crate/rest/medical/aircraft hotspots; first three open existing controls as modals over camp; aircraft is a full-page colored top view with selected repair markers and ingredient details.
Spec: user request 2026-10-27 and attached references (composition only), existing API/Context and safe-zone behavior.
Execution: same branch; preserve all uncommitted safety/result work. No commit/push, no backend/API change.

- [x] RED controller tests: new targets accepted, camp dialog/aircraft Back/Escape returns camp, camp Back main, uncertain request close doesn't reopen, navigation costs no mutation. Repair markers stable per partId and missing part fallback.
- [x] Original generated forest background asset saved in repo; original colored SVG aircraft schematic. Hotspots and diagram markers accessible buttons, no art/UI text burned in background.
- [x] Camp scene + subdialog reuse existing safe-zone controls by area. Aircraft scene shows four selected parts at distinct physical locations; completed icons/check state, details and server-authorized repair/escape. HUD persistent, separate heading/Back.
- [x] Host fullpage/modal layering, returning focus, stale import guard, busy/pending controls. Existing expedition and ending routes intact; dev preview retains old overview for isolated testing.
- [x] Full suites, real 18080/testDB browser: hotspot navigation no mutation, areas split, inventory workflows, actual repair/escape, blocked repair info, nested X/Escape, uncertainty, three viewports; review and docs contracts/guides/status.

Evidence: JS61 and Java/MySQL115, build; actual Chrome/18080 test DB, 3 camp + 3 aircraft layouts, real rest/move/clean/treatment/4 repairs/escape, offline close and focus. Exact repair materials prepared only in owned test games, not natural-play balance. Review: no significant findings. No commit/push.
