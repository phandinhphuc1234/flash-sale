import Link from "next/link";

export const adminPage = "min-w-0 flex-1 bg-[#f8fafc] p-4 md:p-8 lg:p-10";
export const adminContent = "mx-auto max-w-7xl";
export const adminCard = "rounded-2xl border border-slate-200 bg-white shadow-sm";
export const adminInput = "mt-2 w-full rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-orange-400 focus:bg-white focus:ring-4 focus:ring-orange-100";
export const adminSelect = adminInput;
export const adminPrimaryButton = "rounded-xl bg-orange-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-orange-700 disabled:cursor-wait disabled:opacity-60";
export const adminSecondaryButton = "rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-medium text-slate-600 transition hover:border-orange-200 hover:bg-orange-50 hover:text-orange-700";
export const adminSubtleButton = "rounded-xl border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 transition hover:border-orange-200 hover:bg-orange-50 hover:text-orange-700";

export function AdminPageHeader({ eyebrow, title, description, action }) {
  return (
    <div className="flex flex-col justify-between gap-5 sm:flex-row sm:items-end">
      <div>
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-orange-600">{eyebrow}</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-950">{title}</h1>
        {description && <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">{description}</p>}
      </div>
      {action}
    </div>
  );
}

export function AdminBackLink({ href, children }) {
  return (
    <Link href={href} className="group inline-flex items-center gap-2 text-sm font-medium text-slate-500 transition hover:text-orange-700">
      <span aria-hidden="true" className="transition-transform group-hover:-translate-x-0.5">←</span>
      {children}
    </Link>
  );
}

export function AdminStatus({ children, tone = "slate" }) {
  const tones = {
    green: "bg-emerald-50 text-emerald-700 ring-emerald-100",
    orange: "bg-orange-50 text-orange-700 ring-orange-100",
    red: "bg-red-50 text-red-700 ring-red-100",
    slate: "bg-slate-100 text-slate-600 ring-slate-200",
  };
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${tones[tone] || tones.slate}`}>{children}</span>;
}
