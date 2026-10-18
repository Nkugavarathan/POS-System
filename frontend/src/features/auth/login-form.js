"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Field, Input, PasswordInput } from "@/components/ui/form";
import { Alert } from "@/components/ui/feedback";
import { errorMessage } from "@/lib/api-error";

export const loginSchema = z.object({
  username: z.string().trim().min(1, "Enter your username").max(50),
  password: z.string().min(1, "Enter your password").max(128),
});

/**
 * Presentational login form. `onSubmit` receives the validated credentials and returns a
 * promise; its rejection is shown as a form-level error.
 */
export function LoginForm({ onSubmit, notice }) {
  const [serverError, setServerError] = useState(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(loginSchema), defaultValues: { username: "", password: "" } });

  const submit = handleSubmit(async (values) => {
    setServerError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      setServerError(errorMessage(error));
    }
  });

  return (
    <form onSubmit={submit} noValidate className="space-y-4" aria-label="Sign in">
      {notice && <Alert tone="info">{notice}</Alert>}
      {serverError && <Alert tone="danger">{serverError}</Alert>}
      <Field label="Username" error={errors.username?.message} required>
        {(a11y) => <Input {...a11y} autoComplete="username" autoFocus {...register("username")} />}
      </Field>
      <Field label="Password" error={errors.password?.message} required>
        {(a11y) => <PasswordInput {...a11y} autoComplete="current-password" {...register("password")} />}
      </Field>
      <Button type="submit" className="w-full" size="lg" loading={isSubmitting}>
        Sign in
      </Button>
    </form>
  );
}
