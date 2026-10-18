"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Boxes, ClipboardCheck, Plus, Search } from "lucide-react";
import { PageHeader, Card, Table, Th, Td, Pagination } from "@/components/ui/layout";
import { Checkbox, Field, Input, Select, Textarea } from "@/components/ui/form";
import { Alert, Badge, EmptyState, ErrorState, TableSkeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Tabs } from "@/components/ui/tabs";
import { AuthGate } from "@/features/auth/auth-gate";
import { useMe } from "@/features/auth/hooks";
import { catalogApi, catalogKeys } from "@/features/catalog/api";
import { inventoryApi, MOVEMENT_LABELS } from "@/features/inventory/api";
import { hasPermission } from "@/lib/permissions";
import { useDebouncedValue } from "@/lib/hooks";
import { errorMessage } from "@/lib/api-error";
import { formatDate, formatDateTime, formatMoney, formatQuantity } from "@/lib/format";

function StockTab({ showCost }) {
  const [q, setQ] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [lowStock, setLowStock] = useState(false);
  const [page, setPage] = useState(0);
  const dq = useDebouncedValue(q);
  const params = { q: dq || undefined, categoryId: categoryId || undefined, lowStock: lowStock || undefined, page, size: 25 };
  const { data, error, isPending, isFetching, refetch } = useQuery({
    queryKey: ["inventory", "list", params],
    queryFn: () => inventoryApi.list(params),
    placeholderData: keepPreviousData,
  });
  const categories = useQuery({ queryKey: catalogKeys.categories, queryFn: () => catalogApi.categories() });
  return (
    <Card>
      <div className="flex flex-col gap-3 border-b border-slate-200 p-4 md:flex-row md:items-center">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
          <Input className="pl-9" placeholder="Search name or SKU" aria-label="Search stock" value={q} onChange={(e) => (setQ(e.target.value), setPage(0))} />
        </div>
        <Select aria-label="Category" className="md:w-56" value={categoryId} onChange={(e) => (setCategoryId(e.target.value), setPage(0))}>
          <option value="">All categories</option>
          {categories.data?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.path}
            </option>
          ))}
        </Select>
        <Checkbox label="Low stock only" checked={lowStock} onChange={(e) => (setLowStock(e.target.checked), setPage(0))} />
      </div>
      {isPending ? (
        <TableSkeleton rows={10} cols={5} />
      ) : error ? (
        <ErrorState error={error} onRetry={refetch} />
      ) : data.content.length === 0 ? (
        <EmptyState icon={Boxes} title="Nothing to show" />
      ) : (
        <div className={isFetching ? "opacity-70" : undefined}>
          <Table>
            <thead>
              <tr>
                <Th>Product</Th>
                <Th>Category</Th>
                <Th align="right">On hand</Th>
                <Th align="right">Reorder at</Th>
                {showCost && <Th align="right">Avg cost</Th>}
                {showCost && <Th align="right">Stock value</Th>}
                <Th>Last movement</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((r) => (
                <tr key={r.productId} className="hover:bg-slate-50">
                  <Td>
                    <Link href={`/inventory/${r.productId}`} className="font-medium text-primary-700 hover:underline">
                      {r.name}
                    </Link>
                    <p className="font-mono text-xs text-slate-500">
                      {r.sku} {r.trackBatches && <Badge className="ml-1">batches</Badge>}
                    </p>
                  </Td>
                  <Td className="text-slate-600">{r.categoryName}</Td>
                  <Td align="right" className={r.lowStock ? "font-semibold text-red-600" : "font-medium"}>
                    {formatQuantity(r.quantityOnHand)} <span className="text-xs font-normal text-slate-400">{r.unitOfMeasure.toLowerCase()}</span>
                  </Td>
                  <Td align="right" className="text-slate-500">{formatQuantity(r.reorderLevel)}</Td>
                  {showCost && <Td align="right">{formatMoney(r.averageCost)}</Td>}
                  {showCost && <Td align="right">{formatMoney(r.stockValue)}</Td>}
                  <Td className="text-xs text-slate-500">{r.lastMovementAt ? formatDateTime(r.lastMovementAt) : "-"}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
          <Pagination {...data} onPageChange={setPage} />
        </div>
      )}
    </Card>
  );
}

function MovementsTab() {
  const [type, setType] = useState("");
  const [days, setDays] = useState("7");
  const [page, setPage] = useState(0);
  const from = new Date(Date.now() - Number(days) * 864e5).toISOString();
  const params = { type: type || undefined, from, page, size: 50 };
  const { data, error, isPending, refetch } = useQuery({
    queryKey: ["inventory", "movements", { type, days, page }],
    queryFn: () => inventoryApi.movements(params),
    placeholderData: keepPreviousData,
  });
  return (
    <Card>
      <div className="flex flex-wrap gap-3 border-b border-slate-200 p-4">
        <Select aria-label="Movement type" className="w-56" value={type} onChange={(e) => (setType(e.target.value), setPage(0))}>
          <option value="">All movement types</option>
          {Object.entries(MOVEMENT_LABELS)
            .filter(([k]) => !k.startsWith("TRANSFER"))
            .map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
        </Select>
        <Select aria-label="Period" className="w-40" value={days} onChange={(e) => (setDays(e.target.value), setPage(0))}>
          <option value="1">Last 24 hours</option>
          <option value="7">Last 7 days</option>
          <option value="30">Last 30 days</option>
          <option value="90">Last 90 days</option>
        </Select>
      </div>
      {isPending ? (
        <TableSkeleton rows={10} cols={6} />
      ) : error ? (
        <ErrorState error={error} onRetry={refetch} />
      ) : data.content.length === 0 ? (
        <EmptyState title="No movements in this period" />
      ) : (
        <>
          <Table>
            <thead>
              <tr>
                <Th>When</Th>
                <Th>Product</Th>
                <Th>Type</Th>
                <Th align="right">Change</Th>
                <Th align="right">Before → after</Th>
                <Th>Reference</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((m) => (
                <tr key={m.id}>
                  <Td className="whitespace-nowrap text-xs text-slate-500">{formatDateTime(m.createdAt)}</Td>
                  <Td>
                    <Link href={`/inventory/${m.productId}`} className="text-primary-700 hover:underline">
                      {m.productName}
                    </Link>
                  </Td>
                  <Td className="text-xs">{MOVEMENT_LABELS[m.type] ?? m.type}</Td>
                  <Td align="right" className={Number(m.quantityChange) < 0 ? "text-red-600" : "text-emerald-700"}>
                    {Number(m.quantityChange) > 0 ? "+" : ""}
                    {formatQuantity(m.quantityChange)}
                  </Td>
                  <Td align="right" className="text-slate-500">
                    {formatQuantity(m.quantityBefore)} → {formatQuantity(m.quantityAfter)}
                  </Td>
                  <Td className="text-xs text-slate-500">
                    {m.referenceType.replaceAll("_", " ").toLowerCase()} {m.referenceId ? `#${m.referenceId}` : ""}
                    {m.reason && <span className="block text-slate-400">{m.reason}</span>}
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
          <Pagination {...data} onPageChange={setPage} />
        </>
      )}
    </Card>
  );
}

function AdjustmentsTab({ canAdjust }) {
  const [page, setPage] = useState(0);
  const { data, error, isPending, refetch } = useQuery({
    queryKey: ["inventory", "adjustments", page],
    queryFn: () => inventoryApi.adjustments({ page, size: 25 }),
  });
  return (
    <Card>
      <div className="flex justify-end border-b border-slate-200 p-4">
        {canAdjust && (
          <Link href="/inventory/adjust">
            <Button>
              <Plus className="h-4 w-4" aria-hidden="true" /> New adjustment
            </Button>
          </Link>
        )}
      </div>
      {isPending ? (
        <TableSkeleton rows={6} cols={5} />
      ) : error ? (
        <ErrorState error={error} onRetry={refetch} />
      ) : data.content.length === 0 ? (
        <EmptyState title="No adjustments yet" description="Opening balances, corrections and write-offs appear here." />
      ) : (
        <>
          <Table>
            <thead>
              <tr>
                <Th>Number</Th>
                <Th>Type</Th>
                <Th>Reason</Th>
                <Th align="right">Value</Th>
                <Th>Date</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((a) => (
                <tr key={a.id}>
                  <Td className="font-mono text-xs">{a.adjustmentNumber}</Td>
                  <Td>
                    <Badge color={a.type.startsWith("WRITE_OFF") ? "red" : "gray"}>{a.type.replaceAll("_", " ").toLowerCase()}</Badge>
                    {a.approvedBy && <Badge color="amber" className="ml-1">approved</Badge>}
                  </Td>
                  <Td className="text-slate-600">{a.reason}</Td>
                  <Td align="right">{formatMoney(a.totalValue)}</Td>
                  <Td className="text-xs text-slate-500">{formatDateTime(a.createdAt)}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
          <Pagination {...data} onPageChange={setPage} />
        </>
      )}
    </Card>
  );
}

function NewCountDialog({ onClose }) {
  const router = useRouter();
  const [categoryId, setCategoryId] = useState("");
  const [notes, setNotes] = useState("");
  const categories = useQuery({ queryKey: catalogKeys.categories, queryFn: () => catalogApi.categories({ active: true }) });
  const create = useMutation({
    mutationFn: () => inventoryApi.createCount({ categoryId: categoryId ? Number(categoryId) : null, notes }),
    onSuccess: (c) => {
      toast.success(`Count ${c.countNumber} started with ${c.itemCount} products.`);
      router.push(`/inventory/counts/${c.id}`);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title="Start a stock count"
      description="Current system quantities are recorded now; variances are applied against live stock when the count is posted."
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={() => create.mutate()} loading={create.isPending}>
            Start count
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Field label="Scope">
          {(a) => (
            <Select {...a} value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
              <option value="">Whole store</option>
              {categories.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.path}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Notes">{(a) => <Textarea {...a} rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />}</Field>
      </div>
    </Modal>
  );
}

const COUNT_COLOR = { OPEN: "blue", SUBMITTED: "amber", POSTED: "green", CANCELLED: "gray" };

function CountsTab({ canCount }) {
  const [open, setOpen] = useState(false);
  const [page, setPage] = useState(0);
  const { data, error, isPending, refetch } = useQuery({
    queryKey: ["inventory", "counts", page],
    queryFn: () => inventoryApi.counts({ page, size: 25 }),
  });
  return (
    <Card>
      <div className="flex justify-end border-b border-slate-200 p-4">
        {canCount && (
          <Button onClick={() => setOpen(true)}>
            <ClipboardCheck className="h-4 w-4" aria-hidden="true" /> Start count
          </Button>
        )}
      </div>
      {isPending ? (
        <TableSkeleton rows={5} cols={4} />
      ) : error ? (
        <ErrorState error={error} onRetry={refetch} />
      ) : data.content.length === 0 ? (
        <EmptyState title="No stock counts yet" />
      ) : (
        <>
          <Table>
            <thead>
              <tr>
                <Th>Count</Th>
                <Th>Status</Th>
                <Th>Started</Th>
                <Th>Posted</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((c) => (
                <tr key={c.id}>
                  <Td>
                    <Link href={`/inventory/counts/${c.id}`} className="font-mono text-primary-700 hover:underline">
                      {c.countNumber}
                    </Link>
                    {c.notes && <span className="block text-xs text-slate-500">{c.notes}</span>}
                  </Td>
                  <Td>
                    <Badge color={COUNT_COLOR[c.status]}>{c.status.toLowerCase()}</Badge>
                  </Td>
                  <Td className="text-xs text-slate-500">{formatDateTime(c.createdAt)}</Td>
                  <Td className="text-xs text-slate-500">{c.postedAt ? formatDateTime(c.postedAt) : "-"}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
          <Pagination {...data} onPageChange={setPage} />
        </>
      )}
      {open && <NewCountDialog onClose={() => setOpen(false)} />}
    </Card>
  );
}

function AlertsTab() {
  const queryClient = useQueryClient();
  const alerts = useQuery({ queryKey: ["inventory", "alerts"], queryFn: () => inventoryApi.alerts({ size: 100 }) });
  const expiring = useQuery({ queryKey: ["inventory", "expiring"], queryFn: () => inventoryApi.expiring(30) });
  const ack = useMutation({
    mutationFn: inventoryApi.acknowledge,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["inventory", "alerts"] }),
    onError: (e) => toast.error(errorMessage(e)),
  });
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card>
        <div className="border-b border-slate-200 px-5 py-4">
          <h2 className="text-sm font-semibold">Open alerts</h2>
        </div>
        {alerts.isPending ? (
          <TableSkeleton rows={4} cols={2} />
        ) : alerts.data?.content.length === 0 ? (
          <EmptyState title="No open alerts" />
        ) : (
          <ul className="divide-y divide-slate-100">
            {alerts.data?.content.map((a) => (
              <li key={a.id} className="flex items-start justify-between gap-3 px-5 py-3">
                <div>
                  <Badge color={a.type === "LOW_STOCK" ? "amber" : "red"}>{a.type.replace("_", " ").toLowerCase()}</Badge>
                  <p className="mt-1 text-sm text-slate-700">{a.message}</p>
                  <p className="text-xs text-slate-400">{formatDateTime(a.createdAt)}</p>
                </div>
                <Button size="sm" variant="ghost" onClick={() => ack.mutate(a.id)}>
                  Dismiss
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Card>
      <Card>
        <div className="border-b border-slate-200 px-5 py-4">
          <h2 className="text-sm font-semibold">Batches expiring within 30 days</h2>
        </div>
        {expiring.isPending ? (
          <TableSkeleton rows={4} cols={3} />
        ) : expiring.data?.length === 0 ? (
          <EmptyState title="Nothing expiring soon" />
        ) : (
          <Table>
            <thead>
              <tr>
                <Th>Product / batch</Th>
                <Th>Expiry</Th>
                <Th align="right">Qty</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {expiring.data?.map((b) => (
                <tr key={b.batchId}>
                  <Td>
                    <Link href={`/inventory/${b.productId}`} className="text-primary-700 hover:underline">
                      {b.productName}
                    </Link>
                    <span className="block font-mono text-xs text-slate-500">{b.batchNumber}</span>
                  </Td>
                  <Td>
                    {formatDate(b.expiryDate)}{" "}
                    <Badge color={b.daysToExpiry < 0 ? "red" : b.daysToExpiry <= 3 ? "amber" : "gray"}>
                      {b.daysToExpiry < 0 ? "expired" : `${b.daysToExpiry} d`}
                    </Badge>
                  </Td>
                  <Td align="right">{formatQuantity(b.quantityRemaining)}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
    </div>
  );
}

function InventoryContent() {
  const params = useSearchParams();
  const router = useRouter();
  const { data: me } = useMe();
  const tab = params.get("tab") || "stock";
  const setTab = (t) => router.replace(`/inventory?tab=${t}`);
  return (
    <>
      <PageHeader
        title="Inventory"
        description="Every change is a ledger movement; stock is never edited directly."
        actions={
          hasPermission(me, "INVENTORY_ADJUST") && (
            <Link href="/inventory/adjust">
              <Button variant="outline">
                <Plus className="h-4 w-4" aria-hidden="true" /> Adjust stock
              </Button>
            </Link>
          )
        }
      />
      <Tabs
        value={tab}
        onChange={setTab}
        tabs={[
          { id: "stock", label: "Stock levels" },
          { id: "movements", label: "Movements" },
          { id: "adjustments", label: "Adjustments" },
          { id: "counts", label: "Stock counts" },
          { id: "alerts", label: "Alerts & expiry" },
        ]}
      />
      {tab === "stock" && <StockTab showCost={hasPermission(me, "PRODUCT_VIEW_COST")} />}
      {tab === "movements" && <MovementsTab />}
      {tab === "adjustments" && <AdjustmentsTab canAdjust={hasPermission(me, "INVENTORY_ADJUST") || hasPermission(me, "INVENTORY_WRITE_OFF")} />}
      {tab === "counts" && <CountsTab canCount={hasPermission(me, "INVENTORY_COUNT")} />}
      {tab === "alerts" && <AlertsTab />}
      {tab === "stock" && !hasPermission(me, "PRODUCT_VIEW_COST") && (
        <Alert tone="info" className="mt-4">
          Cost and valuation columns are visible to users with cost permission.
        </Alert>
      )}
    </>
  );
}

export default function InventoryPage() {
  return (
    <AuthGate anyOf={["INVENTORY_VIEW"]}>
      <Suspense>
        <InventoryContent />
      </Suspense>
    </AuthGate>
  );
}
