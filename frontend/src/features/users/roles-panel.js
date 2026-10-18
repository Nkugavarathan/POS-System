"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Card } from "@/components/ui/layout";
import { Badge, ErrorState, TableSkeleton } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Checkbox, Field, Input } from "@/components/ui/form";
import { errorMessage } from "@/lib/api-error";
import { userKeys, usersApi } from "./api";

function RoleEditor({ role, permissions, onClose }) {
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState(new Set(role.permissions));
  const [maxDiscount, setMaxDiscount] = useState(role.maxDiscountPercent);
  const byModule = useMemo(() => {
    const groups = {};
    for (const p of permissions) (groups[p.module] ||= []).push(p);
    return groups;
  }, [permissions]);

  const save = useMutation({
    mutationFn: () =>
      usersApi.updateRole(role.id, { permissions: [...selected], maxDiscountPercent: String(maxDiscount) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.roles });
      toast.success(`Role ${role.name} updated. Holders get the change at their next token refresh.`);
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={`Edit ${role.name}`}
      description="Permission changes apply to every user with this role."
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={() => save.mutate()} loading={save.isPending}>
            Save role
          </Button>
        </>
      }
    >
      <Field label="Maximum discount without approval (%)" className="mb-4 max-w-xs">
        {(a) => (
          <Input {...a} type="number" min="0" max="100" step="0.01" value={maxDiscount} onChange={(e) => setMaxDiscount(e.target.value)} />
        )}
      </Field>
      <div className="grid gap-5 sm:grid-cols-2">
        {Object.entries(byModule).map(([module, perms]) => (
          <fieldset key={module}>
            <legend className="text-xs font-semibold uppercase tracking-wide text-slate-500">{module}</legend>
            <div className="mt-2 space-y-1.5">
              {perms.map((p) => (
                <Checkbox
                  key={p.code}
                  label={<span title={p.description}>{p.code}</span>}
                  checked={selected.has(p.code)}
                  onChange={() => {
                    const next = new Set(selected);
                    next.has(p.code) ? next.delete(p.code) : next.add(p.code);
                    setSelected(next);
                  }}
                />
              ))}
            </div>
          </fieldset>
        ))}
      </div>
    </Modal>
  );
}

export function RolesPanel({ canManage }) {
  const [editing, setEditing] = useState(null);
  const roles = useQuery({ queryKey: userKeys.roles, queryFn: usersApi.roles });
  const permissions = useQuery({ queryKey: userKeys.permissions, queryFn: usersApi.permissions, enabled: canManage });

  if (roles.isPending) return <Card><TableSkeleton rows={5} cols={3} /></Card>;
  if (roles.error) return <Card><ErrorState error={roles.error} onRetry={roles.refetch} /></Card>;

  return (
    <div className="space-y-4">
      {roles.data.map((role) => (
        <Card key={role.id} className="p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h3 className="font-semibold text-slate-900">
                {role.name} <span className="text-xs font-normal text-slate-400">{role.code}</span>
              </h3>
              <p className="text-sm text-slate-500">{role.description}</p>
              <p className="mt-1 text-xs text-slate-500">
                Discount limit without approval: <strong>{role.maxDiscountPercent}%</strong>
              </p>
            </div>
            {canManage && (
              <Button variant="outline" size="sm" onClick={() => setEditing(role)} disabled={!permissions.data}>
                Edit permissions
              </Button>
            )}
          </div>
          <div className="mt-3 flex flex-wrap gap-1.5">
            {role.permissions.map((p) => (
              <Badge key={p} color="primary">
                {p}
              </Badge>
            ))}
          </div>
        </Card>
      ))}
      {editing && permissions.data && (
        <RoleEditor role={editing} permissions={permissions.data} onClose={() => setEditing(null)} />
      )}
    </div>
  );
}
