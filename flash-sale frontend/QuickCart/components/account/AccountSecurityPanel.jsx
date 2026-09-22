"use client";

import Link from "next/link";
import { useState } from "react";
import { useAppContext } from "@/context/AppContext";

export default function AccountSecurityPanel() {
  const { logout, logoutAll, router } = useAppContext();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [pending, setPending] = useState(false);

  const signOutCurrent = async () => {
    setPending(true);
    try {
      await logout();
      router.push("/login");
    } finally {
      setPending(false);
    }
  };

  const signOutEverywhere = async () => {
    setPending(true);
    try {
      await logoutAll();
      router.push("/login?reason=logout-all");
    } finally {
      setPending(false);
      setConfirmOpen(false);
    }
  };

  return <>
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm md:p-7">
      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Session security</p>
      <h2 className="mt-2 text-xl font-semibold text-slate-950">Manage your sign-in</h2>
      <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">Sign out this browser normally. Use the all-devices option if you no longer trust another browser or device.</p>
      <div className="mt-6 flex flex-wrap gap-3 border-t border-slate-100 pt-5">
        <button disabled={pending} onClick={signOutCurrent} className="rounded-xl bg-slate-950 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-slate-800 disabled:cursor-wait disabled:opacity-50">{pending ? "Signing out…" : "Sign out this device"}</button>
        <button disabled={pending} onClick={() => setConfirmOpen(true)} className="rounded-xl border border-red-200 px-4 py-2.5 text-sm font-semibold text-red-700 transition hover:bg-red-50 disabled:opacity-50">Sign out all devices</button>
        <Link href="/" className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-50">Back to storefront</Link>
      </div>
    </section>
    {confirmOpen && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 p-4" role="presentation">
      <div role="dialog" aria-modal="true" aria-labelledby="logout-all-title" className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-red-600">Security action</p>
        <h2 id="logout-all-title" className="mt-2 text-xl font-semibold text-slate-950">Sign out of all devices?</h2>
        <p className="mt-3 text-sm leading-6 text-slate-600">This ends every active session, including this browser. You will need to sign in again.</p>
        <div className="mt-6 flex justify-end gap-3">
          <button disabled={pending} onClick={() => setConfirmOpen(false)} className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-50">Cancel</button>
          <button disabled={pending} onClick={signOutEverywhere} className="rounded-xl bg-red-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-red-700 disabled:cursor-wait disabled:opacity-50">{pending ? "Signing out…" : "Sign out all devices"}</button>
        </div>
      </div>
    </div>}
  </>;
}
