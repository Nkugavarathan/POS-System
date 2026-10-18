import { api } from "@/lib/http";
import { idempotent } from "@/lib/idempotency";

/** Store-side fulfilment of online click & collect orders. */
export const fulfilmentApi = {
  board: (params) => api.get("/online-orders", params),
  summary: () => api.get("/online-orders/summary"),
  get: (id) => api.get(`/online-orders/${id}`),
  findByCode: (code) => api.get("/online-orders/collect", { code }),
  confirm: (id) => api.post(`/online-orders/${id}/confirm`),
  startPacking: (id) => api.post(`/online-orders/${id}/start-packing`),
  pick: (id, itemId, pickedQuantity) =>
    api.post(`/online-orders/${id}/items/${itemId}/pick`, pickedQuantity ? { pickedQuantity } : {}),
  substitute: (id, itemId, body) => api.post(`/online-orders/${id}/items/${itemId}/substitute`, body),
  unavailable: (id, itemId) => api.post(`/online-orders/${id}/items/${itemId}/unavailable`),
  reset: (id, itemId) => api.post(`/online-orders/${id}/items/${itemId}/reset`),
  ready: (id) => api.post(`/online-orders/${id}/ready`),
  cancel: (id, reason) => api.post(`/online-orders/${id}/cancel`, { reason }),
  collect: (id, body, key) => api.post(`/online-orders/${id}/collect`, body, idempotent(key)),
  rescueDeals: () => api.get("/online-orders/rescue-deals"),
  runRescue: () => api.post("/online-orders/rescue-deals/run"),
};

export const fulfilmentKeys = {
  board: (params) => ["online-orders", "board", params],
  summary: ["online-orders", "summary"],
  order: (id) => ["online-orders", "order", id],
  rescue: ["online-orders", "rescue"],
};

export const BOARD_COLUMNS = [
  { status: "PLACED", label: "New", hint: "Confirm to accept" },
  { status: "CONFIRMED", label: "Confirmed", hint: "Start packing" },
  { status: "PACKING", label: "Packing", hint: "Pick & substitute" },
  { status: "READY_FOR_PICKUP", label: "Ready", hint: "Awaiting customer" },
];
