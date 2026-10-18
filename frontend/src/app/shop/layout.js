import { ShopShell } from "@/features/shop/components/shop-shell";
import { SHOP_NAME } from "@/features/shop/brand";

export const metadata = {
  title: { default: `${SHOP_NAME} · Click & collect`, template: `%s · ${SHOP_NAME}` },
  description: "Order groceries online, watch them packed, and collect in store.",
};

export default function ShopLayout({ children }) {
  return <ShopShell>{children}</ShopShell>;
}
