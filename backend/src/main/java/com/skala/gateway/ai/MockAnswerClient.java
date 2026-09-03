package com.skala.gateway.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 모델을 부르지 않고 답변을 지어내는 제공자 ({@code ANSWER_PROVIDER=mock}).
 *
 * <p>왜 두는가. 시연은 네트워크와 무료 키 할당량 위에서 돌아갈 일이 아니다. 교육장
 * 회선이 흔들리거나 하루 한도가 떨어지면 출력 검사 장면이 통째로 사라진다. 여기서
 * 답을 지어내면 그 뒤 검사 경로는 실제와 완전히 같다 — 갈리는 것은 문장이 어디서
 * 왔는가뿐이고, 규칙 판정도 유출 검사도 확정 절차도 손대지 않는다.
 *
 * <p>이것이 곧 과제 4쪽 <b>Interface First</b>의 증거이기도 하다. Mock과 Gemini가 같은
 * {@link AnswerClient}를 구현하고 환경변수 하나로 갈리며, 프론트엔드는 어느 쪽인지
 * 모른다 — 화면이 받는 JSON이 같기 때문이다.
 *
 * <h2>무엇을 지어내는가</h2>
 *
 * <p>두 갈래다. 코드가 섞인 질문에는 <b>고친 코드를 통째로 되돌려준다.</b> 실제 모델이
 * 하는 일이 그렇고, 사내 코드가 외부를 한 바퀴 돌아 그대로 나오는 것이 유출 검사가
 * 잡아야 할 장면이다. 그 외에는 깨끗하게 답한다 — 기본 경로는 통과여야 한다.
 *
 * <p>질문을 따옴표로 되읽지 않는다. 인용하면 코드 되돌림 검사(40자)에 걸려 깨끗한
 * 질문까지 검토 대기가 된다. 실제 모델도 질문을 그대로 되읽지는 않는다.
 *
 * <p>프론트엔드 픽스처 서버가 같은 문장을 만든다. 두 곳이 갈리면 픽스처로 본 판정과
 * 실서버 판정이 달라진다.
 */
@Component
@ConditionalOnProperty(name = "answer.provider", havingValue = "mock")
public class MockAnswerClient implements AnswerClient {

    private static final Logger log = LoggerFactory.getLogger(MockAnswerClient.class);

    /** 코드로 볼 만한 신호. 줄 끝 기호이거나 흔한 키워드다 */
    private static final Pattern CODE_TAIL = Pattern.compile("[;{}()=]\\s*$", Pattern.MULTILINE);
    private static final Pattern CODE_WORD =
            Pattern.compile("\\b(int|const|let|var|def|return|public|private|class|void)\\b");
    private static final Pattern CODE_LINE = Pattern.compile(".*[;{}()].*");

    private static final String CLEAN_ANSWER = """
            요청하신 내용을 정리했습니다.

            1. 핵심 항목을 먼저 두고 세부는 뒤에 붙였습니다.
            2. 대괄호로 가려진 부분은 그대로 두었습니다.
            3. 더 필요한 항목이 있으면 말씀해 주세요.""";

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String providerName() {
        return "Mock (모델 미호출)";
    }

    @Override
    public Result ask(String maskedPrompt) {
        String masked = maskedPrompt == null ? "" : maskedPrompt;
        log.info("Mock 답변 생성 len={}", masked.length());
        return new Answered(looksLikeCode(masked) ? echoCode(masked) : CLEAN_ANSWER, "mock-1");
    }

    private static boolean looksLikeCode(String text) {
        return CODE_TAIL.matcher(text).find() || CODE_WORD.matcher(text).find();
    }

    /** 코드 줄만 골라 되돌려준다. 되돌림 검사가 잡는 것이 이 장면이다 */
    private static String echoCode(String masked) {
        String body = Arrays.stream(masked.split("\n"))
                .filter(line -> CODE_LINE.matcher(line).matches())
                .collect(Collectors.joining("\n"));
        return "문제가 될 만한 곳을 고쳤습니다.\n\n" + body + "\n\nnull 가능성이 있는 값은 기본값으로 막았습니다.";
    }
}
