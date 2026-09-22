/** Presentation-only JWT claim helpers. The API remains authoritative. */
export function readAccessTokenClaims(token) {
  if (!token || typeof window === "undefined") return {};
  try {
    const payload = token.split(".")[1];
    if (!payload) return {};
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(normalized.length + ((4 - normalized.length % 4) % 4), "=");
    return JSON.parse(window.atob(padded));
  } catch {
    return {};
  }
}

export function getAuthorities(token) {
  const claims = readAccessTokenClaims(token);
  const authorities = claims.authorities || claims.roles || [];
  return Array.isArray(authorities) ? authorities : String(authorities).split(" ").filter(Boolean);
}

export function isAdminToken(token) {
  return getAuthorities(token).includes("ROLE_ADMIN");
}
