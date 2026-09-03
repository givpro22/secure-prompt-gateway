# -*- coding: utf-8 -*-
"""홀드아웃 1회 실행. 이 수치를 본 뒤 프롬프트를 고치면 홀드아웃이 아니게 된다."""
import hashlib, json, re, sys, time, urllib.request
from collections import defaultdict

JAVA = "../../backend/src/main/java/com/skala/gateway/ai/PromptAssembler.java"
raw = open(JAVA, "rb").read()
h = hashlib.md5(raw).hexdigest()
frozen = open("PROMPT_FROZEN.md5").read().strip()
print(f"프롬프트 해시 {h}  (동결값 {frozen})  {'일치' if h == frozen else '★불일치 — 점수 무효★'}\n")

src = raw.decode("utf-8")
body = src.split('SENTENCE_SYSTEM_PROMPT = """', 1)[1].split('""";', 1)[0]
SYSTEM = "\n".join(l[12:] if l.startswith(" " * 12) else l.strip()
                   for l in body.split("\n")).strip()

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
MODEL = sys.argv[1] if len(sys.argv) > 1 else "qwen2.5:7b-instruct"

def classify(text):
    body = json.dumps({"model": MODEL, "stream": False, "think": False, "format": FMT,
                       "options": {"temperature": 0, "num_predict": 200},
                       "messages": [{"role": "system", "content": SYSTEM},
                                    {"role": "user", "content": "문장 목록:\n1. " + text}]}).encode()
    # 타임아웃 없이 열면 요청 하나가 멎을 때 전체가 영구히 매달린다. 실제로 겪었다.
    req = urllib.request.Request("http://localhost:11434/api/chat", body,
                                 {"Content-Type": "application/json"})
    for attempt in range(3):
        try:
            d = json.load(urllib.request.urlopen(req, timeout=60))
            return next((x["label"] for x in json.loads(d["message"]["content"])["items"]
                         if x["index"] == 1), "NONE")
        except Exception as e:
            print(f"      재시도 {attempt+1}/3 — {type(e).__name__}", flush=True)
    return "NONE"

# SentenceSplitter.java 와 같은 규칙: 종결부호 다음이 공백이거나 끝일 때만 자른다
def split(text):
    out, cur = [], 0
    for i, c in enumerate(text):
        if c == "\n" or (c in ".!?" and (i + 1 >= len(text) or text[i + 1].isspace())):
            s = text[cur:i + 1].strip()
            if s: out.append(s) if len(s) >= 6 or not out else out.__setitem__(-1, out[-1] + " " + s)
            cur = i + 1
    s = text[cur:].strip()
    if s: out.append(s) if len(s) >= 6 or not out else out.__setitem__(-1, out[-1] + " " + s)
    return out

# ── 1. 문장 단위 ────────────────────────────────────────────────────────
CASES = json.load(open("holdout2.json"))["cases"]
sent = [i for i, c in enumerate(CASES) if not covered(c["t"])]
got = {i: "NONE" for i in range(len(CASES)) if i not in sent}
t0 = time.time()
for n, i in enumerate(sent, 1):
    got[i] = classify(CASES[i]["t"])
    if n % 20 == 0: print(f"    … {n}/{len(sent)} ({time.time()-t0:.0f}s)", flush=True)
el = time.time() - t0

tp = fp = tn = fn = 0; per = defaultdict(lambda: [0, 0]); wrong = []
for i, c in enumerate(CASES):
    per[c["g"]][1] += 1
    if c["l"] == got[i]:
        per[c["g"]][0] += 1; tp += c["l"] == "CONFIDENTIAL"; tn += c["l"] == "NONE"
    else:
        fp += c["l"] == "NONE"; fn += c["l"] == "CONFIDENTIAL"; wrong.append((c["g"], c["l"], c["t"]))
p = tp / (tp + fp) if tp + fp else 0
r = tp / (tp + fn) if tp + fn else 0
f1 = 2 * p * r / (p + r) if p + r else 0
print(f"\n{'='*74}\n[문장 단위] {tp+tn}/{len(CASES)} · 정밀도 {p:.3f} · 재현율 {r:.3f} · F1 {f1:.3f}")
print(f"            오탐 {fp}/{tn+fp} ({fp/(tn+fp)*100:.1f}%) · 미탐 {fn}/{tp+fn} · {el:.0f}s")
for g in sorted(per):
    ok, n = per[g]; print(f"    {g:16s} {ok:3d}/{n:3d} {ok/n*100:5.1f}%")
print(f"\n  틀린 것 {len(wrong)}건")
for g, e, t in wrong:
    print(f"    [{'오탐' if e=='NONE' else '미탐'}] {g:14s} {t[:56]}")

# ── 2. 메시지 단위 ──────────────────────────────────────────────────────
MSGS = json.load(open("holdout_messages2.json"))["messages"]
print(f"\n{'='*74}\n[메시지 단위] {len(MSGS)}건")
hit = miss = bad = 0
for m in MSGS:
    flagged = [s for s in split(m["text"]) if not covered(s) and classify(s) == "CONFIDENTIAL"]
    joined = " || ".join(flagged)
    exp_hit = [e for e in m["expect"] if any(e in f for f in flagged)]
    exp_missed = [e for e in m["expect"] if e not in exp_hit]
    forbidden = [f for f in m["forbid"] if any(f in x for x in flagged)]
    hit += len(exp_hit); miss += len(exp_missed); bad += len(forbidden)
    mark = "OK " if not exp_missed and not forbidden else "X  "
    print(f"  {mark}{m['id']} ({m['dept']}) 후보 {len(flagged)}건")
    if flagged: print(f"       → {joined[:110]}")
    if exp_missed: print(f"       놓침: {exp_missed}")
    if forbidden: print(f"       금지 위반: {forbidden}")
print(f"\n  기대 후보 {hit}/{hit+miss} 적중 · 금지 위반 {bad}건")
