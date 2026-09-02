import client from './client'

/** GET /inspections/{id} — 폴링과 감사 상세 패널이 함께 쓴다 (계약서 §1-5). */
export async function fetchInspection(inspectionId) {
  const { data } = await client.get(`/inspections/${inspectionId}`)
  return data
}

/**
 * GET /inspections — 감사 목록 (계약서 §1-6).
 * 응답은 목록 봉투다. `res.data.filter(...)`를 호출하면 죽는다.
 */
export async function fetchInspections(params) {
  const { data } = await client.get('/inspections', { params })
  return data
}

/**
 * PATCH /inspections/{id}/findings/{findingId} — AI 후보 확정 (계약서 §1-7).
 * reviewStatus는 ACCEPTED / REJECTED만 허용된다. 응답에 재산출된 inspection 상태가 함께 온다.
 */
export async function reviewFinding(inspectionId, findingId, reviewStatus, comment) {
  const body = { reviewStatus }
  if (comment) body.comment = comment
  const { data } = await client.patch(
    `/inspections/${inspectionId}/findings/${findingId}`,
    body,
  )
  return data
}
