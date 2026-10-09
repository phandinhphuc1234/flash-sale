const PHASES = new Set(["LIVE", "UPCOMING", "ENDED"]);

export function normalizePublicCampaign(value) {
  if (!value || typeof value !== "object") return null;
  if (!value.id || !value.name || !PHASES.has(value.phase)) return null;

  const startAt = new Date(value.startAt);
  const endAt = new Date(value.endAt);
  if (Number.isNaN(startAt.getTime()) || Number.isNaN(endAt.getTime())) return null;

  return {
    ...value,
    startAt: startAt.toISOString(),
    endAt: endAt.toISOString(),
    purchaseLimitPerUser: Math.max(1, Number(value.purchaseLimitPerUser || 1)),
    presentationAvailable: value.presentationAvailable === true,
    reservable: value.reservable === true,
  };
}

export function normalizePublicCampaignPage(payload) {
  const data = Array.isArray(payload?.data)
    ? payload.data.map(normalizePublicCampaign).filter(Boolean)
    : [];
  return {
    data,
    page: {
      number: Math.max(0, Number(payload?.page?.number || 0)),
      size: Math.max(1, Number(payload?.page?.size || 12)),
      totalElements: Math.max(0, Number(payload?.page?.totalElements || 0)),
      totalPages: Math.max(0, Number(payload?.page?.totalPages || 0)),
      hasNext: payload?.page?.hasNext === true,
    },
  };
}

export function reconcileCampaignPhase(campaign, now = new Date()) {
  if (!campaign) return "ENDED";
  const current = now instanceof Date ? now.getTime() : new Date(now).getTime();
  const starts = new Date(campaign.startAt).getTime();
  const ends = new Date(campaign.endAt).getTime();
  if (campaign.phase === "ENDED" || current >= ends) return "ENDED";
  if (current >= starts) return "LIVE";
  return "UPCOMING";
}

export function safeReturnPath(value, fallback = "/") {
  if (typeof value !== "string" || !value.startsWith("/") || value.startsWith("//")) return fallback;
  return value;
}
