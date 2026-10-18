"use client";

import { useEffect } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { isApiError } from "@/lib/api-error";
import { shopApi, shopKeys } from "./api";
import { useShopCart } from "./cart-store";

/** The signed-in shopper, or null when browsing as a guest (a 401 is not an error here). */
export function useShopMe() {
  return useQuery({
    queryKey: shopKeys.me,
    queryFn: async () => {
      try {
        return await shopApi.me();
      } catch (e) {
        if (isApiError(e) && (e.status === 401 || e.status === 403)) return null;
        throw e;
      }
    },
    staleTime: 60_000,
  });
}

export function useShopStores() {
  return useQuery({ queryKey: shopKeys.stores, queryFn: shopApi.stores, staleTime: 10 * 60_000 });
}

/**
 * The store the shopper is buying from: their saved choice, else their account's preferred
 * store, else the first open store. Prices, stock and rescue deals are all per store.
 */
export function useActiveStore() {
  const storeId = useShopCart((s) => s.storeId);
  const setStore = useShopCart((s) => s.setStore);
  const stores = useShopStores();
  const me = useShopMe();

  useEffect(() => {
    if (!stores.data?.length) return;
    const valid = stores.data.some((s) => s.id === storeId);
    if (valid) return;
    const preferred = stores.data.find((s) => s.id === me.data?.preferredStoreId);
    setStore((preferred || stores.data[0]).id);
  }, [stores.data, storeId, me.data?.preferredStoreId, setStore]);

  const store = stores.data?.find((s) => s.id === storeId) || null;
  return { storeId: store ? storeId : null, store, stores: stores.data || [], isLoading: stores.isLoading };
}

export function useUnreadCount(enabled) {
  return useQuery({
    queryKey: shopKeys.unread,
    queryFn: shopApi.unread,
    enabled,
    refetchInterval: enabled ? 30_000 : false,
    select: (d) => d.unread,
  });
}

export function useShopLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: shopApi.logout,
    onSettled: () => {
      queryClient.setQueryData(shopKeys.me, null);
      queryClient.removeQueries({ queryKey: ["shop", "orders"] });
      queryClient.removeQueries({ queryKey: ["shop", "order"] });
      queryClient.removeQueries({ queryKey: shopKeys.notifications });
      queryClient.removeQueries({ queryKey: ["shop", "buy-again"] });
    },
  });
}

/**
 * Live server quote for the basket at the active store (prices, promotions, rescue deals,
 * availability). The basket UI shows these numbers, never locally computed totals.
 */
export function useCartQuote(storeId, items) {
  const body = storeId && items.length
    ? {
        storeId,
        items: items.map((i) => ({ productId: i.productId, quantity: i.quantity, substitutionPreference: i.preference })),
      }
    : null;
  return useQuery({
    queryKey: shopKeys.quote(body),
    queryFn: () => shopApi.quote(body),
    enabled: Boolean(body),
    placeholderData: keepPreviousData,
    staleTime: 15_000,
  });
}
