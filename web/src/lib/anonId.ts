const ANON_ID_KEY = 'ad_anonymous_id';

/** 匿名访客稳定标识：用于广告频控与点击去重（后端 userId 为 null 时匿名请求会绕过频控、
 *  去重 key 也会退化为共享 IP），因此前端保证始终携带一个稳定 userId。 */
export function getAnonymousId(): string {
  if (typeof window === 'undefined') return '';
  let id = localStorage.getItem(ANON_ID_KEY);
  if (!id) {
    id =
      typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID()
        : `anon-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    localStorage.setItem(ANON_ID_KEY, id);
  }
  return id;
}
