import test from 'node:test';import assert from 'node:assert/strict';
import { repairMarker } from '../../main/resources/static/js/aircraft-repair.mjs';
test('all seven part ids have separate fixed locations on the plane regardless of recipe order',()=>{const markers=[1,2,3,4,5,6,7].map(repairMarker);assert.equal(new Set(markers.map(m=>m.x+','+m.y)).size,7);for(const m of markers){assert.ok(m.x>0&&m.x<100&&m.y>0&&m.y<100);}assert.deepEqual(repairMarker(3),markers[2]);});
test('unknown part has a safe central marker without guessing another known component',()=>{assert.deepEqual(repairMarker(999),{x:50,y:50});});
