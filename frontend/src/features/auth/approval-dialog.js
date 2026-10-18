"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { ShieldCheck } from "lucide-react";
import { Modal } from "@/components/ui/dialog";
import { Field, Input } from "@/components/ui/form";
import { Alert } from "@/components/ui/feedback";
import { Button } from "@/components/ui/button";
import { authApi } from "./api";
import { errorMessage } from "@/lib/api-error";

/**
 * A manager approves an action on the current user's screen with username + PIN (or
 * password). The manager is NOT signed in here; the server returns a single-use token that
 * the caller sends with the action. `value` is the discount % / amount being approved.
 */
export function ApprovalDialog({ action, title, description, value, registerId, onApproved, onClose }) {
  const [username, setUsername] = useState("");
  const [secret, setSecret] = useState("");
  const approve = useMutation({
    mutationFn: () => authApi.requestApproval({ action, username: username.trim(), secret, registerId, value: value ?? undefined }),
    onSuccess: (grant) => onApproved(grant.approvalToken, grant),
  });
  return (
    <Modal
      open
      onOpenChange={(o) => !o && onClose()}
      title={title || "Manager approval"}
      description={description}
      size="sm"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button form="approval-form" type="submit" loading={approve.isPending} disabled={!username || !secret}>
            <ShieldCheck className="h-4 w-4" aria-hidden="true" /> Approve
          </Button>
        </>
      }
    >
      <form
        id="approval-form"
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          approve.mutate();
        }}
      >
        {approve.error && <Alert tone="danger">{errorMessage(approve.error)}</Alert>}
        {value != null && (
          <p className="rounded-md bg-secondary-50 px-3 py-2 text-sm text-secondary-900">
            Approving: <strong className="tabular">{value}</strong>
          </p>
        )}
        <Field label="Manager username">
          {(a) => <Input {...a} autoFocus autoComplete="off" value={username} onChange={(e) => setUsername(e.target.value)} />}
        </Field>
        <Field label="PIN or password">
          {(a) => <Input {...a} type="password" autoComplete="off" value={secret} onChange={(e) => setSecret(e.target.value)} />}
        </Field>
      </form>
    </Modal>
  );
}
