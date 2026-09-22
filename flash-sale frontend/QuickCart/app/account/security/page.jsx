"use client";

import { useEffect } from "react";
import { useAppContext } from "@/context/AppContext";
import AccountSecurityPanel from "@/components/account/AccountSecurityPanel";

export default function AccountSecurityPage() {
  const { authReady, router, userData } = useAppContext();
  useEffect(() => { if (authReady && !userData) router.replace("/login?next=/account/security"); }, [authReady, router, userData]);
  if (!authReady || !userData) return <main className="mx-auto max-w-3xl px-6 py-16 text-sm text-slate-500">Checking account…</main>;
  return <main className="mx-auto max-w-3xl px-6 py-12 md:px-10"><div className="mb-8"><p className="text-xs font-semibold uppercase tracking-[0.2em] text-orange-600">Account / Security</p><h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950">Session security</h1><p className="mt-2 text-sm text-slate-500">Control this browser or revoke every active session owned by your account.</p></div><AccountSecurityPanel /></main>;
}
