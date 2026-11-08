# Domain spec — <Tên feature>

Nguồn quy trình: [.ai/workflows/feature-spec-loop.md](../../.ai/workflows/feature-spec-loop.md).
Nguồn nghiệp vụ/actor xác nhận, ngày, trạng thái quyết định: ghi cụ thể; không tự nâng PROPOSED thành CONFIRMED.

## 1. Domain Overview

- Business Context: thực trạng nghiệp vụ trước khi có tính năng.
- Business Goals: vấn đề cần giải quyết, giá trị, người nhận giá trị, hành vi/quy trình cần đổi và kết quả mong muốn.
- Target Users: đối tượng sử dụng.

## 2. Scope

- In Scope: phần tính năng thực hiện.
- Out of Scope: phần không thực hiện, giới hạn V1 và hệ thống liên quan.

## 3. Actors & Roles

- Actor List và Role Definitions: ai làm gì, vai trò nào.
- Permissions / Responsibilities: quyền, trách nhiệm và scope campus/class/child theo server.

## 4. Domain Concepts

Các concept/object chính; định nghĩa, chức năng và trách nhiệm từng concept. Dẫn glossary khi đã có.

## 5. Domain Relationships

Concept liên quan thế nào, cardinality và ownership; mục này cùng mục 4 là đầu vào thiết kế DB, chưa tự quyết định bảng vật lý.

## 6. User Requirements

- User Stories / user flow: người dùng muốn đạt điều gì.
- Feature Requirements: cần những tính năng nào.
- Functional Requirements: hệ thống phải thực hiện gì để cung cấp tính năng.

## 7. Business Rules

Rule ID hiện có, nội dung liên quan, trạng thái và nguồn; rule mới theo BUSINESS_RULES.md. Không đánh số BR riêng thay ID CareNest nếu đã có rule tương ứng.

## 8. Business Processes

Process List; Main Flows; Alternative / Exception Flows; Process Outcomes và giá trị cho nghiệp vụ. Phân biệt bước ngoài hệ thống [B] với bước hệ thống [S].

## 9. State / Lifecycle

State-based Concepts, State Definitions, State Transitions (sự kiện gây chuyển trạng thái) và Transition Rules (điều kiện cho phép/chặn).

## 10. Business Constraints

- Business Constraints: giới hạn nghiệp vụ.
- Policy Constraints: chính sách trường/team và quy định hiện hành đã có nguồn.
- Data / Operational Constraints: ràng buộc dữ liệu và vận hành, phụ thuộc hệ thống khác.

## 11. Non-Functional Requirements

Performance, Security, Availability, Usability và NFR khác thực sự liên quan; dùng tiêu chí đo được, không tự hứa SLA chưa chốt.

## 12. Acceptance Criteria

AC-ID Given/When/Then; map tới rule/requirement và ca kiểm chứng, gồm luồng thành công, lỗi, quyền và dữ liệu cũ khi liên quan.

## 13. Domain Terminology

Thống nhất từ vựng tiếng Việt và identifier; dẫn GLOSSARY.md, ghi rõ thuật ngữ mới hoặc dễ nhầm.

## 14. Assumptions & Open Questions

- Assumptions: tách giả định phục vụ phân tích khỏi quyết định user đã chốt.
- Unresolved Questions: câu hỏi, mức chặn, người cần xác nhận; ghi câu trả lời và nguồn/ngày khi được chốt.
- Không code khi còn câu hỏi nghiệp vụ chặn triển khai; tuân thủ clarify-business.md.
