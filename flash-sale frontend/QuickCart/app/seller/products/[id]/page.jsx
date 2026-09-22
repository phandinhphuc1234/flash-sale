"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import ApiNotice from "@/components/ApiNotice";
import { useAppContext } from "@/context/AppContext";
import { AdminBackLink, AdminPageHeader, adminCard, adminInput, adminPage, adminPrimaryButton } from "@/components/seller/AdminUi";

export default function ProductEditor() {
  const { id } = useParams();
  const { request, router } = useAppContext();
  const [product, setProduct] = useState(null);
  const [categories, setCategories] = useState([]);
  const [error, setError] = useState(null);
  const [pending, setPending] = useState(false);
  const [form, setForm] = useState({ name: "", shortDescription: "", description: "", sku: "", variantName: "", basePrice: "", categoryId: "", mediaUrl: "", altText: "" });

  const load = async () => {
    try {
      const [detail, categoryResponse] = await Promise.all([request(`/api/v1/admin/catalog/products/${id}`), request("/api/v1/catalog/categories")]);
      const value = detail.data;
      setProduct(value);
      setCategories(categoryResponse.data?.data || []);
      const variant = value.variants?.[0] || {};
      const category = value.categories?.[0] || {};
      const media = value.media?.[0] || {};
      setForm({ name: value.name || "", shortDescription: value.shortDescription || "", description: value.description || "", sku: variant.sku || "", variantName: variant.name || "", basePrice: variant.basePrice || "", categoryId: category.id || "", mediaUrl: media.url || "", altText: media.altText || "" });
    } catch (caught) {
      setError(caught);
    }
  };

  useEffect(() => { load(); }, [id]);
  const update = (event) => setForm({ ...form, [event.target.name]: event.target.value });

  const save = async (event) => {
    event.preventDefault();
    if (!product) return;
    setError(null);
    setPending(true);
    try {
      const variants = form.sku ? [{ id: product.variants?.[0]?.id || null, sku: form.sku, barcode: product.variants?.[0]?.barcode || null, name: form.variantName, basePrice: Number(form.basePrice), currency: "VND", status: "ACTIVE", sortOrder: 0 }] : [];
      const response = await request(`/api/v1/admin/catalog/products/${id}/composition`, {
        method: "PUT",
        headers: { "If-Match": String(product.version) },
        body: JSON.stringify({ name: form.name, shortDescription: form.shortDescription, description: form.description, variants, categories: form.categoryId ? [{ id: form.categoryId, primary: true, sortOrder: 0 }] : [], media: form.mediaUrl ? [{ id: product.media?.[0]?.id || null, variantId: null, mediaType: "IMAGE", url: form.mediaUrl, altText: form.altText || form.name, sortOrder: 0, status: "ACTIVE" }] : [] }),
      });
      setProduct({ ...product, version: response.data.version });
    } catch (caught) {
      setError(caught);
    } finally {
      setPending(false);
    }
  };

  return (
    <main className={adminPage}>
      <div className="mx-auto max-w-4xl">
        <AdminBackLink href="/seller/product-list">Catalog</AdminBackLink>
        <div className="mt-5"><AdminPageHeader eyebrow="Catalog / Composition" title="Product composition" description="Complete the variants, category and media composition required by the catalog API." /></div>
        <ApiNotice error={error} className="mt-6" />
        {product && <form onSubmit={save} className={`${adminCard} mt-6 p-5 md:p-7`}>
          <div className="grid gap-5 md:grid-cols-2">
            {[['name', 'Name', 'text'], ['shortDescription', 'Short description', 'text'], ['sku', 'Variant SKU', 'text'], ['variantName', 'Variant name', 'text'], ['basePrice', 'Base price (VND)', 'number'], ['mediaUrl', 'Image URL', 'url'], ['altText', 'Image alt text', 'text']].map(([name, label, type]) => (
              <label key={name} className="block text-sm font-medium text-slate-700">{label}<input required={name !== "mediaUrl" && name !== "altText"} name={name} type={type} value={form[name]} onChange={update} className={adminInput} /></label>
            ))}
          </div>
          <label className="mt-5 block text-sm font-medium text-slate-700">Primary category<select value={form.categoryId} name="categoryId" onChange={update} className={adminInput}><option value="">No category</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
          <label className="mt-5 block text-sm font-medium text-slate-700">Description<textarea required name="description" value={form.description} onChange={update} rows="5" className={adminInput} /></label>
          <div className="mt-7 flex justify-end border-t border-slate-100 pt-5"><button disabled={pending} className={adminPrimaryButton}>{pending ? "Saving…" : `Save composition (v${product.version})`}</button></div>
        </form>}
      </div>
    </main>
  );
}
