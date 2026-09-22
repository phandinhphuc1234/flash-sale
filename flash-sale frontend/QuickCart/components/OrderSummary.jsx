"use client";

import { useAppContext } from "@/context/AppContext";
import { formatVnd } from "@/lib/api";

export default function OrderSummary() {
  const { cart, getCartAmount, router } = useAppContext();
  const eligibleItems = cart.items.filter((item) => item.detailsAvailable && item.sellable === true);

  return <aside className="h-fit w-full rounded-2xl border border-gray-200 bg-white p-6 shadow-sm md:w-96">
    <h2 className="text-xl font-medium text-gray-800">Cart summary</h2>
    <div className="mt-5 space-y-3 text-sm">
      <div className="flex justify-between text-gray-600"><span>Total quantity</span><span>{cart.totalQuantity}</span></div>
      <div className="flex justify-between text-gray-600"><span>Eligible variants</span><span>{eligibleItems.length}</span></div>
      <div className="flex justify-between border-t pt-4 text-lg font-medium"><span>Estimated subtotal</span><span>{formatVnd(getCartAmount())}</span></div>
    </div>
    <p className="mt-5 rounded-xl bg-orange-50 p-4 text-xs leading-5 text-orange-800">Cart does not lock price or stock. Open an eligible product to continue through Flash Sale Reservation, Order and Payment.</p>
    <button onClick={() => router.push("/all-products")} className="mt-5 w-full rounded-xl bg-orange-600 py-3 font-medium text-white transition hover:-translate-y-0.5 hover:bg-orange-700 hover:shadow-md">Browse products</button>
  </aside>;
}
