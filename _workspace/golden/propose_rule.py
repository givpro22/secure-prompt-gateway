# -*- coding: utf-8 -*-
"""규칙 후보 자동 생성 프로토타입.

AI가 정규식을 만들고, 골든셋이 그것을 죽이거나 살린다.
생성은 자동, 활성화는 게이트 통과 + 사람 승인.
"""
import json, re, subprocess, sys, urllib.request

SYSTEM = """너는 사내 정보보안팀의 탐지 규칙 작성 보조기다.
마스킹됐어야 하는데 규칙이 놓친 예시를 받아 Java Pattern 문법의 정규식을 만든다.

[규칙]
- 반드시 주어진 예시 전부에 매칭되어야 한다.
- 일반 업무 문장에 걸리면 안 된다. 넓게 잡느니 좁게 잡는다.
- 숫자만 있는 패턴은 금지다. 문맥 키워드나 형식 제약을 반드시 포함한다.
- (?<= ) 룩비하인드, (?<! ) 부정 룩비하인드를 쓸 수 있다. Java는 가변 길이 룩비하인드를 허용한다.
- 키워드와 값 사이에는 공백·조사가 낀다. 문맥 창을 [^\n]{0,8} 처럼 넉넉히 잡아라.
- 설명 없이 JSON만 반환한다.

[사내 규칙 예시 — 이 형태를 따라라]
계좌번호(PII-ACCOUNT-06). 형식만으로는 주문번호와 구분되지 않아 금융 문맥을 요구한다.
  (?<=계좌[^\n]{0,8}|입금[^\n]{0,8}|송금[^\n]{0,8}|이체[^\n]{0,8})(?<![0-9])\\d{2,6}-\\d{2,6}-\\d{2,8}(?![0-9])

주민등록번호(PII-RRN-01). 월·일 범위를 패턴에 내장해 오탐을 줄인다.
  (?<![0-9])\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])-?[1-4]\\d{6}(?![0-9])"""

FMT = {"type":"object","properties":{
  "pattern":{"type":"string"}, "mask_label":{"type":"string"},
  "code":{"type":"string"}, "rationale":{"type":"string"}},
  "required":["pattern","mask_label","code","rationale"]}

def ask(examples, negatives, feedback=None):
    u = "마스킹됐어야 하는 예시:\n" + "\n".join(f"- {e}" for e in examples)
    u += "\n\n걸리면 안 되는 문장:\n" + "\n".join(f"- {n}" for n in negatives)
    if feedback:
        u += f"\n\n직전 시도 실패: {feedback[:160]}\n고쳐서 다시 만들어라."
    body = json.dumps({"model":"qwen2.5:7b-instruct","stream":False,"think":False,
        "format":FMT,"options":{"temperature":0,"num_predict":800},
        "messages":[{"role":"system","content":SYSTEM},{"role":"user","content":u}]}).encode()
    d = json.load(urllib.request.urlopen(urllib.request.Request(
        "http://localhost:11434/api/chat", body, {"Content-Type":"application/json"}), timeout=180))
    raw = d["message"]["content"]
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        # 출력이 잘리면 게이트 이전에 죽는다. 실패로 세고 넘어간다.
        return {"pattern": None, "mask_label": "", "code": "?", "rationale": "",
                "_broken": raw[:120]}

def gate(pattern, examples, negatives):
    """골든셋 게이트. 실제 런타임과 같은 java.util.regex로 검증한다.

    하나라도 어기면 승인 화면에 올라가지 않는다. 이 게이트가 있어야
    "AI가 정규식을 만든다"가 위험하지 않은 말이 된다.
    """
    open("/tmp/ex.txt", "w").write("\n".join(examples))
    open("/tmp/neg.txt", "w").write("\n".join(negatives))
    r = subprocess.run(["java", "RuleGate.java", pattern, "/tmp/ex.txt", "/tmp/neg.txt"],
                       capture_output=True, text=True, timeout=120)
    out = (r.stdout or r.stderr).strip()
    return out.startswith("OK"), out

EXAMPLES = ["주민번호 001123이야?", "주민등록번호 900101 이었나", "주민번호 앞자리 851212 알려줘"]
cases = json.load(open("devset.json"))["cases"] + json.load(open("holdout2.json"))["cases"]
FULL = re.compile(r'(?<![0-9])\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])-?[1-4]\d{6}(?![0-9])')
# 기존 규칙이 이미 잡는 문장은 참음성에서 뺀다 — 중첩 억제가 처리한다
NEG = [c["t"] for c in cases if c["l"] == "NONE" and not FULL.search(c["t"])]
HINT = ["주민번호 규칙이 123456 형태로 바뀌었어", "주민번호 검증 오류코드 400123 떴어",
        "생년월일 컬럼 길이를 128000 으로 늘려", "주민등록번호 발급 기관 코드 100200 확인"]
NEG += HINT

print(f"예시 {len(EXAMPLES)}건 · 참음성 {len(NEG)}건\n")
feedback = None
for attempt in range(1, 6):
    c = ask(EXAMPLES, HINT, feedback)
    if c["pattern"] is None:
        passed, why = False, f"JSON 출력이 잘렸다: {c['_broken']}"
    else:
        passed, why = gate(c["pattern"], EXAMPLES, NEG)
    print(f"[{attempt}회] {'✅ 통과' if passed else '❌ 기각'}  {c['code']} → {c['mask_label']}")
    print(f"       {c['pattern']}")
    print(f"       {why}\n")
    if passed:
        print("승인 화면에 올릴 후보:"); print(json.dumps(c, ensure_ascii=False, indent=2)); sys.exit(0)
    feedback = why
print("5회 안에 게이트를 통과하지 못했다. 사람에게 넘긴다.")
