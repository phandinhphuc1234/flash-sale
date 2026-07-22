# 01 — Cấu trúc một Feature Specification (`spec.md`)

**Phạm vi**: Hướng dẫn viết một feature specification tương thích GitHub Spec Kit và đủ chặt cho
hệ thống microservice có concurrency, tiền, Kafka, Redis và dữ liệu phân tán.

**Nguồn chuẩn liên quan**:

- [Spec template của repository](../../.specify/templates/spec-template.md)
- [Project constitution](../../.specify/memory/constitution.md)
- [Cấu trúc bộ artifact](02-spec-kit-artifact-system.md)
- [Coding constraints](03-coding-constraints.md)

## 1. Vai trò của `spec.md`

`spec.md` trả lời hai câu hỏi:

1. **WHY** — vì sao thay đổi này cần tồn tại và kết quả mong muốn là gì?
2. **WHAT** — hệ thống phải thể hiện hành vi quan sát được nào?

`spec.md` không phải implementation plan. Trừ khi một công nghệ đã là constraint được phê duyệt,
spec không chọn class, framework, table, index, Kafka partition, Redis key, deployment manifest hoặc
chi tiết adapter. Những quyết định đó thuộc `plan.md`.

Điều không được viết ra phải được xem là **chưa biết**, không phải quyền để developer hoặc AI tự
chọn một mặc định có vẻ hợp lý.

## 2. Phần lõi và phần mở rộng

Template hiện tại của repository giữ các phần lõi của Spec Kit. Profile Flash Sale bổ sung một số
phần có điều kiện, nhưng không thay thế cấu trúc lõi.

| Phần | Mức áp dụng | Mục đích |
|------|-------------|----------|
| Metadata và status | Bắt buộc | Nhận diện feature, trạng thái và input ban đầu |
| Problem and Scope | Khuyến nghị | Tóm tắt vấn đề, in scope, out of scope và non-goals |
| Baseline References | Chỉ với brownfield | Chỉ ra hành vi hiện tại đang bị tác động |
| Requirement Delta | Chỉ khi sửa hành vi hiện có | Ghi ADDED, MODIFIED, REMOVED hoặc RENAMED |
| User Scenarios & Testing | Bắt buộc | Chia feature thành các lát ưu tiên và kiểm thử độc lập |
| Use Case detail | Theo rủi ro | Mô tả flow phức tạp, lifecycle, tiền, concurrency hoặc compensation |
| Requirements | Bắt buộc | Các nghĩa vụ có ID và có thể kiểm tra |
| Domain Model Delta | Khi đổi khái niệm nghiệp vụ | Ghi concept, state hoặc invariant thay đổi |
| Success Criteria | Bắt buộc | Kết quả đo được, ưu tiên solution-neutral |
| Human Decisions Required | Khi còn điều chưa rõ | Giữ câu hỏi mở và chặn đúng gate |
| Constitutional Constraints | Bắt buộc | Đối chiếu feature với luật toàn repository |

Không tạo heading có điều kiện nếu nó không có nội dung. File rỗng và section rỗng tạo cảm giác
kiểm soát giả, không tăng chất lượng yêu cầu.

### 2.1. Mapping bốn tầng yêu cầu vào Spec Kit

Profile dùng bốn tầng nhưng không bắt buộc tách thành bốn file:

| Tầng | Vị trí trong `spec.md` | Vai trò |
|------|------------------------|---------|
| Business Requirement | Problem/Scope, linked BR và Success Criteria | Vì sao làm và kết quả business nào cần đạt |
| Use Case | User Story và Use Case detail khi cần | Actor, trigger, flow và postcondition |
| Domain/Entity Model | Key Entities và Domain Model Delta | Ngôn ngữ, state, lifecycle và invariant nghiệp vụ |
| Acceptance Criteria | Acceptance Scenarios | Hành vi quan sát được dùng để review và test |

User Story của Spec Kit vẫn là lát delivery có priority và independent test. Use Case làm flow chính
xác hơn bên trong lát đó; nó không thay cơ chế User Story mà `tasks.md` dùng để tổ chức phase.

## 3. Khung `spec.md` chuẩn

Khung sau mở rộng template hiện tại mà vẫn giữ nguyên các heading Spec Kit cần dùng:

```markdown
# Feature Specification: <Feature name>

**Feature Branch**: `NNN-kebab-name`
**Created**: YYYY-MM-DD
**Status**: Draft | Approved | Implementing | Verified | Retired
**Input**: User description: "<original request>"
**Linked Business Requirements**: `BR-NNN` hoặc `<documented justification>`
**Business Owner**: <name or role>
**Required Reviewers**: <business/technical/security roles>

## Problem and Scope

### Problem Statement
<Vấn đề hoặc cơ hội; chưa mô tả giải pháp kỹ thuật>

### In Scope
- ...

### Out of Scope
- ...

### Non-goals
- ...

## Baseline References *(brownfield only)*
- <BR/UC/context/contract/NFR đang bị tác động>

## Requirement Delta *(when existing behavior changes)*

### ADDED
- ...

### MODIFIED
- Before: ...
- After: ...

### REMOVED
- ...

### RENAMED
- ...

## User Scenarios & Testing *(mandatory)*

### User Story 1 - <Outcome> (Priority: P1)

As a <actor>, I want <capability>, so that <value>.

**Why this priority**: ...
**Independent Test**: ...
**Use Case IDs**: `UC-CTX-001` *(only when the profile uses UC IDs)*

#### Use Case UC-CTX-001: <Business verb + object> *(when detailed flow is needed)*

**Trigger**: ...
**Preconditions**: ...

**Main Flow**:
1. ...

**Alternative Flows**:
- A1. ...

**Exceptions**:
- E1. ...

**Postconditions**:
- Success: ...
- Minimal guarantee on failure: ...

**Acceptance Scenarios**:
1. **UC-CTX-001/AC-01** — **Given** ..., **When** ..., **Then** ...
2. **UC-CTX-001/AC-02** — **Given** ..., **When** ..., **Then** ...

### Edge Cases
- ...

## Requirements *(mandatory)*

### Functional Requirements
- **FR-001**: The system MUST ...

### Non-Functional Requirements *(when applicable)*
- **NFR-PERF-001**: ...
- **NFR-REL-001**: ...

### Key Entities *(when the feature involves domain data)*
- **Concept**: business meaning, identity, lifecycle and relationships

### Domain Model Delta *(when domain language or invariants change)*
- Added concept/state/invariant: ...
- Modified concept/state/invariant: ...

## Success Criteria *(mandatory)*

### Measurable Outcomes
- **SC-001**: ...

## Dependencies and Compatibility *(when applicable)*
- Affected upstream/downstream behavior: ...
- Contract compatibility expectation: ...

## Assumptions
- ...

## Human Decisions Required *(only while questions remain)*
- **[NEEDS CLARIFICATION: <question>]**

| Priority | Why it blocks | Options and trade-offs | Owner | Decision deadline |
|----------|---------------|------------------------|-------|-------------------|
| P0 | ... | ... | ... | ... |

## Constitutional Constraints *(mandatory)*
- **Service ownership**: ...
- **External ingress**: ...
- **API/event contracts**: ...
- **Durable and hot-path data**: ...
- **Messaging reliability**: ...
- **Root infrastructure ownership**: ...
- **Observability**: ...
- **Verification**: ...
- **Architecture decisions**: ...

## Approval and History
- YYYY-MM-DD — Draft created
- YYYY-MM-DD — Approved by <roles>
```

`Business Owner`, `Required Reviewers`, UC IDs, baseline delta và approval history là profile mở
rộng. Spec Kit không tự động bắt buộc các field này; team chỉ dùng khi chúng tạo ra giá trị kiểm soát
thực tế.

## 4. Cách dùng User Story, Use Case, Requirement và Success Criterion

Các loại nội dung này không thay thế nhau:

| Thành phần | Câu hỏi nó trả lời | Ví dụ |
|------------|--------------------|-------|
| User Story | Lát giá trị nào cần ưu tiên và có thể kiểm thử độc lập? | Người mua giữ được quyền mua stock |
| Use Case | Actor/trigger/flow/lỗi/postcondition cụ thể là gì? | Reserve stock cho một campaign |
| Acceptance Scenario | Hành vi quan sát được nào chứng minh use case đúng? | Duplicate key trả lại reservation cũ |
| Functional Requirement | Nghĩa vụ chuẩn hóa nào hệ thống phải đáp ứng? | Mỗi idempotency key chỉ tạo tối đa một reservation |
| Success Criterion | Kết quả đo được của feature là gì? | Không oversell trong load profile đã duyệt |

Với feature nhỏ, một User Story và các Acceptance Scenarios có thể đủ. Với stock, order, payment,
security hoặc lifecycle phức tạp, thêm Use Case detail để reviewer nhìn được main flow, alternative,
exception và failure guarantee.

User Story phải là một lát có thể kiểm thử độc lập; nó không phải một câu ba dòng đứng một mình rồi
được dùng làm implementation specification.

## 5. Luật viết Acceptance Scenario

Mỗi acceptance scenario chỉ nên chứng minh một hành vi quan sát được.

Một feature rủi ro cao cần chủ động xem xét, nhưng không máy móc thêm, các nhóm sau:

- happy path;
- validation và authorization;
- concurrency hoặc race condition;
- duplicate request và idempotency;
- timeout hoặc expiry;
- partial failure và retry;
- event redelivery hoặc out-of-order;
- compensation hoặc reconciliation;
- audit và observability nếu đó là hành vi vận hành bắt buộc.

Nếu feature tác động stock, order hoặc payment mà bỏ qua concurrency, duplicate hoặc partial failure,
spec phải giải thích vì sao chúng không áp dụng.

Acceptance Scenario không được dùng câu mơ hồ như:

- “hệ thống hoạt động đúng”;
- “API đủ nhanh”;
- “xử lý lỗi phù hợp”;
- “theo mặc định”.

## 6. Baseline và Requirement Delta

`Baseline References` và `Requirement Delta` là mở rộng hữu ích cho brownfield, không phải artifact
lõi bắt buộc của Spec Kit.

Khi dùng `MODIFIED`, luôn ghi cả hành vi trước và sau:

```markdown
### MODIFIED

- **Before**: duplicate reserve request returns conflict.
- **After**: duplicate reserve request with the same idempotency key returns the original result.
```

Không chỉ chép hành vi mới, vì reviewer sẽ không biết điều gì thực sự thay đổi.

Nếu repository chưa có baseline canonical, tham chiếu code, contract, production behavior hoặc spec
đã được chấp nhận và đánh dấu mức độ xác minh. Không tự tạo một baseline tưởng tượng để điền đủ mẫu.

## 7. Domain model không phải data model

Trong spec, `Key Entities` và `Domain Model Delta` mô tả ngôn ngữ nghiệp vụ:

- ý nghĩa của concept;
- identity ở mức nghiệp vụ;
- state và lifecycle;
- relationship;
- invariant;
- thuật ngữ dễ nhầm ở context khác.

Các nội dung sau thuộc `data-model.md` hoặc `plan.md`:

- tên table hoặc collection;
- SQL datatype;
- index, foreign key và migration;
- JPA annotation;
- Redis key format;
- serialization framework.

Không phải mọi danh từ trong spec đều là Entity hoặc Aggregate Root. Aggregate boundary chỉ được
chọn trong plan sau khi phân tích invariant và transaction boundary.

## 8. Đánh dấu điều chưa rõ

Dùng marker canonical mà Spec Kit nhận diện trong requirement:

```text
[NEEDS CLARIFICATION: <câu hỏi cụ thể>]
```

Trong `Human Decisions Required`, theo dõi thêm priority:

- P0: chặn toàn bộ feature; liên quan correctness, tiền, dữ liệu hoặc security.
- P1: chặn plan hoặc một nhánh implementation chính.
- P2: phần độc lập có thể tiếp tục; câu hỏi vẫn phải có owner.

Mỗi clarification phải có câu hỏi, lý do cần quyết định, các option, trade-off, owner và deadline.

Priority của decision không phải priority của User Story: `User Story P1` là thứ tự delivery, còn
`Decision P1` là mức độ một câu hỏi chặn plan/implementation.

- P0 hoặc P1 chưa được quyết định: không chuyển feature sang implementation.
- P2: chỉ triển khai phần không phụ thuộc câu hỏi; có thể ghi thêm `[OPEN QUESTION - P2]` trong bảng
  quản lý, nhưng requirement marker vẫn dùng cú pháp canonical.
- AI có thể phân tích option nhưng không tự chọn policy về tiền, TTL, purchase limit, permission,
  compensation, consistency hoặc idempotency semantics.

Sau khi có quyết định, cập nhật nội dung canonical trong spec và giữ decision/history cần thiết;
không chỉ xóa marker rồi để quyết định nằm trong chat.

## 9. Ranh giới giữa `spec.md` và `plan.md`

| Nội dung | `spec.md` | `plan.md` |
|----------|-----------|-----------|
| Business outcome và scope | Có | Tham chiếu |
| Actor, use case, acceptance behavior | Có | Map sang thiết kế/test |
| Business invariant | Có | Chọn transaction/concurrency mechanism |
| Framework, class, package | Không, trừ approved constraint | Có |
| PostgreSQL/Redis/Kafka design | Không, trừ constitutional constraint | Có |
| Schema, index, migration | Không | Có hoặc `data-model.md` |
| Retry, ordering, outbox implementation | Nêu behavior/failure guarantee | Thiết kế chi tiết |
| Testable outcome | Có | Chọn test layer và command |

Nếu plan cần một business decision chưa có trong spec, quay lại clarify spec. Không dùng lựa chọn kỹ
thuật để che một rule nghiệp vụ chưa rõ.

## 10. Definition of Ready cho `spec.md`

Một spec sẵn sàng chuyển sang plan khi:

- [ ] Problem, in scope và out of scope đọc được mà không cần xem chat.
- [ ] Các User Story được ưu tiên và kiểm thử độc lập.
- [ ] Use Case phức tạp có actor, trigger, flow, exception và postcondition.
- [ ] Acceptance Scenarios quan trọng cụ thể, quan sát được và solution-neutral.
- [ ] FR/SC có ID ổn định và không mâu thuẫn.
- [ ] Domain terms và invariants liên quan đã rõ.
- [ ] Contract, compatibility và data impact đã được nhận diện, chưa cần thiết kế chi tiết.
- [ ] Không còn P0/P1 chưa có quyết định.
- [ ] Constitutional Constraints đã được điền, kể cả mục không áp dụng và lý do.
- [ ] Business reviewer đã xác nhận WHAT/WHY.

Checklist kiểm chất lượng yêu cầu nằm trong `checklists/`; nó kiểm cách viết spec, không chứng minh
implementation đã chạy.

## 11. Anti-pattern

- Viết spec sau code để hợp thức hóa implementation.
- Để user story ba dòng làm source of truth duy nhất.
- Trộn HOW vào WHAT/WHY đến mức spec chỉ còn là thiết kế kỹ thuật.
- Để AI tự chọn TTL, quota, refund, compensation, permission hoặc consistency semantics.
- Ghi cùng một business rule ở nhiều nơi mà không chỉ ra owner canonical.
- Dùng tên service, table hoặc message class thay cho ubiquitous language.
- Biến exception thành nơi hợp thức hóa bug hiện tại.
- Tạo một spec khổng lồ qua nhiều bounded context nhưng không có lát release độc lập.
- Tạo mọi section và artifact dù không áp dụng.

## 12. Template Business Requirement

Business Requirement trả lời **vì sao làm** và kết quả business nào cần đạt. Nó phải
solution-neutral; Kafka, Redis, table hoặc class chỉ xuất hiện nếu đó là constraint bất biến đã được
phê duyệt.

Với feature nhỏ, có thể đặt BR summary trực tiếp trong `Problem and Scope`. Với product baseline đã
có governance, BR có thể là artifact canonical riêng và feature spec chỉ link tới nó.

```markdown
# BR-NNN: <Tên kết quả kinh doanh>

**Status**: Draft | Approved | Achieved | Retired
**Owner**: <business owner>
**Reviewers**: <required stakeholders>

## Business Problem
<Vấn đề hoặc cơ hội hiện tại; nêu evidence hoặc trạng thái chưa xác minh>

## Goal
<Kết quả mong muốn, không mô tả implementation>

## Success Metrics

| Metric | Baseline | Target | Measurement Window | Data Source | Owner |
|--------|----------|--------|--------------------|-------------|-------|
| ... | unknown | ... | ... | ... | ... |

## In Scope
- ...

## Out of Scope
- ...

## Constraints and Known Assumptions
- ...

## Linked Use Cases
- `UC-CTX-001`
```

Nếu baseline metric chưa biết, ghi `unknown` cùng owner và cách xác minh. Không chế số để BR trông
hoàn chỉnh. Mỗi Use Case phải link về một BR hoặc được đánh dấu `technical-enabler` kèm
justification.

## 13. Template Domain Concept

Domain concept mô tả danh từ, state và invariant nghiệp vụ; đây không phải ERD hoặc JPA model.

```markdown
### <Concept Name>

- **Kind**: entity | value object | policy | domain event | aggregate candidate
- **Meaning**: <khái niệm đại diện điều gì trong bounded context này>
- **Identity**: <identity ở mức nghiệp vụ, nếu có>
- **Responsibilities**: <rule mà concept chịu trách nhiệm>
- **States**: <các trạng thái nghiệp vụ>
- **Relationships**: <quan hệ với concept khác>
- **Invariants**: <điều luôn phải đúng>
- **Lifecycle**: <được tạo, chuyển trạng thái và kết thúc ra sao>
- **Not the same as**: <khái niệm dễ nhầm ở context khác>
- **Canonical term**: <tên được dùng>
- **Disallowed aliases**: <tên mơ hồ hoặc bị cấm>
```

`aggregate candidate` chỉ là giả thuyết nghiệp vụ. Aggregate/transaction boundary được quyết định
trong plan sau khi phân tích invariant; không biến mọi danh từ thành Aggregate Root.

## 14. Template Ubiquitous Language

Khi feature thêm hoặc đổi thuật ngữ, dùng bảng sau trong spec hoặc glossary canonical của bounded
context:

| Canonical Term | Meaning in This Context | States/Qualifiers | Not This | Allowed Synonyms | Forbidden/Ambiguous Terms | Owner |
|----------------|-------------------------|-------------------|----------|------------------|---------------------------|-------|
| Reservation | Quyền mua stock được giữ tạm thời | Pending, Confirmed, Expired | Payment authorization hold | Stock hold | Booking | Inventory owner |

Quy tắc:

- một term có đúng một nghĩa trong một bounded context;
- cùng từ có nghĩa khác ở context khác phải được định nghĩa riêng và ghi collision trong context map;
- tên table, transport message hoặc service không thay thế business term;
- class, method, event và test nên ưu tiên canonical term;
- thay đổi public event/database naming vẫn cần compatibility/migration plan, dù business term được
  đổi cho rõ hơn.
