"use client";

import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import { toMilli } from "@/lib/money";

/**
 * The shopper's basket and chosen store, kept in this browser only. It holds product ids,
 * quantities and display hints, never prices the server trusts: every total is re-quoted
 * by the backend, and the order is refused if the price moved since the quote.
 */
export const useShopCart = create(
  persist(
    (set, get) => ({
      storeId: null,
      items: [], // { productId, name, unitOfMeasure, fractional, price, offerPrice, quantity, preference }

      setStore: (storeId) => set({ storeId }),

      add: (product, quantity = product.fractional ? "0.5" : "1") =>
        set((s) => {
          const existing = s.items.find((i) => i.productId === product.id);
          if (existing) {
            return {
              items: s.items.map((i) =>
                i.productId === product.id ? { ...i, quantity: addQuantity(i.quantity, quantity) } : i,
              ),
            };
          }
          return {
            items: [
              ...s.items,
              {
                productId: product.id,
                name: product.name,
                unitOfMeasure: product.unitOfMeasure,
                fractional: product.fractional,
                price: product.price,
                offerPrice: product.offerPrice ?? null,
                quantity: String(quantity),
                preference: "SUBSTITUTE",
              },
            ],
          };
        }),

      setQuantity: (productId, quantity) =>
        set((s) => ({
          items:
            toMilli(quantity) <= 0n
              ? s.items.filter((i) => i.productId !== productId)
              : s.items.map((i) => (i.productId === productId ? { ...i, quantity: String(quantity) } : i)),
        })),

      setPreference: (productId, preference) =>
        set((s) => ({ items: s.items.map((i) => (i.productId === productId ? { ...i, preference } : i)) })),

      remove: (productId) => set((s) => ({ items: s.items.filter((i) => i.productId !== productId) })),

      clear: () => set({ items: [] }),

      quantityOf: (productId) => get().items.find((i) => i.productId === productId)?.quantity ?? null,
    }),
    {
      name: "shop.cart",
      version: 1,
      storage: createJSONStorage(() => {
        try {
          return window.localStorage;
        } catch {
          return undefined;
        }
      }),
    },
  ),
);

/** Adds two quantity strings exactly (milli-units), keeping up to 3 decimals. */
export function addQuantity(a, b) {
  const total = toMilli(a) + toMilli(b);
  const whole = total / 1000n;
  const frac = total % 1000n;
  return frac === 0n ? String(whole) : `${whole}.${String(frac).padStart(3, "0").replace(/0+$/, "")}`;
}

/** Step size for the +/- buttons: whole units, or 0.25 for weighed goods. */
export function stepFor(fractional) {
  return fractional ? "0.25" : "1";
}

export function subtractQuantity(a, b) {
  const total = toMilli(a) - toMilli(b);
  if (total <= 0n) return "0";
  return addQuantity("0", `${total / 1000n}.${String(total % 1000n).padStart(3, "0")}`);
}

export function cartCount(items) {
  return items.length;
}
