"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Navbar from "@/components/Navbar";
import ApiNotice from "@/components/ApiNotice";
import { useAppContext } from "@/context/AppContext";
import { waitFor } from "@/lib/api";

const delays = [500, 1000, 1500, 2000, 3000, 5000];
export default function ReservationPage() {
  const { id } = useParams(); const { request, router } = useAppContext();
  const [reservation, setReservation] = useState(null); const [message, setMessage] = useState("Confirming your reserved item…"); const [error, setError] = useState(null);
  useEffect(() => { let cancelled = false; const load = async () => { try { for (const delay of delays) { const result = await request(`/api/v1/flash-sales/reservations/${id}`); if (cancelled) return; setReservation(result.data); if (["RELEASED", "EXPIRED"].includes(result.data.status)) { setMessage("This reservation is no longer available."); return; } const orders = await request("/api/v1/orders?page=0&size=20"); for (const order of orders.data?.data || []) { const detail = await request(`/api/v1/orders/${order.id}`); if (detail.data.reservationId === id) { router.replace(`/orders/${order.id}`); return; } } await waitFor(delay); } setMessage("Your order is still being created. You can check its status in My Orders."); } catch (caught) { if (!cancelled) setError(caught); } }; load(); return () => { cancelled = true; }; }, [id]);
  return <><Navbar /><main className="mx-auto min-h-[70vh] max-w-xl px-6 py-20 text-center"><div className="rounded-lg bg-gray-50 p-8"><p className="text-sm text-orange-600">Reservation status</p><h1 className="mt-2 text-2xl font-medium">{reservation?.status || "Processing"}</h1><p className="mt-4 text-gray-600">{message}</p>{reservation?.expiresAt && <p className="mt-3 text-sm text-gray-500">Reserved until {new Date(reservation.expiresAt).toLocaleString()}</p>}<ApiNotice error={error} className="mt-5 text-left" /><button onClick={() => router.push("/my-orders")} className="mt-6 rounded bg-orange-600 px-5 py-2.5 text-white">View my orders</button></div></main></>;
}
