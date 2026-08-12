# 02 — Cấu trúc bộ Artifact Spec Kit trong hệ thống

**Phạm vi**: Giải thích cấu trúc file, thứ tự nguồn chuẩn và lifecycle của Spec-Driven Development
trong repository này, đồng thời phân biệt lõi GitHub Spec Kit với profile mở rộng cho dự án rủi ro
cao.

**Tài liệu liên quan**:

- [Cấu trúc một spec.md](01-feature-spec-structure.md)
- [Coding constraints](03-coding-constraints.md)
- [GitHub Spec Kit](https://github.com/github/spec-kit)
- [Spec Kit documentation](https://github.github.com/spec-kit/)

## 1. Lõi Spec Kit và profile của repository

Lõi Spec Kit là chuỗi:

```text
Constitution -> Spec -> Plan -> Tasks -> Implement
```

Các lệnh `clarify`, `checklist` và `analyze` là quality commands chính thức nhưng không thuộc bốn
bước tạo/triển khai artifact tối thiểu. Repository có thể nâng chúng thành gate bắt buộc theo mức
rủi ro. `converge` là capability được cài trong repository này để đối chiếu code với artifacts và
bổ sung task còn thiếu; không nên giả định mọi dự án Spec Kit đều có nó.

Ba nhãn được dùng trong guide này:

| Nhãn | Ý nghĩa |
|------|---------|
| **CORE** | Cấu trúc hoặc command lõi của Spec Kit |
| **REPO** | Quy ước bắt buộc của repository hiện tại |
| **PROFILE** | Mở rộng khuyến nghị theo rủi ro; muốn bắt buộc ở dự án khác phải ghi vào governance/template |

## 2. Cấu trúc tương thích repository hiện tại

```text
flash-sale/
├── .specify/
│   ├── memory/
│   │   └── constitution.md                 # REPO: governance cao nhất
│   ├── templates/
│   │   ├── spec-template.md                # CORE template hiện hành
│   │   ├── plan-template.md
│   │   ├── tasks-template.md
│   │   ├── checklist-template.md
│   │   └── overrides/                      # PROFILE: project-local overrides, nếu dùng
│   ├── scripts/
│   │   └── powershell/                     # CORE mechanics cho Windows
│   ├── workflows/
│   │   ├── speckit/workflow.yml            # CORE cycle + review gates hiện tại
│   │   └── flash-sale-risk/workflow.yml     # PROFILE: risk-gated cycle cho feature mới
│   └── feature.json                        # CORE/local state: active feature pointer
├── presets/
│   └── flash-sale-risk-profile/             # PROFILE source package; chưa phải installed state
├── specs/
│   └── NNN-kebab-feature/
│       ├── spec.md                          # CORE: WHAT/WHY
│       ├── plan.md                          # CORE: HOW
│       ├── tasks.md                         # CORE: executable work graph
│       ├── research.md                      # CONDITIONAL
│       ├── data-model.md                    # CONDITIONAL
│       ├── contracts/                       # CONDITIONAL
│       ├── quickstart.md                    # CONDITIONAL
│       └── checklists/
│           └── requirements.md              # QUALITY artifact
├── contracts/                               # REPO: accepted cross-feature contract catalog, nếu dùng
├── docs/
│   └── adr/                                 # REPO: architecture decisions
├── AGENTS.md                                # REPO: operational instructions for agents
├── services/
└── infra/
```

Không đổi `infra/` thành `platform/` hoặc `deploy/` chỉ để giống một profile chung. Constitution và
[ADR 0001](../adr/0001-root-infrastructure-ownership.md) của repository đã chọn root `infra/` làm
ownership boundary.

## 3. Ba lớp thông tin chuẩn

### 3.1. Governance

| Artifact | Trả lời câu hỏi | Quy tắc |
|----------|-----------------|---------|
| `.specify/memory/constitution.md` | Luật nào luôn đúng trong repository? | Nguồn chuẩn cao nhất; amendment có version và Sync Impact Report |
| `AGENTS.md` | Agent phải thao tác thế nào để tuân thủ luật? | Bản hướng dẫn rút gọn; không được mâu thuẫn hoặc vượt constitution |
| `docs/adr/*.md` | Vì sao một quyết định kiến trúc được chọn? | Chứa context, alternatives và consequences; không thay feature requirements |

Không đặt TTL reservation, purchase limit hoặc refund policy vào constitution nếu chúng là business
rule có thể thay đổi theo feature.

### 3.2. Feature/change workspace

`specs/NNN-kebab-feature/` mô tả thay đổi đang được đề xuất hoặc triển khai. Nó không tự động trở
thành mô tả production hiện tại chỉ vì file đã tồn tại.

```text
spec.md -> plan.md -> tasks.md -> code/tests/evidence
```

- `spec.md` sở hữu WHAT/WHY.
- `plan.md` sở hữu thiết kế HOW và Constitution Check.
- `tasks.md` sở hữu thứ tự công việc, dependency, file path và validation checkpoints.
- Code, tests và contract là implementation/evidence phải khớp các artifacts trên.

### 3.3. Product baseline — tùy chọn

`specs/_baseline/` trong profile đính kèm là một mở rộng hợp lý cho brownfield, nhưng không phải cấu
trúc CORE mà Spec Kit hiện tại tự sinh hoặc tự promote.

Chỉ thêm baseline khi team đã quyết định:

- artifact nào là canonical;
- ai có quyền cập nhật;
- trạng thái `verified/unverified` được xác định thế nào;
- feature delta được merge vào baseline ở gate nào;
- automation nào ngăn baseline đi trước production behavior.

Nếu chưa có governance đó, dùng accepted contracts, ADR, verified feature specs và code/tests làm
bằng chứng hiện trạng. Không tạo cây baseline lớn chỉ để trông đầy đủ.

## 4. Artifact bắt buộc và có điều kiện

### 4.1. Artifact lõi của mỗi feature

| Artifact | Mức | Nội dung tối thiểu | Gate |
|----------|-----|--------------------|------|
| `spec.md` | CORE + REPO | User stories, acceptance scenarios, FR, SC, assumptions, constitutional impact | WHAT/WHY được review; không còn P0/P1 |
| `plan.md` | CORE + REPO | Technical context, Constitution Check, architecture, dependencies, structure, verification | Technical review và Constitution Check pass |
| `tasks.md` | CORE + REPO | Task có ID, exact path, dependency, story/checkpoint và validation | Không có requirement quan trọng bị bỏ khỏi task graph |

Ba file này là minimum implementation chain. Implementation không bắt đầu nếu một file còn thiếu,
chưa approved hoặc mâu thuẫn.

### 4.2. Artifact có điều kiện

| Artifact | Tạo khi | Không tạo khi |
|----------|----------|---------------|
| `research.md` | Có lựa chọn kỹ thuật chưa chắc, phiên bản dễ đổi hoặc cần nguồn chính thức | Mọi quyết định đã được constitution/plan xác định rõ |
| `data-model.md` | Đổi schema, migration, persistence mapping, Redis data lifecycle | Feature không có data impact |
| `contracts/` | Thêm/sửa HTTP, Kafka, message header, error hoặc compatibility contract | Không đổi communication boundary |
| `quickstart.md` | Cần nhiều bước để chạy và verify cục bộ | Validation chỉ là một command đã rõ |
| `checklists/*.md` | Cần kiểm chất lượng yêu cầu, security, architecture hoặc release | Không dùng file rỗng để đánh dấu hình thức |
| Feature decision log | Có quyết định cục bộ đáng lưu nhưng chưa phải architecture-wide | Rationale đã nằm đầy đủ trong `research.md` hoặc `plan.md` |
| ADR | Quyết định kiến trúc, ownership hoặc cross-feature khó đảo ngược | Chi tiết implementation cục bộ, dễ đổi |

### 4.3. Traceability

Spec Kit không bắt buộc một `traceability.md` riêng cho mọi feature. Traceability tối thiểu có thể
đạt bằng ID xuyên suốt:

```text
User Story / UC -> Acceptance Scenario -> FR -> Task -> Test/Contract -> PR
```

Tạo `traceability.md` riêng khi feature có nhiều UC, nghĩa vụ audit, tiền, concurrency hoặc contract
phân tán khiến mapping trong `spec.md`, `tasks.md` và test name không còn dễ review.

Không map từng dòng code. Mức hữu ích là requirement/behavior -> task -> evidence.

## 5. Những điểm điều chỉnh từ profile đính kèm

| Đề xuất trong profile | Cách dùng bám sát Spec Kit |
|-----------------------|----------------------------|
| `specs/_baseline/` luôn tồn tại | Xem là PROFILE tùy chọn; chỉ tạo sau khi có owner và promotion process |
| `traceability.md` luôn bắt buộc | Dùng ID inline trước; file riêng chỉ cho feature cần audit sâu |
| `decisions.md` luôn tồn tại | Ưu tiên `research.md`, `plan.md`, hoặc ADR; tránh lặp rationale |
| Chuyển feature vào `_archive/` sau khi xong | Không làm mặc định; Spec Kit và `.specify/feature.json` đang trỏ trực tiếp feature directory |
| Mọi feature có `data-model.md`, contracts, quickstart | Chỉ tạo khi có data/contract/verification complexity tương ứng |
| `clarify/checklist/analyze/converge` đều là core gates | Clarify/checklist/analyze là quality commands; converge là capability của repo; policy quyết định gate nào bắt buộc |
| Root dùng `platform/` và `deploy/` | Repository này dùng `infra/` theo constitution và ADR |

Việc gắn nhãn không làm các mở rộng kém giá trị. Nó giúp mang profile sang dự án khác mà không giả
định tooling, governance hoặc risk model chưa tồn tại.

## 6. Active feature và `.specify/feature.json`

Các script hiện tại resolve active feature theo thứ tự:

1. `SPECIFY_FEATURE_DIRECTORY` nếu được cung cấp;
2. `.specify/feature.json`;
3. báo lỗi nếu không có feature context.

`feature.json` chứa đường dẫn như:

```json
{"feature_directory":"specs/001-scaffold-maven-services"}
```

Vì vậy:

- không di chuyển hoặc đổi tên active feature folder bằng tay;
- không trỏ `feature.json` vào thư mục archive không còn được workflow xử lý;
- trước khi chạy plan/tasks/implement, xác nhận active pointer đúng feature;
- không commit thay đổi pointer ngoài ý muốn nếu team xem đây là local workflow state.

Nếu team muốn archive vật lý, phải thiết kế workflow cập nhật pointer, links và tool discovery trước.
Phương án an toàn hơn là giữ numbered feature folder, cập nhật `Status`, và dùng Git history/tags để
giữ audit trail.

## 7. Template resolution và cách tái sử dụng cho nhiều dự án

Spec Kit trong repository hỗ trợ thứ tự resolve template:

1. `.specify/templates/overrides/` — project-local override;
2. `.specify/presets/<preset>/templates/` — preset theo priority;
3. `.specify/extensions/<extension>/templates/` — extension template;
4. `.specify/templates/` — core template.

Đối với một profile dùng ở vài dự án:

- dùng `overrides/` cho khác biệt chỉ thuộc một repository;
- đóng gói preset cho bộ template dùng chung nhiều repository;
- dùng extension khi cần thêm command, hook hoặc integration behavior;
- tránh sửa script CORE nếu template/preset đã giải quyết được;
- pin/ghi nhận Spec Kit version và review diff khi upgrade.

Repository hiện đã tùy chỉnh các core templates. Trước khi biến profile này thành package dùng lại,
nên chuyển phần tổ chức-specific sang override hoặc preset để upgrade Spec Kit không ghi đè hoặc tạo
merge khó kiểm soát.

Lưu ý tooling tại thời điểm viết guide: các generator PowerShell đang gọi `Resolve-Template`, tức
chọn một whole-file template theo priority. `Resolve-TemplateContent` có code cho chiến lược
`prepend`, `append` và `wrap` nhưng chưa được generator gọi. Vì vậy preset của repository này dùng
`strategy: replace` và cung cấp template hoàn chỉnh cho đến khi một tooling task có kiểm thử nối
generator với composition resolver.

## 8. Workflow được khuyến nghị

### 8.1. Core workflow hiện có

Workflow `.specify/workflows/speckit/workflow.yml` hiện chạy:

```text
specify -> review spec -> plan -> review plan -> tasks -> implement
```

Đây vẫn là flow mặc định gọn. Repository đồng thời có source workflow
`.specify/workflows/flash-sale-risk/workflow.yml` cho feature mới cần risk profile. Workflow mới thêm
clarify, hai checklist, task/analyze gate, convergence review và final evidence gate; mỗi gate vẫn cần
quyết định của con người, không tự suy ra approval từ việc file tồn tại.

### 8.2. Risk-adjusted workflow

Workflow source tương ứng nằm tại `.specify/workflows/flash-sale-risk/workflow.yml`. Nó orchestration
các command trong bảng dưới đây; nội dung spec, plan, tasks và evidence vẫn là nguồn quyết định.

| Bước | Command trong Codex skills mode | Gate |
|------|-------------------------------|------|
| 0. Governance | `$speckit-constitution` | Chỉ khi tạo hoặc đổi luật repository |
| 1. Specify | `$speckit-specify` | Sinh `spec.md`, chỉ WHAT/WHY |
| 2. Clarify | `$speckit-clarify` | Khuyến nghị mọi feature; bắt buộc profile nếu còn P0/P1 hoặc domain rủi ro cao |
| 3. Requirements checklist | `$speckit-checklist` | Kiểm chất lượng yêu cầu, không kiểm code |
| 4. Human spec review | Không có command thay thế approval | Business owner xác nhận behavior |
| 5. Plan | `$speckit-plan` | Constitution Check và technical review pass |
| 6. Plan checklist/review | `$speckit-checklist` hoặc human review | Kiểm architecture/security/release concerns khi áp dụng |
| 7. Tasks | `$speckit-tasks` | Task graph có exact paths và verification |
| 8. Human task review | Không tự động trong workflow hiện tại | Tasks được Approved trước code |
| 9. Analyze | `$speckit-analyze` | Chạy trước code cho feature phức tạp/rủi ro cao |
| 10. Human implementation approval | Không tự động | Không còn mâu thuẫn hoặc P0/P1 |
| 11. Implement | `$speckit-implement` | Làm một phase/coherent group và dừng ở checkpoint |
| 12. Converge | `$speckit-converge` | Capability repo: tìm phần còn thiếu và bổ sung tasks |
| 13. Verify | Commands trong plan/tasks | Evidence pass trước khi Done |

Với feature scaffold hoặc documentation nhỏ, clarify/checklist/analyze có thể nhẹ hơn nếu spec/plan
ghi rõ lý do. Với stock, ordering, payment, security, schema hoặc contract breaking change, các gate
chất lượng không được bỏ chỉ để đi nhanh.

## 9. Thứ tự thẩm quyền và xử lý xung đột

```text
Constitution
  -> Accepted ADRs
    -> Approved feature spec
      -> Approved plan
        -> Approved tasks
          -> Implementation and evidence
```

`AGENTS.md` tóm tắt cách làm việc nhưng không được tạo luật trái constitution.

Khi có xung đột:

1. Code khác spec: code là bug hoặc spec change chưa được duyệt; không sửa spec để hợp thức hóa âm
   thầm.
2. Plan cần behavior mới: quay lại spec và review WHAT/WHY.
3. Task khác plan: cập nhật task graph trước implementation.
4. Spec hoặc plan trái constitution: sửa thiết kế hoặc thực hiện exception/amendment process.
5. ADR cũ trái constitution mới: review và supersede ADR; không để hai nguồn cùng được coi là đúng.

## 10. Naming và ID

CORE Spec Kit yêu cầu numbered feature directory và task ID. Các ID BR/UC/AC/NFR sau là PROFILE mở
rộng, phù hợp khi cần traceability sâu:

| Artifact | Format | Ví dụ |
|----------|--------|-------|
| Feature | `NNN-kebab-name` | `002-reserve-flash-sale-stock` |
| Functional requirement | `FR-NNN` | `FR-004` |
| Success criterion | `SC-NNN` | `SC-003` |
| Business requirement | `BR-NNN` | `BR-003` |
| Use case | `UC-CTX-NNN` | `UC-INV-001` |
| Acceptance criterion | `UC-CTX-NNN/AC-NN` | `UC-INV-001/AC-04` |
| Non-functional requirement | `NFR-CAT-NNN` | `NFR-PERF-002` |
| Task | `TNNN [P?] [USN] Description (UC/AC refs)` | `T014 [P] [US1] Add duplicate test (UC-INV-001/AC-04)` |

Chỉ dùng context code đã được team xác nhận. Không để AI tự tạo bounded context mới từ tên một
service hoặc table.

## 11. Lifecycle của một thay đổi

```text
Draft spec
  -> Clarified and reviewed spec
    -> Approved plan
      -> Dependency-ordered tasks
        -> Small implementation checkpoints
          -> Verification evidence
            -> Verified feature status
```

Nếu repository áp dụng product baseline, chỉ promote behavior sau khi merge/deploy/verify theo môi
trường đã quy định. Không copy toàn bộ plan kỹ thuật tạm thời vào baseline nghiệp vụ.

## 12. Quy tắc chống drift

- Sửa nguồn canonical trước, rồi đồng bộ artifact dẫn xuất.
- Không lặp cùng business rule trong constitution, AGENTS, spec, plan và code comment.
- Constitution chứa invariant toàn repo; spec chứa behavior feature; plan chứa design; ADR chứa
  rationale kiến trúc.
- Checklist không thay acceptance tests.
- Task `[x]` không phải evidence nếu validation bắt buộc chưa pass.
- Không xóa câu hỏi hoặc decision history chỉ để tài liệu trông sạch.
- Review template/preset sau mỗi lần nâng Spec Kit.

## 13. Giới hạn enforcement hiện tại

Tài liệu phải phân biệt policy với thứ tooling đang tự cưỡng chế:

- Bundled workflow chỉ có `review-spec` và `review-plan`. Project-local `flash-sale-risk` thêm các
  human gate cho tasks/analyze, convergence và final evidence; gate xác nhận quyết định chứ không tự
  kiểm nội dung artifact.
- Prerequisite scripts chủ yếu kiểm file/directory tồn tại, không xác nhận `Status: Approved`, không
  hiểu P0/P1 và không chứng minh checklist đã hoàn tất.
- Giá trị `BRANCH` khi script fallback từ feature directory là feature identifier, không nhất thiết
  là Git branch thực tế.
- `tasks.md` checkbox là ledger, không phải build log. Repository chưa áp đặt một file evidence duy
  nhất; plan phải chọn PR/CI/quickstart result hoặc validation artifact thích hợp.
- Một artifact được script xem là optional vẫn có thể trở thành bắt buộc trong feature nếu
  spec/plan/tasks của feature đó dựa vào nó.

Source preset/workflow hiện đã được tạo, nhưng Specify CLI chưa có trên `PATH`, nên chưa thể cài preset,
đăng ký workflow hoặc chạy engine để kiểm chứng end-to-end. Không sửa thủ công
`.specify/presets/` hay `workflow-registry.json` để giả lập installed state; dùng CLI chính thức khi
kích hoạt feature kế tiếp.

## 14. Context map cho product baseline tùy chọn

Context map là bản đồ ngữ nghĩa nghiệp vụ, không phải deployment diagram. Chỉ tạo khi team đã chọn
baseline governance và có owner xác minh nội dung.

| Context | Business Responsibility | Owned Concepts | Upstream/Downstream | Conceptual Integration | Term Collisions | Status |
|---------|-------------------------|----------------|---------------------|------------------------|-----------------|--------|
| Inventory | Giữ, xác nhận và giải phóng quyền mua stock | Stock, Reservation | Campaign -> Inventory -> Ordering | Command/event contracts | Reservation khác payment hold | verified/unverified |

Quy tắc:

- bounded context là semantic/business boundary;
- service là implementation/deployment boundary;
- mapping context -> service phải được quyết định trong plan/ADR, không suy ra tự động;
- một service mới không tự động tạo ra một bounded context mới;
- collision thuật ngữ phải link tới glossary của từng context;
- `unverified` phải được giữ cho đến khi có owner/evidence, không điền giả để hoàn tất bảng.

## 15. Skeleton `plan.md` cho feature rủi ro cao

Core `plan.md` vẫn là HOW. Risk preset mở rộng nó bằng các section sau; section không áp dụng phải có
rationale ngắn thay vì nội dung giả.

```markdown
# Implementation Plan: <Feature>

**Branch/Feature ID**: ...
**Date**: YYYY-MM-DD
**Spec**: <link to feature spec.md>
**Status**: Draft | Approved | Implementing | Verified

## Summary
<Requirement + technical approach ở mức overview>

## Technical Context
- Language/version
- Primary dependencies
- Storage/messaging/cache
- Target platform
- Performance and scale

## Constitution Check
- Specification traceability
- Service/data ownership
- Communication/contracts
- Durable truth/hot path
- Messaging reliability
- Root infrastructure ownership
- Observability
- Verification
- ADR/exception impact

## Risk Classification
- Financial/correctness/security/concurrency/data/compatibility risks
- Why the risk profile applies

## Context and Service Ownership
- Bounded context
- Owning service
- Source of truth
- Upstream/downstream dependencies

## Architecture and Hexagonal Mapping
- Driving adapters
- Input ports/use-case handlers
- Domain model/invariants
- Output ports
- Driven adapters
- Approved simplifications

## Synchronous and Asynchronous Flows
- Request/event sequence
- Transaction boundaries
- Ordering/partition key

## Data and Concurrency Design
- PostgreSQL schema/migration impact
- Redis model/Lua atomicity
- Idempotency boundary
- Reconciliation/expiry

## Contract and Compatibility Plan
- HTTP/Kafka contracts
- Versioning and rollout order
- Error/header semantics

## Failure, Retry and Compensation
- Timeout/retry/DLQ
- Partial failure
- Compensation owner
- Recovery/reconciliation

## Security Boundaries
- Authentication/authorization/data exposure
- Threat-sensitive decisions already approved by spec

## Observability
- Logs, metrics, traces and alerts
- Trace propagation path
- Operational endpoint impact

## Migration and Rollback
- Forward migration
- Backward compatibility/coexistence
- Rollback or mitigation

## Verification Strategy
| Acceptance/Requirement | Unit | Integration | Contract | E2E | Load/Concurrency |
|------------------------|------|-------------|----------|-----|------------------|

## Project Structure
<Exact paths>

## Complexity Tracking
<Only constitutional exceptions with rejected simpler alternative>
```

Plan không được chọn business behavior mới. Nếu retry, timeout, compensation hoặc consistency choice
thay observable behavior, quay lại spec và clarify trước.

## 16. Cấu trúc và luật của `tasks.md`

Giữ format lõi của Spec Kit để tooling vẫn tổ chức phase theo User Story:

```text
- [ ] T014 [P] [US1] Add duplicate-reservation contract test at <exact/path>
      (UC-INV-001/AC-04)
```

Quy tắc:

1. Task ID tăng dần và ổn định trong feature.
2. `[P]` chỉ dùng khi không sửa cùng file và không phụ thuộc output chưa hoàn thành.
3. `[USN]` giữ mapping với independently testable delivery slice của Spec Kit.
4. UC/AC/NFR ID là trace reference bổ sung, không thay `[USN]`.
5. Mỗi task có action rõ, exact path và verification/evidence expectation.
6. Contract/test task đứng trước implementation tương ứng khi approved plan yêu cầu test-first.
7. Mỗi vertical slice kết thúc bằng checkpoint chạy được.
8. Không dùng task mơ hồ như “implement service”, “handle edge cases” hoặc “add tests”.
9. Thêm task contract, baseline promotion, migration hoặc validation evidence chỉ khi áp dụng.
10. `[x]` chỉ được đánh dấu sau required validation; checkbox không thay test log.

## 17. Prompt vận hành

### Trước specify

```text
Tạo feature spec theo Flash Sale SDD Profile và active risk classification.
Chỉ mô tả WHAT và WHY; giữ User Stories ưu tiên và independently testable.
Liên kết BR hoặc documented justification, xác định scope, Use Cases/AC liên quan,
domain vocabulary và constitutional impact.
Dùng [NEEDS CLARIFICATION: ...] cho business rule, consistency, TTL, limit,
compensation, security hoặc idempotency semantics chưa có bằng chứng. Ghi priority,
options, owner và deadline trong Human Decisions Required; không tự chọn đáp án.
```

### Trước plan

```text
Đọc constitution, AGENTS, approved spec và contracts/baseline được tham chiếu.
Lập HOW mà không thêm business behavior. Phân tích context/service ownership,
source of truth, transaction/idempotency/concurrency boundary, retry/ordering,
compensation/reconciliation, compatibility, security, observability,
migration/rollback và mapping acceptance behavior sang test layers.
Nếu plan cần quyết định chưa có trong spec, dừng và đưa clarification.
```

### Trước implement

```text
Chỉ triển khai task hoặc coherent group đã được phê duyệt trong phase hiện tại.
Đối chiếu User Story/UC/AC/FR trước khi sửa. Tuân theo test ordering trong plan/tasks.
Không mở rộng scope hoặc thay business rule trong code. Khi phát hiện spec gap,
dừng, cập nhật spec/plan/contracts/tasks theo approval workflow.
Sau khi hoàn thành, báo file đổi, command/test đã chạy, exit/result evidence và rủi ro còn lại;
không tự bắt đầu phase tiếp theo.
```

## 18. Lộ trình áp dụng profile

1. Giữ nguyên feature lịch sử đã hoàn tất; không regenerate theo template mới.
2. Cài hoặc kích hoạt `flash-sale-risk-profile` cho feature tiếp theo có financial, correctness,
   security, concurrency, data hoặc compatibility risk.
3. Chạy workflow `flash-sale-risk` thay cho bundled workflow khi risk profile áp dụng.
4. Pilot bằng một Use Case thật sau khi P0/P1 liên quan đã được quyết định.
5. Chỉ tạo product baseline tối thiểu khi đã có owner và promotion process.
6. Review preset/workflow sau pilot rồi mới phổ biến sang repository khác.

Trình tự kích hoạt cho feature mới, sau khi cài Specify CLI tương thích với
`.specify/init-options.json`:

```powershell
specify preset add --dev .\presets\flash-sale-risk-profile --priority 5
specify preset info flash-sale-risk-profile
specify preset resolve spec-template
specify preset resolve plan-template
specify preset resolve tasks-template

specify workflow run .specify\workflows\flash-sale-risk\workflow.yml `
  -i spec="<business outcome, users, scope, and known constraints>" `
  -i integration=codex
```

Chỉ chạy chuỗi này khi đã có mô tả cho feature kế tiếp. Không chạy generator, không đổi active pointer
và không amend artifact trong `specs/001-scaffold-maven-services/`.

Metrics tháng đầu:

| Metric | Cách đo | Mục đích |
|--------|---------|----------|
| Trace Ratio | PR có User Story/UC/FR reference / tổng PR feature | Đo traceability thực tế |
| Acceptance Evidence Coverage | Acceptance behavior có test/contract evidence / tổng behavior quan trọng | Phát hiện orphan criteria |
| Clarification Escape Rate | Decision nghiệp vụ chỉ được phát hiện sau khi code bắt đầu | Đo chất lượng specify/clarify |
| Spec Drift | PR đổi observable behavior nhưng spec/contract không đổi | Phát hiện code đi trước intent |

Không đo số dòng spec; tài liệu dài không đồng nghĩa requirement rõ.
