"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { authApi } from "./api";
import { useSessionStore } from "@/stores/session-store";

export const ME_KEY = ["auth", "me"];

/** The signed-in user (permissions, stores). `error.status === 401` means signed out. */
export function useMe(options = {}) {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: authApi.me,
    staleTime: 5 * 60_000,
    retry: false,
    ...options,
  });
}

/** Picks a valid active store for this user (keeps the remembered one if still allowed). */
export function syncActiveStore(user) {
  const { activeStoreId, setActiveStore } = useSessionStore.getState();
  const allowed = (user?.stores || []).map((s) => s.id);
  if (!activeStoreId || !allowed.includes(activeStoreId)) {
    setActiveStore(user?.defaultStoreId ?? allowed[0] ?? null);
  }
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: authApi.login,
    onSuccess: (user) => {
      queryClient.clear();
      syncActiveStore(user);
      queryClient.setQueryData(ME_KEY, user);
    },
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  const router = useRouter();
  return useMutation({
    mutationFn: authApi.logout,
    onSettled: () => {
      queryClient.clear();
      router.replace("/login");
    },
  });
}

export function useChangePassword() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: authApi.changePassword,
    onSuccess: (user) => queryClient.setQueryData(ME_KEY, user),
  });
}
