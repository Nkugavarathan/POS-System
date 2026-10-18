"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, CheckCheck, PackageCheck, RefreshCcw, Store, XCircle } from "lucide-react";
import { cn } from "@/lib/cn";
import { formatDateTime } from "@/lib/format";
import { shopApi, shopKeys } from "@/features/shop/api";
import { useShopMe } from "@/features/shop/hooks";
import { ErrorState } from "@/components/ui/feedback";

function iconFor(kind = "") {
  if (/SUBSTITUT/.test(kind)) return RefreshCcw;
  if (/READY|COLLECT/.test(kind)) return PackageCheck;
  if (/CANCEL|EXPIR/.test(kind)) return XCircle;
  if (/PACK|CONFIRM/.test(kind)) return Store;
  return Bell;
}

export default function NotificationsPage() {
  const me = useShopMe();
  const router = useRouter();
  const queryClient = useQueryClient();
  const list = useQuery({
    queryKey: shopKeys.notifications,
    queryFn: () => shopApi.notifications({ size: 50 }),
    enabled: Boolean(me.data),
    refetchInterval: 30_000,
  });
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: shopKeys.notifications });
    queryClient.invalidateQueries({ queryKey: shopKeys.unread });
  };
  const readAll = useMutation({ mutationFn: shopApi.markAllRead, onSuccess: refresh });
  const open = useMutation({ mutationFn: (id) => shopApi.markRead(id), onSettled: refresh });

  if (me.isLoading) return <div className="mx-auto max-w-2xl px-4 py-10" />;
  if (!me.data) {
    return (
      <div className="mx-auto max-w-md px-4 py-16 text-center">
        <Link href="/shop/sign-in?next=/shop/notifications" className="font-semibold text-primary-700 hover:underline">
          Sign in to see notifications
        </Link>
      </div>
    );
  }

  const items = list.data?.content || [];
  return (
    <div className="mx-auto max-w-2xl px-4 py-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-extrabold text-slate-900">Notifications</h1>
        {items.some((n) => !n.read) && (
          <button
            type="button"
            onClick={() => readAll.mutate()}
            className="inline-flex items-center gap-1 text-sm font-medium text-primary-700 hover:underline"
          >
            <CheckCheck className="h-4 w-4" aria-hidden="true" /> Mark all read
          </button>
        )}
      </div>
      <div className="mt-6">
        {list.isError ? (
          <ErrorState error={list.error} onRetry={list.refetch} />
        ) : list.isLoading ? (
          <div className="space-y-2">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-20 animate-pulse rounded-2xl bg-slate-100" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center rounded-3xl border border-dashed border-slate-300 bg-white px-6 py-16 text-center">
            <Bell className="h-10 w-10 text-slate-300" aria-hidden="true" />
            <p className="mt-3 font-semibold text-slate-800">You&rsquo;re all caught up</p>
            <p className="mt-1 text-sm text-slate-500">Order updates will appear here as they happen.</p>
          </div>
        ) : (
          <ul className="overflow-hidden rounded-3xl border border-slate-200 bg-white">
            {items.map((n) => {
              const Icon = iconFor(n.kind);
              const number = n.orderNumber;
              return (
                <li key={n.id} className="border-b border-slate-100 last:border-0">
                  <button
                    type="button"
                    onClick={() => {
                      if (!n.read) open.mutate(n.id);
                      if (number) router.push(`/shop/orders/${number}`);
                    }}
                    className={cn("flex w-full items-start gap-3 p-4 text-left hover:bg-slate-50", !n.read && "bg-primary-50/50")}
                  >
                    <span
                      className={cn(
                        "flex h-10 w-10 shrink-0 items-center justify-center rounded-full",
                        !n.read ? "bg-primary text-primary-foreground" : "bg-slate-100 text-slate-500",
                      )}
                    >
                      <Icon className="h-5 w-5" aria-hidden="true" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className={cn("block text-sm", n.read ? "text-slate-700" : "font-semibold text-slate-900")}>{n.title}</span>
                      {n.body && <span className="mt-0.5 block text-sm text-slate-500">{n.body}</span>}
                      <span className="mt-1 block text-xs text-slate-400">{formatDateTime(n.createdAt)}</span>
                    </span>
                    {!n.read && <span className="mt-2 h-2.5 w-2.5 shrink-0 rounded-full bg-primary" aria-label="Unread" />}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
