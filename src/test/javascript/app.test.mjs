import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createAppController } from '../../main/resources/static/js/app.mjs';
const fixture=()=>JSON.parse(fs.readFileSync(new URL('../../../docs/api/examples/SE-16.json',import.meta.url))).response.body;
const reply=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{'Content-Type':'application/json'}});
function setup(options={}){
 const screen=fixture();const calls=[],panels=[],messages=[];
 const fetchImpl=async(path,o)=>{
  calls.push({path,...o});if(options.fetch)return options.fetch(path,o,screen,calls);
  return reply(o.method==='GET'?screen:path==='/api/games'?screen.state:{message:'처리 완료',state:{...screen.state,elapsedHours:99}},path==='/api/games'?201:200);
 };
 const c=createAppController({clientOptions:{fetchImpl,randomUUID:()=>crypto.randomUUID()},onPanelChange:p=>panels.push(p),onMessage:m=>messages.push(m)});
 return {c,screen,calls,panels,messages};
}
test('opening the app never creates a game automatically',()=>{
 const {c,calls}=setup();assert.equal(c.getScreen(),null);assert.deepEqual(c.getFlags(),{busy:false,hasPending:false});assert.equal(calls.length,0);
});
test('new game uses refreshed snapshot rather than action response state',async()=>{
 const {c,screen}=setup();await c.dispatch({type:'START_GAME'});const result=await c.dispatch({type:'REST',hours:6});
 assert.equal(result.ok,true);assert.equal(c.getScreen().state.elapsedHours,0);assert.deepEqual(c.getScreen(),screen);
});
test('all mutation commands map to existing backend routes and payloads',async()=>{
 const {c,screen,calls}=setup();await c.dispatch({type:'START_GAME'});const base='/api/games/'+screen.state.gameId;
 const cases=[
  [{type:'EXPLORE',locationId:6},'POST','/explorations',{locationId:6}],
  [{type:'MOVE_ITEM',batchId:3,storage:'CHEST'},'PATCH','/items/3/storage',{storage:'CHEST'}],
  [{type:'CLEAN_ITEM',batchId:3},'POST','/items/3/clean',undefined],
  [{type:'DISCARD_ITEM',batchId:3},'DELETE','/items/3',undefined],
  [{type:'REPAIR_PART',partId:2},'POST','/repairs/2',undefined],
  [{type:'REST',hours:6},'POST','/rests',{hours:6}],
  [{type:'TREAT'},'POST','/treatments',undefined],
  [{type:'ESCAPE'},'POST','/escape',undefined],
  [{type:'DEFER_ROOM',locationId:2},'POST','/secret-rooms/2/defer',undefined],
  [{type:'OPEN_ROOM',locationId:2,mode:'REVISIT'},'POST','/secret-rooms/2/open',{mode:'REVISIT'}]
 ];
 for(const [command,method,path,body] of cases){const before=calls.length;assert.equal((await c.dispatch(command)).ok,true);const sent=calls[before];assert.equal(sent.path,base+path);assert.equal(sent.method,method);assert.deepEqual(sent.body===undefined?undefined:JSON.parse(sent.body),body);assert.ok(sent.headers['Idempotency-Key']);}
});
test('unknown commands return an error without sending a request',async()=>{
 const {c,calls,messages}=setup();const result=await c.dispatch({type:'UNKNOWN'});assert.equal(result.ok,false);assert.equal(calls.length,0);assert.equal(messages.length,1);
});
test('lost new-game response retains request key and retry returns same game',async()=>{
 let posts=0;const keys=[];
 const {c,screen}=setup({fetch:async(path,o,s)=>{if(o.method==='GET')return reply(s);keys.push(o.headers['Idempotency-Key']);if(++posts===1)throw new TypeError('lost');return reply(s.state,201);}});
 assert.equal((await c.dispatch({type:'START_GAME'})).ok,false);assert.equal(c.getFlags().hasPending,true);
 assert.equal((await c.dispatch({type:'START_GAME'})).ok,false);assert.equal(posts,1);
 assert.equal((await c.dispatch({type:'RETRY'})).ok,true);assert.equal(c.getScreen().state.gameId,screen.state.gameId);assert.equal(keys[0],keys[1]);assert.equal(c.getFlags().hasPending,false);
});
test('late navigation refresh cannot reopen a panel closed while loading',async()=>{
 let release,delay=false;const wait=new Promise(r=>release=r);
 const {c}=setup({fetch:async(path,o,s)=>{if(o.method==='GET'&&delay)await wait;return reply(o.method==='GET'?s:s.state);}});
 await c.dispatch({type:'START_GAME'});delay=true;const opening=c.navigate({name:'exploration'});await c.navigate(null);release();await opening;assert.equal(c.getPanel(),null);
});
test('terminal snapshot opens result once and closing does not cause repeated reopening',async()=>{
 const {c,screen,panels}=setup();screen.state.status='FAILED';screen.state.failureReason='TIME_LIMIT';await c.dispatch({type:'START_GAME'});
 assert.equal(c.getPanel().name,'result');await c.navigate(null);await c.dispatch({type:'REFRESH'});assert.equal(c.getPanel(),null);assert.equal(panels.filter(p=>p?.name==='result').length,1);
});
test('starting a different game resets previous panel and selections at host boundary',async()=>{
 const {c,screen}=setup();await c.dispatch({type:'START_GAME'});await c.navigate({name:'exploration'});
 await c.dispatch({type:'START_GAME'});screen.state.gameId='22222222-2222-4222-8222-222222222222';await c.dispatch({type:'START_GAME'});assert.equal(c.getPanel(),null);assert.equal(c.getScreen().state.gameId,screen.state.gameId);
});
test('closing an immediate room twice sends defer once',async()=>{
 const {c,screen,calls}=setup();screen.state.secrets.rooms=[{locationId:1,opened:false,immediateAvailable:true}];await c.dispatch({type:'START_GAME'});
 await c.navigate({name:'secret-room',locationId:1});await Promise.all([c.navigate(null),c.navigate(null)]);
 assert.equal(calls.filter(x=>x.path.endsWith('/secret-rooms/1/defer')).length,1);assert.equal(c.getPanel(),null);
});
test('closing a room during an uncertain request never adds a defer request',async()=>{
 let broken=false;const {c,screen,calls}=setup({fetch:async(path,o,s)=>{if(broken&&o.method!=='GET')throw new TypeError('lost');return reply(o.method==='GET'?s:s.state);}});
 screen.state.secrets.rooms=[{locationId:1,opened:false,immediateAvailable:true}];await c.dispatch({type:'START_GAME'});await c.navigate({name:'secret-room',locationId:1});
 broken=true;await c.dispatch({type:'OPEN_ROOM',locationId:1,mode:'IMMEDIATE'});await c.navigate(null);
 assert.equal(c.getFlags().hasPending,true);assert.equal(calls.filter(x=>x.path.endsWith('/defer')).length,0);
});
test('one server error produces one message, including client precondition errors',async()=>{
 let broken=false;const {c,messages}=setup({fetch:async(path,o,s)=>broken?reply({code:'INSUFFICIENT_STAMINA',message:'체력 부족'},409):reply(o.method==='GET'?s:s.state)});
 await c.dispatch({type:'START_GAME'});messages.length=0;broken=true;const result=await c.dispatch({type:'EXPLORE',locationId:1});assert.equal(result.ok,false);assert.deepEqual(messages,['체력 부족']);
});
test('new game returns to welcome without API requests and creates only after another start',async()=>{
 const {c,screen,calls,messages}=setup();await c.dispatch({type:'START_GAME'});await c.navigate({name:'exploration'});
 const before=calls.length;const reset=await c.dispatch({type:'START_GAME'});
 assert.equal(reset.ok,true);assert.equal(reset.screen,null);assert.equal(reset.result,null);
 assert.equal(c.getScreen(),null);assert.equal(c.getPanel(),null);assert.equal(calls.length,before);assert.equal(messages.at(-1),'');
 screen.state.gameId='33333333-3333-4333-8333-333333333333';
 await c.dispatch({type:'START_GAME'});assert.equal(c.getScreen().state.gameId,screen.state.gameId);
 assert.equal(calls.filter(r=>r.method==='POST'&&r.path==='/api/games').length,2);
});
test('refresh cannot resurrect an abandoned game after returning to welcome',async()=>{
 const {c,calls}=setup();await c.dispatch({type:'START_GAME'});await c.dispatch({type:'START_GAME'});
 const before=calls.length;const response=await c.dispatch({type:'REFRESH'});
 assert.equal(response.ok,false);assert.equal(response.error.code,'NO_GAME');assert.equal(c.getScreen(),null);assert.equal(calls.length,before);
});
test('uncertain action blocks returning to welcome and preserves current game for retry',async()=>{
 let broken=false;const {c}=setup({fetch:async(path,o,s)=>{if(broken&&o.method!=='GET')throw new TypeError('lost');return reply(o.method==='GET'?s:s.state);}});
 await c.dispatch({type:'START_GAME'});const before=c.getScreen();broken=true;await c.dispatch({type:'REST',hours:6});
 const response=await c.dispatch({type:'START_GAME'});assert.equal(response.ok,false);assert.equal(response.error.code,'PENDING_REQUEST');assert.equal(c.getScreen(),before);assert.equal(c.getFlags().hasPending,true);
});
test('loot receipt reports actual collected and left-behind amounts, including after retry',async()=>{
 let attempts=0;const {c,messages}=setup({fetch:async(path,o,s)=>{
  if(o.method==='GET')return reply(s);
  if(path==='/api/games')return reply(s.state,201);
  if(++attempts===1)throw new TypeError('lost');
  return reply({message:'재료 1개를 가져왔습니다.',state:s.state,loot:{materials:[{name:'철 조각',quantity:1,collectedQuantity:1},{name:'기어',quantity:2,collectedQuantity:0}]}});
 }});
 await c.dispatch({type:'START_GAME'});await c.dispatch({type:'EXPLORE',locationId:1});await c.dispatch({type:'RETRY'});
 assert.match(messages.at(-1),/철 조각: 획득 1개/);assert.match(messages.at(-1),/기어: 획득 0개 · 남김 2개/);
});
test('zero loot keeps the successful empty exploration message and refreshed empty inventory',async()=>{
 const {c,messages}=setup({fetch:async(path,o,s)=>{
  if(path.endsWith('/explorations')){s.state.elapsedHours=6;s.state.stamina=8;s.state.statistics.emptyExplorations=1;return reply({message:'구석까지 뒤졌지만 쓸 만한 부품은 없었습니다.',state:s.state,loot:{quantity:0,collectedQuantity:0,leftBehindQuantity:0,materials:[]}});}
  return reply(o.method==='GET'?s:s.state);
 }});
 await c.dispatch({type:'START_GAME'});const result=await c.dispatch({type:'EXPLORE',locationId:1});
 assert.equal(result.ok,true);assert.equal(c.getScreen().state.elapsedHours,6);assert.equal(c.getScreen().state.inventory.length,0);assert.equal(messages.at(-1),'구석까지 뒤졌지만 쓸 만한 부품은 없었습니다.');
});
test('exploration opens reward screen and confirmation returns to main without another request',async()=>{
 const {c,calls}=setup();await c.dispatch({type:'START_GAME'});await c.navigate({name:'exploration'});await c.dispatch({type:'EXPLORE',locationId:6});
 assert.equal(c.getPanel().name,'exploration-result');assert.equal(c.getPanel().locationId,6);const before=calls.length;
 const result=await c.dispatch({type:'ACK_EXPLORATION_RESULT'});assert.equal(result.ok,true);assert.equal(c.getPanel(),null);assert.equal(calls.length,before);
});
test('discovery choice waits until loot confirmation and neither confirmation nor reading uses a key',async()=>{
 const {c,screen,calls}=setup({fetch:async(path,o,s)=>o.method==='GET'?reply(s):path==='/api/games'?reply(s.state):reply({message:'발견',state:s.state,loot:{materials:[]},secretFindings:{roomDiscovered:true,roomLocationId:1,keysAdded:0}})});
 screen.state.secrets.keyAmount=1;screen.state.secrets.rooms=[{locationId:1,opened:false,immediateAvailable:true}];
 await c.dispatch({type:'START_GAME'});await c.navigate({name:'exploration'});await c.dispatch({type:'EXPLORE',locationId:1});assert.equal(c.getPanel().name,'exploration-result');
 const before=calls.length;await c.dispatch({type:'ACK_EXPLORATION_RESULT'});assert.equal(c.getPanel().name,'secret-room');assert.equal(c.getPanel().fromDiscovery,true);assert.equal(calls.length,before);assert.equal(c.getScreen().state.secrets.keyAmount,1);
});
test('uncertain exploration retry preserves location and opens exactly one reward screen',async()=>{
 let attempts=0;const {c,panels}=setup({fetch:async(path,o,s)=>{if(o.method==='GET')return reply(s);if(path==='/api/games')return reply(s.state);if(++attempts===1)throw new TypeError('lost');return reply({message:'보상',state:s.state,loot:{materials:[]}});}});
 await c.dispatch({type:'START_GAME'});await c.navigate({name:'exploration'});await c.dispatch({type:'EXPLORE',locationId:4});await c.dispatch({type:'RETRY'});
 assert.equal(c.getPanel().locationId,4);assert.equal(panels.filter(p=>p?.name==='exploration-result').length,1);
});
test('leaving while exploration is pending does not reopen a reward screen later',async()=>{
 let attempts=0;const {c}=setup({fetch:async(path,o,s)=>{if(o.method==='GET')return reply(s);if(path==='/api/games')return reply(s.state);if(++attempts===1)throw new TypeError('lost');return reply({message:'보상',state:s.state,loot:{materials:[]}});}});
 await c.dispatch({type:'START_GAME'});await c.navigate({name:'exploration'});await c.dispatch({type:'EXPLORE',locationId:4});await c.navigate(null);await c.dispatch({type:'RETRY'});assert.equal(c.getPanel(),null);
});
test('failed expedition shows its loot before final ending, then confirmation opens ending',async()=>{
 const {c}=setup({fetch:async(path,o,s)=>{if(o.method==='GET')return reply(s);if(path==='/api/games')return reply(s.state);s.state.status='FAILED';s.state.failureReason='TIME_LIMIT';return reply({message:'기한초과',state:s.state,loot:{materials:[]}});}});
 await c.dispatch({type:'START_GAME'});await c.dispatch({type:'EXPLORE',locationId:1});assert.equal(c.getPanel().name,'exploration-result');await c.dispatch({type:'ACK_EXPLORATION_RESULT'});assert.equal(c.getPanel().name,'result');
});
test('room admission returns a room reward screen and later deferral returns main once',async()=>{
 const {c,screen,calls}=setup();screen.state.secrets.rooms=[{locationId:1,opened:false,immediateAvailable:false}];await c.dispatch({type:'START_GAME'});await c.navigate({name:'secret-room',locationId:1});
 await c.dispatch({type:'OPEN_ROOM',locationId:1,mode:'REVISIT'});assert.equal(c.getPanel().name,'exploration-result');assert.equal(c.getPanel().activity,'OPEN_ROOM');await c.dispatch({type:'ACK_EXPLORATION_RESULT'});assert.equal(c.getPanel(),null);
 await c.navigate({name:'secret-room',locationId:1});await c.dispatch({type:'DEFER_ROOM',locationId:1});assert.equal(c.getPanel(),null);assert.equal(calls.filter(r=>r.path.endsWith('/defer')).length,1);
});
test('receipt survives a successful expedition whose screen refresh needs retry without repeating POST',async()=>{
 let failRefresh=false,explorationPosts=0;const {c,panels}=setup({fetch:async(path,o,s)=>{
  if(o.method==='GET'){if(failRefresh){failRefresh=false;throw new TypeError('snapshot unavailable');}return reply(s);}
  if(path==='/api/games')return reply(s.state,201);
  explorationPosts++;failRefresh=true;return reply({message:'획득',state:s.state,loot:{materials:[{materialId:1,name:'철 조각',quantity:2,collectedQuantity:2}]},secretFindings:null});
 }});
 await c.dispatch({type:'START_GAME'});await c.navigate({name:'exploration'});
 const pending=await c.dispatch({type:'EXPLORE',locationId:3});assert.equal(pending.ok,false);assert.equal(c.getPanel().name,'exploration');assert.equal(c.getFlags().hasPending,true);
 const retried=await c.dispatch({type:'RETRY'});assert.equal(retried.ok,true);assert.equal(explorationPosts,1);assert.equal(c.getPanel().name,'exploration-result');assert.equal(c.getPanel().locationId,3);assert.equal(c.getPanel().receipt.loot.materials[0].collectedQuantity,2);assert.equal(panels.filter(p=>p?.name==='exploration-result').length,1);
});
