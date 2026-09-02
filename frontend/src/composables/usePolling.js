import { onUnmounted, ref } from 'vue'

/**
 * 폴링 상한. 서버 설정 `gateway.polling.max-attempts`(기본 30)와 같은 값이며
 * 응답에 실려 오지 않으므로 FE 상수로 둔다 (계약서 §5-4).
 * 간격은 상수로 두지 않는다 — 202 응답의 `pollAfterMs`를 쓴다.
 */
export const POLL_MAX_ATTEMPTS = 30

/**
 * 202 폴링 (기획서 5.3 / D12).
 *
 * 세 가지를 반드시 지킨다.
 *  1. 종료 — `aiStatus`가 PENDING이 아니면 즉시 중단 (판단은 호출자의 `isDone`)
 *  2. 상한 — maxAttempts 초과 시 중단하고 "검토가 지연되고 있습니다"를 표시
 *  3. 정리 — onUnmounted에서 타이머를 clear. 안 하면 감사 콘솔로 옮긴 뒤에도 요청이 계속 나간다
 *
 * 사람의 확정(ACCEPT/REJECT)은 여기서 따라가지 않는다. 확정 시점을 예측할 수 없어
 * 무한 폴링이 되기 때문이며, 화면 재조회로 반영한다 (D12).
 */
export function usePolling() {
  const attempts = ref(0)
  const elapsedSec = ref(0)
  const isPolling = ref(false)
  const exhausted = ref(false)

  let timerId = null
  let tickerId = null
  /** stop() 이후 도착한 응답이 다음 폴링을 예약하지 못하게 막는 토큰 */
  let runId = 0

  function clearTimers() {
    if (timerId !== null) {
      window.clearTimeout(timerId)
      timerId = null
    }
    if (tickerId !== null) {
      window.clearInterval(tickerId)
      tickerId = null
    }
  }

  function stop() {
    runId += 1
    clearTimers()
    isPolling.value = false
  }

  /**
   * @param {object} options
   * @param {number} options.intervalMs   202 응답의 pollAfterMs
   * @param {() => Promise<any>} options.poll        GET /inspections/{id}
   * @param {(data: any) => boolean} options.isDone  종료 조건 (aiStatus !== 'PENDING')
   * @param {(data: any) => void} [options.onTick]   매 회차 응답
   * @param {(data: any) => void} [options.onDone]   종료 시 마지막 응답
   * @param {() => void} [options.onExhausted]       상한 초과
   * @param {(err: any) => void} [options.onError]   요청 실패
   * @param {number} [options.maxAttempts]
   */
  function start({
    intervalMs,
    poll,
    isDone,
    onTick,
    onDone,
    onExhausted,
    onError,
    maxAttempts = POLL_MAX_ATTEMPTS,
  }) {
    stop()
    const myRun = runId
    attempts.value = 0
    elapsedSec.value = 0
    exhausted.value = false
    isPolling.value = true

    const startedAt = Date.now()
    tickerId = window.setInterval(() => {
      elapsedSec.value = Math.floor((Date.now() - startedAt) / 1000)
    }, 1000)

    async function tick() {
      if (myRun !== runId) return
      attempts.value += 1
      let data
      try {
        data = await poll()
      } catch (err) {
        if (myRun !== runId) return
        stop()
        onError?.(err)
        return
      }
      if (myRun !== runId) return

      onTick?.(data)

      if (isDone(data)) {
        stop()
        onDone?.(data)
        return
      }
      if (attempts.value >= maxAttempts) {
        stop()
        exhausted.value = true
        onExhausted?.()
        return
      }
      timerId = window.setTimeout(tick, intervalMs)
    }

    timerId = window.setTimeout(tick, intervalMs)
  }

  onUnmounted(stop)

  return { attempts, elapsedSec, isPolling, exhausted, start, stop }
}
