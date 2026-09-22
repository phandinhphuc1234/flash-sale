"use client";

import { useState } from "react";
import { useAppContext } from "@/context/AppContext";
import ApiNotice from "@/components/ApiNotice";
import { AdminPageHeader, adminCard, adminInput, adminPage, adminPrimaryButton, adminSecondaryButton } from "@/components/seller/AdminUi";

export default function AddProduct() {
  const { request, router } = useAppContext();
  const [form, setForm] = useState({ code: "", slug: "", name: "", shortDescription: "", description: "" });
  const [error, setError] = useState(null);
  const [pending, setPending] = useState(false);
  const update = (event) => setForm({ ...form, [event.target.name]: event.target.value });

  const submit = async (event) => {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      const response = await request("/api/v1/admin/catalog/products", {
        method: "POST",
        headers: { "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify(form),
      });
      router.push(`/seller/product-list?selected=${response.data.id}`);
    } catch (caught) {
      setError(caught);
    } finally {
      setPending(false);
    }
  };

  return (
    <main className={adminPage}>
      <div className="mx-auto max-w-4xl">
        <AdminPageHeader
          eyebrow="Catalog / New draft"
          title="Create a product draft"
          description="Start with the product identity. Variants, categories and media can be completed in the composition step."
        />
        <ApiNotice error={error} className="mt-6" />
        <form onSubmit={submit} className={`${adminCard} mt-6 p-5 md:p-7`}>
          <div className="grid gap-5 md:grid-cols-2">
            {[['code', 'Product code'], ['slug', 'Slug'], ['name', 'Name'], ['shortDescription', 'Short description']].map(([name, label]) => (
              <label key={name} className="text-sm font-medium text-slate-700">
                {label}
                <input required name={name} value={form[name]} onChange={update} className={adminInput} />
              </label>
            ))}
          </div>
          <label className="mt-5 block text-sm font-medium text-slate-700">
            Description
            <textarea required name="description" rows="6" value={form.description} onChange={update} className={adminInput} />
          </label>
          <div className="mt-7 flex flex-wrap justify-end gap-3 border-t border-slate-100 pt-5">
            <button type="button" onClick={() => router.push('/seller')} className={adminSecondaryButton}>Cancel</button>
            <button disabled={pending} className={adminPrimaryButton}>{pending ? "Creating…" : "Create draft"}</button>
          </div>
        </form>
      </div>
    </main>
  );
}
