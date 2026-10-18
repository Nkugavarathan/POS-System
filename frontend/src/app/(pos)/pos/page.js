"use client";

import { AuthGate } from "@/features/auth/auth-gate";
import { PosTerminal } from "@/features/pos/pos-terminal";

/** Full-screen cashier terminal: no back-office chrome. */
export default function PosPage() {
  return <AuthGate anyOf={["POS_SELL"]}>{(user) => <PosTerminal user={user} />}</AuthGate>;
}
