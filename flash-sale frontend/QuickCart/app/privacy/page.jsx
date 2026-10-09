import TrustPage from "@/components/TrustPage";

export default function PrivacyPage() {
  return <TrustPage eyebrow="Privacy" title="How this demo handles data" intro="The storefront stores only the account and commerce data needed to demonstrate authentication, carts, orders, reservations, and test payments.">
    <section><h2 className="text-xl font-semibold text-slate-900">Account and commerce data</h2><p className="mt-2 leading-7">The backend may retain account identifiers, profile details you provide, cart items, orders, reservations, and test payment references in its local databases.</p></section>
    <section><h2 className="text-xl font-semibold text-slate-900">Payment data</h2><p className="mt-2 leading-7">Card entry is hosted by Stripe in test mode. The storefront must not store full card numbers or webhook signing secrets.</p></section>
    <section><h2 className="text-xl font-semibold text-slate-900">Operational data</h2><p className="mt-2 leading-7">Trace identifiers, logs, and metrics may be recorded to diagnose failures. Do not enter real sensitive information into this portfolio environment.</p></section>
  </TrustPage>;
}
