"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useAppContext } from "@/context/AppContext";
import ApiNotice from "@/components/ApiNotice";
import {
  AdminPageHeader,
  AdminStatus,
  adminCard,
  adminPage,
  adminPrimaryButton,
  adminSubtleButton,
} from "@/components/seller/AdminUi";

const statusTone = (status) => ({
  ACTIVE: "green",
  DRAFT: "orange",
  INACTIVE: "red",
  ARCHIVED: "slate",
}[status] || "slate");

const dangerButton = "rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700 transition hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-50";

export default function ProductList() {
  const { request, router } = useAppContext();
  const [products, setProducts] = useState([]);
  const [selectedIds, setSelectedIds] = useState(() => new Set());
  const [error, setError] = useState(null);
  const [feedback, setFeedback] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const [bulkBusy, setBulkBusy] = useState(false);
  const [bulkAction, setBulkAction] = useState(null);

  const load = useCallback(async () => {
    try {
      const response = await request("/api/v1/admin/catalog/products?page=0&size=30");
      const nextProducts = response.data?.data || [];
      setProducts(nextProducts);
      setSelectedIds((current) => {
        const visibleIds = new Set(nextProducts.map((product) => product.id));
        return new Set([...current].filter((id) => visibleIds.has(id)));
      });
    } catch (caught) {
      setError(caught);
    }
  }, [request]);

  useEffect(() => {
    load();
  }, [load]);

  const selectedProducts = useMemo(
    () => products.filter((product) => selectedIds.has(product.id)),
    [products, selectedIds],
  );
  const deactivateCandidates = useMemo(
    () => selectedProducts.filter((product) => product.status === "ACTIVE"),
    [selectedProducts],
  );
  const archiveCandidates = useMemo(
    () => selectedProducts.filter((product) => product.status !== "ARCHIVED"),
    [selectedProducts],
  );
  const toggleProduct = (productId) => {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(productId)) next.delete(productId);
      else next.add(productId);
      return next;
    });
  };

  const lifecycle = async (product, action) => {
    setBusyId(product.id);
    setError(null);
    setFeedback(null);
    try {
      await request(`/api/v1/admin/catalog/products/${product.id}/${action}`, {
        method: "POST",
        headers: {
          "If-Match": String(product.version),
          "Idempotency-Key": crypto.randomUUID(),
        },
      });
      await load();
    } catch (caught) {
      setError(caught);
    } finally {
      setBusyId(null);
    }
  };

  const runBulk = async (action) => {
    const candidates = action === "deactivate" ? deactivateCandidates : archiveCandidates;
    if (!candidates.length) {
      setBulkAction(null);
      return;
    }

    setBulkBusy(true);
    setError(null);
    setFeedback(null);
    try {
      const results = await Promise.allSettled(candidates.map(async (product) => {
        await request(`/api/v1/admin/catalog/products/${product.id}/${action}`, {
          method: "POST",
          headers: {
            "If-Match": String(product.version),
            "Idempotency-Key": crypto.randomUUID(),
          },
        });
        return product.id;
      }));
      const failedIds = new Set(
        results
          .map((result, index) => (result.status === "rejected" ? candidates[index].id : null))
          .filter(Boolean),
      );
      const succeeded = candidates.length - failedIds.size;
      const skipped = selectedProducts.length - candidates.length;
      await load();
      setSelectedIds(failedIds);
      setFeedback({ succeeded, failed: failedIds.size, skipped, action });
      setBulkAction(null);
    } catch (caught) {
      setError(caught);
    } finally {
      setBulkBusy(false);
    }
  };

  return (
    <main className={adminPage}>
      <div className="mx-auto max-w-7xl">
        <AdminPageHeader
          eyebrow="Catalog admin"
          title="Products"
          description="Build, publish and maintain the products available in your storefront."
          action={<button onClick={() => router.push("/seller/products/new")} className={adminPrimaryButton}>New draft</button>}
        />

        <ApiNotice error={error} className="mt-6" />
        {feedback && (
          <div className="mt-6 rounded-2xl border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm text-emerald-800" role="status">
            {feedback.succeeded} product{feedback.succeeded === 1 ? "" : "s"} {feedback.action === "deactivate" ? "hidden from the storefront" : "archived"}.
            {feedback.skipped > 0 && ` ${feedback.skipped} already-ineligible product${feedback.skipped === 1 ? " was" : "s were"} skipped.`}
            {feedback.failed > 0 && ` ${feedback.failed} failed and remain selected for retry.`}
          </div>
        )}

        <div className={`${adminCard} mt-6 overflow-hidden`}>
          <div className="flex flex-col gap-4 border-b border-slate-100 px-5 py-4 md:px-6 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <h2 className="font-semibold text-slate-950">Product catalog</h2>
              <p className="mt-1 text-sm text-slate-500">{products.length} records loaded</p>
            </div>
            <span className="w-fit rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-500">Admin only</span>
          </div>

          {selectedIds.size > 0 && (
            <div className="flex flex-col gap-3 border-b border-orange-100 bg-orange-50/70 px-5 py-4 md:flex-row md:items-center md:justify-between md:px-6">
              <div>
                <p className="text-sm font-semibold text-orange-900">{selectedIds.size} selected</p>
                <p className="mt-1 text-xs text-orange-800/80">Deactivate hides ACTIVE products. Archive is permanent and requires confirmation.</p>
              </div>
              <div className="flex flex-wrap gap-2">
                <button
                  type="button"
                  disabled={bulkBusy || !deactivateCandidates.length}
                  onClick={() => runBulk("deactivate")}
                  className={adminSubtleButton}
                >
                  Hide from shop{deactivateCandidates.length ? ` (${deactivateCandidates.length})` : ""}
                </button>
                <button
                  type="button"
                  disabled={bulkBusy || !archiveCandidates.length}
                  onClick={() => setBulkAction("archive")}
                  className={dangerButton}
                >
                  Archive{archiveCandidates.length ? ` (${archiveCandidates.length})` : ""}
                </button>
                <button type="button" disabled={bulkBusy} onClick={() => setSelectedIds(new Set())} className={adminSubtleButton}>Clear</button>
              </div>
            </div>
          )}

          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="w-12 px-5 py-3 md:px-6" aria-label="Selection" />
                  <th className="px-5 py-3 font-semibold md:px-6">Name</th>
                  <th className="px-5 py-3 font-semibold md:px-6">Code</th>
                  <th className="px-5 py-3 font-semibold md:px-6">Status</th>
                  <th className="px-5 py-3 font-semibold md:px-6">Version</th>
                  <th className="px-5 py-3 font-semibold md:px-6">Actions</th>
                </tr>
              </thead>
              <tbody>
                {products.map((product) => (
                  <tr key={product.id} className="border-t border-slate-100 transition hover:bg-orange-50/30">
                    <td className="px-5 py-4 md:px-6">
                      <input
                        type="checkbox"
                        checked={selectedIds.has(product.id)}
                        onChange={() => toggleProduct(product.id)}
                        aria-label={`Select ${product.name}`}
                        className="h-4 w-4 rounded border-slate-300 text-orange-600 focus:ring-orange-500"
                      />
                    </td>
                    <td className="px-5 py-4 font-medium text-slate-900 md:px-6">{product.name}</td>
                    <td className="px-5 py-4 text-slate-500 md:px-6">{product.code}</td>
                    <td className="px-5 py-4 md:px-6"><AdminStatus tone={statusTone(product.status)}>{product.status}</AdminStatus></td>
                    <td className="px-5 py-4 text-slate-500 md:px-6">v{product.version}</td>
                    <td className="px-5 py-4 md:px-6">
                      <div className="flex flex-wrap gap-2">
                        <button type="button" onClick={() => router.push(`/seller/products/${product.id}`)} className={adminSubtleButton}>Edit</button>
                        {product.status === "DRAFT" && (
                          <button type="button" disabled={busyId === product.id} onClick={() => lifecycle(product, "publish")} className="rounded-xl border border-orange-200 bg-orange-50 px-3 py-2 text-sm font-medium text-orange-700 transition hover:bg-orange-100 disabled:opacity-50">Publish</button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!products.length && <p className="px-6 py-12 text-center text-sm text-slate-500">No products found.</p>}
          </div>
        </div>
      </div>

      {bulkAction === "archive" && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4" role="dialog" aria-modal="true" aria-labelledby="archive-dialog-title">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-red-600">Permanent catalog action</p>
            <h2 id="archive-dialog-title" className="mt-2 text-xl font-semibold text-slate-950">Archive selected products?</h2>
            <p className="mt-3 text-sm leading-6 text-slate-600">Archived products leave the storefront and cannot be edited, published, or reactivated. This does not delete historical order data.</p>
            <div className="mt-6 flex justify-end gap-3">
              <button type="button" disabled={bulkBusy} onClick={() => setBulkAction(null)} className={adminSubtleButton}>Cancel</button>
              <button type="button" disabled={bulkBusy} onClick={() => runBulk("archive")} className={dangerButton}>{bulkBusy ? "Archiving…" : "Archive permanently"}</button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
