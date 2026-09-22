"use client";

import { useEffect, useMemo, useState } from "react";
import toast from "react-hot-toast";
import Navbar from "@/components/Navbar";
import Footer from "@/components/Footer";
import ApiNotice from "@/components/ApiNotice";
import { useAppContext } from "@/context/AppContext";
import { createIdempotencyKey, formatVnd } from "@/lib/api";

const unavailableMessages = {
  PRODUCT_DETAILS_UNAVAILABLE: "Product information is temporarily unavailable.",
  PRODUCT_NOT_FOUND: "This product no longer exists.",
  PRODUCT_NOT_SELLABLE: "This product is not currently for sale.",
};

function CartSkeleton() {
  return <div className="mt-7 space-y-4">{[1, 2].map((item) => <div key={item} className="flex animate-pulse gap-5 rounded-2xl border p-5"><div className="h-28 w-28 rounded-xl bg-gray-200" /><div className="flex-1 space-y-3 py-2"><div className="h-5 w-2/5 rounded bg-gray-200" /><div className="h-4 w-1/4 rounded bg-gray-100" /><div className="h-9 w-32 rounded bg-gray-100" /></div></div>)}</div>;
}

export default function CartPage() {
  const { cart, cartLoading, cartError, authReady, userData, router, request, loadCart, setCartItem, removeCartItem, clearCart, getCartAmount } = useAppContext();
  const [draftQuantities, setDraftQuantities] = useState({});
  const [busyVariant, setBusyVariant] = useState(null);
  const [clearing, setClearing] = useState(false);
  const [checkingOut, setCheckingOut] = useState(false);
  const [actionError, setActionError] = useState(null);
  const [checkoutKey, setCheckoutKey] = useState(null);

  useEffect(() => {
    setDraftQuantities(Object.fromEntries(cart.items.map((item) => [item.variantId, String(item.quantity)])));
  }, [cart.items]);

  const subtotal = useMemo(() => getCartAmount(), [getCartAmount]);

  const refresh = async () => {
    setActionError(null);
    try { await loadCart(); } catch (caught) { setActionError(caught); }
  };

  const commitQuantity = async (item, requested) => {
    const quantity = Number(requested);
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 10) {
      setDraftQuantities((current) => ({ ...current, [item.variantId]: String(item.quantity) }));
      setActionError(new Error("Quantity must be a whole number from 1 to 10."));
      return;
    }
    if (quantity === item.quantity) return;
    setBusyVariant(item.variantId);
    setActionError(null);
    try {
      await setCartItem(item.variantId, quantity);
      toast.success("Cart quantity updated");
    } catch (caught) {
      setDraftQuantities((current) => ({ ...current, [item.variantId]: String(item.quantity) }));
      setActionError(caught);
    } finally {
      setBusyVariant(null);
    }
  };

  const remove = async (variantId) => {
    setBusyVariant(variantId);
    setActionError(null);
    try {
      await removeCartItem(variantId);
      toast.success("Item removed");
    } catch (caught) {
      setActionError(caught);
    } finally {
      setBusyVariant(null);
    }
  };

  const clear = async () => {
    if (!window.confirm("Remove every item from your cart?")) return;
    setClearing(true);
    setActionError(null);
    try {
      await clearCart();
      toast.success("Cart cleared");
    } catch (caught) {
      setActionError(caught);
    } finally {
      setClearing(false);
    }
  };

  const eligibleItems = cart.items.filter((item) => item.detailsAvailable && item.sellable === true && item.basePrice != null);
  const eligibleItemsCount = eligibleItems.length;
  const unavailableItemsCount = cart.items.length - eligibleItemsCount;

  const checkoutCart = async () => {
    if (!cart.cartVersion || eligibleItemsCount === 0 || unavailableItemsCount > 0) {
      setActionError(new Error("Remove unavailable cart items before checkout. The backend checks the complete cart snapshot."));
      return;
    }
    const key = checkoutKey || createIdempotencyKey();
    setCheckoutKey(key);
    setCheckingOut(true);
    setActionError(null);
    try {
      const response = await request("/api/v1/orders/cart-checkouts", {
        method: "POST",
        headers: { "Idempotency-Key": key },
        body: JSON.stringify({
          cartVersion: cart.cartVersion,
          items: eligibleItems.map((item) => ({
            variantId: item.variantId,
            quantity: item.quantity,
            itemVersion: item.itemVersion,
            expectedUnitPrice: Number(item.basePrice),
            currency: item.currency || "VND",
          })),
        }),
      });
      setCheckoutKey(null);
      router.push(`/orders/${response.data.orderId}`);
    } catch (caught) {
      if (caught?.status >= 400 && caught?.status < 500) setCheckoutKey(null);
      setActionError(caught);
    } finally {
      setCheckingOut(false);
    }
  };

  return <>
    <Navbar />
    <main className="min-h-[70vh] bg-gradient-to-b from-orange-50/40 to-white px-6 py-10 md:px-16 lg:px-32">
      <div className="mx-auto max-w-6xl">
        <div className="flex flex-col justify-between gap-4 border-b border-gray-200 pb-6 sm:flex-row sm:items-end">
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.18em] text-orange-600">Your selection</p>
            <h1 className="mt-2 text-3xl font-medium text-gray-900">Shopping cart</h1>
            <p className="mt-2 text-sm text-gray-500">{cart.distinctItemCount} variants · {cart.totalQuantity} total items</p>
          </div>
          <div className="flex gap-3">
            {userData && <button onClick={refresh} disabled={cartLoading} className="rounded-xl border bg-white px-4 py-2 text-sm transition hover:border-orange-300 hover:text-orange-600 disabled:opacity-50">{cartLoading ? "Refreshing…" : "Refresh"}</button>}
            {cart.items.length > 0 && <button onClick={clear} disabled={clearing} className="rounded-xl px-4 py-2 text-sm text-red-600 transition hover:bg-red-50 disabled:opacity-50">{clearing ? "Clearing…" : "Clear cart"}</button>}
          </div>
        </div>

        <ApiNotice error={actionError || cartError} className="mt-5" />

        {!authReady || (cartLoading && cart.items.length === 0) ? <CartSkeleton /> : !userData ? <section className="mt-8 rounded-2xl border border-dashed bg-white p-12 text-center shadow-sm"><div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-orange-100 text-2xl text-orange-600">◇</div><h2 className="mt-4 text-xl font-medium">Sign in to see your cart</h2><p className="mx-auto mt-2 max-w-md text-sm leading-6 text-gray-500">Your cart is connected to your shopper account and will be restored on every device after login.</p><button onClick={() => router.push("/login")} className="mt-6 rounded-xl bg-orange-600 px-6 py-3 font-medium text-white transition hover:-translate-y-0.5 hover:bg-orange-700 hover:shadow-md">Go to login</button></section> : cart.items.length === 0 ? <section className="mt-8 rounded-2xl border border-dashed bg-white p-12 text-center shadow-sm"><div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-gray-100 text-2xl text-gray-400">□</div><h2 className="mt-4 text-xl font-medium">Your cart is empty</h2><p className="mt-2 text-sm text-gray-500">Choose a product variant to save it here.</p><button onClick={() => router.push("/all-products")} className="mt-6 rounded-xl bg-orange-600 px-6 py-3 font-medium text-white transition hover:-translate-y-0.5 hover:shadow-md">Explore products</button></section> : <div className="mt-7 grid gap-7 lg:grid-cols-[minmax(0,1fr)_340px]">
          <section className="space-y-4">
            {cart.items.map((item) => {
              const available = item.detailsAvailable && item.sellable === true;
              const quantity = draftQuantities[item.variantId] ?? String(item.quantity);
              const busy = busyVariant === item.variantId;
              return <article key={item.variantId} className={`group rounded-2xl border bg-white p-4 shadow-sm transition-all duration-300 hover:-translate-y-0.5 hover:shadow-md sm:p-5 ${available ? "border-gray-200" : "border-amber-200"}`}>
                <div className="flex flex-col gap-5 sm:flex-row">
                  <div className="flex h-28 w-full shrink-0 items-center justify-center overflow-hidden rounded-xl bg-gray-50 sm:w-28">
                    {item.primaryImageUrl && item.detailsAvailable ? <img src={item.primaryImageUrl} alt={item.productName || item.variantId} className="h-full w-full object-cover transition duration-300 group-hover:scale-105" /> : <span className="text-3xl text-gray-300">◇</span>}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-start justify-between gap-4">
                      <div><h2 className="font-medium text-gray-900">{item.productName || "Product details unavailable"}</h2><p className="mt-1 text-sm text-gray-500">{item.variantName || `Variant ${item.variantId}`}</p>{item.sku && <p className="mt-1 text-xs text-gray-400">SKU {item.sku}</p>}</div>
                      {available && <div className="whitespace-nowrap text-right"><p className="font-medium text-gray-900">{formatVnd(Number(item.basePrice) * item.quantity)}</p><p className="mt-1 text-xs text-gray-400">{formatVnd(item.basePrice)} × {item.quantity}</p></div>}
                    </div>
                    {!available && <div className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800"><p>{unavailableMessages[item.unavailableReason] || "This item cannot be purchased right now."}</p>{item.unavailableReason === "PRODUCT_DETAILS_UNAVAILABLE" && <button onClick={refresh} className="mt-1 font-medium underline underline-offset-2">Try again</button>}</div>}
                    <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
                      <div className="flex h-10 items-center overflow-hidden rounded-xl border border-gray-200">
                        <button disabled={busy || item.quantity <= 1} onClick={() => commitQuantity(item, item.quantity - 1)} className="h-full px-3 transition hover:bg-orange-50 disabled:opacity-30">−</button>
                        <input disabled={busy} aria-label={`Quantity for ${item.productName || item.variantId}`} min="1" max="10" step="1" value={quantity} onChange={(event) => setDraftQuantities((current) => ({ ...current, [item.variantId]: event.target.value }))} onBlur={() => commitQuantity(item, quantity)} onKeyDown={(event) => { if (event.key === "Enter") event.currentTarget.blur(); }} className="h-full w-12 border-x text-center outline-none disabled:bg-gray-50" />
                        <button disabled={busy || item.quantity >= 10} onClick={() => commitQuantity(item, item.quantity + 1)} className="h-full px-3 transition hover:bg-orange-50 disabled:opacity-30">+</button>
                      </div>
                      <div className="flex items-center gap-3"><button disabled={busy} onClick={() => remove(item.variantId)} className="text-sm text-red-600 transition hover:text-red-700 disabled:opacity-40">{busy ? "Updating…" : "Remove"}</button>{item.productSlug && <button disabled={!available} onClick={() => router.push(`/product/${item.productSlug}`)} className="rounded-lg bg-gray-900 px-4 py-2 text-sm text-white transition hover:bg-orange-600 disabled:cursor-not-allowed disabled:bg-gray-300">Open product</button>}</div>
                    </div>
                  </div>
                </div>
              </article>;
            })}
          </section>

          <aside className="h-fit rounded-2xl border border-gray-200 bg-white p-6 shadow-sm lg:sticky lg:top-6">
            <h2 className="text-xl font-medium text-gray-900">Cart summary</h2>
            <div className="mt-5 space-y-3 text-sm"><div className="flex justify-between text-gray-600"><span>Total quantity</span><span>{cart.totalQuantity}</span></div><div className="flex justify-between text-gray-600"><span>Eligible variants</span><span>{cart.items.filter((item) => item.detailsAvailable && item.sellable === true).length}</span></div><div className="flex justify-between border-t pt-4 text-base font-medium text-gray-900"><span>Estimated subtotal</span><span>{formatVnd(subtotal)}</span></div></div>
            <div className="mt-5 rounded-xl bg-orange-50 p-4 text-xs leading-5 text-orange-800">This is an estimate only. Price and stock are checked again by Order, Product, and Inventory before a payable Order is created.</div>
            {unavailableItemsCount > 0 && <p className="mt-4 rounded-xl bg-amber-50 p-3 text-xs leading-5 text-amber-800">{unavailableItemsCount} item(s) need attention. Remove them or refresh the cart before checkout; checkout is all-or-nothing.</p>}
            <button disabled={checkingOut || eligibleItemsCount === 0 || unavailableItemsCount > 0} onClick={checkoutCart} className="mt-4 w-full rounded-xl bg-orange-600 py-3 font-medium text-white transition hover:bg-orange-700 disabled:cursor-not-allowed disabled:opacity-50">{checkingOut ? "Creating order…" : "Checkout all items"}</button>
            <p className="mt-2 text-center text-xs text-gray-500">Checkout creates one regular Order. Payment opens on the Order page.</p>
            <button onClick={() => router.push("/all-products")} className="mt-5 w-full rounded-xl border border-orange-500 py-3 font-medium text-orange-600 transition hover:bg-orange-50">Continue shopping</button>
          </aside>
        </div>}
      </div>
    </main>
    <Footer />
  </>;
}
