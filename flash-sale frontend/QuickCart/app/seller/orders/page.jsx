import Link from "next/link";
import { adminCard, adminPage } from "@/components/seller/AdminUi";

export default function AdminOrdersUnavailable() {
  return (
    <main className={adminPage}>
      <div className="mx-auto max-w-5xl">
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-orange-600">Operations / Orders</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950">Order administration is unavailable</h1>
        <section className={`${adminCard} mt-6 max-w-2xl p-6 md:p-7`}>
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-orange-50 text-2xl text-orange-600">i</div>
          <p className="mt-5 text-sm leading-6 text-slate-600">The current Gateway contract only provides customer-scoped order endpoints. This frontend intentionally does not show mock admin orders or call an internal service endpoint.</p>
          <Link href="/seller" className="mt-6 inline-flex rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-medium text-slate-600 transition hover:border-orange-200 hover:bg-orange-50 hover:text-orange-700">Back to overview</Link>
        </section>
      </div>
    </main>
  );
}
