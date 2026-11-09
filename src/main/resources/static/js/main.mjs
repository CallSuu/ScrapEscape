import { itemIcon } from './item-icons.mjs';
import { explorationSharedInfo } from './exploration.mjs';

export function createMainView(root, context) {
  // Static HTML describes structure; server-supplied values are always textContent.
  root.className = 'se-main';
  root.innerHTML = `
    <div class="se-main__world" aria-hidden="true"></div>
    <header class="se-main__brand"><span>SCRAP ESCAPE</span><span class="se-main__edition">생존 · 수집 · 탈출</span></header>
    <section class="se-main__welcome">
      <p class="se-main__eyebrow">불시착 이후, 남은 시간 10일</p>
      <h1>SCRAP<br><span>ESCAPE</span></h1>
      <p class="se-main__intro">도시에서 재료를 찾고, 비행기를 수리하세요.<br>다시 하늘로 돌아갈 수 있도록.</p>
      <button class="se-main__start" type="button" data-action="start">새 게임 시작 <span aria-hidden="true">↗</span></button>
      <p class="se-main__hint">버튼을 눌러 행동할 때만 시간이 흐릅니다.</p>
    </section>
    <div class="se-main__playing" hidden>
      <div class="se-main__clock-stack">
      <section class="se-main__clock se-main__hud" aria-label="시간과 생존 기한">
        <p class="se-main__eyebrow">현재 위치 · 숲의 불시착 지점</p>
        <div class="se-main__clock-line"><strong data-value="clock"></strong><span data-value="period"></span></div>
        <p data-value="deadline"></p><p class="se-main__storm" data-value="storm"></p>
      </section>
      <aside class="se-main__exploration-note" aria-label="탐색 안내" role="note" hidden><strong>탐색 안내</strong><p data-value="exploration-cost"></p><p data-value="exploration-rewards"></p><p class="se-main__exploration-risk" data-value="exploration-risk"></p></aside>
      </div>
      <nav class="se-main__places" aria-label="행동할 장소">
        <button class="se-main__place se-main__place--safe" type="button" data-panel="safe-zone"><span class="se-main__place-icon" aria-hidden="true">✈</span><span><small>FOREST CAMP</small><strong>안전지대</strong><em>비행기 · 보관 · 정비</em></span></button>
        <button class="se-main__place se-main__place--city" type="button" data-panel="exploration"><span class="se-main__place-icon" aria-hidden="true">⌖</span><span><small>ABANDONED CITY</small><strong>탐색 지역</strong><em>재료 · 보급품 · 숨겨진 방</em></span></button>
      </nav>
      <section class="se-main__status se-main__hud" aria-label="플레이어 상태">
        <h2>생존 상태 <span data-value="condition"></span></h2>
        <label>체력 <strong data-value="stamina"></strong><progress data-gauge="stamina" max="15"></progress></label>
        <label>오염도 <strong data-value="contamination"></strong><progress data-gauge="contamination" max="100"></progress></label>
        <p data-value="disease"></p><p class="se-main__muted" data-value="consecutive"></p>
      </section>
      <div class="se-main__inventory-stack">

      <section class="se-main__inventory se-main__hud" aria-label="휴대품">
        <h2>휴대 인벤토리 <span data-value="slots"></span></h2>
        <div class="se-main__slots"></div>
        <div class="se-main__supplies"><span data-value="cleaner"></span><span data-value="medical"></span><span data-value="keys"></span></div>
      </section>
      </div>
      <nav class="se-main__menu" aria-label="게임 메뉴"><button type="button" data-action="results" hidden>결과 보기</button><button type="button" data-action="restart">새 게임</button></nav>
    </div>`;
  const get = key => root.querySelector('[data-value="' + key + '"]');
  const abort = new AbortController();
  root.addEventListener('click', event => {
    const button = event.target.closest('button'); if (!button || button.disabled) return;
    if (button.dataset.action === 'start' || button.dataset.action === 'restart') {
      const current = context.getScreen();
      if ((button.dataset.action === 'restart' && !current) || (button.dataset.action === 'start' && current)) return;
      if (current?.state.status === 'RUNNING' && !globalThis.confirm('진행 중인 게임을 떠나 시작 화면으로 돌아갈까요?')) return;
      void context.dispatch({ type: 'START_GAME' });
    } else if (button.dataset.action === 'results') void context.navigate({ name: 'result' });
    else if (button.dataset.panel) void context.navigate({ name: button.dataset.panel });
  }, { signal: abort.signal });
  return {
    render(screen, flags) {
      const panel = context.getPanel(), atLocation = ['exploration-result', 'secret-room'].includes(panel?.name);
      const location = screen?.locations.find(l => l.locationId === panel?.locationId);
      root.dataset.scene = ['safe-zone','camp-action','aircraft-repair'].includes(panel?.name) ? 'forest-camp' : atLocation ? 'location' : ['exploration','exploration-choice','exploration-guide'].includes(panel?.name) ? 'city' : 'camp';
      const imageId = [1, 2, 3, 4, 5, 6].includes(location?.locationId) ? location.locationId : 1;
      const locationBackground = panel?.name === 'exploration-result'
        ? '/assets/locations/scavenge-result.png'
        : '/assets/locations/location-' + imageId + '.png';
      root.style.setProperty('--se-location-image', 'url("' + locationBackground + '")');
      root.querySelector('.se-main__clock .se-main__eyebrow').textContent = atLocation ? '현재 위치 · ' + (location?.name || '탐색지') : root.dataset.scene === 'forest-camp' ? '현재 위치 · 숲속 야영지' : root.dataset.scene === 'city' ? '현재 위치 · 오염 도시 입구' : '현재 위치 · 숲의 불시착 지점';
      root.querySelector('.se-main__welcome').hidden = Boolean(screen);
      root.querySelector('.se-main__playing').hidden = !screen;
      const showExplorationNote = false;
      root.querySelector('.se-main__exploration-note').hidden = !showExplorationNote;
      if (showExplorationNote) {
        const info = explorationSharedInfo(screen.locations, screen.state);
        get('exploration-cost').textContent = info.cost;
        get('exploration-rewards').textContent = info.rewards;
        get('exploration-risk').textContent = info.risk;
      }
      root.querySelectorAll('[data-action="start"], [data-action="restart"]').forEach(button => { button.disabled = flags.busy || flags.hasPending; });
      if (!screen) { root.dataset.night = 'false'; return; }
      const s = screen.state;
      root.dataset.night = String(s.night);
      get('clock').textContent = s.day + '일 · ' + String(s.hour).padStart(2, '0') + ':00';
      get('period').textContent = s.night ? '밤' : '낮';
      get('deadline').textContent = '생존 제한까지 ' + s.remainingHours + '시간';
      get('storm').textContent = s.currentStorm ? '방사능 폭풍 진행 중' : s.nextStorm ? '다음 폭풍까지 ' + (s.nextStorm.startHour - s.elapsedHours) + '시간' : '예정된 폭풍 없음';
      get('condition').textContent = s.status === 'RUNNING' ? '진행 중' : s.status === 'SUCCESS' ? '탈출 성공' : '생존 실패';
      get('stamina').textContent = s.stamina + ' / ' + s.maxStamina;
      get('contamination').textContent = s.contamination + ' / 100';
      root.querySelector('[data-gauge="stamina"]').value = s.stamina;
      root.querySelector('[data-gauge="stamina"]').max = s.maxStamina;
      root.querySelector('[data-gauge="contamination"]').value = s.contamination;
      get('disease').textContent = s.diseased ? '질병 · 치료까지 ' + s.treatmentRemainingHours + '시간' : '질병 없음';
      get('disease').classList.toggle('se-main__danger', s.diseased);
      get('consecutive').textContent = '연속 탐색 ' + s.consecutiveExplorations + '회';
      get('slots').textContent = s.inventory.length + ' / ' + s.inventoryCapacity;
      const slots = root.querySelector('.se-main__slots'); slots.replaceChildren();
      for (let i = 0; i < s.inventoryCapacity; i++) {
        const item = s.inventory[i], tile = document.createElement('div'); tile.className = 'se-main__item';
        if (!item) { tile.textContent = '빈칸'; tile.classList.add('se-main__item--empty'); }
        else {
          tile.dataset.condition = item.condition;
          const label = document.createElement('strong'), detail = document.createElement('small');
          label.textContent = item.name + ' ×' + item.quantity;
          detail.textContent = item.condition === 'DIRTY' ? '미세척 · ' + item.expiresInHours + 'h' : item.condition === 'CLEAN' ? '✓ 세척 완료' : '사용 불가';
          tile.dataset.urgency = item.condition === 'UNUSABLE' || item.condition === 'DIRTY' && item.expiresInHours <= 6 ? 'danger' : item.condition === 'DIRTY' && item.expiresInHours <= 12 ? 'warning' : 'normal';
          tile.append(itemIcon(item.materialId), label, detail);
        }
        slots.append(tile);
      }
      get('cleaner').textContent = '세척액 ' + s.cleanerAmount + '/' + s.cleanerCapacity;
      get('medical').textContent = '키트 ' + s.medicalKitAmount + '/' + s.medicalKitCapacity;
      get('keys').textContent = '열쇠 ' + s.secrets.keyAmount + '/' + s.secrets.keyCapacity;
      for (const [field,icon] of [['cleaner','cleaner'],['medical','medical'],['keys','key']]) get(field).prepend(itemIcon(icon));
      root.querySelector('[data-action="results"]').hidden = s.status === 'RUNNING';
    },
    destroy() { abort.abort(); root.replaceChildren(); }
  };
}
