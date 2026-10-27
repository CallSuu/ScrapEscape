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
      navigation++; changePanel(null);
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
            else changePanel(null);
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
      const revision = ++navigation;
      if (target === null) {
        const previous = panel;
        const room = screen?.state.secrets.rooms.find(r => r.locationId === previous?.locationId);
        const defer = previous?.name === 'secret-room' && room?.immediateAvailable && !room.opened &&
          screen.state.status === 'RUNNING' && !flags.busy && !flags.hasPending;
        changePanel(null);
        if (defer) await context.dispatch({ type: 'DEFER_ROOM', locationId: previous.locationId });
        return;
      }
      if (!screen) { onMessage('게임을 먼저 시작하세요.'); return; }
      if (!['safe-zone', 'exploration', 'secret-room', 'result'].includes(target.name)) { onMessage('알 수 없는 화면입니다.'); return; }
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
  const app = document.querySelector('#se-app');
  const close = document.querySelector('#se-panel-close'), retry = document.querySelector('#se-retry');
  const message = document.querySelector('#se-messages');
  let mainView, view, viewVersion = 0, returnFocus;
  const names = { 'safe-zone': '안전지대', exploration: '탐색 지역', 'secret-room': '?', result: '게임 결과' };
  const modules = {
    'safe-zone': ['safe-zone', 'createSafeZoneView'],
    exploration: ['exploration', 'createExplorationView'],
    'exploration-result': ['exploration-result', 'createExplorationResultView'],
    'secret-room': ['secret-room', 'createSecretRoomView'],
    result: ['result', 'createResultView']
  };
  const context = createAppController({
    onMessage(text) { message.textContent = text; message.classList.toggle('se-message--visible', Boolean(text)); },
    onRender(screen, flags) {
      app.dataset.pending = String(flags.hasPending);
      mainView?.render(screen, flags);
      view?.render(screen, flags);
      retry.hidden = !flags.hasPending;
      retry.disabled = flags.busy;
      retry.textContent = flags.busy ? '결과 확인 중…' : '다시 시도';
      mainRoot.querySelectorAll('[data-panel]').forEach(b => { b.disabled = !overlay.hidden || !page.hidden; });
    },
    async onPanelChange(panel) {
      const revision = ++viewVersion;
      view?.destroy(); view = null; modalContent.replaceChildren(); pageContent.replaceChildren();
      const hadSurface = !overlay.hidden || !page.hidden;
      const isExpeditionPage = ['exploration', 'exploration-result', 'secret-room'].includes(panel?.name);
      app.dataset.page = isExpeditionPage ? panel.name : 'camp';
      document.title = isExpeditionPage ? 'Scrap Escape · ' + (panel.name === 'exploration' ? '탐색 갈림길' : panel.name === 'secret-room' ? '비밀의 방' : '탐색 결과') : 'Scrap Escape';
      if (!panel) {
        overlay.hidden = true; page.hidden = true;
        queueMicrotask(() => { if (overlay.hidden && page.hidden && returnFocus?.isConnected && !returnFocus.disabled) returnFocus.focus(); });
        return;
      }
      if (!hadSurface) returnFocus = document.activeElement;
      overlay.hidden = isExpeditionPage; page.hidden = !isExpeditionPage;
      const content = isExpeditionPage ? pageContent : modalContent;
      back.hidden = panel.name === 'exploration-result';
      back.textContent = panel.name === 'secret-room' ? '← 메인으로 돌아가기' : '← 돌아가기';
      if (isExpeditionPage) {
        const location = context.getScreen()?.locations.find(l => l.locationId === panel.locationId);
        document.querySelector('#se-page-location').textContent = location?.name || '오염 도시 입구';
        pageTitle.textContent = panel.name === 'exploration' ? '어느 길로 갈까?' : panel.name === 'secret-room' ? (panel.fromDiscovery ? '비밀의 방을 발견했다' : '기억한 비밀의 방') : panel.activity === 'OPEN_ROOM' ? '비밀의 방 결과' : '탐색 결과';
        pageTitle.focus();
      }
      else { title.textContent = names[panel.name]; title.focus(); }
      const loading = document.createElement('p'); loading.textContent = '화면을 준비하고 있습니다.'; content.append(loading);
      try {
        const [file, factory] = modules[panel.name];
        const module = await import('./' + file + '.mjs');
        if (revision !== viewVersion) return;
        if (!document.querySelector('link[data-view="' + file + '"]')) {
          const css = document.createElement('link'); css.rel = 'stylesheet'; css.href = '/css/' + file + '.css'; css.dataset.view = file;
          document.head.append(css);
        }
        content.replaceChildren(); view = module[factory](content, context);
        view.render(context.getScreen(), context.getFlags());
      } catch {
        if (revision !== viewVersion) return;
        content.replaceChildren(loading);
        loading.textContent = ['safe-zone', 'result'].includes(panel.name)
          ? '팀원이 담당할 화면입니다. 모듈이 준비되면 이 위치에 연결됩니다.'
          : '이 선택창은 다음 개발 단계에서 연결합니다.';
      }
    }
  });
  mainView = createMainView(mainRoot, context);
  mainView.render(null, context.getFlags());
  close.addEventListener('click', () => { void context.navigate(null); });
  back.addEventListener('click', () => { void context.navigate(null); });
  retry.addEventListener('click', () => { void context.dispatch({ type: 'RETRY' }); });
  document.addEventListener('keydown', event => {
    if (overlay.hidden && page.hidden) return;
    if (event.key === 'Escape') { event.preventDefault(); void context.navigate(null); }
    if (event.key === 'Tab' && !overlay.hidden) {
      const focusable = [...overlay.querySelectorAll('button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), [tabindex="0"]')]
        .filter(element => element.getClientRects().length > 0 && getComputedStyle(element).visibility !== 'hidden');
      const first = focusable[0], last = focusable.at(-1);
      if (event.shiftKey && (document.activeElement === first || document.activeElement === title)) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    }
  });
}
if (typeof document !== 'undefined' && document.querySelector('#se-app')) mountApp();
