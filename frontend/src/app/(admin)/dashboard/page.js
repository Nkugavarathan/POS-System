"use client";

import Link from "next/link";
import { useMe } from "@/features/auth/hooks";
import { NAV_SECTIONS } from "@/lib/navigation";
import { hasAny } from "@/lib/permissions";
import { PageHeader, Card } from "@/components/ui/layout";

export default function DashboardPage() {
  const { data: user } = useMe();
  const shortcuts = NAV_SECTIONS.flatMap((s) => s.items).filter(
    (i) => i.href !== "/dashboard" && i.href !== "/account" && hasAny(user, i.anyOf),
  );

  return (
    <div className="space-y-6">
      <PageHeader title={`Welcome, ${user?.fullName?.split(" ")[0] ?? ""}`} description="Today at your store" />
      <section aria-labelledby="shortcuts">
        <h2 id="shortcuts" className="mb-3 text-sm font-semibold text-slate-700">
          Go to
        </h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {shortcuts.map(({ href, label, icon: Icon }) => (
            <Link key={href} href={href} className="group">
              <Card className="flex items-center gap-3 p-4 transition group-hover:border-brand-300 group-hover:shadow">
                <span className="flex h-9 w-9 items-center justify-center rounded-md bg-brand-50 text-brand-700">
                  <Icon className="h-5 w-5" aria-hidden="true" />
                </span>
                <span className="text-sm font-medium text-slate-800">{label}</span>
              </Card>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}
