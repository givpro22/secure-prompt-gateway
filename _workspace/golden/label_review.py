# -*- coding: utf-8 -*-
"""홀드아웃 v2 오차 9건의 라벨 재검토.
모델이 틀린 것인지, 내 라벨이 과했던 것인지를 가른다. LLM 호출 없음."""
import json

REVIEW = {
 # 미탐 8건
 "우리 납품 단가는 개당 1만 8천원입니다.": ("모델오류", "CONFIDENTIAL",
   "'납품 단가'는 B2B 개별 계약가를 가리키는 말이다. 공표 정가라면 '판매가'라고 쓴다. "
   "거래처가 알면 다음 협상에서 그대로 하한이 된다."),
 "그 채널의 광고 단가는 기존의 절반입니다.": ("모델오류", "CONFIDENTIAL",
   "할인 폭을 드러낸다. 다른 매체가 알면 같은 조건을 요구한다."),
 "우리 창고 회전율은 업계 평균을 밑돕니다.": ("모델오류", "CONFIDENTIAL",
   "내부 운영 지표이고 열위를 자백하는 서술이다."),
 "해당 앱의 결제 전환율은 1.2%입니다.": ("모델오류", "CONFIDENTIAL",
   "전환율은 내부 분석 지표다. 공개하는 회사는 없다."),
 "품질 클레임이 들어왔는데 원인 분석 전이라 고객사에 회신을 미루고 있습니다.": ("모델오류", "CONFIDENTIAL",
   "'고객사에 회신을 미루고 있다'가 핵심이다. 신호어가 명시적인데도 놓쳤다."),

 "이번 노선의 탑승률은 62% 수준입니다.": ("라벨과함", "AMBIGUOUS",
   "항공사는 월별 수송실적(탑승률 포함)을 공시하는 경우가 많다. 이 수치가 공시분인지 "
   "내부 추정인지는 문장만으로 알 수 없다. 사람도 같은 이유로 망설인다."),
 "그 매장의 임대 계약이 내년 2월에 끝납니다.": ("라벨과함", "AMBIGUOUS",
   "만료일 자체는 등기로 확인되기도 한다. 폐점 신호라는 해석이 붙어야 기밀이 되는데 "
   "그 해석은 문장 밖에 있다."),
 "그 설비의 잔여 감가상각 기간은 1년입니다.": ("라벨과함", "AMBIGUOUS",
   "감가상각 정책은 재무제표 주석에 자산군 단위로 공시된다. 개별 설비 잔여기간이 "
   "유출됐을 때의 손해도 특정하기 어렵다."),

 # 오탐 1건
 "이번 주 회의 참석자 명단을 정리해주세요.": ("모델오류", "NONE",
   "명단을 '서술'한 게 아니라 '정리해달라'는 요청이다. 프롬프트의 "
   "'조건·상태·명단을 단정적으로 서술하면'에서 '명단'이라는 단어가 유발했을 가능성이 크다."),
}

cases = json.load(open("holdout2.json"))["cases"]
amb = {t for t,(k,_,_) in REVIEW.items() if k=="라벨과함"}

print("=" * 78)
for kind in ("모델오류", "라벨과함"):
    items = [(t,v) for t,v in REVIEW.items() if v[0]==kind]
    head = "모델이 틀렸다 — 라벨은 유효" if kind=="모델오류" else "내 라벨이 과했다 — 문맥 없이는 판단 불가"
    print(f"\n## {head}  ({len(items)}건)\n")
    for t,(_,lab,why) in items:
        print(f"  · {t}")
        print(f"      {why}\n")

# 재계산 — AMBIGUOUS 3건을 채점에서 제외
def score(exclude):
    tp=fp=tn=fn=0
    miss = {t for t,(k,_,_) in REVIEW.items() if k=="모델오류"} | amb
    for c in cases:
        if c["t"] in exclude: continue
        wrong = c["t"] in REVIEW
        if c["l"]=="CONFIDENTIAL":
            if wrong: fn+=1
            else: tp+=1
        else:
            if wrong: fp+=1
            else: tn+=1
    p=tp/(tp+fp); r=tp/(tp+fn)
    return p, r, 2*p*r/(p+r), tp, fp, fn, tp+tn+fp+fn

print("=" * 78)
print("\n## 재계산\n")
for name, ex in (("원래 (111문장)", set()), ("AMBIGUOUS 3건 제외 (108문장)", amb)):
    p,r,f1,tp,fp,fn,n = score(ex)
    print(f"  {name:28s} 정밀도 {p:.3f} · 재현율 {r:.3f} · F1 {f1:.3f} · 미탐 {fn} · 오탐 {fp}")
print(f"\n  → 미탐 8건 중 3건은 모델 탓이 아니었다. 재현율 0.822 → {score(amb)[1]:.3f}")
