# Cart Frontend Integration Handoff

## 1. Mục tiêu

Tài liệu này là đầu vào tự chứa cho việc tích hợp Cart Service vào frontend Next.js `QuickCart`.
Người triển khai có thể dùng trực tiếp tài liệu này làm yêu cầu cho một AI coding agent.

Phạm vi gồm:

- đọc giỏ hàng của shopper đã đăng nhập;
- đặt số lượng tuyệt đối cho một variant;
- xóa một item;
- xóa toàn bộ giỏ hàng;
- đồng bộ số lượng trên Navbar và Cart page;
- hiển thị đúng trạng thái Product tạm thời không truy cập được, không tồn tại hoặc không còn bán;
- giữ nguyên flow Flash Sale Reservation -> Order -> Payment hiện có.

Không nằm trong phạm vi:

- checkout trực tiếp toàn bộ Cart;
- giữ hoặc trừ tồn kho;
- tạo Order/Payment từ Cart;
- gọi endpoint nội bộ của Product từ trình duyệt;
- lưu `ownerId`, `cartId`, access token hoặc Cart vào `localStorage`.

## 2. Trạng thái hệ thống và ranh giới triển khai

- Backend Cart G1-G8 đã được merge vào `develop`.
- Cart hiện đã được kiểm thử với local Docker/Gateway; chưa được coi là đã triển khai lên cloud chỉ
  vì source đã merge.
- Frontend phải gọi API Gateway qua `NEXT_PUBLIC_API_BASE_URL`, không gọi trực tiếp cổng Cart
  Service `18089`.
- Local Gateway mặc định là `http://localhost:18080`.
- Cart Service sở hữu `variantId` và `quantity` đã lưu. Product Service vẫn sở hữu tên, ảnh, giá,
  SKU và trạng thái bán hiện tại.
- Chủ Cart được lấy duy nhất từ `sub` trong shopper JWT. Frontend không gửi `ownerId` hoặc `cartId`.

## 3. Quy ước HTTP chung

Mọi request Cart cần đi qua helper xác thực hiện có và gửi:

```http
Authorization: Bearer <accessToken>
Accept: application/json
X-Trace-Id: <uuid>
```

`Content-Type: application/json` chỉ cần cho request có JSON body.

Frontend hiện đã có `lib/api.js`:

- ghép `NEXT_PUBLIC_API_BASE_URL` với path;
- gửi cookie bằng `credentials: "include"`;
- thêm `Authorization` khi có access token;
- tạo `X-Trace-Id`;
- trả `undefined` cho HTTP `204`;
- chuyển response lỗi thành `ApiError` với `status`, `code` và `errors`.

Frontend nên gọi Cart bằng hàm `request()` trong `AppContext`, vì hàm này đã refresh access token
một lần khi gặp `401`. Không gọi `fetch()` trực tiếp ở từng component.

Success envelope:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {},
  "timestamp": "2026-08-29T00:00:00Z"
}
```

Error envelope:

```json
{
  "success": false,
  "errorCode": "CART_VALIDATION_ERROR",
  "message": "Cart request is invalid",
  "errors": [
    {
      "field": "quantity",
      "message": "quantity must be at most 10"
    }
  ],
  "timestamp": "2026-08-29T00:00:00Z"
}
```

Mọi Cart response có `Cache-Control: no-store`. Frontend không dùng Cart response như một cache giá
hay cam kết tồn kho.

## 4. Data model frontend

Không tiếp tục dùng object local có dạng:

```javascript
{ [productId]: quantity }
```

State đề xuất:

```javascript
const EMPTY_CART = {
  items: [],
  distinctItemCount: 0,
  totalQuantity: 0,
  updatedAt: null,
};

const [cart, setCart] = useState(EMPTY_CART);
const [cartLoading, setCartLoading] = useState(false);
const [cartError, setCartError] = useState(null);
```

`CartItem` đầy đủ:

```typescript
type CartItem = {
  variantId: string;
  quantity: number;
  detailsAvailable: boolean;
  sellable: boolean | null;
  unavailableReason:
    | "PRODUCT_DETAILS_UNAVAILABLE"
    | "PRODUCT_NOT_FOUND"
    | "PRODUCT_NOT_SELLABLE"
    | null;
  productId: string | null;
  productSlug: string | null;
  productName: string | null;
  variantName: string | null;
  sku: string | null;
  basePrice: string | null;
  currency: string | null;
  primaryImageUrl: string | null;
  updatedAt: string;
};
```

`Cart`:

```typescript
type Cart = {
  items: CartItem[];
  distinctItemCount: number;
  totalQuantity: number;
  updatedAt: string | null;
};
```

Giá được trả dưới dạng chuỗi decimal. Chỉ chuyển bằng `Number(item.basePrice)` tại nơi tính/format
giá hiển thị. Đây là giá tham khảo hiện tại, không phải giá đã khóa cho Order.

## 5. API-041 - Đọc Cart

### Request

```http
GET /api/v1/cart
Authorization: Bearer <accessToken>
X-Trace-Id: <uuid>
```

### Response `200 OK`

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
        "updatedAt": "2026-08-29T00:00:00Z"
      }
    ],
    "distinctItemCount": 1,
    "totalQuantity": 2,
    "updatedAt": "2026-08-29T00:00:00Z"
  },
  "timestamp": "2026-08-29T00:00:00Z"
}
```

Cart chưa tồn tại vẫn trả `200`:

```json
{
  "items": [],
  "distinctItemCount": 0,
  "totalQuantity": 0,
  "updatedAt": null
}
```

### Cách xử lý trạng thái item

| Trạng thái | UI cần làm |
|---|---|
| `detailsAvailable=true`, `sellable=true` | Hiển thị đầy đủ và cho phép tiếp tục flow mua |
| `PRODUCT_DETAILS_UNAVAILABLE` | Giữ item/quantity, hiện “Tạm thời chưa lấy được thông tin”, cho phép refresh, khóa mua |
| `PRODUCT_NOT_FOUND` | Hiện “Sản phẩm không còn tồn tại”, cho phép xóa item, khóa mua |
| `PRODUCT_NOT_SELLABLE` | Hiện “Sản phẩm hiện không bán”, cho phép xóa item, khóa mua |

## 6. API-042 - Thêm hoặc thay số lượng

### Request

```http
PUT /api/v1/cart/items/{variantId}
Authorization: Bearer <accessToken>
X-Trace-Id: <uuid>
Content-Type: application/json

{
  "quantity": 2
}
```

Quy tắc:

- `{variantId}` phải là UUID của variant đang được chọn, không phải Product ID;
- `quantity` phải là số nguyên từ `1` đến `10`;
- đây là absolute replacement: nếu Cart đang có `2`, gửi `3` thì kết quả là `3`, không phải `5`;
- gửi lại cùng request là an toàn và không tạo item trùng;
- không gửi `quantity=0`; muốn xóa phải dùng API-043.

### Response `200 OK`

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Cart item saved",
  "data": {
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
    "updatedAt": "2026-08-29T00:00:00Z"
  },
  "timestamp": "2026-08-29T00:00:00Z"
}
```

Sau PUT thành công, cách đơn giản và nhất quán nhất là gọi lại API-041 để lấy aggregate Cart và
Product display hiện tại.

## 7. API-043 - Xóa một item

```http
DELETE /api/v1/cart/items/{variantId}
Authorization: Bearer <accessToken>
X-Trace-Id: <uuid>
```

Response: `204 No Content`, body rỗng.

Xóa item không tồn tại vẫn thành công. Sau response, xóa item khỏi state hoặc gọi lại API-041.
Không gọi `response.json()` cho response `204`.

## 8. API-044 - Xóa toàn bộ Cart

```http
DELETE /api/v1/cart
Authorization: Bearer <accessToken>
X-Trace-Id: <uuid>
```

Response: `204 No Content`, body rỗng.

Cart chưa tồn tại hoặc đã rỗng vẫn thành công. Sau response, đặt state về `EMPTY_CART`.

## 9. Bảng xử lý lỗi

| HTTP | `errorCode` | Hành vi frontend |
|---:|---|---|
| 400 | `CART_VALIDATION_ERROR` | Hiện lỗi UUID/body/quantity; không retry tự động |
| 401 | `UNAUTHENTICATED` | Dùng flow refresh token hiện có đúng một lần; nếu vẫn lỗi thì đưa về đăng nhập |
| 404 | `CART_VARIANT_NOT_FOUND` | Báo variant không tồn tại; reload Cart hoặc cho phép xóa item |
| 409 | `CART_VARIANT_NOT_SELLABLE` | Báo variant không bán được; không reserve/mua |
| 503 | `CART_PRODUCT_DEPENDENCY_UNAVAILABLE` | Giữ input của người dùng; cho retry có backoff hoặc nút thử lại |
| 500 | `CART_INTERNAL_ERROR` | Hiện lỗi chung; không retry vô hạn |

Không dùng `CART_PRODUCT_UNAVAILABLE`; mã thực tế của Cart Service là
`CART_PRODUCT_DEPENDENCY_UNAVAILABLE`.

## 10. Flow xác thực và đồng bộ state

```text
App khởi động
  -> thử POST /api/v1/auth/refresh bằng refresh cookie
  -> nếu nhận access token: set accessToken
  -> GET /api/v1/cart
  -> render Navbar và Cart page

Login thành công
  -> nhận access token
  -> set accessToken
  -> GET /api/v1/cart bằng token vừa nhận

Logout/logout-all
  -> gọi API auth hiện có
  -> xóa accessToken/userData
  -> setCart(EMPTY_CART)
```

Lưu ý React: `setAccessToken(token)` là bất đồng bộ. Không gọi ngay một hàm đang đóng trên access
token cũ. Có thể chọn một trong hai cách:

1. dùng `useEffect` theo dõi `accessToken` rồi gọi `loadCart()`; hoặc
2. cho `loadCart(tokenOverride)` nhận token vừa login/refresh và gọi `apiFetch` với token đó.

Không gọi API Cart khi chưa xác định xong trạng thái auth. Shopper chưa đăng nhập nên thấy Cart rỗng
ở UI và khi bấm “Add to cart” thì được chuyển tới `/login`.

## 11. Flow UI cần triển khai

### Product Detail

Flow đúng:

```text
shopper chọn variantId + quantity
  -> kiểm tra đã đăng nhập
  -> PUT /api/v1/cart/items/{variantId}
  -> reload Cart
  -> hiện toast thành công
```

Điểm bắt buộc sửa: code hiện tại gọi `addToCart(product.id)`. Phải truyền `variantId` đang chọn và
quantity hiện tại.

### Cart Page

Cart page phải render trực tiếp `cart.items`, không tìm Product bằng object local và Product ID.

```text
mở /cart
  -> load Cart nếu đã đăng nhập
  -> render productName, variantName, image, price và quantity
  -> đổi quantity 1..10: PUT rồi reload
  -> quantity 0 hoặc Remove: DELETE item
  -> Clear cart: DELETE cart
```

Không gửi PUT trên từng ký tự người dùng gõ. Nên giữ input tạm trong component và submit khi blur,
nhấn Enter hoặc sau debounce ngắn; vô hiệu hóa nút trong lúc request đang chạy.

### Navbar

Dùng `cart.totalQuantity`, không tự cộng object local. Khi chưa đăng nhập hoặc logout, hiển thị `0`.

### Tổng tiền hiển thị

Có thể tính subtotal hiển thị bằng:

```javascript
cart.items.reduce((sum, item) => {
  if (!item.detailsAvailable || item.sellable !== true || item.basePrice == null) return sum;
  return sum + Number(item.basePrice) * item.quantity;
}, 0);
```

Nhãn UI nên là “Tạm tính”. Giá và stock sẽ được kiểm tra lại trong flow Reservation/Order; Cart
không bảo đảm giá hoặc tồn kho.

### Flow mua hàng

Cart MVP không có endpoint checkout Cart. Không tự tạo request mới tới Order hoặc Payment.

```text
Cart item
  -> mở Product Detail theo productSlug/productId
  -> shopper chọn campaign hiện hành
  -> POST Flash Sale Reservation hiện có
  -> Order được tạo bất đồng bộ
  -> Payment aggregate
  -> Stripe Checkout
```

## 12. Các file QuickCart cần sửa

### `context/AppContext.jsx`

- thay `cartItems` object bằng `cart`, `cartLoading`, `cartError`;
- thêm `loadCart`, `setCartItem`, `removeCartItem`, `clearCart`;
- dùng helper `request()` để giữ refresh-token behavior;
- load Cart sau login/refresh;
- reset Cart sau logout/logout-all;
- đổi `getCartCount()` sang trả `cart.totalQuantity`;
- đổi `getCartAmount()` sang tính từ `cart.items` có Product details hợp lệ;
- không lưu Cart hoặc token trong localStorage.

### `app/product/[id]/page.jsx`

- đổi `addToCart(product.id)` thành mutation dùng `variantId` đang chọn;
- truyền quantity hiện tại;
- yêu cầu đăng nhập trước mutation;
- khóa nút và hiện pending/error/toast trong khi gọi;
- giữ nguyên nút và flow `Reserve now`.

### `app/cart/page.jsx`

- xóa dòng mô tả “Cart Service has no frontend HTTP API”;
- render `cart.items` thay vì nối local product list;
- hỗ trợ loading, empty, dependency unavailable và non-sellable;
- PUT quantity 1..10;
- DELETE item khi Remove;
- thêm Clear cart dùng API-044;
- không cho mua item có `detailsAvailable=false` hoặc `sellable!==true`.

### `components/Navbar.jsx`

- badge dùng `cart.totalQuantity`;
- reset về 0 khi logout.

### `components/OrderSummary.jsx`

- nếu component còn được dùng cho Cart, tính “Tạm tính” từ `cart.items`;
- không diễn giải subtotal Cart là giá Order đã khóa;
- không tạo checkout Cart vì backend chưa có contract này.

### `lib/api.js`

Không cần tạo API base URL thứ hai. Giữ một Gateway base URL duy nhất. Có thể giữ nguyên helper nếu
nó tiếp tục xử lý đúng `204` và `ApiError`.

## 13. Acceptance criteria

1. Shopper chưa đăng nhập không gọi GET Cart; thao tác thêm Cart đưa tới login.
2. Login/refresh thành công sẽ tải Cart đúng một lần mà không dùng token cũ.
3. Reload trang vẫn lấy lại Cart từ backend.
4. Chọn variant thứ hai của một Product gửi đúng variant thứ hai, không gửi Product ID.
5. PUT quantity `1`, `5`, `10` thành công; UI không gửi `0`, số âm, số thập phân hoặc trên `10`.
6. Gửi cùng PUT hai lần không làm quantity tăng gấp đôi.
7. DELETE item và clear Cart xử lý đúng `204` body rỗng.
8. Hai tài khoản shopper không nhìn thấy Cart của nhau.
9. Navbar hiển thị `totalQuantity`, không phải `distinctItemCount`.
10. Product dependency lỗi vẫn hiển thị variantId/quantity và không làm mất item.
11. Item missing/non-sellable bị khóa khỏi flow mua nhưng vẫn xóa được.
12. Logout xóa Cart state trên trình duyệt; login lại tải từ backend.
13. Không có browser call tới `/internal/v1/catalog/variants/display-details` hoặc cổng `18089`.
14. Flow Reservation -> Order -> Payment hiện có không bị thay đổi.
15. `npm run build` hoàn tất với exit code `0`.

## 14. Validation thủ công

Với local backend/Gateway đang chạy và frontend có:

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:18080
```

Kiểm tra lần lượt:

1. đăng nhập shopper A;
2. mở Product Detail, chọn một variant và quantity `2`;
3. thêm Cart, reload trình duyệt và xác nhận item vẫn còn;
4. đổi quantity thành `3`, reload và xác nhận vẫn là `3`;
5. mở session ẩn danh hoặc shopper B và xác nhận không thấy Cart A;
6. xóa một item, lặp lại DELETE và xác nhận không lỗi;
7. thêm lại rồi clear Cart;
8. logout và xác nhận Navbar về `0`;
9. chạy `npm run build` và chỉ kết luận thành công khi exit code là `0`.

## 15. Prompt có thể đưa thẳng cho AI coding agent

```text
Hãy tích hợp backend Cart Service vào frontend Next.js QuickCart theo toàn bộ tài liệu
docs/api/cart-frontend-ai-handoff.md.

Trước khi sửa:
- đọc lib/api.js, context/AppContext.jsx, app/product/[id]/page.jsx, app/cart/page.jsx,
  components/Navbar.jsx và components/OrderSummary.jsx;
- kiểm tra git status và bảo toàn mọi thay đổi hiện có của người dùng;
- giữ nguyên thiết kế UI hiện tại, chỉ thêm state/loading/error/control cần thiết;
- tất cả browser API phải đi qua NEXT_PUBLIC_API_BASE_URL và API Gateway;
- không gọi endpoint internal, không dùng Cart Service port 18089;
- không lưu access token hoặc Cart vào localStorage;
- dùng variantId, không dùng productId, cho Cart mutations;
- giữ nguyên flow Flash Sale Reservation -> Order -> Payment.

Hãy triển khai bốn API Cart, xử lý đầy đủ 204, 401 refresh một lần, 400/404/409/503/500,
detailsAvailable, sellable và unavailableReason. Sau khi sửa, chạy npm run build. Không báo hoàn
thành nếu build chưa exit 0. Cuối cùng liệt kê file đã sửa và mô tả flow đã kiểm thử.
```

## 16. Nguồn hợp đồng

- `docs/api/frontend-integration-guide.md`, phần “4.1 Cart của shopper”;
- `docs/api/README.md`, API-041 đến API-045;
- `specs/048-cart-mvp/contracts/public-cart-http.md`;
- `services/cart-service/.../adapter/in/web/CartController.java` và các response record cùng package.

Nếu tài liệu khác mâu thuẫn với code/error vocabulary hiện tại, hợp đồng Cart Feature 048 và
controller/error handler của Cart là nguồn đối chiếu cuối cùng trước khi sửa frontend.
