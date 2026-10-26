# Scrap Escape core expansion implementation plan

> Execution: implement inline in the existing backend/bootstrap feature branch, following the user's instruction to proceed in order. No extra worktree, push or merge.

Goal: complete the first backend expansion milestone before frontend work. Keep the existing Spring Boot/JdbcTemplate architecture and API error contracts.
Spec: approved final conversation and Notion Scrap Escape game plan, 2026-10-26 resumption.

## Scope
- New games: 240h, 4 random distinct parts from 7; inventory4/chest16; 10 materials/6 locations; fixed recipes as approved.
- Exploration: day0..3/night0..4 total with existing5%empty buckets; up to2 kinds. Choose first kind uniformly and split remaining quantity into a second distinct kind on nonempty totals>=2; never report a generated quantity as collected if storage rejects it. Deterministic first chosen kind wins scarce slots; expose lost quantity. Uniform candidate draws are a builder default, not a user-set probability.
- All batches max6; dirty ages remain distinct. Clean stacks pack to6 without lost quantities. Full destination only accepts a whole move if clean headroom fits. Multi-batch crafting supported.
- Medical kits hospital guaranteed1, counter max2, no slot; cleaner1/5 with50%extra unchanged.
- Following backend task in same milestone: contamination0..100, base+10, third consecutive+5; rest>=4 −10/reset, rest>=6 stamina bonus; disease thresholds0/30/40/60; illness+2h/+2stamina; kit−60 cures,1h;72h untreatedfailure.
- Storms24..48h apart,1..3h, precomputed nonoverlapping schedule, +5/exposed hour; safe actions no exposure. Next storm and action previews returned.
- All repaired activates explicit escape API; deadline/diseasefailure overrides successful escape. Existing user UI intent requires manual escape, not automatic success.
- Secret rooms/keys remain out of MVP; no frontend in this backend milestone.

## Data safety
Use Flyway for tracked SQL migrations. V1 captures existing schema/data; existing databases baseline at1, fresh databases executeV1. V2 creates junction table, adds resource/risk fields and rules version, expands fixed data and splits old batches above6 preserving quantities/ages. Old games remain readable and old progress isn't erased; old-rules games are read-only after upgrade and return LEGACY_GAME on actions. New game uses rules_version2. Local credentials never printed/committed. Only scrap_escape_test is used for automated fixtures; no drop of existing user databases.

## Tasks
- [x] Baseline realMySQL test suite; add failing integration tests for newgame/loot/batches/health/escape.
- [x] Migrate DB safely and idempotently; verify new and existing test databases including legacy rows.
- [x] Implement expanded fixeddata/storage/loot with previews and medicalcounter.
- [x] Implement health/storm/cure/explicitescape using rowlock + one transaction.
- [x] Update HTTP tests, smoke script and API guide; run MySQL suite and fresh-schema/upgrade checks.
- [x] Launch isolated HTTP port and smoke real API; buildjar, review diff and report precise completion and remaining frontend.

## Review focus
Stock6 overflow, rollback before partial moves, acquired-age protection; partialloot stats;72h cure and240h lastrepair boundaries;storm overlap and safe rest;repeat escape/cure/concurrentloot; legacy read-only idempotent migration.

## Completion evidence
2026-10-26: baseline30/30; expansion52/52 realMySQL tests;fresh/legacy/upgradererun validated;bootJar passed;HTTP smoke PASS on18080/testDB. Read-only independent review found no actionable defect. Production gameplay DB not migrated by this run;next application startup applies migrations. UI and actual balance remain next milestone; secretrooms excluded.
