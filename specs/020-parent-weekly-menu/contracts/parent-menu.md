# Contract — thực đơn phụ huynh

Tiền tố API và `ResponseJson` như hiện có. Cả ba: `hasRole('PARENT')`, lỗi quyền 403 / 4108.

## `GET /children/{childId}/menu?weekStart=YYYY-MM-DD`
`data`:

```json
{
  "childId": 1,
  "weekStart": "2026-11-23",
  "published": true,
  "mealsHidden": false,
  "unmatchedAllergies": ["Dâu tây"],
  "days": [
    {
      "date": "2026-11-23",
      "enrolled": true,
      "dayOff": false,
      "meals": [
        {
          "session": "LUNCH",
          "dishes": [
            {
              "name": "Cá sốt cà chua",
              "allergens": [{ "id": 4, "code": "FISH", "name": "Cá" }],
              "childAllergens": [{ "id": 4, "code": "FISH", "name": "Cá" }]
            }
          ],
          "photo": { "photoId": 10, "uploadedAt": "2026-11-23T04:05:00Z" }
        }
      ]
    }
  ]
}
```

- `days` luôn 5 phần tử. `meals` rỗng khi chưa công bố, ngày nghỉ, chưa học hoặc `mealsHidden`.
- Bữa xuất hiện theo thứ tự `MORNING`, `LUNCH`, `AFTERNOON`, chỉ bữa của lớp, có món hoặc có ảnh. `photo` null khi chưa đăng.
- `mealsHidden=true`: con không báo ăn và tuần từ hiện tại trở đi.
- `childAllergens` và `unmatchedAllergies` rỗng khi con không báo ăn hoặc tuần chưa công bố.

## `GET /children/{childId}/meal-photos/{photoId}/url`
`data`: `{ url, expiresAt }` (`FileUrlResponse`).

## `GET /children/{childId}/meal-photos/{photoId}/content`
Byte ảnh, `Cache-Control: no-store, private`, `X-Content-Type-Options: nosniff`.

Ảnh không hiển thị được cho con (AC-7) ⇒ 404 / 6068, không phân biệt với ảnh không tồn tại.

## Client
- APP `parent_menu/`: thay `FakeWeeklyMenuSource`; bỏ `time`, `energyNote`, `postedBy`, "suất thay thế"; ảnh theo từng bữa; hiện lý do khi trống.
- APP tải ảnh theo `meals[].photo.photoId` bằng `/url` (liên kết ký sẵn, có `expiresAt`) hoặc `/content` (kèm token); `childId` kiểu số.
- APP lý do trống: `published=false` chưa công bố; `mealsHidden=true` con không báo ăn; `enrolled=false` chưa học; `dayOff=true` ngày nghỉ.
- Ô "Thực đơn tuần" ở Trang chủ phụ huynh dùng cùng API, không truyền `weekStart`.
- Web: không đổi.
