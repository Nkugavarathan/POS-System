import { beforeEach, describe, expect, it, vi } from "vitest";
import { __clients, api, onSessionExpired } from "@/lib/http";
import { ApiError } from "@/lib/api-error";

/**
 * Fake adapter shared by both Axios instances. `routes` maps "METHOD url" to a handler
 * returning { status, data }. Every call is recorded.
 */
function installAdapter(routes) {
  const calls = [];
  const adapter = async (config) => {
    const key = `${config.method.toUpperCase()} ${config.url}`;
    calls.push({ key, headers: config.headers });
    const handler = routes[key];
    const { status, data } = handler ? await handler(calls) : { status: 404, data: { code: "NOT_FOUND" } };
    const response = { status, data, headers: {}, config, statusText: String(status) };
    if (status >= 400) {
      const error = new Error(`HTTP ${status}`);
      error.config = config;
      error.response = response;
      throw error;
    }
    return response;
  };
  __clients.http.defaults.adapter = adapter;
  __clients.raw.defaults.adapter = adapter;
  return calls;
}

describe("http client", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=test-token; path=/";
  });

  it("refreshes once for concurrent 401s and retries every request", async () => {
    let refreshed = false;
    const calls = installAdapter({
      "GET /products": () => (refreshed ? { status: 200, data: { ok: "products" } } : { status: 401, data: { code: "TOKEN_EXPIRED" } }),
      "GET /sales": () => (refreshed ? { status: 200, data: { ok: "sales" } } : { status: 401, data: { code: "TOKEN_EXPIRED" } }),
      "POST /auth/refresh": async () => {
        await new Promise((r) => setTimeout(r, 10));
        refreshed = true;
        return { status: 200, data: {} };
      },
    });

    const [a, b] = await Promise.all([api.get("/products"), api.get("/sales")]);

    expect(a).toEqual({ ok: "products" });
    expect(b).toEqual({ ok: "sales" });
    expect(calls.filter((c) => c.key === "POST /auth/refresh")).toHaveLength(1);
  });

  it("signals session expiry when the refresh fails", async () => {
    const expired = vi.fn();
    onSessionExpired(expired);
    installAdapter({
      "GET /me-thing": () => ({ status: 401, data: { code: "UNAUTHENTICATED" } }),
      "POST /auth/refresh": () => ({ status: 401, data: { code: "REFRESH_TOKEN_INVALID" } }),
    });

    await expect(api.get("/me-thing")).rejects.toBeInstanceOf(ApiError);
    expect(expired).toHaveBeenCalledTimes(1);
  });

  it("maps problem details to ApiError with the stable code", async () => {
    installAdapter({
      "POST /users": () => ({
        status: 400,
        data: { code: "VALIDATION_FAILED", detail: "The request contains invalid values.", fieldErrors: [{ field: "username", message: "bad" }] },
      }),
    });
    const error = await api.post("/users", {}).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe("VALIDATION_FAILED");
    expect(error.fieldErrors[0].field).toBe("username");
  });

  it("sends the CSRF header on unsafe requests", async () => {
    const calls = installAdapter({ "POST /things": () => ({ status: 201, data: {} }) });
    await api.post("/things", { a: 1 });
    const headers = calls.find((c) => c.key === "POST /things").headers;
    expect(headers["X-XSRF-TOKEN"]).toBe("test-token");
  });

  it("retries once after fetching a fresh CSRF token", async () => {
    let attempts = 0;
    const calls = installAdapter({
      "POST /things": () => (++attempts === 1 ? { status: 403, data: { code: "CSRF_INVALID" } } : { status: 201, data: { done: true } }),
      "GET /auth/csrf": () => ({ status: 204, data: "" }),
    });
    await expect(api.post("/things", {})).resolves.toEqual({ done: true });
    expect(calls.some((c) => c.key === "GET /auth/csrf")).toBe(true);
  });
});
