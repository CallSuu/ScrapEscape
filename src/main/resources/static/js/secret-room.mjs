import { applyActionAvailability } from './game-api.mjs';

export function secretRoomChoices(room, state) {
  const mode = room?.immediateAvailable ? 'IMMEDIATE' : 'REVISIT';
  const common = state.rulesVersion !== 2 ? '이전 규칙의 게임입니다.' : state.status !== 'RUNNING' ? '종료된 게임입니다.' :
    !room ? '기억한 방이 없습니다.' : room.opened ? '이미 확인한 방입니다.' : state.secrets.keyAmount < 1 ? '비밀의 열쇠가 없습니다.' : null;
  const preview = mode === 'IMMEDIATE' ? room?.immediate : room?.revisit;
  return {
    mode,
    enter: { ...preview, allowed: !common && Boolean(preview?.allowed), unavailableReason: common || preview?.unavailableReason || null },
    later: { allowed: !common, unavailableReason: common, durationHours: 0, staminaCost: 0, keyCost: 0 }
  };
}

export function createSecretRoomView(root, context) {
  const abort = new AbortController();
  root.innerHTML = `<section class="se-room">
    <div class="se-room__door" aria-hidden="true"><span>?</span><small>숨겨진 공간</small></div>
    <div class="se-room__choice">
      <p class="se-room__eyebrow">기억한 장소</p><h3 class="se-room__question">이곳에 들어갈까?</h3>
      <p class="se-room__intro">추가 보상이 있을 수도 있지만, 재료를 잃거나 오염에 노출될 수도 있습니다.</p>
      <p class="se-room__keys"></p><p class="se-room__cost"></p><p class="se-room__risk"></p>
      <div class="se-room__actions"><button class="se-room__enter" type="button">들어간다</button><button class="se-room__later" type="button">나중에 들어간다</button></div>
      <p class="se-room__reason"></p><p class="se-room__hint">나중에 들어가기는 열쇠를 소비하지 않습니다. 위치는 기억한 상태로 남습니다.</p>
    </div>
  </section>`;
  const find = name => root.querySelector('.se-room__' + name);
  find('enter').addEventListener('click', () => {
    const screen = context.getScreen(), locationId = context.getPanel()?.locationId;
    const room = screen?.state.secrets.rooms.find(r => r.locationId === locationId);
    if (!screen) return;
    const choices = secretRoomChoices(room,screen.state),flags=context.getFlags();
    if (choices.enter.allowed&&!flags.busy&&!flags.hasPending) void context.dispatch({type:'OPEN_ROOM',locationId,mode:choices.mode});
  }, { signal:abort.signal });
  find('later').addEventListener('click', () => {
    const screen=context.getScreen(),locationId=context.getPanel()?.locationId;
    const room=screen?.state.secrets.rooms.find(r=>r.locationId===locationId),flags=context.getFlags();
    if (screen&&secretRoomChoices(room,screen.state).later.allowed&&!flags.busy&&!flags.hasPending) void context.dispatch({type:'DEFER_ROOM',locationId});
  }, { signal:abort.signal });
  return {
    render(screen,flags) {
      if (!screen) return;
      const room=screen.state.secrets.rooms.find(r=>r.locationId===context.getPanel()?.locationId);
      const choices=secretRoomChoices(room,screen.state),preview=choices.enter;
      find('eyebrow').textContent=room?.locationName||'기억한 장소';
      find('question').textContent=room?.opened?'이미 확인한 방입니다.':'이곳에 들어갈까?';
      find('keys').textContent='비밀의 열쇠 '+screen.state.secrets.keyAmount+' / '+screen.state.secrets.keyCapacity;
      find('cost').textContent=room?(choices.mode==='IMMEDIATE'?'발견 직후 입장':'기억한 방으로 재방문')+' · 시간 '+preview.durationHours+'h · 체력 '+preview.staminaCost+' · 열쇠 '+preview.keyCost+'개':'';
      find('risk').textContent=room?'예상 오염 +'+preview.expectedContaminationIncrease+(preview.rewardFreeSlots===0?' · 재료 보상을 담을 휴대 공간이 없습니다.':'')+(preview.deadlineRisk?' · 완료 시 생존 또는 치료 기한 초과 위험':''):'';
      const enterReason=applyActionAvailability(find('enter'),choices.enter,flags);
      const laterReason=applyActionAvailability(find('later'),choices.later,flags);
      find('reason').textContent=[...new Set([enterReason,laterReason].filter(Boolean))].join(' ');
      find('hint').textContent=screen.state.secrets.keyAmount===0?'두 선택에는 열쇠가 필요합니다. 위의 메인으로 돌아가기로 나갈 수 있고, 방 위치는 계속 기억합니다.':'나중에 들어가기는 열쇠를 소비하지 않습니다. 위치는 기억한 상태로 남습니다.';
    },
    destroy(){abort.abort();root.replaceChildren();}
  };
}
