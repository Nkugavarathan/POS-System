import { redirect } from "next/navigation";

/** Customers land on the online shop; staff use /login (back office and POS). */
export default function Home() {
  redirect("/shop");
}
