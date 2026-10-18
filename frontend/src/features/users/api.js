import { api } from "@/lib/http";

export const usersApi = {
  list: (params) => api.get("/users", params),
  get: (id) => api.get(`/users/${id}`),
  create: (body) => api.post("/users", body),
  update: (id, body) => api.put(`/users/${id}`, body),
  setStatus: (id, body) => api.patch(`/users/${id}/status`, body),
  resetPassword: (id, temporaryPassword) => api.post(`/users/${id}/password-reset`, { temporaryPassword }),
  setPin: (id, pin) => api.put(`/users/${id}/pin`, { pin }),
  roles: () => api.get("/roles"),
  updateRole: (id, body) => api.put(`/roles/${id}`, body),
  permissions: () => api.get("/permissions"),
};

export const storesApi = {
  accessible: () => api.get("/stores"),
  get: (id) => api.get(`/stores/${id}`),
  update: (id, body) => api.put(`/stores/${id}`, body),
  registers: (storeId) => api.get(`/stores/${storeId}/registers`),
  createRegister: (storeId, body) => api.post(`/stores/${storeId}/registers`, body),
  updateRegister: (storeId, id, body) => api.put(`/stores/${storeId}/registers/${id}`, body),
  tenderTypes: () => api.get("/tender-types"),
  settings: () => api.get("/settings"),
  updateSettings: (scope, settings) => api.put(`/settings?scope=${scope}`, { settings }),
};

export const userKeys = {
  all: ["users"],
  list: (params) => ["users", "list", params],
  detail: (id) => ["users", "detail", id],
  roles: ["roles"],
  permissions: ["permissions"],
  stores: ["stores", "accessible"],
};
