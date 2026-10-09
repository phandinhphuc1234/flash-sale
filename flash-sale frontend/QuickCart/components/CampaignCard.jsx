"use client";

import Link from "next/link";
import { formatVnd } from "@/lib/api";
import { reconcileCampaignPhase } from "@/lib/publicCampaigns.mjs";

const phaseStyle = {
  LIVE: "bg-emerald-100 text-emerald-700",
  UPCOMING: "bg-amber-100 text-amber-700",
  ENDED: "bg-slate-100 text-slate-600",
};

export default function CampaignCard({ campaign, now }) {
  const phase = reconcileCampaignPhase(campaign, now);
  const timestamp = phase === "UPCOMING" ? campaign.startAt : campaign.endAt;

  return (
    <article className="group flex h-full flex-col rounded-3xl border border-slate-200 bg-white p-6 shadow-sm transition duration-200 hover:-translate-y-1 hover:border-orange-200 hover:shadow-lg motion-reduce:transition-none">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-orange-600">Limited offer</p>
          <h2 className="mt-2 text-xl font-semibold text-slate-900">{campaign.name}</h2>
        </div>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${phaseStyle[phase]}`}>{phase}</span>
      </div>

      <div className="mt-6 rounded-2xl bg-slate-50 p-4">
        <p className="text-xs uppercase tracking-wide text-slate-500">Variant</p>
        <p className="mt-1 font-medium text-slate-800">
          {campaign.presentationAvailable ? campaign.variantSku : "Offer details temporarily unavailable"}
        </p>
        {campaign.presentationAvailable && (
          <div className="mt-4 flex flex-wrap items-baseline gap-2">
            <span className="text-2xl font-semibold text-orange-600">{formatVnd(campaign.campaignPrice)}</span>
            <span className="text-sm text-slate-400 line-through">{formatVnd(campaign.basePrice)}</span>
          </div>
        )}
      </div>

      <p className="mt-5 text-sm text-slate-500">
        {phase === "UPCOMING" ? "Starts" : phase === "LIVE" ? "Ends" : "Ended"}{" "}
        <time dateTime={timestamp}>{new Date(timestamp).toLocaleString()}</time>
      </p>
      <p className="mt-1 text-sm text-slate-500">Limit {campaign.purchaseLimitPerUser} per shopper</p>

      <Link href={`/flash-sale/${campaign.id}`} className="mt-6 inline-flex items-center justify-center rounded-xl bg-slate-900 px-5 py-3 font-medium text-white transition hover:bg-orange-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-orange-500 focus-visible:ring-offset-2">
        {phase === "LIVE" ? "View live offer" : phase === "UPCOMING" ? "View upcoming offer" : "View offer"}
      </Link>
    </article>
  );
}
