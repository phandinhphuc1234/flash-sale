// The repository's local Docker Gateway is published on port 18080. Deployments
// should still provide NEXT_PUBLIC_API_BASE_URL explicitly (for example, the
// HTTPS cloud edge); this fallback keeps a fresh local checkout usable without
// requiring a hand-created .env.local file.
const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:18080").replace(/\/$/, "");

export class ApiError extends Error {
  constructor(status, body = {}) {
    super(body.message || "The request could not be completed.");
    this.name = "ApiError";
    this.status = status;
    this.code = body.errorCode;
    this.errors = body.errors || [];
  }
}

export async function apiFetch(path, init = {}, accessToken) {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  headers.set("X-Trace-Id", crypto.randomUUID());
  if (init.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers, credentials: "include" });
  if (response.status === 204) return undefined;
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new ApiError(response.status, body);
  return body;
}

export const waitFor = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));
export const formatVnd = (amount) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(Number(amount || 0));
export const createIdempotencyKey = () => crypto.randomUUID();
export { buildCatalogProductsPath } from "./catalogQuery.mjs";
