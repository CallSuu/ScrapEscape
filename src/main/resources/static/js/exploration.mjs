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
      ' · 세척액 ' + first.cleanerProbabilityPercent + '% · 열쇠 ' + state.secrets.keyProbabilityPercent +
      '% · 방 발견 ' + state.secrets.discoveryProbabilityPercent + '%',
    risk: locations.some(card => explorationDeadlineRisk(card, state)) ? '탐색 완료 시 생존 또는 치료 기한 초과 위험' : ''
  };
}

export function createExplorationView(root, context) {
  const abort = new AbortController(), cards = new Map();
  const element = (tag, className, text) => {
    const node = document.createElement(tag); node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  };
  const container = element('section', 'se-explore');
  const common = element('div', 'se-explore__common');
  const summary = element('p', 'se-explore__summary'), rewards = element('p', 'se-explore__rewards');
  const risk = element('p', 'se-explore__risk');
  common.append(summary, rewards, risk);
  const grid = element('div', 'se-explore__grid');
  container.append(common, grid); root.replaceChildren(container);

  function createCard(locationId) {
    const card = element('article', 'se-explore__card'); card.dataset.location = String(locationId);
    const picture = element('div', 'se-explore__picture');
    const image = element('img', 'se-explore__image'); image.width = 160; image.height = 90;
    if ([1, 2, 3, 4, 5, 6].includes(locationId)) image.src = '/assets/locations/location-' + locationId + '.svg';
    else image.hidden = true;
    image.addEventListener('error', () => { image.hidden = true; }, { signal: abort.signal });
    picture.append(image);
    const name = element('h3', 'se-explore__name'), description = element('p', 'se-explore__description');
    const materials = element('p', 'se-explore__materials'), supplies = element('p', 'se-explore__supplies');
    picture.append(name, supplies);
    const actions = element('div', 'se-explore__actions');
    const start = element('button', 'se-explore__start', '탐색하기'); start.type = 'button'; start.dataset.action = 'explore'; start.dataset.location = String(locationId);
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
      const locations = screen?.locations || [], present = new Set(locations.map(c => c.locationId));
      for (const [id, nodes] of cards) if (!present.has(id)) { nodes.card.remove(); cards.delete(id); }
      if (!screen) { summary.textContent = '게임을 먼저 시작하세요.'; return; }
      const state = screen.state;
      const info = explorationSharedInfo(locations, state);
      summary.textContent = info.cost; rewards.textContent = info.rewards;
      risk.textContent = info.risk; risk.hidden = !info.risk;
      for (const location of locations) {
        if (!cards.has(location.locationId)) cards.set(location.locationId, createCard(location.locationId));
        const n = cards.get(location.locationId);
        n.name.textContent = location.name; n.image.alt = location.name + ' 임시 일러스트'; n.description.textContent = location.description;
        n.materials.textContent = '재료 후보 · ' + location.materials.map(m => m.name).join(' · ');
        n.description.title = location.description; n.materials.title = n.materials.textContent;
        n.supplies.textContent = location.medicalKitReward > 0 ? '의료 키트 ' + location.medicalKitReward + '개 확정 · 보유 한도 내' : '';
        n.supplies.hidden = !location.medicalKitReward;
        n.start.setAttribute('aria-label', location.name + ' 탐색하기');
        const unavailable = applyActionAvailability(n.start, { allowed: location.canExplore, unavailableReason: location.unavailableReason }, flags);
        n.reason.textContent = unavailable || ''; n.reason.hidden = !unavailable;
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
