export class GameApiError extends Error {
  constructor(code, message, status = 0, retryable = false) {
    super(message);
    Object.assign(this, { code, status, retryable });
  }
}

// One client per game screen. Retry context intentionally lives only in this page.
export function createGameClient({ fetchImpl = globalThis.fetch, randomUUID = () => globalThis.crypto.randomUUID(),
  timeoutMs = 15000, onBusy = () => {}, onError = () => {}, onSnapshot = () => {} } = {}) {
  let busy = false, pending = null, gameId = null;
  const fail = (code, message) => new GameApiError(code, message);
  function idle() {
    if (busy) throw fail('REQUEST_BUSY', '처리 중입니다.');
  }
  function setBusy(value) { busy = value; onBusy({ busy, hasPending: pending !== null }); }
  async function request(method, path, body, key) {
    const abort = new AbortController();
    const timeout = setTimeout(() => abort.abort(), timeoutMs);
    try {
      const headers = {};
      if (body !== undefined) headers['Content-Type'] = 'application/json';
      if (key) headers['Idempotency-Key'] = key;
      const r = await fetchImpl(path, { method, headers, body, signal: abort.signal });
      let data;
      try { data = await r.json(); }
      catch { throw new GameApiError('INVALID_RESPONSE', '서버 응답을 확인하지 못했습니다. 다시 시도하세요.', r.status, true); }
      if (!r.ok) throw new GameApiError(data.code || 'HTTP_ERROR', data.message || '요청을 처리하지 못했습니다.', r.status, r.status >= 500);
      return data;
    } catch (e) {
      if (e instanceof GameApiError) throw e;
      throw new GameApiError('NETWORK_ERROR', '연결이 끊겼거나 응답 시간이 지났습니다. 다시 시도하세요.', 0, true);
    } finally { clearTimeout(timeout); }
  }
  async function snapshot(id) {
    const data = await request('GET', '/api/games/' + encodeURIComponent(id) + '/screen');
    if (data?.state?.gameId !== id || !data.locations || !data.actions)
      throw new GameApiError('INVALID_RESPONSE', '화면 정보를 확인하지 못했습니다. 다시 시도하세요.', 0, true);
    gameId = id;
    onSnapshot(data);
    return data;
  }
  async function complete() {
    try {
      if (!pending.result) {
        pending.result = await request(pending.method, pending.path, pending.body, pending.key);
        const id = pending.result?.gameId || pending.result?.state?.gameId;
        if (!id) { pending.result = null; throw new GameApiError('INVALID_RESPONSE', '행동 결과를 확인하지 못했습니다. 다시 시도하세요.', 0, true); }
        pending.gameId = id;
      }
    } catch (e) {
      if (!e.retryable) pending = null;
      throw e;
    }
    // Even a replay may contain an old state. Refresh before allowing another action.
    let screen;
    try { screen = await snapshot(pending.gameId); }
    catch (e) { throw new GameApiError('REFRESH_REQUIRED', '행동은 처리됐지만 화면을 갱신하지 못했습니다. 다시 시도하면 화면만 갱신합니다.', e.status, true); }
    const result = pending.result;
    pending = null;
    return { result, screen };
  }
  async function run(action) {
    setBusy(true);
    try { return await action(); }
    catch (e) {
      if (e.status === 409 && pending === null && gameId) {
        // A changed game can invalidate a visible card. Keep the domain error if refresh also fails.
        try { await snapshot(gameId); } catch { /* Retry refresh when the panel is opened again. */ }
      }
      onError(e);
      throw e;
    }
    finally { setBusy(false); }
  }
  return {
    get busy() { return busy; },
    get hasPending() { return pending !== null; },
    async load(id) {
      idle();
      if (pending) throw fail('PENDING_REQUEST', '먼저 이전 요청의 결과를 확인하세요.');
      return run(() => snapshot(id));
    },
    async act(method, path, body) {
      idle();
      if (pending) throw fail('PENDING_REQUEST', '먼저 이전 요청의 결과를 확인하세요.');
      if (!['POST', 'PATCH', 'DELETE'].includes(method) || !path.startsWith('/api/games'))
        throw fail('INVALID_ACTION', '게임 변경 요청을 확인하세요.');
      pending = { method, path, body: body === undefined ? undefined : JSON.stringify(body), key: randomUUID(), result: null };
      return run(complete);
    },
    async retry() {
      idle();
      if (!pending) throw fail('NO_PENDING_REQUEST', '다시 시도할 요청이 없습니다.');
      return run(complete);
    },
    async refresh() {
      if (!gameId) throw fail('NO_GAME', '게임을 먼저 시작하세요.');
      return this.load(gameId);
    }
  };
}

export function applyActionAvailability(button, availability, { busy = false, hasPending = false } = {}) {
  const reason = busy ? '처리 중입니다.' : hasPending ? '먼저 이전 요청의 결과를 확인하세요.' :
    availability.allowed ? null : availability.unavailableReason || '실행할 수 없습니다.';
  button.disabled = reason !== null;
  button.setAttribute('aria-disabled', String(button.disabled));
  button.title = reason || '';
  return reason; // Also display this below the button; disabled buttons cannot receive focus.
}
