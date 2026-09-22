"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import ApiNotice from "@/components/ApiNotice";
import { useAppContext } from "@/context/AppContext";
import {
  AdminPageHeader,
  adminCard,
  adminInput,
  adminPage,
  adminPrimaryButton,
  adminSubtleButton,
} from "@/components/seller/AdminUi";

const PAGE_SIZE = 20;

function formatQuantity(value) {
  return new Intl.NumberFormat("en-US").format(Number(value || 0));
}

function statusLabel(display) {
  if (!display?.found) return "Unavailable";
  if (display.productStatus === "ARCHIVED" || display.variantStatus === "ARCHIVED") return "Archived";
  if (display.productStatus !== "ACTIVE" || display.variantStatus !== "ACTIVE") return "Not for sale";
  return "Sellable";
}

export default function InventoryPage() {
  const { request } = useAppContext();
  const [page, setPage] = useState(0);
  const [inventoryPage, setInventoryPage] = useState(null);
  const [displayByVariant, setDisplayByVariant] = useState({});
  const [selected, setSelected] = useState(null);
  const [movements, setMovements] = useState([]);
  const [movement, setMovement] = useState({ type: "INCREASE", quantity: "", reason: "" });
  const [error, setError] = useState(null);
  const [displayWarning, setDisplayWarning] = useState(null);
  const [pending, setPending] = useState(false);
  const [loading, setLoading] = useState(false);

  const loadPage = useCallback(async (pageNumber) => {
    setLoading(true);
    setError(null);
    setDisplayWarning(null);
    try {
      const response = await request(`/api/v1/admin/inventory?page=${pageNumber}&size=${PAGE_SIZE}`);
      const nextPage = response?.data || { data: [], page: { number: pageNumber, size: PAGE_SIZE, totalElements: 0, totalPages: 0, hasNext: false } };
      setInventoryPage(nextPage);
      const variantIds = (nextPage.data || []).map((item) => item.variantId);
      if (variantIds.length === 0) {
        setDisplayByVariant({});
        setSelected(null);
        return;
      }
      try {
        const displayResponse = await request("/api/v1/admin/catalog/variants/display-details", {
          method: "POST",
          headers: { "X-Trace-Id": "quickcart-inventory-display" },
          body: JSON.stringify({ variantIds }),
        });
        const nextDisplay = (displayResponse?.data?.variants || []).reduce((lookup, item) => {
          lookup[item.variantId] = item;
          return lookup;
        }, {});
        setDisplayByVariant(nextDisplay);
      } catch {
        setDisplayByVariant({});
        setDisplayWarning("Stock is visible, but product labels are temporarily unavailable. SKU snapshots are shown as a fallback.");
      }
    } catch (caught) {
      setError(caught);
    } finally {
      setLoading(false);
    }
  }, [request]);

  useEffect(() => {
    loadPage(page);
  }, [loadPage, page]);

  const rows = useMemo(() => inventoryPage?.data || [], [inventoryPage]);
  const pageMeta = inventoryPage?.page;
  const selectedRow = useMemo(
    () => rows.find((row) => row.variantId === selected?.variantId) || selected,
    [rows, selected],
  );

  const selectRow = async (row) => {
    setSelected(row);
    setMovements([]);
    setError(null);
    try {
      const response = await request(`/api/v1/admin/inventory/${row.variantId}/movements?page=0&size=20`);
      setMovements(response?.data?.data || []);
    } catch (caught) {
      setError(caught);
    }
  };

  const adjust = async (event) => {
    event.preventDefault();
    if (!selectedRow) return;
    setPending(true);
    setError(null);
    try {
      const response = await request(`/api/v1/admin/inventory/${selectedRow.variantId}/adjustments`, {
        method: "POST",
        body: JSON.stringify({
          requestId: crypto.randomUUID(),
          type: movement.type,
          quantity: Number(movement.quantity),
          reason: movement.reason,
        }),
      });
      const nextInventory = response?.data;
      setSelected(nextInventory);
      setInventoryPage((current) => current && {
        ...current,
        data: current.data.map((row) => row.variantId === nextInventory.variantId
          ? { ...row, ...nextInventory, updatedAt: new Date().toISOString() }
          : row),
      });
      setMovement({ ...movement, quantity: "", reason: "" });
      const history = await request(`/api/v1/admin/inventory/${selectedRow.variantId}/movements?page=0&size=20`);
      setMovements(history?.data?.data || []);
    } catch (caught) {
      setError(caught);
    } finally {
      setPending(false);
    }
  };

  return (
    <main className={adminPage}>
      <div className="mx-auto max-w-6xl">
        <AdminPageHeader
          eyebrow="Inventory admin"
          title="Stock overview"
          description="Review every initialized variant in one paginated view, then manage stock movements without copying UUIDs."
        />

        <ApiNotice error={error} className="mt-5" />
        {displayWarning && (
          <p className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            {displayWarning}
          </p>
        )}

        <section className={`${adminCard} mt-6 overflow-hidden`}>
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4 md:px-7">
            <div>
              <h2 className="font-semibold text-slate-950">Inventory items</h2>
              <p className="mt-1 text-sm text-slate-500">Quantities come from Inventory Service; product labels come from one bounded Product lookup.</p>
            </div>
            <button onClick={() => loadPage(page)} className={adminSubtleButton} disabled={loading}>
              {loading ? "Refreshing…" : "Refresh"}
            </button>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-5 py-3 font-semibold md:px-7">Product / variant</th>
                  <th className="px-5 py-3 font-semibold">SKU</th>
                  <th className="px-5 py-3 text-right font-semibold">On hand</th>
                  <th className="px-5 py-3 text-right font-semibold">Allocated</th>
                  <th className="px-5 py-3 text-right font-semibold">Available</th>
                  <th className="px-5 py-3 font-semibold">Status</th>
                  <th className="px-5 py-3 text-right font-semibold md:px-7">Action</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => {
                  const display = displayByVariant[row.variantId];
                  return (
                    <tr key={row.variantId} className="border-t border-slate-100 align-middle">
                      <td className="px-5 py-4 md:px-7">
                        <p className="font-medium text-slate-950">{display?.productName || "Product unavailable"}</p>
                        <p className="mt-1 text-xs text-slate-500">{display?.variantName || "Variant label unavailable"}</p>
                      </td>
                      <td className="px-5 py-4 font-mono text-xs text-slate-600">{display?.sku || row.skuSnapshot}</td>
                      <td className="px-5 py-4 text-right font-medium text-slate-900">{formatQuantity(row.onHandQuantity)}</td>
                      <td className="px-5 py-4 text-right text-orange-700">{formatQuantity(row.campaignAllocatedQuantity)}</td>
                      <td className="px-5 py-4 text-right font-semibold text-emerald-700">{formatQuantity(row.availableQuantity)}</td>
                      <td className="px-5 py-4"><span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-600">{statusLabel(display)}</span></td>
                      <td className="px-5 py-4 text-right md:px-7"><button onClick={() => selectRow(row)} className={adminSubtleButton}>Manage</button></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {!loading && rows.length === 0 && (
            <div className="px-6 py-14 text-center"><p className="font-medium text-slate-900">No inventory items yet</p><p className="mt-2 text-sm text-slate-500">Initialize stock from the existing inventory fixture or product workflow, then refresh this page.</p></div>
          )}
          <div className="flex items-center justify-between border-t border-slate-100 px-5 py-4 text-sm md:px-7">
            <p className="text-slate-500">Page {(pageMeta?.number ?? page) + 1} · {formatQuantity(pageMeta?.totalElements)} items</p>
            <div className="flex gap-2"><button disabled={page === 0 || loading} onClick={() => setPage((current) => current - 1)} className={adminSubtleButton}>Previous</button><button disabled={!pageMeta?.hasNext || loading} onClick={() => setPage((current) => current + 1)} className={adminSubtleButton}>Next</button></div>
          </div>
        </section>

        {selectedRow && (
          <section className={`${adminCard} mt-6 p-5 md:p-7`}>
            <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-wide text-orange-600">Selected item</p><h2 className="mt-1 text-xl font-semibold text-slate-950">{displayByVariant[selectedRow.variantId]?.productName || selectedRow.skuSnapshot}</h2><p className="mt-1 text-sm text-slate-500">{displayByVariant[selectedRow.variantId]?.variantName || "Variant"} · {displayByVariant[selectedRow.variantId]?.sku || selectedRow.skuSnapshot}</p></div><button onClick={() => setSelected(null)} className={adminSubtleButton}>Close</button></div>
            <div className="mt-6 grid gap-3 sm:grid-cols-3"><div className="rounded-xl bg-slate-50 p-4"><p className="text-xs font-semibold uppercase tracking-wide text-slate-400">On hand</p><p className="mt-2 text-2xl font-semibold text-slate-950">{formatQuantity(selectedRow.onHandQuantity)}</p></div><div className="rounded-xl bg-orange-50 p-4"><p className="text-xs font-semibold uppercase tracking-wide text-orange-600">Allocated</p><p className="mt-2 text-2xl font-semibold text-slate-950">{formatQuantity(selectedRow.campaignAllocatedQuantity)}</p></div><div className="rounded-xl bg-emerald-50 p-4"><p className="text-xs font-semibold uppercase tracking-wide text-emerald-600">Available</p><p className="mt-2 text-2xl font-semibold text-slate-950">{formatQuantity(selectedRow.availableQuantity)}</p></div></div>
            <form onSubmit={adjust} className="mt-7 border-t border-slate-100 pt-6"><h3 className="font-semibold text-slate-950">Adjust stock</h3><p className="mt-1 text-sm text-slate-500">Every adjustment is recorded in movement history.</p><div className="mt-4 grid gap-3 sm:grid-cols-2"><select value={movement.type} onChange={(event) => setMovement({ ...movement, type: event.target.value })} className={adminInput}><option>INCREASE</option><option>DECREASE</option></select><input required min="1" type="number" value={movement.quantity} onChange={(event) => setMovement({ ...movement, quantity: event.target.value })} placeholder="Quantity" className={adminInput} /></div><input required value={movement.reason} onChange={(event) => setMovement({ ...movement, reason: event.target.value })} placeholder="Reason" className={adminInput} /><div className="mt-4"><button disabled={pending} className={adminPrimaryButton}>{pending ? "Saving…" : "Adjust stock"}</button></div></form>
            <section className="mt-7 border-t border-slate-100 pt-6"><h3 className="font-semibold text-slate-950">Movement history</h3><p className="mt-1 text-sm text-slate-500">Recent changes for this variant.</p>{movements.length > 0 ? <div className="mt-4 overflow-x-auto"><table className="w-full min-w-[620px] text-left text-sm"><thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500"><tr><th className="p-3 font-semibold">Time</th><th className="p-3 font-semibold">Type</th><th className="p-3 font-semibold">Delta</th><th className="p-3 font-semibold">Reason</th></tr></thead><tbody>{movements.map((entry) => <tr key={entry.id} className="border-t border-slate-100"><td className="p-3 text-slate-500">{new Date(entry.createdAt).toLocaleString()}</td><td className="p-3 text-slate-700">{entry.type}</td><td className="p-3 font-medium text-slate-900">{entry.onHandDelta}</td><td className="p-3 text-slate-500">{entry.reason}</td></tr>)}</tbody></table></div> : <p className="mt-4 text-sm text-slate-500">No movements recorded for this item.</p>}</section>
          </section>
        )}
      </div>
    </main>
  );
}
