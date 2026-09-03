import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * 규칙 후보 게이트. 실제 런타임과 같은 java.util.regex로 검증한다.
 *
 * Python re로 검증하면 Java가 지원하는 가변 길이 룩비하인드를 멋대로 기각한다 —
 * 계좌번호 규칙(PII-ACCOUNT-06)이 이미 그 문법을 쓰고 있다.
 *
 * 인자: <pattern> <examples.txt> <negatives.txt>
 * 출력: OK  또는  FAIL <사유>
 */
public class RuleGate {
    public static void main(String[] args) throws Exception {
        Pattern p;
        try {
            p = Pattern.compile(args[0]);
        } catch (PatternSyntaxException e) {
            System.out.println("FAIL 컴파일 실패: " + e.getDescription());
            return;
        }
        List<String> examples = Files.readAllLines(Path.of(args[1]));
        List<String> negatives = Files.readAllLines(Path.of(args[2]));

        List<String> missed = new ArrayList<>();
        for (String e : examples) if (!e.isBlank() && !p.matcher(e).find()) missed.add(e);
        if (!missed.isEmpty()) { System.out.println("FAIL 예시를 못 잡음: " + missed); return; }

        List<String> fp = new ArrayList<>();
        for (String n : negatives) if (!n.isBlank() && p.matcher(n).find()) fp.add(n);
        if (!fp.isEmpty()) {
            System.out.println("FAIL 참음성 오탐 " + fp.size() + "건: "
                    + fp.subList(0, Math.min(3, fp.size())));
            return;
        }
        // 무엇을 가리는지도 보여준다. 사람이 승인 화면에서 볼 값이다.
        StringBuilder sb = new StringBuilder("OK 매칭 예:");
        for (String e : examples) {
            Matcher m = p.matcher(e);
            if (m.find()) sb.append(" [").append(m.group()).append("]");
        }
        System.out.println(sb);
    }
}
