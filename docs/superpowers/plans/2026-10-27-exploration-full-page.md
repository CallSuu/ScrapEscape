# Exploration Full Page Implementation Plan

Goal: user-requested city approach/fork scene, full-page exploration selection with Back, six image-centered cards visible without scrolling.
Constraints: existing HUD always visible; compact HUD on city scene at1280x720. No navigation time cost or new API, no B product edits. Keep Context Panel contract; exploration gets own full-page root, other panels remain modal.
- [x] RED shared info tests; browser full-page/Back/no-scroll assertions.
- [x] Add exploration page root/host transition and city SVG background; consolidate common info; enlarge location images; keep unique kit reward/local reason on cards.
- [x] Verify JS/Java build and Chrome at1280x720/1440x900/1920x1080: all6 cards and buttons in viewport, no scroll/clipping, HUD, Back/no time advance, actual exploration/disabled/retry, modal unaffected.
- [x] Sync A/B common contract/local guide, document actual verification. No commit/push.

Evidence: work/fullpage-java.log (115), fullpage-js.log (34), fullpage-browser-result.json (six geometry scenarios/no scroll/fullpage/back/real action/modal preserved). Reviewer flagged pointer blocking; real hit-test failed then passed after scoped pointer-events. No commit/push.
