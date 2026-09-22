"use client";

import { useCallback, useEffect, useState } from "react";
import Navbar from "@/components/Navbar";
import Footer from "@/components/Footer";
import Loading from "@/components/Loading";
import ApiNotice from "@/components/ApiNotice";
import { formatVnd } from "@/lib/api";
import { useAppContext } from "@/context/AppContext";

export default function MyOrders() {
  const { request, router, userData, authReady } = useAppContext();
  const [orders, setOrders] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await request("/api/v1/orders?page=0&size=20");
      setOrders(response.data?.data || []);
    } catch (caught) {
      setError(caught);
    } finally {
      setLoading(false);
    }
  }, [request]);

  useEffect(() => {
    if (!authReady) return;
    if (!userData) {
      router.replace("/login");
      return;
    }
    load();
  }, [authReady, load, router, userData]);

  return <>
    <Navbar />
    <main className="min-h-screen px-6 py-10 md:px-16 lg:px-32">
      <div className="flex items-center justify-between gap-4">
        <div>
          <p className="text-xs font-medium uppercase tracking-[0.18em] text-orange-600">Purchase history</p>
          <h1 className="mt-2 text-2xl font-medium">My orders</h1>
        </div>
        <button onClick={load} disabled={loading || !userData} className="rounded-xl border px-4 py-2 text-sm disabled:opacity-50">{loading ? "Refreshing…" : "Refresh"}</button>
      </div>
      <ApiNotice error={error} className="mt-5" />
      {loading ? <Loading /> : <div className="mt-6 divide-y rounded-2xl border bg-white shadow-sm">
        {orders.length ? orders.map((order) => <button key={order.id} onClick={() => router.push(`/orders/${order.id}`)} className="flex w-full items-center justify-between gap-4 p-5 text-left transition hover:bg-orange-50/40">
          <div className="min-w-0"><p className="font-medium">{order.orderNumber}</p><p className="mt-1 text-sm text-gray-500">{order.purchaseSource === "CART" ? "Cart checkout" : order.purchaseSource === "BUY_NOW" ? "Buy now" : "Flash Sale"} · {new Date(order.createdAt).toLocaleString()}</p></div>
          <div className="shrink-0 text-right"><p>{formatVnd(order.totalAmount)}</p><p className="mt-1 text-sm text-orange-600">{order.status}</p></div>
        </button>) : <p className="p-8 text-center text-gray-500">You have no orders yet.</p>}
      </div>}
    </main>
    <Footer />
  </>;
}
