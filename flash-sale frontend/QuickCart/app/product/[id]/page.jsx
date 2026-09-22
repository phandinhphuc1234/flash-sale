"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import toast from "react-hot-toast";
import Navbar from "@/components/Navbar";
import Footer from "@/components/Footer";
import Loading from "@/components/Loading";
import ApiNotice from "@/components/ApiNotice";
import { createIdempotencyKey, formatVnd } from "@/lib/api";
import { useAppContext } from "@/context/AppContext";

export default function ProductPage() {
  const { id: slug } = useParams();
  const { request, router, userData, authReady, setCartItem } = useAppContext();
  const [product, setProduct] = useState(null);
  const [variantId, setVariantId] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [campaignId, setCampaignId] = useState(process.env.NEXT_PUBLIC_DEFAULT_CAMPAIGN_ID || "");
  const [error, setError] = useState(null);
  const [adding, setAdding] = useState(false);
  const [buying, setBuying] = useState(false);
  const [reserving, setReserving] = useState(false);
  const buyNowKeyRef = useRef(null);

  const loadProduct = useCallback(async () => {
    try {
      const response = await request(`/api/v1/catalog/products/${slug}`);
      setProduct(response.data);
      setVariantId((current) => current || response.data.variants?.[0]?.id || "");
    } catch (caught) {
      setError(caught);
    }
  }, [request, slug]);

  useEffect(() => {
    loadProduct();
  }, [loadProduct]);

  const changeQuantity = (value) => {
    const next = Number(value);
    if (Number.isInteger(next)) setQuantity(Math.min(10, Math.max(1, next)));
  };

  const addSelectedVariant = async () => {
    if (!authReady) return;
    if (!userData) {
      router.push("/login");
      return;
    }
    setError(null);
    setAdding(true);
    try {
      await setCartItem(variantId, quantity);
      toast.success("Added to your cart");
    } catch (caught) {
      setError(caught);
    } finally {
      setAdding(false);
    }
  };

  const reserve = async () => {
    if (!userData) return router.push("/login");
    if (!campaignId.trim()) {
      setError(new Error("Campaign ID is required. The backend does not yet provide public campaign discovery."));
      return;
    }
    setError(null);
    setReserving(true);
    try {
      const response = await request(`/api/v1/flash-sales/${campaignId.trim()}/reservations`, {
        method: "POST",
        headers: { "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify({ variantId, quantity }),
      });
      const reservation = response.data;
      sessionStorage.setItem("flash-sale-purchase", JSON.stringify(reservation));
      router.push(`/reservations/${reservation.reservationId}`);
    } catch (caught) {
      setError(caught);
    } finally {
      setReserving(false);
    }
  };

  const buyNow = async () => {
    if (!userData) return router.push("/login");
    if (!selected?.basePrice || !variantId) return;
    const idempotencyKey = buyNowKeyRef.current || createIdempotencyKey();
    buyNowKeyRef.current = idempotencyKey;
    setError(null);
    setBuying(true);
    try {
      const response = await request("/api/v1/orders/buy-now", {
        method: "POST",
        headers: { "Idempotency-Key": idempotencyKey },
        body: JSON.stringify({
          variantId,
          quantity,
          expectedUnitPrice: Number(selected.basePrice),
          currency: selected.currency || "VND",
        }),
      });
      buyNowKeyRef.current = null;
      router.push(`/orders/${response.data.orderId}`);
    } catch (caught) {
      if (caught?.status >= 400 && caught?.status < 500) buyNowKeyRef.current = null;
      setError(caught);
    } finally {
      setBuying(false);
    }
  };

  if (!product && !error) return <Loading />;
  if (!product) return <><Navbar /><main className="mx-auto min-h-screen max-w-3xl px-6 py-16"><ApiNotice error={error} /></main></>;

  const selected = product.variants.find((variant) => variant.id === variantId);
  const primaryImage = product.media?.[0];

  return <>
    <Navbar />
    <main className="px-6 py-10 md:px-16 md:py-14 lg:px-32">
      <div className="grid grid-cols-1 gap-10 md:grid-cols-2 lg:gap-16">
        <section className="group relative overflow-hidden rounded-2xl bg-gradient-to-br from-gray-50 to-orange-50/60">
          <div className="flex aspect-square items-center justify-center overflow-hidden">
            {primaryImage?.url
              ? <img src={primaryImage.url} alt={primaryImage.altText || product.name} className="h-full w-full object-cover transition duration-500 group-hover:scale-[1.03]" />
              : <div className="flex flex-col items-center gap-2 text-gray-400"><span className="text-5xl">◇</span><span>No product image</span></div>}
          </div>
          <span className="absolute left-4 top-4 rounded-full bg-white/90 px-3 py-1 text-xs font-medium text-orange-700 shadow-sm backdrop-blur">Flash-sale ready</span>
        </section>

        <section className="flex flex-col justify-center">
          <p className="text-sm font-medium uppercase tracking-[0.16em] text-orange-600">{product.categories?.filter((category) => category.primary).map((category) => category.name).join(", ") || "Ecommerce"}</p>
          <h1 className="mt-2 text-3xl font-medium text-gray-900 md:text-4xl">{product.name}</h1>
          <p className="mt-4 leading-7 text-gray-600">{product.description || product.shortDescription}</p>
          <p className="mt-6 text-3xl font-medium text-gray-900">{formatVnd(selected?.basePrice)}</p>

          <div className="mt-7 space-y-4 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
            <label className="block text-sm font-medium">Variant
              <select value={variantId} onChange={(event) => setVariantId(event.target.value)} className="mt-2 w-full rounded-xl border border-gray-200 bg-gray-50 p-3 outline-none transition focus:border-orange-400 focus:ring-2 focus:ring-orange-100">
                {product.variants.map((variant) => <option key={variant.id} value={variant.id}>{variant.name} · {variant.sku}</option>)}
              </select>
            </label>
            <div className="flex items-end justify-between gap-4">
              <label className="block text-sm font-medium">Quantity
                <div className="mt-2 flex h-11 items-center overflow-hidden rounded-xl border border-gray-200">
                  <button type="button" onClick={() => changeQuantity(quantity - 1)} className="h-full px-4 text-lg transition hover:bg-orange-50">−</button>
                  <input aria-label="Quantity" min="1" max="10" step="1" type="number" value={quantity} onChange={(event) => changeQuantity(event.target.value)} className="h-full w-14 border-x text-center outline-none" />
                  <button type="button" onClick={() => changeQuantity(quantity + 1)} className="h-full px-4 text-lg transition hover:bg-orange-50">+</button>
                </div>
              </label>
              <span className="pb-3 text-xs text-gray-500">Maximum 10 per variant</span>
            </div>
          </div>

          <ApiNotice error={error} className="mt-4" />
          <div className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-3">
            <button disabled={adding || !variantId || !authReady} onClick={addSelectedVariant} className="rounded-xl border border-orange-500 bg-white py-3.5 font-medium text-orange-600 transition hover:-translate-y-0.5 hover:bg-orange-50 hover:shadow-md disabled:translate-y-0 disabled:cursor-not-allowed disabled:opacity-50">
              {adding ? "Adding…" : userData ? "Add to cart" : "Login to add"}
            </button>
            <button disabled={buying || !variantId || !authReady} onClick={buyNow} className="rounded-xl bg-gray-900 py-3.5 font-medium text-white shadow-sm transition hover:-translate-y-0.5 hover:bg-gray-800 hover:shadow-md disabled:translate-y-0 disabled:cursor-not-allowed disabled:opacity-50">
              {buying ? "Creating order…" : userData ? "Buy now" : "Login to buy"}
            </button>
            <button disabled={reserving || !variantId} onClick={reserve} className="rounded-xl bg-orange-600 py-3.5 font-medium text-white shadow-sm transition hover:-translate-y-0.5 hover:bg-orange-700 hover:shadow-md disabled:translate-y-0 disabled:cursor-not-allowed disabled:opacity-50">
              {reserving ? "Reserving…" : "Reserve now"}
            </button>
          </div>

          <label className="mt-5 block text-xs font-medium text-gray-600">Flash-sale campaign ID
            <input value={campaignId} onChange={(event) => setCampaignId(event.target.value)} placeholder="Set default campaign or enter an ID" className="mt-2 w-full rounded-xl border border-gray-200 p-3 text-sm outline-none transition focus:border-orange-400" />
          </label>
          <p className="mt-3 text-xs leading-5 text-gray-500">Buy now follows the regular Order → Payment flow. Reserve now is only for an active Flash Sale campaign.</p>
        </section>
      </div>
    </main>
    <Footer />
  </>;
}
