import axios from "axios";
import { toApiError } from "./api-error";
import { getActiveStoreId } from "@/stores/session-store";

/**
 * The single HTTP client. Same-origin (/api is proxied by Next.js to the backend), so:
 *  - auth cookies (HttpOnly) are sent automatically (withCredentials);
 *  - the XSRF-TOKEN cookie is echoed as X-XSRF-TOKEN on unsafe methods (double-submit CSRF);
 *  - an expired access token triggers ONE refresh shared by all concurrent requests,
 *    after which the failed requests are retried once.
 */
const config = {
  baseURL: "/api/v1",
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
  withXSRFToken: true,
  timeout: 20_000,
  headers: { Accept: "application/json" },
};

export const http = axios.create(config);
// Bare client for session plumbing (no refresh interceptor → no recursion).
const raw = axios.create(config);

const UNSAFE = new Set(["post", "put", "patch", "delete"]);
const SESSION_ENDPOINTS = [
  "/auth/login",
  "/auth/refresh",
  "/auth/logout",
  "/auth/csrf",
  "/shop/auth/login",
  "/shop/auth/register",
  "/shop/auth/refresh",
  "/shop/auth/logout",
];

/** Online-shop calls use the customer session (SHOP_* cookies), never the staff one. */
function isShopUrl(url = "") {
  return url.startsWith("/shop/");
}

let sessionExpiredHandler = () => {};
/** Called once when the session can no longer be refreshed (the app redirects to /login). */
export function onSessionExpired(handler) {
  sessionExpiredHandler = handler;
}

function readXsrfCookie() {
  if (typeof document === "undefined") return null;
  const entry = document.cookie.split("; ").find((c) => c.startsWith("XSRF-TOKEN="));
  return entry ? decodeURIComponent(entry.slice("XSRF-TOKEN=".length)) : null;
}

function hasXsrfCookie() {
  return Boolean(readXsrfCookie());
}

export async function ensureCsrfToken(force = false) {
  if (force || !hasXsrfCookie()) {
    await raw.get("/auth/csrf");
  }
}

// One in-flight refresh per session kind, shared by every request that hit a 401.
const refreshPromises = { staff: null, shop: null };
function refreshSession(kind) {
  if (!refreshPromises[kind]) {
    refreshPromises[kind] = (async () => {
      await ensureCsrfToken();
      await raw.post(kind === "shop" ? "/shop/auth/refresh" : "/auth/refresh");
    })().finally(() => {
      refreshPromises[kind] = null;
    });
  }
  return refreshPromises[kind];
}

function isSessionEndpoint(url = "") {
  return SESSION_ENDPOINTS.some((p) => url.startsWith(p));
}

async function prepare(request) {
  const storeId = getActiveStoreId();
  if (storeId && !request.headers["X-Store-Id"]) {
    request.headers["X-Store-Id"] = String(storeId);
  }
  if (UNSAFE.has((request.method || "get").toLowerCase())) {
    if (!hasXsrfCookie()) await ensureCsrfToken();
    // Set explicitly (not left to Axios internals) so the double-submit header is always present.
    const token = readXsrfCookie();
    if (token) request.headers["X-XSRF-TOKEN"] = token;
  }
  return request;
}

http.interceptors.request.use(prepare);
raw.interceptors.request.use(prepare);

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config || {};
    const status = error.response?.status;
    const code = error.response?.data?.code;

    // CSRF cookie missing or rotated: fetch a fresh one and retry once.
    if (status === 403 && code === "CSRF_INVALID" && !original._csrfRetried) {
      original._csrfRetried = true;
      await ensureCsrfToken(true);
      return http(original);
    }

    if (status === 401 && !isSessionEndpoint(original.url) && !original._authRetried) {
      original._authRetried = true;
      const shop = isShopUrl(original.url);
      try {
        await refreshSession(shop ? "shop" : "staff");
      } catch {
        // Another tab may have rotated the token a moment ago; its new cookies are ours too.
        try {
          return await raw(original);
        } catch {
          // A signed-out shopper is normal; only the back office redirects to its login.
          if (!shop) sessionExpiredHandler();
          return Promise.reject(toApiError(error));
        }
      }
      return http(original);
    }
    return Promise.reject(toApiError(error));
  },
);

raw.interceptors.response.use(
  (r) => r,
  (error) => Promise.reject(toApiError(error)),
);

/** Convenience: unwraps `data`. */
export const api = {
  get: (url, params, options = {}) => http.get(url, { params, ...options }).then((r) => r.data),
  post: (url, body, options = {}) => http.post(url, body, options).then((r) => r.data),
  put: (url, body, options = {}) => http.put(url, body, options).then((r) => r.data),
  patch: (url, body, options = {}) => http.patch(url, body, options).then((r) => r.data),
  delete: (url, options = {}) => http.delete(url, options).then((r) => r.data),
};

/** Both Axios instances, exposed so tests can install a fake adapter. */
export const __clients = { http, raw };

/** Session calls that must bypass the refresh interceptor. */
export const sessionApi = {
  login: async (body) => {
    await ensureCsrfToken();
    return raw.post("/auth/login", body).then((r) => r.data);
  },
  logout: async () => {
    await ensureCsrfToken();
    return raw.post("/auth/logout");
  },
};
