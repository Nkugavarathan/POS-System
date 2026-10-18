"use client";

import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";

/**
 * Per-browser conveniences only: the store the user last worked in and the register this
 * terminal uses. No tokens or personal data are kept here (tokens are HttpOnly cookies).
 */
export const useSessionStore = create(
  persist(
    (set) => ({
      activeStoreId: null,
      registerId: null,
      setActiveStore: (activeStoreId) => set({ activeStoreId }),
      setRegister: (registerId) => set({ registerId }),
      clear: () => set({ activeStoreId: null }),
    }),
    {
      name: "pos.session",
      storage: createJSONStorage(() => {
        try {
          return window.localStorage;
        } catch {
          return undefined;
        }
      }),
      partialize: (s) => ({ activeStoreId: s.activeStoreId, registerId: s.registerId }),
    },
  ),
);

/** Non-hook accessor for the Axios interceptor. */
export function getActiveStoreId() {
  return useSessionStore.getState().activeStoreId;
}
