import test from "node:test";
import assert from "node:assert/strict";
import { ADMIN_MAX_REASON_LENGTH, ADMIN_PAGE_SIZE, ADMIN_ROUTES } from "../admin/adminPolicies.js";

test("admin_route_contract_is_exact", () => assert.deepEqual(ADMIN_ROUTES, { dashboard: "/admin", reports: "/admin/reports", posts: "/admin/posts", users: "/admin/users" }));
test("default_page_size_is_twenty", () => assert.equal(ADMIN_PAGE_SIZE, 20));
test("reason_limit_is_one_thousand", () => assert.equal(ADMIN_MAX_REASON_LENGTH, 1000));
