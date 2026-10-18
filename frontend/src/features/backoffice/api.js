import { api } from "@/lib/http";
import { idempotent } from "@/lib/idempotency";

/** Back-office endpoints for sales history, returns, shifts, customers and purchasing. */
export const salesApi = {
  list: (params) => api.get("/sales", params),
  get: (id) => api.get(`/sales/${id}`),
  byReceipt: (number) => api.get(`/sales/receipt/${encodeURIComponent(number)}`),
  reprint: (id) => api.post(`/sales/${id}/reprints`),
};

export const returnsApi = {
  list: (params) => api.get("/returns", params),
  get: (id) => api.get(`/returns/${id}`),
  preview: (body) => api.post("/returns/preview", body),
  create: (body, key) => api.post("/returns", body, idempotent(key)),
};

export const shiftsApi = {
  list: (params) => api.get("/pos/shifts", params),
  summary: (id) => api.get(`/pos/shifts/${id}/summary`),
  current: () => api.get("/pos/shifts/current").then((d) => d || null),
};

export const customersApi = {
  list: (params) => api.get("/customers", params),
  get: (id) => api.get(`/customers/${id}`),
  create: (body) => api.post("/customers", body),
  update: (id, body) => api.put(`/customers/${id}`, body),
  loyalty: (id, params) => api.get(`/customers/${id}/loyalty`, params),
  adjust: (id, body) => api.post(`/customers/${id}/loyalty/adjustments`, body),
};

export const purchasingApi = {
  suppliers: (params) => api.get("/suppliers", params),
  supplier: (id) => api.get(`/suppliers/${id}`),
  createSupplier: (body) => api.post("/suppliers", body),
  updateSupplier: (id, body) => api.put(`/suppliers/${id}`, body),
  orders: (params) => api.get("/purchases/orders", params),
  order: (id) => api.get(`/purchases/orders/${id}`),
  createOrder: (body) => api.post("/purchases/orders", body),
  updateOrder: (id, body) => api.put(`/purchases/orders/${id}`, body),
  submit: (id) => api.post(`/purchases/orders/${id}/submit`),
  close: (id) => api.post(`/purchases/orders/${id}/close`),
  cancel: (id) => api.post(`/purchases/orders/${id}/cancel`),
  receive: (id, body, key) => api.post(`/purchases/orders/${id}/receipts`, body, idempotent(key)),
  receipts: (params) => api.get("/purchases/receipts", params),
  receipt: (id) => api.get(`/purchases/receipts/${id}`),
};

/** Local calendar day (yyyy-mm-dd from a date input) → ISO instant at its start / end. */
export function dayStart(value) {
  return value ? new Date(`${value}T00:00:00`).toISOString() : undefined;
}
export function dayEnd(value) {
  return value ? new Date(`${value}T23:59:59.999`).toISOString() : undefined;
}
export function todayInput(offsetDays = 0) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
