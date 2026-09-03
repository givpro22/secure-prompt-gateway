<script setup>
/*
 * 좌측 사이드바.
 *
 * 계정 전환이 하단 사용자 칩에 붙어 있는 이유는 로그인이 없기 때문이다(기획서 0.3).
 * 부서에 따라 판정이 갈리는 것이 데모의 핵심이라 상시 손닿는 자리에 둔다.
 *
 * "이번 세션"은 시안의 오늘/어제 이력 자리다. 대화 영속화가 범위 밖이라 지난 대화는
 * 만들 수 없고, 지금 화면에 있는 것만 보여준다 — 눌러도 아무것도 안 열리는 목록보다 낫다.
 */
import { useRouter } from 'vue-router'
import UserMenu from './UserMenu.vue'
import { useSessionStore } from '../stores/session'
import { useThreadStore } from '../stores/thread'
import { STATUS_TERMS } from '../lib/terms'
import { computed } from 'vue'

const session = useSessionStore()
const thread = useThreadStore()
const router = useRouter()

/*
 * 시연용 대화 이력. **전부 (demo) 표시가 붙는다.**
 *
 * 대화 영속화는 범위 밖이라(0.3) 실제 지난 대화가 없다. 표시 없이 그려두면 화면에
 * 있는 것과 없는 것을 구분할 수 없고, 시연 중에 눌렀을 때 아무것도 안 열린다.
 * 눌리면 그 문장이 입력창에 들어가도록 해 죽은 목록이 되지 않게 했다.
 *
 * `answers`는 `prompts`와 자리를 맞춘 응답이다. **모델을 부른 결과가 아니라 미리 적어
 * 둔 문장이다** (0.3 — 실제 LLM 호출은 범위 밖). 판정만 뜨고 답이 없으면 대화로
 * 보이지 않아서 넣었다.
 *
 * 내용은 모델이 **마스킹된 본문만 봤다는 전제로** 썼다. 가려진 자리를 답변이 그대로
 * 라벨로 되받는 것이 이 서비스가 하는 일을 가장 짧게 보여준다. 차단된 턴은 애초에
 * 나가지 않았으므로 답이 없다 — `null`로 둔다.
 */
/*
 * 시연용 대화 이력 — **계정마다 다르다.** 전부 (demo) 표시가 붙는다.
 *
 * 대화 영속화는 범위 밖이라(0.3) 실제 지난 대화가 없다. 표시 없이 그려두면 화면에
 * 있는 것과 없는 것을 구분할 수 없고, 시연 중에 눌렀을 때 아무것도 안 열린다.
 *
 * 부서별로 나눈 이유는 판정이 부서 정책에서 갈리기 때문이다. 같은 "SKALA NOVA" 문장이
 * 개발·영업에선 엠바고에 걸려 차단되고 홍보팀에선 자기 정책이라 통과한다 — 계정을
 * 바꿔 가며 같은 말을 넣어 보는 것이 이 데모의 핵심 장면이다. 각 항목의 점 색은
 * 픽스처 규칙에 실제로 넣어 본 판정이다 (2026-09-03 확인).
 *
 * `answers`는 `prompts`와 자리를 맞춘 응답이다. **모델을 부른 결과가 아니라 미리 적어
 * 둔 문장이다** (0.3). 모델이 마스킹된 본문만 봤다는 전제로 썼고, 차단된 턴은 애초에
 * 나가지 않았으므로 `null`이다.
 */
const DEMO_HISTORY_BY_USER = {
  // 개발팀 — 디버깅·코드 리뷰·설계 검토. 개인정보보다 자격증명·내부 주소·엠바고에 걸린다.
  1: [
    { group: '오늘', items: [
      {
        // 개발자가 오류 난 코드를 붙여 넣는 흔한 장면. 개인정보는 없고 사내 IP만 가려진다.
        // 모델이 고친 코드를 통째로 돌려주면 사내 코드가 외부를 한 바퀴 돈 것이다 —
        // 출력 검사의 코드 되돌림 검사가 이것을 잡아 담당자에게 넘긴다 (UC-08).
        key: 'dev-npe', decision: 'MASK', text: '결제 재시도 NPE 디버깅',
        prompts: ['결제 재시도에서 NPE 나는데 봐줘.\nRetryPolicy policy = config.getRetryPolicy();\nint max = policy.getMaxAttempts();\nclient.connect("10.0.3.21", 5432);'],
        answers: ['policy가 null일 수 있습니다. getRetryPolicy()가 설정이 없을 때 null을 돌려주는지 확인하고, 기본값으로 막으세요.\n\nRetryPolicy policy = config.getRetryPolicy();\nif (policy == null) policy = RetryPolicy.defaults();\nint max = policy.getMaxAttempts();\nclient.connect("[내부IP]", 5432);\n\n연결 대상은 [내부IP]로 가려져 있어 그대로 뒀습니다.'],
      },
      {
        key: 'dev-review', decision: 'ALLOW', text: '트랜잭션 경계 코드 리뷰',
        prompts: ['이 코드 리뷰해줘. 트랜잭션 경계가 이상한 것 같아.\n@Transactional\npublic void settle(Order o) {\n  repo.save(o);\n  mailer.send(o.email());\n}'],
        answers: ['트랜잭션 안에서 메일을 보내는 게 문제입니다. 두 가지가 생깁니다.\n\n1. 메일 전송이 느리면 그 시간만큼 DB 락을 잡고 있습니다\n2. 메일은 나갔는데 커밋이 실패하면 되돌릴 수 없습니다\n\n저장은 트랜잭션 안에, 메일은 커밋 후로 빼세요. Spring이면 TransactionSynchronization.afterCommit이나 도메인 이벤트 + @TransactionalEventListener(AFTER_COMMIT)가 그 자리입니다.'],
      },
      {
        // 차단당하고 → 문제가 된 부분을 빼고 다시 보내 → 통과한다.
        key: 'dev-dburl', decision: 'BLOCK', text: '결제 오류 로그 확인',
        prompts: [
          '결제 오류 로그 좀 봐줘. DB_URL=postgres://admin:p%40ss@10.0.3.21/prod 붙이면 죽어.',
          '접속 문자열은 빼고 다시. 커넥션 타임아웃 오류 원인만 짚어줘.',
          '재시도 로직은 어떻게 잡는 게 좋아?',
        ],
        answers: [
          null,
          '커넥션 타임아웃이면 먼저 볼 곳은 세 군데입니다.\n\n1. 커넥션 풀 고갈 — 반납되지 않은 커넥션이 쌓이면 새 요청이 대기만 하다 끊깁니다\n2. 네트워크 경로 — 보안 그룹이나 방화벽이 특정 포트만 막고 있는 경우\n3. DB 쪽 max_connections 상한\n\n풀 사용률 그래프가 상한에 붙어 있는지부터 보시는 걸 권합니다. 대개 1번입니다.',
          '지수 백오프에 지터를 얹는 방식이 무난합니다.\n\n· 초기 대기 200ms, 배수 2, 최대 3회\n· 매 회차에 ±20% 무작위를 더해 재시도가 한꺼번에 몰리지 않게\n· 타임아웃 자체는 재시도하되 인증 실패나 문법 오류는 즉시 포기',
        ],
      },
    ]},
    { group: '어제', items: [
      {
        key: 'dev-redis', decision: 'ALLOW', text: 'Redis 캐시 키 설계 검토',
        prompts: ['Redis 캐시 키 설계 검토해줘. 사용자별 세션은 sess:{userId}, 상품은 prod:{id} 로 잡았어.'],
        answers: ['방향은 맞습니다. 세 가지만 더 잡으면 됩니다.\n\n1. 접두사에 버전을 넣으세요 — sess:v1:{userId}. 구조가 바뀔 때 키를 일괄 무효화할 수 있습니다\n2. TTL — 세션은 만료가 있어야 하고, 상품은 갱신 이벤트에서 삭제하는 쪽이 낫습니다\n3. 키 수가 많은 컬렉션은 해시 태그 {…}로 같은 슬롯에 묶으면 클러스터에서 한 번에 다룰 수 있습니다'],
      },
      {
        // 개발팀에는 엠바고가 걸린다. 홍보팀이 정한 정책이 개발팀에 적용되는 장면 (D19).
        key: 'dev-nova', decision: 'BLOCK', text: 'NOVA 위젯 A/B 지표 해석',
        prompts: ['NOVA 추천 위젯 A/B 테스트 지표 해석해줘. 전환율 3.1% vs 3.8%.'],
        answers: [null],
      },
    ]},
  ],
  // 영업팀 — 전략·제안·파이프라인. 고객 이름과 고객사명이 딸려 들어온다.
  2: [
    { group: '오늘', items: [
      {
        key: 'sales-strategy', decision: 'MASK', text: '고객사별 분기 영업 전략',
        prompts: ['이 고객사 담당자 리스트 보고 분기 영업 전략 세워줘. 조종원(제조), 서지윤(유통), 박재현(금융) 순으로 접점이 많아.'],
        answers: ['담당자 이름은 [고객명]으로 가려져 있어 업종 기준으로 잡았습니다.\n\n· 제조 — 접점이 가장 많으니 분기 초에 확장 제안. 기존 라인 증설 수요를 먼저 확인\n· 유통 — 성수기 전 재계약 시점을 잡아 조건 협의\n· 금융 — 보안 요건이 관건. 인증·감사 대응 자료를 먼저 준비\n\n각 담당자와의 다음 접점 일정을 주시면 주차별로 나눠 드리겠습니다.'],
      },
      {
        // 엠바고에 걸려 차단 → 제품명을 빼고 다시 → 통과. 영업팀에도 홍보팀 정책이 걸린다.
        key: 'sales-nova', decision: 'BLOCK', text: 'NOVA 포지셔닝 전략',
        prompts: [
          'SKALA NOVA 경쟁사 대비 포지셔닝 전략 세워줘. 가격대와 타깃 고객 중심으로.',
          '출시 예정 신제품 경쟁사 대비 포지셔닝 전략 세워줘. 제품명은 비우고 가격대와 타깃 중심으로.',
        ],
        answers: [
          null,
          '제품명을 비운 포지셔닝 뼈대입니다.\n\n· 가격대 — 경쟁사 중간 가격에서 10% 아래로 시작해 도입 장벽을 낮추고, 부가 모듈로 단가를 회복\n· 타깃 — 중견 기업의 운영팀. 대기업은 기존 벤더 락인이 강하고, 소기업은 예산이 없음\n· 메시지 — "도입 2주"처럼 시간으로 말하기. 기능 비교표는 뒤에\n\n제품명과 출시일은 공개 시점에 채우시면 됩니다.',
        ],
      },
    ]},
    { group: '어제', items: [
      {
        // 고객사명 + 프로젝트 — 규칙이 결정하지 못해 AI 제안을 거쳐 담당자가 확정하는 검토 대기 장면.
        key: 'sales-client', decision: 'PENDING', text: 'A사 제안 전략',
        prompts: ['A사 차세대 프로젝트 제안 전략 잡아줘. 경쟁사 대비 우리 강점 위주로.'],
        answers: [null],
      },
      {
        key: 'sales-pipeline', decision: 'ALLOW', text: '분기 파이프라인 우선순위',
        prompts: ['분기 파이프라인 정리하고 우선순위 매겨줘. 신규 12건, 갱신 7건, 이탈 위험 3건.'],
        answers: ['우선순위는 이 순서를 권합니다.\n\n1. 이탈 위험 3건 — 잃으면 매출이 바로 빠지고 되돌리기 어렵습니다. 이번 주 안에 접촉\n2. 갱신 7건 — 성사 확률이 가장 높습니다. 조건 변경 여부만 확인해 빠르게 마감\n3. 신규 12건 — 규모·성사 가능성으로 2단계로 나눠 상위 4건에 집중\n\n각 건의 금액과 마감 예정일을 주시면 주차별 계획으로 옮겨 드리겠습니다.'],
      },
    ]},
  ],
  // 인사팀 — 채용·규정·안내문 초안. 연락처와 주민번호가 딸려 들어온다.
  3: [
    { group: '오늘', items: [
      {
        key: 'hr-interview', decision: 'MASK', text: '지원자 이력 요약과 면접 질문',
        prompts: ['이 지원자 이력 요약하고 면접 질문 5개 뽑아줘. 백엔드 3년, 연락처 010-1234-5678.'],
        answers: ['요약 — 백엔드 3년. 연락처는 [전화번호]로 가려져 있어 요약에 넣지 않았습니다.\n\n면접 질문 5개\n1. 가장 오래 운영한 서비스에서 장애를 어떻게 잡았는지\n2. 트랜잭션 경계를 잘못 잡아 문제가 됐던 경험\n3. 성능 개선을 수치로 말해 본다면\n4. 코드 리뷰에서 자주 지적하는 것과 지적받는 것\n5. 3년차에 가장 아쉬웠던 기술 선택'],
      },
      {
        key: 'hr-payroll', decision: 'MASK', text: '퇴직금 정산 규정 초안',
        prompts: ['퇴직금 정산 규정 초안 잡아줘. 계산 기준은 주민번호 900101-1234567 사원 사례로.'],
        answers: ['규정 초안 뼈대입니다. 사례로 주신 사원 정보는 [주민번호]로 가려져 있어 일반 기준으로 썼습니다.\n\n제1조 목적 · 제2조 적용 대상 · 제3조 산정 기준(평균임금 × 근속연수) · 제4조 지급 시기(퇴직일로부터 14일 이내) · 제5조 예외\n\n사례는 규정에 싣지 말고 별도 계산 예시 문서로 두는 것을 권합니다.'],
      },
    ]},
    { group: '어제', items: [
      {
        key: 'hr-onboarding', decision: 'ALLOW', text: '신입 온보딩 체크리스트',
        prompts: ['신입 온보딩 2주 체크리스트 만들어줘. 개발 직군 기준.'],
        answers: ['2주 체크리스트입니다.\n\n1주차 — 계정·장비, 저장소 접근, 로컬 개발 환경, 코드 컨벤션 읽기, 첫 PR(문서 수정 정도)\n2주차 — 서비스 구조 세션, 온콜 절차, 작은 기능 하나 끝까지, 멘토 회고\n\n각 항목에 담당자를 붙이면 바로 쓸 수 있습니다.'],
      },
      {
        key: 'hr-leave', decision: 'ALLOW', text: '연차 촉진 안내문',
        prompts: ['연차 촉진 안내문 초안 써줘. 미사용 연차 3일 이상인 직원 대상.'],
        answers: ['안내문 초안입니다.\n\n[안내] 연차 사용 촉진\n\n미사용 연차가 3일 이상인 분들께 안내드립니다. 연말 소멸 전 사용 계획을 이달 말까지 회신해 주세요. 회신이 없으면 회사가 사용 시기를 지정할 수 있습니다(근로기준법 제61조).\n\n문의는 인사팀으로 부탁드립니다.'],
      },
    ]},
  ],
  // 정보보안팀 — 정책을 만드는 쪽이라 검사받는 프롬프트가 적다. 훈련·보고 정도.
  4: [
    { group: '오늘', items: [
      {
        key: 'sec-phishing', decision: 'ALLOW', text: '피싱 모의훈련 시나리오',
        prompts: ['피싱 모의훈련 시나리오 3개 만들어줘. 난이도 상중하.'],
        answers: ['난이도별 시나리오입니다.\n\n하 — 외부 도메인에서 온 "비밀번호 만료" 메일. 링크 주소가 대놓고 다릅니다\n중 — 사내 도메인을 흉내 낸 발신자, 실제 공지 양식을 베낀 "급여명세서 확인"\n상 — 실제 진행 중인 프로젝트명을 언급하며 협업 툴 초대를 가장한 메일\n\n각 단계에서 클릭률과 신고율을 같이 재면 교육 효과가 보입니다.'],
      },
    ]},
    { group: '어제', items: [
      {
        key: 'sec-audit', decision: 'ALLOW', text: '분기 보안 점검 요약',
        prompts: ['분기 보안 점검 결과 경영진 보고용으로 요약해줘. 취약점 3건, 조치 완료 2건.'],
        answers: ['경영진 보고용 요약입니다.\n\n· 발견 3건 중 2건 조치 완료, 1건 진행 중(다음 분기 초 완료 예정)\n· 재발 방지 — 취약점 유형을 정기 점검 항목에 반영\n\n첫 줄만 읽어도 상태가 보이게 썼습니다.'],
      },
    ]},
  ],
  // 홍보팀 — 시안·카피·보도자료. 엠바고를 정하는 부서라 같은 NOVA 문장이 여기서는 통과한다 (D19).
  5: [
    { group: '오늘', items: [
      {
        key: 'pr-visual', decision: 'ALLOW', text: 'NOVA 런칭 키비주얼 콘셉트',
        prompts: ['SKALA NOVA 런칭 키비주얼 시안 콘셉트 3개 뽑아줘. 9월 20일 발표.'],
        answers: ['콘셉트 3개입니다.\n\n1. "새벽" — 어두운 배경에 한 줄기 빛. 새로 시작하는 느낌, 제품명 타이포만 크게\n2. "연결" — 점과 선이 이어지는 네트워크. 팀을 잇는 도구라는 메시지\n3. "속도" — 잔상이 남는 사선 구도. 도입 2주를 시각으로\n\n1번이 보도자료 썸네일과 가장 잘 맞습니다.'],
      },
      {
        key: 'pr-quote', decision: 'MASK', text: '고객 인터뷰 인용문 다듬기',
        prompts: ['고객 인터뷰 인용문 다듬어줘. 조종원 고객이 "도입 후 처리 시간이 반으로 줄었다"고 했어.'],
        answers: ['다듬은 인용문입니다.\n\n"도입 후 처리 시간이 절반으로 줄었습니다. 팀 전체가 체감하고 있어요." — [고객명] 님\n\n이름이 [고객명]으로 가려져 있어 자리만 잡아 뒀습니다. 게재 전 본인 동의를 받은 표기로 채워 주세요.'],
      },
    ]},
    { group: '어제', items: [
      {
        key: 'pr-sns', decision: 'ALLOW', text: '런칭 SNS 카피 10개',
        prompts: ['신제품 런칭 SNS 카피 10개 써줘. 20대 타깃, 짧게.'],
        answers: ['10개입니다.\n\n1. 기다린 만큼, 빠르게.\n2. 오늘부터 일이 가벼워진다\n3. 클릭 하나로 끝\n4. 복잡한 건 우리가\n5. 팀이 먼저 알아본 도구\n6. 2주면 충분해\n7. 설명 대신 써보기\n8. 늦게 오는 답은 없다\n9. 일요일에도 부담 없이\n10. 시작은 지금\n\n짧은 순서로 배치했습니다. 해시태그는 채널별로 붙이시면 됩니다.'],
      },
    ]},
  ],
}

/** 지금 계정의 데모 이력. 없는 계정은 빈 목록이다 */
const demoHistory = computed(() => DEMO_HISTORY_BY_USER[session.currentUserId] ?? [])

/*
 * 계정별 시연용 작성 중 초안. 부서에 따라 쓰는 말이 다르다는 것을 보여주는 자리다.
 *
 * **비어 있는 계정이 둘 있다.** 모두가 뭔가를 쓰다 만 상태로 두면 자리 채우기로
 * 보인다. 하필 정보보안팀과 홍보팀을 비운 것도 이유가 있다 — 정책을 만드는 쪽이라
 * 검사받는 프롬프트를 쓰고 있을 자리가 아니다.
 */
const DEMO_DRAFTS = {
  1: {
    key: 'w-dev',
    text: '결제 재시도 설계 검토',
    prompt: '결제 실패 재시도 로직 설계 검토해줘. 백오프 간격이랑 상한 어떻게 잡는 게 좋아?',
  },
  2: {
    key: 'w-sales',
    text: '제안서 요약 초안',
    prompt: '고객사 제안서 요약 초안 잡아줘. 도입 효과랑 일정 위주로.',
  },
  3: {
    key: 'w-hr',
    text: '분기 보고서 개요',
    prompt: '3분기 실적 보고서 개요 잡아줘. 매출·이슈·다음 분기 계획 순서로.',
  },
}

const demoDraft = computed(() => DEMO_DRAFTS[session.currentUserId] ?? null)

const isAdmin = computed(() => session.currentUser?.role === 'SECURITY_ADMIN')

/*
 * 데모 대화는 지금 대화를 갈아끼운다. 이어붙이면 서로 다른 세션의 판정이 한 흐름처럼
 * 보이고, 누를수록 쌓이기만 한다.
 */
function openDemo(item) {
  thread.openDemo(item.key, item.prompts, item.answers ?? [])
  if (router.currentRoute.value.name !== 'chat') router.push('/chat')
}

/*
 * 작성 중을 누르면 **새 대화로 연다.** 지금 대화 뒤에 붙이면 보내지도 않은 문장이
 * 앞 판정들 아래에 끼어들어, 한 흐름처럼 보인다. 쓰다 만 것은 아직 시작하지 않은
 * 대화이므로 빈 화면에 입력만 얹은 상태가 맞다.
 */
function restoreDraft(text, key) {
  thread.openDraft(key, text)
  if (router.currentRoute.value.name !== 'chat') router.push('/chat')
}




function newChat() {
  thread.clear()
  if (router.currentRoute.value.name !== 'chat') router.push('/chat')
}

function token(decision) {
  return STATUS_TERMS[decision]?.token ?? 'gray'
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <span class="mark" aria-hidden="true">SP</span>
      <span class="name">
        <strong>Secure Prompt</strong>
        <em>Gateway</em>
      </span>
    </div>

    <button type="button" class="new-chat" @click="newChat">＋ 새 대화</button>

    <nav class="nav">
      <RouterLink to="/chat" class="nav-item">직원 AI 챗</RouterLink>
      <!-- 감사 콘솔은 보안 담당자에게만 보인다. 라우터도 같은 조건으로 막는다 -->
      <RouterLink v-if="isAdmin" to="/admin/audit" class="nav-item">관리자 감사 콘솔</RouterLink>
    </nav>

    <div class="scroll">
      <section v-if="thread.current" class="history">
        <h2>이번 세션</h2>
        <ul>
          <li>
            <button
              type="button"
              class="history-item"
              :class="{ current: thread.viewing === 'own' }"
              @click="thread.resumeOwn()"
            >
              <span class="dot" :class="`t-${token(thread.current.decision)}`" aria-hidden="true" />
              <span class="text">{{ thread.current.title }}</span>
              <span v-if="thread.current.turns > 1" class="turns">{{ thread.current.turns }}턴</span>
            </button>
          </li>
        </ul>
      </section>

      <section class="history">
        <h2>작성 중</h2>
        <ul>
          <li v-if="thread.writing">
            <button
              type="button"
              class="history-item"
              @click="restoreDraft(thread.writing.text, 'w-live')"
            >
              <span class="dot writing" aria-hidden="true" />
              <span class="text">{{ thread.writing.text }}</span>
            </button>
          </li>
          <li v-if="demoDraft">
            <button
              type="button"
              class="history-item"
              @click="restoreDraft(demoDraft.prompt, demoDraft.key)"
            >
              <span class="dot writing" aria-hidden="true" />
              <span class="text">{{ demoDraft.text }}</span>
              <span class="demo">(demo)</span>
            </button>
          </li>
          <li v-if="!thread.writing && !demoDraft" class="none">작성 중인 내용이 없습니다</li>
        </ul>
      </section>

      <section v-for="block in demoHistory" :key="block.group" class="history">
        <h2>{{ block.group }}</h2>
        <ul>
          <li v-for="item in block.items" :key="item.key">
            <button
              type="button"
              class="history-item"
              :class="{ current: thread.viewing === item.key }"
              @click="openDemo(item)"
            >
              <span class="dot" :class="`t-${token(item.decision)}`" aria-hidden="true" />
              <span class="text">{{ item.text }}</span>
              <span v-if="item.prompts.length > 1" class="turns">{{ item.prompts.length }}턴</span>
              <span class="demo">(demo)</span>
            </button>
          </li>
        </ul>
      </section>
    </div>

    <div class="account">
      <UserMenu />
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 258px;
  flex: none;
  height: 100%;
  overflow: visible;
  padding: 18px 16px;
  background: var(--nav-bg);
  color: var(--nav-fg);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 4px;
}

.mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: var(--nav-bg-active);
  color: var(--nav-fg);
  font-size: 13px;
  font-weight: 700;
}

.name {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.name strong {
  font-size: 15.5px;
}

.name em {
  font-style: normal;
  font-size: 12.5px;
  color: var(--nav-muted);
}

.new-chat {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid var(--nav-line);
  border-radius: 8px;
  background: var(--nav-bg-soft);
  color: var(--nav-fg);
  font: inherit;
  font-size: 14px;
  font-weight: 600;
}

.new-chat:hover {
  background: var(--nav-bg-active);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--nav-muted);
  text-decoration: none;
  font-size: 15px;
}

.nav-item:hover {
  background: var(--nav-bg-soft);
  color: var(--nav-fg);
}

.nav-item.router-link-active {
  background: var(--nav-bg-active);
  color: var(--nav-fg);
  font-weight: 700;
}

.history h2 {
  margin: 6px 0 6px 4px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--nav-muted);
}

.scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history ul {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--nav-muted);
  font: inherit;
  font-size: 13.5px;
  text-align: left;
}

.history-item.current {
  background: var(--nav-bg-soft);
  color: var(--nav-fg);
}

.history-item:hover {
  background: var(--nav-bg-soft);
  color: var(--nav-fg);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex: none;
  background: var(--nav-muted);
}

.t-green {
  background: #4ec98a;
}
.t-amber {
  background: #e0a63c;
}
.t-red {
  background: #e5705a;
}
.t-purple {
  background: #a78bde;
}

.none {
  padding: 7px 12px;
  color: var(--nav-fg-dim);
  font-size: 13px;
}

/* 작성 중 — 판정이 아직 없다. 채우지 않고 테두리만 둔다 */
.dot.writing {
  background: transparent;
  border: 1.5px solid var(--nav-muted);
}

.turns {
  flex: none;
  font-size: 11.5px;
  color: var(--nav-muted);
}

.demo {
  flex: none;
  font-size: 11.5px;
  color: var(--nav-line);
}

.text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account {
  padding-top: 10px;
  border-top: 1px solid var(--nav-line);
}


.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
}
</style>
