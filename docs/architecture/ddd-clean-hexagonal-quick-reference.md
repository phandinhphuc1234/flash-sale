# DDD, Clean Architecture & Hexagonal — Quick Reference

Tài liệu này là checklist ngắn dùng khi thiết kế và code một feature. Quy tắc chi tiết nằm trong
[`service-clean-hex-structure.md`](service-clean-hex-structure.md) và bộ tài liệu
[`spec-driven-development`](../spec-driven-development/02-spec-kit-artifact-system.md).

## 1. Bốn triết lý cần nhớ

| Triết lý | Câu hỏi chính |
|---|---|
| DDD | Nghiệp vụ dùng ngôn ngữ nào, ranh giới ở đâu, Aggregate nào bảo vệ invariant? |
| Clean Architecture | Dependency source code có luôn hướng vào core không? |
| Hexagonal | Core cung cấp/cần capability nào qua input/output port, adapter nào nối công nghệ vào port đó? |
| Clean Code | Tên có thể hiện ý định, class có một trách nhiệm và code có dễ kiểm thử không? |

DDD mô hình hóa nghiệp vụ; Clean Architecture kiểm soát dependency; Hexagonal mô tả ports/adapters;
Clean Code giữ từng class và method dễ hiểu. Có folder `domain` chưa có nghĩa là đã làm DDD.

## 2. Luồng và dependency chuẩn

```text
Web / Kafka consumer / Scheduler
  -> inbound adapter
  -> input port
  -> application use case
  -> domain model/policy
  -> output port
  -> persistence / Kafka producer / HTTP client / Redis
```

```text
adapter       -> application -> domain
configuration -> adapter + application
domain        -> Java thuần
```

- Runtime có thể đi từ core ra database, nhưng source code của core chỉ biết output-port interface.
- HTTP DTO, JPA entity/projection, Kafka payload, protobuf type và vendor exception phải dừng ở adapter.
- Domain không import Spring, JPA, Kafka, Redis, Jackson, MapStruct hoặc provider SDK.

## 3. Chọn nơi đặt một class

| Class trả lời câu hỏi gì? | Vị trí |
|---|---|
| Khái niệm, invariant, Aggregate, Value Object, Domain Event? | `domain/<aggregate-or-capability>` |
| Use case, command/query/result hoặc orchestration? | `application/<capability>` |
| Core cho phép tác nhân bên ngoài làm gì? | `application/.../port/in` |
| Core cần gì từ thế giới bên ngoài? | `application/.../port/out` |
| REST controller, Kafka listener, scheduler? | `adapter/in/<channel>` |
| JPA, Kafka publisher, HTTP/gRPC client, Redis, storage? | `adapter/out/<technology-or-provider>` |
| Spring bean/client/executor wiring? | `configuration` hoặc `config` |

Mỗi boundary có model và mapper riêng:

```text
WebRequest -> Command -> Domain Aggregate -> JpaEntity
Domain/Application Result -> WebResponse
DomainEvent -> VersionedIntegrationEvent
External DTO -> anti-corruption mapper -> internal model
```

Không dùng một `ProductDto` hoặc `ProductMapper` cho web, database, Kafka và external service.
MapStruct chỉ giảm mapping cơ học; business rule vẫn thuộc domain/application.

## 4. Khi số class, exception và bảng tăng

- Không suy ra `1 table = 1 domain model`. Table là persistence; Aggregate được chọn theo invariant,
  lifecycle và transaction/consistency boundary.
- Chia mỗi layer theo capability/Aggregate, ví dụ `domain/product`, `domain/category`,
  `application/catalog`, `adapter/in/web/catalog`, `adapter/out/persistence/product`.
- Read model như `ProductSummary` hoặc `PageMetadata` nên ở application query model khi write-domain
  bắt đầu giàu hành vi.
- Domain exception đặt gần Aggregate sở hữu luật; application exception đặt gần use case; adapter
  dịch HTTP/JPA/Kafka/vendor exception tại boundary.
- Không tạo global dumping ground như `model`, `exception`, `dto`, `mapper`, `utils` cho toàn service.
- Chỉ tạo package khi feature có class thật; không dựng sẵn cây folder để “trông đúng kiến trúc”.
- Không tách microservice chỉ vì nhiều class/bảng. Chỉ đổi service boundary khi bounded context,
  ownership, ngôn ngữ, lifecycle hoặc nhu cầu vận hành thực sự độc lập; quyết định đó cần ADR.
- Khi package boundary bắt đầu dễ bị phá, ưu tiên thêm ArchUnit test để chặn dependency sai và cycle.
  Chỉ cân nhắc Spring Modulith nếu một service thật sự phát triển thành nhiều functional module.

## 5. Dùng Spec Kit skills khi code

Skills áp dụng theo **feature hoặc coherent task group**, không chạy lại toàn bộ chuỗi cho từng class.

### Feature mới hoặc đổi observable behavior

```text
$speckit-specify
  -> $speckit-clarify
  -> $speckit-checklist
  -> human approves spec
  -> $speckit-plan
  -> human approves plan
  -> $speckit-tasks
  -> $speckit-analyze        # feature phức tạp/rủi ro
  -> human approves tasks
  -> $speckit-implement      # một phase/coherent group mỗi lượt
  -> $speckit-converge       # tìm phần còn thiếu/drift khi cần
  -> verify + record evidence
```

### Tiếp tục feature đã approved

1. Xác nhận `.specify/feature.json` trỏ đúng feature và status cho phép triển khai.
2. Đọc Constitution, `AGENTS.md`, `spec.md`, `plan.md`, `tasks.md` và contract liên quan.
3. Chọn một task hoặc coherent group; dùng `$speckit-implement` và không mở rộng scope.
4. Viết/chạy test theo plan, ghi command + scope + result; chỉ sau đó mới đánh dấu task hoàn tất.
5. Nếu phát hiện behavior chưa được spec quyết định, dừng code và quay lại clarify; đồng bộ
   spec/contract/plan/tasks rồi mới tiếp tục.

`$speckit-constitution` chỉ dùng khi thay luật toàn repository. `$speckit-taskstoissues` chỉ dùng khi
cần tạo GitHub issues. Checklist kiểm chất lượng requirement, không thay test; task `[x]` không thay
validation evidence.

## 6. Prompt ngắn dùng cho mỗi coding session

```text
Dùng active feature hiện tại. Đọc Constitution, AGENTS.md, spec.md, plan.md, tasks.md và contracts.
Chỉ triển khai task <TASK-ID hoặc coherent group> đã approved.

Giữ dependency: adapter -> application -> domain; configuration -> adapter + application.
Tổ chức class theo capability/Aggregate; không dùng HTTP DTO, JPA entity hoặc Kafka payload làm
domain model. Business invariant ở domain; orchestration ở application; framework/I/O ở adapter.

Không tự suy diễn behavior, consistency, idempotency, security, tiền, stock, retry hoặc TTL.
Nếu gặp spec gap, dừng và báo quyết định cần làm rõ. Chạy validation trong plan/tasks, ghi evidence,
và báo file đã đổi cùng rủi ro còn lại trước khi kết thúc.
```

## 7. Definition of Done tối thiểu

- Behavior khớp approved spec và contract; không có scope ngầm.
- Dependency hướng vào trong; không rò rỉ framework model qua boundary.
- Domain invariant và use case có test; adapter quan trọng có integration/contract test phù hợp.
- Migration/event/API/dependency mới đã có plan và artifact tương ứng.
- Required Maven/module validation pass và evidence được ghi nhận.

Thứ tự thẩm quyền:

```text
Constitution -> accepted ADR -> approved spec -> approved plan -> approved tasks -> code/tests/evidence
```

`AGENTS.md` quy định cách agent làm việc nhưng không được tạo behavior trái các nguồn chuẩn trên.

## Nguồn tham khảo

- [Alistair Cockburn — Hexagonal Architecture, bài gốc 2005](https://alistair.cockburn.us/hexagonal-architecture/)
- [Robert C. Martin — The Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Eric Evans — Domain-Driven Design Reference](https://www.domainlanguage.com/wp-content/uploads/2016/05/DDD_Reference_2015-03.pdf)
- [GitHub Spec Kit — official documentation](https://github.github.com/spec-kit/)
- [ArchUnit — official user guide](https://www.archunit.org/userguide/html/000_Index.html)
- [Spring Modulith — module fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html)
- [BuckPal — Spring Clean/Hexagonal example](https://github.com/thombergs/buckpal)
- [DDD by Examples — Library](https://github.com/ddd-by-examples/library)
