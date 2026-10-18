import { api, http } from "@/lib/http";

export const reportsApi = {
  dashboard: () => api.get("/reports/dashboard"),
  sales: (params) => api.get("/reports/sales", params),
  topProducts: (params) => api.get("/reports/top-products", params),
  categories: (params) => api.get("/reports/categories", params),
  hours: (params) => api.get("/reports/hours", params),
  lowStock: () => api.get("/reports/low-stock"),
  expiring: (days) => api.get("/reports/expiring", { days }),
  stockValuation: () => api.get("/reports/stock-valuation"),
  audit: (params) => api.get("/audit", params),
};

/** Downloads a CSV export through the authenticated client and saves it with the server's filename. */
export async function downloadCsv(report, params = {}) {
  const response = await http.get(`/reports/${report}.csv`, { params, responseType: "blob" });
  const disposition = response.headers["content-disposition"] || "";
  const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] || `${report}.csv`;
  const url = URL.createObjectURL(response.data);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
