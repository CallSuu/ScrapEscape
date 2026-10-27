# Exploration Screen Implementation Plan

Goal: next guided A step: six location cards -> real exploration -> HUD/receipt refresh.
Spec: frontend A contractv1.1; keep B modules untouched. Backend/API unchanged.
- [x] RED deadline boundary and actual loot/retry receipt regression tests.
- [x] Create exploration.mjs with createExplorationView/render/destroy, root-local DOM, scoped exploration.css and local SVG placeholders for6 locations. Reuse Context and availability helper. Unknown/ended restrictions remain server-authoritative. ? navigation only; room view next step.
- [x] Show real collected/left-behind amounts in shared receipt, including global retry. No optimistic state changes.
- [x] JS/MySQL/JAR and actual Chrome: six cards, no POST opening, hospital exploration, zero/overflow, restrictions/reasons, deadline warnings, room visibility, destroy/reopen and close during request;3 viewports. Test fixtures only in dedicated test DB/browser.
- [x] Beginner guide/progress evidence and docs index; no commit/push.

Evidence: JS32 pass, MySQL115 pass, JAR success. Chrome real hospital exploration and controlled test-game restrictions; isolated View fixture for text safety/lifecycle. Hidden room button focus bug found by independent reviewer, reproduced via real Tab, fixed by visibility filtering, full browser rerun green. No commit/push.
