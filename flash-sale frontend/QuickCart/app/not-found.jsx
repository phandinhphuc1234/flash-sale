import Link from "next/link";
import Navbar from "@/components/Navbar";
import Footer from "@/components/Footer";

export default function NotFound() {
  return (
    <>
      <Navbar />
      <main className="flex min-h-[65vh] items-center justify-center bg-slate-50 px-6 py-16">
        <section className="max-w-xl text-center">
          <p className="text-sm font-semibold uppercase tracking-[0.25em] text-orange-600">404</p>
          <h1 className="mt-3 text-4xl font-semibold text-slate-950">This page is not on the shelf</h1>
          <p className="mt-4 text-slate-600">The link may be old, or the destination is not part of this storefront.</p>
          <div className="mt-7 flex flex-wrap justify-center gap-3"><Link href="/" className="rounded-xl border border-slate-200 bg-white px-5 py-3 font-medium text-slate-700">Home</Link><Link href="/all-products" className="rounded-xl bg-orange-600 px-5 py-3 font-medium text-white">Shop products</Link></div>
        </section>
      </main>
      <Footer />
    </>
  );
}
