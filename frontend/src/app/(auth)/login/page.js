"use client";

import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { LoginForm } from "@/features/auth/login-form";
import { useLogin } from "@/features/auth/hooks";
import { landingPath } from "@/lib/permissions";

function safeNext(next) {
  // Only same-site relative paths, never "//evil.example" (open redirect).
  return next && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/login") ? next : null;
}

function LoginContent() {
  const router = useRouter();
  const params = useSearchParams();
  const login = useLogin();
  const notice = params.get("expired") ? "Your session ended. Please sign in again." : null;

  async function handleSubmit(values) {
    const user = await login.mutateAsync(values);
    if (user.mustChangePassword) {
      router.replace("/account?mustChange=1");
      return;
    }
    router.replace(safeNext(params.get("next")) || landingPath(user));
  }

  return <LoginForm onSubmit={handleSubmit} notice={notice} />;
}

export default function LoginPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-100 to-primary-50 px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-primary text-lg font-bold text-primary-foreground">
            P
          </div>
          <h1 className="mt-4 text-2xl font-semibold text-slate-900">Supermarket POS</h1>
          <p className="mt-1 text-sm text-slate-500">Sign in to continue</p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
          <Suspense>
            <LoginContent />
          </Suspense>
        </div>
        <p className="mt-6 text-center text-xs text-slate-400">Authorized staff only. Activity is logged.</p>
      </div>
    </main>
  );
}
