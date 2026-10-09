"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Navbar from "@/components/Navbar";
import Footer from "@/components/Footer";
import CampaignCard from "@/components/CampaignCard";
import ApiNotice from "@/components/ApiNotice";
import { useAppContext } from "@/context/AppContext";
import { normalizePublicCampaignPage, reconcileCampaignPhase } from "@/lib/publicCampaigns.mjs";

const filters = ["ALL", "LIVE", "UPCOMING"];

export default function FlashSalePage() {
  const { request, router } = useAppContext();
  const [filter, setFilter] = useState("ALL");
  const [page, setPage] = useState(0);
  const [campaignPage, setCampaignPage] = useState({ data: [], page: { number: 0, totalPages: 0, hasNext: false } });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [now, setNow] = useState(() => new Date());

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await request(`/api/v1/campaigns?phase=${filter}&page=${page}&size=12`);
      setCampaignPage(normalizePublicCampaignPage(response?.data));
    } catch (caught) {
      setError(caught);
    } finally {
      setLoading(false);
    }
  }, [filter, page, request]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000);
    const refresh = window.setInterval(load, 30000);
    return () => { window.clearInterval(timer); window.clearInterval(refresh); };
  }, [load]);

  const groups = useMemo(() => ({
    live: campaignPage.data.filter((item) => reconcileCampaignPhase(item, now) === "LIVE"),
    upcoming: campaignPage.data.filter((item) => reconcileCampaignPhase(item, now) === "UPCOMING"),
  }), [campaignPage.data, now]);

  return (
    <>
      <Navbar />
      <main className="min-h-[70vh] bg-slate-50 px-6 py-10 md:px-16 lg:px-32">
        <section className="mx-auto max-w-7xl">
          <div className="overflow-hidden rounded-3xl bg-slate-950 px-7 py-10 text-white md:px-10">
            <p className="text-xs font-semibold uppercase tracking-[0.22em] text-orange-400">Flash Sale</p>
            <h1 className="mt-3 max-w-3xl text-3xl font-semibold md:text-5xl">Limited offers, clear timing, no hidden stock claims.</h1>
            <p className="mt-4 max-w-2xl text-slate-300">Discover live and upcoming campaigns. A reservation is confirmed only after the backend accepts it.</p>
          </div>

          <div className="mt-8 flex flex-wrap gap-2" role="group" aria-label="Campaign lifecycle filter">
            {filters.map((value) => (
              <button key={value} type="button" onClick={() => { setFilter(value); setPage(0); }} aria-pressed={filter === value} className={`rounded-full px-5 py-2 text-sm font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-orange-500 focus-visible:ring-offset-2 ${filter === value ? "bg-orange-600 text-white" : "border border-slate-200 bg-white text-slate-600 hover:border-orange-300"}`}>
                {value === "ALL" ? "All offers" : value === "LIVE" ? "Live now" : "Upcoming"}
              </button>
            ))}
          </div>

          <ApiNotice error={error} className="mt-6" />
          {error && <button type="button" onClick={load} className="mt-3 rounded-xl border border-red-200 bg-white px-4 py-2 text-sm font-medium text-red-700">Try again</button>}

          {loading ? (
            <div className="mt-8 grid gap-5 md:grid-cols-2 xl:grid-cols-3" aria-label="Loading Flash Sale campaigns">
              {Array.from({ length: 3 }).map((_, index) => <div key={index} className="h-80 animate-pulse rounded-3xl bg-slate-200 motion-reduce:animate-none" />)}
            </div>
          ) : !error && campaignPage.data.length === 0 ? (
            <div className="mt-8 rounded-3xl border border-dashed border-slate-300 bg-white px-6 py-14 text-center">
              <h2 className="text-2xl font-semibold text-slate-900">No Flash Sale offers right now</h2>
              <p className="mt-2 text-slate-500">The catalog is still available while the next campaign is prepared.</p>
              <button type="button" onClick={() => router.push("/all-products")} className="mt-6 rounded-xl bg-orange-600 px-5 py-3 font-medium text-white">Browse products</button>
            </div>
          ) : (
            <div className="mt-8 space-y-10">
              {groups.live.length > 0 && <section><h2 className="text-2xl font-semibold text-slate-900">Live now</h2><div className="mt-4 grid gap-5 md:grid-cols-2 xl:grid-cols-3">{groups.live.map((campaign) => <CampaignCard key={campaign.id} campaign={campaign} now={now} />)}</div></section>}
              {groups.upcoming.length > 0 && <section><h2 className="text-2xl font-semibold text-slate-900">Coming soon</h2><div className="mt-4 grid gap-5 md:grid-cols-2 xl:grid-cols-3">{groups.upcoming.map((campaign) => <CampaignCard key={campaign.id} campaign={campaign} now={now} />)}</div></section>}
            </div>
          )}

          {!loading && !error && campaignPage.page.totalPages > 1 && (
            <nav aria-label="Campaign pages" className="mt-10 flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4">
              <button type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)} className="rounded-lg border px-4 py-2 disabled:opacity-40">Previous</button>
              <span className="text-sm text-slate-500">Page {page + 1} of {campaignPage.page.totalPages}</span>
              <button type="button" disabled={!campaignPage.page.hasNext} onClick={() => setPage((value) => value + 1)} className="rounded-lg border px-4 py-2 disabled:opacity-40">Next</button>
            </nav>
          )}
        </section>
      </main>
      <Footer />
    </>
  );
}
