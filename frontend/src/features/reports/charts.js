"use client";

import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatMoney } from "@/lib/format";

// Charts need numbers; money stays a decimal string everywhere else (tables, totals).
const num = (v) => Number(v ?? 0);
const short = (v) => (v >= 1_000_000 ? `${(v / 1_000_000).toFixed(1)}M` : v >= 1000 ? `${Math.round(v / 1000)}k` : String(v));

function MoneyTooltip({ active, payload, label, labelFormatter }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-md border border-slate-200 bg-white px-3 py-2 text-xs shadow">
      <p className="font-semibold text-slate-800">{labelFormatter ? labelFormatter(label) : label}</p>
      {payload.map((p) => (
        <p key={p.dataKey} className="text-slate-600">
          {p.name}: <span className="font-semibold tabular">{p.payload[`${p.dataKey}Text`] ?? formatMoney(String(p.value))}</span>
        </p>
      ))}
    </div>
  );
}

/** Revenue per day (bars) with refunds; `rows` = DailySalesRow[]. */
export function DailyRevenueChart({ rows, height = 240 }) {
  const data = rows.map((r) => ({
    day: r.day,
    revenue: num(r.grossSales),
    revenueText: formatMoney(r.grossSales),
    refunds: num(r.refunds),
    refundsText: formatMoney(r.refunds),
  }));
  const label = (d) => new Date(`${d}T00:00:00`).toLocaleDateString("en-LK", { day: "numeric", month: "short" });
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
        <XAxis dataKey="day" tickFormatter={label} tick={{ fontSize: 11, fill: "#64748b" }} tickLine={false} axisLine={false} />
        <YAxis tickFormatter={short} tick={{ fontSize: 11, fill: "#64748b" }} tickLine={false} axisLine={false} width={44} />
        <Tooltip content={<MoneyTooltip labelFormatter={label} />} cursor={{ fill: "rgba(148,163,184,0.12)" }} />
        <Bar dataKey="revenue" name="Sales" fill="var(--primary)" radius={[4, 4, 0, 0]} />
        <Bar dataKey="refunds" name="Refunds" fill="var(--secondary)" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}

/** Sales by hour of day; `rows` = HourRow[]. */
export function HourlyChart({ rows, height = 200 }) {
  const data = rows.map((r) => ({ hour: r.hour, amount: num(r.amount), amountText: formatMoney(r.amount), count: r.count }));
  const label = (h) => `${String(h).padStart(2, "0")}:00`;
  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
        <XAxis dataKey="hour" tickFormatter={label} interval={2} tick={{ fontSize: 11, fill: "#64748b" }} tickLine={false} axisLine={false} />
        <YAxis tickFormatter={short} tick={{ fontSize: 11, fill: "#64748b" }} tickLine={false} axisLine={false} width={44} />
        <Tooltip content={<MoneyTooltip labelFormatter={label} />} />
        <Line type="monotone" dataKey="amount" name="Sales" stroke="var(--primary)" strokeWidth={2.5} dot={false} />
      </LineChart>
    </ResponsiveContainer>
  );
}
