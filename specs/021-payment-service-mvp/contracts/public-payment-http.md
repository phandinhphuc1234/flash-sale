# Public Payment HTTP Contract

**Status**: Draft — ready for approval with Feature 021 plan
**Ingress**: API Gateway only
**Authentication**: Bearer JWT for every endpoint in this document
**Content type**: `application/json`
**Response model**: repository-standard `ApiResponse<T>` and `ApiErrorResponse`

## Security and common rules

- API Gateway authenticates and routes `/api/v1/payments/**`; Payment Service validates JWT trust
  and derives the owner from the authenticated principal. A caller-supplied user ID is ignored/not
  accepted.
- Missing and foreign-owned Payment/Order references both return `404 PAYMENT_NOT_FOUND` with the
  same shape and message class.
- Success and errors return the repository trace header. Bodies follow shared envelope rules and do
  not duplicate trace metadata.
- Responses never contain provider idempotency keys, webhook/provider event IDs, card/customer data,
  provider errors/secrets, or raw internal state.

## Payment representation

```json
{
  "id": "5e6dd7ce-86e2-4ab8-a51a-987809a251a0",
  "orderId": "32d90fcc-25e6-42a4-8c84-f7cf26778188",
  "amount": 250000.0000,
  "currency": "VND",
  "status": "PROCESSING",
  "paymentDeadline": "2026-08-17T15:30:00Z",
  "attemptsUsed": 1,
  "failureReason": null,
  "createdAt": "2026-08-17T15:24:58Z",
  "updatedAt": "2026-08-17T15:25:01Z"
}
```

Public statuses: `PENDING`, `PROCESSING`, `UNKNOWN`, `SUCCEEDED`, `FAILED`, `EXPIRED`.

## GET `/api/v1/payments/{paymentId}`

Returns the authenticated owner's Payment.

| Outcome | Status | Code |
|---|---:|---|
| Found | 200 | Success envelope with Payment representation |
| Missing or foreign | 404 | `PAYMENT_NOT_FOUND` |
| Invalid UUID | 400 | `VALIDATION_FAILED` |
| Missing/invalid authentication | 401 | repository security error contract |

Checkout URL is never returned by a query.

## GET `/api/v1/payments/by-order/{orderId}`

Returns the authenticated owner's Payment for the Order. Outcomes match the Payment-ID query,
including indistinguishable missing/foreign `404`.

## POST `/api/v1/payments/{paymentId}/checkout-sessions`

Creates, resumes, or recovers the one current hosted Checkout attempt.

### Request

- Required header: `Idempotency-Key`, 1–255 case-sensitive printable characters.
- No request body. Amount, currency, owner, and deadline come from the trusted Payment aggregate.
- Reusing the key with a different owner, Payment, operation, or canonical request is a conflict.

### Available response

```json
{
  "data": {
    "paymentId": "5e6dd7ce-86e2-4ab8-a51a-987809a251a0",
    "status": "PROCESSING",
    "checkoutUrl": "https://checkout.stripe.com/c/pay/...",
    "paymentDeadline": "2026-08-17T15:30:00Z"
  }
}
```

The exact outer envelope follows `common-web`. `checkoutUrl` appears only in a `201`/`200` response
where a current open Session was retrieved/created. Every response from this endpoint, including
errors, includes `Cache-Control: no-store`.

### Outcomes

| Outcome | Status | Headers/body |
|---|---:|---|
| New attempt and open Session known | 201 | `Location: /api/v1/payments/{paymentId}`, no-store, URL present |
| Matching replay/resume and open Session known | 200 | no-store, URL present |
| Durable attempt accepted but provider result is ambiguous/recovering | 202 | no-store, optional `Retry-After: 1`, URL absent |
| Missing/foreign Payment | 404 | `PAYMENT_NOT_FOUND` |
| Idempotency key reused for another request | 409 | `PAYMENT_IDEMPOTENCY_CONFLICT` |
| Status cannot accept another Checkout | 409 | `PAYMENT_NOT_PAYABLE` |
| Deadline already passed | 409 | `PAYMENT_DEADLINE_PASSED` |
| Three attempts already allocated | 409 | `PAYMENT_ATTEMPT_LIMIT_REACHED` |
| Different unresolved attempt owns the Payment | 409 | `PAYMENT_CHECKOUT_IN_PROGRESS` |
| Provider definitely unavailable before request submission | 503 | `PAYMENT_PROVIDER_UNAVAILABLE`; never used for ambiguous submission |
| Unexpected internal fault | 500 | repository internal error contract |

Provider decline details and raw Stripe error messages are intentionally not public.

## Cache, redirect, and browser rules

- The Checkout URL is bearer-like and is never persisted, logged, placed in metrics/traces/events,
  returned in a query, or included in an error.
- Success/cancel browser redirects provide navigation only; they cannot mark Payment successful or
  failed. Clients query Payment status after returning.
- Gateway/proxy caches must respect `Cache-Control: no-store`; this header is tested end to end.

## Observability contract

Safe labels include route template, HTTP status class, application error code, and normalized outcome.
Payment/user/order/session IDs may appear only under the repository's safe structured-logging policy,
never as unbounded metric labels. Idempotency keys, URL, JWT, secrets, signature, card/customer data,
and provider body are forbidden.
