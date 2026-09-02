import client from './client'

/*
 * 마스터 조회 3종 (계약서 §1-1 ~ §1-3).
 * 세 응답 모두 목록 봉투 `{ items, page, size, total }`다 (C1). 배열이 아니다.
 */

export async function fetchDepartments() {
  const { data } = await client.get('/departments')
  return data
}

export async function fetchUsers(deptId) {
  const params = deptId === null || deptId === undefined ? {} : { deptId }
  const { data } = await client.get('/users', { params })
  return data
}

/** deptId는 필수다 (C6). 누락하면 서버가 400 INVALID_PARAMETER를 반환한다. */
export async function fetchPolicies(deptId) {
  const { data } = await client.get('/policies', { params: { deptId } })
  return data
}
