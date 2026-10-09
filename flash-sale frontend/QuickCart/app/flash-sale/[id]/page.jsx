"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import Navbar from "@/components/Navbar";
import Footer from "@/components/Footer";
import ApiNotice from "@/components/ApiNotice";
import Loading from "@/components/Loading";
import { useAppContext } from "@/context/AppContext";
import { createIdempotencyKey, formatVnd } from "@/lib/api";
import { normalizePublicCampaign, reconcileCampaignPhase } from "@/lib/publicCampaigns.mjs";

export default function FlashSaleDetailPage() {
  const { id } = useParams();
  const { request, router, userData, authReady } = useAppContext();
  const [campaign, setCampaign] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [reserving, setReserving] = useState(false);
  const [now, setNow] = useState(() => new Date());
  const idempotencyKey = useRef(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const response = await request(`/api/v1/campaigns/${id}`);
      const normalized = normalizePublicCampaign(response?.data);
      if (!normalized) throw new Error("Campaign details are temporarily unavailable.");
      setCampaign(normalized);
      setQuantity((current) => Math.min(current, normalized.purchaseLimitPerUser));
    } catch (caught) {
      setError(caught);
    } finally {
      setLoading(false);
    }
  }, [id, request]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000);
    const refresh = window.setInterval(load, 15000);
    return () => { window.clearInterval(timer); window.clearInterval(refresh); };
  }, [load]);

  const reserve = async () => {
    if (!authReady) return;
    if (!userData) {
      router.push(`/login?returnTo=${encodeURIComponent(`/flash-sale/${id}`)}`);
      return;
    }
    const key = idempotencyKey.current || createIdempotencyKey();
    idempotencyKey.current = key;
    setError(null);
    setReserving(true);
    try {
      const response = await request(`/api/v1/flash-sales/${id}/reservations`, {
        method: "POST",
        headers: { "Idempotency-Key": key },
        body: JSON.stringify({ variantId: campaign.variantId, quantity }),
      });
      idempotencyKey.current = null;
      sessionStorage.setItem("flash-sale-purchase", JSON.stringify(response.data));
      router.push(`/reservations/${response.data.reservationId}`);
    } catch (caught) {
      if (caught?.status >= 400 && caught?.status < 500) idempotencyKey.current = null;
      setError(caught);
    } finally {
      setReserving(false);
    }
  };

  if (loading && !campaign) return <Loading />;
  if (!campaign) return <><Navbar /><main className="mx-auto min-h-[65vh] max-w-3xl px-6 py-16"><ApiNotice error={error} /><button type="button" onClick={() => router.push("/flash-sale")} className="mt-5 rounded-xl bg-slate-900 px-5 py-3 text-white">Back to Flash Sale</button></main><Footer /></>;

  const phase = reconcileCampaignPhase(campaign, now);
  const canReserve = phase === "LIVE" && campaign.reservable && campaign.presentationAvailable;

  return (
    <>
      <Navbar />
      <main className="min-h-[70vh] bg-slate-50 px-6 py-10 md:px-16 lg:px-32">
        <div className="mx-auto grid max-w-6xl gap-8 lg:grid-cols-[1.05fr_0.95fr]">
          <section className="flex min-h-96 flex-col justify-between overflow-hidden rounded-3xl bg-gradient-to-br from-slate-950 via-slate-900 to-orange-950 p-8 text-white md:p-10">
            <div><p className="text-xs font-semibold uppercase tracking-[0.22em] text-orange-400">{phase} Flash Sale</p><h1 className="mt-4 text-4xl font-semibold md:text-5xl">{campaign.name}</h1><p className="mt-5 text-slate-300">A limited offer backed by the existing reservation and Order workflow.</p></div>
            <div className="mt-10"><p className="text-sm text-slate-400">{phase === "UPCOMING" ? "Starts" : phase === "LIVE" ? "Ends" : "Ended"}</p><time className="mt-1 block text-xl font-medium" dateTime={phase === "UPCOMING" ? campaign.startAt : campaign.endAt}>{new Date(phase === "UPCOMING" ? campaign.startAt : campaign.endAt).toLocaleString()}</time></div>
          </section>

          <section className="rounded-3xl border border-slate-200 bg-white p-7 shadow-sm md:p-9">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Campaign offer</p>
            <h2 className="mt-3 text-2xl font-semibold text-slate-900">{campaign.presentationAvailable ? campaign.variantSku : "Offer presentation unavailable"}</h2>
            {campaign.presentationAvailable ? <div className="mt-6 flex items-baseline gap-3"><span className="text-4xl font-semibold text-orange-600">{formatVnd(campaign.campaignPrice)}</span><span className="text-slate-400 line-through">{formatVnd(campaign.basePrice)}</span></div> : <p className="mt-4 text-slate-500">We cannot safely display this offer yet. No product name, price, or stock has been guessed.</p>}
            <p className="mt-6 rounded-2xl bg-orange-50 px-4 py-3 text-sm text-orange-900">Limit {campaign.purchaseLimitPerUser} per shopper. Exact remaining quantity is intentionally not shown.</p>

            {phase === "LIVE" && campaign.presentationAvailable && <label className="mt-6 block text-sm font-medium text-slate-700">Quantity<input aria-label="Reservation quantity" type="number" min="1" max={campaign.purchaseLimitPerUser} value={quantity} onChange={(event) => setQuantity(Math.max(1, Math.min(campaign.purchaseLimitPerUser, Number(event.target.value) || 1)))} className="mt-2 w-full rounded-xl border border-slate-200 p-3 outline-none focus:border-orange-400 focus:ring-2 focus:ring-orange-100" /></label>}
            <ApiNotice error={error} className="mt-5" />

            <button type="button" disabled={!authReady || reserving || !canReserve} onClick={reserve} className="mt-6 w-full rounded-xl bg-orange-600 px-5 py-3.5 font-semibold text-white transition hover:bg-orange-700 disabled:cursor-not-allowed disabled:bg-slate-300">
              {reserving ? "Submitting reservation…" : phase === "UPCOMING" ? "Offer has not started" : phase === "ENDED" ? "Offer has ended" : !campaign.presentationAvailable ? "Offer unavailable" : userData ? "Reserve now" : "Sign in to reserve"}
            </button>
            <p className="mt-3 text-xs leading-5 text-slate-500">Submitting does not prove success. The reservation page confirms the authoritative result.</p>
          </section>
        </div>
      </main>
      <Footer />
    </>
  );
}
