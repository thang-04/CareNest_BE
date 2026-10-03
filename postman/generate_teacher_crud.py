"""
Builds CareNest_Teacher_CRUD.postman_collection.json: teacher account CRUD, used with CareNest.postman_environment.json.
Run: python postman/generate_teacher_crud.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))


def item(name, method, path, token=None, body=None, tests=(), pre=None, desc=None):
    headers = [{"key": "X-Client-Type", "value": "mobile"}]
    if token:
        headers.append({"key": "Authorization", "value": "Bearer {{" + token + "}}"})
    p, _, q = path.partition("?")
    url = {"raw": "{{baseUrl}}" + path, "host": ["{{baseUrl}}"], "path": [x for x in p.split("/") if x]}
    if q:
        url["query"] = [{"key": k, "value": v} for k, v in (kv.split("=", 1) for kv in q.split("&"))]
    req = {"method": method, "header": headers, "url": url}
    if body is not None:
        headers.append({"key": "Content-Type", "value": "application/json"})
        req["body"] = {"mode": "raw", "raw": json.dumps(body, ensure_ascii=False, indent=2)}
    if desc:
        req["description"] = desc
    events = [{"listen": "test", "script": {"type": "text/javascript", "exec": [
        "let b = {}; try { b = pm.response.json(); } catch (e) {}", "const d = b.data;", *tests]}}]
    if pre:
        events.insert(0, {"listen": "prerequest", "script": {"type": "text/javascript", "exec": pre}})
    return {"name": name, "event": events, "request": req}


def ok(status=200):
    return [f"pm.test('HTTP {status}', () => pm.response.to.have.status({status}));",
            "pm.test('code = 0', () => pm.expect(b.code).to.eql(0));"]


def fail(status, code):
    return [f"pm.test('HTTP {status}', () => pm.response.to.have.status({status}));",
            f"pm.test('code = {code}', () => pm.expect(b.code).to.eql({code}));"]


def save(var, expr):
    return f"pm.environment.set('{var}', {expr});"


items = [
    item("1. Login Hiệu trưởng", "POST", "/api/auth/login",
         body={"identifier": "{{principalEmail}}", "password": "{{principalPassword}}"},
         tests=ok() + [save("principalToken", "d.accessToken")]),

    # ---- Create ----
    item("2. Tạo giáo viên", "POST", "/api/management/staff", "principalToken",
         body={"email": "{{teacherEmail}}", "fullName": "Cô Lan", "phoneNumber": "{{teacherPhone}}",
               "password": "{{teacherPassword}}", "role": "TEACHER", "position": "Giáo viên lớp Mầm"},
         pre=["// New email/phone each run so the request can be sent again.",
              "const n = String(Date.now()).slice(-8);",
              "pm.environment.set('teacherEmail', 'gv' + n + '@gmail.com');",
              "pm.environment.set('teacherPhone', '07' + n);"],
         tests=ok(201) + ["pm.test('role TEACHER', () => pm.expect(d.roles).to.eql(['TEACHER']));",
                          "pm.test('bắt đổi mật khẩu', () => pm.expect(d.mustChangePassword).to.be.true);",
                          save("teacherId", "d.id")]),
    item("2b. Tạo giáo viên email trùng (lỗi)", "POST", "/api/management/staff", "principalToken",
         body={"email": "{{teacherEmail}}", "fullName": "Trùng", "password": "teacher123", "role": "TEACHER"},
         tests=fail(409, 1200)),
    item("2c. Tạo giáo viên sai dữ liệu (lỗi)", "POST", "/api/management/staff", "principalToken",
         body={"email": "abc", "fullName": "", "phoneNumber": "123", "password": "123", "role": "TEACHER"},
         tests=fail(400, 1000) + ["pm.test('báo lỗi từng trường', () => pm.expect(d).to.include.keys('email', 'fullName', 'phoneNumber', 'password'));"]),

    # ---- Read ----
    item("3. Danh sách giáo viên", "GET", "/api/management/users?role=TEACHER&status=ACTIVE&keyword=&page=0&size=20",
         "principalToken", tests=ok() + ["pm.test('có phân trang', () => pm.expect(d).to.include.keys('items', 'totalElements', 'totalPages'));"]),
    item("4. Chi tiết giáo viên", "GET", "/api/management/users/{{teacherId}}", "principalToken",
         tests=ok() + ["pm.test('đúng giáo viên', () => pm.expect(d.account.email).to.eql(pm.environment.get('teacherEmail')));"]),
    item("5. Giáo viên đăng nhập", "POST", "/api/auth/login",
         body={"identifier": "{{teacherEmail}}", "password": "{{teacherPassword}}"},
         tests=ok() + [save("teacherToken", "d.accessToken"), save("teacherRefresh", "d.refreshToken")]),

    # ---- Update ----
    item("6. Sửa thông tin giáo viên", "PUT", "/api/management/users/{{teacherId}}", "principalToken",
         body={"fullName": "Cô Lan Anh", "email": "{{teacherEmail}}", "phoneNumber": "{{teacherPhone}}",
               "position": "Giáo viên lớp Chồi"},
         tests=ok() + ["pm.test('tên mới', () => pm.expect(d.fullName).to.eql('Cô Lan Anh'));"],
         desc="Không đổi email/SĐT nên giáo viên không bị đăng xuất."),
    item("7. Chỉ định tổ trưởng", "PUT", "/api/management/teachers/{{teacherId}}/head-teacher", "principalToken",
         tests=ok() + ["pm.test('có HEAD_TEACHER', () => pm.expect(d.roles).to.include.members(['TEACHER', 'HEAD_TEACHER']));"]),
    item("7b. Token cũ của GV bị từ chối (đổi role)", "GET", "/api/users/me", "teacherToken", tests=fail(401, 1104)),
    item("8. Bỏ chức tổ trưởng", "DELETE", "/api/management/teachers/{{teacherId}}/head-teacher", "principalToken",
         tests=ok() + ["pm.test('không còn HEAD_TEACHER', () => pm.expect(d.roles).to.not.include('HEAD_TEACHER'));"]),
    item("9. Đặt mật khẩu tạm cho giáo viên", "POST", "/api/management/users/{{teacherId}}/reset-password",
         "principalToken", body={"password": "temp1234"},
         tests=ok() + ["pm.environment.set('teacherPassword', 'temp1234');"],
         desc="Biến teacherPassword được đổi thành temp1234."),
    item("10. Giáo viên đăng nhập bằng mật khẩu tạm", "POST", "/api/auth/login",
         body={"identifier": "{{teacherEmail}}", "password": "{{teacherPassword}}"},
         tests=ok() + ["pm.test('bắt đổi mật khẩu', () => pm.expect(d.mustChangePassword).to.be.true);",
                       save("teacherToken", "d.accessToken")]),

    # ---- Lock (soft delete) ----
    item("11. Khoá giáo viên", "PUT", "/api/management/users/{{teacherId}}/status", "principalToken",
         body={"status": "LOCKED"}, tests=ok() + ["pm.test('LOCKED', () => pm.expect(d.status).to.eql('LOCKED'));"]),
    item("11b. Giáo viên bị khoá gọi API", "GET", "/api/users/me", "teacherToken", tests=fail(403, 1102)),
    item("12. Mở khoá giáo viên", "PUT", "/api/management/users/{{teacherId}}/status", "principalToken",
         body={"status": "ACTIVE"}, tests=ok() + ["pm.test('ACTIVE', () => pm.expect(d.status).to.eql('ACTIVE'));"]),
    item("13. Xoá giáo viên đã đăng nhập (lỗi, phải khoá)", "DELETE", "/api/management/users/{{teacherId}}",
         "principalToken", tests=fail(409, 1210)),

    # ---- Delete (account created by mistake) ----
    item("14. Tạo nhầm tài khoản giáo viên", "POST", "/api/management/staff", "principalToken",
         body={"email": "nham{{$timestamp}}@gmail.com", "fullName": "Tạo nhầm", "password": "teacher123", "role": "TEACHER"},
         tests=ok(201) + [save("mistakeTeacherId", "d.id")]),
    item("15. Xoá tài khoản tạo nhầm", "DELETE", "/api/management/users/{{mistakeTeacherId}}", "principalToken",
         tests=ok()),
    item("16. Xem tài khoản đã xoá", "GET", "/api/management/users/{{mistakeTeacherId}}", "principalToken",
         tests=fail(404, 1203)),
]

collection = {
    "info": {
        "name": "CareNest - CRUD giáo viên",
        "description": "Chọn environment 'CareNest Local' rồi chạy lần lượt 1 -> 16 (hoặc Run collection). "
                       "Token và id tự lưu vào environment.",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "item": items,
}
with open(os.path.join(HERE, "CareNest_Teacher_CRUD.postman_collection.json"), "w", encoding="utf-8") as f:
    json.dump(collection, f, ensure_ascii=False, indent=2)
print(len(items), "requests")
