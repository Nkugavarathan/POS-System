"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { onSessionExpired } from "@/lib/http";
import { useMe, syncActiveStore } from "./hooks";
import { hasAny } from "@/lib/permissions";
import { Spinner, ErrorState } from "@/components/ui/feedback";

/**
 * Client-side session gate: redirects to /login when signed out and to the password page
 * while a temporary password is active. This is a UX convenience; the backend rejects
 * every unauthenticated or unauthorized call regardless.
 */
export function AuthGate({ children, anyOf = [] }) {
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const { data: user, error, isPending, refetch } = useMe();

  useEffect(() => {
    onSessionExpired(() => {
      queryClient.clear();
      router.replace(`/login?expired=1&next=${encodeURIComponent(window.location.pathname)}`);
    });
  }, [queryClient, router]);

  const signedOut = error?.status === 401;
  const mustChange = user?.mustChangePassword && pathname !== "/account";

  useEffect(() => {
    if (signedOut) router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [signedOut, pathname, router]);

  useEffect(() => {
    if (user) syncActiveStore(user);
  }, [user]);

  useEffect(() => {
    if (mustChange) router.replace("/account?mustChange=1");
  }, [mustChange, router]);

  if (isPending || signedOut || mustChange) {
    return (
      <div className="flex h-screen items-center justify-center">
        <Spinner label="Checking your session" />
      </div>
    );
  }
  if (error) {
    return <ErrorState error={error} onRetry={refetch} className="h-screen justify-center" />;
  }
  if (!hasAny(user, anyOf)) {
    return (
      <div className="flex h-screen flex-col items-center justify-center gap-2 text-center">
        <p className="text-lg font-semibold text-slate-800">You do not have access to this page</p>
        <button className="text-sm text-brand-700 underline" onClick={() => router.replace("/dashboard")}>
          Go to the dashboard
        </button>
      </div>
    );
  }
  return typeof children === "function" ? children(user) : children;
}
