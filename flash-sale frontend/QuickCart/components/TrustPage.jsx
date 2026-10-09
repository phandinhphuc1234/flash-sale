import Link from "next/link";
import Navbar from "@/components/Navbar";
import Footer from "@/components/Footer";

export default function TrustPage({ eyebrow, title, intro, children }) {
  return (
    <>
      <Navbar />
      <main className="min-h-[65vh] bg-slate-50 px-6 py-12 md:px-16 lg:px-32">
        <div className="mx-auto max-w-4xl rounded-3xl border border-slate-200 bg-white p-7 shadow-sm md:p-10">
          <p className="text-xs font-semibold uppercase tracking-[0.2em] text-orange-600">{eyebrow}</p>
          <h1 className="mt-3 text-3xl font-semibold text-slate-950 md:text-4xl">{title}</h1>
          <p className="mt-4 max-w-2xl leading-7 text-slate-600">{intro}</p>
          <div className="mt-8 space-y-7 text-slate-700">{children}</div>
          <div className="mt-10 flex flex-wrap gap-3">
            <Link href="/" className="rounded-xl border border-slate-200 px-5 py-3 font-medium hover:border-orange-300 hover:text-orange-700">Back home</Link>
            <Link href="/all-products" className="rounded-xl bg-orange-600 px-5 py-3 font-medium text-white hover:bg-orange-700">Browse products</Link>
          </div>
        </div>
      </main>
      <Footer />
    </>
  );
}
