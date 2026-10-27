import { applyActionAvailability } from './game-api.mjs';

export function safeSelection(screen, batchId) {
  if (!screen) return null;
  const item = [...screen.state.inventory, ...screen.state.chest].find(i => i.batchId === batchId);
  const actions = screen.actions.items.find(a => a.batchId === batchId);
  return item && actions ? { item, actions } : null;
}

export function toggleSelection(selected, batchId) {
  if (selected.has(batchId)) selected.delete(batchId); else selected.add(batchId);
  return selected;
}

export function selectedMoveAvailability(screen, batchIds) {
  const blocked = message => ({ allowed:false, unavailableReason:message });
  if (!screen || !batchIds.length) return blocked('재료를 선택하세요.');
  const selections = batchIds.map(id=>safeSelection(screen,id));
  if (selections.some(s=>!s)) return blocked('재료 목록이 바뀌었습니다. 다시 선택하세요.');
  const source = selections[0].item.storage, storage = selections[0].actions.moveTo;
  if (selections.some(s=>s.item.storage!==source)) return blocked('휴대품 또는 상자 중 한 곳의 재료만 선택하세요.');
  const denied = selections.find(s=>!s.actions.move.allowed);
  if (denied) return blocked(denied.actions.move.unavailableReason || '이동할 수 없습니다.');
  const targets = storage==='CHEST'?screen.state.chest:screen.state.inventory;
  const capacity = storage==='CHEST'?screen.state.chestCapacity:screen.state.inventoryCapacity;
  const limit = screen.state.stackLimit, headroom = new Map(), clean = new Map();
  let slots = 0;
  for (const item of targets) if (item.condition==='CLEAN') headroom.set(item.materialId,(headroom.get(item.materialId)||0)+limit-item.quantity);
  for (const {item} of selections) {
    if (item.condition==='CLEAN') clean.set(item.materialId,(clean.get(item.materialId)||0)+item.quantity);
    else slots++;
  }
  for (const [material,quantity] of clean) slots+=Math.ceil(Math.max(0,quantity-(headroom.get(material)||0))/limit);
  if (slots>capacity-targets.length) return blocked('선택한 재료 전체를 이동할 보관 공간이 부족합니다.');
  return { allowed:true, unavailableReason:null, storage };
}

export function selectedCareAvailability(screen, batchIds, action) {
  const blocked = unavailableReason => ({ allowed:false, unavailableReason });
  if (!screen || !batchIds.length) return blocked('재료를 선택하세요.');
  const selections = batchIds.map(id => safeSelection(screen,id));
  if (selections.some(s => !s)) return blocked('재료 목록이 바뀌었습니다. 다시 선택하세요.');
  if (action === 'clean' && screen.state.cleanerAmount < batchIds.length)
    return blocked('세척액이 부족합니다. 선택 ' + batchIds.length + '묶음 / 보유 ' + screen.state.cleanerAmount + '회분');
  const denied = selections.find(s => !s.actions[action]?.allowed);
  return denied ? blocked(denied.actions[action]?.unavailableReason || '실행할 수 없는 재료가 포함되어 있습니다.') : { allowed:true, unavailableReason:null };
}

export function restSummary(preview, screen) {
  return actionSummary(preview, screen).replace(' · 체력 소모 0', '');
}

export function actionSummary(preview, screen) {
  if (!preview) return '';
  const texts = ['시간 ' + preview.durationHours + 'h', '체력 소모 ' + preview.staminaCost];
  const p = preview.projected;
  if (p && screen) {
    const signed = n => n >= 0 ? '+' + n : String(n);
    texts.push('완료 ' + (Math.floor((8 + p.elapsedHours) / 24) + 1) + '일 · ' + String((8 + p.elapsedHours) % 24).padStart(2, '0') + ':00');
    texts.push('체력 ' + signed(p.stamina - screen.state.stamina), '오염 ' + signed(p.contamination - screen.state.contamination));
    if (p.expiringBatchIds.length) texts.push('다른 미세척 재료 ' + p.expiringBatchIds.length + '묶음 사용 불가 예정');
    if (p.status === 'FAILED') texts.push('⚠ ' + (p.failureReason === 'UNTREATED_DISEASE' ? '질병 치료 기한 초과' : '생존 제한 시간 초과') + '로 생존 실패 예정');
  }
  return texts.join(' · ');
}

export function treatmentSummary(preview, screen) {
  const summary = actionSummary(preview, screen), p = preview?.projected;
  if (!p || !screen) return summary;
  return summary + ' · 키트 ' + screen.state.medicalKitAmount + ' → ' + p.medicalKitAmount + ' · ' + (p.diseased ? '질병 유지' : screen.state.diseased ? '질병 제거' : '질병 없음');
}

export function createSafeZoneView(root, context) {
  const abort = new AbortController();
  let gameId = null, selectedIds = new Set(), discardIds = [], hours = 6, gridSignature = '', repairSignature = '';
  root.innerHTML = `<section class="se-safe" aria-label="비행기 안전지대">
    <aside class="se-safe__survival"><section><h3>생존 상태</h3><p data-info="status"></p><p data-info="supplies"></p></section>
      <section><h3>휴식</h3><label>휴식 시간 <select data-action="hours" aria-label="휴식 시간"></select></label><p class="se-safe__muted">6시간 이상 휴식 시 체력 회복 보너스</p><p data-info="rest-preview"></p><button type="button" data-action="rest">휴식하기</button><p class="se-safe__reason" data-info="rest-reason"></p></section>
      <section><h3>치료</h3><p class="se-safe__muted">정상 치료 시 키트 1개 · 질병 제거 · 오염 최대60 감소</p><p data-info="treat-preview"></p><button type="button" data-action="treat">의료 키트 사용</button><p class="se-safe__reason" data-info="treat-reason"></p></section>
    </aside>
    <section class="se-safe__storage"><h3 data-info="inventory-count"></h3><div class="se-safe__slots" data-grid="inventory"></div><h3 data-info="chest-count"></h3><div class="se-safe__slots se-safe__slots--chest" data-grid="chest"></div>
      <section class="se-safe__selection" aria-live="polite"><h3 data-info="selected">재료를 선택하세요</h3><p data-info="item-detail"></p><div class="se-safe__item-actions"><button type="button" data-action="move">이동</button><button type="button" data-action="clean">세척</button><button type="button" data-action="discard">폐기</button></div><p data-info="clean-preview"></p><p class="se-safe__reason" data-info="item-reason"></p>
        <div class="se-safe__discard" hidden><p data-info="discard-confirm"></p><button type="button" data-action="confirm-discard">전체 폐기 확인</button><button type="button" data-action="cancel-discard">취소</button></div>
      </section>
    </section>
    <section class="se-safe__maintenance"><h3>비행기 수리</h3><p class="se-safe__muted">휴대품·상자의 세척 완료 재료를 함께 사용합니다.</p><div class="se-safe__repairs"></div><section class="se-safe__escape"><h3 data-info="repair-count"></h3><p data-info="escape-preview"></p><button type="button" data-action="escape">탈출하기</button><p class="se-safe__reason" data-info="escape-reason"></p></section></section>
  </section>`;
  const info = key => root.querySelector('[data-info="' + key + '"]');
  const button = action => root.querySelector('[data-action="' + action + '"]');
  const make = (tag, text, className) => { const e = document.createElement(tag); if (text !== undefined) e.textContent = text; if (className) e.className = className; return e; };
  for (let h = 1; h <= 8; h++) { const option = make('option', h + '시간'); option.value = h; button('hours').append(option); }
  const unavailable = { allowed: false, unavailableReason: '재료를 선택하세요.' };
  function apply(action, preview, flags, reason) { const text = applyActionAvailability(button(action), preview || unavailable, flags); if (reason) info(reason).textContent = text || ''; return text; }
  function refresh() { view.render(context.getScreen(), context.getFlags()); }
  button('hours').addEventListener('change', () => { const value = Number(button('hours').value); if (Number.isInteger(value) && value >= 1 && value <= 8) { hours = value; refresh(); } }, { signal: abort.signal });
  root.addEventListener('click', event => {
    const target = event.target.closest('button'); if (!target || !root.contains(target) || target.disabled) return;
    const screen = context.getScreen(), flags = context.getFlags(); if (!screen) return;
    if (target.dataset.batch) { toggleSelection(selectedIds,Number(target.dataset.batch)); discardIds = []; refresh(); return; }
    const action = target.dataset.action;
    if (action === 'cancel-discard') { discardIds = []; refresh(); return; }
    const selectedId = selectedIds.size===1?[...selectedIds][0]:null;
    const selection = safeSelection(screen, selectedId);
    if (action === 'discard') { if (!flags.busy && !flags.hasPending && selectedCareAvailability(screen,[...selectedIds],'discard').allowed) { discardIds = [...selectedIds]; refresh(); button('confirm-discard').focus(); } return; }
    if (flags.busy || flags.hasPending) return;
    let command, preview;
    if (action === 'rest') { command = { type: 'REST', hours }; preview = screen.actions.rests.find(r => r.hours === hours)?.preview; }
    if (action === 'treat') { command = { type: 'TREAT' }; preview = screen.actions.treatment; }
    if (action === 'escape') { command = { type: 'ESCAPE' }; preview = screen.actions.escape; }
    if (action === 'repair') { const partId = Number(target.dataset.part); command = { type: 'REPAIR_PART', partId }; preview = screen.actions.repairs.find(r => r.partId === partId)?.preview; }
    if (action === 'move') { preview = selectedMoveAvailability(screen,[...selectedIds]); command = selectedIds.size>1?{type:'MOVE_ITEMS',batchIds:[...selectedIds],storage:preview.storage}:{type:'MOVE_ITEM',batchId:selectedId,storage:preview.storage}; }
    if (action === 'clean') { command = { type: 'CLEAN_ITEMS', batchIds:[...selectedIds] }; preview = selectedCareAvailability(screen,[...selectedIds],'clean'); }
    if (action === 'confirm-discard' && discardIds.length && discardIds.length===selectedIds.size && discardIds.every(id=>selectedIds.has(id))) { command = { type: 'DISCARD_ITEMS', batchIds:[...discardIds] }; preview = selectedCareAvailability(screen,discardIds,'discard'); }
    if (preview?.allowed && command) void context.dispatch(command);
  }, { signal: abort.signal });
  const view = {
    render(screen, flags) {
      if (!screen) { root.querySelector('.se-safe').hidden = true; return; }
      root.querySelector('.se-safe').hidden = false;
      root.querySelector('.se-safe').dataset.area = context.getPanel()?.area || 'all';
      const s = screen.state;
      if (gameId !== s.gameId) { gameId = s.gameId; selectedIds.clear(); discardIds = []; hours = 6; gridSignature = ''; repairSignature = ''; }
      for (const id of selectedIds) if (!safeSelection(screen,id)) selectedIds.delete(id);
      const selectedId = selectedIds.size===1?[...selectedIds][0]:null;
      const selection = safeSelection(screen, selectedId);
      if (discardIds.some(id=>!selectedIds.has(id)) || discardIds.length!==selectedIds.size) discardIds = [];
      const selected = [...selectedIds].map(id=>safeSelection(screen,id).item);
      const move = selectedMoveAvailability(screen,[...selectedIds]);
      const clean = selectedCareAvailability(screen,[...selectedIds],'clean');
      const discard = selectedCareAvailability(screen,[...selectedIds],'discard');
      button('hours').value = String(hours);
      info('status').textContent = '체력 ' + s.stamina + '/' + s.maxStamina + ' · 오염 ' + s.contamination + '/100 · 연속 탐색 ' + s.consecutiveExplorations + '회' + (s.diseased ? '\n질병 · 치료까지 ' + s.treatmentRemainingHours + '시간' : '\n질병 없음');
      info('supplies').textContent = '세척액 ' + s.cleanerAmount + '/' + s.cleanerCapacity + ' · 키트 ' + s.medicalKitAmount + '/' + s.medicalKitCapacity + ' · 열쇠 ' + s.secrets.keyAmount + '/' + s.secrets.keyCapacity;
      const rest = screen.actions.rests.find(r => r.hours === hours)?.preview;
      info('rest-preview').textContent = restSummary(rest, screen); apply('rest', rest, flags, 'rest-reason');
      info('treat-preview').textContent = treatmentSummary(screen.actions.treatment, screen); apply('treat', screen.actions.treatment, flags, 'treat-reason');
      info('inventory-count').textContent = '휴대 인벤토리 ' + s.inventory.length + '/' + s.inventoryCapacity;
      info('chest-count').textContent = '비행기 보관함 ' + s.chest.length + '/' + s.chestCapacity;
      const signature = JSON.stringify([s.inventory, s.chest, s.inventoryCapacity, s.chestCapacity]);
      if (signature !== gridSignature) {
        gridSignature = signature;
        for (const [key, items, capacity] of [['inventory', s.inventory, s.inventoryCapacity], ['chest', s.chest, s.chestCapacity]]) {
          const grid = root.querySelector('[data-grid="' + key + '"]'); grid.replaceChildren();
          for (const item of items) {
            const tile = make('button', undefined, 'se-safe__item'); tile.type = 'button'; tile.dataset.batch = item.batchId; tile.dataset.condition = item.condition;
            const label = make('strong', item.name + ' ×' + item.quantity), detail = make('small', item.condition === 'DIRTY' ? '미세척 · ' + item.expiresInHours + 'h 남음' : item.condition === 'CLEAN' ? '✓ 세척 완료' : '사용 불가');
            tile.dataset.urgency = item.condition === 'UNUSABLE' || item.condition === 'DIRTY' && item.expiresInHours <= 6 ? 'danger' : item.condition === 'DIRTY' && item.expiresInHours <= 12 ? 'warning' : 'normal';
            tile.append(label, detail); grid.append(tile);
          }
          for (let i = items.length; i < capacity; i++) grid.append(make('span', '빈칸', 'se-safe__empty'));
        }
      }
      root.querySelectorAll('[data-batch]').forEach(tile => tile.setAttribute('aria-pressed', String(selectedIds.has(Number(tile.dataset.batch)))));
      info('selected').textContent = selection ? selection.item.name + ' ×' + selection.item.quantity : selectedIds.size ? selectedIds.size+'묶음 선택 · 총 '+selected.reduce((n,i)=>n+i.quantity,0)+'개' : '재료를 선택하세요';
      info('item-detail').textContent = selection ? (selection.item.storage === 'CHEST' ? '보관함' : '휴대품') + ' · ' + (selection.item.condition === 'DIRTY' ? '미세척 · ' + selection.item.expiresInHours + '시간 후 사용 불가' : selection.item.condition === 'CLEAN' ? '세척 완료 · 만료 없음' : '사용 불가 · 폐기할 수 있습니다.') : selectedIds.size ? selected.map(i=>i.name+' ×'+i.quantity).join(' / ') : '클릭으로 여러 묶음을 선택하고, 다시 클릭하면 해제합니다.';
      const toChest = selected.length && selected.every(i=>i.storage==='INVENTORY');
      button('move').textContent = selectedIds.size>1 ? '선택 '+selectedIds.size+'묶음 '+(toChest?'상자에 넣기':'꺼내기') : toChest ? '상자에 넣기' : selection ? '꺼내기' : '이동';
      const reasons = [apply('move', move, flags), apply('clean', clean, flags), apply('discard', discard, flags)];
      info('item-reason').textContent = [...new Set(reasons.filter(Boolean))].join(' ');
      button('clean').textContent = selectedIds.size>1?'선택 '+selectedIds.size+'묶음 세척':'세척';
      button('discard').textContent = selectedIds.size>1?'선택 '+selectedIds.size+'묶음 폐기':'폐기';
      info('clean-preview').textContent = selectedIds.size ? '선택 '+selectedIds.size+'묶음 전체 세척 · 세척액 '+selectedIds.size+'회분 · 시간 '+selectedIds.size+'h · 체력 소모 없음' : '';
      root.querySelector('.se-safe__discard').hidden = !discardIds.length;
      info('discard-confirm').textContent = discardIds.length ? selected.map(i=>i.name+' ×'+i.quantity).join(' / ')+' — 선택한 '+selected.length+'묶음 전체를 폐기할까요? 되돌릴 수 없습니다.' : '';
      apply('confirm-discard', discardIds.length ? discard : null, flags);
      const parts = JSON.stringify(s.repairs.map(r => r.partId));
      if (parts !== repairSignature) {
        repairSignature = parts; const list = root.querySelector('.se-safe__repairs'); list.replaceChildren();
        for (const part of s.repairs) {
          const card = make('article', undefined, 'se-safe__part'); card.dataset.partCard = part.partId;
          const name = make('h4'), ingredients = make('ul'), preview = make('p', '', 'se-safe__muted'), start = make('button', '수리·장착'), reason = make('p', '', 'se-safe__reason');
          preview.dataset.preview = ''; reason.dataset.reason = ''; start.type = 'button'; start.dataset.action = 'repair'; start.dataset.part = part.partId;
          card.append(name, ingredients, preview, start, reason); list.append(card);
        }
      }
      for (const part of s.repairs) {
        const card = root.querySelector('[data-part-card="' + part.partId + '"]');
        card.querySelector('h4').textContent = part.name + (part.repaired ? ' · 수리 완료' : '');
        const list = card.querySelector('ul'); list.replaceChildren();
        for (const ingredient of part.ingredients) { const li = make('li', ingredient.name + ' ' + ingredient.availableQuantity + '/' + ingredient.requiredQuantity); li.dataset.enough = String(ingredient.availableQuantity >= ingredient.requiredQuantity); list.append(li); }
        const p = screen.actions.repairs.find(r => r.partId === part.partId)?.preview;
        card.querySelector('[data-preview]').textContent = part.repaired ? '장착 완료' : actionSummary(p, screen);
        card.querySelector('[data-reason]').textContent = applyActionAvailability(card.querySelector('button'), p || unavailable, flags) || '';
      }
      info('repair-count').textContent = '수리 완료 ' + s.repairs.filter(p => p.repaired).length + '/' + s.repairs.length;
      info('escape-preview').textContent = actionSummary(screen.actions.escape, screen);
      apply('escape', screen.actions.escape, flags, 'escape-reason');
    },
    destroy() { abort.abort(); root.replaceChildren(); }
  };
  return view;
}
