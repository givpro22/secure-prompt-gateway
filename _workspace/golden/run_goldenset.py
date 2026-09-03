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
def _rule_coverage():
    """규칙 엔진이 이미 보는 것을 DB에서 직접 읽는다.

    운영의 PolicyRuleCoverageSource와 같은 값을 봐야 골든셋이 운영을 재는 게 된다.
    마이그레이션 SQL을 파싱하면 V4처럼 UPDATE로 패턴을 조이는 변경을 놓친다.

    gateway-pg 컨테이너가 떠 있어야 한다.
    """
    import subprocess
    def q(sql):
        r = subprocess.run(
            ["docker", "exec", "gateway-pg", "psql", "-U", "gateway", "-d", "gateway", "-tA", "-c", sql],
            capture_output=True, text=True)
        if r.returncode != 0:
            raise SystemExit("DB를 읽지 못했습니다. gateway-pg가 떠 있는지 확인하세요.\n" + r.stderr)
        return [l for l in r.stdout.strip().split("\n") if l]
    labels = q("select distinct mask_label from policy_rule "
               "where is_active and mask_label is not null;")
    pats = q("select pattern from policy_rule "
             "where is_active and rule_type = 'REGEX' and pattern is not null;")
    return labels, pats


LABELS, SEED = _rule_coverage()


def _compile(pats):
    """Java Pattern은 되지만 Python re가 못 쓰는 것이 있다 — 가변 길이 lookbehind
    (계좌번호 규칙의 문맥 조건)가 그렇다. 건너뛰되 조용히 넘기지 않는다."""
    import re as _re
    out = []
    for p in pats:
        try:
            out.append(_re.compile(p))
        except _re.error as e:
            print(f"  [사전 필터] Python re가 못 쓰는 패턴을 건너뜁니다: {p[:44]}… ({e})")
    return out


_COMPILED = _compile(SEED)



def covered(t):
    if any(l in t for l in LABELS): return "라벨"
    return "정규식" if any(p.search(t) for p in _COMPILED) else None

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
