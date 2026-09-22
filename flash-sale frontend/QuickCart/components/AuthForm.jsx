"use client";

import { useState } from "react";
import Link from "next/link";
import { useAppContext } from "@/context/AppContext";
import ApiNotice from "@/components/ApiNotice";

export default function AuthForm({ mode }) {
  const { login, register, router } = useAppContext();
  const [form, setForm] = useState({ email: "", username: "", password: "", login: "", deviceName: "Web browser" });
  const [error, setError] = useState(null);
  const [pending, setPending] = useState(false);
  const isRegister = mode === "register";
  const update = (event) => setForm({ ...form, [event.target.name]: event.target.value });

  const submit = async (event) => {
    event.preventDefault(); setError(null); setPending(true);
    try {
      if (isRegister) { await register({ email: form.email, username: form.username || undefined, password: form.password }); router.push("/login?registered=1"); }
      else { await login({ login: form.login, password: form.password, deviceName: form.deviceName }); router.push("/"); }
    } catch (caught) { setError(caught); } finally { setPending(false); }
  };
  return <main className="min-h-screen bg-gray-50 px-6 py-16"><form onSubmit={submit} className="mx-auto max-w-md space-y-5 rounded-lg border bg-white p-7 shadow-sm">
    <Link href="/" className="text-sm text-orange-600">← Ecommerce</Link>
    <div><h1 className="text-2xl font-medium">{isRegister ? "Create account" : "Welcome back"}</h1><p className="mt-1 text-sm text-gray-500">{isRegister ? "Shopper accounts are created with the standard user role." : "Sign in to reserve a flash-sale item."}</p></div>
    <ApiNotice error={error} />
    {isRegister && <><label className="block text-sm">Email<input required name="email" type="email" value={form.email} onChange={update} className="mt-1 w-full rounded border p-2.5" /></label><label className="block text-sm">Username (optional)<input name="username" maxLength="100" value={form.username} onChange={update} className="mt-1 w-full rounded border p-2.5" /></label></>}
    {!isRegister && <label className="block text-sm">Email or username<input required name="login" value={form.login} onChange={update} className="mt-1 w-full rounded border p-2.5" /></label>}
    <label className="block text-sm">Password<input required name="password" type="password" minLength="12" maxLength="128" value={form.password} onChange={update} className="mt-1 w-full rounded border p-2.5" /></label>
    <button disabled={pending} className="w-full rounded bg-orange-600 py-3 font-medium text-white disabled:opacity-60">{pending ? "Please wait…" : isRegister ? "Create account" : "Login"}</button>
    <p className="text-center text-sm text-gray-600">{isRegister ? "Already have an account?" : "New shopper?"} <Link className="text-orange-600" href={isRegister ? "/login" : "/register"}>{isRegister ? "Login" : "Register"}</Link></p>
  </form></main>;
}
