"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft } from "lucide-react";
import { PageHeader } from "@/components/ui/layout";
import { purchasingApi } from "@/features/backoffice/api";
import { PoEditor } from "@/features/backoffice/po-editor";
import { errorMessage } from "@/lib/api-error";

export default function NewPurchaseOrderPage() {
  const router = useRouter();
  const create = useMutation({
    mutationFn: purchasingApi.createOrder,
    onSuccess: (po) => {
      toast.success(`Draft ${po.poNumber} saved`);
      router.push(`/purchases/${po.id}`);
    },
  });
  return (
    <div>
      <Link href="/purchases" className="mb-3 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-800">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Purchase orders
      </Link>
      <PageHeader title="New purchase order" description="Saved as a draft; submit it when it is ready to send to the supplier." />
      <PoEditor onSubmit={(body) => create.mutate(body)} saving={create.isPending} error={create.isError ? errorMessage(create.error) : null} />
    </div>
  );
}
