import test from 'node:test';
import assert from 'node:assert/strict';
import { explorationDeadlineRisk } from '../../main/resources/static/js/exploration.mjs';
test('normal exploration does not treat absent disease deadline as zero',()=>{
 assert.equal(explorationDeadlineRisk({durationHours:6},{remainingHours:240,diseased:false,treatmentRemainingHours:null}),false);
});
test('completing exactly at survival deadline is warned',()=>{
 assert.equal(explorationDeadlineRisk({durationHours:7},{remainingHours:7,diseased:false,treatmentRemainingHours:null}),true);
});
test('completing exactly at treatment deadline is warned even with overall time left',()=>{
 assert.equal(explorationDeadlineRisk({durationHours:9},{remainingHours:100,diseased:true,treatmentRemainingHours:9}),true);
});
test('missing treatment deadline does not disable a diseased exploration',()=>{
 assert.equal(explorationDeadlineRisk({durationHours:8},{remainingHours:100,diseased:true,treatmentRemainingHours:null}),false);
});
test('shorter exploration remains allowed before both deadlines',()=>{
 assert.equal(explorationDeadlineRisk({durationHours:8},{remainingHours:100,diseased:true,treatmentRemainingHours:9}),false);
});
test('shared header includes common costs and probabilities but not hospital-only kit reward',async()=>{
 const {explorationSharedInfo}=await import('../../main/resources/static/js/exploration.mjs');
 const {default:fs}=await import('node:fs');const screen=JSON.parse(fs.readFileSync(new URL('../../../docs/api/examples/SE-16.json',import.meta.url))).response.body;
 const info=explorationSharedInfo(screen.locations,screen.state);
 assert.match(info.cost,/시간 6h/);assert.match(info.cost,/체력 2/);assert.match(info.cost,/오염 \+10/);
 assert.match(info.rewards,/0개 5%/);assert.match(info.rewards,/세척액 80%/);assert.match(info.rewards,/열쇠 10%/);
 assert.doesNotMatch(info.rewards,/키트/);assert.equal(info.risk,'');
});
test('shared header refreshes night disease costs and warns without blocking cards',async()=>{
 const {explorationSharedInfo}=await import('../../main/resources/static/js/exploration.mjs');
 const info=explorationSharedInfo([{durationHours:9,staminaCost:5,expectedContaminationIncrease:15,cleanerProbabilityPercent:50,rewards:[{quantity:0,probabilityPercent:5},{quantity:4,probabilityPercent:40}],stormExposureHours:1}],{night:true,diseased:true,treatmentRemainingHours:9,remainingHours:100,secrets:{keyProbabilityPercent:10,discoveryProbabilityPercent:15}});
 assert.match(info.cost,/밤/);assert.match(info.cost,/9h/);assert.match(info.cost,/체력 5/);assert.match(info.cost,/질병/);assert.ok(info.risk);
});
