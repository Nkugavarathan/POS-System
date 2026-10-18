"use client";

import { use, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, Search } from "lucide-react";
import { PageHeader, Card, Table, Th, Td } from "@/components/ui/layout";
import { Alert, Badge, ErrorState, Skeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/dialog";
import { Input } from "@/components/ui/form";
import { AuthGate } from "@/features/auth/auth-gate";
import { useMe } from "@/features/auth/hooks";
import { inventoryApi } from "@/features/inventory/api";
import { errorMessage } from "@/lib/api-error";
import { hasPermission } from "@/lib/permissions";
import { formatDateTime, formatQuantity } from "@/lib/format";

export default function CountPage({ params }) {
  const { id } = use(params);
  const queryClient = useQueryClient();
  const { data: me } = useMe();
  const [draft, setDraft] = useState({});
  const [filter, setFilter] = useState("");
  const [confirm, setConfirm] = useState(null);
  const { data: count, error, isPending, refetch } = useQuery({
    queryKey: ["inventory", "count", id],
    queryFn: () => inventoryApi.count(id),
  });
  useEffect(() => setDraft({}), [count?.version]);
  const onSaved = (c) => {
    queryClient.setQueryData(["inventory", "count", id], c);
    queryClient.invalidateQueries({ queryKey: ["inventory", "counts"] });
  };

  const save = useMutation({
    mutationFn: () =>
      inventoryApi.recordCount(id, {
        version: count.version,
        entries: Object.entries(draft).map(([productId, v]) => ({
          productId: Number(productId),
          countedQuantity: v === "" ? null : v,
        })),
      }),
    onSuccess: (c) => {
      onSaved(c);
      toast.success("Counts saved.");
    },
    onError: (e) => {
      if (e.code === "CONCURRENT_MODIFICATION") {
        toast.error("Someone else saved this count. Reloaded; re-enter your changes.");
        refetch();
      } else toast.error(errorMessage(e));
    },
  });
  const action = useMutation({
    mutationFn: (a) => inventoryApi.countAction(id, a),
    onSuccess: (c, a) => {
      onSaved(c);
      setConfirm(null);
      queryClient.invalidateQueries({ queryKey: ["inventory"] });
      toast.success(a === "post" ? "Count posted to the ledger." : `Count ${c.status.toLowerCase()}.`);
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const items = useMemo(() => {
    const f = filter.trim().toLowerCase();
    return (count?.items || []).filter((i) => !f || i.name.toLowerCase().includes(f) || i.sku.toLowerCase().includes(f));
  }, [count, filter]);
  const editable = count?.status === "OPEN" && hasPermission(me, "INVENTORY_COUNT");
  const dirty = Object.keys(draft).length > 0;

  return (
    <AuthGate anyOf={["INVENTORY_VIEW"]}>
      <div className="mx-auto max-w-5xl space-y-6">
        <Link href="/inventory?tab=counts" className="inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Stock counts
        </Link>
        {isPending ? (
          <Skeleton className="h-96" />
        ) : error ? (
          <ErrorState error={error} onRetry={refetch} />
        ) : (
          <>
            <PageHeader
              title={`Count ${count.countNumber}`}
              description={
                <span className="flex flex-wrap items-center gap-2">
                  <Badge color={count.status === "POSTED" ? "green" : count.status === "SUBMITTED" ? "amber" : "blue"}>
                    {count.status.toLowerCase()}
                  </Badge>
                  {count.countedCount} of {count.itemCount} counted · started {formatDateTime(count.createdAt)}
                </span>
              }
              actions={
                <>
                  {editable && (
                    <>
                      <Button variant="outline" onClick={() => save.mutate()} disabled={!dirty} loading={save.isPending}>
                        Save counts
                      </Button>
                      <Button onClick={() => setConfirm("submit")} disabled={dirty}>
                        Submit for posting
                      </Button>
                    </>
                  )}
                  {count.status === "SUBMITTED" && hasPermission(me, "INVENTORY_COUNT_POST") && (
                    <>
                      <Button variant="outline" onClick={() => action.mutate("reopen")}>
                        Reopen
                      </Button>
                      <Button onClick={() => setConfirm("post")}>Post variances</Button>
                    </>
                  )}
                  {["OPEN", "SUBMITTED"].includes(count.status) && hasPermission(me, "INVENTORY_COUNT") && (
                    <Button variant="ghost" onClick={() => setConfirm("cancel")}>
                      Cancel count
                    </Button>
                  )}
                </>
              }
            />
            {dirty && <Alert tone="warning">You have unsaved counts. Save before submitting.</Alert>}
            <Card>
              <div className="border-b border-slate-200 p-4">
                <div className="relative max-w-sm">
                  <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                  <Input className="pl-9" placeholder="Filter products" aria-label="Filter products" value={filter} onChange={(e) => setFilter(e.target.value)} />
                </div>
              </div>
              <Table>
                <thead>
                  <tr>
                    <Th>Product</Th>
                    <Th align="right">System at start</Th>
                    <Th>Counted</Th>
                    {count.status === "POSTED" && <Th align="right">Live at post</Th>}
                    {count.status === "POSTED" && <Th align="right">Variance</Th>}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {items.map((i) => {
                    const value = draft[i.productId] ?? (i.countedQuantity == null ? "" : formatQuantity(i.countedQuantity));
                    return (
                      <tr key={i.id}>
                        <Td>
                          <span className="font-medium">{i.name}</span>
                          <span className="block font-mono text-xs text-slate-500">{i.sku}</span>
                        </Td>
                        <Td align="right">{formatQuantity(i.systemQuantity)}</Td>
                        <Td>
                          {editable ? (
                            <Input
                              aria-label={`Counted quantity for ${i.name}`}
                              className="w-28"
                              inputMode="decimal"
                              value={value}
                              onChange={(e) => setDraft({ ...draft, [i.productId]: e.target.value })}
                            />
                          ) : (
                            <span>{i.countedQuantity == null ? "—" : formatQuantity(i.countedQuantity)}</span>
                          )}
                        </Td>
                        {count.status === "POSTED" && <Td align="right">{formatQuantity(i.quantityAtPost)}</Td>}
                        {count.status === "POSTED" && (
                          <Td align="right" className={Number(i.variance) < 0 ? "text-red-600" : Number(i.variance) > 0 ? "text-emerald-700" : ""}>
                            {i.variance == null ? "—" : `${Number(i.variance) > 0 ? "+" : ""}${formatQuantity(i.variance)}`}
                          </Td>
                        )}
                      </tr>
                    );
                  })}
                </tbody>
              </Table>
            </Card>
            <ConfirmDialog
              open={Boolean(confirm)}
              onOpenChange={(o) => !o && setConfirm(null)}
              title={{ submit: "Submit this count?", post: "Post variances to stock?", cancel: "Cancel this count?" }[confirm]}
              description={
                {
                  submit: "A manager reviews and posts it. Uncounted products are left unchanged.",
                  post: "Each counted product is corrected to the counted quantity against live stock. This creates ledger movements and cannot be undone.",
                  cancel: "No stock changes are made.",
                }[confirm]
              }
              confirmLabel={{ submit: "Submit", post: "Post variances", cancel: "Cancel count" }[confirm]}
              tone={confirm === "cancel" ? "danger" : "primary"}
              loading={action.isPending}
              onConfirm={() => action.mutate(confirm)}
            />
          </>
        )}
      </div>
    </AuthGate>
  );
}
