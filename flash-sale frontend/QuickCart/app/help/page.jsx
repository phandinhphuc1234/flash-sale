import TrustPage from "@/components/TrustPage";

export default function HelpPage() {
  return <TrustPage eyebrow="Customer guide" title="Help with shopping" intro="This portfolio storefront supports catalog browsing, a shopper cart, regular Buy Now orders, Flash Sale reservations, and Stripe test checkout.">
    <section><h2 className="text-xl font-semibold text-slate-900">Regular purchase</h2><p className="mt-2 leading-7">Choose a published product and variant, then add it to Cart or use Buy now. The Order page is the source of truth for price and status.</p></section>
    <section><h2 className="text-xl font-semibold text-slate-900">Flash Sale</h2><p className="mt-2 leading-7">Open Flash Sale, select a live offer, and submit a reservation. Submission is not success by itself; wait for the reservation result before continuing to payment.</p></section>
    <section><h2 className="text-xl font-semibold text-slate-900">Test payments</h2><p className="mt-2 leading-7">This project uses Stripe test mode only. It does not collect or process real money.</p></section>
  </TrustPage>;
}
