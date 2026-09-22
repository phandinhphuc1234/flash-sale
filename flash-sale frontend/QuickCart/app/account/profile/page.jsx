"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useAppContext } from "@/context/AppContext";
import { accountInitials } from "@/lib/accountProfile";

const emptyProfile = {
  displayName: "Account",
  login: "Account",
  username: "",
  email: "Unavailable",
  fullName: "",
  phone: "",
  address: "",
};

export default function AccountProfilePage() {
  const { accountProfile, authReady, router, updateAccountProfile, userData } = useAppContext();
  const [form, setForm] = useState({ username: "", fullName: "", phone: "", address: "" });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (authReady && !userData) router.replace("/login?next=/account/profile");
  }, [authReady, router, userData]);

  const profile = accountProfile || {
    ...emptyProfile,
    displayName: userData?.login || "Account",
    login: userData?.login || "Account",
  };
  useEffect(() => {
    setForm({
      username: profile.username || "",
      fullName: profile.fullName || "",
      phone: profile.phone || "",
      address: profile.address || "",
    });
  }, [profile.address, profile.fullName, profile.phone, profile.username]);

  if (!authReady || !userData) {
    return <main className="mx-auto max-w-3xl px-6 py-16 text-sm text-slate-500">Checking account…</main>;
  }

  const updateField = (event) => setForm((current) => ({ ...current, [event.target.name]: event.target.value }));
  const submit = async (event) => {
    event.preventDefault();
    setSaving(true);
    try {
      const payload = { ...form };
      // An empty legacy username means "leave username unchanged"; contact fields
      // intentionally remain in the payload so an empty value can clear them.
      if (!payload.username.trim()) delete payload.username;
      await updateAccountProfile(payload);
      toast.success("Profile updated");
    } catch (error) {
      const fieldMessage = error?.errors?.[0]?.message;
      toast.error(fieldMessage || error?.message || "Could not update your profile");
    } finally {
      setSaving(false);
    }
  };

  return (
    <main className="mx-auto max-w-3xl px-6 py-12 md:px-10">
      <div className="flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <Link
            href="/all-products"
            className="group mb-5 inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3.5 py-2 text-sm font-medium text-slate-600 shadow-sm transition hover:border-orange-200 hover:bg-orange-50 hover:text-orange-700"
          >
            <span aria-hidden="true" className="text-base leading-none transition-transform group-hover:-translate-x-0.5">←</span>
            Back to shop
          </Link>
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-orange-600">Account</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950">Profile</h1>
          <p className="mt-2 text-sm text-slate-500">Keep your contact details up to date for checkout and order support.</p>
        </div>
        <Link href="/account/security" className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50">Security</Link>
      </div>

      <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center gap-4 border-b border-slate-100 pb-6">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-slate-950 text-lg font-semibold text-white">{accountInitials(profile)}</span>
          <div>
            <h2 className="text-xl font-semibold text-slate-950">{profile.displayName}</h2>
            <p className="mt-1 text-sm text-slate-500">{profile.email}</p>
          </div>
        </div>

        <form className="mt-6 space-y-5" onSubmit={submit}>
          <div className="grid gap-5 sm:grid-cols-2">
            <label className="block text-sm font-medium text-slate-700">
              Username
              <input name="username" value={form.username} onChange={updateField} maxLength={100} className="mt-2 w-full rounded-xl border border-slate-200 px-4 py-3 font-normal outline-none transition focus:border-orange-500 focus:ring-2 focus:ring-orange-100" placeholder="Choose a username" />
              <span className="mt-1 block text-xs font-normal text-slate-400">Shown in your account menu.</span>
            </label>
            <label className="block text-sm font-medium text-slate-700">
              Email
              <input value={profile.email} readOnly className="mt-2 w-full rounded-xl border border-slate-100 bg-slate-50 px-4 py-3 font-normal text-slate-500" />
              <span className="mt-1 block text-xs font-normal text-slate-400">Email changes require a separate verification flow.</span>
            </label>
          </div>
          <label className="block text-sm font-medium text-slate-700">
            Full name
            <input name="fullName" value={form.fullName} onChange={updateField} maxLength={150} className="mt-2 w-full rounded-xl border border-slate-200 px-4 py-3 font-normal outline-none transition focus:border-orange-500 focus:ring-2 focus:ring-orange-100" placeholder="Your name" />
          </label>
          <label className="block text-sm font-medium text-slate-700">
            Phone
            <input name="phone" value={form.phone} onChange={updateField} maxLength={32} inputMode="tel" className="mt-2 w-full rounded-xl border border-slate-200 px-4 py-3 font-normal outline-none transition focus:border-orange-500 focus:ring-2 focus:ring-orange-100" placeholder="+84 90 123 4567" />
          </label>
          <label className="block text-sm font-medium text-slate-700">
            Address
            <textarea name="address" value={form.address} onChange={updateField} maxLength={500} rows={3} className="mt-2 w-full resize-y rounded-xl border border-slate-200 px-4 py-3 font-normal outline-none transition focus:border-orange-500 focus:ring-2 focus:ring-orange-100" placeholder="Your delivery or contact address" />
          </label>
          <div className="flex items-center justify-end gap-3 border-t border-slate-100 pt-5">
            <Link href="/" className="rounded-xl px-4 py-2.5 text-sm font-medium text-slate-600 hover:bg-slate-50">Cancel</Link>
            <button type="submit" disabled={saving} className="rounded-xl bg-orange-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-orange-700 disabled:cursor-not-allowed disabled:opacity-60">
              {saving ? "Saving…" : "Save changes"}
            </button>
          </div>
        </form>
      </section>
    </main>
  );
}
