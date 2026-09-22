"use client";
import Navbar from "@/components/Navbar";
import { useAppContext } from "@/context/AppContext";
export default function PaymentCancelPage() { const { router } = useAppContext(); return <><Navbar /><main className="mx-auto min-h-[70vh] max-w-xl px-6 py-20 text-center"><h1 className="text-2xl font-medium">Checkout was cancelled</h1><p className="mt-3 text-gray-600">Your reservation may still be valid until its expiry time. Return to your order to retry payment.</p><button onClick={() => router.push("/my-orders")} className="mt-6 rounded bg-orange-600 px-5 py-2.5 text-white">View my orders</button></main></>; }
