"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import ApiNotice from "@/components/ApiNotice";
import { useAppContext } from "@/context/AppContext";
import { AdminPageHeader, adminCard, adminPage } from "@/components/seller/AdminUi";

const actions = [
  { title: "Create product draft", description: "Start a catalog item and add its sellable variant.", href: "/seller/products/new", tone: "bg-orange-600" },
  { title: "Review inventory", description: "Adjust stock and inspect movement history by variant.", href: "/seller/inventory", tone: "bg-slate-800" },
  { title: "Manage campaigns", description: "Attach products and move a campaign through its lifecycle.", href: "/seller/campaigns", tone: "bg-emerald-600" },
];

export default function AdminDashboard() {
  const { request } = useAppContext();
  const [summary, setSummary] = useState({ products: "—", status: "Checking" });
  const [error, setError] = useState(null);

  useEffect(() => {
    request("/api/v1/admin/catalog/products?page=0&size=1")
      .then((response) => setSummary({ products: response.data?.page?.totalElements ?? response.data?.totalElements ?? 0, status: "Operational" }))
      .catch((caught) => { setSummary((current) => ({ ...current, status: "Needs attention" })); setError(caught); });
  }, [request]);

  return (
    <main className={adminPage}>
      <div className="mx-auto max-w-7xl">
        <AdminPageHeader eyebrow="Admin overview" title="Good to see you back." description="A focused workspace for catalog, inventory and flash-sale operations. Customer checkout stays in the storefront." action={<Link href="/" className="inline-flex w-fit items-center rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-medium text-slate-700 shadow-sm transition hover:border-orange-200 hover:bg-orange-50 hover:text-orange-700">View storefront <span className="ml-2">↗</span></Link>} />
        <ApiNotice error={error} className="mt-6" />
        <section className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <div className="rounded-2xl bg-orange-600 p-5 text-white shadow-sm"><p className="text-sm text-orange-100">Catalog products</p><p className="mt-3 text-3xl font-semibold">{summary.products}</p><p className="mt-2 text-xs text-orange-100">Public and draft records</p></div>
          <div className={`${adminCard} p-5`}><p className="text-sm text-slate-500">Operations status</p><p className="mt-3 text-2xl font-semibold text-emerald-600">{summary.status}</p><p className="mt-2 text-xs text-slate-400">Live API contract check</p></div>
          <div className={`${adminCard} p-5`}><p className="text-sm text-slate-500">Role</p><p className="mt-3 text-2xl font-semibold text-slate-950">Administrator</p><p className="mt-2 text-xs text-slate-400">Catalog · Inventory · Campaigns</p></div>
        </section>
        <section className="mt-8"><div><h2 className="text-lg font-semibold text-slate-950">Quick actions</h2><p className="mt-1 text-sm text-slate-500">Jump directly into the workflow you need.</p></div><div className="mt-4 grid gap-4 md:grid-cols-3">{actions.map((action) => <Link key={action.href} href={action.href} className="group rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-orange-200 hover:shadow-md"><span className={`flex h-10 w-10 items-center justify-center rounded-xl text-lg text-white ${action.tone}`}>↗</span><h3 className="mt-5 font-semibold text-slate-950 group-hover:text-orange-700">{action.title}</h3><p className="mt-2 text-sm leading-6 text-slate-500">{action.description}</p><span className="mt-5 inline-flex text-sm font-medium text-orange-600">Open workspace →</span></Link>)}</div></section>
        <section className={`${adminCard} mt-8 p-5 md:p-6`}><p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Operating note</p><h2 className="mt-2 text-lg font-semibold text-slate-950">Admin APIs are intentionally scoped</h2><p className="mt-2 max-w-3xl text-sm leading-6 text-slate-500">Every change goes through the Gateway with the authenticated ROLE_ADMIN token. Order administration is not displayed until the backend exposes a customer-safe admin contract.</p></section>
      </div>
    </main>
  );
}
