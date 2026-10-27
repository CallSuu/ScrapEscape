export function createCampView(root, context) {
  const abort = new AbortController();
  root.innerHTML = `<section class="se-camp" aria-label="숲속 비행기 야영지">
    <p class="se-camp__intro">비행기 주변에 마련한 작은 거점<br><span>이용할 공간을 선택하세요.</span></p>
    <button type="button" class="se-camp__spot se-camp__spot--storage" data-camp-area="storage"><span class="se-camp__pin" aria-hidden="true">▣</span><strong>비행기 보관함</strong><small>보관 · 세척 · 폐기</small></button>
    <button type="button" class="se-camp__spot se-camp__spot--rest" data-camp-area="rest"><span class="se-camp__pin" aria-hidden="true">☾</span><strong>휴식 공간</strong><small>시간을 정해 체력 회복</small></button>
    <button type="button" class="se-camp__spot se-camp__spot--treatment" data-camp-area="treatment"><span class="se-camp__pin" aria-hidden="true">✚</span><strong>치료 공간</strong><small>의료 키트 · 오염 관리</small></button>
    <button type="button" class="se-camp__spot se-camp__spot--repair" data-camp-area="repair"><span class="se-camp__pin" aria-hidden="true">⚒</span><strong>비행기 정비</strong><small data-camp-repairs></small></button>
    <p class="se-camp__hint">공간을 오가는 데에는 시간과 체력이 소모되지 않습니다.</p>
  </section>`;
  root.addEventListener('click', event => {
    const button = event.target.closest('[data-camp-area]'); if (!button || !root.contains(button) || !context.getScreen()) return;
    void context.navigate(button.dataset.campArea === 'repair' ? { name: 'aircraft-repair' } : { name: 'camp-action', area: button.dataset.campArea });
  }, { signal: abort.signal });
  return {
    render(screen) {
      const parts = screen?.state.repairs || [];
      root.querySelector('[data-camp-repairs]').textContent = '수리 ' + parts.filter(p => p.repaired).length + '/' + parts.length + ' · 장착 · 탈출';
    },
    destroy() { abort.abort(); root.replaceChildren(); }
  };
}
