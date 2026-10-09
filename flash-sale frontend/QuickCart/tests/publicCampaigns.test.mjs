import test from "node:test";
import assert from "node:assert/strict";
import {
  normalizePublicCampaign,
  normalizePublicCampaignPage,
  reconcileCampaignPhase,
  safeReturnPath,
} from "../lib/publicCampaigns.mjs";

const campaign = {
  id: "campaign-1",
  name: "Weekend phone sale",
  phase: "UPCOMING",
  startAt: "2026-10-09T10:00:00Z",
  endAt: "2026-10-09T11:00:00Z",
  purchaseLimitPerUser: 2,
  presentationAvailable: true,
  reservable: false,
};

test("normalizes a bounded public campaign page", () => {
  const result = normalizePublicCampaignPage({ data: [campaign, null], page: { number: 1, size: 12, totalElements: 13, totalPages: 2, hasNext: false } });
  assert.equal(result.data.length, 1);
  assert.equal(result.page.number, 1);
  assert.equal(result.page.totalElements, 13);
});

test("rejects malformed campaign payloads", () => {
  assert.equal(normalizePublicCampaign({ ...campaign, phase: "DRAFT" }), null);
  assert.equal(normalizePublicCampaign({ ...campaign, startAt: "invalid" }), null);
});

test("reconciles lifecycle boundaries using authoritative timestamps", () => {
  assert.equal(reconcileCampaignPhase(campaign, "2026-10-09T09:59:59Z"), "UPCOMING");
  assert.equal(reconcileCampaignPhase(campaign, "2026-10-09T10:00:00Z"), "LIVE");
  assert.equal(reconcileCampaignPhase(campaign, "2026-10-09T11:00:00Z"), "ENDED");
});

test("accepts only local return paths", () => {
  assert.equal(safeReturnPath("/flash-sale/campaign-1"), "/flash-sale/campaign-1");
  assert.equal(safeReturnPath("https://evil.example"), "/");
  assert.equal(safeReturnPath("//evil.example"), "/");
});
