import { PackageCheck, Repeat, ShieldCheck } from "lucide-react";
import { ShopLogo } from "./shop-shell";

/** Only same-site shop paths are allowed as a post-login destination (no open redirects). */
export function safeNext(value) {
  return value && /^\/shop(\/[\w\-/?=&.%]*)?$/.test(value) && !value.startsWith("//") ? value : "/shop";
}

export function AuthCard({ title, subtitle, children, footer }) {
  return (
    <div className="mx-auto grid max-w-5xl items-stretch gap-0 px-4 py-10 md:grid-cols-2">
      <div className="hidden rounded-l-3xl bg-gradient-to-br from-primary-800 to-primary-600 p-8 text-white md:flex md:flex-col">
        <p className="text-sm font-semibold text-primary-100">Why an account?</p>
        <ul className="mt-6 space-y-5">
          {[
            { icon: PackageCheck, t: "Live packing tracker", b: "See each item packed and get a pickup code." },
            { icon: ShieldCheck, t: "Swaps on your terms", b: "Approve or refuse substitutes - never at a higher price." },
            { icon: Repeat, t: "One loyalty card", b: "Link your in-store card: points and till receipts in one place." },
          ].map((x) => (
            <li key={x.t} className="flex gap-3">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white/10">
                <x.icon className="h-5 w-5 text-secondary" aria-hidden="true" />
              </span>
              <span>
                <span className="block font-semibold">{x.t}</span>
                <span className="text-sm text-primary-100">{x.b}</span>
              </span>
            </li>
          ))}
        </ul>
      </div>
      <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8 md:rounded-l-none">
        <ShopLogo />
        <h1 className="mt-6 text-2xl font-extrabold text-slate-900">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
        <div className="mt-6">{children}</div>
        {footer && <div className="mt-6 border-t border-slate-100 pt-4 text-sm text-slate-600">{footer}</div>}
      </div>
    </div>
  );
}
