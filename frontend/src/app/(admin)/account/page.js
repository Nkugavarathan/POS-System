"use client";

import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { useMutation } from "@tanstack/react-query";
import { PageHeader, Card, CardHeader } from "@/components/ui/layout";
import { Field, Input, PasswordInput } from "@/components/ui/form";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/feedback";
import { useChangePassword, useMe } from "@/features/auth/hooks";
import { applyFieldErrors, errorMessage } from "@/lib/api-error";
import { api } from "@/lib/http";
import { hasAny, landingPath } from "@/lib/permissions";

export const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, "Enter your current password"),
    newPassword: z
      .string()
      .min(8, "At least 8 characters")
      .max(128)
      .regex(/[A-Za-z]/, "Include at least one letter")
      .regex(/\d/, "Include at least one digit"),
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, { path: ["confirmPassword"], message: "Passwords do not match" });

const pinSchema = z.object({ pin: z.string().regex(/^\d{4,6}$/, "4-6 digits") });

function PasswordCard({ mustChange }) {
  const router = useRouter();
  const change = useChangePassword();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm({ resolver: zodResolver(passwordSchema) });

  const submit = handleSubmit(async ({ currentPassword, newPassword }) => {
    try {
      const user = await change.mutateAsync({ currentPassword, newPassword });
      reset();
      toast.success("Password changed. Other sessions were signed out.");
      if (mustChange) router.replace(landingPath(user));
    } catch (error) {
      if (!applyFieldErrors(error, setError)) toast.error(errorMessage(error));
    }
  });

  return (
    <Card>
      <CardHeader title="Change password" description="At least 8 characters with a letter and a digit." />
      <form onSubmit={submit} noValidate className="space-y-4 p-5">
        {mustChange && (
          <Alert tone="warning" title="Temporary password">
            Your administrator set a temporary password. Choose your own to continue.
          </Alert>
        )}
        <Field label="Current password" error={errors.currentPassword?.message} required>
          {(a) => <PasswordInput {...a} autoComplete="current-password" {...register("currentPassword")} />}
        </Field>
        <Field label="New password" error={errors.newPassword?.message} required>
          {(a) => <PasswordInput {...a} autoComplete="new-password" {...register("newPassword")} />}
        </Field>
        <Field label="Confirm new password" error={errors.confirmPassword?.message} required>
          {(a) => <PasswordInput {...a} autoComplete="new-password" {...register("confirmPassword")} />}
        </Field>
        <Button type="submit" loading={change.isPending}>
          Update password
        </Button>
      </form>
    </Card>
  );
}

function PinCard({ user }) {
  const setPin = useMutation({ mutationFn: ({ pin }) => api.put(`/users/${user.id}/pin`, { pin }) });
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({ resolver: zodResolver(pinSchema) });
  const submit = handleSubmit(async (values) => {
    try {
      await setPin.mutateAsync(values);
      reset();
      toast.success("Approval PIN saved.");
    } catch (error) {
      toast.error(errorMessage(error));
    }
  });
  return (
    <Card>
      <CardHeader
        title="Approval PIN"
        description="Used to approve discounts, voids and returns at a cashier's counter without signing in."
      />
      <form onSubmit={submit} noValidate className="flex items-end gap-3 p-5">
        <Field label="New PIN" error={errors.pin?.message} className="w-40">
          {(a) => <PasswordInput {...a} inputMode="numeric" autoComplete="off" maxLength={6} {...register("pin")} />}
        </Field>
        <Button type="submit" loading={setPin.isPending}>
          Save PIN
        </Button>
      </form>
    </Card>
  );
}

function AccountContent() {
  const params = useSearchParams();
  const { data: user } = useMe();
  const mustChange = Boolean(user?.mustChangePassword || params.get("mustChange"));
  const canApprove = hasAny(user, ["DISCOUNT_APPROVE", "VOID_APPROVE", "RETURN_APPROVE", "PRICE_OVERRIDE"]);
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <PageHeader title="My account" description={`${user?.fullName} · ${user?.username}`} />
      <PasswordCard mustChange={mustChange} />
      {canApprove && !mustChange && <PinCard user={user} />}
    </div>
  );
}

export default function AccountPage() {
  return (
    <Suspense>
      <AccountContent />
    </Suspense>
  );
}
