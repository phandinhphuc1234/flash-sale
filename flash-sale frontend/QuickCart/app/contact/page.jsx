import TrustPage from "@/components/TrustPage";

export default function ContactPage() {
  return <TrustPage eyebrow="Project support" title="Contact and issue reporting" intro="This is a personal portfolio project, not a staffed commercial store. There is no 24/7 customer-service team.">
    <section><h2 className="text-xl font-semibold text-slate-900">Report a technical issue</h2><p className="mt-2 leading-7">Share the page, approximate time, and the safe trace identifier shown by the application. Never include passwords, access tokens, card data, or webhook secrets.</p><a className="mt-3 inline-flex font-medium text-orange-600 underline-offset-4 hover:underline" href="https://github.com/phandinhphuc1234/flash-sale/issues" target="_blank" rel="noreferrer">Open the project issue tracker →</a></section>
    <section><h2 className="text-xl font-semibold text-slate-900">Payment notice</h2><p className="mt-2 leading-7">Only Stripe test-mode payments are supported. No real purchase, shipping, refund, or fulfillment support is provided.</p></section>
  </TrustPage>;
}
