import { createAppController } from '../js/app.mjs';
import { createSafeZoneView } from '../js/safe-zone.mjs';
import { createResultView } from '../js/result.mjs';
const find = id => document.getElementById('dev-' + id);
let view;
const context = createAppController({
  onRender(screen, flags) {
    view?.render(screen, flags);
    const s = screen?.state, blocked = flags.busy || flags.hasPending;
    find('start').disabled = blocked; find('start').textContent = s ? '시작 화면으로 초기화' : '테스트 판 시작';
    find('safe').disabled = !s; find('result').disabled = !s || s.status === 'RUNNING';
    find('explore').disabled = !s || blocked || !screen.locations.find(l => l.locationId === 6)?.canExplore;
    find('rest').disabled = !s || blocked || !screen.actions.rests.find(r => r.hours === 6)?.preview.allowed;
    find('retry').hidden = !flags.hasPending; find('retry').disabled = flags.busy;
    find('status').textContent = s ? s.day + '일 ' + s.hour + ':00 · 체력 ' + s.stamina + '/' + s.maxStamina + ' · 오염 ' + s.contamination + ' · ' + s.status : '시작 전입니다.';
  },
  onPanelChange(panel) {
    view?.destroy(); view = null; find('content').replaceChildren();
    // Expeditions are test-data preparation here; the production host owns their scenes.
    find('shell').hidden = !['safe-zone', 'result'].includes(panel?.name);
    if (find('shell').hidden) return;
    find('title').textContent = panel.name === 'safe-zone' ? '안전지대' : '최종 결과';
    view = (panel.name === 'safe-zone' ? createSafeZoneView : createResultView)(find('content'), context);
    find('title').focus();
  },
  onMessage(text) { find('message').textContent = text; }
});
find('start').addEventListener('click', async () => {
  if (context.getScreen()?.state.status === 'RUNNING' && !window.confirm('현재 판을 떠나 시작 화면으로 돌아갈까요?')) return;
  const result = await context.dispatch({ type: 'START_GAME' });
  if (result.ok && result.screen) await context.navigate({ name: 'safe-zone' });
});
find('safe').addEventListener('click', () => { void context.navigate({ name: 'safe-zone' }); });
find('result').addEventListener('click', () => { void context.navigate({ name: 'result' }); });
find('close').addEventListener('click', () => { void context.navigate(null); find('safe').focus(); });
find('retry').addEventListener('click', () => { void context.dispatch({ type: 'RETRY' }); });
find('explore').addEventListener('click', async () => { const r = await context.dispatch({ type: 'EXPLORE', locationId: 6 }); if (r.ok && context.getPanel()?.name === 'exploration-result') { await context.dispatch({ type: 'ACK_EXPLORATION_RESULT' }); await context.navigate({ name: 'safe-zone' }); } });
find('rest').addEventListener('click', () => { void context.dispatch({ type: 'REST', hours: 6 }); });
