"use client";

import { AuthGate } from "@/features/auth/auth-gate";
import { AppShell } from "@/components/app-shell";

export default function AdminLayout({ children }) {
  return <AuthGate>{(user) => <AppShell user={user}>{children}</AppShell>}</AuthGate>;
}
