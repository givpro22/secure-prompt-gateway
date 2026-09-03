# -*- coding: utf-8 -*-
"""규칙 후보 자동 생성 — 슬롯 방식.

LLM에게 정규식을 쓰게 하지 않는다. 7B는 못 쓴다(propose_rule.py에서 5/5 기각).
검증된 템플릿을 코드가 갖고 있고, LLM은 슬롯만 고른다:

    문맥 키워드  ← LLM이 예시에서 뽑는다 (매번 정확했다)
    값 형태     ← 코드가 좁은 것부터 넣어 본다
    정규식      ← 코드가 조립한다 (문법 오류가 날 수 없다)

값 형태까지 LLM에 맡겼더니 3시행 중 1회만 통과했다. 오탐을 알려줘도 DIGITS6를
고집한다. 선택지가 4개뿐이니 코드가 좁은 것부터 넣어 보면 탐색이 끝난다 —
"span은 코드가 만들고 LLM은 라벨만 고른다"를 한 겹 더 적용한 것이다.
"""
import json, re, subprocess, sys, urllib.request

# 검증된 값 형태. 사람이 쓰고 골든셋으로 검증한 것만 들어온다.
ORDER = ["DATE6", "DIGITS_GROUPED", "ALNUM8", "DIGITS6"]   # 좁은 것부터

VALUE_TYPES = {
    "DATE6":    (r"\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])", "YYMMDD 6자리 (생년월일)"),
    "DIGITS6":  (r"\d{6}", "숫자 6자리"),
    "DIGITS_GROUPED": (r"\d{2,6}-\d{2,6}-\d{2,8}", "하이픈으로 끊긴 숫자 묶음"),
    "ALNUM8":   (r"[A-Za-z0-9]{8,}", "영숫자 8자 이상"),
}

def build(keywords, value_type, window=8):
    """문맥 창 + 값 형태로 정규식을 조립한다. 문법 오류가 날 수 없다."""
    body, _ = VALUE_TYPES[value_type]
    ctx = "|".join(f"{re.escape(k)}[^\\n]{{0,{window}}}" for k in keywords)
    return f"(?<={ctx})(?<![0-9]){body}(?![0-9])"

SYSTEM = """너는 사내 정보보안팀의 탐지 규칙 작성 보조기다.
마스킹됐어야 하는데 규칙이 놓친 예시를 받아, 규칙의 재료를 고른다.
정규식은 네가 쓰지 않는다. 값의 형태도 네가 정하지 않는다.

너가 고를 것은 하나다.
- context_keywords: 가려야 할 값 바로 앞에 오는 한국어 단어들. 예시에 실제로 나온 것만 쓴다.
  이 단어가 없으면 마스킹하지 않겠다는 뜻이므로 신중히 고른다.

함께 적을 것: mask_label(가린 자리에 넣을 표시), code(규칙 코드), rationale(한 줄 근거).
mask_label은 무엇을 가렸는지만 적는다. 예시 문장의 내용을 담지 않는다.

설명 없이 JSON만 반환한다."""

FMT = {"type":"object","properties":{
  "context_keywords":{"type":"array","items":{"type":"string"}},
  "mask_label":{"type":"string"}, "code":{"type":"string"}, "rationale":{"type":"string"}},
  "required":["context_keywords","mask_label","code","rationale"]}

def ask(examples, negatives, feedback, temp):
    u = "마스킹됐어야 하는 예시:\n" + "\n".join(f"- {e}" for e in examples)
    u += "\n\n걸리면 안 되는 문장:\n" + "\n".join(f"- {n}" for n in negatives)
    if feedback: u += f"\n\n직전 시도 실패: {feedback[:160]}\n고쳐서 다시 골라라."
    body = json.dumps({"model":"qwen2.5:7b-instruct","stream":False,"think":False,
        "format":FMT,"options":{"temperature":temp,"num_predict":500},
        "messages":[{"role":"system","content":SYSTEM},{"role":"user","content":u}]}).encode()
    d = json.load(urllib.request.urlopen(urllib.request.Request(
        "http://localhost:11434/api/chat", body, {"Content-Type":"application/json"}), timeout=180))
    return json.loads(d["message"]["content"])

def gate(pattern, examples, negatives):
    open("/tmp/ex.txt","w").write("\n".join(examples))
    open("/tmp/neg.txt","w").write("\n".join(negatives))
    r = subprocess.run(["java","RuleGate.java",pattern,"/tmp/ex.txt","/tmp/neg.txt"],
                       capture_output=True, text=True, timeout=120)
    out = (r.stdout or r.stderr).strip()
    return out.startswith("OK"), out

EXAMPLES = ["주민번호 001123이야?", "주민등록번호 900101 이었나", "주민번호 앞자리 851212 알려줘"]
cases = json.load(open("devset.json"))["cases"] + json.load(open("holdout2.json"))["cases"]
FULL = re.compile(r'(?<![0-9])\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])-?[1-4]\d{6}(?![0-9])')
HINT = ["주민번호 규칙이 123456 형태로 바뀌었어","주민번호 검증 오류코드 400123 떴어",
        "생년월일 컬럼 길이를 128000 으로 늘려","주민등록번호 발급 기관 코드 100200 확인"]
NEG = [c["t"] for c in cases if c["l"]=="NONE" and not FULL.search(c["t"])] + HINT

print(f"예시 {len(EXAMPLES)}건 · 참음성 {len(NEG)}건\n")
feedback = None
for attempt in range(1, 4):
    # 재시도마다 온도를 올린다. temperature=0이면 같은 답만 반복해 재시도가 무의미하다.
    c = ask(EXAMPLES, HINT, feedback, temp=0 if attempt == 1 else 0.3 * attempt)
    print(f"[{attempt}회] LLM 키워드 → {c['context_keywords']}")
    fails = []
    for vt in ORDER:                       # 좁은 형태부터. 통과하는 가장 좁은 것을 쓴다
        pattern = build(c["context_keywords"], vt)
        passed, why = gate(pattern, EXAMPLES, NEG)
        print(f"        {vt:15s} {'✅' if passed else '❌'} {why[:72]}")
        if passed:
            print("\n승인 화면에 올릴 후보:")
            print(json.dumps({"code":c["code"],"pattern":pattern,"mask_label":c["mask_label"],
                              "value_type":vt,"rule_type":"REGEX","action":"MASK",
                              "rationale":c["rationale"],"derived_from":EXAMPLES,"gate":why},
                             ensure_ascii=False, indent=2))
            sys.exit(0)
        fails.append(f"{vt}: {why}")
    feedback = "모든 값 형태가 실패했다. 문맥 키워드가 너무 넓거나 좁다. " + fails[0][:100]
    print()
print("키워드 3회 시도 모두 실패 → 사람에게 넘긴다")
