# 03 — Coding Constraints trong Spec-Driven Development

**Phạm vi**: Các ràng buộc khi chuyển một feature đã được duyệt từ spec sang code, test, contract
và infrastructure trong repository Flash Sale.

**Nguồn chuẩn**:

- [Constitution v1.2.0](../../.specify/memory/constitution.md)
- [AGENTS.md](../../AGENTS.md)
- [ADR 0001 — Root Infrastructure Ownership](../adr/0001-root-infrastructure-ownership.md)
- Approved `spec.md`, `plan.md` và `tasks.md` của active feature

Tài liệu này là index và hướng dẫn áp dụng. Nếu có xung đột, constitution và approved feature
artifacts thắng; sửa nguồn chuẩn trước rồi đồng bộ lại guide.

## 1. Mức độ ràng buộc

| Nhãn | Ý nghĩa |
|------|---------|
| `GLOBAL` | Bắt buộc bởi constitution hoặc AGENTS của repository |
| `ADR` | Bắt buộc trong phạm vi quyết định kiến trúc đã Accepted |
| `FEATURE` | Bắt buộc trong phạm vi approved spec/plan/tasks |
| `PROFILE` | Quy tắc SDD/DDD/Hexagonal theo rủi ro; chỉ thành bắt buộc khi được plan/governance chấp nhận |

Từ khóa:

- **MUST/MUST NOT**: nghĩa vụ bắt buộc trong scope được gắn nhãn.
- **SHOULD/SHOULD NOT**: mặc định mạnh; nếu làm khác phải ghi rationale trong plan/review.
- **MAY**: lựa chọn hợp lệ, không phải yêu cầu.

Không biến một constraint chỉ thuộc feature scaffold thành lệnh cấm toàn repository. Ví dụ “không
thêm Docker/Kubernetes” là scope của feature scaffold hiện tại, trong khi constitution cho phép
infrastructure ở root `infra/` thông qua một feature được phê duyệt.

## 2. Gate trước khi code

Không thay đổi production code cho đến khi tất cả điều sau đúng:

- [ ] Active feature pointer đang trỏ đúng feature.
- [ ] `spec.md` đã Approved và mô tả WHAT/WHY đủ rõ.
- [ ] Không còn clarification P0/P1 liên quan phần sắp làm.
- [ ] `plan.md` đã Approved và Constitution Check pass.
- [ ] Dependency, contract, data, infrastructure và ADR impact đã được ghi trong plan.
- [ ] `tasks.md` đã Approved, task hiện tại có exact path và verification.
- [ ] Applicable test layers đã được xác định; layer bị bỏ có rationale.
- [ ] Chỉ một task hoặc một coherent task group được chọn.

Script kiểm tra file tồn tại không thay thế approval. Một file có mặt trên disk hoặc một checkbox
`[x]` không chứng minh gate đã pass.

## 3. Không tự phát minh requirement

### SDD-001 — Unresolved behavior (`GLOBAL`)

Developer và AI MUST NOT implement unresolved requirement. Nếu chi tiết ảnh hưởng behavior nhưng
không có trong approved spec, dừng và clarify.

### SDD-002 — High-risk decisions (`PROFILE`, bắt buộc cho domain rủi ro cao)

AI MUST NOT tự chọn:

- TTL, quota hoặc purchase limit;
- idempotency semantics và key scope;
- refund, compensation hoặc late-payment policy;
- authorization hoặc security boundary;
- consistency model và source-of-truth semantics;
- invariant liên quan stock hoặc tiền;
- business timeout, retry hoặc reconciliation ownership.

Dùng marker canonical trong requirement:

```text
[NEEDS CLARIFICATION: duplicate payment callback phải trả kết quả cũ hay tạo attempt mới?]
```

Theo dõi priority, owner, options và deadline trong `Human Decisions Required`:

| Priority | Question | Why it blocks | Options/trade-offs | Owner | Deadline |
|----------|----------|---------------|--------------------|-------|----------|
| P0 | ... | ... | ... | ... | ... |

- P0: chặn toàn feature hoặc correctness/security/financial path.
- P1: chặn plan hoặc một implementation branch chính.
- P2: phần độc lập có thể tiếp tục; câu hỏi vẫn có owner.

Reasonable assumption chỉ được dùng cho chi tiết rủi ro thấp, dễ đảo ngược, không đổi business
behavior và phải được ghi trong `Assumptions` hoặc plan. “Không được đoán” không có nghĩa là mọi tên
file nội bộ đều cần business owner quyết định.

### SDD-003 — Spec gap trong lúc code (`GLOBAL`)

Khi phát hiện spec sai hoặc thiếu:

```text
Stop task
  -> update spec/acceptance behavior
    -> human review
      -> update plan/contracts/tasks if affected
        -> resume implementation
```

Không vá rule âm thầm trong controller, consumer, config hoặc test.

## 4. Service và module boundaries

### ARC-001 — Independent services (`GLOBAL`)

- Production code MUST dùng Java 21 và root Maven Wrapper.
- Mỗi service MUST build, test và deploy độc lập.
- Service MUST NOT có direct Maven dependency vào service khác.
- Deployment lifecycle MUST NOT bị couple nếu chưa có approved ADR.

### ARC-002 — Service ownership (`GLOBAL`)

Mỗi service sở hữu:

- application source;
- POM dependencies;
- runtime configuration;
- tests;
- database và schema;
- database migrations.

Service MUST NOT query database của service khác hoặc share JPA entity, repository hay business
domain model.

Generated contract type hoặc cross-cutting infrastructure utility MAY được share khi không chuyển
business ownership sang shared library.

### ARC-003 — Ingress và discovery (`GLOBAL`)

- Public traffic MUST đi qua `api-gateway`.
- Kubernetes Service và DNS MUST là service-discovery mechanism.
- Eureka MUST NOT được thêm vào.
- Internal HTTP address MUST dùng platform-provided service name theo approved plan.

## 5. Communication và contracts

### COM-001 — Contract-first (`GLOBAL`)

- Synchronous cross-service communication MUST dùng documented HTTP contract.
- Asynchronous communication MUST dùng documented, versioned Kafka contract.
- Contract file và compatibility impact MUST được cập nhật trước implementation.
- Database access hoặc shared domain code MUST NOT thay service contract.

Feature-local `contracts/` mô tả proposed delta. Nếu repository dùng root `contracts/` làm accepted
catalog, plan/tasks phải có bước promote sau khi change được chấp nhận; không để hai bản cùng drift.

### COM-002 — Compatibility (`GLOBAL` + `FEATURE`)

Plan phải xác định khi contract thay đổi:

- backward/forward compatibility expectation;
- versioning strategy;
- producer/consumer rollout order;
- error and header semantics;
- contract tests;
- rollback hoặc coexistence window.

Không rename/remove public field hoặc event meaning chỉ vì refactor code nội bộ.

## 6. Data, concurrency và reliable messaging

### DATA-001 — Durable truth (`GLOBAL`)

- PostgreSQL MUST là durable source of truth.
- Redis MUST NOT trở thành authoritative business store.
- Plan MUST ghi persistence, reconciliation, expiration và failure recovery cho Redis-backed state.

### DATA-002 — Flash-sale hot path (`GLOBAL`)

Stock deduction trên flash-sale hot path MUST atomic qua Redis Lua. Feature tác động stock phải định
nghĩa invariant chống oversell và có concurrency/load evidence phù hợp.

Redis Lua là implementation constraint đã được constitution phê duyệt; business semantics về giữ,
xác nhận, hết hạn hoặc hoàn stock vẫn phải nằm trong spec.

### EVT-001 — Idempotent consumers (`GLOBAL`)

Kafka consumer MUST idempotent để redelivery không tạo duplicate business effect. Plan phải xác định
deduplication key, storage/lifecycle, retry, ordering và recovery behavior.

### EVT-002 — Outbox (`GLOBAL`)

Khi durable state change phải được theo sau bởi event publication đáng tin cậy, design MUST dùng
transactional outbox, trừ khi plan chứng minh atomic publication không cần thiết.

Không dùng “Kafka sẽ retry” để thay thế atomicity, idempotency hoặc reconciliation decision.

## 7. Hexagonal Architecture theo rủi ro

Constitution hiện không áp đặt một package layout Hexagonal duy nhất cho mọi service. Các luật dưới
đây là `PROFILE`; chúng trở thành `FEATURE` constraints khi approved plan chọn Hexagonal
Architecture cho service/domain đang thay đổi.

### HEX-001 — Dependency direction (`PROFILE`)

Compile-time dependency phải hướng vào core:

```text
Driving adapter -> Input port -> Application/domain core -> Output port <- Driven adapter
```

Domain/application core MUST NOT import Spring MVC/WebFlux, JPA entity, Kafka client, Redis client
hoặc provider SDK.

### HEX-002 — Ports describe business intent (`PROFILE`)

- Input port dùng động từ nghiệp vụ như `ReserveStock`, `ConfirmOrder`, `IssueRefund`.
- Output port mô tả nhu cầu của core như lưu reservation, publish domain event hoặc đọc policy.
- Port MUST NOT lộ API của framework/provider vào core.
- HTTP controller, Kafka consumer và scheduler là driving adapters.
- PostgreSQL, Redis, Kafka và external providers là driven adapters.

### HEX-003 — Replaceable adapters (`PROFILE`)

Thay adapter không được làm đổi UC/Acceptance Scenario nếu business behavior giữ nguyên. Domain test
SHOULD chạy không cần network, broker hoặc database thật.

### HEX-004 — Risk-adjusted depth (`PROFILE`)

| Thành phần | Mức áp dụng |
|------------|-------------|
| Stock, ordering, payment, security, lifecycle/concurrency phức tạp | UC/AC, domain invariants, ports/adapters và failure tests đầy đủ |
| Supporting context hoặc CRUD ít rule | Vocabulary rõ, flow/AC rõ; plan có thể chọn cấu trúc nhẹ |
| One-off migration | Preconditions, postconditions, rollback và verification |
| Disposable prototype | Spec tối thiểu và explicit disposal boundary |

Không hạ nhẹ feature stock/order/payment chỉ vì endpoint ban đầu trông giống CRUD.

## 8. Ubiquitous language và naming

### DDD-001 — Canonical terms (`PROFILE`)

- Một term có một nghĩa trong một bounded context.
- Cùng từ nhưng khác nghĩa ở context khác phải được định nghĩa riêng.
- Bounded context là semantic/business boundary; service là implementation/deployment boundary.
  Plan/ADR quyết định mapping giữa chúng, không suy ra context chỉ từ danh sách service.
- Service name, table name hoặc transport class không thay business term.
- Class, method, event và test SHOULD ưu tiên canonical business language.

Tên có intent:

```text
ReserveStock, ExpireReservation, ConfirmPayment, IssueRefund
```

Tên quá generic cần xem lại:

```text
InventoryService, OrderManager, CommonProcessor, GenericHandler
```

Hậu tố `Service` không bị cấm tuyệt đối. Vấn đề là tên có diễn đạt use case/domain intent hay chỉ mô
tả pattern kỹ thuật.

Test name có thể giữ traceability:

```java
@DisplayName("UC-INV-001 / AC-04 - duplicate request returns the original reservation")
```

Nếu không dùng UC/AC extension, dùng User Story/FR ID nhất quán thay vì tạo ID giả.

Repository chưa áp đặt commit convention có UC ID. Nếu team muốn traceability này, phải ghi nó trong
governance hoặc contribution guide; profile có thể dùng dạng `feat(UC-INV-001): reserve stock
atomically`, nhưng đây chưa phải `GLOBAL` constraint.

## 9. Observability

### OBS-001 — Operational endpoints (`GLOBAL`)

Mỗi service MUST expose:

- liveness;
- readiness;
- Prometheus-compatible metrics endpoint.

Với Spring Boot 3.x, service MUST dùng Actuator auto-configuration, runtime Prometheus registry
dependency và declarative `application.yml` configuration.

Service code MUST NOT tự construct `PrometheusMeterRegistry` hoặc couple business logic vào một
registry implementation nếu chưa có approved plan và ADR exception.

### OBS-002 — Traceability at runtime (`GLOBAL`)

Important request và event MUST truyền trace ID qua gateway, HTTP, Kafka, logs và downstream
processing. Plan/tests phải chỉ ra trace path và failure signals cần kiểm tra.

Metric/log/trace không thay business evidence. Test chỉ thấy log được ghi chưa chứng minh invariant
đúng.

## 10. Infrastructure ownership

### INFRA-001 — Root infrastructure (`GLOBAL` + `ADR`)

Shared assets sau MUST nằm dưới root `infra/`:

- local backing-service orchestration;
- Kubernetes bases và overlays;
- approved Helm deployment assets;
- Prometheus scrape infrastructure;
- Grafana dashboards và alert rules.

Service-owned config và migrations MUST ở service. Service-specific image build recipe MAY ở service
để giữ independent image build; shared Compose topology thuộc `infra/docker/`.

Prometheus endpoint của service không phải Prometheus server. Actuator/Micrometer ở service;
scraper/dashboard/alerts ở `infra/monitoring/`.

### INFRA-002 — Kubernetes validation (`GLOBAL`)

Mọi changed Kubernetes overlay MUST pass:

```bash
kubectl apply --dry-run=client -k <overlay>
```

Helm và Kustomize MUST NOT cùng là source of truth độc lập cho cùng một resource; plan phải chỉ định
owner.

## 11. Dependency và architecture change control

### CHG-001 — New production dependency (`GLOBAL`)

Không thêm production dependency trước khi plan ghi module, purpose, alternatives và impact.

### CHG-002 — ADR (`GLOBAL`)

Architectural change phải có ADR Accepted và plan update trước implementation. Bao gồm thay đổi:

- service boundary hoặc deployment coupling;
- discovery/ingress;
- persistence ownership;
- communication style;
- durability;
- root infrastructure ownership exception.

### CHG-003 — Constitutional departure (`GLOBAL`)

Departure phải nằm trong `Complexity Tracking`, nêu lý do và simpler alternative đã bị loại. Dùng
ADR hoặc constitution amendment tùy phạm vi; không biến exception thành precedent ngầm.

## 12. Testing và validation gates

### TEST-001 — Risk-based test layers (`GLOBAL`)

Unit, integration, contract và load test MUST được áp dụng theo risk. Plan phải nêu layer nào áp dụng
và giải thích layer bị bỏ.

| Loại thay đổi | Gate tối thiểu |
|---------------|---------------|
| Service-local | `./mvnw -pl services/<service> -am verify` và applicable test layers |
| Cross-cutting hoặc root build | `./mvnw clean verify` |
| HTTP/Kafka contract | Contract update, compatibility review và contract tests |
| Kafka consumer | Idempotency/redelivery/recovery tests |
| Stock/Redis hot path | Atomicity, concurrency và reconciliation tests |
| Schema/migration | Forward migration và rollback/mitigation verification |
| Kubernetes overlay | Client-side dry-run cho từng overlay |
| Operational behavior | Health, probes, metrics và trace-path checks theo plan |

Required gate fail thì task chưa complete. Không skip/disable test hoặc sửa expectation để làm build
xanh nếu behavior vẫn trái spec.

### TEST-002 — Evidence (`GLOBAL`)

Lưu command, scope, kết quả/exit status và CI/PR reference ở nơi plan quy định. Checkbox `[x]` trong
`tasks.md` là completion ledger, không tự thân là test evidence.

Repository chưa có một artifact validation evidence bắt buộc duy nhất; mỗi feature plan phải chọn
PR/CI run, quickstart result hoặc một validation document nếu audit yêu cầu.

## 13. Vòng lặp implementation

Cho mỗi task hoặc coherent group:

1. Đọc lại UC/User Story, Acceptance Scenario, FR và constraint liên quan.
2. Xác nhận task path/dependency và không có unresolved decision.
3. Viết hoặc cập nhật test/contract trước implementation khi tasks yêu cầu.
4. Thực hiện thay đổi nhỏ nhất đáp ứng behavior.
5. Chạy applicable local/module tests.
6. Rà boundary, contract, observability và migration impact.
7. Chạy checkpoint được ghi trong tasks/plan.
8. Ghi evidence rồi mới đánh dấu `[x]`.
9. Dừng ở review checkpoint; không tự mở rộng sang phase khác.

Không refactor rộng chỉ vì đang ở gần code nếu refactor không nằm trong scope/task hoặc tạo
architecture impact chưa được review.

## 14. Definition of Done

- [ ] Mọi Acceptance Scenario trong scope có evidence tương ứng.
- [ ] Required unit/integration/contract/load tests pass.
- [ ] Module/full build gate theo plan pass.
- [ ] Contract và compatibility artifacts được đồng bộ.
- [ ] Idempotency, redelivery và recovery pass khi Kafka consumer bị tác động.
- [ ] Concurrency/oversell evidence pass khi stock/reservation bị tác động.
- [ ] Migration và rollback/mitigation được verify khi data thay đổi.
- [ ] Liveness/readiness/metrics/trace behavior được verify khi áp dụng.
- [ ] Không có P0/P1 hoặc required test đang fail.
- [ ] Validation evidence đã được lưu.
- [ ] Spec/plan/tasks status và history phản ánh thực tế.
- [ ] Baseline chỉ được promote nếu repository đã có baseline governance và behavior đã được chấp
  nhận ở môi trường quy định.

## 15. Anti-pattern bị cấm

- Viết code trước rồi sửa spec cho khớp code.
- Tự chọn rule liên quan tiền, stock, security, TTL hoặc compensation.
- Cross-service database query hoặc share JPA/domain model.
- Dùng Eureka hoặc bypass gateway/discovery policy.
- Dùng Redis làm durable truth.
- Kafka consumer không idempotent hoặc dựa vào retry để tránh duplicate effect.
- Publish event quan trọng sau DB commit mà không có outbox decision.
- Domain/application core import trực tiếp provider framework khi approved plan yêu cầu Hexagonal.
- Test chỉ assert HTTP 200 mà không assert business invariant.
- Task mơ hồ như “implement service”, “handle edge cases” hoặc “add tests”.
- Đánh dấu task complete khi validation bắt buộc fail hoặc chưa chạy.
- Đặt shared platform assets trong service module.
- Lặp business rule ở nhiều file mà không có canonical owner.

## 16. Exception workflow

| Tình huống | Hành động bắt buộc |
|------------|--------------------|
| Requirement chưa rõ | Dừng, cập nhật/approve spec |
| New dependency | Cập nhật/approve plan trước khi thêm |
| Contract change | Cập nhật contract và compatibility/test impact trước code |
| Architecture/ownership exception | ADR Accepted và plan update trước code |
| Bỏ một test layer | Ghi rationale trong plan; không được bỏ required gate đang fail |
| Constitution change | Semantic version bump, Sync Impact Report và đồng bộ templates/guidance |

Exception chỉ hợp lệ trong scope được phê duyệt. Nó không tự động thay đổi constitution cho feature
khác.

## 17. Audit khoảng cách giữa spec và ngôn ngữ code

Thực hiện trước feature nghiệp vụ đầu tiên của một bounded context và sau refactor lớn:

1. Chọn ngẫu nhiên ít nhất năm file trong domain/application core của context.
2. Lấy tên class, public method, input port, output port, event và test chính.
3. Đối chiếu với User Story/Use Case và ubiquitous language canonical.
4. Phân loại:
   - **business-named**: tên nói được intent như `ReserveStock`, `ExpireReservation`,
     `ConfirmPayment`, `IssueRefund`;
   - **technical-generic**: tên chủ yếu nói pattern như `InventoryService`, `OrderManager`,
     `CommonProcessor`, `GenericHandler`;
   - **transport/provider-specific**: tên adapter hợp lệ ở boundary nhưng không được rò vào core.
5. Ghi các collision hoặc term chưa có owner; không tự rename public contract.

Nếu tỷ lệ technical-generic cao, xử lý theo thứ tự:

1. xác minh canonical terms và collision giữa contexts;
2. xác minh User Story/Use Case dùng động từ và danh từ nghiệp vụ đúng;
3. đổi tên input port, application handler và test trước;
4. refactor sâu domain model chỉ khi test bảo vệ invariant;
5. không đổi database/event/public API name nếu chưa có migration và compatibility plan.

Checklist audit:

- [ ] User Story không đứng một mình như implementation spec.
- [ ] Use Case phức tạp có actor, trigger, flow, exception, postcondition và acceptance behavior.
- [ ] Domain concept mô tả meaning/state/lifecycle/invariant, không phải ERD trá hình.
- [ ] Một term có một nghĩa trong context; collision được ghi trong context map/glossary.
- [ ] Domain/application core không phụ thuộc adapter/provider khi plan chọn Hexagonal.
- [ ] Adapter có thể thay mà không đổi acceptance behavior nếu business behavior giữ nguyên.
- [ ] Tên code ưu tiên business intent; tên generic có rationale hoặc refactor task.
- [ ] Public contract rename có compatibility/migration plan.

Audit này là tín hiệu chất lượng, không phải quota naming. Không ép đổi mọi class có hậu tố `Service`
và không refactor chỉ để đạt một tỷ lệ đẹp.
