# Frontend Integration Guide

Tài liệu này mô tả contract HTTP hiện có của hệ thống Flash Sale để frontend tích hợp mà không
phải suy đoán từ code Java. Phạm vi gồm **47 endpoint**: 38 endpoint dành cho shopper/admin, một
Stripe webhook, một JWKS endpoint và bảy endpoint nội bộ. Cart Service có bốn endpoint shopper;
Notification Service chưa có HTTP API.

> Source of truth cuối cùng vẫn là controller/DTO của service và OpenAPI sinh tại runtime. Tài liệu
> này không tạo contract mới và không thay đổi hành vi backend.

## 1. Kết nối đúng boundary

Frontend chỉ gọi **API Gateway**, không gọi port riêng của microservice.

| Môi trường | Base URL |
|---|---|
| Local Docker Compose | `http://localhost:${GATEWAY_PORT}`; mặc định repo là `http://localhost:8080` |
| Cloud development | `https://api.flashsale123.tech` |

Ví dụ:

```text
GET http://localhost:8080/api/v1/catalog/products?page=0&size=20
GET https://api.flashsale123.tech/api/v1/catalog/products?page=0&size=20
```

Gateway Swagger chỉ bật có chủ đích ở local bằng `API_DOCS_ENABLED=true`:

```text
http://localhost:8080/swagger-ui.html
```

Nếu `infra/docker/.env` đặt `GATEWAY_PORT=18080`, thay `8080` bằng `18080`. Kiểm tra port thật bằng
`docker compose ... ps api-gateway`; không hardcode port riêng của service vào frontend.

## 2. Quy ước dùng chung

### 2.1 Success envelope

Phần lớn API trả về:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {},
  "timestamp": "2026-08-29T03:00:00Z"
}
```

Frontend nên đọc nghiệp vụ từ `data`, không phụ thuộc tuyệt đối vào nội dung `message`.

### 2.2 Error envelope

```json
{
  "success": false,
  "errorCode": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "errors": [
    {
      "field": "quantity",
      "message": "must be greater than 0"
    }
  ],
  "timestamp": "2026-08-29T03:00:00Z"
}
```

Xử lý lỗi theo thứ tự:

1. HTTP status quyết định nhóm lỗi.
2. `errorCode` quyết định hành vi UI cụ thể.
3. `errors[]` hiển thị lỗi cạnh input.
4. `message` dùng làm thông báo dự phòng.

### 2.3 Pagination envelope

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "data": [],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 0,
      "totalPages": 0,
      "hasNext": false
    }
  },
  "timestamp": "2026-08-29T03:00:00Z"
}
```

Lưu ý hai cấp `data`: envelope có `data`, còn page payload cũng có `data` là danh sách.

### 2.4 Header chuẩn

| Header | Khi nào dùng | Quy tắc |
|---|---|---|
| `Authorization` | API có bảo vệ | `Bearer <accessToken>` |
| `Content-Type` | Request có JSON | `application/json` |
| `X-Trace-Id` | Khuyến nghị cho mọi request; bắt buộc ở Product Admin | UUID mới cho mỗi thao tác, tối đa 128 ký tự |
| `Idempotency-Key` | Create/mutation quan trọng | UUID ổn định cho **một ý định nghiệp vụ**; retry phải dùng lại đúng key và payload |
| `If-Match` | Optimistic locking | Product dùng số thô như `0`; Campaign dùng ETag có dấu nháy như `"0"` |
| `Retry-After` | Response `202`, `429` hoặc pending | Chờ số giây do server trả rồi thử lại |

### 2.5 Kiểu dữ liệu

- UUID: chuỗi, ví dụ `b9051292-88ca-45d2-a859-6710d687fbab`.
- `Instant`: ISO-8601 UTC, ví dụ `2026-08-29T04:00:00Z`.
- Tiền: backend dùng decimal; frontend nên giữ dạng string/decimal, không dùng phép toán tiền bằng
  floating point.
- `VND`: ví dụ `179000` nghĩa là 179.000 VND.

### 2.6 TypeScript types khuyến nghị

```ts
export type ApiResponse<T> = {
  success: true;
  code: string;
  message: string;
  data: T;
  timestamp: string;
};

export type FieldError = { field: string; message: string };

export type ApiErrorResponse = {
  success: false;
  errorCode: string;
  message: string;
  errors: FieldError[] | null;
  timestamp: string;
};

export type PageMeta = {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
};

export type PageResponse<T> = { data: T[]; page: PageMeta };
```

## 3. Authentication và quản lý session

### 3.1 Quy tắc frontend

- Access token được trả trong JSON; nên giữ trong memory của ứng dụng.
- Refresh token nằm trong cookie `HttpOnly`, JavaScript không đọc được và không cần đọc.
- Mọi request login/refresh/logout phải dùng `credentials: "include"`.
- Request protected gửi `Authorization: Bearer ...`.
- Khi gặp `401`, chỉ nên để một request thực hiện refresh; các request khác chờ kết quả để tránh
  nhiều lần rotate cùng refresh token.
- Đăng ký công khai luôn tạo `ROLE_USER`. Tuyệt đối không gửi `role`, `roles`, `authority` hoặc
  `authorities` trong payload.

### API-001 — Đăng ký shopper

```http
POST /api/v1/auth/register
Content-Type: application/json
```

Request:

```json
{
  "email": "shopper@example.com",
  "username": "shopper01",
  "password": "StrongPassword123!"
}
```

- `email`: bắt buộc, email hợp lệ, tối đa 320 ký tự.
- `username`: tùy chọn, tối đa 100 ký tự.
- `password`: bắt buộc, 12–128 Unicode code point.

Response `201 Created`:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Account registered",
  "data": {
    "userId": "b9051292-88ca-45d2-a859-6710d687fbab",
    "email": "shopper@example.com",
    "username": "shopper01",
    "role": "ROLE_USER",
    "status": "ACTIVE"
  },
  "timestamp": "2026-08-29T03:00:00Z"
}
```

Lỗi chính: `AUTH_VALIDATION_FAILED` (`400`), `AUTH_ACCOUNT_ALREADY_EXISTS` (`409`).

### API-002 — Đăng nhập

```http
POST /api/v1/auth/login
Content-Type: application/json
```

Request:

```json
{
  "login": "shopper@example.com",
  "password": "StrongPassword123!",
  "deviceName": "Chrome on Windows"
}
```

Response `200 OK` và header `Set-Cookie: refresh_token=...; HttpOnly`:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "tokenType": "Bearer",
    "accessToken": "eyJ...",
    "expiresIn": 900
  },
  "timestamp": "2026-08-29T03:00:00Z"
}
```

Lỗi chính: `AUTH_INVALID_CREDENTIALS` (`401`), `AUTH_TOO_MANY_ATTEMPTS` (`429`, đọc
`Retry-After`), `AUTHENTICATION_UNAVAILABLE` (`503`).

### API-003 — Refresh access token

```http
POST /api/v1/auth/refresh
Cookie: refresh_token=<HttpOnly cookie tự gửi bởi browser>
```

Request body: **không có**.

Response `200 OK` giống payload login và rotate cookie mới:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "tokenType": "Bearer",
    "accessToken": "eyJ...new...",
    "expiresIn": 900
  },
  "timestamp": "2026-08-29T03:15:00Z"
}
```

Lỗi chính: `AUTH_REFRESH_TOKEN_INVALID` (`401`), `AUTH_REFRESH_REUSE_DETECTED` (`401`). Khi reuse
bị phát hiện, xóa auth state và đưa người dùng về màn hình login.

### API-004 — Logout session hiện tại

```http
POST /api/v1/auth/logout
Cookie: refresh_token=<cookie>
```

Request body: không có. Response: `204 No Content`, không có JSON; server xóa cookie.

### API-005 — Logout tất cả thiết bị

```http
POST /api/v1/auth/logout-all
Authorization: Bearer <accessToken>
Cookie: refresh_token=<cookie nếu có>
```

Request body: không có. Response: `204 No Content`.

## 4. Public catalog

### API-008 — Danh sách category

```http
GET /api/v1/catalog/categories?parentId=<optional-uuid>
```

Response `200 OK`:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "data": [
      {
        "id": "3f848bed-2cd0-4f53-8458-7d991e75aed4",
        "parentId": null,
        "slug": "electronics",
        "name": "Electronics",
        "sortOrder": 0
      }
    ]
  },
  "timestamp": "2026-08-29T03:00:00Z"
}
```

### API-009 — Danh sách product

```http
GET /api/v1/catalog/products?categorySlug=electronics&page=0&size=20
```

`categorySlug` là tùy chọn; `page` bắt đầu từ 0.

Response `200 OK`:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "data": [
      {
        "id": "0e9e1ff4-17b9-43f0-971b-88dfdae04aa3",
        "code": "PHONE-001",
        "slug": "flash-phone-001",
        "name": "Flash Phone",
        "shortDescription": "Demo flash-sale product",
        "variants": [
          {
            "id": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
            "sku": "PHONE-001-BLACK",
            "name": "Black",
            "basePrice": "299000",
            "currency": "VND"
          }
        ]
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "timestamp": "2026-08-29T03:00:00Z"
}
```

### API-010 — Chi tiết product theo slug

```http
GET /api/v1/catalog/products/flash-phone-001
```

Response `200 OK`:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "id": "0e9e1ff4-17b9-43f0-971b-88dfdae04aa3",
    "code": "PHONE-001",
    "slug": "flash-phone-001",
    "name": "Flash Phone",
    "shortDescription": "Demo flash-sale product",
    "description": "Long description",
    "variants": [
      {
        "id": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
        "sku": "PHONE-001-BLACK",
        "name": "Black",
        "basePrice": "299000",
        "currency": "VND"
      }
    ],
    "categories": [
      {
        "id": "3f848bed-2cd0-4f53-8458-7d991e75aed4",
        "slug": "electronics",
        "name": "Electronics",
        "primary": true,
        "sortOrder": 0
      }
    ],
    "media": [
      {
        "id": "491ada00-b45b-49c4-a5ae-13012536c674",
        "mediaType": "IMAGE",
        "url": "https://cdn.example.com/phone.jpg",
        "altText": "Flash Phone",
        "sortOrder": 0
      }
    ]
  },
  "timestamp": "2026-08-29T03:00:00Z"
}
```

Lỗi public catalog chính: `CATEGORY_NOT_FOUND`, `PRODUCT_NOT_FOUND` (`404`) và
`INVALID_CATALOG_REQUEST` (`400`).

## 4.1 Cart của shopper

Cart chỉ dành cho shopper đã đăng nhập và phải được gọi qua API Gateway. Cart lưu ý định chọn
variant, còn Product Service vẫn là nguồn dữ liệu hiện tại cho tên, giá, ảnh và trạng thái bán. Mọi
response Cart đều có `Cache-Control: no-store`; không gửi `ownerId` hoặc `cartId` trong request.

### API-041 — Đọc Cart của tôi

```http
GET /api/v1/cart
Authorization: Bearer <accessToken>
X-Trace-Id: <uuid>
```

Response `200 OK`:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "items": [
      {
        "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
        "quantity": 2,
        "detailsAvailable": true,
        "sellable": true,
        "unavailableReason": null,
        "productId": "0e9e1ff4-17b9-43f0-971b-88dfdae04aa3",
        "productSlug": "flash-sale-shirt",
        "productName": "Flash Sale Shirt",
        "variantName": "Black / M",
        "sku": "FSS-BLK-M",
        "basePrice": "299000.0000",
        "currency": "VND",
        "primaryImageUrl": "https://cdn.example.com/products/fss-black-m.jpg",
        "itemVersion": 1,
        "updatedAt": "2026-08-29T00:00:00Z"
      }
    ],
    "distinctItemCount": 1,
    "totalQuantity": 2,
    "cartVersion": 1,
    "updatedAt": "2026-08-29T00:00:00Z"
  },
  "timestamp": "2026-08-29T00:00:00Z"
}
```

Cart chưa tồn tại vẫn trả `200` với `items: []`, `distinctItemCount: 0`, `totalQuantity: 0` và
`updatedAt: null`; request này không tạo row rỗng. Khi Product không truy cập được, item vẫn giữ
`variantId`/`quantity` nhưng có `detailsAvailable=false`, các field Product là `null` và
`unavailableReason=PRODUCT_DETAILS_UNAVAILABLE`. Khi Product xác nhận variant không tồn tại hoặc
không bán được, reason lần lượt là `PRODUCT_NOT_FOUND` hoặc `PRODUCT_NOT_SELLABLE`.

Lỗi chính: `401 UNAUTHENTICATED`, `500 CART_INTERNAL_ERROR`.

### API-042 — Đặt hoặc thay quantity một variant

```http
PUT /api/v1/cart/items/{variantId}
Authorization: Bearer <accessToken>
X-Trace-Id: <uuid>
Content-Type: application/json
```

Request:

```json
{ "quantity": 2 }
```

`variantId` là UUID; `quantity` là số nguyên từ `1` đến `10`. Đây là **absolute replacement**:
gửi lại cùng payload là an toàn và không tạo dòng trùng. Thành công trả `200 OK` với
`ApiResponse<CartItemResponse>` (cùng shape item ở API-041) và header `Cache-Control: no-store`.

| Status | Error code | Frontend action |
|---:|---|---|
| 400 | `CART_VALIDATION_ERROR` | Hiển thị lỗi quantity/UUID/body |
| 401 | `UNAUTHENTICATED` | Refresh session một lần rồi retry |
| 404 | `CART_VARIANT_NOT_FOUND` | Xóa/đánh dấu variant không còn tồn tại |
| 409 | `CART_VARIANT_NOT_SELLABLE` | Hiển thị không thể bán, không tự reserve |
| 503 | `CART_PRODUCT_DEPENDENCY_UNAVAILABLE` | Giữ input, retry có backoff |
| 500 | `CART_INTERNAL_ERROR` | Hiển thị lỗi chung, không lặp vô hạn |

Product được kiểm tra trước khi Cart mutate; lỗi Product không làm thay đổi Cart cũ.

### API-043 — Xóa một item

```http
DELETE /api/v1/cart/items/{variantId}
Authorization: Bearer <accessToken>
X-Trace-Id: <uuid>
```

Response `204 No Content` với body rỗng. Xóa item không tồn tại cũng là thành công (no-op), nên
frontend có thể retry an toàn. Lỗi có thể gặp: `400 CART_VALIDATION_ERROR`, `401 UNAUTHENTICATED`,
`500 CART_INTERNAL_ERROR`.

### API-044 — Xóa toàn bộ Cart

```http
DELETE /api/v1/cart
Authorization: Bearer <accessToken>
X-Trace-Id: <uuid>
```

Response `204 No Content` với body rỗng. Cart rỗng/chưa tồn tại cũng là thành công và chỉ xóa item
của subject trong JWT hiện tại. Lỗi có thể gặp: `401 UNAUTHENTICATED`, `500 CART_INTERNAL_ERROR`.

### Cart flow cho frontend

```text
login -> accessToken
  -> GET /api/v1/cart
  -> PUT /api/v1/cart/items/{variantId} (quantity tuyệt đối)
  -> GET lại để lấy Product display hiện tại
  -> DELETE item hoặc DELETE /api/v1/cart khi cần
  -> khi mua, chuyển sang flow reservation/order hiện có
```

Cart không giữ giá/stock guarantee, không tạo Order/Payment và không reserve Inventory. Mỗi retry
của cùng một thao tác PUT nên giữ cùng ý định/payload; không gửi Cart request trực tiếp tới
`cart-service`.

### API-046 — Checkout toàn bộ Cart thành một Order thường

Đây là public endpoint qua Gateway. Frontend gửi đúng `cartVersion`, `itemVersion`, giá/currency
đã hiển thị và một `Idempotency-Key`; backend vẫn gọi Product để xác nhận giá thật và Inventory để
giữ toàn bộ item theo nguyên tắc all-or-nothing.

```http
POST /api/v1/orders/cart-checkouts
Authorization: Bearer <shopperToken>
Idempotency-Key: <uuid>
X-Trace-Id: <uuid>
Content-Type: application/json
```

Request body:

```json
{
  "cartVersion": 7,
  "items": [
    {
      "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
      "quantity": 2,
      "itemVersion": 6,
      "expectedUnitPrice": 299000.0000,
      "currency": "VND"
    }
  ]
}
```

`items` có 1–20 variant khác nhau, mỗi quantity từ 1–10; tất cả item phải cùng currency. Owner
được lấy từ JWT, không gửi `ownerId` hoặc `cartId`. Thành công trả `201` với cùng shape checkout
response như Buy Now (`purchaseRequestId`, `orderId`, `source=CART`, `PENDING_PAYMENT`, tổng tiền,
`paymentDeadline`). Gửi lại cùng key và cùng body trả kết quả gốc (`200` và
`Idempotency-Replayed: true`), còn dùng lại key cho body khác bị từ chối.

Các lỗi quan trọng:

| Status | Error code | Ý nghĩa |
|---:|---|---|
| 400 | `CART_VALIDATION_ERROR` | body, quantity, version hoặc currency không hợp lệ |
| 401 | `UNAUTHENTICATED` | thiếu/hết JWT |
| 404 | `CART_NOT_FOUND` / `CART_EMPTY` | Cart không tồn tại hoặc không có item |
| 409 | `CART_CHANGED` | Cart/item revision khác snapshot đã gửi |
| 409 | `PRICE_CHANGED` | Product trả giá/currency hiện tại khác giá shopper xác nhận; không tạo Order/Payment |
| 409 | `INSUFFICIENT_STOCK` | thiếu bất kỳ item nào; toàn bộ checkout bị từ chối |
| 503 | `CART_SERVICE_UNAVAILABLE` | không lấy được snapshot Cart |

Sau khi Payment thành công, frontend poll `GET /api/v1/orders/{orderId}` và
`GET /api/v1/payments/{paymentId}`. Cart được cleanup bất đồng bộ: chỉ item có cùng
`variantId + quantity + itemVersion` với snapshot mới bị xóa; mọi edit sau lúc submit được giữ lại.
Payment thất bại/expired không xóa Cart.

### API-047 — Buy Now sản phẩm thường

```http
POST /api/v1/orders/buy-now
Authorization: Bearer <shopperToken>
Idempotency-Key: <uuid>
X-Trace-Id: <uuid>
Content-Type: application/json
```

```json
{
  "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
  "quantity": 1,
  "expectedUnitPrice": 299000.0000,
  "currency": "VND"
}
```

Buy Now không thêm item vào Cart. Sau `201`, tiếp tục bằng API-039 → API-037; Cart vẫn giữ
nguyên. Cùng key/body là replay an toàn.

## 5. Product Admin

Tất cả endpoint phần này cần `Authorization: Bearer ...` có `CATALOG_ADMIN` và `X-Trace-Id`.

### API-011 — Tạo product draft

```http
POST /api/v1/admin/catalog/products
Authorization: Bearer <adminToken>
Idempotency-Key: 942da3b3-9a98-4cf4-94ae-ff281c1e36ab
X-Trace-Id: 79e2c762-f2eb-4694-af5e-141053c72ee4
Content-Type: application/json
```

Request:

```json
{
  "code": "PHONE-001",
  "slug": "flash-phone-001",
  "name": "Flash Phone",
  "shortDescription": "Demo flash-sale product",
  "description": "Long description"
}
```

Response `201 Created`, kèm `Location`:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "id": "0e9e1ff4-17b9-43f0-971b-88dfdae04aa3",
    "status": "DRAFT",
    "version": 0
  },
  "timestamp": "2026-08-29T03:00:00Z"
}
```

### API-012 — Danh sách product cho admin

```http
GET /api/v1/admin/catalog/products?status=DRAFT&q=phone&page=0&size=20
Authorization: Bearer <adminToken>
X-Trace-Id: <uuid>
```

`status`: `DRAFT`, `ACTIVE`, `INACTIVE`, `ARCHIVED`; `q` là tùy chọn.

Response:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "data": [
      {
        "id": "0e9e1ff4-17b9-43f0-971b-88dfdae04aa3",
        "code": "PHONE-001",
        "slug": "flash-phone-001",
        "name": "Flash Phone",
        "status": "DRAFT",
        "publishedAt": null,
        "version": 0
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "timestamp": "2026-08-29T03:00:00Z"
}
```

### API-013 — Chi tiết product cho admin

```http
GET /api/v1/admin/catalog/products/{productId}
Authorization: Bearer <adminToken>
X-Trace-Id: <uuid>
```

Response:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "id": "0e9e1ff4-17b9-43f0-971b-88dfdae04aa3",
    "code": "PHONE-001",
    "slug": "flash-phone-001",
    "name": "Flash Phone",
    "shortDescription": "Demo flash-sale product",
    "description": "Long description",
    "status": "DRAFT",
    "publishedAt": null,
    "version": 0,
    "variants": [],
    "categories": [],
    "media": []
  },
  "timestamp": "2026-08-29T03:00:00Z"
}
```

Khi có dữ liệu, phần tử `variants` gồm `id, sku, barcode, name, basePrice, currency, status,
sortOrder`; `categories` gồm `id, primary, sortOrder`; `media` gồm `id, variantId, mediaType, url,
altText, sortOrder, status`.

### API-014 — Thay toàn bộ composition

```http
PUT /api/v1/admin/catalog/products/{productId}/composition
Authorization: Bearer <adminToken>
If-Match: 0
X-Trace-Id: <uuid>
Content-Type: application/json
```

Request:

```json
{
  "name": "Flash Phone",
  "shortDescription": "Demo flash-sale product",
  "description": "Long description",
  "variants": [
    {
      "id": null,
      "sku": "PHONE-001-BLACK",
      "barcode": null,
      "name": "Black",
      "basePrice": 299000,
      "currency": "VND",
      "status": "ACTIVE",
      "sortOrder": 0
    }
  ],
  "categories": [
    {
      "id": "3f848bed-2cd0-4f53-8458-7d991e75aed4",
      "primary": true,
      "sortOrder": 0
    }
  ],
  "media": [
    {
      "id": null,
      "variantId": null,
      "mediaType": "IMAGE",
      "url": "https://cdn.example.com/phone.jpg",
      "altText": "Flash Phone",
      "sortOrder": 0,
      "status": "ACTIVE"
    }
  ]
}
```

Đây là thao tác **replace**, không phải append. Mảng bị bỏ trống/null được backend chuẩn hóa thành
mảng rỗng.

Response:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "id": "0e9e1ff4-17b9-43f0-971b-88dfdae04aa3",
    "version": 1
  },
  "timestamp": "2026-08-29T03:00:00Z"
}
```

### API-015 / API-016 / API-017 — Publish, deactivate, archive

```http
POST /api/v1/admin/catalog/products/{productId}/publish
POST /api/v1/admin/catalog/products/{productId}/deactivate
POST /api/v1/admin/catalog/products/{productId}/archive
Authorization: Bearer <adminToken>
If-Match: 1
Idempotency-Key: <uuid>
X-Trace-Id: <uuid>
```

Request body: không có.

Response chung:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "id": "0e9e1ff4-17b9-43f0-971b-88dfdae04aa3",
    "status": "ACTIVE",
    "publishedAt": "2026-08-29T03:10:00Z",
    "version": 2
  },
  "timestamp": "2026-08-29T03:10:00Z"
}
```

`status` tương ứng thao tác. Sau mỗi mutation phải lưu `version` mới cho `If-Match` kế tiếp.

Lỗi Product Admin quan trọng: `PRODUCT_NOT_FOUND` (`404`), `DUPLICATE_PRODUCT_CODE`,
`DUPLICATE_PRODUCT_SLUG`, `DUPLICATE_VARIANT_SKU`, `DUPLICATE_BARCODE`,
`IDEMPOTENCY_KEY_REUSED`, `STALE_PRODUCT_VERSION` (`409`), `INVALID_ADMIN_REQUEST` (`400`).

## 6. Inventory Admin

Tất cả endpoint cần `INVENTORY_ADMIN`.

### API-027 — Đọc tồn kho variant

```http
GET /api/v1/admin/inventory/{variantId}
Authorization: Bearer <adminToken>
```

Response:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
    "skuSnapshot": "PHONE-001-BLACK",
    "onHandQuantity": 100,
    "campaignAllocatedQuantity": 20,
    "availableQuantity": 80
  },
  "timestamp": "2026-08-29T03:00:00Z"
}
```

### API-028 — Điều chỉnh tồn kho

```http
POST /api/v1/admin/inventory/{variantId}/adjustments
Authorization: Bearer <adminToken>
Content-Type: application/json
```

Request:

```json
{
  "requestId": "20f19ea7-4e1c-4611-9b96-8f73d574ac29",
  "type": "INCREASE",
  "quantity": 100,
  "reason": "Initial stock for campaign"
}
```

- `requestId` là idempotency identity.
- `type`: `INCREASE` hoặc `DECREASE`.
- `quantity`: số nguyên dương.

Response giống API-027 với tồn kho mới.

### API-029 — Lịch sử movement

```http
GET /api/v1/admin/inventory/{variantId}/movements?page=0&size=20
Authorization: Bearer <adminToken>
```

Response:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "data": [
      {
        "id": "c86cecad-e116-4ef0-b5f4-ae2d4ca09688",
        "requestId": "20f19ea7-4e1c-4611-9b96-8f73d574ac29",
        "allocationId": null,
        "type": "INCREASE",
        "onHandDelta": 100,
        "allocatedDelta": 0,
        "onHandAfter": 100,
        "allocatedAfter": 0,
        "reason": "Initial stock for campaign",
        "createdAt": "2026-08-29T03:00:00Z"
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "timestamp": "2026-08-29T03:00:00Z"
}
```

Lỗi chính: `INVENTORY_NOT_FOUND` (`404`), `INVENTORY_INSUFFICIENT_STOCK`,
`INVENTORY_OPERATION_REJECTED` (`409`), `VALIDATION_ERROR` (`400`).

## 7. Campaign Admin

Tất cả endpoint cần authority/scope Campaign Admin. Campaign dùng ETag dạng **có dấu nháy**:
`If-Match: "0"`.

### Campaign response dùng chung

```json
{
  "id": "c266db71-091a-4306-8a5d-e088662a21a5",
  "code": "FLASH-AUG-001",
  "name": "August Flash Sale",
  "status": "DRAFT",
  "startAt": "2026-08-29T04:00:00Z",
  "endAt": "2026-08-29T05:00:00Z",
  "scheduledAt": null,
  "activatedAt": null,
  "endedAt": null,
  "version": 0,
  "item": null,
  "createdAt": "2026-08-29T03:00:00Z",
  "updatedAt": "2026-08-29T03:00:00Z"
}
```

Khi đã có item:

```json
{
  "productId": "0e9e1ff4-17b9-43f0-971b-88dfdae04aa3",
  "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
  "inventoryAllocationId": "9867c059-392a-4ef0-9366-ce6e10787aea",
  "variantSku": "PHONE-001-BLACK",
  "basePrice": 299000,
  "currency": "VND",
  "campaignPrice": 179000,
  "requestedQuantity": 20,
  "allocatedQuantity": 20,
  "purchaseLimitPerUser": 1
}
```

### API-019 — Tạo campaign draft

```http
POST /api/v1/admin/campaigns
Authorization: Bearer <adminToken>
Content-Type: application/json
```

```json
{
  "code": "FLASH-AUG-001",
  "name": "August Flash Sale",
  "startAt": "2026-08-29T04:00:00Z",
  "endAt": "2026-08-29T05:00:00Z"
}
```

Response `201 Created`: success envelope chứa Campaign response, header `Location` và `ETag: "0"`.

### API-020 — Sửa metadata draft

```http
PATCH /api/v1/admin/campaigns/{campaignId}
Authorization: Bearer <adminToken>
If-Match: "0"
Content-Type: application/json
```

```json
{
  "name": "August Flash Sale Updated",
  "startAt": "2026-08-29T04:30:00Z",
  "endAt": "2026-08-29T05:30:00Z"
}
```

Response `200`: success envelope chứa Campaign response và ETag/version mới.

### API-021 — Gắn một campaign item

```http
PUT /api/v1/admin/campaigns/{campaignId}/item
Authorization: Bearer <adminToken>
If-Match: "1"
Content-Type: application/json
```

```json
{
  "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
  "campaignPrice": 179000,
  "requestedQuantity": 20,
  "purchaseLimitPerUser": 1
}
```

Response `200`: success envelope chứa Campaign response có `item`, kèm ETag mới.

### API-022 — Đọc campaign admin

```http
GET /api/v1/admin/campaigns/{campaignId}
Authorization: Bearer <adminToken>
```

Request body: không có. Response `200`: Campaign response dùng chung, kèm ETag hiện tại.

### API-023 — Schedule campaign

```http
POST /api/v1/admin/campaigns/{campaignId}/schedule
Authorization: Bearer <adminToken>
If-Match: "2"
Idempotency-Key: <uuid>
X-Trace-Id: <uuid>
Content-Type: application/json
```

Request:

```json
{}
```

Response `200`: Campaign response. Backend sẽ validate Product, allocate Inventory và lưu durable
schedule operation; frontend không được tự gọi internal Product/Inventory API.

### API-024 — Kích hoạt thủ công

```http
POST /api/v1/admin/campaigns/{campaignId}/activate
Authorization: Bearer <adminToken>
If-Match: "3"
X-Trace-Id: <uuid>
Content-Type: application/json
```

Request:

```json
{}
```

Response `200`: Campaign response có `status: "ACTIVE"`. Đây là đường recovery/manual; luồng bình
thường kích hoạt theo lịch.

### API-025 — Requeue outbox event lỗi terminal

```http
POST /api/v1/admin/campaigns/{campaignId}/outbox-events/{eventId}/requeue
Authorization: Bearer <adminToken>
Content-Type: application/json
```

Request:

```json
{}
```

Response:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "eventId": "e7370f52-ab3c-4619-9dfe-c8101444c3cf",
    "campaignId": "c266db71-091a-4306-8a5d-e088662a21a5",
    "publishStatus": "PENDING",
    "retryCount": 3,
    "requeueCount": 1
  },
  "timestamp": "2026-08-29T03:00:00Z"
}
```

Lỗi Campaign chính: validation (`400`); not found (`404`); code/version/idempotency/status/allocation
conflict (`409`); Product/Inventory unavailable (`503`).

## 8. Shopper flash-sale flow

```text
Login
  -> browse Product
  -> biết campaignId + variantId
  -> reserve (202)
  -> poll Reservation
  -> Order được tạo bất đồng bộ
  -> poll Orders và tìm theo purchaseRequestId/reservationId
  -> poll Payment by orderId
  -> tạo Stripe Checkout Session
  -> redirect sang Stripe
  -> Stripe webhook gọi backend
  -> poll Payment SUCCEEDED
  -> poll Order CONFIRMED
  -> poll Reservation CONFIRMED
```

### API-034 — Reserve quota

```http
POST /api/v1/flash-sales/{campaignId}/reservations
Authorization: Bearer <shopperToken>
Idempotency-Key: <uuid>
X-Trace-Id: <uuid>
Content-Type: application/json
```

Request:

```json
{
  "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
  "quantity": 1
}
```

Không gửi `userId`, giá hoặc currency; backend lấy identity từ JWT và giá từ campaign projection.

Response `202 Accepted`, `Location` trỏ tới reservation:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "purchaseRequestId": "97898af8-ec3a-4621-bf59-6be0eac8b748",
    "reservationId": "2613642f-e2d5-4ca9-9f47-46fb50a30788",
    "campaignId": "c266db71-091a-4306-8a5d-e088662a21a5",
    "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
    "quantity": 1,
    "status": "RESERVED",
    "expiresAt": "2026-08-29T04:10:00Z"
  },
  "timestamp": "2026-08-29T04:00:00Z"
}
```

Retry mạng phải dùng lại cùng `Idempotency-Key` và payload. Lỗi chính: campaign không active,
variant không hợp lệ, sold out, purchase limit, idempotency conflict (`409`); Redis/projection không
sẵn sàng (`503`); acceptance pending có thể kèm `Retry-After: 1`.

### API-033 — Đọc reservation của chính mình

```http
GET /api/v1/flash-sales/reservations/{reservationId}
Authorization: Bearer <shopperToken>
```

Response:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "purchaseRequestId": "97898af8-ec3a-4621-bf59-6be0eac8b748",
    "reservationId": "2613642f-e2d5-4ca9-9f47-46fb50a30788",
    "campaignId": "c266db71-091a-4306-8a5d-e088662a21a5",
    "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
    "sku": "PHONE-001-BLACK",
    "unitPrice": 179000,
    "currency": "VND",
    "quantity": 1,
    "status": "RESERVED",
    "acceptedAt": "2026-08-29T04:00:00Z",
    "expiresAt": "2026-08-29T04:10:00Z"
  },
  "timestamp": "2026-08-29T04:00:01Z"
}
```

Status reservation: `RESERVED`, `CONFIRMED`, `RELEASED`, `EXPIRED`. API owner-scoped; reservation
không thuộc user hiện tại cũng có thể được che thành `404`.

## 9. Order

Order thường được tạo bằng API-046/API-047. Flash Sale vẫn giữ flow reservation Kafka cũ; frontend
không gửi internal service request hoặc tự tạo Order bằng database.

### API-036 — Danh sách order của user

```http
GET /api/v1/orders?page=0&size=20
Authorization: Bearer <shopperToken>
```

Response:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "data": [
      {
        "id": "d1f68896-91ab-471e-985d-36aaf16739cb",
        "orderNumber": "FS-20260829-000001",
        "status": "PENDING_PAYMENT",
        "currency": "VND",
        "totalAmount": 179000,
        "reservationExpiresAt": "2026-08-29T04:10:00Z",
        "createdAt": "2026-08-29T04:00:02Z"
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "timestamp": "2026-08-29T04:00:03Z"
}
```

Sau reserve, poll danh sách này rồi gọi detail để match `purchaseRequestId`/`reservationId`.

### API-035 — Chi tiết order của user

```http
GET /api/v1/orders/{orderId}
Authorization: Bearer <shopperToken>
```

Response:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "id": "d1f68896-91ab-471e-985d-36aaf16739cb",
    "orderNumber": "FS-20260829-000001",
    "purchaseRequestId": "97898af8-ec3a-4621-bf59-6be0eac8b748",
    "reservationId": "2613642f-e2d5-4ca9-9f47-46fb50a30788",
    "campaignId": "c266db71-091a-4306-8a5d-e088662a21a5",
    "status": "PENDING_PAYMENT",
    "currency": "VND",
    "subtotalAmount": 179000,
    "totalAmount": 179000,
    "acceptedAt": "2026-08-29T04:00:00Z",
    "reservationExpiresAt": "2026-08-29T04:10:00Z",
    "items": [
      {
        "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
        "quantity": 1,
        "unitPrice": 179000,
        "lineAmount": 179000
      }
    ],
    "createdAt": "2026-08-29T04:00:02Z",
    "updatedAt": "2026-08-29T04:00:02Z"
  },
  "timestamp": "2026-08-29T04:00:03Z"
}
```

Status order: `PENDING_PAYMENT`, `CONFIRMED`, `CANCELLED`, `EXPIRED`.

## 10. Payment và Stripe Checkout

Payment endpoints chỉ tồn tại khi các Payment runtime flags liên quan được bật. Frontend không gọi
Stripe webhook và không được giữ provider secret.

### API-039 — Tìm Payment theo Order

```http
GET /api/v1/payments/by-order/{orderId}
Authorization: Bearer <shopperToken>
```

Response:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "id": "03b10f2e-44f1-4079-8f35-b615119c18a9",
    "orderId": "d1f68896-91ab-471e-985d-36aaf16739cb",
    "amount": 179000,
    "currency": "VND",
    "status": "PENDING",
    "paymentDeadline": "2026-08-29T04:10:00Z",
    "attemptsUsed": 0,
    "failureReason": null,
    "createdAt": "2026-08-29T04:00:03Z",
    "updatedAt": "2026-08-29T04:00:03Z"
  },
  "timestamp": "2026-08-29T04:00:04Z"
}
```

Payment được tạo bất đồng bộ từ Order; `404` ngay sau khi Order xuất hiện có thể là eventual
consistency, hãy poll có giới hạn.

### API-038 — Đọc Payment theo paymentId

```http
GET /api/v1/payments/{paymentId}
Authorization: Bearer <shopperToken>
```

Response giống API-039. Status: `PENDING`, `PROCESSING`, `UNKNOWN`, `SUCCEEDED`, `FAILED`,
`EXPIRED`. `failureReason` có thể là `PAYMENT_DEADLINE_EXPIRED`,
`CHECKOUT_ATTEMPT_LIMIT_REACHED`, `PROVIDER_TERMINAL_FAILURE` hoặc `null`.

### API-037 — Tạo/replay Checkout Session

```http
POST /api/v1/payments/{paymentId}/checkout-sessions
Authorization: Bearer <shopperToken>
Idempotency-Key: <uuid>
X-Trace-Id: <uuid>
```

Request body: **không có**; đừng gửi `{}`.

Response `201 Created` lần đầu, `200 OK` khi replay, hoặc `202 Accepted` khi đang recovery:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "paymentId": "03b10f2e-44f1-4079-8f35-b615119c18a9",
    "status": "PROCESSING",
    "checkoutUrl": "https://checkout.stripe.com/c/pay/cs_test_...",
    "paymentDeadline": "2026-08-29T04:10:00Z"
  },
  "timestamp": "2026-08-29T04:00:05Z"
}
```

Không log/persist `checkoutUrl`; chuyển browser tới URL ngay. Nếu `202`, đọc `Retry-After` rồi gọi
lại với cùng idempotency key.

Lỗi Payment chính: not found (`404`); idempotency conflict, not payable, deadline expired, attempt
limit, checkout in progress (`409`); Stripe/provider unavailable (`503`).

### Sau khi Stripe redirect về frontend

Local URL hiện tại:

```text
http://localhost:3000/payments/success
http://localhost:3000/payments/cancel
```

Redirect success **không phải** bằng chứng thanh toán đã durable. Trang success phải poll:

1. Payment đến `SUCCEEDED`.
2. Order đến `CONFIRMED`.
3. Reservation đến `CONFIRMED`.

Stripe gọi webhook backend, backend phát Kafka Payment event, Order Saga xử lý rồi phát command
confirm/release Reservation. Vì vậy các trạng thái có thể cập nhật lệch nhau vài giây.

## 11. Luồng admin chuẩn để tạo một đợt sale

```text
Admin login
  -> POST Product draft
  -> PUT Product composition
  -> POST Product publish
  -> POST Inventory adjustment
  -> POST Campaign draft
  -> PUT Campaign item
  -> POST Campaign schedule
  -> chờ ACTIVE
  -> shopper mới có thể reserve
```

Quy tắc version quan trọng:

- Product: lấy `data.version`, gửi `If-Match: 1` (không có dấu nháy).
- Campaign: ưu tiên lấy header `ETag`, gửi nguyên giá trị `If-Match: "1"`.
- Nếu nhận version conflict, fetch resource mới, hiển thị dữ liệu mới rồi cho admin thao tác lại;
  không tự tăng version ở frontend.

## 12. Endpoint không dành cho frontend

Các endpoint sau được tài liệu hóa để frontend biết **không được gọi**.

### API-006 — JWKS public key publication

```http
GET /.well-known/jwks.json
```

Response trực tiếp theo chuẩn JWKS, không dùng ApiResponse:

```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "local-dev-20260726",
      "use": "sig",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

Đây là public key để Gateway/services verify JWT, không phải user profile API.

### API-007 — OAuth2 client credentials nội bộ

```http
POST /oauth2/token
Authorization: Basic base64(clientId:clientSecret)
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&scope=<approved-scope>
```

Response OAuth chuẩn:

```json
{
  "access_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 300,
  "scope": "campaign.snapshot.read"
}
```

Client secret chỉ ở backend/Kubernetes Secret, tuyệt đối không đưa vào frontend bundle.

### API-018 — Product campaign validation nội bộ

```http
POST /internal/v1/catalog/variants/campaign-validation
Authorization: Bearer <serviceToken>
X-Trace-Id: <traceId>
Content-Type: application/json

{"variantId":"711ffdce-0dfa-4b66-ad25-4e247037f3ec"}
```

Response trực tiếp, không có envelope:

```json
{
  "productId": "0e9e1ff4-17b9-43f0-971b-88dfdae04aa3",
  "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
  "sku": "PHONE-001-BLACK",
  "productStatus": "ACTIVE",
  "variantStatus": "ACTIVE",
  "sellable": true,
  "basePrice": 299000,
  "currency": "VND"
}
```

### API-045 — Product display batch nội bộ cho Cart

Cart gọi endpoint này bằng OAuth2 client-credentials; frontend tuyệt đối không gọi trực tiếp.

```http
POST /internal/v1/catalog/variants/display-details
Authorization: Bearer <cartServiceToken>
X-Trace-Id: <traceId>
Content-Type: application/json

{
  "variantIds": [
    "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
    "06a5de75-f305-49d1-b11e-5ef372bf20d3"
  ]
}
```

Response `200 OK` dùng `ApiResponse` và trả đúng thứ tự các variant duy nhất:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "variants": [
      {
        "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
        "found": true,
        "sellable": true,
        "productId": "0e9e1ff4-17b9-43f0-971b-88dfdae04aa3",
        "productSlug": "flash-sale-shirt",
        "productName": "Flash Sale Shirt",
        "variantName": "Black / M",
        "sku": "FSS-BLK-M",
        "basePrice": "299000.0000",
        "currency": "VND",
        "primaryImageUrl": "https://cdn.example.com/products/fss-black-m.jpg"
      },
      {
        "variantId": "06a5de75-f305-49d1-b11e-5ef372bf20d3",
        "found": false,
        "sellable": false,
        "productId": null,
        "productSlug": null,
        "productName": null,
        "variantName": null,
        "sku": null,
        "basePrice": null,
        "currency": null,
        "primaryImageUrl": null
      }
    ]
  },
  "timestamp": "2026-08-29T03:00:00Z"
}
```

Required trust: subject `cart-service`, audience `flash-sale-internal-api`, scope
`catalog.variant-display.read`. Wrong/missing token returns `401`/`403`; malformed input returns
`400`. The endpoint is not routed by Gateway and carries no Cart owner, quantity, stock, campaign,
order, or payment data.

### API-026 — Campaign snapshot nội bộ

```http
GET /internal/v1/campaigns/{campaignId}/snapshot
Authorization: Bearer <flashsale-service-token>
```

Response trực tiếp:

```json
{
  "campaignId": "c266db71-091a-4306-8a5d-e088662a21a5",
  "campaignCode": "FLASH-AUG-001",
  "status": "ACTIVE",
  "startAt": "2026-08-29T04:00:00Z",
  "endAt": "2026-08-29T05:00:00Z",
  "aggregateVersion": 4,
  "item": {
    "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
    "inventoryAllocationId": "9867c059-392a-4ef0-9366-ce6e10787aea",
    "variantSku": "PHONE-001-BLACK",
    "campaignPrice": 179000,
    "currency": "VND",
    "allocatedQuantity": 20,
    "purchaseLimitPerUser": 1
  }
}
```

### API-030 — Allocate campaign stock nội bộ

```http
POST /internal/v1/campaign-stock-allocations
Authorization: Bearer <serviceToken>
Content-Type: application/json
```

```json
{
  "requestId": "9867c059-392a-4ef0-9366-ce6e10787aea",
  "campaignId": "c266db71-091a-4306-8a5d-e088662a21a5",
  "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
  "quantity": 20,
  "reason": "Campaign scheduling"
}
```

### API-031 — Release allocation nội bộ

```http
POST /internal/v1/campaign-stock-allocations/{requestId}/release
Authorization: Bearer <serviceToken>
Content-Type: application/json
```

```json
{
  "reason": "Campaign cancelled"
}
```

### API-032 — Reconcile allocation nội bộ

```http
POST /internal/v1/campaign-stock-allocations/{requestId}/reconcile
Authorization: Bearer <serviceToken>
Content-Type: application/json
```

```json
{
  "soldQuantity": 18,
  "returnedQuantity": 2,
  "reason": "Campaign ended"
}
```

Ba Inventory internal API trả success envelope có payload chung:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "id": "aa4a12e4-cb86-44e4-9e6d-9b169543b786",
    "requestId": "9867c059-392a-4ef0-9366-ce6e10787aea",
    "campaignId": "c266db71-091a-4306-8a5d-e088662a21a5",
    "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
    "allocatedQuantity": 20,
    "soldQuantity": 18,
    "returnedQuantity": 2,
    "status": "RECONCILED"
  },
  "timestamp": "2026-08-29T05:01:00Z"
}
```

Status allocation: `ACTIVE`, `RELEASED`, `RECONCILED`.

### API-040 — Stripe webhook

```http
POST /webhooks/v1/payments/stripe
Stripe-Signature: t=...,v1=...
Content-Type: application/json
```

Request là raw Stripe event. Frontend không thể tự tạo signature hợp lệ. Backend trả:

- `204 No Content`: event mới hoặc duplicate hợp lệ đã được acknowledge.
- `400`: payload/signature không hợp lệ.
- `503`: chưa lưu được durable provider receipt.
- `500`: lỗi xử lý không mong đợi.

## 13. Màn hình frontend và API tương ứng

| Màn hình | API chính |
|---|---|
| Register | API-001 |
| Login/session | API-002 đến API-005 |
| Home/catalog | API-008, API-009 |
| Product detail | API-010 |
| My cart | API-041 đến API-044 |
| Flash-sale detail | API-010 + campaign data còn thiếu public API |
| Reserve result | API-034, API-033 |
| My orders | API-036, API-035 |
| Checkout thường | API-046 hoặc API-047 -> API-039 -> API-037 |
| Checkout Flash Sale | API-039, API-037 |
| Payment result | API-038, API-035, API-033 |
| Admin product | API-011 đến API-017 |
| Admin inventory | API-027 đến API-029 |
| Admin campaign | API-019 đến API-025 |

## 14. Polling và timeout khuyến nghị

Không poll vô hạn. Một cấu hình frontend ban đầu hợp lý:

```ts
const POLL_DELAYS_MS = [500, 1000, 1500, 2000, 3000, 5000];
```

- Order sau reservation: tối đa khoảng 30 giây.
- Payment sau Order: tối đa khoảng 30 giây.
- Sau Stripe: tối đa khoảng 60 giây để Payment/Order/Reservation hội tụ.
- Tôn trọng `Retry-After` nếu có.
- Hủy polling khi user rời trang bằng `AbortController`.
- Nếu hết budget, hiển thị “đang xử lý” và cho phép refresh trạng thái; không tự kết luận thất bại.

## 15. Fetch client mẫu

```ts
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: ApiErrorResponse,
  ) {
    super(body.message);
  }
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  accessToken?: string,
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  headers.set("X-Trace-Id", crypto.randomUUID());

  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    credentials: "include",
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const body = await response.json();
  if (!response.ok) {
    throw new ApiError(response.status, body as ApiErrorResponse);
  }
  return body as T;
}
```

Ví dụ reserve:

```ts
const idempotencyKey = crypto.randomUUID();

const result = await apiFetch<ApiResponse<ReservationAccepted>>(
  `/api/v1/flash-sales/${campaignId}/reservations`,
  {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({ variantId, quantity: 1 }),
  },
  accessToken,
);
```

Giữ `idempotencyKey` cho mọi retry của cùng click “Mua ngay”; click mua một giao dịch mới thì sinh
key mới.

## 16. Các khoảng trống backend frontend phải biết

1. **Chưa có public Campaign discovery API.** Frontend hiện không thể tự lấy danh sách campaign
   active, campaign price và countdown chỉ từ public HTTP API. Tạm thời phải dùng campaign ID được
   cấu hình/seed; giải pháp đúng về lâu dài là đặc tả và triển khai public campaign read projection.
2. **Chưa có Order lookup theo `purchaseRequestId` hoặc `reservationId`.** Frontend phải poll danh
   sách order rồi đọc detail để match. Nên bổ sung endpoint/query contract nếu UX cần nhanh và rõ.
3. **Chưa có HTTP API tạo/quản lý category.** Product composition cần UUID category đã tồn tại.
4. Notification Service chưa có HTTP endpoint; Cart MVP đã có API-041 đến API-044.
5. Swagger/OpenAPI là công cụ local; public cloud edge cố ý không bật documentation.
6. Khi deploy frontend cloud, phải cập nhật CORS trusted origin từ `http://localhost:3000` sang
   origin cloud tương ứng trước khi browser gọi API.

Không nên “chữa tạm” các khoảng trống này bằng cách gọi internal API, query database hoặc hardcode
service port trong frontend.

## 17. Checklist trước khi frontend hoàn tất

- [ ] Chỉ có một `API_BASE_URL`, trỏ tới Gateway.
- [ ] `credentials: "include"` cho session cookie.
- [ ] Access token không ghi vào log.
- [ ] Refresh có single-flight và retry request cũ tối đa một lần.
- [ ] Mỗi mutation có `X-Trace-Id`.
- [ ] Idempotency key được giữ qua network retry.
- [ ] Product/Campaign version lấy từ response, không tự cộng.
- [ ] UI xử lý `202`, `204`, `401`, `403`, `404`, `409`, `429`, `503` đúng nghĩa.
- [ ] Stripe success page poll trạng thái backend, không tin redirect một cách mù quáng.
- [ ] Polling có timeout, backoff và cancel.
- [ ] Không gọi `/internal/**`, `/oauth2/token` hoặc Stripe webhook từ browser.

## 18. Tài liệu và kiểm tra liên quan

- Danh mục 47 endpoint: [`README.md`](README.md) hoặc [endpoint-registry.md](endpoint-registry.md)
- Contract inventory: [`../../specs/047-api-documentation/contracts/http-inventory.md`](../../specs/047-api-documentation/contracts/http-inventory.md)
- Flash-sale end-to-end flow: [`../architecture/flash-sale-end-to-end-flow.md`](../architecture/flash-sale-end-to-end-flow.md)
- Kiểm tra catalog/OpenAPI wiring:

  ```powershell
  pwsh -NoLogo -NoProfile -File .\infra\scripts\docs\verify-api-documentation.ps1
  ```


