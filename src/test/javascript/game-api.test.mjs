import test from 'node:test';
import assert from 'node:assert/strict';
import { createGameClient, applyActionAvailability } from '../../main/resources/static/js/game-api.mjs';
const id='11111111-1111-4111-8111-111111111111';
const response=(body,status=200,replayed=false)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Idempotency-Replayed':String(replayed)}});
const snapshot={state:{gameId:id,elapsedHours:6},locations:[],actions:{}};
const result={message:'rested',state:snapshot.state};
const path='/api/games/'+id+'/rests';
test('lost mutation response reuses exact key and serialized payload then refreshes screen',async()=>{
 const calls=[];let mutations=0;
 const c=createGameClient({randomUUID:()=> 'same-key',fetchImpl:async(url,options)=>{
  calls.push({url,options});if(options.method==='POST'&&++mutations===1)throw new TypeError('connection lost');
  return response(options.method==='POST'?result:snapshot,200,true);
 }});
 const body={hours:6};await assert.rejects(c.act('POST',path,body),e=>e.retryable);body.hours=1;
 assert.equal(c.hasPending,true);await assert.rejects(c.act('POST',path,{hours:1}),e=>e.code==='PENDING_REQUEST');
 const done=await c.retry();assert.equal(done.screen.state.elapsedHours,6);assert.equal(c.hasPending,false);
 assert.equal(calls[0].options.body,'{"hours":6}');assert.equal(calls[1].options.body,'{"hours":6}');
 assert.equal(calls[0].options.headers['Idempotency-Key'],calls[1].options.headers['Idempotency-Key']);
 assert.equal(calls[2].url,'/api/games/'+id+'/screen');
});
test('second click while action in flight does not send a second request',async()=>{
 let release;const waiting=new Promise(r=>release=r);let calls=0;
 const c=createGameClient({fetchImpl:async()=>{calls++;if(calls===1)await waiting;return response(calls===1?result:snapshot);}});
 const first=c.act('POST',path,{hours:6});await assert.rejects(c.act('POST',path,{hours:6}),e=>e.code==='REQUEST_BUSY');
 release();await first;assert.equal(calls,2);assert.equal(c.busy,false);
});
test('successful action whose refresh fails retries only GET and blocks new actions meanwhile',async()=>{
 const methods=[];let reads=0;
 const c=createGameClient({fetchImpl:async(url,o)=>{methods.push(o.method);if(o.method==='GET'&&++reads===1)throw new TypeError('offline');return response(o.method==='GET'?snapshot:result);}});
 await assert.rejects(c.act('POST',path,{hours:6}),e=>e.retryable);assert.equal(c.hasPending,true);
 await c.retry();assert.deepEqual(methods,['POST','GET','GET']);assert.equal(c.hasPending,false);
});
test('server domain error is displayed and clears pending action so a corrected action gets new key',async()=>{
 let number=0;const errors=[],keys=[];
 const c=createGameClient({randomUUID:()=>String(++number),onError:e=>errors.push(e),fetchImpl:async(url,o)=>{
  if(o.method==='GET')return response(snapshot);keys.push(o.headers['Idempotency-Key']);return keys.length===1?response({code:'INSUFFICIENT_STAMINA',message:'체력이 부족합니다.'},409):response(result);
 }});
 await assert.rejects(c.act('POST',path,{hours:6}),e=>e.code==='INSUFFICIENT_STAMINA'&&!e.retryable);
 assert.equal(c.hasPending,false);assert.equal(errors[0].message,'체력이 부족합니다.');await c.act('POST',path,{hours:1});assert.deepEqual(keys,['1','2']);
});
test('HTTP500 is uncertain and retries the same mutation key',async()=>{
 const keys=[];const c=createGameClient({randomUUID:()=> 'stable',fetchImpl:async(url,o)=>{
  if(o.method==='GET')return response(snapshot);keys.push(o.headers['Idempotency-Key']);return keys.length===1?response({code:'INTERNAL_ERROR',message:'error'},500):response(result);
 }});
 await assert.rejects(c.act('POST',path,{hours:6}),e=>e.retryable);await c.retry();assert.deepEqual(keys,['stable','stable']);
});
test('malformed success JSON keeps action retryable and busy is always released',async()=>{
 const flags=[];const c=createGameClient({onBusy:s=>flags.push(s),fetchImpl:async()=>new Response('broken',{status:200})});
 await assert.rejects(c.act('POST',path,{hours:6}),e=>e.retryable);assert.equal(c.busy,false);assert.equal(c.hasPending,true);assert.equal(flags.at(-1).busy,false);assert.equal(flags.at(-1).hasPending,true);
});
test('timeout aborts fetch and keeps request for safe manual retry',async()=>{
 const c=createGameClient({timeoutMs:5,fetchImpl:async(url,o)=>new Promise((resolve,reject)=>o.signal.addEventListener('abort',()=>reject(new DOMException('timeout','AbortError'))))});
 await assert.rejects(c.act('POST',path,{hours:6}),e=>e.retryable);assert.equal(c.hasPending,true);assert.equal(c.busy,false);
});
test('new game response establishes game id and refreshes screen, next action receives new key',async()=>{
 const keys=[],urls=[];let n=0;
 const c=createGameClient({randomUUID:()=>String(++n),fetchImpl:async(url,o)=>{urls.push(url);if(o.method==='GET')return response(snapshot);keys.push(o.headers['Idempotency-Key']);return response(url==='/api/games'?snapshot.state:result,url==='/api/games'?201:200);}});
 await c.act('POST','/api/games');await c.act('POST',path,{hours:6});assert.deepEqual(keys,['1','2']);assert.equal(urls[1],'/api/games/'+id+'/screen');
});
test('GET errors do not invent a pending mutation or change the loaded game',async()=>{
 const errors=[];const c=createGameClient({onError:e=>errors.push(e),fetchImpl:async()=>response({code:'NOT_FOUND',message:'없는 게임'},404)});
 await assert.rejects(c.load(id),e=>e.status===404);assert.equal(c.hasPending,false);assert.equal(c.busy,false);assert.equal(errors.length,1);
});
test('button helper disables only actions and exposes reason without changing label',()=>{
 const b={textContent:'세척',setAttribute(k,v){this[k]=v;}};
 assert.equal(applyActionAvailability(b,{allowed:false,unavailableReason:'세척액 부족'}),'세척액 부족');assert.equal(b.disabled,true);assert.equal(b['aria-disabled'],'true');assert.equal(b.textContent,'세척');
 applyActionAvailability(b,{allowed:true},{busy:true});assert.equal(b.disabled,true);
 applyActionAvailability(b,{allowed:true},{hasPending:true});assert.equal(b.disabled,true);
 applyActionAvailability(b,{allowed:true});assert.equal(b.disabled,false);assert.equal(b.title,'');
});
test('409 refreshes changed availability before releasing buttons and reports original error',async()=>{
 const calls=[],states=[],errors=[];
 const c=createGameClient({onSnapshot:s=>states.push(s),onError:e=>errors.push(e),fetchImpl:async(url,o)=>{
  calls.push(o.method);return o.method==='GET'?response(snapshot):response({code:'STORAGE_FULL',message:'공간 부족'},409);
 }});
 await c.load(id);await assert.rejects(c.act('PATCH','/api/games/'+id+'/items/1/storage',{storage:'CHEST'}),e=>e.code==='STORAGE_FULL');
 assert.deepEqual(calls,['GET','PATCH','GET']);assert.equal(states.length,2);assert.equal(errors[0].code,'STORAGE_FULL');assert.equal(c.hasPending,false);
});
