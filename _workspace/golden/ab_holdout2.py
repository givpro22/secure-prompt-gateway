# -*- coding: utf-8 -*-
"""같은 홀드아웃 v2에 옛 프롬프트(D)를 돌린다. 튜닝이 아니라 통제 비교다."""
import json, re, time, urllib.request
from collections import defaultdict
exec(open("run_holdout.py").read().split("# ── 1. 문장 단위")[0]
     .replace('print(f"프롬프트 해시', '#print(f"프롬프트 해시'))

# 현행(E)에서 이번에 추가한 부분만 제거해 D를 복원한다
SPEC_START = "문장이 우리 회사의 구체적인 수치·단가·조건·상태·명단을"
i = SYSTEM.index(SPEC_START); j = SYSTEM.index("예시:", i)
D = SYSTEM[:i] + SYSTEM[j:]
for line in ['- "우리 쪽 회수 기준 단가는 킬로당 340원입니다" -> CONFIDENTIAL (내부 단가를 단정 서술)',
             '- "그 대리점 미수금이 넉 달째입니다" -> CONFIDENTIAL (거래처 상태)',
             '- "해당 공정의 실제 수율은 목표에 못 미칩니다" -> CONFIDENTIAL (내부 실적)',
             '- "빌드가 12분 걸립니다" -> NONE (기술 지표, 밖으로 나가도 손해 없음)',
             '- "회의는 매주 수요일에 합니다" -> NONE (업무 진행 방식)']:
    D = D.replace(line + "\n", "").replace("\n" + line, "")
assert SPEC_START not in D and "킬로당 340원" not in D
print(f"D 복원 확인 — 현행 {len(SYSTEM)}자 → D {len(D)}자\n")

CASES = json.load(open("holdout2.json"))["cases"]
def run(name, sysp):
    sent=[i for i,c in enumerate(CASES) if not covered(c["t"])]
    got={i:"NONE" for i in range(len(CASES)) if i not in sent}
    t0=time.time()
    for i in sent:
        body=json.dumps({"model":"qwen2.5:7b-instruct","stream":False,"think":False,"format":FMT,
            "options":{"temperature":0,"num_predict":200},
            "messages":[{"role":"system","content":sysp},
                        {"role":"user","content":"문장 목록:\n1. "+CASES[i]["t"]}]}).encode()
        req=urllib.request.Request("http://localhost:11434/api/chat",body,{"Content-Type":"application/json"})
        for _ in range(3):
            try:
                d=json.load(urllib.request.urlopen(req,timeout=60))
                got[i]=next((x["label"] for x in json.loads(d["message"]["content"])["items"]
                             if x["index"]==1),"NONE"); break
            except Exception: pass
    tp=fp=tn=fn=0; per=defaultdict(lambda:[0,0])
    for i,c in enumerate(CASES):
        per[c["g"]][1]+=1
        if c["l"]==got[i]:
            per[c["g"]][0]+=1; tp+= c["l"]=="CONFIDENTIAL"; tn+= c["l"]=="NONE"
        else:
            fp+= c["l"]=="NONE"; fn+= c["l"]=="CONFIDENTIAL"
    p=tp/(tp+fp) if tp+fp else 0; r=tp/(tp+fn) if tp+fn else 0
    print(f"### {name}  {tp+tn}/{len(CASES)} · 정밀도 {p:.3f} · 재현율 {r:.3f} · "
          f"F1 {2*p*r/(p+r) if p+r else 0:.3f} · 오탐 {fp}/{tn+fp} ({fp/(tn+fp)*100:.1f}%) · "
          f"미탐 {fn}/{tp+fn} · 완곡 {per['H2-완곡'][0]}/{per['H2-완곡'][1]} · {time.time()-t0:.0f}s")
run("D 옛 프롬프트", D)
