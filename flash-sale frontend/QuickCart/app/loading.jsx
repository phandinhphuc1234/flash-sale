export default function GlobalLoading() {
  return (
    <main className="min-h-screen bg-slate-50 px-6 py-12" aria-busy="true" aria-label="Loading page">
      <div className="mx-auto max-w-6xl animate-pulse space-y-6 motion-reduce:animate-none">
        <div className="h-10 w-44 rounded-xl bg-slate-200" />
        <div className="h-64 rounded-3xl bg-slate-200" />
        <div className="grid gap-5 md:grid-cols-3"><div className="h-44 rounded-2xl bg-slate-200" /><div className="h-44 rounded-2xl bg-slate-200" /><div className="h-44 rounded-2xl bg-slate-200" /></div>
        <p className="text-sm text-slate-500">Loading your shopping experience…</p>
      </div>
    </main>
  );
}
