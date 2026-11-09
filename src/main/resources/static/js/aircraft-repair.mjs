import { itemIcon } from './item-icons.mjs';
import { applyActionAvailability } from './game-api.mjs';
import { actionSummary } from './safe-zone.mjs';
const positions = { 1:{x:35.8,y:37}, 2:{x:64.2,y:42}, 3:{x:50,y:56}, 4:{x:68,y:55}, 5:{x:50,y:83}, 6:{x:50,y:23}, 7:{x:29,y:55} };
export function repairMarker(partId) { return { ...(positions[partId] || { x:50, y:50 }) }; }

export function createAircraftRepairView(root, context) {
  const abort = new AbortController(); let gameId, selectedId = null, signature = '';
  root.innerHTML = `<section class="se-aircraft" aria-label="비행기 부위별 수리">
    <div class="se-aircraft__diagram"><div class="se-aircraft__map"><img src="/assets/safe-zone/aircraft-top.svg" alt="푸른색과 흰색 개인 비행기 상면도"><div class="se-aircraft__markers"></div></div><p class="se-aircraft__legend">⚒ 미수리 · ✓ 수리 완료<br>표시를 눌러 필요한 재료를 확인하세요.</p></div>
    <aside class="se-aircraft__detail"><div class="se-aircraft__selected"><p class="se-aircraft__eyebrow">선택한 정비 위치</p><h3 data-repair-info="name"></h3><p data-repair-info="state"></p><ul class="se-aircraft__ingredients"></ul><p data-repair-info="preview"></p><button type="button" data-repair-action="repair">수리·장착</button><p class="se-aircraft__reason" data-repair-info="reason"></p></div>
      <div class="se-aircraft__escape"><h3 data-repair-info="count"></h3><p class="se-aircraft__muted">선택된 모든 부품을 수리한 뒤 탈출할 수 있습니다.</p><p data-repair-info="escape-preview"></p><button type="button" data-repair-action="escape">탈출하기</button><p class="se-aircraft__reason" data-repair-info="escape-reason"></p></div>
    </aside></section>`;
  const info = key => root.querySelector('[data-repair-info="' + key + '"]'), button = action => root.querySelector('[data-repair-action="' + action + '"]');
  root.addEventListener('click', event => {
    const target = event.target.closest('button'); if (!target || !root.contains(target) || target.disabled) return;
    const screen = context.getScreen(); if (!screen) return;
    if (target.dataset.repairPart) { selectedId = Number(target.dataset.repairPart); view.render(screen, context.getFlags()); return; }
    const flags = context.getFlags(); if (flags.busy || flags.hasPending) return;
    if (target.dataset.repairAction === 'escape' && screen.actions.escape.allowed) void context.dispatch({ type:'ESCAPE' });
    if (target.dataset.repairAction === 'repair' && screen.actions.repairs.find(p=>p.partId===selectedId)?.preview.allowed) void context.dispatch({ type:'REPAIR_PART', partId:selectedId });
  }, { signal:abort.signal });
  const view = {
    render(screen, flags) {
      if (!screen) return;
      const s = screen.state;
      if (gameId !== s.gameId) { gameId = s.gameId; selectedId = null; signature = ''; }
      if (!s.repairs.some(p=>p.partId===selectedId)) selectedId = s.repairs.find(p=>!p.repaired)?.partId || s.repairs[0]?.partId;
      const ids = JSON.stringify(s.repairs.map(p=>p.partId)), list = root.querySelector('.se-aircraft__markers');
      if (signature !== ids) {
        signature = ids; list.replaceChildren();
        for (const part of s.repairs) {
          const marker = document.createElement('button'), icon = document.createElement('span'), label = document.createElement('small');
          const pos = repairMarker(part.partId); marker.type='button'; marker.className='se-aircraft__marker'; marker.dataset.repairPart=part.partId; marker.style.left=pos.x+'%'; marker.style.top=pos.y+'%'; icon.setAttribute('aria-hidden','true'); label.textContent=part.name; marker.append(icon,label); list.append(marker);
        }
      }
      for (const part of s.repairs) {
        const marker = list.querySelector('[data-repair-part="'+part.partId+'"]'); marker.dataset.repaired=String(part.repaired); marker.setAttribute('aria-pressed',String(part.partId===selectedId)); marker.setAttribute('aria-label',part.name+' · '+(part.repaired?'수리 완료':'미수리')+' · 재료 확인'); marker.querySelector('span').textContent=part.repaired?'✓':'⚒';
      }
      const part = s.repairs.find(p=>p.partId===selectedId), preview=screen.actions.repairs.find(p=>p.partId===selectedId)?.preview;
      info('name').textContent=part?.name || '수리 위치를 선택하세요'; info('state').textContent=part?.repaired?'수리·장착 완료':'미수리 · 양쪽 저장소의 세척 완료 재료 사용';
      const ingredients=root.querySelector('.se-aircraft__ingredients'); ingredients.replaceChildren();
      for(const ingredient of part?.ingredients||[]){const li=document.createElement('li');li.textContent=ingredient.name+' '+ingredient.availableQuantity+' / '+ingredient.requiredQuantity;li.dataset.enough=String(ingredient.availableQuantity>=ingredient.requiredQuantity);li.prepend(itemIcon(ingredient.materialId));ingredients.append(li);}
      info('preview').textContent=part?.repaired?'이 부품은 이미 장착했습니다.':actionSummary(preview,screen);
      info('reason').textContent=applyActionAvailability(button('repair'),preview||{allowed:false,unavailableReason:'수리 위치를 선택하세요.'},flags)||'';
      info('count').textContent='수리 완료 '+s.repairs.filter(p=>p.repaired).length+' / '+s.repairs.length;
      info('escape-preview').textContent=actionSummary(screen.actions.escape,screen); info('escape-reason').textContent=applyActionAvailability(button('escape'),screen.actions.escape,flags)||'';
    },
    destroy(){abort.abort();root.replaceChildren();}
  };return view;
}
