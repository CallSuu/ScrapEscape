import test from 'node:test';
import assert from 'node:assert/strict';
import { secretRoomChoices } from '../../main/resources/static/js/secret-room.mjs';
const state=(keys=1)=>({status:'RUNNING',rulesVersion:2,secrets:{keyAmount:keys}});
const room=()=>({opened:false,immediateAvailable:true,immediate:{allowed:true,unavailableReason:null,durationHours:1,staminaCost:1,keyCost:1},revisit:{allowed:true,unavailableReason:null,durationHours:4,staminaCost:2,keyCost:1}});
test('no key disables both enter and later choices',()=>{
 const choices=secretRoomChoices(room(),state(0));assert.equal(choices.enter.allowed,false);assert.equal(choices.later.allowed,false);
});
test('low stamina blocks admission but later stays free and available with a key',()=>{
 const r=room();r.immediate.allowed=false;r.immediate.unavailableReason='체력 부족';const choices=secretRoomChoices(r,state());assert.equal(choices.enter.allowed,false);assert.equal(choices.later.allowed,true);assert.equal(choices.later.keyCost,0);
});
test('remembered room uses shorter revisit preview and opened rooms cannot be chosen again',()=>{
 const r=room();r.immediateAvailable=false;let choices=secretRoomChoices(r,state());assert.equal(choices.mode,'REVISIT');assert.equal(choices.enter.durationHours,4);r.opened=true;choices=secretRoomChoices(r,state());assert.equal(choices.enter.allowed,false);assert.equal(choices.later.allowed,false);
});
