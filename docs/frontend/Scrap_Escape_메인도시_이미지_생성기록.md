# 메인·오염 도시 이미지 생성 기록

내장 image_gen으로 만든 원본 배경2종이다. CLI/API 키를 사용하지 않았다. 첨부 UI 이미지는 분위기·배치 참고이며 패널·글자·버튼을 제품 배경에 복사하지 않았다.

| 용도 | 저장소 파일 | 생성 원본 |
|---|---|---|
| 숲의 비행기와 오염 도시 메인 | src/main/resources/static/assets/main/main-landscape.png | /Users/suhyeokyun/.codex/generated_images/01a10eab-85b0-7460-a357-9e07e59453cb/exec-df3ce6e1-933c-4669-8a92-18b403144249.png |
|6개 탐색 건물이 구분되는 도시 | src/main/resources/static/assets/main/city-district.png | /Users/suhyeokyun/.codex/generated_images/01a10eab-85b0-7460-a357-9e07e59453cb/exec-754ecba5-6b97-4c9f-8bb7-0363be3cf603.png |

원본을 저장소로 복사했다. 실행 시에는 저장소 파일만 사용한다. 정보 창의 지역 사진은 같은 도시 이미지를 해당 건물 좌표 중심으로 CSS 확대해 표시한다. 글자·사진 틀·버튼은 HTML/CSS이다.

## 메인 최종 프롬프트

```text
Production environment background for desktop survival crafting game Scrap Escape, 16:9 wide cinematic landscape illustration, grounded detailed semi-realistic painterly game environment. NO UI, NO TEXT, NO logos, NO watermarks, NO people. Match this composition: warm sunlit green forest refuge occupies left half of image, wrecked small white-and-blue twin propeller PRIVATE airplane and a tarp camp with supply crates in a clearing on a high rocky forest ledge. A path travels from the foreground toward the horizon; right half overlooks an abandoned contaminated city valley with decaying overgrown hospital, supermarket and industrial buildings, broken roads and distant cooling towers, muted gray green haze. Forest is safe and green while city is colder and gloomy; natural continuous landscape, not split-screen. Mountains in distance, late-afternoon sun, detailed foliage and cracked infrastructure. View from slightly elevated forest edge looking across the camp and city, beautiful atmospheric depth. Keep top-left upper 15% and both lower corners lower visual contrast for live HUD overlays, keep central mid-left airplane and mid-right city road entrance clearly visible for two navigation buttons to be overlaid by code. No existing reference text, no inset screens or panels. Original artwork.
```

## 도시 최종 프롬프트

```text
Production background image for Scrap Escape, landscape 16:9 original detailed semi-realistic painterly abandoned contaminated city environment, consistent with a cinematic forest crash survival game. NO UI, NO PEOPLE, NO readable text, no watermarks. Slightly elevated wide view across a small urban district where SIX distinct searchable sites are clearly separated by cracked roads and rubble. Composition: upper-left around x20% y32% is a fenced SCRAPYARD with rusted metal piles and wrecked cars; upper-middle x50% y32% is a ruined ELECTRONICS STORE with display windows and old televisions; upper-right x80% y32% is a large SUPERMARKET storefront with broken shopping carts. Lower-left x20% y62% is a GARAGE with open vehicle repair bays, rusty car and tools; lower-middle x50% y62% is a COMMUNICATIONS building with antenna mast and satellite dishes; lower-right x80% y62% is a HOSPITAL with an unlettered old medical cross sign and ambulance wreck. Make each building have distinct architecture and visual silhouette. Hospital must be unmistakable. Entire scene abandoned after a disaster, overgrown ivy, subtle pale radioactive green-gray haze, weak late-afternoon sunlight, empty quiet streets and distant ruined skyline. Road junctions naturally connect all six sites, enough open breathing space between buildings for code buttons. Keep top 15% mostly distant skyline, bottom 20% mostly dark broken road/grass foreground for HUD. No labels or graphical map pins baked into art, no isometric tiles or comic borders. This is a continuous street environment, not six framed thumbnails.
```
