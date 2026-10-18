import { api } from "@/lib/http";

export const catalogApi = {
  categories: (params) => api.get("/categories", params),
  categoryTree: () => api.get("/categories/tree"),
  createCategory: (body) => api.post("/categories", body),
  updateCategory: (id, body) => api.put(`/categories/${id}`, body),
  setCategoryStatus: (id, body) => api.patch(`/categories/${id}/status`, body),

  products: (params) => api.get("/products", params),
  product: (id) => api.get(`/products/${id}`),
  brands: () => api.get("/products/brands"),
  createProduct: (body) => api.post("/products", body),
  updateProduct: (id, body) => api.put(`/products/${id}`, body),
  setProductStatus: (id, body) => api.patch(`/products/${id}/status`, body),
  addBarcode: (id, body) => api.post(`/products/${id}/barcodes`, body),
  removeBarcode: (id, barcodeId) => api.delete(`/products/${id}/barcodes/${barcodeId}`),
  createPromotion: (id, body) => api.post(`/products/${id}/promotions`, body),
  updatePromotion: (id, promoId, body) => api.put(`/products/${id}/promotions/${promoId}`, body),
  byBarcode: (code, format) => api.get(`/products/barcode/${encodeURIComponent(code)}`, format ? { format } : undefined),
  posSearch: (q) => api.get("/products/pos-search", { q }),

  taxRates: () => api.get("/tax-rates"),
};

export const catalogKeys = {
  categories: ["categories"],
  tree: ["categories", "tree"],
  products: (params) => ["products", "list", params],
  product: (id) => ["products", "detail", String(id)],
  brands: ["products", "brands"],
  taxRates: ["tax-rates"],
};

export const UNITS = [
  { value: "EACH", label: "Each (unit)" },
  { value: "PACK", label: "Pack" },
  { value: "BOTTLE", label: "Bottle" },
  { value: "KG", label: "Kilogram (kg)" },
  { value: "G", label: "Gram (g)" },
  { value: "L", label: "Litre (L)" },
  { value: "ML", label: "Millilitre (ml)" },
];
