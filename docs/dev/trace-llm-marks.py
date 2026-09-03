#!/usr/bin/env python3
"""상용 LLM 로고 이미지를 SVG 경로로 추적한다.

`frontend/src/components/llmMarkPaths.js`를 다시 만드는 스크립트다. 손으로 그린
근사가 13px에서 알아볼 수 없게 무너져서 원본 윤곽을 그대로 따는 쪽으로 바꿨다.

원본 이미지는 저장소에 넣지 않는다. 각 사 브랜드 페이지에서 받아 `--src`로 넘긴다.

    pip install potracer pillow numpy
    python3 docs/dev/trace-llm-marks.py --src chatgpt=~/Downloads/openai.png ...

흰 배경 위 단색 로고를 가정한다. 배경 판정은 밝기가 아니라 흰색과의 거리로 한다 —
Gemini의 노란 꼭짓점은 밝지만 배경이 아니다.
"""

import argparse
import json
from pathlib import Path

import numpy as np
import potrace
from PIL import Image

BOX = 16.0   # viewBox 한 변
PAD = 0.3    # 여백

# 로고마다 곡선 단순화 정도가 다르다. 매듭이 촘촘한 ChatGPT는 조이고,
# 면이 넓은 Gemini는 풀어야 경로가 짧아진다.
TOLERANCE = {"chatgpt": 0.4, "gemini": 0.6, "claude": 0.4, "grok": 0.4}


def to_mask(path: Path) -> np.ndarray:
    """도형이 True인 불리언 마스크. 투명 배경은 흰색 위에 합성한다."""
    a = np.array(Image.open(path).convert("RGBA")).astype(np.float32)
    alpha = a[..., 3:4] / 255.0
    composited = a[..., :3] / 255.0 * alpha + (1 - alpha)
    return composited.min(axis=2) < 0.88


def trace(mask: np.ndarray, tolerance: float) -> str:
    ys, xs = np.where(mask)
    mask = mask[ys.min():ys.max() + 1, xs.min():xs.max() + 1]

    h, w = mask.shape
    span = BOX - 2 * PAD
    scale = span / max(w, h)
    ox = PAD + (span - w * scale) / 2
    oy = PAD + (span - h * scale) / 2
    fx = lambda x: round(ox + x * scale, 2)   # noqa: E731
    fy = lambda y: round(oy + y * scale, 2)   # noqa: E731

    # potrace.Bitmap은 생성자에서 invert()를 부른다. 도형을 그대로 넘기면
    # 배경이 추적되므로 뒤집어서 넣는다.
    curves = potrace.Bitmap(~mask).trace(
        turdsize=max(4, int(w * h * 3e-4)),
        alphamax=1.0,
        opttolerance=tolerance,
    )

    out = []
    for curve in curves:
        start = curve.start_point
        out.append(f"M{fx(start.x)} {fy(start.y)}")
        for seg in curve:
            end = seg.end_point
            if seg.is_corner:
                out.append(f"L{fx(seg.c.x)} {fy(seg.c.y)}L{fx(end.x)} {fy(end.y)}")
            else:
                out.append(
                    f"C{fx(seg.c1.x)} {fy(seg.c1.y)} "
                    f"{fx(seg.c2.x)} {fy(seg.c2.y)} {fx(end.x)} {fy(end.y)}"
                )
        out.append("Z")
    return "".join(out)


HEADER = """/*
 * 상용 서비스 마크의 윤곽선.
 *
 * 각 사가 공개한 로고 이미지를 16x16 viewBox로 추적한 것이다. 손으로 그린 근사가
 * 아니라 원본 윤곽이라 13px에서도 형태가 무너지지 않는다.
 *
 * **원본 브랜드 자산 자체는 아니다.** 목록에서 서비스를 알아보게 하는 용도이며,
 * 정식 배포물에는 각 사 브랜드 가이드의 자산을 받아 교체해야 한다.
 *
 * 재생성: docs/dev/trace-llm-marks.py
 */
"""


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", action="append", required=True,
                    metavar="ID=PATH", help="예: chatgpt=~/Downloads/openai.png")
    ap.add_argument("--out", type=Path,
                    default=Path("frontend/src/components/llmMarkPaths.js"))
    args = ap.parse_args()

    paths = {}
    for item in args.src:
        name, _, raw = item.partition("=")
        paths[name] = trace(to_mask(Path(raw).expanduser()), TOLERANCE.get(name, 0.4))
        print(f"  {name:8} {len(paths[name]):5}자")

    body = "\n".join(
        f"export const {k.upper()}_MARK =\n  {json.dumps(v)}\n" for k, v in paths.items()
    )
    args.out.write_text(HEADER + "\n" + body, encoding="utf-8")
    print(f"→ {args.out}")


if __name__ == "__main__":
    main()
