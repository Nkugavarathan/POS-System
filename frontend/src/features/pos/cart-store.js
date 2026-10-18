"use client";

import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import { toMilli } from "@/lib/money";

/**
 * The basket being built at the till. It holds only what the cashier chose (products,
 * quantities, requested discounts/overrides, customer, approval tokens). Prices shown come
 * from the server quote. Persisted per browser tab (sessionStorage) so a refresh does not
 * lose a basket.
 */
let keySeq = 0;
const newKey = () => `l${Date.now().toString(36)}${(keySeq++).toString(36)}`;

function addQuantity(a, b, fractional) {
  const sum = toMilli(a) + toMilli(b);
  if (!fractional) return String(sum / 1000n);
  const whole = sum / 1000n;
  const frac = String(sum % 1000n).padStart(3, "0").replace(/0+$/, "");
  return frac ? `${whole}.${frac}` : String(whole);
}

const empty = {
  lines: [],
  customer: null,
  transactionDiscount: null,
  approvalTokens: [],
  selectedKey: null,
};

export const useCartStore = create(
  persist(
    (set, get) => ({
      ...empty,

      /** Adds a product, or increases the quantity of its existing plain line. */
      addProduct: (product, quantity = "1") => {
        const lines = get().lines;
        const existing = lines.find(
          (l) => l.productId === product.id && !l.manualDiscount && !l.priceOverride && !product.fractionalQuantity,
        );
        if (existing) {
          const updated = lines.map((l) =>
            l.key === existing.key ? { ...l, quantity: addQuantity(l.quantity, quantity, false) } : l,
          );
          set({ lines: updated, selectedKey: existing.key });
          return existing.key;
        }
        const line = {
          key: newKey(),
          productId: product.id,
          name: product.name,
          sku: product.sku,
          barcode: product.barcode,
          unitOfMeasure: product.unitOfMeasure,
          fractional: product.fractionalQuantity,
          allowPriceOverride: product.allowPriceOverride,
          displayPrice: product.sellingPrice,
          quantity: String(quantity),
          manualDiscount: null,
          priceOverride: null,
        };
        set({ lines: [...lines, line], selectedKey: line.key });
        return line.key;
      },
      setQuantity: (key, quantity) =>
        set({ lines: get().lines.map((l) => (l.key === key ? { ...l, quantity: String(quantity) } : l)) }),
      increment: (key, delta) => {
        const line = get().lines.find((l) => l.key === key);
        if (!line || line.fractional) return;
        const next = Number(line.quantity) + delta;
        if (next < 1) return;
        get().setQuantity(key, String(next));
      },
      removeLine: (key) => {
        const lines = get().lines.filter((l) => l.key !== key);
        set({ lines, selectedKey: lines.at(-1)?.key ?? null });
      },
      setLineDiscount: (key, manualDiscount) =>
        set({ lines: get().lines.map((l) => (l.key === key ? { ...l, manualDiscount } : l)) }),
      setPriceOverride: (key, priceOverride) =>
        set({ lines: get().lines.map((l) => (l.key === key ? { ...l, priceOverride } : l)) }),
      select: (selectedKey) => set({ selectedKey }),
      setCustomer: (customer) => set({ customer }),
      setTransactionDiscount: (transactionDiscount) => set({ transactionDiscount }),
      addApprovalToken: (token) => set({ approvalTokens: [...get().approvalTokens, token] }),
      loadLines: (lines) => set({ ...empty, lines: lines.map((l) => ({ ...l, key: newKey() })) }),
      clear: () => set({ ...empty }),
    }),
    {
      name: "pos.cart",
      storage: createJSONStorage(() => {
        try {
          return window.sessionStorage;
        } catch {
          return undefined;
        }
      }),
    },
  ),
);

/** The request body for /sales/quote and /sales/checkout (no prices, only choices). */
export function cartRequest(state, registerId) {
  return {
    registerId,
    customerId: state.customer?.id ?? null,
    items: state.lines.map((l) => ({
      productId: l.productId,
      quantity: l.quantity,
      manualDiscount: l.manualDiscount || undefined,
      priceOverride: l.priceOverride || undefined,
    })),
    transactionDiscount: state.transactionDiscount || undefined,
    approvalTokens: state.approvalTokens.length ? state.approvalTokens : undefined,
  };
}

