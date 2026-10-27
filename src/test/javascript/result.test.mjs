import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { resultSummary } from '../../main/resources/static/js/result.mjs';
const fixture=()=>JSON.parse(fs.readFileSync(new URL('../../../docs/api/examples/SE-16.json',import.meta.url))).response.body.state;
test('running and missing games never receive ending labels',()=>{assert.equal(resultSummary(null),null);assert.equal(resultSummary(fixture()),null);});
test('success and known failure reasons come only from server status',()=>{const s=fixture();s.status='SUCCESS';s.repairs[0].repaired=true;assert.equal(resultSummary(s).title,'탈출 성공');assert.equal(resultSummary(s).repaired.length,1);s.status='FAILED';s.failureReason='UNTREATED_DISEASE';assert.match(resultSummary(s).reason,/질병 치료 기한/);s.failureReason='TIME_LIMIT';assert.match(resultSummary(s).reason,/생존 제한/);s.failureReason=null;assert.equal(resultSummary(s).reason,'탈출 준비를 마치지 못했습니다.');});
test('zero stats remain visible and general exploration counts exclude room visits',()=>{const s=fixture();s.status='FAILED';s.statistics.explorations=2;s.secrets.statistics.openedRooms=3;const r=resultSummary(s);assert.equal(r.stats.find(x=>x[0]==='일반 탐색')[1],2);assert.equal(r.stats.find(x=>x[0]==='개방한 방')[1],3);assert.equal(r.stats.find(x=>x[0]==='폐기한 재료')[1],0);});
