"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { CreditCard, LogOut, MapPin, Package, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/form";
import { Alert } from "@/components/ui/feedback";
import { errorMessage } from "@/lib/api-error";
import { formatMoney } from "@/lib/format";
import { shopApi, shopKeys } from "@/features/shop/api";
import { useShopCart } from "@/features/shop/cart-store";
import { useShopLogout, useShopMe, useShopStores } from "@/features/shop/hooks";
import { SHOP_NAME } from "@/features/shop/brand";

function LoyaltyCard({ me }) {
  return (
    <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-primary-700 via-primary-600 to-primary-800 p-6 text-white shadow-xl">
      <div className="pointer-events-none absolute -right-10 -top-10 h-40 w-40 rounded-full bg-secondary/30 blur-2xl" />
      <p className="text-xs font-bold uppercase tracking-[0.2em] text-primary-100">{SHOP_NAME} loyalty</p>
      <p className="mt-6 text-4xl font-extrabold tabular">{me.points.toLocaleString()}</p>
      <p className="text-sm text-primary-100">points · worth Rs {formatMoney(me.pointsValue)}</p>
      <div className="mt-6 flex items-end justify-between">
        <div>
          <p className="text-[11px] uppercase tracking-wider text-primary-200">Card</p>
          <p className="font-mono text-lg tracking-widest">{me.loyaltyCard || "—"}</p>
        </div>
        <p className="text-sm font-semibold">{me.fullName}</p>
      </div>
    </div>
  );
}

function LinkCardForm({ me }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ cardNumber: "", phone: "" });
  const [error, setError] = useState(null);
  const link = useMutation({
    mutationFn: () => shopApi.linkCard({ cardNumber: form.cardNumber.trim(), phone: form.phone.replace(/\s/g, "") }),
    onSuccess: (data) => {
      queryClient.setQueryData(shopKeys.me, data);
      queryClient.invalidateQueries({ queryKey: ["shop", "buy-again"] });
      setForm({ cardNumber: "", phone: "" });
      toast.success("Card linked. Your in-store points and history are now here.");
    },
    onError: (e) => setError(errorMessage(e)),
  });
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        setError(null);
        link.mutate();
      }}
      className="space-y-3"
    >
      <p className="text-sm text-slate-600">
        Have a physical card from the till? Link it to merge its points and receipts into this account. Your account card is{" "}
        <span className="font-mono">{me.loyaltyCard}</span>.
      </p>
      {error && <Alert tone="danger">{error}</Alert>}
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Card number" required>
          {(a11y) => (
            <Input {...a11y} inputMode="numeric" value={form.cardNumber} onChange={(e) => setForm({ ...form, cardNumber: e.target.value })} />
          )}
        </Field>
        <Field label="Mobile on that card" required>
          {(a11y) => <Input {...a11y} type="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />}
        </Field>
      </div>
      <Button type="submit" loading={link.isPending} disabled={!form.cardNumber || !form.phone} className="rounded-full">
        <CreditCard className="h-4 w-4" aria-hidden="true" /> Link card
      </Button>
    </form>
  );
}

export default function AccountPage() {
  const me = useShopMe();
  const stores = useShopStores();
  const setCartStore = useShopCart((s) => s.setStore);
  const queryClient = useQueryClient();
  const logout = useShopLogout();
  const router = useRouter();
  const setStore = useMutation({
    mutationFn: (storeId) => shopApi.setStore(storeId),
    onSuccess: (data) => {
      queryClient.setQueryData(shopKeys.me, data);
      setCartStore(data.preferredStoreId);
      toast.success("Home store updated.");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  if (me.isLoading) return <div className="mx-auto max-w-4xl px-4 py-10" />;
  if (!me.data) {
    return (
      <div className="mx-auto max-w-md px-4 py-16 text-center">
        <Link href="/shop/sign-in?next=/shop/account" className="font-semibold text-primary-700 hover:underline">
          Sign in to your account
        </Link>
      </div>
    );
  }
  const m = me.data;

  return (
    <div className="mx-auto max-w-4xl px-4 py-8">
      <h1 className="text-2xl font-extrabold text-slate-900">Hi, {m.fullName.split(" ")[0]}</h1>
      <p className="text-sm text-slate-500">
        {m.email} · {m.maskedPhone}
      </p>

      <div className="mt-6 grid gap-6 md:grid-cols-[340px_1fr]">
        <div className="space-y-4">
          <LoyaltyCard me={m} />
          <p className="flex items-start gap-2 rounded-2xl bg-secondary-50 p-3 text-xs text-secondary-900">
            <Sparkles className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            Give this card number at the till too — online and in-store purchases earn points on the same card and power your
            &ldquo;Running low?&rdquo; reminders.
          </p>
          <div className="grid gap-2">
            <Link href="/shop/orders" className="flex items-center gap-2 rounded-2xl border border-slate-200 bg-white p-3 text-sm font-medium text-slate-800 hover:bg-slate-50">
              <Package className="h-4 w-4 text-primary" aria-hidden="true" /> My orders
            </Link>
            <button
              type="button"
              onClick={() => logout.mutate(undefined, { onSettled: () => router.push("/shop") })}
              className="flex items-center gap-2 rounded-2xl border border-slate-200 bg-white p-3 text-left text-sm font-medium text-slate-800 hover:bg-slate-50"
            >
              <LogOut className="h-4 w-4 text-slate-500" aria-hidden="true" /> Sign out
            </button>
          </div>
        </div>

        <div className="space-y-6">
          <section className="rounded-3xl border border-slate-200 bg-white p-5">
            <h2 className="flex items-center gap-2 font-bold text-slate-900">
              <MapPin className="h-5 w-5 text-primary" aria-hidden="true" /> Home store
            </h2>
            <p className="mt-1 text-sm text-slate-500">Where you usually collect. You can still switch stores from the header.</p>
            <div className="mt-4 grid gap-2 sm:grid-cols-2">
              {(stores.data || []).map((s) => (
                <button
                  key={s.id}
                  type="button"
                  disabled={setStore.isPending}
                  onClick={() => setStore.mutate(s.id)}
                  className={`rounded-2xl border p-3 text-left ${
                    m.preferredStoreId === s.id ? "border-primary bg-primary-50 ring-2 ring-primary/20" : "border-slate-200 hover:border-slate-300"
                  }`}
                >
                  <span className="block text-sm font-semibold text-slate-900">{s.name}</span>
                  <span className="block text-xs text-slate-500">{[s.addressLine1, s.city].filter(Boolean).join(", ")}</span>
                </button>
              ))}
            </div>
          </section>

          <section className="rounded-3xl border border-slate-200 bg-white p-5">
            <h2 className="flex items-center gap-2 font-bold text-slate-900">
              <CreditCard className="h-5 w-5 text-primary" aria-hidden="true" /> Link an in-store card
            </h2>
            <div className="mt-3">
              <LinkCardForm me={m} />
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
