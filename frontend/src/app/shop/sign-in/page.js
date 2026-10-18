"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/form";
import { Alert } from "@/components/ui/feedback";
import { errorMessage } from "@/lib/api-error";
import { shopApi, shopKeys } from "@/features/shop/api";
import { AuthCard, safeNext } from "@/features/shop/components/auth-card";

const schema = z.object({
  email: z.string().trim().min(1, "Enter your email").max(120),
  password: z.string().min(1, "Enter your password").max(128),
});

function SignIn() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const queryClient = useQueryClient();
  const [serverError, setServerError] = useState(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(schema), defaultValues: { email: "", password: "" } });

  const submit = handleSubmit(async (values) => {
    setServerError(null);
    try {
      const me = await shopApi.login(values);
      queryClient.setQueryData(shopKeys.me, me);
      queryClient.invalidateQueries({ queryKey: shopKeys.unread });
      router.replace(next);
    } catch (e) {
      setServerError(errorMessage(e));
    }
  });

  return (
    <AuthCard
      title="Welcome back"
      subtitle="Sign in to place orders and follow them live."
      footer={
        <>
          New here?{" "}
          <Link href={`/shop/register?next=${encodeURIComponent(next)}`} className="font-semibold text-primary-700 hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={submit} noValidate className="space-y-4" aria-label="Sign in">
        {serverError && <Alert tone="danger">{serverError}</Alert>}
        <Field label="Email" error={errors.email?.message} required>
          {(a11y) => <Input {...a11y} type="email" autoComplete="email" autoFocus {...register("email")} />}
        </Field>
        <Field label="Password" error={errors.password?.message} required>
          {(a11y) => <Input {...a11y} type="password" autoComplete="current-password" {...register("password")} />}
        </Field>
        <Button type="submit" size="lg" className="w-full rounded-full" loading={isSubmitting}>
          Sign in
        </Button>
      </form>
    </AuthCard>
  );
}

export default function SignInPage() {
  return (
    <Suspense>
      <SignIn />
    </Suspense>
  );
}
