import { api } from "@/lib/http";
import { idempotent } from "@/lib/idempotency";

export const inventoryApi = {
  list: (params) => api.get("/inventory", params),
  detail: (productId) => api.get(`/inventory/${productId}`),
  movements: (params) => api.get("/inventory/movements", params),
  expiring: (days) => api.get("/inventory/expiring", { days }),
  adjust: (body, key) => api.post("/inventory/adjustments", body, idempotent(key)),
  adjustments: (params) => api.get("/inventory/adjustments", params),
  adjustment: (id) => api.get(`/inventory/adjustments/${id}`),
  counts: (params) => api.get("/inventory/counts", params),
  count: (id) => api.get(`/inventory/counts/${id}`),
  createCount: (body) => api.post("/inventory/counts", body),
  recordCount: (id, body) => api.put(`/inventory/counts/${id}/items`, body),
  countAction: (id, action) => api.post(`/inventory/counts/${id}/${action}`),
  alerts: (params) => api.get("/inventory/alerts", params),
  acknowledge: (id) => api.post(`/inventory/alerts/${id}/acknowledge`),
};

export const MOVEMENT_LABELS = {
  OPENING_BALANCE: "Opening balance",
  PURCHASE_RECEIPT: "Goods received",
  SALE: "Sale",
  SALE_RETURN: "Customer return",
  ADJUSTMENT_IN: "Adjustment +",
  ADJUSTMENT_OUT: "Adjustment −",
  WRITE_OFF_DAMAGED: "Write-off (damaged)",
  WRITE_OFF_EXPIRED: "Write-off (expired)",
  COUNT_CORRECTION: "Count correction",
  ONLINE_ORDER_PICK: "Picked for online order",
  ONLINE_ORDER_RELEASE: "Online order released",
  TRANSFER_OUT: "Transfer out",
  TRANSFER_IN: "Transfer in",
};
