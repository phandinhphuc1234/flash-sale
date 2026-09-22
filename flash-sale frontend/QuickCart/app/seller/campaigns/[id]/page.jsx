"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { useAppContext } from "@/context/AppContext";
import ApiNotice from "@/components/ApiNotice";
import { formatVnd } from "@/lib/api";
import {
  AdminBackLink,
  AdminPageHeader,
  AdminStatus,
  adminCard,
  adminInput,
  adminPage,
  adminPrimaryButton,
  adminSecondaryButton,
  adminSubtleButton,
} from "@/components/seller/AdminUi";

const pad = (value) => String(value).padStart(2, "0");

// datetime-local represents a wall-clock value. Do not use toISOString here:
// that would silently shift the operator's local time into UTC.
const toLocalInputDate = (value) => {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

const formatDate = (value) => {
  if (!value) return "Not set";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Not set" : new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium", timeStyle: "short" }).format(date);
};

const statusTone = (status) => ({ ACTIVE: "green", SCHEDULED: "orange", ENDED: "slate", DRAFT: "slate" }[status] || "slate");

export default function CampaignItemPage() {
  const { id } = useParams();
  const { request } = useAppContext();
  const [campaign, setCampaign] = useState(null);
  const [catalog, setCatalog] = useState([]);
  const [catalogError, setCatalogError] = useState(null);
  const [item, setItem] = useState({ variantId: "", campaignPrice: "", requestedQuantity: "", purchaseLimitPerUser: "1" });
  const [metadata, setMetadata] = useState({ name: "", startAt: "", endAt: "" });
  const [selectedProductId, setSelectedProductId] = useState("");
  const [eventId, setEventId] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState(null);
  const [metadataError, setMetadataError] = useState("");
  const [offerError, setOfferError] = useState("");
  const [pendingAction, setPendingAction] = useState("");
  const [timeZone, setTimeZone] = useState("");

  const load = useCallback(async () => {
    const campaignResponse = await request(`/api/v1/admin/campaigns/${id}`);
    const value = campaignResponse.data;
    setCampaign(value);
    setMetadata({ name: value.name || "", startAt: toLocalInputDate(value.startAt), endAt: toLocalInputDate(value.endAt) });
    setItem(value.item ? {
      variantId: value.item.variantId || "",
      campaignPrice: value.item.campaignPrice ?? "",
      requestedQuantity: value.item.requestedQuantity ?? "",
      purchaseLimitPerUser: value.item.purchaseLimitPerUser ?? "1",
    } : { variantId: "", campaignPrice: "", requestedQuantity: "", purchaseLimitPerUser: "1" });

    try {
      const catalogResponse = await request("/api/v1/catalog/products?page=0&size=40");
      setCatalog((catalogResponse.data?.data || []).filter((product) => Array.isArray(product.variants)));
      setCatalogError(null);
    } catch (catalogCaught) {
      setCatalogError(catalogCaught);
    }
  }, [id, request]);

  useEffect(() => {
    setTimeZone(Intl.DateTimeFormat().resolvedOptions().timeZone);
    load().catch((caught) => setError(caught));
  }, [load]);

  const products = useMemo(() => catalog.map((product) => ({ ...product, variants: product.variants || [] })), [catalog]);
  const selectedProduct = products.find((product) => product.id === selectedProductId);
  const selectedVariant = selectedProduct?.variants.find((variant) => variant.id === item.variantId);
  const currentProduct = selectedProduct || products.find((product) => product.variants.some((variant) => variant.id === item.variantId));
  const basePrice = Number(selectedVariant?.basePrice ?? campaign?.item?.basePrice ?? 0);
  const campaignPrice = Number(item.campaignPrice);
  const discountPercent = basePrice > 0 && campaignPrice > 0 && campaignPrice < basePrice
    ? Math.round((1 - campaignPrice / basePrice) * 100)
    : null;
  const isDraft = campaign?.status === "DRAFT";
  const canEdit = isDraft && !pendingAction;
  const isReadyToSchedule = Boolean(campaign?.item) && campaign?.status === "DRAFT";

  useEffect(() => {
    if (!selectedProductId && currentProduct) setSelectedProductId(currentProduct.id);
  }, [currentProduct, selectedProductId]);

  const run = async (action, operation, successMessage) => {
    setError(null);
    setNotice("");
    setPendingAction(action);
    try {
      const value = await operation();
      await load();
      if (successMessage) setNotice(successMessage);
      return value;
    } catch (caught) {
      setError(caught);
      return null;
    } finally {
      setPendingAction("");
    }
  };

  const updateMetadata = (event) => {
    event.preventDefault();
    const start = new Date(metadata.startAt);
    const end = new Date(metadata.endAt);
    if (!metadata.name.trim()) return setMetadataError("Enter a campaign name.");
    if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || end <= start) {
      return setMetadataError("Choose an end time after the start time.");
    }
    setMetadataError("");
    run("metadata", () => request(`/api/v1/admin/campaigns/${id}`, {
      method: "PATCH",
      headers: { "If-Match": `\"${campaign.version}\"` },
      body: JSON.stringify({ name: metadata.name.trim(), startAt: start.toISOString(), endAt: end.toISOString() }),
    }), "Campaign details saved.");
  };

  const updateProduct = (event) => {
    const productId = event.target.value;
    setSelectedProductId(productId);
    setItem((current) => ({ ...current, variantId: "", campaignPrice: "" }));
    setOfferError("");
  };

  const updateVariant = (event) => {
    const variantId = event.target.value;
    const variant = selectedProduct?.variants.find((candidate) => candidate.id === variantId);
    setItem((current) => ({ ...current, variantId, campaignPrice: variant?.basePrice ? String(variant.basePrice) : "" }));
    setOfferError("");
  };

  const attach = (event) => {
    event.preventDefault();
    const price = Number(item.campaignPrice);
    const quantity = Number(item.requestedQuantity);
    const limit = Number(item.purchaseLimitPerUser);
    if (!item.variantId) return setOfferError("Choose a product variant first.");
    if (!Number.isFinite(price) || price <= 0) return setOfferError("Enter a sale price greater than zero.");
    if (basePrice > 0 && price >= basePrice) return setOfferError(`Sale price must be lower than the base price (${formatVnd(basePrice)}).`);
    if (!Number.isInteger(quantity) || quantity <= 0) return setOfferError("Requested quantity must be a positive whole number.");
    if (!Number.isInteger(limit) || limit <= 0 || limit > quantity) return setOfferError("Per-user limit must be between 1 and the requested quantity.");
    setOfferError("");
    run("item", () => request(`/api/v1/admin/campaigns/${id}/item`, {
      method: "PUT",
      headers: { "If-Match": `\"${campaign.version}\"` },
      body: JSON.stringify({ variantId: item.variantId, campaignPrice: price, requestedQuantity: quantity, purchaseLimitPerUser: limit }),
    }), "Flash-sale offer saved.");
  };

  const schedule = () => run("schedule", () => request(`/api/v1/admin/campaigns/${id}/schedule`, {
    method: "POST",
    headers: { "If-Match": `\"${campaign.version}\"`, "Idempotency-Key": crypto.randomUUID() },
    body: "{}",
  }), "Campaign scheduled.");

  const activate = () => run("activate", () => request(`/api/v1/admin/campaigns/${id}/activate`, {
    method: "POST",
    headers: { "If-Match": `\"${campaign.version}\"` },
    body: "{}",
  }), "Campaign activated.");

  const requeue = () => run("requeue", () => request(`/api/v1/admin/campaigns/${id}/outbox-events/${eventId}/requeue`, {
    method: "POST",
    body: "{}",
  }), "Outbox event requeued.");

  if (!campaign) {
    return <main className={adminPage}><div className="mx-auto max-w-6xl"><AdminBackLink href="/seller/campaigns">Campaigns</AdminBackLink><ApiNotice error={error} className="mt-6" /><p className="mt-6 text-sm text-slate-500">Loading campaign details…</p></div></main>;
  }

  return (
    <main className={adminPage}>
      <div className="mx-auto max-w-6xl">
        <AdminBackLink href="/seller/campaigns">Campaigns</AdminBackLink>
        <div className="mt-5">
          <AdminPageHeader
            eyebrow="Campaign / Detail"
            title={campaign.name || "Campaign"}
            description="Prepare the offer, review the launch checklist, then schedule the sale when everything is ready."
            action={<div className="flex flex-wrap items-center gap-2"><AdminStatus tone={statusTone(campaign.status)}>{campaign.status} · v{campaign.version}</AdminStatus><span className="text-xs text-slate-400">{campaign.code}</span></div>}
          />
        </div>
        <ApiNotice error={error} className="mt-6" />
        {notice && <p className="mt-5 rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-700 ring-1 ring-emerald-100">{notice}</p>}

        <section className={`${adminCard} mt-6 p-4 md:p-5`} aria-label="Campaign progress">
          <div className="grid gap-3 md:grid-cols-3">
            {[
              ["01", "Campaign details", metadata.name && metadata.startAt && metadata.endAt],
              ["02", "Flash-sale offer", Boolean(campaign.item)],
              ["03", "Launch", campaign.status !== "DRAFT"],
            ].map(([number, label, complete]) => <div key={number} className="flex items-center gap-3 rounded-xl bg-slate-50 px-3 py-3"><span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-bold ${complete ? "bg-emerald-100 text-emerald-700" : "bg-orange-100 text-orange-700"}`}>{complete ? "✓" : number}</span><div><p className="text-sm font-semibold text-slate-800">{label}</p><p className="text-xs text-slate-500">{complete ? "Ready" : "Needs attention"}</p></div></div>)}
          </div>
        </section>

        <div className="mt-5 grid gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
          <div className="space-y-5">
            <form onSubmit={updateMetadata} className={`${adminCard} space-y-5 p-5 md:p-6`}>
              <div><p className="text-xs font-semibold uppercase tracking-[0.16em] text-orange-600">Step 1</p><h2 className="mt-1 text-lg font-semibold text-slate-950">Campaign details</h2><p className="mt-1 text-sm text-slate-500">Set the name and active window in your local time.</p></div>
              <fieldset disabled={!canEdit} className="space-y-4">
                <label className="block text-sm font-medium text-slate-700">Campaign name<input required name="name" value={metadata.name} onChange={(event) => { setMetadata({ ...metadata, name: event.target.value }); setMetadataError(""); }} className={adminInput} /></label>
                <div className="grid gap-4 sm:grid-cols-2"><label className="block text-sm font-medium text-slate-700">Start time<input required name="startAt" type="datetime-local" value={metadata.startAt} onChange={(event) => { setMetadata({ ...metadata, startAt: event.target.value }); setMetadataError(""); }} className={`${adminInput} min-w-0`} /></label><label className="block text-sm font-medium text-slate-700">End time<input required name="endAt" type="datetime-local" value={metadata.endAt} onChange={(event) => { setMetadata({ ...metadata, endAt: event.target.value }); setMetadataError(""); }} className={`${adminInput} min-w-0`} /></label></div>
                <p className="text-xs text-slate-500">Browser time zone: {timeZone || "detecting…"}</p>
                {metadataError && <p role="alert" className="text-sm text-red-700">{metadataError}</p>}
                <button disabled={!canEdit} className={adminSecondaryButton}>Save details</button>
              </fieldset>
            </form>

            <form onSubmit={attach} className={`${adminCard} space-y-5 p-5 md:p-6`}>
              <div><p className="text-xs font-semibold uppercase tracking-[0.16em] text-orange-600">Step 2</p><h2 className="mt-1 text-lg font-semibold text-slate-950">Flash-sale offer</h2><p className="mt-1 text-sm text-slate-500">Choose a product customers recognize. The system will submit the underlying variant ID for you.</p></div>
              {catalogError && <p className="rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800 ring-1 ring-amber-100">Product catalog could not be loaded. Refresh the page or choose again later.</p>}
              <fieldset disabled={!canEdit} className="space-y-4">
                <label className="block text-sm font-medium text-slate-700">Product<select value={selectedProductId} onChange={updateProduct} className={adminInput}><option value="">Choose a product</option>{products.map((product) => <option key={product.id} value={product.id}>{product.name}{product.code ? ` · ${product.code}` : ""}</option>)}</select></label>
                <label className="block text-sm font-medium text-slate-700">Sellable variant<select required value={item.variantId} onChange={updateVariant} className={adminInput}><option value="">Choose a variant</option>{selectedProduct?.variants.map((variant) => <option key={variant.id} value={variant.id}>{variant.name || variant.sku}{variant.sku ? ` · ${variant.sku}` : ""} · {formatVnd(variant.basePrice)}</option>)}{item.variantId && !selectedVariant && <option value={item.variantId}>Current variant · {item.variantId.slice(0, 8)}…</option>}</select></label>
                <div className="grid gap-4 sm:grid-cols-3"><label className="block text-sm font-medium text-slate-700">Sale price (VND)<input required min="1" type="number" value={item.campaignPrice} onChange={(event) => setItem({ ...item, campaignPrice: event.target.value })} className={adminInput} /></label><label className="block text-sm font-medium text-slate-700">Quantity<input required min="1" step="1" type="number" value={item.requestedQuantity} onChange={(event) => setItem({ ...item, requestedQuantity: event.target.value })} className={adminInput} /></label><label className="block text-sm font-medium text-slate-700">Per-user limit<input required min="1" step="1" type="number" value={item.purchaseLimitPerUser} onChange={(event) => setItem({ ...item, purchaseLimitPerUser: event.target.value })} className={adminInput} /></label></div>
                {selectedVariant && <p className="rounded-xl bg-slate-50 px-4 py-3 text-sm text-slate-600">Base price: <strong>{formatVnd(selectedVariant.basePrice)}</strong>{discountPercent !== null && <span className="ml-2 font-semibold text-emerald-700">{discountPercent}% off</span>}</p>}
                {offerError && <p role="alert" className="text-sm text-red-700">{offerError}</p>}
                <button disabled={!canEdit || !selectedProduct} className={adminPrimaryButton}>{pendingAction === "item" ? "Saving…" : "Save offer"}</button>
              </fieldset>
            </form>
          </div>

          <aside className={`${adminCard} h-fit p-5 lg:sticky lg:top-6`}>
            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-orange-600">Step 3</p>
            <h2 className="mt-1 text-lg font-semibold text-slate-950">Launch checklist</h2>
            <div className="mt-5 space-y-3 text-sm"><div className="flex justify-between gap-3"><span className="text-slate-500">Window</span><span className="text-right font-medium text-slate-800">{formatDate(campaign.startAt)}<br />to {formatDate(campaign.endAt)}</span></div><div className="flex justify-between gap-3"><span className="text-slate-500">Offer</span><span className="font-medium text-slate-800">{campaign.item ? "Attached" : "Not attached"}</span></div><div className="flex justify-between gap-3"><span className="text-slate-500">Status</span><AdminStatus tone={statusTone(campaign.status)}>{campaign.status}</AdminStatus></div></div>
            <div className="mt-5 border-t border-slate-100 pt-5"><button type="button" onClick={schedule} disabled={!isReadyToSchedule || Boolean(pendingAction)} className={`${adminPrimaryButton} w-full`}>{pendingAction === "schedule" ? "Scheduling…" : campaign.status === "DRAFT" ? "Schedule campaign" : "Already scheduled"}</button>{campaign.status === "DRAFT" && !campaign.item && <p className="mt-2 text-xs leading-5 text-slate-500">Save a product offer before scheduling.</p>}</div>
          </aside>
        </div>

        <details className={`${adminCard} mt-5 p-5 md:p-6`}>
          <summary className="cursor-pointer list-none text-sm font-semibold text-slate-800">Advanced recovery</summary>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">These operations are for recovery and support. They are not part of the normal draft setup flow.</p>
          <div className="mt-5 grid gap-5 lg:grid-cols-2">
            <div className="rounded-xl border border-slate-200 p-4"><h3 className="text-sm font-semibold text-slate-900">Manual activation</h3><p className="mt-1 text-xs leading-5 text-slate-500">Use only for a scheduled campaign during its valid sale window.</p><button type="button" onClick={activate} disabled={campaign.status !== "SCHEDULED" || Boolean(pendingAction)} className={`${adminSubtleButton} mt-4`}>{pendingAction === "activate" ? "Activating…" : "Activate manually"}</button></div>
            <div className="rounded-xl border border-slate-200 p-4"><h3 className="text-sm font-semibold text-slate-900">Requeue failed outbox event</h3><p className="mt-1 text-xs leading-5 text-slate-500">Paste an outbox event UUID only after reviewing the failure.</p><div className="mt-4 flex flex-col gap-2 sm:flex-row"><input value={eventId} onChange={(event) => setEventId(event.target.value)} placeholder="Outbox event UUID" className={`${adminInput} mt-0`} /><button type="button" disabled={!eventId || Boolean(pendingAction)} onClick={requeue} className={adminSubtleButton}>{pendingAction === "requeue" ? "Requeuing…" : "Requeue"}</button></div></div>
          </div>
        </details>
      </div>
    </main>
  );
}
