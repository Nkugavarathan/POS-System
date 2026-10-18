"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Checkbox, Field, Input, Select } from "@/components/ui/form";
import { storesApi, userKeys, usersApi } from "./api";

const base = {
  fullName: z.string().trim().min(1, "Required").max(120),
  email: z.union([z.literal(""), z.string().trim().email("Invalid email").max(120)]),
  roles: z.array(z.string()).min(1, "Choose at least one role"),
  storeIds: z.array(z.number()).min(1, "Choose at least one store"),
  defaultStoreId: z.number().nullable(),
};

export const createUserSchema = z.object({
  username: z
    .string()
    .trim()
    .regex(/^[A-Za-z0-9._-]{3,50}$/, "3-50 letters, digits, dots, underscores or dashes"),
  temporaryPassword: z
    .string()
    .min(8, "At least 8 characters")
    .max(128)
    .regex(/[A-Za-z]/, "Include a letter")
    .regex(/\d/, "Include a digit"),
  ...base,
});

export const updateUserSchema = z.object(base);

/**
 * Create / edit form. On edit, `user` is the UserDetailDto. Roles the current admin may not
 * grant are still shown; the server rejects forbidden grants (e.g. SUPER_ADMIN by an ADMIN).
 */
export function UserForm({ user, onSubmit, submitting, setErrorRef }) {
  const isEdit = Boolean(user);
  const { data: roles = [] } = useQuery({ queryKey: userKeys.roles, queryFn: usersApi.roles });
  const { data: stores = [] } = useQuery({ queryKey: userKeys.stores, queryFn: storesApi.accessible });

  const form = useForm({
    resolver: zodResolver(isEdit ? updateUserSchema : createUserSchema),
    defaultValues: isEdit
      ? {
          fullName: user.fullName,
          email: user.email ?? "",
          roles: user.roles,
          storeIds: user.stores.map((s) => s.id),
          defaultStoreId: user.stores.find((s) => s.isDefault)?.id ?? null,
        }
      : { username: "", temporaryPassword: "", fullName: "", email: "", roles: [], storeIds: [], defaultStoreId: null },
  });
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    setError,
    formState: { errors },
  } = form;
  if (setErrorRef) setErrorRef.current = setError;

  const selectedRoles = watch("roles");
  const selectedStores = watch("storeIds");

  function toggle(field, value) {
    const current = form.getValues(field);
    setValue(field, current.includes(value) ? current.filter((v) => v !== value) : [...current, value], {
      shouldValidate: true,
    });
  }

  return (
    <form onSubmit={handleSubmit((v) => onSubmit({ ...v, email: v.email || null }))} noValidate className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2">
        {!isEdit && (
          <Field label="Username" error={errors.username?.message} required>
            {(a) => <Input {...a} autoComplete="off" {...register("username")} />}
          </Field>
        )}
        <Field label="Full name" error={errors.fullName?.message} required>
          {(a) => <Input {...a} {...register("fullName")} />}
        </Field>
        <Field label="Email" error={errors.email?.message} hint="Optional">
          {(a) => <Input {...a} type="email" {...register("email")} />}
        </Field>
        {!isEdit && (
          <Field
            label="Temporary password"
            error={errors.temporaryPassword?.message}
            hint="The user must change it at first sign-in."
            required
          >
            {(a) => <Input {...a} type="password" autoComplete="new-password" {...register("temporaryPassword")} />}
          </Field>
        )}
      </div>

      <fieldset>
        <legend className="text-sm font-medium text-slate-700">Roles</legend>
        <div className="mt-2 grid gap-2 sm:grid-cols-2">
          {roles.map((r) => (
            <Checkbox
              key={r.code}
              label={
                <span>
                  {r.name} <span className="text-xs text-slate-400">({r.code})</span>
                </span>
              }
              checked={selectedRoles.includes(r.code)}
              onChange={() => toggle("roles", r.code)}
            />
          ))}
        </div>
        {errors.roles && <p className="mt-1 text-xs font-medium text-red-600">{errors.roles.message}</p>}
      </fieldset>

      <fieldset>
        <legend className="text-sm font-medium text-slate-700">Store access</legend>
        <div className="mt-2 grid gap-2 sm:grid-cols-2">
          {stores.map((s) => (
            <Checkbox key={s.id} label={s.name} checked={selectedStores.includes(s.id)} onChange={() => toggle("storeIds", s.id)} />
          ))}
        </div>
        {errors.storeIds && <p className="mt-1 text-xs font-medium text-red-600">{errors.storeIds.message}</p>}
        {selectedStores.length > 1 && (
          <Field label="Default store" className="mt-3 max-w-xs">
            {(a) => (
              <Select
                {...a}
                value={watch("defaultStoreId") ?? ""}
                onChange={(e) => setValue("defaultStoreId", e.target.value ? Number(e.target.value) : null)}
              >
                <option value="">First assigned</option>
                {stores
                  .filter((s) => selectedStores.includes(s.id))
                  .map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
              </Select>
            )}
          </Field>
        )}
      </fieldset>

      <div className="flex justify-end">
        <Button type="submit" loading={submitting}>
          {isEdit ? "Save changes" : "Create user"}
        </Button>
      </div>
    </form>
  );
}
