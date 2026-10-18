import {
  Apple,
  Baby,
  Beef,
  CakeSlice,
  Coffee,
  CupSoda,
  Droplets,
  Egg,
  Fish,
  Milk,
  Package,
  Snowflake,
  Soup,
  Sparkles,
  SprayCan,
  Wheat,
} from "lucide-react";
import { cn } from "@/lib/cn";

// Keyword → icon, so products without a photo still get a recognisable, consistent tile.
const ICONS = [
  [/milk|dairy|yog|cheese|curd/i, Milk],
  [/egg/i, Egg],
  [/bread|bun|bak|cake|biscuit/i, CakeSlice],
  [/rice|flour|grain|dhal|wheat|noodle/i, Wheat],
  [/tea|coffee/i, Coffee],
  [/drink|soda|juice|water|beverage|cola/i, CupSoda],
  [/fruit|veg|produce|banana|apple|onion|potato/i, Apple],
  [/fish|tuna|sardine|seafood/i, Fish],
  [/meat|chicken|beef|sausage/i, Beef],
  [/frozen|ice/i, Snowflake],
  [/oil|sauce|ghee|margarine|fat/i, Droplets],
  [/spice|curry|chili|salt|condiment|soup/i, Soup],
  [/soap|shampoo|tooth|care|personal|beauty/i, Sparkles],
  [/clean|detergent|household|wash/i, SprayCan],
  [/baby|diaper/i, Baby],
];

const TINTS = [
  "bg-primary-50 text-primary-700",
  "bg-secondary-50 text-secondary-700",
  "bg-emerald-50 text-emerald-700",
  "bg-sky-50 text-sky-700",
  "bg-rose-50 text-rose-700",
  "bg-violet-50 text-violet-700",
];

function iconFor(text) {
  for (const [re, Icon] of ICONS) if (re.test(text)) return Icon;
  return Package;
}

function hash(text) {
  let h = 0;
  for (let i = 0; i < text.length; i++) h = (h * 31 + text.charCodeAt(i)) | 0;
  return Math.abs(h);
}

/** Product photo, or a generated tile (icon from name/category, tint from the name). */
export function ProductArt({ name = "", category = "", imageUrl, className, iconClassName }) {
  if (imageUrl) {
    // eslint-disable-next-line @next/next/no-img-element
    return <img src={imageUrl} alt="" className={cn("h-full w-full object-cover", className)} loading="lazy" />;
  }
  const Icon = iconFor(`${name} ${category}`);
  return (
    <div className={cn("flex h-full w-full items-center justify-center", TINTS[hash(name) % TINTS.length], className)}>
      <Icon className={cn("h-10 w-10 opacity-80", iconClassName)} strokeWidth={1.5} aria-hidden="true" />
    </div>
  );
}
