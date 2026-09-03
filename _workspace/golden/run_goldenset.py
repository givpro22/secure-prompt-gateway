# -*- coding: utf-8 -*-
"""골든셋 v2 실행. 운영과 같은 설정: 문장 단위 · 머리말 없음 · 사전 필터 · 누락 재요청."""
import json, re, sys, time, urllib.request
from collections import defaultdict

CASES = json.load(open("goldenset.json"))["cases"]

# 운영 시스템 프롬프트를 자바 소스에서 직접 읽는다
_JAVA = "../../backend/src/main/java/com/skala/gateway/ai/PromptAssembler.java"
_src = open(_JAVA, encoding="utf-8").read()
_body = _src.split('SENTENCE_SYSTEM_PROMPT = """', 1)[1].split('""";', 1)[0]
SYSTEM = "\n".join(l[12:] if l.startswith(" " * 12) else l.strip()
                   for l in _body.split("\n")).strip()

# 사전 필터: 시드(V2)의 mask_label과 REGEX 패턴. KEYWORD는 넣지 않는다.
LABELS = ["[주민번호]", "[카드번호]", "[전화번호]", "[이메일]", "[내부IP]"]
SEED = [r'\d{6}-?[1-4]\d{6}', r'\b(\d{4}-?){3}\d{4}\b', r'01[016789]-?\d{3,4}-?\d{4}',
        r'[\w.+-]+@[\w-]+\.[\w.]+', r'AKIA[0-9A-Z]{16}',
        r'(postgres|mysql|jdbc)[\w+]*://[^\s]+',
        r'\b(10\.\d{1,3}|192\.168|172\.(1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3}\b']

def covered(t):
    if any(l in t for l in LABELS): return "라벨"
    return "정규식" if any(re.search(p, t) for p in SEED) else None

FMT = {"type": "object", "properties": {"items": {"type": "array", "items": {"type": "object",
       "properties": {"index": {"type": "integer"},
                      "label": {"type": "string", "enum": ["CONFIDENTIAL", "NONE"]}},
       "required": ["index", "label"]}}}, "required": ["items"]}

def ask(model, text):
    u = "문장 목록:\n1. " + text
    body = json.dumps({"model": model, "stream": False, "think": False, "format": FMT,
                       "options": {"temperature": 0, "num_predict": 200},
                       "messages": [{"role": "system", "content": SYSTEM},
                                    {"role": "user", "content": u}]}).encode()
    req = urllib.request.Request("http://localhost:11434/api/chat", body,
                                 {"Content-Type": "application/json"})
    d = json.load(urllib.request.urlopen(req))
    for it in json.loads(d["message"]["content"])["items"]:
        if it["index"] == 1:
            return it["label"]
    return None

def run(model):
    sent = [i for i, c in enumerate(CASES) if not covered(c["t"])]
    got = {i: "NONE" for i in range(len(CASES)) if i not in sent}
    t0 = time.time(); retried = 0
    for n, i in enumerate(sent, 1):
        label = ask(model, CASES[i]["t"])
        if label is None:
            retried += 1
            label = ask(model, CASES[i]["t"]) or "NONE"
        got[i] = label
        if n % 40 == 0:
            print(f"    … {n}/{len(sent)} ({time.time()-t0:.0f}s)", flush=True)
    el = time.time() - t0

    tp = fp = tn = fn = 0
    per = defaultdict(lambda: [0, 0]); wrong = []
    for i, c in enumerate(CASES):
        exp, g = c["l"], got[i]
        per[c["g"]][1] += 1
        if exp == g:
            per[c["g"]][0] += 1
            tp += exp == "CONFIDENTIAL"; tn += exp == "NONE"
        else:
            fp += exp == "NONE"; fn += exp == "CONFIDENTIAL"
            wrong.append((c["g"], exp, g, c["t"]))

    prec = tp / (tp + fp) if tp + fp else 0
    rec = tp / (tp + fn) if tp + fn else 0
    f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0
    print(f"\n{'='*74}")
    print(f"{model}   {tp+tn}/{len(CASES)}  ({el:.0f}s · LLM 전송 {len(sent)}문장 · "
          f"문장당 {el/len(sent):.1f}s · 재요청 {retried})")
    print(f"  정밀도 {prec:.3f} · 재현율 {rec:.3f} · F1 {f1:.3f}")
    print(f"  오탐 {fp}/{tn+fp} (정상 문장 중 {fp/(tn+fp)*100:.1f}%) · 미탐 {fn}/{tp+fn}")
    print(f"\n  그룹별")
    for g in sorted(per):
        ok, n = per[g]
        print(f"    {g:14s} {ok:3d}/{n:3d}  {'█'*int(ok/n*20):<20} {ok/n*100:5.1f}%")
    print(f"\n  틀린 것 {len(wrong)}건")
    for g, e, gt, t in wrong:
        kind = "오탐" if e == "NONE" else "미탐"
        print(f"    [{kind}] {g:12s} {t[:52]}")
    return f1

for m in sys.argv[1:]:
    print(f"\n>>> {m} 시작", flush=True)
    run(m)
