# Scrap Escape 문서

## API 명세

- [전체 API 명세서](api/Scrap_Escape_API_명세서.md): 요청·응답·오류·비용·DB 변화
- [OpenAPI3.1 JSON](api/openapi.json): 도구 가져오기용 명세
- [실제 요청·응답 예시](api/examples): 검증용 예제
- [API 실행 가이드](api/Scrap_Escape_API_실행가이드.md): 서버 실행·curl·DB 확인

## 구현 상태와 검증

- [기획 대비 백엔드 구현 현황](backend/Scrap_Escape_백엔드_구현현황.md)
- [핵심 백엔드 확장 검증](backend/Scrap_Escape_백엔드_확장_검증.md)
- [비밀의 방 구현·검증](backend/Scrap_Escape_비밀의방_구현_검증.md)
- [요청 중복 방지 구현·검증](backend/Scrap_Escape_요청중복방지_구현_검증.md)
- [구현 계획](superpowers/plans)

## 프론트엔드 연동

- [도시 건물 선택·넓은 창 배치](frontend/Scrap_Escape_도시선택_창배치_학습가이드.md)

- [숲속 야영지·항공기 정비 화면](frontend/Scrap_Escape_야영지_화면_학습가이드.md)

- [안전지대·최종 결과 사용법/CRUD/검증](frontend/Scrap_Escape_안전지대_학습가이드.md)

- [탐색 보상·비밀의 방 전체 화면 학습/검증](frontend/Scrap_Escape_탐색결과_학습가이드.md)

- [두 번째 단계: 탐색 카드·실행·결과 학습 가이드](frontend/Scrap_Escape_탐색화면_학습가이드.md)

- [첫 화면 실행·HTML/CSS/JavaScript 학습 가이드](frontend/Scrap_Escape_프론트_첫화면_학습가이드.md)

두 사람 분업용 명세서(각 문서에 백엔드 현황·API·공통 계약·담당 범위·AI 전달 프롬프트 포함):

- [본인 A: 메인·HUD·탐색·비밀의 방·통합](frontend/Scrap_Escape_본인담당_개발명세서.md)
- [팀원 B: 안전지대·재료 관리·수리·결과](frontend/Scrap_Escape_팀원담당_개발명세서.md)


- [화면 영역·API·버튼 제한·공통 통신 명세](frontend/Scrap_Escape_화면_연동_명세.md)
- [화면 조회·미리보기·통신 검증](backend/Scrap_Escape_화면연동_구현_검증.md)

## 문서 관리 규칙

백엔드 기능을 추가하거나 변경할 때 같은 작업에서 API 명세서·OpenAPI·관련 예시·구현 현황을 함께 갱신한다. 별도 HTTP API 없이 처리되는 내부 기능은 연결된 API의 동작·DB 변화·데이터 사전에 반영한다. 문서는 계획과 실제 구현·실행 검증을 구분하고, 파일 경로 변경 시 관련 링크도 수정한다.


## 2026-10-31 규칙 조정

최신 [게임 기획안과 전체 확률](game/Scrap_Escape_게임_기획안.md) 참고. 세척액 최대10·탐색1개70%/2개30%, 재료1개당액1(시간은 묶음당1h), 휴식 시간당 오염5 감소, 7종 조합 각7개. 탐색 확인 후 지역 선택 복귀·정비 가운데 배치·0비용 문구 생략. API19개/새 cleanerRewards 필드, DB V9~V10 적용 필요.
