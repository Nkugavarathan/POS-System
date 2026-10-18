"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Plus } from "lucide-react";
import { PageHeader, Card, CardHeader, Table, Th, Td } from "@/components/ui/layout";
import { Field, Input, Textarea, Checkbox } from "@/components/ui/form";
import { Badge, ErrorState, Skeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { AuthGate } from "@/features/auth/auth-gate";
import { useMe } from "@/features/auth/hooks";
import { storesApi } from "@/features/users/api";
import { useSessionStore } from "@/stores/session-store";
import { applyFieldErrors, errorMessage } from "@/lib/api-error";
import { hasPermission } from "@/lib/permissions";

const storeSchema = z.object({
  name: z.string().trim().min(1, "Required").max(120),
  addressLine1: z.string().max(150).optional(),
  addressLine2: z.string().max(150).optional(),
  city: z.string().max(80).optional(),
  phone: z.string().max(30).optional(),
  email: z.union([z.literal(""), z.string().email("Invalid email").max(120)]).optional(),
  taxRegistrationNo: z.string().max(40).optional(),
  pricesIncludeTax: z.boolean(),
  receiptHeader: z.string().max(500).optional(),
  receiptFooter: z.string().max(500).optional(),
});

function StoreProfile({ storeId, canEdit }) {
  const queryClient = useQueryClient();
  const { data: store, error, isPending, refetch } = useQuery({
    queryKey: ["store", storeId],
    queryFn: () => storesApi.get(storeId),
  });
  const form = useForm({ resolver: zodResolver(storeSchema) });
  useEffect(() => {
    if (store) {
      form.reset({
        ...Object.fromEntries(Object.entries(store).map(([k, v]) => [k, v ?? ""])),
        pricesIncludeTax: store.pricesIncludeTax,
      });
    }
  }, [store, form]);

  const save = useMutation({
    mutationFn: (values) => storesApi.update(storeId, { ...values, version: store.version }),
    onSuccess: (s) => {
      queryClient.setQueryData(["store", storeId], s);
      toast.success("Store profile saved.");
    },
    onError: (e) => {
      if (e.code === "CONCURRENT_MODIFICATION") refetch();
      if (!applyFieldErrors(e, form.setError)) toast.error(errorMessage(e));
    },
  });

  if (isPending) return <Skeleton className="h-72" />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  const errors = form.formState.errors;

  return (
    <Card>
      <CardHeader
        title="Store profile & receipt"
        description={`${store.code} · currency ${store.currency} · timezone ${store.timezone}`}
      />
      <form onSubmit={form.handleSubmit((v) => save.mutate(v))} noValidate className="grid gap-4 p-5 sm:grid-cols-2">
        <fieldset disabled={!canEdit} className="contents">
          <Field label="Store name" error={errors.name?.message} required>
            {(a) => <Input {...a} {...form.register("name")} />}
          </Field>
          <Field label="Phone" error={errors.phone?.message}>
            {(a) => <Input {...a} {...form.register("phone")} />}
          </Field>
          <Field label="Address line 1">{(a) => <Input {...a} {...form.register("addressLine1")} />}</Field>
          <Field label="Address line 2">{(a) => <Input {...a} {...form.register("addressLine2")} />}</Field>
          <Field label="City">{(a) => <Input {...a} {...form.register("city")} />}</Field>
          <Field label="Email" error={errors.email?.message}>
            {(a) => <Input {...a} type="email" {...form.register("email")} />}
          </Field>
          <Field label="Tax registration number">{(a) => <Input {...a} {...form.register("taxRegistrationNo")} />}</Field>
          <div className="flex items-end pb-2">
            <Checkbox label="Shelf prices include tax" {...form.register("pricesIncludeTax")} />
          </div>
          <Field label="Receipt header" className="sm:col-span-2">
            {(a) => <Textarea {...a} rows={2} {...form.register("receiptHeader")} />}
          </Field>
          <Field label="Receipt footer" className="sm:col-span-2">
            {(a) => <Textarea {...a} rows={2} {...form.register("receiptFooter")} />}
          </Field>
        </fieldset>
        {canEdit && (
          <div className="flex justify-end sm:col-span-2">
            <Button type="submit" loading={save.isPending}>
              Save profile
            </Button>
          </div>
        )}
      </form>
    </Card>
  );
}

function RegisterDialog({ storeId, register, onClose }) {
  const queryClient = useQueryClient();
  const [code, setCode] = useState(register?.code ?? "");
  const [name, setName] = useState(register?.name ?? "");
  const [active, setActive] = useState(register?.active ?? true);
  const save = useMutation({
    mutationFn: () => {
      const body = { code: code.trim().toUpperCase(), name: name.trim(), active, version: register?.version };
      return register ? storesApi.updateRegister(storeId, register.id, body) : storesApi.createRegister(storeId, body);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["registers", storeId] });
      toast.success("Register saved.");
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={register ? `Edit register ${register.code}` : "New register"}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={() => save.mutate()} loading={save.isPending} disabled={!code.trim() || !name.trim()}>
            Save
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Field label="Code" hint="Printed on receipts, e.g. R04">
          {(a) => <Input {...a} value={code} maxLength={20} onChange={(e) => setCode(e.target.value)} />}
        </Field>
        <Field label="Name">{(a) => <Input {...a} value={name} onChange={(e) => setName(e.target.value)} />}</Field>
        <Checkbox label="Active" checked={active} onChange={(e) => setActive(e.target.checked)} />
      </div>
    </Modal>
  );
}

function Registers({ storeId, canEdit }) {
  const [editing, setEditing] = useState(undefined);
  const { data, error, isPending, refetch } = useQuery({
    queryKey: ["registers", storeId],
    queryFn: () => storesApi.registers(storeId),
  });
  return (
    <Card>
      <CardHeader
        title="Registers"
        description="Checkout terminals. A cashier opens a shift on one register at a time."
        actions={
          canEdit && (
            <Button size="sm" variant="secondary" onClick={() => setEditing(null)}>
              <Plus className="h-4 w-4" aria-hidden="true" /> Add register
            </Button>
          )
        }
      />
      {isPending ? (
        <Skeleton className="m-5 h-24" />
      ) : error ? (
        <ErrorState error={error} onRetry={refetch} />
      ) : (
        <Table>
          <thead>
            <tr>
              <Th>Code</Th>
              <Th>Name</Th>
              <Th>Status</Th>
              {canEdit && <Th align="right">Actions</Th>}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {data.map((r) => (
              <tr key={r.id}>
                <Td className="font-mono">{r.code}</Td>
                <Td>{r.name}</Td>
                <Td>{r.active ? <Badge color="green">active</Badge> : <Badge>inactive</Badge>}</Td>
                {canEdit && (
                  <Td align="right">
                    <Button size="sm" variant="ghost" onClick={() => setEditing(r)}>
                      Edit
                    </Button>
                  </Td>
                )}
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {editing !== undefined && <RegisterDialog storeId={storeId} register={editing} onClose={() => setEditing(undefined)} />}
    </Card>
  );
}

const SETTING_LABELS = {
  "cash.payoutApprovalThreshold": "Pay-outs above this amount need manager approval",
  "shift.varianceApprovalThreshold": "Cash variances above this amount need manager approval",
  "void.lineApprovalThreshold": "Line voids above this amount need manager approval",
  "void.cartApprovalRequired": "Cancelling a whole cart needs manager approval",
  "returns.windowDays": "Return window (days)",
  "returns.approvalRequired": "Returns always need manager approval",
  "inventory.writeOffApprovalThreshold": "Write-offs above this cost value need approval",
  "purchasing.poApprovalThreshold": "Purchase orders above this total need approval",
  "loyalty.enabled": "Loyalty programme enabled",
  "loyalty.pointsPer100": "Loyalty points earned per 100 of net sales",
  "loyalty.pointValue": "Value of one loyalty point when redeemed",
  "loyalty.minRedeemPoints": "Minimum points per redemption",
  "heldCart.ttlHours": "Held carts expire after (hours)",
  "receipt.paperWidth": "Receipt paper (80mm or A4)",
};

function BusinessSettings({ canEdit }) {
  const queryClient = useQueryClient();
  const { data, error, isPending, refetch } = useQuery({ queryKey: ["settings"], queryFn: storesApi.settings });
  const [draft, setDraft] = useState({});
  const save = useMutation({
    mutationFn: () =>
      storesApi.updateSettings(
        "STORE",
        Object.entries(draft).map(([key, value]) => ({ key, value: String(value) })),
      ),
    onSuccess: (s) => {
      queryClient.setQueryData(["settings"], s);
      setDraft({});
      toast.success("Settings saved for this store.");
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  if (isPending) return <Skeleton className="h-64" />;
  if (error) return <ErrorState error={error} onRetry={refetch} />;
  return (
    <Card>
      <CardHeader title="Business rules" description="Values marked “store” override the global default for this store." />
      <div className="divide-y divide-slate-100">
        {data.map((s) => {
          const value = draft[s.key] ?? s.value;
          return (
            <div key={s.key} className="flex flex-col gap-2 px-5 py-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="text-sm text-slate-800">{SETTING_LABELS[s.key] ?? s.key}</p>
                <p className="text-xs text-slate-400">
                  {s.key} · <Badge color={s.scope === "STORE" ? "brand" : "gray"}>{s.scope.toLowerCase()}</Badge>
                </p>
              </div>
              {s.valueType === "BOOLEAN" ? (
                <Checkbox
                  label={value === "true" ? "Yes" : "No"}
                  checked={value === "true"}
                  disabled={!canEdit}
                  onChange={(e) => setDraft({ ...draft, [s.key]: e.target.checked ? "true" : "false" })}
                />
              ) : (
                <Input
                  aria-label={s.key}
                  className="sm:w-40"
                  value={value}
                  disabled={!canEdit}
                  inputMode={s.valueType === "STRING" ? "text" : "decimal"}
                  onChange={(e) => setDraft({ ...draft, [s.key]: e.target.value })}
                />
              )}
            </div>
          );
        })}
      </div>
      {canEdit && (
        <div className="flex justify-end border-t border-slate-200 px-5 py-3">
          <Button onClick={() => save.mutate()} disabled={!Object.keys(draft).length} loading={save.isPending}>
            Save settings
          </Button>
        </div>
      )}
    </Card>
  );
}

export default function SettingsPage() {
  const { data: me } = useMe();
  const storeId = useSessionStore((s) => s.activeStoreId) ?? me?.defaultStoreId;
  const sysConfig = hasPermission(me, "SYSTEM_CONFIG");
  return (
    <AuthGate anyOf={["SYSTEM_CONFIG", "REGISTER_MANAGE"]}>
      <div className="mx-auto max-w-4xl space-y-6">
        <PageHeader title="Store settings" description="Receipt details, registers and business rules for the active store." />
        {storeId && <StoreProfile storeId={storeId} canEdit={sysConfig} />}
        {storeId && <Registers storeId={storeId} canEdit={hasPermission(me, "REGISTER_MANAGE")} />}
        <BusinessSettings canEdit={sysConfig} />
      </div>
    </AuthGate>
  );
}
