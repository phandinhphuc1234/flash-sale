# Flash Sale → Kafka → Order — Stress Test

Test có tên dễ tìm: **`flash-sale-to-order-stress`**.

Đo từ yêu cầu giữ suất đến lúc shopper đọc được **Order `PENDING_PAYMENT`**. Khác với
`flashsale-service/adaptive-arrival-rate.js`, test này không dừng ở HTTP `202`.
Không thanh toán Stripe, không chạy full checkout E2E, không đổi code nghiệp vụ.

## 1. Luồng được kiểm tra

```text
Shopper JWT + Idempotency-Key
  → API Gateway
  → Flash Sale: Redis Lua → durable acceptance + outbox
  → Kafka: flashsale.purchase.events.v1
  → Order consumer → lưu Order
  → shopper GET danh sách + chi tiết Order qua Gateway
```

Runner quan sát bằng HTTP, **không đọc DB/Kafka trực tiếp**. Để kết luận luồng thực sự đi qua Kafka,
cần chạy với các service thật và đối chiếu telemetry/log/trace. Test giả lập của runner chỉ chứng
minh công cụ kiểm tra đúng, không chứng minh throughput hệ thống.

Mỗi lượt dùng một shopper riêng:

1. `GET /api/v1/orders?page=0&size=2` xác nhận shopper chưa có đơn.
2. `POST /api/v1/flash-sales/{campaignId}/reservations` với `{ "variantId": "...", "quantity": 1 }`.
3. Nếu được nhận, kiểm tra đầy đủ payload `202`; ở chế độ Replay, gọi lại với **cùng** key.
4. Poll danh sách Order của chủ sở hữu; danh sách phải có đúng một đơn, không còn trang tiếp theo.
5. GET chi tiết: khớp `purchaseRequestId`, `reservationId`, `campaignId`, `items[0].variantId`,
   `quantity=1`, `purchaseSource=FLASH_SALE` và `status=PENDING_PAYMENT`.
6. Sau một khoảng ngắn, đọc lại danh sách/chi tiết để kiểm tra đơn không đổi hoặc bị nhân đôi.

`data.data` là danh sách trong response; `data.page` là metadata. API **không** hỗ trợ filter
`purchaseRequestId`, nên runner không gửi tham số giả. Dùng shopper cũ có nhiều đơn sẽ làm fixture
không hợp lệ. Khoảng recheck mặc định 1 giây không chứng minh “exactly once mãi mãi”.

## 2. Ba chế độ, không trộn số liệu

| `-Scenario` | Mục đích | Điều kiện |
|---|---|---|
| `NewOrders` (mặc định) | Nhiều yêu cầu mới → nhiều Order mới | Đủ allocation cho toàn ladder + warm-up |
| `Replay` | Mỗi yêu cầu mới gọi lại cùng key một lần | Hai POST nhưng chỉ một reservation/Order; đây là replay tuần tự, không phải duplicate burst |
| `SoldOut` | Tranh mua allocation hữu hạn | Một stage, không warm-up; phải có cả winner và `409 FLASH_SALE_SOLD_OUT` |

Replay/hết hàng không được tính thành Order mới. Mỗi chế độ nên dùng **fixture và shopper mới**;
không chạy nối tiếp trên cùng tập người mua rồi coi số liệu còn hợp lệ.

## 3. Chuẩn bị trước khi chạy thật

- Chạy local Compose trước; **không cần dựng lại EKS**. Chỉ dùng Gateway, thường là
  `http://127.0.0.1:18080`, không gọi thẳng Flash Sale hoặc Order để né Gateway.
- PowerShell 7 (`pwsh`), k6 và Git trên PATH. Node chỉ cần cho kiểm thử công cụ offline.
- Auth/Gateway/Product/Campaign/Inventory/Flash Sale/Order và backing services phải healthy.
  Outbox và consumer tạo Order phải hoạt động, topics/schema đúng version; kiểm tra chức năng trước.
- Chuẩn bị **một campaign/variant riêng**, ACTIVE, giá/stock hợp lệ; không có người khác dùng fixture.
  `ExpectedAllocation` là allocation mới của campaign cho variant, không phải stock tổng của kho.
- Campaign phải còn ACTIVE suốt các stage. Reservation TTL phải dài hơn **toàn bộ budget ladder**.
  Runner kiểm tra `expiresAt` sau khi giữ suất; nếu quá ngắn sẽ dừng với
  `reservation_window_too_short`. Hãy rút ngắn/chia nhỏ bài test, không sửa TTL nghiệp vụ để lấy số đẹp.
- Dùng shopper mới, chưa có Order và chưa mua fixture này. Một JWT cho mỗi **subject khác nhau**;
  nhiều JWT của cùng user không thay thế nhiều người mua. Token phải còn hạn qua budget + 60 giây.
  Token phải có quyền shopper; parser kiểm tra subject/expiry, Gateway mới xác thực chữ ký/quyền.
- Lưu JSON array token vào file Git-ignored
  `load-tests/flash-sale-to-order-stress/shopper-tokens.json`, hoặc truyền file ignored đã có bằng
  `-ShopperTokenFile`. Không gửi token/password vào chat hoặc commit Git.
- Runner không tạo tài khoản, không seed stock, không đổi Payment flags, không tạo Checkout Session.
  Các consumer downstream đang bật vẫn có thể xử lý sự kiện thật; báo cáo phải ghi đúng topology.

Mặc định warm-up 1 lượt/s trong 5 giây, sau đó 1 → 5 lượt/s, mỗi stage 10 giây:
**72 shopper riêng** và allocation ít nhất **72** (bao gồm 1 giây headroom mỗi stage).
Warm-up dùng stock thật và token riêng, nhưng không tính là mức tải đo đạt.
Shopper thiếu hoặc allocation quá nhỏ sẽ bị chặn trước khi gửi traffic.

## 4. Chạy từng bước

Mở PowerShell tại repository root. Nhập ID của fixture thật (không nhập dấu `<...>`):

```powershell
$CampaignId = Read-Host "Campaign UUID cua fixture test"
$VariantId = Read-Host "Variant UUID cua fixture test"
```

**Chỉ kiểm tra kế hoạch, không gửi HTTP và không đọc token:**

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\load\run-flash-sale-to-order-stress.ps1 `
  -CampaignId $CampaignId -VariantId $VariantId -ExpectedAllocation 100
```

Con số `100` chỉ dùng khi fixture thực sự có allocation 100. Lệnh in số shopper cần chuẩn bị.
Nếu muốn rehearsal chỉ 2 lượt trước, dùng fixture riêng và:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\load\run-flash-sale-to-order-stress.ps1 `
  -CampaignId $CampaignId -VariantId $VariantId -ExpectedAllocation 100 `
  -RatesCsv "1" -StageSeconds 2 -WarmupSeconds 0 -Run
```

Rehearsal cần 3 token, tạo khoảng 2 Order. Không dùng lại shopper đã dùng để chạy ladder sau.
Khi chuẩn bị fixture/token mới, chạy ladder mặc định:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\load\run-flash-sale-to-order-stress.ps1 `
  -CampaignId $CampaignId -VariantId $VariantId -ExpectedAllocation 100 -Run
```

Để kiểm tra replay, dùng lệnh tương tự nhưng thêm `-Scenario Replay` với fixture mới.
SoldOut ví dụ: fixture allocation đúng **2**, 2 lượt/s × 5 giây, cần 12 token:

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\load\run-flash-sale-to-order-stress.ps1 `
  -CampaignId $CampaignId -VariantId $VariantId -ExpectedAllocation 2 `
  -Scenario SoldOut -RatesCsv "2" -StageSeconds 5 -WarmupSeconds 0 -Run
```

Chỉ tăng dần sau khi mức thấp pass và health/backlog ổn. Không mặc định nhảy 500/1.000 RPS.
Muốn target remote cần `-GatewayBaseUrl https://<gateway-thuoc-quyen-cua-ban> -AllowRemote`;
không áp dụng lệnh này nếu cloud đang tắt. Chỉ opt-in sau khi có ngân sách và scope phù hợp.

## 5. Tham số và guardrail

| Tham số | Mặc định | Ý nghĩa |
|---|---:|---|
| `RatesCsv` | `1,5` | Lượt mua mới bắt đầu/giây, tăng dần, cap 500 |
| `StageSeconds` | 10 | Thời gian phát arrivals mỗi stage |
| `WarmupSeconds` / `CooldownSeconds` | 5 / 5 | Làm nóng / nghỉ giữa stage |
| `OrderTimeoutSeconds` | 30 | Hạn từ trước POST đến lần đọc được Order khớp |
| `PollIntervalMs` | 1000 | Khoảng nghỉ giữa các lần chưa thấy Order |
| `DuplicateCheckSeconds` | 1 | Chờ rồi kiểm tra lại Order duy nhất |
| `RequestTimeoutSeconds` | 5 | Hạn từng HTTP call; polling không vượt observation deadline |
| `VirtualUsers` | 50 | Số VU cố định tối đa; không tự tăng vô hạn |
| `AdmissionP95LimitMs` / `AdmissionP99LimitMs` | 300 / 700 | Guard latency của POST đầu tiên |
| `OrderP95LimitMs` | 5000 | Guard latency từ POST đến Order đọc được |
| `ConsecutiveBreaches` | 2 | Dừng sau hai stage vi phạm latency liên tiếp; warm-up fail thì dừng |
| `TimeoutSeconds` | 900 | Trần tổng budget; runner còn tính deadline nhỏ hơn từ các stage |

k6 dùng [constant-arrival-rate](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/):
rate là **iterations/s**, không phải tổng HTTP requests/s. Một iteration có baseline GET, POST,
poll/detail/recheck và có thể thêm replay. Observer tạo thêm tải thật lên Gateway/Order; đây là
benchmark pipeline **có read observer**, không phải benchmark POST-only hoặc đo Kafka riêng.

Dừng tăng tải nếu có: mất/không quan sát được Order, trùng/sai ID, HTTP bất ngờ, outcome chưa rõ,
fixture không hợp lệ, vượt allocation, k6 dropped iterations, process timeout hoặc summary thiếu.
`503 FLASH_SALE_ACCEPTANCE_PENDING` và network timeout có thể đã commit: đánh fail/inconclusive,
**không** kết luận mất dữ liệu, không tự gửi lại bằng key mới.

Guard đánh giá định kỳ và giữa stage, không thể đảm bảo máy không bao giờ OOM. Theo dõi CPU/RAM,
restart, outbox backlog và Kafka consumer lag bên ngoài runner. Nhiều poll hoặc thiếu VU có thể là
nút thắt của **load generator**; dropped iterations không tự chứng minh server hết khả năng.

## 6. Đọc báo cáo và dùng số liệu cho CV

Mỗi run ghi `load-tests/flash-sale-to-order-stress/results/<runId>/report.json` và các stage JSON.
Các file này bị Git ignore. Chỉ publish bản đã review/sanitized.

| Trường | Cách hiểu |
|---|---|
| `offeredArrivalsPerSecond` | Mức iterations/s đã yêu cầu k6 |
| `actualAdmissionRps` | Số POST mới bắt đầu trong arrival window / số giây của window; không gồm replay/GET |
| `acceptedUniqueReservations` | Số POST đầu tiên có payload 202 hợp lệ |
| `observedOrders` | Số Order mới khớp ID và pass recheck, mỗi iteration tối đa một |
| `observedOrdersInWindowRps` | Order lần đầu được quan sát trong arrival window / thời gian window |
| `observedCompletionRps` | Tổng Order quan sát được / thời gian từ POST đầu đến lần quan sát cuối; gồm phần tail |
| `admissionP95Ms` / `admissionP99Ms` | HTTP latency của POST đầu tiên, tách khỏi replay và observer |
| `orderVisibleP95Ms` / `orderVisibleP99Ms` | Client-observed POST → Order visible; có network và polling delay, không phải timestamp DB commit |
| `observerReadRequests` / `replayRequests` | Lượng HTTP phụ để kiểm correctness |
| `observationTailMs` | Lần quan sát Order cuối trễ bao nhiêu so với hết arrival window; **không phải Kafka drain time** |
| `failureCounts` | Nhóm lỗi đã allowlist, không chứa payload/token |
| `kafkaLag` / `databaseCommitLatencyMs` | `null`: công cụ này không đo, không điền số đoán |

`passed_tested_rates_not_capacity_limit` chỉ nói **các mức đã thử** pass, không phải giới hạn tối đa.
Nếu có một stage breach rồi stage sau pass, report vẫn ghi `completed_with_breaches` và exit khác 0.
P95 của một stage không được lấy trung bình với p95 stage khác để ra p95 tổng.

Để viết CV: lưu commit/image, CPU/RAM/topology, máy phát tải, thời lượng, số shopper/allocation,
POST mới/s thực tế, Order quan sát/s, latency, drops/errors và evidence backlog/health riêng.
Đừng ghi “1.000 đơn/s” từ tổng HTTP gồm replay, sold-out và polling. Hiện chưa có live result cho
profile mới này; các báo cáo reservation cũ không thay thế được.

## 7. Kiểm thử công cụ mà không đụng hệ thống thật

```powershell
node --test load-tests/flash-sale-to-order-stress/tests/contracts.test.mjs
node --test load-tests/flash-sale-to-order-stress/tests/runner.test.mjs
```

Lệnh thứ hai cần k6/pwsh/Git: tạo Gateway giả lập ở port loopback ngẫu nhiên, JWT giả và dữ liệu trong
bộ nhớ; kiểm cả success và lỗi. Không gọi port `18080`, không mở Docker/EKS/Stripe.
Kết quả mock không dùng làm số liệu hiệu năng ứng dụng.

## 8. Khi dừng hoặc bị fail

1. Mở `report.json`: xem stage đầu lỗi, `dangerReasons`, `failureCounts`, accepted so với observed.
2. `owner_not_unique`, `shopper_not_fresh`: kiểm tra fixture/user cũ hoặc duplicate; không nới assertion.
3. `order_correlation_mismatch`: đối chiếu event/order identity; không bỏ bước so ID.
4. `unresolvedOutcomes > 0`: kiểm tra outbox/consumer/health; không retry mù. `observer_http_error`
   còn có thể là JWT hết hạn, quyền, Gateway timeout hoặc Order read lỗi.
5. `generator_dropped_iterations`: xem máy k6 và VU/poll traffic trước khi kết luận server chậm.
6. Dừng rồi kiểm tra recovery/backlog/health. Không tăng stage nếu chưa rõ nguyên nhân.

Runner chỉ dọn child process do nó tạo; không xóa file token của bạn, không xóa campaign/product,
reservation hoặc Order. Test thật **tạo dữ liệu thật**. Cleanup qua quy trình/API được hỗ trợ hoặc
reset một môi trường local disposable đã được cho phép; không xóa SQL để làm đẹp kết quả.
