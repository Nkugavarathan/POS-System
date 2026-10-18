"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Plus, Search, UserSquare2 } from "lucide-react";
import { Card, PageHeader, Pagination, Table, Td, Th } from "@/components/ui/layout";
import { Badge, EmptyState, ErrorState, TableSkeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { Checkbox, Field, Input } from "@/components/ui/form";
import { Modal } from "@/components/ui/dialog";
import { useMe } from "@/features/auth/hooks";
import { customersApi } from "@/features/backoffice/api";
import { applyFieldErrors, errorMessage } from "@/lib/api-error";
import { useDebouncedValue } from "@/lib/hooks";
import { hasPermission } from "@/lib/permissions";

function NewCustomerDialog({ onClose }) {
  const router = useRouter();
  const [form, setForm] = useState({ fullName: "", phone: "", email: "", marketingConsent: false, enrolLoyalty: true });
  const [errors, setErrors] = useState({});
  const create = useMutation({
    mutationFn: () => customersApi.create({ ...form, email: form.email || null, phone: form.phone || null }),
    onSuccess: (c) => {
      toast.success(`${c.fullName} added${c.loyalty ? ` · card ${c.loyalty.cardNumber}` : ""}`);
      router.push(`/customers/${c.id}`);
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
      title="New customer"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={create.isPending} disabled={!form.fullName.trim()} onClick={() => create.mutate()}>
            Create
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <Field label="Full name" required error={errors.fullName}>
          {(a) => <Input {...a} autoFocus value={form.fullName} onChange={set("fullName")} />}
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Mobile" error={errors.phone}>
            {(a) => <Input {...a} type="tel" value={form.phone} onChange={set("phone")} />}
          </Field>
          <Field label="Email" error={errors.email}>
            {(a) => <Input {...a} type="email" value={form.email} onChange={set("email")} />}
          </Field>
        </div>
        <Checkbox label="Issue a loyalty card" checked={form.enrolLoyalty} onChange={set("enrolLoyalty")} />
        <Checkbox label="Agrees to marketing messages" checked={form.marketingConsent} onChange={set("marketingConsent")} />
      </div>
    </Modal>
  );
}

export default function CustomersPage() {
  const { data: me } = useMe();
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const search = useDebouncedValue(q.trim(), 300);
  const params = { q: search || undefined, page, size: 25, sort: "fullName,asc" };
  const list = useQuery({ queryKey: ["customers", "list", params], queryFn: () => customersApi.list(params), placeholderData: keepPreviousData });

  return (
    <div>
      <PageHeader
        title="Customers"
        description="Loyalty members from the tills and the online shop — one record, one card."
        actions={
          hasPermission(me, "CUSTOMER_MANAGE") && (
            <Button onClick={() => setCreating(true)}>
              <Plus className="h-4 w-4" aria-hidden="true" /> New customer
            </Button>
          )
        }
      />
      <Card>
        <div className="border-b border-slate-200 p-4">
          <div className="relative max-w-md">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <Input
              className="pl-9"
              placeholder="Name, phone, email or card number"
              value={q}
              onChange={(e) => (setQ(e.target.value), setPage(0))}
              aria-label="Search customers"
            />
          </div>
        </div>
        {list.isError ? (
          <ErrorState error={list.error} onRetry={list.refetch} />
        ) : list.isLoading ? (
          <TableSkeleton cols={6} />
        ) : list.data.content.length === 0 ? (
          <EmptyState icon={UserSquare2} title="No customers found" />
        ) : (
          <>
            <Table>
              <thead>
                <tr>
                  <Th>Customer</Th>
                  <Th>Code</Th>
                  <Th>Phone</Th>
                  <Th>Email</Th>
                  <Th>Card</Th>
                  <Th align="right">Points</Th>
                  <Th>Status</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {list.data.content.map((c) => (
                  <tr key={c.id} className="hover:bg-slate-50">
                    <Td>
                      <Link href={`/customers/${c.id}`} className="font-medium text-primary-700 hover:underline">
                        {c.fullName}
                      </Link>
                    </Td>
                    <Td className="font-mono text-xs">{c.customerCode}</Td>
                    <Td>{c.maskedPhone || "—"}</Td>
                    <Td>{c.email || "—"}</Td>
                    <Td className="font-mono text-xs">{c.cardNumber || "—"}</Td>
                    <Td align="right">{c.pointsBalance.toLocaleString()}</Td>
                    <Td>{c.active ? <Badge color="green">Active</Badge> : <Badge>Inactive</Badge>}</Td>
                  </tr>
                ))}
              </tbody>
            </Table>
            <Pagination page={page} size={25} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPageChange={setPage} />
          </>
        )}
      </Card>
      {creating && <NewCustomerDialog onClose={() => setCreating(false)} />}
    </div>
  );
}
