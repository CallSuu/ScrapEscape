import { createGameClient, GameApiError } from './game-api.mjs';
import { createMainView } from './main.mjs';

// The host owns server state and requests. Views only read this Context.
export function createAppController({ clientOptions = {}, onRender = () => {}, onPanelChange = () => {}, onMessage = () => {} } = {}) {
  let screen = null, flags = { busy: false, hasPending: false }, panel = null, navigation = 0, pendingAction = null;
  const terminalShown = new Set(), reported = new WeakSet();
  const render = () => onRender(screen, { ...flags });
  function changePanel(next) { panel = next; onPanelChange(next); render(); }
  function report(error) {
    if (!reported.has(error)) { reported.add(error); onMessage(error.message || '요청을 처리하지 못했습니다.'); }
  }
  const isExpedition = action => ['EXPLORE', 'OPEN_ROOM'].includes(action?.type);
  function showEnding() {
    if (!screen || screen.state.status === 'RUNNING' || terminalShown.has(screen.state.gameId)) return;
    const waitingForLoot = isExpedition(pendingAction) && pendingAction.navigation === navigation && pendingAction.gameId === screen.state.gameId;
    if (waitingForLoot || panel?.name === 'exploration-result') return;
    terminalShown.add(screen.state.gameId); navigation++; changePanel({ name: 'result' });
  }
  function showActionResult(action, receipt) {
    if (!action || action.navigation !== navigation || action.gameId !== screen?.state.gameId) return;
    if (isExpedition(action)) {
      navigation++;
      changePanel({ name: 'exploration-result', locationId: action.locationId, activity: action.type, receipt });
    } else if (action.type === 'DEFER_ROOM' && panel?.name === 'secret-room' && panel.locationId === action.locationId) {
      navigation++; changePanel({name:'exploration'});
    } else if (['REST','TREAT'].includes(action.type) &&
      panel?.name === 'camp-action' && screen.state.status === 'RUNNING') {
      navigation++; changePanel({ name:'safe-zone' });
    }
  }
  const client = createGameClient({ ...clientOptions,
    onBusy(next) { flags = next; render(); },
    onError: report,
    onSnapshot(next) {
      const differentGame = screen?.state.gameId !== next.state.gameId;
      screen = next;
      if (differentGame) { navigation++; changePanel(null); }
      showEnding();
      render();
    }
  });
  const context = {
    getScreen: () => screen,
    getFlags: () => ({ ...flags }),
    getPanel: () => panel,
    announce: onMessage,
    async dispatch(command) {
      try {
        if (command.type === 'ACK_EXPLORATION_RESULT') {
          if (client.busy || client.hasPending) throw new GameApiError('REQUEST_BUSY', '먼저 진행 중인 요청을 확인하세요.');
          if (!screen || panel?.name !== 'exploration-result') throw new GameApiError('NO_EXPLORATION_RESULT', '확인할 탐색 결과가 없습니다.');
          const completed = panel;
          navigation++;
          if (screen.state.status !== 'RUNNING') {
            terminalShown.add(screen.state.gameId); changePanel({ name: 'result' });
          } else {
            const found = completed.receipt.secretFindings;
            const room = screen.state.secrets.rooms.find(r => r.locationId === found?.roomLocationId);
            if (completed.activity === 'EXPLORE' && found?.roomDiscovered && room && !room.opened)
              changePanel({ name: 'secret-room', locationId: room.locationId, fromDiscovery: true });
            else changePanel({name:'exploration'});
          }
          return { ok: true, screen, result: null };
        }
        if (command.type === 'START_GAME' && screen) {
          if (client.busy) throw new GameApiError('REQUEST_BUSY', '처리 중입니다.');
          if (client.hasPending) throw new GameApiError('PENDING_REQUEST', '먼저 이전 요청의 결과를 확인하세요.');
          // Returning to the title is local; create a new game only on the next Start click.
          screen = null; navigation++; pendingAction = null; terminalShown.clear();
          changePanel(null); onMessage('');
          return { ok: true, screen: null, result: null };
        }
        const base = '/api/games/' + screen?.state.gameId;
        let response;
        if (command.type === 'RETRY') response = await client.retry();
        else if (command.type === 'REFRESH') {
          if (!screen) throw new GameApiError('NO_GAME', '게임을 먼저 시작하세요.');
          return { ok: true, screen: await client.refresh(), result: null };
        }
        else {
          const requests = {
            START_GAME: ['POST', '/api/games'],
            EXPLORE: ['POST', base + '/explorations', { locationId: command.locationId }],
            MOVE_ITEM: ['PATCH', base + '/items/' + command.batchId + '/storage', { storage: command.storage }],
            MOVE_ITEMS: ['PATCH', base + '/items/storage', { batchIds: command.batchIds, storage: command.storage }],
            CLEAN_ITEMS: ['POST', base + '/items/clean', { batchIds: command.batchIds }],
            DISCARD_ITEMS: ['DELETE', base + '/items', { batchIds: command.batchIds }],
            CLEAN_ITEM: ['POST', base + '/items/' + command.batchId + '/clean'],
            DISCARD_ITEM: ['DELETE', base + '/items/' + command.batchId],
            REPAIR_PART: ['POST', base + '/repairs/' + command.partId],
            REST: ['POST', base + '/rests', { hours: command.hours }],
            TREAT: ['POST', base + '/treatments'],
            ESCAPE: ['POST', base + '/escape'],
            DEFER_ROOM: ['POST', base + '/secret-rooms/' + command.locationId + '/defer'],
            OPEN_ROOM: ['POST', base + '/secret-rooms/' + command.locationId + '/open', { mode: command.mode }]
          };
          if (!requests[command.type]) throw new GameApiError('INVALID_ACTION', '알 수 없는 행동입니다.');
          if (client.busy) throw new GameApiError('REQUEST_BUSY', '처리 중입니다.');
          if (client.hasPending) throw new GameApiError('PENDING_REQUEST', '먼저 이전 요청의 결과를 확인하세요.');
          if (!screen && command.type !== 'START_GAME') throw new GameApiError('NO_GAME', '게임을 먼저 시작하세요.');
          pendingAction = { type: command.type, locationId: command.locationId, navigation, gameId: screen?.state.gameId };
          response = await client.act(...requests[command.type]);
        }
        const action = pendingAction; pendingAction = null;
        showActionResult(action, response.result); showEnding();
        const receipt = (response.result.loot?.materials || []).map(material =>
          material.name + ': 획득 ' + material.collectedQuantity + '개' +
          (material.quantity > material.collectedQuantity ? ' · 남김 ' + (material.quantity - material.collectedQuantity) + '개' : ''));
        onMessage([response.result.message || '비행기가 불시착했습니다. 주변을 살피고 탈출을 준비하세요.', ...receipt].join('\n'));
        return { ok: true, ...response };
      } catch (error) {
        if (!client.hasPending) pendingAction = null;
        showEnding();
        report(error);
        return { ok: false, error: { code: error.code || 'CLIENT_ERROR', message: error.message, retryable: Boolean(error.retryable) } };
      }
    },
    async navigate(target) {
      if (target === null && panel?.name === 'exploration-result') { await context.dispatch({ type: 'ACK_EXPLORATION_RESULT' }); return; }
      if (target === null && panel?.name === 'exploration-choice') { navigation++; changePanel({ name:'exploration' }); return; }
      if (target === null && ['camp-action', 'aircraft-repair'].includes(panel?.name)) {
        navigation++; changePanel({ name: 'safe-zone' }); return;
      }
      const revision = ++navigation;
      if (target === null) {
        const previous = panel;
        const room = screen?.state.secrets.rooms.find(r => r.locationId === previous?.locationId);
        const defer = previous?.name === 'secret-room' && room?.immediateAvailable && !room.opened &&
          screen.state.status === 'RUNNING' && !flags.busy && !flags.hasPending;
        changePanel(previous?.name==='secret-room'?{name:'exploration'}:null);
        if (defer) await context.dispatch({ type: 'DEFER_ROOM', locationId: previous.locationId });
        return;
      }
      if (!screen) { onMessage('게임을 먼저 시작하세요.'); return; }
      if (!['safe-zone', 'camp-action', 'aircraft-repair', 'exploration', 'exploration-choice', 'secret-room', 'result'].includes(target.name) || target.name === 'camp-action' && !['storage','rest','treatment'].includes(target.area)) { onMessage('알 수 없는 화면입니다.'); return; }
      if (!flags.busy && !flags.hasPending) {
        const response = await context.dispatch({ type: 'REFRESH' });
        if (!response.ok || revision !== navigation) return;
      }
      if (revision === navigation) changePanel(target);
    }
  };
  return context;
}

function mountApp() {
  const mainRoot = document.querySelector('#se-main'), overlay = document.querySelector('#se-overlay');
  const modalContent = document.querySelector('#se-panel-content'), title = document.querySelector('#se-panel-title');
  const page = document.querySelector('#se-exploration-page'), pageContent = document.querySelector('#se-exploration-content');
  const pageTitle = document.querySelector('#se-exploration-title'), back = document.querySelector('#se-exploration-back');
  const app = document.querySelector('#se-app'), close = document.querySelector('#se-panel-close'), retry = document.querySelector('#se-retry');
  const message = document.querySelector('#se-messages'), footer = document.querySelector('.se-dialog__footer');
  let mainView, view, backgroundView, viewVersion = 0, returnFocus, previousPanel;
  const modules = {
    'safe-zone': ['camp', 'createCampView'],
    'camp-action': ['safe-zone', 'createSafeZoneView'],
    'aircraft-repair': ['aircraft-repair', 'createAircraftRepairView'],
    exploration: ['exploration', 'createExplorationView'],
    'exploration-choice': ['exploration', 'createExplorationChoiceView'],
    'exploration-result': ['exploration-result', 'createExplorationResultView'],
    'secret-room': ['secret-room', 'createSecretRoomView'],
    result: ['result', 'createResultView']
  };
  const areaNames = { storage:'비행기 보관함', rest:'휴식 공간', treatment:'치료 공간' };
  function updateFooter() {
    footer.hidden = retry.hidden && (app.dataset.page === 'result' || !message.classList.contains('se-message--visible'));
  }
  const context = createAppController({
    onMessage(text) { message.textContent = text; message.classList.toggle('se-message--visible', Boolean(text)); updateFooter(); },
    onRender(screen, flags) {
      app.dataset.pending = String(flags.hasPending);
      mainView?.render(screen, flags); backgroundView?.render(screen, flags); view?.render(screen, flags);
      retry.hidden = !flags.hasPending; retry.disabled = flags.busy; retry.textContent = flags.busy ? '결과 확인 중…' : '다시 시도';
      updateFooter();
      mainRoot.querySelectorAll('[data-panel]').forEach(b => { b.disabled = !overlay.hidden || !page.hidden; });
    },
    async onPanelChange(panel) {
      const revision = ++viewVersion, previous = previousPanel; previousPanel = panel;
      view?.destroy(); backgroundView?.destroy(); view = null; backgroundView = null;
      modalContent.replaceChildren(); pageContent.replaceChildren();
      const hadSurface = !overlay.hidden || !page.hidden;
      const campDialog = panel?.name === 'camp-action', cityDialog = panel?.name === 'exploration-choice';
      const isScene = ['safe-zone', 'aircraft-repair', 'exploration', 'exploration-result', 'secret-room'].includes(panel?.name);
      const camp = ['safe-zone', 'camp-action', 'aircraft-repair'].includes(panel?.name);
      app.dataset.page = panel?.name || 'camp';
      app.dataset.modalArea = campDialog ? panel.area : '';
      const heading = panel?.name === 'safe-zone' || campDialog ? '숲속 야영지' : panel?.name === 'aircraft-repair' ? '비행기 정비' : ['exploration','exploration-choice'].includes(panel?.name) ? '오염 도시 탐색' : panel?.name === 'secret-room' ? (panel.fromDiscovery ? '비밀의 방을 발견했다' : '기억한 비밀의 방') : panel?.activity === 'OPEN_ROOM' ? '비밀의 방 결과' : '탐색 결과';
      document.title = panel ? 'Scrap Escape · ' + (panel.name === 'result' ? '게임 결과' : heading) : 'Scrap Escape';
      if (!panel) {
        overlay.hidden = true; page.hidden = true; page.inert = false; mainRoot.inert = false;
        app.append(message, retry);
        queueMicrotask(() => { if (overlay.hidden && page.hidden && returnFocus?.isConnected && !returnFocus.disabled) returnFocus.focus(); });
        return;
      }
      if (!hadSurface) returnFocus = document.activeElement;
      overlay.hidden = isScene; page.hidden = !isScene && !campDialog && !cityDialog; page.inert = !overlay.hidden; mainRoot.inert = !overlay.hidden;
      (overlay.hidden ? app : footer).append(message, retry);
      back.hidden = panel.name === 'exploration-result';
      back.textContent = panel.name === 'aircraft-repair' ? '← 야영지로 돌아가기' : ['safe-zone','camp-action','secret-room'].includes(panel.name) ? '← 메인으로 돌아가기' : '← 돌아가기';
      if (!page.hidden) {
        const location = context.getScreen()?.locations.find(l => l.locationId === panel.locationId);
        document.querySelector('#se-page-location').textContent = camp ? '숲의 불시착 지점 · 안전지대' : location?.name || '오염 도시 입구';
        pageTitle.textContent = heading;
      }
      if (!overlay.hidden) { title.textContent = campDialog ? areaNames[panel.area] : cityDialog ? (context.getScreen()?.locations.find(l=>l.locationId===panel.locationId)?.name || '탐색 지역') + ' · 수색 정보' : '게임 결과'; title.focus(); }
      else pageTitle.focus();
      async function load(name, content) {
        const loading = document.createElement('p'); loading.textContent = '화면을 준비하고 있습니다.'; content.append(loading);
        try {
          const [file, factory] = modules[name], module = await import('./' + file + '.mjs');
          if (revision !== viewVersion) return null;
          if (!document.querySelector('link[data-view="' + file + '"]')) {
            const css = document.createElement('link'); css.rel = 'stylesheet'; css.href = '/css/' + file + '.css'; css.dataset.view = file; document.head.append(css);
          }
          content.replaceChildren(); const next = module[factory](content, context); next.render(context.getScreen(), context.getFlags()); return next;
        } catch {
          if (revision !== viewVersion) return null;
          content.replaceChildren(loading); loading.textContent = '화면을 불러오지 못했습니다. 돌아간 뒤 다시 시도해주세요.'; return null;
        }
      }
      if (campDialog || cityDialog) {
        const background = await load(campDialog ? 'safe-zone' : 'exploration', pageContent);
        if (revision !== viewVersion) return;
        backgroundView = background;
      }
      const next = await load(panel.name, isScene ? pageContent : modalContent);
      if (revision !== viewVersion) return;
      view = next;
      if (panel.name === 'exploration' && previous?.name === 'exploration-choice') pageContent.querySelector('.se-city__building[data-city-location="' + previous.locationId + '"]')?.focus();
      if (panel.name === 'safe-zone' && ['camp-action','aircraft-repair'].includes(previous?.name)) {
        const area = previous.name === 'aircraft-repair' ? 'repair' : previous.area;
        pageContent.querySelector('[data-camp-area="' + area + '"]')?.focus();
      }
    }
  });
  mainView = createMainView(mainRoot, context); mainView.render(null, context.getFlags());
  close.addEventListener('click', () => { void context.navigate(null); });
  back.addEventListener('click', () => { void context.navigate(null); });
  retry.addEventListener('click', () => { void context.dispatch({ type: 'RETRY' }); });
  document.addEventListener('keydown', event => {
    if (overlay.hidden && page.hidden) return;
    if (event.key === 'Escape') { event.preventDefault(); void context.navigate(null); }
    if (event.key === 'Tab' && !overlay.hidden) {
      const focusable = [...overlay.querySelectorAll('button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), [tabindex="0"]')].filter(element => element.getClientRects().length > 0 && getComputedStyle(element).visibility !== 'hidden');
      const first = focusable[0], last = focusable.at(-1);
      if (event.shiftKey && (document.activeElement === first || document.activeElement === title)) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    }
  });
}
if (typeof document !== 'undefined' && document.querySelector('#se-app')) mountApp();
