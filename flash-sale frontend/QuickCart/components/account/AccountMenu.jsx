"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { accountInitials } from "@/lib/accountProfile";
import { useAppContext } from "@/context/AppContext";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function safeAccountName(profile, userData) {
  const candidates = [profile?.fullName, profile?.displayName, profile?.username, profile?.login, userData?.login];
  return candidates.find((value) => typeof value === "string" && value.trim() && !UUID_PATTERN.test(value.trim()))?.trim() || "Account";
}

function accountEmail(profile, userData) {
  if (typeof profile?.email === "string" && profile.email.trim()) return profile.email.trim();
  const login = typeof userData?.login === "string" ? userData.login.trim() : "";
  return login.includes("@") ? login : "Signed in account";
}

function MenuGlyph({ kind }) {
  const paths = {
    profile: <><circle cx="12" cy="8" r="3.2" /><path d="M5.5 19c.9-3 3.1-4.5 6.5-4.5s5.6 1.5 6.5 4.5" /></>,
    orders: <><path d="M5 7.5h14l-1 11H6L5 7.5Z" /><path d="M9 7.5a3 3 0 0 1 6 0M8 11h.01M16 11h.01" /></>,
    security: <><rect x="5" y="10" width="14" height="10" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v2" /></>,
    operations: <><path d="M4 19h16M6.5 16V8M12 16V5M17.5 16v-6" /><path d="M4 5h4M10 3h4M16 7h4" /></>,
  };
  return <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4">{paths[kind]}</svg>;
}

function MenuLink({ href, kind, children, accent = false, onClick }) {
  return <Link role="menuitem" href={href} onClick={onClick} className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition ${accent ? "font-semibold text-orange-700 hover:bg-orange-50" : "text-slate-700 hover:bg-slate-50"}`}>
    <span className={accent ? "text-orange-600" : "text-slate-400"}><MenuGlyph kind={kind} /></span>
    <span>{children}</span>
  </Link>;
}

export default function AccountMenu({ compact = false }) {
  const { accountProfile, isAdmin, logout, router, userData } = useAppContext();
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const closeOnOutside = (event) => {
      if (!containerRef.current?.contains(event.target)) setOpen(false);
    };
    const closeOnEscape = (event) => { if (event.key === "Escape") setOpen(false); };
    document.addEventListener("mousedown", closeOnOutside);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("mousedown", closeOnOutside);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [open]);

  const name = safeAccountName(accountProfile, userData);
  const email = accountEmail(accountProfile, userData);
  const initials = accountInitials({ displayName: name });

  const signOut = async () => {
    setOpen(false);
    await logout();
    router.push("/login");
  };

  if (!userData) {
    return <button onClick={() => router.push("/login")} className={`rounded-full text-sm font-medium text-slate-700 transition hover:bg-orange-50 hover:text-orange-700 ${compact ? "px-2 py-2" : "px-3 py-2"}`}>
      Login
    </button>;
  }

  return <div ref={containerRef} className="relative">
    <button
      aria-expanded={open}
      aria-haspopup="menu"
      aria-label={`Open account menu for ${name}`}
      onClick={() => setOpen((value) => !value)}
      className={`group flex items-center gap-2 rounded-full border border-transparent transition hover:border-orange-100 hover:bg-orange-50 ${compact ? "p-1" : "px-1.5 py-1"}`}
    >
      <span className="flex h-9 w-9 items-center justify-center rounded-full bg-orange-600 text-xs font-bold text-white shadow-sm ring-2 ring-orange-100">{initials}</span>
      {!compact && <span className="hidden max-w-36 text-left lg:block">
        <span className="block truncate text-sm font-semibold leading-4 text-slate-800">{name}</span>
        <span className="mt-0.5 block truncate text-[11px] leading-3 text-slate-400">{email}</span>
      </span>}
      {!compact && <svg aria-hidden="true" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" className={`h-4 w-4 text-slate-400 transition-transform ${open ? "rotate-180" : ""}`}><path d="m5 7.5 5 5 5-5" strokeLinecap="round" strokeLinejoin="round" /></svg>}
    </button>

    {open && <div role="menu" className="absolute right-0 top-[calc(100%+0.75rem)] z-40 w-72 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-[0_14px_40px_rgba(15,23,42,0.12)]">
      <div className="border-b border-slate-100 bg-slate-50 px-4 py-4 text-slate-900">
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-orange-600 text-sm font-bold text-white ring-2 ring-orange-100">{initials}</span>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">{name}</p>
            <p className="mt-1 truncate text-xs text-slate-500">{email}</p>
          </div>
        </div>
        <span className="mt-4 inline-flex rounded-full bg-orange-100 px-2.5 py-1 text-[11px] font-semibold text-orange-700">{isAdmin ? "ROLE_ADMIN" : "ROLE_USER"}</span>
      </div>
      <div className="p-2">
        <MenuLink href="/account/profile" kind="profile" onClick={() => setOpen(false)}>Profile</MenuLink>
        <MenuLink href="/my-orders" kind="orders" onClick={() => setOpen(false)}>My orders</MenuLink>
        <MenuLink href="/account/security" kind="security" onClick={() => setOpen(false)}>Security</MenuLink>
        {isAdmin && <MenuLink href="/seller" kind="operations" accent onClick={() => setOpen(false)}>Operations console</MenuLink>}
      </div>
      <div className="border-t border-slate-100 p-2">
        <button role="menuitem" onClick={signOut} className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm font-semibold text-red-600 transition hover:bg-red-50">
          <span className="text-red-500"><svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4"><path d="M10 5H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h4M14 16l4-4-4-4M18 12H9" /></svg></span>
          <span>Sign out</span>
        </button>
      </div>
    </div>}
  </div>;
}
