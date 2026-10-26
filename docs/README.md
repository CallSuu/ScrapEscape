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

- [화면 영역·API·버튼 제한·공통 통신 명세](frontend/Scrap_Escape_화면_연동_명세.md)
- [화면 조회·미리보기·통신 검증](backend/Scrap_Escape_화면연동_구현_검증.md)

## 문서 관리 규칙

백엔드 기능을 추가하거나 변경할 때 같은 작업에서 API 명세서·OpenAPI·관련 예시·구현 현황을 함께 갱신한다. 별도 HTTP API 없이 처리되는 내부 기능은 연결된 API의 동작·DB 변화·데이터 사전에 반영한다. 문서는 계획과 실제 구현·실행 검증을 구분하고, 파일 경로 변경 시 관련 링크도 수정한다.
