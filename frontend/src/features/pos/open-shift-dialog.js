"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Modal } from "@/components/ui/dialog";
import { Field, Input, Select } from "@/components/ui/form";
import { Alert } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { useSessionStore } from "@/stores/session-store";
import { parseAmount } from "@/lib/money";
import { errorMessage } from "@/lib/api-error";
import { posApi, posKeys } from "./api";

export function OpenShiftDialog({ storeId }) {
  const queryClient = useQueryClient();
  const remembered = useSessionStore((s) => s.registerId);
  const setRegister = useSessionStore((s) => s.setRegister);
  const registers = useQuery({ queryKey: ["registers", storeId], queryFn: () => posApi.registers(storeId), enabled: Boolean(storeId) });
  const active = (registers.data || []).filter((r) => r.active);
  const [registerId, setRegisterId] = useState(remembered ? String(remembered) : "");
  const [float, setFloat] = useState("5000.00");
  const amount = parseAmount(float);
  const effectiveRegister = registerId || (active.length === 1 ? String(active[0].id) : "");

  const open = useMutation({
    mutationFn: () => posApi.openShift({ registerId: Number(effectiveRegister), openingFloat: amount }),
    onSuccess: (shift) => {
      setRegister(shift.registerId);
      queryClient.setQueryData(posKeys.shift, shift);
      toast.success(`Shift ${shift.shiftNumber} opened.`);
    },
  });

  return (
    <Modal open dismissible={false} onOpenChange={() => {}} title="Open your shift" description="Count the cash float in the drawer before you start selling." size="sm"
      footer={
        <Button onClick={() => open.mutate()} loading={open.isPending} disabled={!effectiveRegister || amount === null} size="lg" className="w-full">
          Open shift
        </Button>
      }
    >
      <div className="space-y-4">
        {open.error && <Alert tone="danger">{errorMessage(open.error)}</Alert>}
        <Field label="Register">
          {(a) => (
            <Select {...a} value={effectiveRegister} onChange={(e) => setRegisterId(e.target.value)}>
              <option value="">Choose a register…</option>
              {active.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.code} · {r.name}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Opening float" hint="Cash counted in the drawer now" error={amount === null ? "Enter an amount, e.g. 5000.00" : null}>
          {(a) => <Input {...a} inputMode="decimal" className="text-lg" value={float} onChange={(e) => setFloat(e.target.value)} />}
        </Field>
      </div>
    </Modal>
  );
}
