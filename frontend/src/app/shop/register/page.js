"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQueryClient } from "@tanstack/react-query";
import { CreditCard } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox, Field, Input } from "@/components/ui/form";
import { Alert } from "@/components/ui/feedback";
import { applyFieldErrors, errorMessage } from "@/lib/api-error";
import { shopApi, shopKeys } from "@/features/shop/api";
import { useShopCart } from "@/features/shop/cart-store";
import { AuthCard, safeNext } from "@/features/shop/components/auth-card";

const schema = z.object({
  fullName: z.string().trim().min(2, "Enter your name").max(120),
  email: z.string().trim().email("Enter a valid email").max(120),
  phone: z
    .string()
    .trim()
    .regex(/^\+?[0-9 ]{9,15}$/, "Enter a mobile number, e.g. 077 123 4567"),
  password: z
    .string()
    .min(8, "At least 8 characters")
    .max(128)
    .regex(/[A-Za-z]/, "Include a letter")
    .regex(/\d/, "Include a number"),
  hasCard: z.boolean(),
  loyaltyCardNumber: z.string().trim().max(20).optional(),
  marketingConsent: z.boolean(),
}).refine((v) => !v.hasCard || (v.loyaltyCardNumber && v.loyaltyCardNumber.length >= 4), {
  path: ["loyaltyCardNumber"],
  message: "Enter the number printed on your card",
});

function Register() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const queryClient = useQueryClient();
  const storeId = useShopCart((s) => s.storeId);
  const [serverError, setServerError] = useState(null);
  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { fullName: "", email: "", phone: "", password: "", hasCard: false, loyaltyCardNumber: "", marketingConsent: false },
  });
  const hasCard = watch("hasCard");

  const submit = handleSubmit(async (values) => {
    setServerError(null);
    try {
      const me = await shopApi.register({
        fullName: values.fullName,
        email: values.email,
        phone: values.phone.replace(/\s/g, ""),
        password: values.password,
        marketingConsent: values.marketingConsent,
        preferredStoreId: storeId,
        loyaltyCardNumber: values.hasCard ? values.loyaltyCardNumber : null,
      });
      queryClient.setQueryData(shopKeys.me, me);
      router.replace(next);
    } catch (e) {
      if (!applyFieldErrors(e, setError)) setServerError(errorMessage(e));
    }
  });

  return (
    <AuthCard
      title="Create your account"
      subtitle="Takes a minute. No card details needed - you pay in store."
      footer={
        <>
          Already have an account?{" "}
          <Link href={`/shop/sign-in?next=${encodeURIComponent(next)}`} className="font-semibold text-primary-700 hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={submit} noValidate className="space-y-4" aria-label="Create account">
        {serverError && <Alert tone="danger">{serverError}</Alert>}
        <Field label="Full name" error={errors.fullName?.message} required>
          {(a11y) => <Input {...a11y} autoComplete="name" autoFocus {...register("fullName")} />}
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Email" error={errors.email?.message} required>
            {(a11y) => <Input {...a11y} type="email" autoComplete="email" {...register("email")} />}
          </Field>
          <Field label="Mobile" error={errors.phone?.message} required hint="For your pickup code">
            {(a11y) => <Input {...a11y} type="tel" autoComplete="tel" {...register("phone")} />}
          </Field>
        </div>
        <Field label="Password" error={errors.password?.message} required hint="8+ characters with a letter and a number">
          {(a11y) => <Input {...a11y} type="password" autoComplete="new-password" {...register("password")} />}
        </Field>

        <div className="rounded-2xl border border-primary-200 bg-primary-50/60 p-4">
          <Checkbox label="I already have an in-store loyalty card" {...register("hasCard")} />
          {hasCard ? (
            <div className="mt-3">
              <Field
                label="Card number"
                error={errors.loyaltyCardNumber?.message}
                hint="It must be registered to the mobile number above. Your points and till history come with it."
              >
                {(a11y) => (
                  <div className="relative">
                    <CreditCard className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                    <Input {...a11y} inputMode="numeric" className="pl-9" {...register("loyaltyCardNumber")} />
                  </div>
                )}
              </Field>
            </div>
          ) : (
            <p className="mt-1 pl-6 text-xs text-slate-500">No card? We&rsquo;ll create one - use it at the till to earn points too.</p>
          )}
        </div>

        <Checkbox label="Send me rescue deal alerts and offers" {...register("marketingConsent")} />

        <Button type="submit" size="lg" className="w-full rounded-full" loading={isSubmitting}>
          Create account
        </Button>
      </form>
    </AuthCard>
  );
}

export default function RegisterPage() {
  return (
    <Suspense>
      <Register />
    </Suspense>
  );
}
