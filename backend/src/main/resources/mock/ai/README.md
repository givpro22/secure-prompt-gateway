# Mock AI 픽스처

`MockAiInspector`가 KEYWORD 매칭으로 골라 반환하는 고정 JSON이다.

| 파일 | 선택 조건 | 출처 |
|---|---|---|
| `case-b-client-project.json` | hits에 `A사` | 손으로 작성 |
| `case-client-generic.json` | hits에 `B사` | **로컬 LLM 실제 출력을 받아 적음** |
| `case-no-reference.json` | 그 외 | 손으로 작성 |

## `case-client-generic.json`은 녹화본이다

`qwen2.5:7b-instruct`가 아래 입력에 실제로 낸 응답을 그대로 옮겼다.

```
입력  B사 견적 검토 부탁해요. 그 거래처 여신 한도는 이미 초과 상태입니다. 회의실은 3번으로 예약했습니다.
경로  SPRING_PROFILES_ACTIVE=llm · inspection 129 · 2026-09-03
```

규칙 엔진은 `B사`만 잡고, LLM이 `여신 한도 초과`를 후보로 올리고, `회의실 예약`은 건드리지
않았다. 정규식으로는 만들 수 없는 판정이라 시연에서 이 케이스를 쓴다.

**시연에서 "지금 AI가 돌고 있다"고 말하지 않는다.** mock 프로파일이고 이 파일은 녹화본이다.
말할 수 있는 것은 "이 응답은 로컬 LLM이 실제로 낸 것이고, 라이브로 돌리면
`AI_PROVIDER=llm` 전환만으로 같은 경로가 돈다"까지다.

## 다시 녹화하려면

```bash
SPRING_PROFILES_ACTIVE=llm SERVER_PORT=8081 ./gradlew bootRun
curl -s -X POST http://localhost:8081/api/v1/messages \
  -H 'Content-Type: application/json' -H 'X-User-Id: 2' \
  -d '{"text":"B사 견적 검토 부탁해요. 그 거래처 여신 한도는 이미 초과 상태입니다. 회의실은 3번으로 예약했습니다."}'
# 폴링 후 DB에서 원본을 꺼낸다
docker exec gateway-pg psql -U gateway -d gateway -tA \
  -c "select ai_result from inspection order by inspection_id desc limit 1;"
```

프롬프트를 바꾸면 출력도 바뀐다. `_workspace/golden/`을 다시 돌리고 이 파일도 다시 구울 것.
