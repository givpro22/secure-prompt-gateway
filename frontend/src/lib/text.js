/** 알림 한 줄에 들어갈 만큼만 자른다 */
export function shortTitle(text) {
  const one = (text ?? '').replace(/\s+/g, ' ').trim()
  return one.length > 20 ? `${one.slice(0, 20)}…` : one
}
