import test from 'node:test';
import assert from 'node:assert/strict';
import { explorationRewardItems } from '../../main/resources/static/js/exploration-result.mjs';
test('reward icons count only acquired materials and supplies, never generated overflow',()=>{
 const items=explorationRewardItems({loot:{materials:[{materialId:1,name:'철',quantity:3,collectedQuantity:1},{materialId:2,name:'기어',quantity:2,collectedQuantity:0}],cleanerAdded:1,medicalKitsAdded:1},secretFindings:{keysAdded:1}});
 assert.deepEqual(items.map(i=>[i.name,i.quantity]),[['철',1],['세척액',1],['의료 키트',1],['비밀의 열쇠',1]]);
});
test('empty loot and capped supplies do not invent reward icons',()=>{
 assert.deepEqual(explorationRewardItems({loot:{materials:[],cleanerFound:true,cleanerAdded:0,medicalKitsAdded:0},secretFindings:{keyFound:true,keysAdded:0}}),[]);
});
