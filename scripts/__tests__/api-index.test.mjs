import assert from "node:assert/strict";
import { test } from "node:test";
import { parseOpenapi, renderIndex } from "../ai-layer/api-index.mjs";

const OPENAPI = [
  "openapi: 3.1.0",
  "paths:",
  "  /api/meal-counts/{mealCountId}/confirm:",
  "    post:",
  "      operationId: confirm_1",
  "      parameters:",
  "      - in: path",
  "        name: mealCountId",
  "      tags:",
  "      - meal-count-controller",
  "  /api/accounts:",
  "    get:",
  "      operationId: searchAccounts",
  "      tags:",
  "      - account-controller",
  "    post:",
  "      operationId: createAccount",
  "      tags:",
  "      - unknown-controller",
  "components:",
  "  /api/not-a-path:",
  "    get:",
  "      operationId: ignored",
].join("\n");

test("parseOpenapi: lấy method, path bỏ /api, operationId, tag; bỏ qua ngoài paths", () => {
  assert.deepEqual(parseOpenapi(OPENAPI), [
    { method: "GET", path: "/accounts", operationId: "searchAccounts", tag: "account-controller" },
    { method: "POST", path: "/accounts", operationId: "createAccount", tag: "unknown-controller" },
    { method: "POST", path: "/meal-counts/{mealCountId}/confirm", operationId: "confirm_1", tag: "meal-count-controller" },
  ]);
});

test("renderIndex: mỗi endpoint 1 dòng trỏ file controller và method, bỏ hậu tố _N", () => {
  const text = renderIndex(parseOpenapi(OPENAPI), [
    "src/main/java/com/carenest/controller/account/AccountController.java",
    "src/main/java/com/carenest/controller/meals/MealCountController.java",
  ]);
  assert.match(text, /^GET \/accounts · src\/main\/java\/com\/carenest\/controller\/account\/AccountController\.java#searchAccounts$/m);
  assert.match(text, /^POST \/meal-counts\/\{mealCountId\}\/confirm · .*\/meals\/MealCountController\.java#confirm$/m);
  assert.match(text, /^POST \/accounts · \(không tìm thấy controller cho tag unknown-controller\)#createAccount$/m);
  assert.match(text, /^Tổng: 3 endpoint\.$/m);
});
