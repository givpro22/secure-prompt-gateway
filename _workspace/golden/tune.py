# -*- coding: utf-8 -*-
"""프롬프트 변형 비교. 개발셋에서만 돌린다 — 홀드아웃은 건드리지 않는다."""
import json, re, sys, time, urllib.request
from collections import defaultdict

CASES = json.load(open("devset.json"))["cases"]
JAVA = "../../backend/src/main/java/com/skala/gateway/ai/PromptAssembler.java"
src = open(JAVA, encoding="utf-8").read()
BASE = "\n".join(l[12:] if l.startswith(" "*12) else l.strip()
    for l in src.split('SENTENCE_SYSTEM_PROMPT = """',1)[1].split('""";',1)[0].split("\n")).strip()

# ── 변형 E: 구체성 판정 ──────────────────────────────────────────────
SPECIFIC = """
문장이 우리 회사의 구체적인 수치·단가·조건·상태·명단을 단정적으로 서술하면 CONFIDENTIAL이다.
"아직", "비공개" 같은 말이 붙어 있지 않아도 마찬가지다. 그 값이 공개 자료에 실렸다고 문장이
스스로 밝히지 않는 한 내부 수치로 본다.
반대로 업무 진행 방식, 도구 사용법, 기술 지표, 일정 조율처럼 밖으로 나가도 손해가 없는 내용은
숫자가 있어도 NONE이다."""

E_EX = """
- "우리 쪽 회수 기준 단가는 킬로당 340원입니다" -> CONFIDENTIAL (내부 단가를 단정 서술)
- "그 대리점 미수금이 넉 달째입니다" -> CONFIDENTIAL (거래처 상태)
- "해당 공정의 실제 수율은 목표에 못 미칩니다" -> CONFIDENTIAL (내부 실적)
- "빌드가 12분 걸립니다" -> NONE (기술 지표, 밖으로 나가도 손해 없음)
- "회의는 매주 수요일에 합니다" -> NONE (업무 진행 방식)"""

# ── 변형 F: E + 유출 가정 자문 ──────────────────────────────────────
LEAK = """
판단이 애매하면 이렇게 자문한다 — 이 문장이 경쟁사나 고객사에 그대로 전달됐을 때 우리에게
불리한가. 불리하면 CONFIDENTIAL, 아무 일도 없으면 NONE."""

FMT = {"type":"object","properties":{"items":{"type":"array","items":{"type":"object",
       "properties":{"index":{"type":"integer"},
                     "label":{"type":"string","enum":["CONFIDENTIAL","NONE"]}},
       "required":["index","label"]}}},"required":["items"]}
LABELS = ["[주민번호]","[카드번호]","[전화번호]","[이메일]","[내부IP]"]
SEED = [r'\d{6}-?[1-4]\d{6}', r'\b(\d{4}-?){3}\d{4}\b', r'01[016789]-?\d{3,4}-?\d{4}',
        r'[\w.+-]+@[\w-]+\.[\w.]+', r'AKIA[0-9A-Z]{16}',
        r'(postgres|mysql|jdbc)[\w+]*://[^\s]+',
        r'\b(10\.\d{1,3}|192\.168|172\.(1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3}\b']
def covered(t):
    return "라벨" if any(l in t for l in LABELS) else ("정규식" if any(re.search(p,t) for p in SEED) else None)

def classify(sysp, text):
    body = json.dumps({"model":"qwen2.5:7b-instruct","stream":False,"think":False,"format":FMT,
        "options":{"temperature":0,"num_predict":200},
        "messages":[{"role":"system","content":sysp},
                    {"role":"user","content":"문장 목록:\n1. "+text}]}).encode()
    req = urllib.request.Request("http://localhost:11434/api/chat", body,
                                 {"Content-Type":"application/json"})
    for _ in range(3):
        try:
            d = json.load(urllib.request.urlopen(req, timeout=60))
            return next((x["label"] for x in json.loads(d["message"]["content"])["items"]
                         if x["index"]==1), "NONE")
        except Exception:
            pass
    return "NONE"

def run(name, sysp):
    sent=[i for i,c in enumerate(CASES) if not covered(c["t"])]
    got={i:"NONE" for i in range(len(CASES)) if i not in sent}
    t0=time.time()
    for n,i in enumerate(sent,1):
        got[i]=classify(sysp, CASES[i]["t"])
        if n%80==0: print(f"      … {n}/{len(sent)} ({time.time()-t0:.0f}s)", flush=True)
    tp=fp=tn=fn=0; per=defaultdict(lambda:[0,0]); wrong=[]
    for i,c in enumerate(CASES):
        per[c["g"]][1]+=1
        if c["l"]==got[i]:
            per[c["g"]][0]+=1; tp+= c["l"]=="CONFIDENTIAL"; tn+= c["l"]=="NONE"
        else:
            fp+= c["l"]=="NONE"; fn+= c["l"]=="CONFIDENTIAL"; wrong.append((c["g"],c["l"],c["t"]))
    p=tp/(tp+fp) if tp+fp else 0; r=tp/(tp+fn) if tp+fn else 0
    f1=2*p*r/(p+r) if p+r else 0
    print(f"\n### {name}  {tp+tn}/{len(CASES)} · 정밀도 {p:.3f} · 재현율 {r:.3f} · F1 {f1:.3f} · "
          f"오탐 {fp}/{tn+fp} ({fp/(tn+fp)*100:.1f}%) · 미탐 {fn}/{tp+fn} · {time.time()-t0:.0f}s")
    wan=[g for g in per if "완곡" in g]
    if wan: print(f"    완곡형: {per[wan[0]][0]}/{per[wan[0]][1]}")
    print("    오탐:", " / ".join(t[:30] for g,e,t in wrong if e=="NONE")[:300])
    return f1

VAR = {
 "D 현행(기준선)": BASE,
 "E +구체성 판정": BASE.replace("\n\n예시:", SPECIFIC+"\n\n예시:")+E_EX,
 "F E+유출 가정":  BASE.replace("\n\n예시:", SPECIFIC+LEAK+"\n\n예시:")+E_EX,
}
for k in sys.argv[1:] or VAR: run(k, VAR[k])
