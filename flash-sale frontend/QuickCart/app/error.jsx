"use client";

import Link from "next/link";

export default function GlobalError({ reset }) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-50 px-6 py-16">
      <section className="w-full max-w-xl rounded-3xl border border-slate-200 bg-white p-8 text-center shadow-sm">
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-orange-600">Temporary problem</p>
        <h1 className="mt-3 text-3xl font-semibold text-slate-950">We could not finish loading this page</h1>
        <p className="mt-4 text-slate-600">Your account and payment details have not been shown here. Try again or return to a safe page.</p>
        <div className="mt-7 flex flex-wrap justify-center gap-3">
          <button type="button" onClick={reset} className="rounded-xl bg-orange-600 px-5 py-3 font-medium text-white hover:bg-orange-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-orange-500 focus-visible:ring-offset-2">Try again</button>
          <Link href="/" className="rounded-xl border border-slate-200 px-5 py-3 font-medium text-slate-700 hover:border-orange-300">Go home</Link>
        </div>
      </section>
    </main>
  );
}
