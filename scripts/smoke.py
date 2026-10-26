"""실행 중인 서버에 새 판을 만들어 기본 CRUD와 실패 엔딩을 확인한다.
기존 게임은 건드리지 않는다. 이 시연에서 생성한 게임은 DB에 남는다.
"""
import json
import os
from urllib.request import Request, urlopen
from urllib.error import HTTPError

BASE = os.environ.get('BASE_URL', 'http://127.0.0.1:8080')
def request(method, path, body=None, expected=200):
    data = None if body is None else json.dumps(body).encode()
    req = Request(BASE + path, data=data, method=method, headers={'Content-Type':'application/json'})
    try:
        with urlopen(req, timeout=15) as response:
            status, result = response.status, json.load(response)
    except HTTPError as error:
        status, result = error.code, json.load(error)
    assert status == expected, (method, path, status, result)
    return result

state = request('POST', '/api/games', expected=201)
game = '/api/games/' + state['gameId']
assert state['stamina'] == 10 and len(state['repairs']) == 4
assert state['remainingHours'] == 240 and state['stackLimit'] == 6
assert state['inventoryCapacity'] == 4 and state['chestCapacity'] == 16
cards = request('GET', game + '/locations')
assert len(cards) == 6 and cards[0]['durationHours'] == 6
for _ in range(5):
    result = request('POST', game + '/explorations', {'locationId':1})
    state = result['state']
    if state['inventory']:
        break
    if state['stamina'] < 3:
        state = request('POST', game + '/rests', {'hours':6})['state']
assert state['inventory'], '재료 0개가 연속 발생했습니다. 다시 실행하세요.'
batch = state['inventory'][0]
state = request('PATCH', game + f"/items/{batch['batchId']}/storage", {'storage':'CHEST'})['state']
assert all(item['batchId'] != batch['batchId'] for item in state['inventory'])
assert state['chest'][0]['batchId'] == batch['batchId']
if state['stamina'] == 0:
    state = request('POST', game + '/rests', {'hours':1})['state']
state = request('POST', game + f"/items/{batch['batchId']}/clean")['state']
assert state['chest'][0]['condition'] == 'CLEAN'
state = request('DELETE', game + f"/items/{batch['batchId']}")['state']
assert not state['chest'] and state['statistics']['discarded'] == batch['quantity']
request('POST', game + '/rests', {'hours':1.5}, expected=400)
request('POST', game + '/escape', expected=409)
# 새 판에서 병원 확정 지급과 치료를 확인한다.
medical = request('POST', '/api/games', expected=201)
medical_path = '/api/games/' + medical['gameId']
medical = request('POST', medical_path + '/explorations', {'locationId':6})['state']
assert medical['medicalKitAmount'] == 1
medical = request('POST', medical_path + '/treatments')['state']
assert medical['medicalKitAmount'] == 0 and medical['contamination'] == 0
request('POST', medical_path + '/treatments', expected=409)
request('POST', game + f"/repairs/{state['repairs'][0]['partId']}", expected=409)
while state['status'] == 'RUNNING':
    state = request('POST', game + '/rests', {'hours':8})['state']
assert state['status'] == 'FAILED' and state['elapsedHours'] >= 240
request('POST', game + '/explorations', {'locationId':1}, expected=409)
print(json.dumps({'result':'PASS','gameId':state['gameId'],'elapsedHours':state['elapsedHours'],
                  'status':state['status'],'statistics':state['statistics']}, ensure_ascii=False, indent=2))
