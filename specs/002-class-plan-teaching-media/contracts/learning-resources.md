# Learning resources API

Base /education-plans/class-docs/theme/{docKey}/learning-resources.

| Method | Suffix | Contract |
| --- | --- | --- |
| GET | empty | metadata list; reader active only, editor all |
| POST | empty | multipart file/activityKey/activitySignature/activityLabel, X-Request-Id UUID; metadata, idempotent |
| PATCH | /{id}/activity | binding key/signature/label JSON; metadata |
| DELETE | /{id} | detach, giữ tệp để gắn lại |
| GET | /{id}/content | protected binary Resource, Range/private-no-store/nosniff |

Metadata id,docKey,activityKey,activitySignature,activityLabel,fileName,contentType,byteSize,uploadedBy,createdAt,active; không storage path/key.
Read principal/VP/teacher trong scope; mutate assigned teacher DRAFT/REJECTED. Key/goal phải thuộc saved theme/year; stale signature409. Existing ApiCode400/403/404/409/413/415/503. Web consume, Mobile contract cũ không đổi.
