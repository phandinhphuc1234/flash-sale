"use client";

import { useEffect, useState } from "react";
import Navbar from "@/components/Navbar";
import ApiNotice from "@/components/ApiNotice";
import { useAppContext } from "@/context/AppContext";
import { waitFor } from "@/lib/api";

const PAYMENT_DELAYS = [500, 1000, 1500, 2000, 3000, 5000, 5000, 5000];

export default function PaymentSuccessPage() {
  const { request, router } = useAppContext();
  const [status, setStatus] = useState("Verifying your payment…");
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    const verify = async () => {
      const saved = sessionStorage.getItem("flash-sale-payment");
      if (!saved) {
        setStatus("Payment returned successfully. Open My Orders to verify the final status.");
        return;
      }

      let paymentContext;
      try {
        paymentContext = JSON.parse(saved);
      } catch {
        sessionStorage.removeItem("flash-sale-payment");
        setStatus("We could not read the payment context. Open My Orders to verify the order.");
        return;
      }

      const { paymentId, orderId, reservationId } = paymentContext;
      if (!paymentId || !orderId) {
        setStatus("Payment returned successfully. Open My Orders to verify the final status.");
        return;
      }

      try {
        for (const delay of PAYMENT_DELAYS) {
          const [payment, order] = await Promise.all([
            request(`/api/v1/payments/${paymentId}`),
            request(`/api/v1/orders/${orderId}`),
          ]);

          let reservation = null;
          // Regular Cart/Buy Now orders have no reservation. Only a Flash Sale
          // order participates in the reservation confirmation boundary.
          if (reservationId) {
            const reservationResponse = await request(`/api/v1/flash-sales/reservations/${reservationId}`);
            reservation = reservationResponse.data;
          }

          if (cancelled) return;
          const reservationStatus = reservation ? ` · Reservation: ${reservation.status}` : "";
          setStatus(`Payment: ${payment.data.status} · Order: ${order.data.status}${reservationStatus}`);

          const paymentDone = payment.data.status === "SUCCEEDED";
          const orderDone = order.data.status === "CONFIRMED";
          const reservationDone = !reservation || reservation.status === "CONFIRMED";
          if (paymentDone && orderDone && reservationDone) {
            sessionStorage.removeItem("flash-sale-payment");
            return;
          }
          await waitFor(delay);
        }

        setStatus("Your payment is still being finalized. Open the order and refresh its status shortly.");
      } catch (caught) {
        if (!cancelled) setError(caught);
      }
    };

    verify();
    return () => { cancelled = true; };
  }, [request]);

  return <>
    <Navbar />
    <main className="mx-auto min-h-[70vh] max-w-xl px-6 py-20 text-center">
      <div className="rounded-2xl border bg-gray-50 p-8 shadow-sm">
        <p className="text-sm text-orange-600">Payment result</p>
        <h1 className="mt-2 text-2xl font-medium">We are confirming your order</h1>
        <p className="mt-4 text-gray-600">{status}</p>
        <ApiNotice error={error} className="mt-5 text-left" />
        <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:justify-center">
          <button onClick={() => router.push("/my-orders")} className="rounded-xl bg-orange-600 px-5 py-2.5 font-medium text-white">View my orders</button>
          <button onClick={() => window.location.reload()} className="rounded-xl border px-5 py-2.5">Check again</button>
        </div>
      </div>
    </main>
  </>;
}
