import { api, ensureCsrfToken } from "@/lib/http";

/** Online shop endpoints (customer session = SHOP_* HttpOnly cookies). */
export const shopApi = {
  // public catalog
  stores: () => api.get("/shop/public/stores"),
  categories: () => api.get("/shop/public/categories"),
  products: (params) => api.get("/shop/public/products", params),
  product: (id, storeId) => api.get(`/shop/public/products/${id}`, { storeId }),
  rescueDeals: (storeId) => api.get("/shop/public/rescue-deals", { storeId }),
  quote: (body) => api.post("/shop/public/cart/quote", body),

  // session
  register: async (body) => {
    await ensureCsrfToken();
    return api.post("/shop/auth/register", body);
  },
  login: async (body) => {
    await ensureCsrfToken();
    return api.post("/shop/auth/login", body);
  },
  logout: () => api.post("/shop/auth/logout"),

  // account
  me: () => api.get("/shop/me"),
  setStore: (storeId) => api.put("/shop/me/store", { storeId }),
  linkCard: (body) => api.post("/shop/me/loyalty-card", body),
  buyAgain: (storeId) => api.get("/shop/me/buy-again", { storeId }),

  notifications: (params) => api.get("/shop/notifications", params),
  unread: () => api.get("/shop/notifications/unread-count"),
  markRead: (id) => api.post(`/shop/notifications/${id}/read`),
  markAllRead: () => api.post("/shop/notifications/read-all"),

  // orders
  place: (body) => api.post("/shop/orders", body),
  orders: (params) => api.get("/shop/orders", params),
  order: (number) => api.get(`/shop/orders/${encodeURIComponent(number)}`),
  cancel: (number) => api.post(`/shop/orders/${encodeURIComponent(number)}/cancel`),
  decide: (number, itemId, accept) =>
    api.post(`/shop/orders/${encodeURIComponent(number)}/items/${itemId}/substitution`, { accept }),
  arrived: (number) => api.post(`/shop/orders/${encodeURIComponent(number)}/arrived`),
};

export const shopKeys = {
  stores: ["shop", "stores"],
  categories: ["shop", "categories"],
  products: (params) => ["shop", "products", params],
  product: (id, storeId) => ["shop", "product", id, storeId],
  rescue: (storeId) => ["shop", "rescue", storeId],
  quote: (body) => ["shop", "quote", body],
  me: ["shop", "me"],
  buyAgain: (storeId) => ["shop", "buy-again", storeId],
  notifications: ["shop", "notifications"],
  unread: ["shop", "unread"],
  orders: ["shop", "orders"],
  order: (number) => ["shop", "order", number],
};

export const ORDER_STEPS = [
  { status: "PLACED", label: "Order placed" },
  { status: "CONFIRMED", label: "Confirmed" },
  { status: "PACKING", label: "Packing" },
  { status: "READY_FOR_PICKUP", label: "Ready for pickup" },
  { status: "COLLECTED", label: "Collected" },
];

export const STATUS_LABEL = {
  PLACED: "Placed",
  CONFIRMED: "Confirmed",
  PACKING: "Packing",
  READY_FOR_PICKUP: "Ready for pickup",
  COLLECTED: "Collected",
  CANCELLED: "Cancelled",
  EXPIRED: "Expired",
};

export const STATUS_COLOR = {
  PLACED: "blue",
  CONFIRMED: "blue",
  PACKING: "secondary",
  READY_FOR_PICKUP: "green",
  COLLECTED: "gray",
  CANCELLED: "red",
  EXPIRED: "red",
};
