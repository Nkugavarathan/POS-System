"use client";

import { useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Plus, Search, Truck } from "lucide-react";
import { Card, PageHeader, Pagination, Table, Td, Th } from "@/components/ui/layout";
import { Badge, EmptyState, ErrorState, TableSkeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { Checkbox, Field, Input, Textarea } from "@/components/ui/form";
import { Modal } from "@/components/ui/dialog";
import { purchasingApi } from "@/features/backoffice/api";
import { applyFieldErrors, errorMessage } from "@/lib/api-error";
import { useDebouncedValue } from "@/lib/hooks";

const EMPTY = {
  code: "",
  name: "",
  contactPerson: "",
  phone: "",
  email: "",
  address: "",
  taxRegistrationNo: "",
  paymentTermsDays: 30,
  notes: "",
  active: true,
};

function SupplierDialog({ supplier, onClose }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState(supplier ? { ...EMPTY, ...Object.fromEntries(Object.entries(supplier).map(([k, v]) => [k, v ?? ""])) } : EMPTY);
  const [errors, setErrors] = useState({});
  const save = useMutation({
    mutationFn: () => {
      const body = {
        ...form,
        code: form.code.trim().toUpperCase(),
        paymentTermsDays: Number(form.paymentTermsDays) || 0,
        email: form.email || null,
        version: supplier?.version ?? null,
      };
      return supplier ? purchasingApi.updateSupplier(supplier.id, body) : purchasingApi.createSupplier(body);
    },
    onSuccess: (s) => {
      queryClient.invalidateQueries({ queryKey: ["suppliers"] });
      toast.success(`${s.name} saved`);
      onClose();
    },
    onError: (e) => {
      const fe = {};
      if (!applyFieldErrors(e, (name, { message }) => (fe[name] = message))) toast.error(errorMessage(e));
      setErrors(fe);
    },
  });
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === "checkbox" ? e.target.checked : e.target.value });
  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={supplier ? `Edit ${supplier.name}` : "New supplier"}
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={save.isPending} disabled={!form.code || !form.name} onClick={() => save.mutate()}>
            Save
          </Button>
        </>
      }
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Code" required error={errors.code} hint="e.g. DAIRYCO">
          {(a) => <Input {...a} value={form.code} onChange={set("code")} disabled={Boolean(supplier)} />}
        </Field>
        <Field label="Name" required error={errors.name}>
          {(a) => <Input {...a} value={form.name} onChange={set("name")} />}
        </Field>
        <Field label="Contact person">{(a) => <Input {...a} value={form.contactPerson} onChange={set("contactPerson")} />}</Field>
        <Field label="Phone" error={errors.phone}>
          {(a) => <Input {...a} value={form.phone} onChange={set("phone")} />}
        </Field>
        <Field label="Email" error={errors.email}>
          {(a) => <Input {...a} type="email" value={form.email} onChange={set("email")} />}
        </Field>
        <Field label="Tax registration no.">{(a) => <Input {...a} value={form.taxRegistrationNo} onChange={set("taxRegistrationNo")} />}</Field>
        <Field label="Payment terms (days)" error={errors.paymentTermsDays}>
          {(a) => <Input {...a} type="number" min={0} value={form.paymentTermsDays} onChange={set("paymentTermsDays")} />}
        </Field>
        <div className="flex items-end pb-2">
          <Checkbox label="Active" checked={form.active} onChange={set("active")} />
        </div>
        <Field label="Address" className="sm:col-span-2">
          {(a) => <Textarea {...a} rows={2} value={form.address} onChange={set("address")} />}
        </Field>
        <Field label="Notes" className="sm:col-span-2">
          {(a) => <Textarea {...a} rows={2} value={form.notes} onChange={set("notes")} />}
        </Field>
      </div>
    </Modal>
  );
}

export default function SuppliersPage() {
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState(null); // null | "new" | supplier
  const search = useDebouncedValue(q.trim(), 300);
  const params = { q: search || undefined, page, size: 25 };
  const list = useQuery({ queryKey: ["suppliers", params], queryFn: () => purchasingApi.suppliers(params), placeholderData: keepPreviousData });

  return (
    <div>
      <PageHeader
        title="Suppliers"
        description="Who you buy from: contacts and payment terms used on purchase orders."
        actions={
          <Button onClick={() => setEditing("new")}>
            <Plus className="h-4 w-4" aria-hidden="true" /> New supplier
          </Button>
        }
      />
      <Card>
        <div className="border-b border-slate-200 p-4">
          <div className="relative max-w-md">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <Input className="pl-9" placeholder="Search name or code" value={q} onChange={(e) => (setQ(e.target.value), setPage(0))} aria-label="Search suppliers" />
          </div>
        </div>
        {list.isError ? (
          <ErrorState error={list.error} onRetry={list.refetch} />
        ) : list.isLoading ? (
          <TableSkeleton cols={6} />
        ) : list.data.content.length === 0 ? (
          <EmptyState icon={Truck} title="No suppliers yet" action={<Button onClick={() => setEditing("new")}>Add the first one</Button>} />
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>Code</Th>
                  <Th>Name</Th>
                  <Th>Contact</Th>
                  <Th>Phone</Th>
                  <Th align="right">Terms</Th>
                  <Th>Status</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {list.data.content.map((s) => (
                  <tr key={s.id} className="cursor-pointer hover:bg-slate-50" onClick={() => setEditing(s)}>
                    <Td className="font-mono text-xs">{s.code}</Td>
                    <Td className="font-medium text-slate-900">{s.name}</Td>
                    <Td>{s.contactPerson || "—"}</Td>
                    <Td>{s.phone || "—"}</Td>
                    <Td align="right">{s.paymentTermsDays} days</Td>
                    <Td>{s.active ? <Badge color="green">Active</Badge> : <Badge>Inactive</Badge>}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={page} size={25} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPageChange={setPage} />
          </>
        )}
      </Card>
      {editing && <SupplierDialog supplier={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
    </div>
  );
}
