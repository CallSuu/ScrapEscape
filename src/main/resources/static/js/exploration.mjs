import { itemIcon } from './item-icons.mjs';
import { applyActionAvailability } from './game-api.mjs';

export function explorationDeadlineRisk(card, state) {
  return card.durationHours >= state.remainingHours || Boolean(state.diseased &&
    state.treatmentRemainingHours !== null && card.durationHours >= state.treatmentRemainingHours);
}

export function explorationSharedInfo(locations, state) {
  const first = locations[0];
  if (!first) return { cost: '탐색할 장소가 없습니다.', rewards: '', risk: '' };
  return {
    cost: (state.night ? '밤 탐색' : '낮 탐색') + ' · 시간 ' + first.durationHours + 'h · 체력 ' + first.staminaCost +
      ' · 예상 오염 +' + first.expectedContaminationIncrease + ' (최대100) · ' +
      (state.diseased ? '질병 비용 포함' : '발병 ' + first.diseaseProbabilityAfterExploration + '%') +
      (first.stormExposureHours > 0 ? ' · 폭풍 노출 ' + first.stormExposureHours + 'h 포함' : ''),
    rewards: '재료 ' + first.rewards.map(r => r.quantity + '개 ' + r.probabilityPercent + '%').join(' / ') +
      ' · 세척액 ' + (first.cleanerRewards || []).map(r=>r.quantity+'개 '+r.probabilityPercent+'%').join(' / ') + ' · 열쇠 ' + state.secrets.keyProbabilityPercent +
      '% · 방 발견 ' + state.secrets.discoveryProbabilityPercent + '%',
    risk: locations.some(card => explorationDeadlineRisk(card, state)) ? '탐색 완료 시 생존 또는 치료 기한 초과 위험' : ''
  };
}

export function createExplorationChoiceView(root, context) {
  const abort = new AbortController(), cards = new Map();
  const element = (tag, className, text) => {
    const node = document.createElement(tag); node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  };
  const container = element('section', 'se-explore se-explore--choice');
  const grid = element('div', 'se-explore__grid');
  container.append(grid); root.replaceChildren(container);

  function createCard(locationId) {
    const card = element('article', 'se-explore__card'); card.dataset.location = String(locationId);
    const picture = element('div', 'se-explore__picture');
    const image = element('img', 'se-explore__image'); image.width = 160; image.height = 90;
    if ([1, 2, 3, 4, 5, 6].includes(locationId)) image.src = '/assets/locations/location-' + locationId + '.png';
    else image.hidden = true;

    image.addEventListener('error', () => { image.hidden = true; }, { signal: abort.signal });
    picture.append(image);
    const name = element('h3', 'se-explore__name'), description = element('p', 'se-explore__description');
    const materials = element('p', 'se-explore__materials'), supplies = element('p', 'se-explore__supplies');
    picture.append(name, supplies);
    const actions = element('div', 'se-explore__actions');
    const start = element('button', 'se-explore__start', '선택하기'); start.type = 'button'; start.dataset.action = 'explore'; start.dataset.location = String(locationId);
    const room = element('button', 'se-explore__room', '?'); room.type = 'button'; room.dataset.action = 'room'; room.dataset.location = String(locationId);
    const opened = element('span', 'se-explore__opened', '방 확인 완료');
    const reason = element('p', 'se-explore__reason');
    actions.append(start, room, opened);
    card.append(picture, description, materials, actions, reason); grid.append(card);
    return { card, name, image, description, materials, supplies, start, room, opened, reason };
  }
  root.addEventListener('click', event => {
    const button = event.target.closest('button'); if (!button || button.disabled) return;
    const screen = context.getScreen(), flags = context.getFlags();
    const location = screen?.locations.find(c => c.locationId === Number(button.dataset.location));
    if (!location) return;
    if (button.dataset.action === 'explore' && location.canExplore && !flags.busy && !flags.hasPending)
      void context.dispatch({ type: 'EXPLORE', locationId: location.locationId });
    else if (button.dataset.action === 'room' && location.secretRoom && !location.secretRoom.opened)
      void context.navigate({ name: 'secret-room', locationId: location.locationId });
  }, { signal: abort.signal });

  return {
    render(screen, flags) {
      const locations = (screen?.locations || []).filter(c => c.locationId === context.getPanel()?.locationId), present = new Set(locations.map(c => c.locationId));
      for (const [id, nodes] of cards) if (!present.has(id)) { nodes.card.remove(); cards.delete(id); }
      if (!screen) return;
      for (const location of locations) {
        if (!cards.has(location.locationId)) cards.set(location.locationId, createCard(location.locationId));
        const n = cards.get(location.locationId);
        n.name.textContent = location.name; n.image.alt = location.name + ' 주변 오염 도시 일러스트'; n.description.textContent = location.description;
        n.materials.replaceChildren();
        const caption=element('span','se-explore__materials-label','획득 가능한 재료');
        const candidates=element('span','se-explore__material-list');
        for(const material of location.materials) { const candidate=element('span','se-explore__material-name');candidate.append(itemIcon(material.materialId),element('span','',material.name));candidates.append(candidate); }
        n.materials.append(caption,candidates);
        n.description.title = location.description; n.materials.title = n.materials.textContent;
        n.supplies.textContent = location.medicalKitReward > 0 ? '의료 키트 ' + location.medicalKitReward + '개 확정 · 보유 한도 내' : '';
        n.supplies.hidden = !location.medicalKitReward;
        n.start.setAttribute('aria-label', location.name + ' 선택하기');
        const unavailable = applyActionAvailability(n.start, { allowed: location.canExplore, unavailableReason: location.unavailableReason }, flags);
        n.reason.textContent = unavailable || (explorationDeadlineRisk(location, screen.state) ? '탐색 완료 시 생존 또는 치료 기한 초과 위험' : ''); n.reason.hidden = !n.reason.textContent;
        n.card.classList.toggle('se-explore__card--unavailable', n.start.disabled);
        const room = location.secretRoom;
        n.room.hidden = !room; n.room.disabled = Boolean(room?.opened); n.opened.hidden = !room?.opened;
        n.room.setAttribute('aria-label', location.name + ' 기억한 위치 정보');
        n.room.title = room?.opened ? '이미 확인한 방입니다.' : '기억한 위치를 확인합니다.';
      }
    },
    destroy() { abort.abort(); cards.clear(); root.replaceChildren(); }
  };
}

const buildingPositions = { 1:[20,34], 2:[50,34], 3:[80,34], 4:[20,65], 5:[50,65], 6:[80,65] };
export function createExplorationView(root, context) {
  const abort = new AbortController(), sites = new Map();
  root.innerHTML = `<section class="se-city" aria-label="오염 도시의 탐색 건물"><button type="button" class="se-city__guide" data-city-guide>ⓘ 탐색 안내</button><div class="se-city__sites"></div></section>`;
  const container = root.querySelector('.se-city__sites');
  root.addEventListener('click', event => {
    const target = event.target.closest('button'); if (!target || !root.contains(target) || target.disabled) return;
    if (target.hasAttribute('data-city-guide')) { void context.navigate({name:'exploration-guide'}); return; }
    const locationId = Number(target.dataset.cityLocation), location = context.getScreen()?.locations.find(c=>c.locationId===locationId); if (!location) return;
    if (target.dataset.cityRoom) { if (location.secretRoom && !location.secretRoom.opened) void context.navigate({name:'secret-room',locationId}); }
    else void context.navigate({name:'exploration-choice',locationId});
  },{signal:abort.signal});
  return {
    render(screen) {
      for(const location of screen?.locations || []){
        let site=sites.get(location.locationId);
        if(!site){
          const node=document.createElement('div'), button=document.createElement('button'), name=document.createElement('strong'), hint=document.createElement('small'), room=document.createElement('button');
          node.className='se-city__site';node.dataset.citySite=location.locationId;const [x,y]=buildingPositions[location.locationId]||[50,50];node.style.left=x+'%';node.style.top=y+'%';button.type='button';button.className='se-city__building';button.dataset.cityLocation=location.locationId;hint.textContent='⌖ 수색하기';button.append(name,hint);room.type='button';room.className='se-city__room';room.dataset.cityLocation=location.locationId;room.dataset.cityRoom='true';room.textContent='?';node.append(button,room);container.append(node);site={node,button,name,room};sites.set(location.locationId,site);
        }
        site.name.textContent=location.name;site.button.setAttribute('aria-label',location.name+' 수색 정보 보기');site.room.hidden=!location.secretRoom;site.room.disabled=Boolean(location.secretRoom?.opened);site.room.setAttribute('aria-label',location.name+' 기억한 위치');site.room.title=location.secretRoom?.opened?'이미 확인한 방입니다.':'기억한 위치를 확인합니다.';
      }
      for(const [id,site] of sites)if(!screen?.locations.some(c=>c.locationId===id)){site.node.remove();sites.delete(id);}
    },
    destroy(){abort.abort();sites.clear();root.replaceChildren();}
  };
}

export function createExplorationGuideView(root, context) {
  root.innerHTML='<section class="se-exploration-guide"><p class="se-exploration-guide__intro">출발 시각과 현재 상태에 따른 탐색 정보입니다.</p><h3>소요 시간과 위험</h3><p data-guide="cost"></p><h3>획득 확률</h3><p data-guide="rewards"></p><p class="se-explore__reason" data-guide="risk"></p><p>재료가 0개여도 탐색은 완료됩니다. 휴대 공간이 부족한 재료는 현장에 남습니다. 비밀의 방 입장은 별도로 선택합니다.</p></section>';
  return { render(screen) { if(!screen)return;const info=explorationSharedInfo(screen.locations,screen.state);for(const key of ['cost','rewards','risk'])root.querySelector('[data-guide="'+key+'"]').textContent=info[key]; }, destroy(){root.replaceChildren();} };
}
