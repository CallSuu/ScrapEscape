# Idempotent Game Requests Implementation Plan

> Inline execution in existing feature branch; preserve previous changes. Scope authorized: resolve GAP-02; retain GAP-01 time-limit priority; explain GAP-03; defer GAP-04 policy and GAP-05 balance.

Goal: repeated HTTP mutations with the same request UUID execute once, even concurrently or after restart/end.
Architecture: optional Idempotency-Key header on all11 mutation endpoints including create. Shared transactional request service stores normalized operation+validated DTO SHA256 and original JSON response in MySQL. No new dependency. Existing response bodies unchanged; add Idempotency-Replayed response header. Missing key retains compatibility.

Contract: UUID header normalized lowercase; invalid key400. Samekey+samecanonical method/path/DTO returns original body/status, even after game ended or later changed. Samekey+different action409 IDEMPOTENCY_KEY_REUSED. Only successful200/201 responses cached, including FAILED game outcomes. Validation/domain/DB errors roll back both action and cache. Differentkeys represent differentactions. Client generates key per intended action and reuses only for uncertain retries; recovered response is historical, client GETs latest state after replay.

Storage: Flyway V6 api_request with globalUUIDPK, fingerprint,nullable gameFK ON DELETE CASCADE,responseJSON,createdAt. Insert ON DUPLICATE KEY no-op obtains unique row lock; read FOR UPDATE; execute action only for a new uncompleted row. Cache/game action commit together. No TTL until GAP-04 retention policy is decided; deleting a game removes its request history and replay guarantee.

Tasks:
- [x] RED HTTP regression: repeatedrest/create/loot,wrongpayload,keyvalidation,concurrentretry,failedactionrollback,delete/endedreplay.
- [x] V6 and shared transaction wrapper;wire all mutation routes, preserve GETs and bodies.
- [x] FullMySQL tests plus fresh/legacy migrations;HTTP replay verification;review.
- [x] Update docs/api Markdown/OpenAPI/examples and docs/backend gaps;record GAP-01 accepted and GAP-04/05 deferred.
