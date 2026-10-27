export function resultSummary(state) {
  if (!state || !['SUCCESS', 'FAILED'].includes(state.status)) return null;
  const statistics = state.statistics, secrets = state.secrets.statistics;
  return {
    title: state.status === 'SUCCESS' ? '탈출 성공' : '생존 실패',
    reason: state.status === 'SUCCESS' ? '비행기를 수리하고 오염 도시를 벗어났습니다.' : state.failureReason === 'TIME_LIMIT' ? '생존 제한 시간 초과' : state.failureReason === 'UNTREATED_DISEASE' ? '질병 치료 기한 초과' : '탈출 준비를 마치지 못했습니다.',
    repaired: state.repairs.filter(p => p.repaired).map(p => p.name),
    stats: [
      ['일반 탐색', statistics.explorations], ['낮 탐색', statistics.dayExplorations], ['밤 탐색', statistics.nightExplorations], ['빈손 탐색', statistics.emptyExplorations],
      ['수집한 재료', statistics.collected], ['세척한 재료', statistics.cleaned], ['폐기한 재료', statistics.discarded],
      ['질병 발생', statistics.diseaseCount], ['치료 횟수', statistics.treatmentCount], ['사용한 의료 키트', statistics.medicalKitsUsed], ['최고 오염도', statistics.maxContamination], ['폭풍 노출 시간', statistics.stormExposureHours],
      ['발견한 방', secrets.discoveredRooms], ['개방한 방', secrets.openedRooms], ['남겨둔 방', secrets.unopenedRooms], ['얻은 열쇠', secrets.keysAcquired], ['사용한 열쇠', secrets.keysUsed],
      ['좋은 방 결과', secrets.goodOutcomes], ['나쁜 방 결과', secrets.badOutcomes], ['방에서 얻은 재료', secrets.materialsCollected], ['방에서 잃은 재료', secrets.materialsLost]
    ]
  };
}

export function createResultView(root, context) {
  const abort = new AbortController();
  root.innerHTML = `<section class="se-result"><p class="se-result__eyebrow">SCRAP ESCAPE · 이번 생존 기록</p><h3 class="se-result__title"></h3><p class="se-result__reason"></p><p class="se-result__time"></p><p class="se-result__repairs"></p><dl class="se-result__stats"></dl><button type="button" class="se-result__restart">새 게임</button></section>`;
  const find = name => root.querySelector('.se-result__' + name);
  find('restart').addEventListener('click', () => { void context.dispatch({ type: 'START_GAME' }); }, { signal: abort.signal });
  return {
    render(screen, flags) {
      const s = screen?.state, summary = resultSummary(s);
      find('restart').disabled = flags.busy || flags.hasPending || !summary;
      find('title').textContent = summary?.title || '아직 종료되지 않았습니다';
      find('reason').textContent = summary?.reason || '게임을 마치면 이번 판의 기록을 볼 수 있습니다.';
      find('time').textContent = summary ? '소요 ' + s.elapsedHours + '시간 · 종료 시각 ' + s.day + '일 ' + String(s.hour).padStart(2, '0') + ':00' : '';
      find('repairs').textContent = summary ? '수리 ' + summary.repaired.length + '/' + s.repairs.length + ' · ' + (summary.repaired.join(' / ') || '완료한 부품 없음') : '';
      root.querySelector('.se-result').dataset.outcome = s?.status || '';
      const stats = find('stats'); stats.replaceChildren();
      for (const [label, value] of summary?.stats || []) {
        const pair = document.createElement('div'), term = document.createElement('dt'), detail = document.createElement('dd');
        term.textContent = label; detail.textContent = String(value); pair.append(term, detail); stats.append(pair);
      }
    },
    destroy() { abort.abort(); root.replaceChildren(); }
  };
}
