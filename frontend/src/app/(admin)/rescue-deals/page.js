"use client";

import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Leaf, Play, Settings } from "lucide-react";
import { Card, CardHeader, PageHeader, Table, Td, Th } from "@/components/ui/layout";
import { Badge, EmptyState, ErrorState, TableSkeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { useMe } from "@/features/auth/hooks";
import { fulfilmentApi, fulfilmentKeys } from "@/features/online-orders/api";
import { errorMessage } from "@/lib/api-error";
import { hasPermission } from "@/lib/permissions";
import { formatDate, formatDateTime, formatMoney, formatQuantity } from "@/lib/format";

export default function RescueDealsPage() {
  const { data: me } = useMe();
  const queryClient = useQueryClient();
  const deals = useQuery({ queryKey: fulfilmentKeys.rescue, queryFn: fulfilmentApi.rescueDeals, refetchInterval: 60_000 });
  const run = useMutation({
    mutationFn: fulfilmentApi.runRescue,
    onSuccess: (r) => {
      toast.success(`Rescue run finished: ${r.created} new deal${r.created === 1 ? "" : "s"}, ${r.ended} ended.`);
      queryClient.invalidateQueries({ queryKey: fulfilmentKeys.rescue });
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const canRun = hasPermission(me, "RESCUE_DEAL_MANAGE");

  return (
    <div>
      <PageHeader
        title="Rescue deals"
        description="Batches close to their expiry date are marked down automatically every hour — on the till and in the online shop, for this store only."
        actions={
          <>
            <Link href="/settings" className="inline-flex h-10 items-center gap-2 rounded-md border border-slate-300 bg-white px-4 text-sm font-medium text-slate-800 hover:bg-slate-50">
              <Settings className="h-4 w-4" aria-hidden="true" /> Rules
            </Link>
            {canRun && (
              <Button loading={run.isPending} onClick={() => run.mutate()}>
                <Play className="h-4 w-4" aria-hidden="true" /> Run now
              </Button>
            )}
          </>
        }
      />
      <div className="mb-4 grid gap-3 sm:grid-cols-3">
        {[
          ["When", "A batch enters the window set by rescue.daysBeforeExpiry (default 3 days)."],
          ["How much", "rescue.discountPercent off (default 30%); rescue.lastDayPercent on the final day (default 50%)."],
          ["Ends", "When the batch sells out or expires — the deal stops by itself."],
        ].map(([t, b]) => (
          <Card key={t} className="p-4">
            <p className="text-sm font-semibold text-slate-900">{t}</p>
            <p className="mt-1 text-xs text-slate-500">{b}</p>
          </Card>
        ))}
      </div>
      <Card>
        <CardHeader title="Running now" description="What customers see under “Rescue deals” and what the POS charges." />
        {deals.isError ? (
          <ErrorState error={deals.error} onRetry={deals.refetch} />
        ) : deals.isLoading ? (
          <TableSkeleton cols={7} />
        ) : deals.data.length === 0 ? (
          <EmptyState icon={Leaf} title="No rescue deals running" description="Nothing in stock is close to its date right now." />
        ) : (
          <Table>
            <thead>
              <tr>
                <Th>Product</Th>
                <Th align="right">Usual</Th>
                <Th align="right">Rescue price</Th>
                <Th align="right">Off</Th>
                <Th>Expires</Th>
                <Th align="right">Left in batch</Th>
                <Th>Deal ends</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {deals.data.map((d) => (
                <tr key={d.productId}>
                  <Td>
                    <Link href={`/products/${d.productId}`} className="font-medium text-slate-900 hover:text-primary-700">
                      {d.name}
                    </Link>
                    {d.brand && <p className="text-xs text-slate-500">{d.brand}</p>}
                  </Td>
                  <Td align="right">{formatMoney(d.price)}</Td>
                  <Td align="right" className="font-semibold text-red-600">
                    {formatMoney(d.offerPrice)}
                  </Td>
                  <Td align="right">{formatQuantity(d.percentOff)}%</Td>
                  <Td>
                    {formatDate(d.expiryDate)}{" "}
                    <Badge color={d.daysLeft <= 1 ? "red" : "amber"}>{d.daysLeft <= 0 ? "today" : `${d.daysLeft}d`}</Badge>
                  </Td>
                  <Td align="right">{formatQuantity(d.quantityLeft)}</Td>
                  <Td className="text-xs text-slate-500">{formatDateTime(d.endsAt)}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
    </div>
  );
}
