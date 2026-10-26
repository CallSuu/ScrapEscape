# Secret Rooms Backend Implementation Plan

> Execution: inline, preserving the existing feature branch and uncommitted core-expansion work. User explicitly requested full secret-room backend now. No frontend, merge or push.

Goal: implement the approved optional secret-room spec as an additive backend feature.
Architecture: existing GameService orchestrates actions/state; focused SecretRoomService/SecretRoomRepository handle room/key logic. Keep row locking and one transaction per action. Add Flyway V5 only; never edit applied migrations.
Stack: Java17/SpringBoot/JdbcTemplate/MySQL/Flyway/JUnit.
Spec: conversation approved rules and existing core-expansion plan.

## Constraints and builder decisions
- Normal successful-running exploration: independent key10% and undiscovered local room15%;empty loot can find keys;cap2,stats only actually stored keys. One room per location/game,one open,6 locations.
- Room discovery is remembered and never blocks exploration. Discovered cards expose buttonLabel="?";undiscovered has null. Reads cost nothing;insufficient resources only disable open.
- Immediate mode:1h/stamina1/key1,only immediately after discovery;any valid intervening action or explicit defer invalidates immediate mode permanently. Reads do not. Defer/X consumes nothing and retains room. Failed requests do not invalidate.
- Revisit:day4h/stamina2,night5h/stamina3,+2h/+2stamina if diseased;includes open cost;counts toward consecutive but not normal exploration/loot rolls.
- Immediate exposure only storm;revisit+10 base,+5third consecutive,+5/exposedstormhour;badcontamination+15 on top. Both check disease/expiry/deadlines and fail-priority exactly like normal actions.
- Outcomes:0..49 material3..4,50..79 supply(cleaner2 orkit1 equal),80..89 loss1..2 from entry dirtyinventory only,90..99 contamination15. No eligible loss becomes contamination15. Loss can span multiple dirtybatches in cyclic order from randomfirst. Chest/clean/unusable protected.
- Rewards fresh dirtybatch acquired atvisitcompletion;no retroactive merging with different-age dirtybatches;max6 andslot limits. Overflow reported;cleaner/kit capped.
- Room row records mode/cost/outcome/reward/loss for audit;compositegame/locationPK,FKgamecascade. Counts+totals included in state/end statistics androomhistory.

## Tasks
- [x] RED: HTTP JSON tests for initial secrets, discovery/key, readonlypreviews, missing resources, deferred memory.
- [x] V5 migration + repository + optional state/loot metadata;initialize keyzero for existinggames without wipingdata.
- [x] implement find/open/defer hooks, previews and rewards with locks and transactions.
- [x] test all probability boundaries/outcomes,cap/slots/age/storm/illness/deadline/concurrency/rollback.
- [x] full MySQL suite, fresh/legacy migration, JAR+realHTTP smoke, review and docs.

## Completion evidence
90/90 realMySQL tests (37secret,3migration) with zero failures/errors/skips. bootJar and realHTTP smoke PASS. Review found cross-room defer bug; regression reproduced RED, fix and whole-suite GREEN. Test server stopped; personal gameplay DB untouched. Frontend and balance remain separate.
