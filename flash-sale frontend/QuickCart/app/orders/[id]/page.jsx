"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import Navbar from "@/components/Navbar";
import ApiNotice from "@/components/ApiNotice";
import { createIdempotencyKey, formatVnd, waitFor } from "@/lib/api";
import { canStartPayment, displayOrderItem, getOrderSourceLabel, getStatusCopy } from "@/lib/purchasePresentation.mjs";
import { useAppContext } from "@/context/AppContext";

const PAYMENT_LOOKUP_DELAYS = [0, 500, 1000, 2000, 3000];

export default function OrderDetailPage() {
  const { id } = useParams();
  const { request, router } = useAppContext();
  const [order, setOrder] = useState(null);
  const [payment, setPayment] = useState(null);
  const [error, setError] = useState(null);
  const [pending, setPending] = useState(false);
  const [loading, setLoading] = useState(true);
  const [paymentMessage, setPaymentMessage] = useState("");
  const checkoutKeyRef = useRef(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await request(`/api/v1/orders/${id}`);
      setOrder(result.data);
      setPayment(null);

      if (["PENDING_PAYMENT", "CONFIRMED"].includes(result.data.status)) {
        for (const delay of PAYMENT_LOOKUP_DELAYS) {
          if (delay) await waitFor(delay);
          try {
            const paymentResult = await request(`/api/v1/payments/by-order/${id}`);
            setPayment(paymentResult.data);
            break;
          } catch (paymentError) {
            if (paymentError?.status !== 404) throw paymentError;
            // Payment is created asynchronously after Order acceptance. Poll
            // for a bounded period instead of showing a permanent empty state.
          }
        }
      }
    } catch (caught) {
      setError(caught);
    } finally {
      setLoading(false);
    }
  }, [id, request]);

  useEffect(() => { load(); }, [load]);

  const checkout = async () => {
    if (!payment) {
      setPaymentMessage("Payment is still being prepared. Refresh this order in a moment.");
      return;
    }

    const idempotencyKey = checkoutKeyRef.current || createIdempotencyKey();
    checkoutKeyRef.current = idempotencyKey;
    setPending(true);
    setError(null);
    setPaymentMessage("");
    try {
      const result = await request(`/api/v1/payments/${payment.id}/checkout-sessions`, {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey },
      });
      if (!result.data?.checkoutUrl) {
        setPaymentMessage("Checkout is being prepared. Keep this page open and try again shortly.");
        return;
      }

      sessionStorage.setItem("flash-sale-payment", JSON.stringify({
        paymentId: payment.id,
        orderId: id,
        reservationId: order?.reservationId || null,
      }));
      checkoutKeyRef.current = null;
      window.location.assign(result.data.checkoutUrl);
    } catch (caught) {
      if (caught?.status >= 400 && caught?.status < 500) checkoutKeyRef.current = null;
      setError(caught);
    } finally {
      setPending(false);
    }
  };

  return <>
    <Navbar />
    <main className="mx-auto min-h-[70vh] max-w-3xl px-6 py-12">
      <div className="flex items-start justify-between gap-4">
        <div>
          <button onClick={() => router.push("/my-orders")} className="text-sm text-orange-600">← My orders</button>
          <p className="mt-5 text-sm text-gray-500">Order</p>
          <h1 className="text-2xl font-medium">{order?.orderNumber || (loading ? "Loading…" : "Order unavailable")}</h1>
          {order && <p className="mt-1 text-sm text-gray-500">{getOrderSourceLabel(order.purchaseSource)}</p>}
        </div>
        {order && <div className="text-right"><span className="rounded-full bg-orange-50 px-3 py-1 text-sm text-orange-700">{getStatusCopy("order", order.status).label}</span><p className="mt-2 text-xs text-gray-500">{getStatusCopy("order", order.status).guidance}</p></div>}
      </div>

      <ApiNotice error={error} className="mt-5" />
      {loading && !order && <div className="mt-8 animate-pulse rounded-2xl border bg-white p-8 text-gray-400">Loading order details…</div>}
      {order && <div className="mt-8 rounded-2xl border bg-white p-5 shadow-sm">
        <div className="space-y-3">
          {order.items.map((item) => { const display = displayOrderItem(item); return <div key={item.variantId} className="flex justify-between gap-4"><div><p>{item.quantity} × {display.primary}</p><p className="mt-1 text-xs text-gray-500">{display.secondary}</p></div><span>{formatVnd(item.lineAmount)}</span></div>; })}
        </div>
        <div className="mt-5 flex justify-between border-t pt-4 text-lg font-medium"><span>Total</span><span>{formatVnd(order.totalAmount)}</span></div>
        {payment?.paymentDeadline && <p className="mt-3 text-xs text-gray-500">Payment deadline: {new Date(payment.paymentDeadline).toLocaleString()}</p>}
        {payment ? <div className="mt-5 rounded-xl bg-gray-50 p-4 text-sm"><div className="flex justify-between"><span>Payment</span><span className="font-medium">{getStatusCopy("payment", payment.status).label}</span></div><p className="mt-2 text-gray-600">{getStatusCopy("payment", payment.status).guidance}</p>{payment.failureReason && <p className="mt-2 text-red-700">{payment.failureReason}</p>}</div> : ["PENDING_PAYMENT", "CONFIRMED"].includes(order.status) && <p className="mt-5 rounded-xl bg-orange-50 p-4 text-sm text-orange-800">Payment is being prepared asynchronously. Refresh in a moment.</p>}
        {payment && canStartPayment(payment.status) && <button disabled={pending} onClick={checkout} className="mt-6 w-full rounded-xl bg-orange-600 py-3 font-medium text-white disabled:opacity-60">{pending ? "Opening secure checkout…" : "Pay securely with Stripe"}</button>}
        {paymentMessage && <p className="mt-3 rounded bg-orange-50 p-3 text-sm text-orange-800">{paymentMessage}</p>}
        <button onClick={load} disabled={loading} className="mt-3 w-full rounded-xl border py-2.5 disabled:opacity-50">{loading ? "Refreshing…" : "Refresh status"}</button>
      </div>}
    </main>
  </>;
}
