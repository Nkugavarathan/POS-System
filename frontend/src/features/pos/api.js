import { api } from "@/lib/http";
import { idempotent } from "@/lib/idempotency";

export const posApi = {
  currentShift: () => api.get("/pos/shifts/current").then((d) => d || null),
  openShift: (body) => api.post("/pos/shifts", body),
  shifts: (params) => api.get("/pos/shifts", params),
  shiftSummary: (id) => api.get(`/pos/shifts/${id}/summary`),
  cashMovement: (id, body, key) => api.post(`/pos/shifts/${id}/cash-movements`, body, idempotent(key)),
  closeShift: (id, body, key) => api.post(`/pos/shifts/${id}/close`, body, idempotent(key)),
  hold: (body) => api.post("/pos/held-carts", body),
  heldCarts: () => api.get("/pos/held-carts"),
  resume: (id) => api.post(`/pos/held-carts/${id}/resume`),
  cancelHeld: (id) => api.post(`/pos/held-carts/${id}/cancel`),
  recordVoid: (body) => api.post("/pos/voids", body),

  quote: (body) => api.post("/sales/quote", body),
  checkout: (body, key) => api.post("/sales/checkout", body, idempotent(key)),
  sale: (id) => api.get(`/sales/${id}`),
  sales: (params) => api.get("/sales", params),
  receipt: (number) => api.get(`/sales/receipt/${encodeURIComponent(number)}`),
  reprint: (id) => api.post(`/sales/${id}/reprints`),

  customerLookup: (value) => api.get("/customers/lookup", { value }),
  createCustomer: (body) => api.post("/customers", body),
  registers: (storeId) => api.get(`/stores/${storeId}/registers`),
  tenderTypes: () => api.get("/tender-types"),
};

export const posKeys = {
  shift: ["pos", "shift"],
  held: ["pos", "held"],
};
