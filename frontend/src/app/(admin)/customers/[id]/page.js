"use client";

import { use, useState } from "react";
import Link from "next/link";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, Globe, Pencil, PlusCircle } from "lucide-react";
import { Card, CardHeader, PageHeader, Pagination, Table, Td, Th } from "@/components/ui/layout";
import { Badge, ErrorState, Skeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { Checkbox, Field, Input } from "@/components/ui/form";
import { Modal } from "@/components/ui/dialog";
import { useMe } from "@/features/auth/hooks";
import { customersApi, salesApi } from "@/features/backoffice/api";
import { errorMessage } from "@/lib/api-error";
import { hasPermission } from "@/lib/permissions";
import { formatDateTime, formatMoney } from "@/lib/format";

function EditDialog({ customer, onClose }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    fullName: customer.fullName,
    phone: customer.phone || "",
    email: customer.email || "",
    marketingConsent: customer.marketingConsent,
  });
  const save = useMutation({
    mutationFn: () =>
      customersApi.update(customer.id, {
        ...form,
        phone: form.phone || null,
        email: form.email || null,
        enrolLoyalty: !customer.loyalty,
        version: customer.version,
      }),
    onSuccess: (c) => {
      queryClient.setQueryData(["customers", customer.id], c);
      toast.success("Saved");
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === "checkbox" ? e.target.checked : e.target.value });
  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title="Edit customer"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={save.isPending} onClick={() => save.mutate()}>
            Save
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <Field label="Full name" required>
          {(a) => <Input {...a} value={form.fullName} onChange={set("fullName")} />}
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Mobile">{(a) => <Input {...a} value={form.phone} onChange={set("phone")} />}</Field>
          <Field label="Email">{(a) => <Input {...a} value={form.email} onChange={set("email")} />}</Field>
        </div>
        <Checkbox label="Agrees to marketing messages" checked={form.marketingConsent} onChange={set("marketingConsent")} />
      </div>
    </Modal>
  );
}

function AdjustDialog({ customer, onClose }) {
  const queryClient = useQueryClient();
  const [points, setPoints] = useState("");
  const [reason, setReason] = useState("");
  const valid = /^-?\d{1,7}$/.test(points.trim()) && Number(points) !== 0 && reason.trim();
  const adjust = useMutation({
    mutationFn: () => customersApi.adjust(customer.id, { points: Number(points), reason: reason.trim() }),
    onSuccess: (c) => {
      queryClient.setQueryData(["customers", customer.id], c);
      queryClient.invalidateQueries({ queryKey: ["customers", customer.id, "loyalty"] });
      toast.success("Points adjusted");
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title="Adjust loyalty points"
      description={`Current balance ${customer.loyalty.pointsBalance.toLocaleString()} points. Recorded in the loyalty ledger and audit log.`}
      size="sm"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={adjust.isPending} disabled={!valid} onClick={() => adjust.mutate()}>
            Apply
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <Field label="Points (use − to deduct)" required>
          {(a) => <Input {...a} autoFocus inputMode="numeric" value={points} onChange={(e) => setPoints(e.target.value)} />}
        </Field>
        <Field label="Reason" required>
          {(a) => <Input {...a} value={reason} onChange={(e) => setReason(e.target.value)} />}
        </Field>
      </div>
    </Modal>
  );
}

export default function CustomerPage({ params }) {
  const { id } = use(params);
  const customerId = Number(id);
  const { data: me } = useMe();
  const [dialog, setDialog] = useState(null);
  const [lpage, setLpage] = useState(0);
  const customer = useQuery({ queryKey: ["customers", customerId], queryFn: () => customersApi.get(customerId) });
  const ledger = useQuery({
    queryKey: ["customers", customerId, "loyalty", lpage],
    queryFn: () => customersApi.loyalty(customerId, { page: lpage, size: 10 }),
    placeholderData: keepPreviousData,
  });
  const sales = useQuery({
    queryKey: ["sales", "customer", customerId],
    queryFn: () => salesApi.list({ customerId, size: 10, sort: "completedAt,desc" }),
  });

  if (customer.isError) return <ErrorState error={customer.error} onRetry={customer.refetch} />;
  const c = customer.data;
  if (!c) return <Skeleton className="h-80" />;

  return (
    <div>
      <Link href="/customers" className="mb-3 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Customers
      </Link>
      <PageHeader
        title={c.fullName}
        description={`${c.customerCode} · member since ${formatDateTime(c.createdAt)}`}
        actions={
          <>
            {hasPermission(me, "CUSTOMER_MANAGE") && (
              <Button variant="outline" onClick={() => setDialog("edit")}>
                <Pencil className="h-4 w-4" aria-hidden="true" /> Edit
              </Button>
            )}
            {c.loyalty && hasPermission(me, "LOYALTY_ADJUST") && (
              <Button variant="outline" onClick={() => setDialog("adjust")}>
                <PlusCircle className="h-4 w-4" aria-hidden="true" /> Adjust points
              </Button>
            )}
          </>
        }
      />
      <div className="grid gap-6 lg:grid-cols-[320px_1fr]">
        <div className="space-y-4">
          <Card className="p-5">
            <dl className="space-y-2 text-sm">
              <div>
                <dt className="text-xs text-slate-500">Mobile</dt>
                <dd>{c.phone || "—"}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">Email</dt>
                <dd>{c.email || "—"}</dd>
              </div>
              <div>
                <dt className="text-xs text-slate-500">Marketing</dt>
                <dd>{c.marketingConsent ? "Opted in" : "No"}</dd>
              </div>
              <div className="flex flex-wrap gap-1.5 pt-1">
                {c.active ? <Badge color="green">Active</Badge> : <Badge>Inactive</Badge>}
                {c.hasOnlineAccount && (
                  <Badge color="primary">
                    <Globe className="mr-1 h-3 w-3" aria-hidden="true" /> Online account
                  </Badge>
                )}
              </div>
            </dl>
          </Card>
          {c.loyalty ? (
            <Card className="bg-gradient-to-br from-primary-700 to-primary-900 p-5 text-white">
              <p className="text-xs uppercase tracking-wider text-primary-100">Loyalty card</p>
              <p className="mt-1 font-mono text-lg tracking-widest">{c.loyalty.cardNumber}</p>
              <p className="mt-4 text-3xl font-bold tabular">{c.loyalty.pointsBalance.toLocaleString()}</p>
              <p className="text-sm text-primary-100">points · worth {formatMoney(c.loyalty.pointsValue)}</p>
            </Card>
          ) : (
            <Card className="p-5 text-sm text-slate-500">No loyalty card.</Card>
          )}
        </div>
        <div className="space-y-6">
          <Card>
            <CardHeader title="Recent purchases" description="In store and online pickups" />
            {sales.data?.content?.length ? (
              <Table>
                <thead>
                  <tr>
                    <Th>Receipt</Th>
                    <Th>When</Th>
                    <Th>Channel</Th>
                    <Th align="right">Total</Th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {sales.data.content.map((s) => (
                    <tr key={s.id}>
                      <Td>
                        <Link href={`/sales/${s.id}`} className="font-mono text-primary-700 hover:underline">
                          {s.receiptNumber}
                        </Link>
                      </Td>
                      <Td>{formatDateTime(s.completedAt)}</Td>
                      <Td>{s.channel === "ONLINE" ? <Badge color="primary">Online</Badge> : <Badge>Store</Badge>}</Td>
                      <Td align="right">{formatMoney(s.grandTotal)}</Td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            ) : (
              <p className="px-5 py-6 text-sm text-slate-500">{sales.isLoading ? "Loading…" : "No purchases yet."}</p>
            )}
          </Card>
          <Card>
            <CardHeader title="Points history" />
            {ledger.data?.content?.length ? (
              <>
                <Table>
                  <thead>
                    <tr>
                      <Th>When</Th>
                      <Th>Type</Th>
                      <Th>Detail</Th>
                      <Th align="right">Points</Th>
                      <Th align="right">Balance</Th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {ledger.data.content.map((t) => (
                      <tr key={t.id}>
                        <Td className="whitespace-nowrap">{formatDateTime(t.createdAt)}</Td>
                        <Td>
                          <Badge color={t.points >= 0 ? "green" : "red"}>{t.type.replaceAll("_", " ").toLowerCase()}</Badge>
                        </Td>
                        <Td className="text-xs text-slate-500">{t.reason || `${t.referenceType ?? ""} ${t.referenceId ?? ""}`}</Td>
                        <Td align="right" className={t.points >= 0 ? "text-emerald-700" : "text-red-600"}>
                          {t.points > 0 ? "+" : ""}
                          {t.points.toLocaleString()}
                        </Td>
                        <Td align="right">{t.balanceAfter.toLocaleString()}</Td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
                <Pagination page={lpage} size={10} totalPages={ledger.data.totalPages} totalElements={ledger.data.totalElements} onPageChange={setLpage} />
              </>
            ) : (
              <p className="px-5 py-6 text-sm text-slate-500">{ledger.isLoading ? "Loading…" : "No points activity yet."}</p>
            )}
          </Card>
        </div>
      </div>
      {dialog === "edit" && <EditDialog customer={c} onClose={() => setDialog(null)} />}
      {dialog === "adjust" && <AdjustDialog customer={c} onClose={() => setDialog(null)} />}
    </div>
  );
}
