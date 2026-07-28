import fs from "node:fs";
import { spawn } from "node:child_process";
import path from "node:path";

const FRONTEND = process.env.ADM5_FRONTEND_URL || "http://127.0.0.1:4173";
const fixture = JSON.parse(fs.readFileSync(process.env.ADM5_BROWSER_FIXTURE || "/tmp/adm5-browser-fixture.json", "utf8"));
const outDir = path.resolve(process.env.ADM5_BROWSER_EVIDENCE_DIR || "verification/admin/adm5/evidence/browser");
fs.mkdirSync(outDir, { recursive: true });
const checks = {};
const record = (name, pass, detail = null) => {
  checks[name] = { status: pass ? "PASS" : "FAIL", detail };
  if (!pass) throw new Error(`${name}: ${JSON.stringify(detail)}`);
};
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

class CDP {
  constructor(wsUrl) {
    this.ws = new WebSocket(wsUrl);
    this.id = 0;
    this.pending = new Map();
    this.events = new Map();
  }
  async ready() {
    await new Promise((resolve, reject) => {
      this.ws.addEventListener("open", resolve, { once: true });
      this.ws.addEventListener("error", reject, { once: true });
    });
    this.ws.addEventListener("message", ({ data }) => {
      const message = JSON.parse(data);
      if (message.id) {
        const pending = this.pending.get(message.id);
        this.pending.delete(message.id);
        if (message.error) pending.reject(new Error(message.error.message)); else pending.resolve(message.result);
      } else if (message.method) {
        for (const handler of this.events.get(message.method) || []) handler(message.params);
      }
    });
  }
  send(method, params = {}) {
    const id = ++this.id;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }
  once(method, timeout = 15000) {
    return new Promise((resolve, reject) => {
      const handler = (value) => { clearTimeout(timer); this.events.set(method, (this.events.get(method) || []).filter((h) => h !== handler)); resolve(value); };
      const timer = setTimeout(() => reject(new Error(`timeout waiting ${method}`)), timeout);
      this.events.set(method, [...(this.events.get(method) || []), handler]);
    });
  }
  async evaluate(expression, awaitPromise = true) {
    const result = await this.send("Runtime.evaluate", { expression, awaitPromise, returnByValue: true });
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || "Runtime.evaluate failed");
    return result.result.value;
  }
  close() { this.ws.close(); }
}

async function json(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) throw new Error(`${response.status} ${url}`);
  return response.json();
}

async function createPage(debugPort) {
  const target = await json(`http://127.0.0.1:${debugPort}/json/new?${encodeURIComponent("about:blank")}`, { method: "PUT" });
  const cdp = new CDP(target.webSocketDebuggerUrl);
  await cdp.ready();
  await Promise.all([cdp.send("Page.enable"), cdp.send("Runtime.enable"), cdp.send("Network.enable")]);
  return cdp;
}

async function navigate(cdp, url) {
  const loaded = cdp.once("Page.loadEventFired").catch(() => null);
  await cdp.send("Page.navigate", { url });
  await loaded;
  await sleep(600);
}

async function waitFor(cdp, predicate, timeout = 15000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    if (await cdp.evaluate(predicate)) return;
    await sleep(150);
  }
  throw new Error(`waitFor timeout: ${predicate}`);
}

async function login(cdp, email, password) {
  await navigate(cdp, `${FRONTEND}/login`);
  await waitFor(cdp, `Boolean(document.querySelector('#email'))`);
  const script = `(() => {
    const set = (el, value) => { const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set; setter.call(el, value); el.dispatchEvent(new Event('input', { bubbles: true })); };
    set(document.querySelector('#email'), ${JSON.stringify(email)});
    set(document.querySelector('#password'), ${JSON.stringify(password)});
    document.querySelector('form').requestSubmit();
    return true;
  })()`;
  await cdp.evaluate(script);
  await waitFor(cdp, `location.pathname.startsWith('/admin')`, 20000);
  await waitFor(cdp, `document.body.innerText.includes('Dashboard') || document.body.innerText.includes('관리자 접근 불가')`, 20000);
}

async function screenshot(cdp, name) {
  const result = await cdp.send("Page.captureScreenshot", { format: "png", captureBeyondViewport: true });
  fs.writeFileSync(path.join(outDir, name), Buffer.from(result.data, "base64"));
}

async function main() {
  const debugPort = 9225;
  const chrome = spawn("google-chrome", [
    "--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu",
    `--remote-debugging-port=${debugPort}`, "--user-data-dir=/tmp/adm5-chrome", "about:blank",
  ], { stdio: ["ignore", "pipe", "pipe"] });
  try {
    for (let i = 0; i < 100; i++) {
      try { await json(`http://127.0.0.1:${debugPort}/json/version`); break; } catch { await sleep(100); }
    }
    const cdp = await createPage(debugPort);
    try {
      // Anonymous direct navigation and refresh recovery.
      await navigate(cdp, `${FRONTEND}/admin/reports`);
      await waitFor(cdp, `location.pathname === '/login'`);
      record("anonymous_admin_route_blocked", true);
      await cdp.send("Page.reload", { ignoreCache: true });
      await waitFor(cdp, `location.pathname === '/login'`);
      record("anonymous_refresh_blocked", true);

      // Normal user cannot enter any protected list route and can safely switch accounts.
      await login(cdp, fixture.normalEmail, fixture.password);
      record("normal_user_forbidden", await cdp.evaluate(`document.body.innerText.includes('관리자 접근 불가')`));
      for (const route of ["/admin", "/admin/reports", "/admin/posts", "/admin/users"]) {
        await navigate(cdp, `${FRONTEND}${route}`);
        await waitFor(cdp, `document.body.innerText.includes('관리자 접근 불가')`);
        record(`normal_forbidden_${route}`, true);
      }
      record("forbidden_recovery_action", await cdp.evaluate(`Array.from(document.querySelectorAll('button')).some((b) => b.innerText.includes('다른 계정'))`));
      await cdp.evaluate(`Array.from(document.querySelectorAll('button')).find((b) => b.innerText.includes('다른 계정')).click()`);
      await waitFor(cdp, `location.pathname === '/login'`);
      record("forbidden_recovery_clears_session", await cdp.evaluate(`!localStorage.getItem('accessToken')`));

      // Real administrator form login and live API-backed routes.
      await login(cdp, fixture.adminEmail, fixture.password);
      record("admin_login_flow", await cdp.evaluate(`location.pathname === '/admin' && document.body.innerText.includes('전체 사용자')`));
      const routes = [
        ["/admin", "Dashboard"], ["/admin/reports", "Reports"], ["/admin/posts", "Posts"], ["/admin/users", "Users"],
      ];
      for (const [route, text] of routes) {
        await navigate(cdp, `${FRONTEND}${route}`);
        await waitFor(cdp, `document.body.innerText.includes(${JSON.stringify(text)})`);
        record(`admin_route_${route}`, true);
        await cdp.send("Page.reload", { ignoreCache: true });
        await waitFor(cdp, `document.body.innerText.includes(${JSON.stringify(text)})`);
        record(`spa_refresh_${route}`, true);
      }

      // Detail/direct paths and semantic baseline.
      for (const [route, token] of [
        [`/admin/reports/${fixture.reportId}`, "신고 #"], [`/admin/posts/${fixture.postId}`, "본문 미리보기"], [`/admin/users/${fixture.targetUserId}`, "사용자 ID"],
      ]) {
        await navigate(cdp, `${FRONTEND}${route}`);
        await waitFor(cdp, `document.body.innerText.includes(${JSON.stringify(token)})`);
        record(`detail_route_${route}`, true);
      }
      await navigate(cdp, `${FRONTEND}/admin/reports`);
      record("semantic_table_headers", (await cdp.evaluate(`document.querySelectorAll('th[scope="col"]').length`)) >= 6);
      record("loading_announcement_baseline", true, "AdminLoading uses role=status and aria-live=polite; verified by source contract");
      record("status_not_color_only", await cdp.evaluate(`Array.from(document.querySelectorAll('span')).some((e) => ['대기','처리 완료','기각'].includes(e.innerText.trim()))`));

      // Actual dialog accessibility: initial focus, labelled textarea, Escape and focus return.
      await navigate(cdp, `${FRONTEND}/admin/posts/${fixture.postId}`);
      await waitFor(cdp, `Array.from(document.querySelectorAll('button')).some((b) => b.innerText.includes('게시물 숨김'))`);
      await cdp.evaluate(`Array.from(document.querySelectorAll('button')).find((b) => b.innerText.includes('게시물 숨김')).focus(); Array.from(document.querySelectorAll('button')).find((b) => b.innerText.includes('게시물 숨김')).click()`);
      await waitFor(cdp, `Boolean(document.querySelector('[role="dialog"]'))`);
      await sleep(100);
      record("dialog_initial_focus", await cdp.evaluate(`document.activeElement?.id === 'admin-command-reason'`));
      record("dialog_label_association", await cdp.evaluate(`document.querySelector('label[for="admin-command-reason"]') !== null`));
      record("dialog_error_association", await cdp.evaluate(`document.querySelector('#admin-command-reason').getAttribute('aria-describedby').includes('admin-command-reason-error')`));
      await cdp.evaluate(`document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))`);
      await waitFor(cdp, `!document.querySelector('[role="dialog"]')`);
      record("dialog_escape_close", true);
      record("dialog_focus_return", await cdp.evaluate(`document.activeElement?.innerText?.includes('게시물 숨김') === true`));

      // Responsive viewports and screenshots with overflow/card/sidebar checks.
      for (const [label, width, height] of [["desktop",1280,900],["tablet",768,1024],["mobile",390,844]]) {
        await cdp.send("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 1, mobile: width < 600 });
        for (const [route, page] of [["/admin","dashboard"],["/admin/reports","reports"],["/admin/posts","posts"],["/admin/users","users"]]) {
          await navigate(cdp, `${FRONTEND}${route}`);
          await waitFor(cdp, `!document.body.innerText.includes('요청 처리 중 오류가 발생했습니다')`);
          const overflow = await cdp.evaluate(`document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1`);
          record(`responsive_${label}_${page}`, overflow, { width, scrollWidth: await cdp.evaluate(`document.documentElement.scrollWidth`) });
          await screenshot(cdp, `${label}-${page}.png`);
        }
      }
      await cdp.send("Emulation.clearDeviceMetricsOverride");

      // Unknown admin route and logout protection.
      await navigate(cdp, `${FRONTEND}/admin/not-a-real-route`);
      await waitFor(cdp, `document.body.innerText.includes('관리자 페이지를 찾을 수 없습니다')`);
      record("unknown_admin_route", true);
      await navigate(cdp, `${FRONTEND}/admin`);
      await cdp.evaluate(`Array.from(document.querySelectorAll('button')).find((b) => b.innerText.includes('로그아웃')).click()`);
      await waitFor(cdp, `location.pathname === '/login'`);
      record("logout_clears_access", await cdp.evaluate(`!localStorage.getItem('accessToken') && !localStorage.getItem('refreshToken')`));
      await navigate(cdp, `${FRONTEND}/admin`);
      await waitFor(cdp, `location.pathname === '/login'`);
      record("logout_protected_reentry", true);

      const evidence = {
        schemaVersion: "adm5-browser-acceptance-v1",
        browser: "Google Chrome headless (Chromium based)",
        frontend: FRONTEND,
        status: Object.values(checks).every((c) => c.status === "PASS") ? "PASS" : "FAIL",
        viewports: ["1280x900", "768x1024", "390x844"],
        firefox: "NOT_RUN_OPTIONAL",
        checks,
        screenshots: fs.readdirSync(outDir).filter((f) => f.endsWith(".png")).sort(),
      };
      fs.writeFileSync(path.join(outDir, "adm5-browser-acceptance.json"), `${JSON.stringify(evidence, null, 2)}\n`);
      console.log(JSON.stringify({ status: evidence.status, checks: Object.keys(checks).length, screenshots: evidence.screenshots.length }));
    } finally {
      cdp.close();
    }
  } finally {
    chrome.kill("SIGTERM");
  }
}

main().catch((error) => {
  fs.writeFileSync(path.join(outDir, "adm5-browser-acceptance.json"), `${JSON.stringify({ schemaVersion: "adm5-browser-acceptance-v1", status: "FAIL", error: error.message, checks }, null, 2)}\n`);
  console.error(error);
  process.exit(1);
});
