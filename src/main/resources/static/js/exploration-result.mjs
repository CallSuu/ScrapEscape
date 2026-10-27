const icons = { 1:'🔩', 2:'⚙️', 3:'🔌', 4:'💾', 5:'🧩', 6:'🛞', 7:'🔋', 8:'▤', 9:'🧱', 10:'📡' };

export function explorationRewardItems(receipt) {
  const loot = receipt?.loot;
  const items = (loot?.materials || []).filter(m => m.collectedQuantity > 0).map(m =>
    ({ name: m.name, quantity: m.collectedQuantity, icon: icons[m.materialId] || '🔧', unit: '개' }));
  if (loot?.cleanerAdded > 0) items.push({ name: '세척액', quantity: loot.cleanerAdded, icon: '🧴', unit: '회분' });
  if (loot?.medicalKitsAdded > 0) items.push({ name: '의료 키트', quantity: loot.medicalKitsAdded, icon: '✚', unit: '개' });
  if (receipt?.secretFindings?.keysAdded > 0) items.push({ name: '비밀의 열쇠', quantity: receipt.secretFindings.keysAdded, icon: '🔑', unit: '개' });
  return items;
}

export function createExplorationResultView(root, context) {
  const abort = new AbortController();
  root.innerHTML = `<section class="se-receipt" aria-label="획득 결과">
    <p class="se-receipt__eyebrow">이번에 가져온 물품</p>
    <div class="se-receipt__items"></div>
    <p class="se-receipt__empty" hidden>가져온 물품이 없습니다. 빈손이어도 탐색 시간과 체력은 소모됩니다.</p>
    <div class="se-receipt__details"><p class="se-receipt__left"></p><p class="se-receipt__caps"></p><p class="se-receipt__loss"></p><p class="se-receipt__message"></p></div>
    <button class="se-receipt__confirm" type="button">확인</button>
  </section>`;
  const find = name => root.querySelector('.se-receipt__' + name);
  find('confirm').addEventListener('click', () => { void context.dispatch({ type: 'ACK_EXPLORATION_RESULT' }); }, { signal: abort.signal });
  return {
    render(screen, flags) {
      const panel = context.getPanel(), receipt = panel?.receipt;
      find('confirm').disabled = flags.busy || flags.hasPending || !receipt;
      if (!receipt) { find('message').textContent = '확인할 탐색 결과가 없습니다.'; return; }
      const items = explorationRewardItems(receipt), list = find('items'); list.replaceChildren();
      for (const item of items) {
        const tile = document.createElement('article'); tile.className = 'se-receipt__item';
        const icon = document.createElement('span'); icon.className = 'se-receipt__icon'; icon.textContent = item.icon; icon.setAttribute('aria-hidden','true');
        const count = document.createElement('strong'); count.className = 'se-receipt__count'; count.textContent = '×' + item.quantity;
        const name = document.createElement('span'); name.className = 'se-receipt__name'; name.textContent = item.name + ' · ' + item.quantity + item.unit;
        tile.append(icon, count, name); list.append(tile);
      }
      find('empty').hidden = items.length > 0;
      const left = (receipt.loot?.materials || []).filter(m => m.quantity > m.collectedQuantity).map(m => m.name + ' ' + (m.quantity-m.collectedQuantity) + '개');
      find('left').textContent = left.length ? '공간이 부족해 남긴 재료 · ' + left.join(' / ') : '';
      const caps = [], loot = receipt.loot;
      if (loot?.cleanerFound && !loot.cleanerAdded) caps.push('세척액 통이 가득 차 추가하지 못했습니다.');
      if (receipt.secretFindings?.keyFound && !receipt.secretFindings.keysAdded) caps.push('열쇠 보유량이 가득 차 추가하지 못했습니다.');
      const location = screen?.locations.find(l=>l.locationId===panel.locationId);
      if ((panel.activity === 'EXPLORE' && location?.medicalKitReward > 0 || receipt.secretRoom?.outcome === 'MEDICAL_KIT') && !loot?.medicalKitsAdded)
        caps.push('의료 키트 보유량이 가득 차 추가하지 못했습니다.');
      find('caps').textContent = caps.join(' ');
      const room = receipt.secretRoom;
      find('loss').textContent = room?.outcome === 'MATERIAL_LOSS' ? '미세척 휴대 재료 ' + room.lostQuantity + '개를 잃었습니다.' : room?.outcome === 'CONTAMINATION' ? '오염 노출 +' + room.contaminationIncrease + ' · 오염도는 최대100까지 적용됩니다.' : '';
      find('message').textContent = receipt.message;
      find('confirm').textContent = screen?.state.status !== 'RUNNING' ? '확인 · 최종 결과 보기' : '확인';
    },
    destroy() { abort.abort(); root.replaceChildren(); }
  };
}
