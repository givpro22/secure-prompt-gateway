/*
 * 표 파일을 텍스트로 뽑는다 (D17).
 *
 * **파일은 브라우저 밖으로 나가지 않는다.** 여기서 뽑은 텍스트가 입력창에 들어가고,
 * 그 텍스트가 기존 `POST /messages`를 그대로 탄다. 첨부 업로드 API도, 백엔드 파서도
 * 없다 — 검사 대상은 여전히 입력 프롬프트다(0.3). 17장 확장 4번이 말한 "추출기만
 * 추가, 엔진 무변경"이 이 파일 하나다.
 *
 * 스프레드시트를 먼저 붙이는 이유는 그것이 사내에서 가장 많이 새는 그릇이기 때문이다.
 * 고객 명단, 연락처, 계약 조건이 표로 돌아다니고, 사람들은 그 표를 통째로 복사해
 * 외부 모델에 붙인다. 한 줄씩 검사해서는 늦다.
 *
 * <b>형식 검사는 방어가 아니라 안내다.</b> 파일을 브라우저에서만 여는 구조라 여기서
 * 막는 것은 사용자 실수를 줄이는 것이지 공격을 막는 것이 아니다. 화면 문구도 그렇게
 * 적어야 한다.
 */

/** 뽑아 올 최대 행. 시연에서 프롬프트가 화면을 넘기지 않을 만큼이다 */
export const MAX_ROWS = 50

/** 프롬프트 상한. 규칙 엔진이 훑는 문자열이 무한정 길어지지 않게 한다 */
export const MAX_CHARS = 4000

const SHEET_EXT = /\.(xlsx|xlsm|xls)$/i
const TEXT_EXT = /\.(csv|tsv|txt)$/i

/*
 * 받는 형식은 여기 하나로 정한다. input의 accept, 화면 안내, 검사 셋이 갈리면
 * 고를 수는 있는데 거절당하는 파일이 생긴다.
 */
export const ACCEPT_EXT = ['.xlsx', '.xlsm', '.xls', '.csv', '.tsv', '.txt']
export const ACCEPT_ATTR = ACCEPT_EXT.join(',')
/**
 * 화면에 세울 이름. **받는 것 전부가 아니라 대표만** 적는다.
 *
 * xlsm과 tsv는 xlsx·csv와 사실상 같은 것이라 줄을 늘리기만 하고 알려 주는 바가 없다.
 * 뒤에 말줄임을 두어 더 있다는 것만 알린다 — 실제로 받는 목록은 위 ACCEPT_EXT이고
 * input의 accept와 검사도 그것을 쓴다. 여기서 줄인 것은 표기뿐이다.
 */
export const ACCEPT_NAMES = ['xlsx', 'xls', 'csv', 'txt', '…']
/** 한 줄로 적을 때. 오류 문구는 받는 것 전부를 적어야 한다 */
export const ACCEPT_LABEL = ACCEPT_EXT.map((e) => e.slice(1)).join(' · ')

export function isSupported(file) {
  const name = file?.name ?? ''
  return SHEET_EXT.test(name) || TEXT_EXT.test(name)
}

/** 한 줄로 붙인다. 셀 안의 줄바꿈은 행 경계와 섞이므로 공백으로 바꾼다 */
function joinRow(cells) {
  return cells
    .map((c) => String(c ?? '').replace(/\s*\n\s*/g, ' ').trim())
    .join('\t')
    .trimEnd()
}

function fromRows(rows) {
  const used = rows.filter((r) => r.some((c) => String(c ?? '').trim().length > 0))
  const kept = used.slice(0, MAX_ROWS)
  const cols = kept.reduce((n, r) => Math.max(n, r.length), 0)
  return { lines: kept.map(joinRow), rows: used.length, kept: kept.length, cols }
}

/*
 * CSV/TSV는 직접 읽는다. 텍스트 한 장을 열자고 400KB짜리 라이브러리를 받을 이유가 없다.
 * 따옴표로 감싼 셀 안의 구분자와 두 번 쓴 따옴표("")까지는 본다 — 엑셀이 내보내는 CSV가
 * 그 형식이다.
 */
export function parseDelimited(text, delimiter) {
  const rows = []
  let row = []
  let cell = ''
  let quoted = false

  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i]
    if (quoted) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          cell += '"'
          i += 1
        } else quoted = false
      } else cell += ch
      continue
    }
    if (ch === '"') quoted = true
    else if (ch === delimiter) {
      row.push(cell)
      cell = ''
    } else if (ch === '\n') {
      row.push(cell)
      rows.push(row)
      row = []
      cell = ''
    } else if (ch !== '\r') cell += ch
  }
  row.push(cell)
  rows.push(row)
  return rows
}

/** 구분자를 첫 줄에서 고른다. 탭이 더 많으면 TSV다 */
function sniffDelimiter(text) {
  const head = text.slice(0, text.indexOf('\n') + 1 || text.length)
  return (head.match(/\t/g) ?? []).length > (head.match(/,/g) ?? []).length ? '\t' : ','
}

/**
 * 파일에서 텍스트를 뽑는다.
 *
 * @returns {Promise<{text: string, meta: object}>}
 */
export async function extractFromFile(file) {
  const name = file.name
  let parsed

  if (TEXT_EXT.test(name)) {
    const raw = await file.text()
    parsed = fromRows(parseDelimited(raw, sniffDelimiter(raw)))
    parsed.sheet = null
  } else {
    /*
     * 엑셀만 라이브러리를 쓴다. 그마저 여기서 처음 부를 때 받아 온다 — 파일을 한 번도
     * 붙이지 않는 사람에게까지 받게 할 이유가 없다. Vite가 별도 청크로 떼어 낸다.
     */
    const mod = await import('xlsx')
    // CJS 번들이라 상호운용 방식에 따라 네임스페이스가 한 겹 더 씌워진다
    const XLSX = mod.read ? mod : mod.default
    // type:'array'는 Uint8Array를 기대한다. ArrayBuffer를 그대로 넘기면 ZIP으로 알아보지
    // 못하고 HTML 표로 읽으려다 "could not find <table>"로 죽는다.
    const book = XLSX.read(new Uint8Array(await file.arrayBuffer()), { type: 'array' })
    const sheet = book.SheetNames[0]
    if (!sheet) throw new Error('시트가 없는 파일입니다.')
    const rows = XLSX.utils.sheet_to_json(book.Sheets[sheet], { header: 1, blankrows: false, raw: false })
    parsed = fromRows(rows)
    parsed.sheet = sheet
    parsed.sheets = book.SheetNames.length
  }

  let body = parsed.lines.join('\n')
  let cut = parsed.kept < parsed.rows
  if (body.length > MAX_CHARS) {
    body = body.slice(0, MAX_CHARS)
    cut = true
  }

  return {
    text: body,
    meta: {
      name,
      sheet: parsed.sheet,
      sheets: parsed.sheets ?? 1,
      rows: parsed.rows,
      kept: parsed.kept,
      cols: parsed.cols,
      truncated: cut,
    },
  }
}

/** 입력창에 넣을 머리말. 무엇을 붙였는지 본문에도 남는다 */
export function headerLine(meta) {
  const where = meta.sheet ? ` · 시트 "${meta.sheet}"` : ''
  const size = `${meta.rows}행 × ${meta.cols}열`
  const cut = meta.truncated ? ` · 앞 ${meta.kept}행만 첨부` : ''
  return `[표 첨부] ${meta.name}${where} · ${size}${cut}`
}
