"use client";

import { useEffect, useRef, useState } from "react";
import ApiNotice from "@/components/ApiNotice";
import { useAppContext } from "@/context/AppContext";
import { suggestCampaignCode } from "@/lib/campaignCode";
import { AdminPageHeader, adminCard, adminInput, adminPage, adminPrimaryButton } from "@/components/seller/AdminUi";

export default function CampaignsPage() {
  const { request, router } = useAppContext();
  const [form, setForm] = useState({ name: "", startAt: "", endAt: "" });
  const [suffix, setSuffix] = useState("");
  const [timeZone, setTimeZone] = useState("");
  const [customCode, setCustomCode] = useState(null);
  const [codeError, setCodeError] = useState("");
  const [windowError, setWindowError] = useState("");
  const [error, setError] = useState(null);
  const [pending, setPending] = useState(false);
  const codeRef = useRef(null);
  const nameRef = useRef(null);
  const endRef = useRef(null);
  const submittingRef = useRef(false);

  useEffect(() => {
    setSuffix(crypto.randomUUID().slice(0, 8).toUpperCase());
    setTimeZone(Intl.DateTimeFormat().resolvedOptions().timeZone);
  }, []);

  const suggestedCode = suggestCampaignCode(form.name, form.startAt, suffix);
  const code = customCode ?? suggestedCode;
  const isCustomized = customCode !== null;

  useEffect(() => {
    if (isCustomized && !pending) codeRef.current?.focus();
  }, [isCustomized, codeError, pending]);

  const update = (event) => {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
    setWindowError("");
    setError(null);
  };

  const toggleCustomization = () => {
    setCustomCode(isCustomized ? null : suggestedCode);
    setCodeError("");
  };

  const create = async (event) => {
    event.preventDefault();
    if (submittingRef.current) return;
    if (!form.name.trim()) {
      nameRef.current?.focus();
      setError(new Error("Enter a campaign name."));
      return;
    }
    if (!code.trim() || code.trim().toUpperCase().length > 64) {
      setCustomCode(code);
      setCodeError("Enter a campaign code with 1–64 characters.");
      return;
    }

    const startAt = new Date(form.startAt);
    const endAt = new Date(form.endAt);
    if (!Number.isFinite(startAt.getTime()) || !Number.isFinite(endAt.getTime()) || endAt <= startAt) {
      setWindowError("Choose an end time after the start time.");
      endRef.current?.focus();
      return;
    }

    submittingRef.current = true;
    setPending(true);
    setError(null);
    setCodeError("");
    setWindowError("");
    try {
      const response = await request("/api/v1/admin/campaigns", {
        method: "POST",
        body: JSON.stringify({
          code: code.trim().toUpperCase(),
          name: form.name.trim(),
          startAt: startAt.toISOString(),
          endAt: endAt.toISOString(),
        }),
      });
      router.push(`/seller/campaigns/${response.data.id}`);
    } catch (caught) {
      if (caught.code === "CAMPAIGN_CODE_ALREADY_EXISTS") {
        setCustomCode(code);
        setCodeError("This code is already in use. Choose another code, then create your draft again.");
      } else {
        setError(caught);
      }
    } finally {
      submittingRef.current = false;
      setPending(false);
    }
  };

  return (
    <main className={adminPage}>
      <div className="mx-auto max-w-5xl">
        <AdminPageHeader eyebrow="Campaign admin" title="Create flash-sale campaign" description="Give your sale a name and choose when it runs. You'll add a product in the next step." />
        <ApiNotice error={error} className="mt-6" />
        <form onSubmit={create} aria-busy={pending} className={`${adminCard} mt-6 max-w-2xl p-5 md:p-7`}>
          <fieldset disabled={pending} className="min-w-0 space-y-6">
            <div>
              <label htmlFor="campaign-name" className="text-sm font-medium text-slate-700">Campaign name</label>
              <input ref={nameRef} id="campaign-name" required maxLength={200} name="name" value={form.name} onChange={update} placeholder="e.g. Mid Autumn Flash Sale" className={adminInput} />
            </div>

            <div className="rounded-xl border border-slate-200 bg-slate-50/60 p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <label htmlFor="campaign-code" className="text-sm font-medium text-slate-700">Internal campaign code</label>
                <button type="button" onClick={toggleCustomization} disabled={!suffix} className="rounded px-1 py-0.5 text-sm font-medium text-orange-700 transition hover:text-orange-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-orange-500 disabled:opacity-50">
                  {isCustomized ? "Use suggested code" : "Customize"}
                </button>
              </div>
              <input
                ref={codeRef}
                id="campaign-code"
                name="code"
                required
                maxLength={64}
                readOnly={!isCustomized}
                value={code}
                onChange={(event) => { setCustomCode(event.target.value); setCodeError(""); }}
                placeholder="Your code will appear here"
                spellCheck={false}
                autoCapitalize="characters"
                aria-invalid={!!codeError}
                aria-describedby={`campaign-code-help${codeError ? " campaign-code-error" : ""}`}
                className={`${adminInput} font-mono text-xs uppercase sm:text-sm ${isCustomized ? "bg-white" : "text-slate-500"} ${codeError ? "border-red-400" : ""}`}
              />
              <p id="campaign-code-help" className="mt-2 text-xs leading-5 text-slate-500">
                {isCustomized ? "Choose a unique code (up to 64 characters)." : "Suggested from your campaign name and start date, with a short suffix to help avoid duplicates."}
                {" "}Used to identify this campaign. It cannot be changed after creation.
              </p>
              {codeError && <p id="campaign-code-error" role="alert" className="mt-2 text-sm text-red-700">{codeError}</p>}
            </div>

            <div>
              <div className="grid gap-5 sm:grid-cols-2">
                <div>
                  <label htmlFor="campaign-start" className="text-sm font-medium text-slate-700">Start time</label>
                  <input id="campaign-start" required name="startAt" type="datetime-local" value={form.startAt} onChange={update} aria-describedby="campaign-time-help" className={`${adminInput} min-w-0`} />
                </div>
                <div>
                  <label htmlFor="campaign-end" className="text-sm font-medium text-slate-700">End time</label>
                  <input ref={endRef} id="campaign-end" required name="endAt" type="datetime-local" min={form.startAt || undefined} value={form.endAt} onChange={update} aria-invalid={!!windowError} aria-describedby={`campaign-time-help${windowError ? " campaign-time-error" : ""}`} className={`${adminInput} min-w-0`} />
                </div>
              </div>
              <p id="campaign-time-help" className="mt-2 text-xs text-slate-500">Times are shown in your local time zone{timeZone ? ` (${timeZone})` : ""}.</p>
              {windowError && <p id="campaign-time-error" role="alert" className="mt-2 text-sm text-red-700">{windowError}</p>}
            </div>

            <p className="rounded-xl bg-orange-50 px-4 py-3 text-sm leading-6 text-orange-800 ring-1 ring-orange-100">Next: choose a product variant, set its sale price and quantity, then schedule your campaign. Creating a draft does not start the sale.</p>
            <div className="flex justify-end"><button disabled={pending || !suffix} className={`${adminPrimaryButton} w-full sm:w-auto`}>{pending ? "Creating…" : "Create draft"}</button></div>
          </fieldset>
        </form>
      </div>
    </main>
  );
}
