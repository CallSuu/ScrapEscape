# Expedition Result and Secret Room Choice Plan

Goal: exploration -> full-page large reward icons/counts -> Confirm -> main, or room discovery choice -> admission/later -> main. All use region-specific replaceable placeholder scenery. User explicitly requires both choice buttons key-gated; provide always-available return-to-main to avoid keyless trap. Later costs no key. Existing backend/APIs unchanged.
- [x] RED host state transition/retry/terminal ordering and reward/key gating tests.
- [x] Track pending action intent in app host; add local ACK_EXPLORATION_RESULT; exact receipt packet in Panel. Preserve Back during pending (no reopen), retries and server-authoritative snapshot. Expedition failure receipt before ending.
- [x] Full-page exploration-result and secret-room Views/CSS; big actual-acquired reward tiles, overflow/empty/cap info. Secret IMMEDIATE/REVISIT preview, Enter OPEN_ROOM and Later DEFER_ROOM, keyless Back. No B files touched.
- [x] JS/MySQL/JAR/Chrome: ordinary, discovery/key0/key1, later/enter exactly once, room reward, repeated/uncertain requests, closed view, terminal receipt,3 viewports. Test DB only, fixture interceptions labelled.
- [x] Update shared A/B contract and beginner guide/evidence. No commit/push.
