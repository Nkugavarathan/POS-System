import { api, sessionApi } from "@/lib/http";

export const authApi = {
  login: (credentials) => sessionApi.login(credentials),
  logout: () => sessionApi.logout(),
  me: () => api.get("/auth/me"),
  changePassword: (body) => api.post("/auth/me/password", body),
  requestApproval: (body) => api.post("/approvals", body),
};
