"use client";

import { use } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Pencil } from "lucide-react";
import { PageHeader, Card, CardHeader, Table, Th, Td } from "@/components/ui/layout";
import { Badge, EmptyState, ErrorState, Skeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { AuthGate } from "@/features/auth/auth-gate";
import { useMe } from "@/features/auth/hooks";
import { inventoryApi, MOVEMENT_LABELS } from "@/features/inventory/api";
import { hasPermission } from "@/lib/permissions";
import { formatDate, formatDateTime, formatMoney, formatQuantity } from "@/lib/format";

export default function InventoryDetailPage({ params }) {
  const { id } = use(params);
  const { data: me } = useMe();
  const { data, error, isPending, refetch } = useQuery({
    queryKey: ["inventory", "detail", id],
    queryFn: () => inventoryApi.detail(id),
  });
  const s = data?.stock;
  return (
    <AuthGate anyOf={["INVENTORY_VIEW"]}>
      <div className="mx-auto max-w-5xl space-y-6">
        <Link href="/inventory" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Inventory
        </Link>
        {isPending ? (
          <Skeleton className="h-80" />
        ) : error ? (
          <ErrorState error={error} onRetry={refetch} />
        ) : (
          <>
            <PageHeader
              title={s.name}
              description={`${s.sku} · ${s.categoryName}`}
              actions={
                <>
                  <Link href={`/products/${s.productId}`}>
                    <Button variant="outline">
                      <Pencil className="h-4 w-4" aria-hidden="true" /> Product
                    </Button>
                  </Link>
                  {hasPermission(me, "INVENTORY_ADJUST") && (
                    <Link href={`/inventory/adjust?productId=${s.productId}`}>
                      <Button>Adjust stock</Button>
                    </Link>
                  )}
                </>
              }
            />
            <div className="grid gap-4 sm:grid-cols-4">
              {[
                ["On hand", `${formatQuantity(s.quantityOnHand)} ${s.unitOfMeasure.toLowerCase()}`, s.lowStock ? "text-red-600" : ""],
                ["Reorder level", formatQuantity(s.reorderLevel), ""],
                ["Average cost", s.averageCost != null ? formatMoney(s.averageCost) : "-", ""],
                ["Stock value", s.stockValue != null ? formatMoney(s.stockValue) : "-", ""],
              ].map(([label, value, cls]) => (
                <Card key={label} className="p-4">
                  <p className="text-xs text-slate-500">{label}</p>
                  <p className={`mt-1 text-xl font-semibold tabular ${cls}`}>{value}</p>
                </Card>
              ))}
            </div>
            {s.trackBatches && (
              <Card>
                <CardHeader title="Batches" description="Sales use the earliest expiry first; expired batches are used last and flagged." />
                {data.batches.length === 0 ? (
                  <EmptyState title="No batches received yet" className="py-8" />
                ) : (
                  <Table>
                    <thead>
                      <tr>
                        <Th>Batch</Th>
                        <Th>Expiry</Th>
                        <Th align="right">Received</Th>
                        <Th align="right">Remaining</Th>
                        <Th align="right">Unit cost</Th>
                        <Th>Status</Th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {data.batches.map((b) => (
                        <tr key={b.id}>
                          <Td className="font-mono">{b.batchNumber}</Td>
                          <Td>
                            {b.expiryDate ? formatDate(b.expiryDate) : "-"}{" "}
                            {b.expired && <Badge color="red">expired</Badge>}
                            {!b.expired && b.daysToExpiry != null && b.daysToExpiry <= 7 && (
                              <Badge color="amber">{b.daysToExpiry} d</Badge>
                            )}
                          </Td>
                          <Td align="right">{formatQuantity(b.quantityReceived)}</Td>
                          <Td align="right" className="font-medium">{formatQuantity(b.quantityRemaining)}</Td>
                          <Td align="right">{b.unitCost != null ? formatMoney(b.unitCost) : "-"}</Td>
                          <Td>
                            <Badge color={b.status === "ACTIVE" ? "green" : "gray"}>{b.status.toLowerCase()}</Badge>
                          </Td>
                        </tr>
                      ))}
                    </tbody>
                  </Table>
                )}
              </Card>
            )}
            <Card>
              <CardHeader title="Recent movements" description="The last 20 ledger entries for this product." />
              {data.recentMovements.length === 0 ? (
                <EmptyState title="No movements yet" className="py-8" />
              ) : (
                <Table>
                  <thead>
                    <tr>
                      <Th>When</Th>
                      <Th>Type</Th>
                      <Th align="right">Change</Th>
                      <Th align="right">After</Th>
                      <Th>Reason</Th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {data.recentMovements.map((m) => (
                      <tr key={m.id}>
                        <Td className="text-xs text-slate-500">{formatDateTime(m.createdAt)}</Td>
                        <Td className="text-xs">{MOVEMENT_LABELS[m.type] ?? m.type}</Td>
                        <Td align="right" className={Number(m.quantityChange) < 0 ? "text-red-600" : "text-emerald-700"}>
                          {Number(m.quantityChange) > 0 ? "+" : ""}
                          {formatQuantity(m.quantityChange)}
                        </Td>
                        <Td align="right">{formatQuantity(m.quantityAfter)}</Td>
                        <Td className="text-xs text-slate-500">{m.reason}</Td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              )}
            </Card>
          </>
        )}
      </div>
    </AuthGate>
  );
}
