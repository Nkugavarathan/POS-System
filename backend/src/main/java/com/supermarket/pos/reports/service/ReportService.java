package com.supermarket.pos.reports.service;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.common.money.MoneyPolicy;
import com.supermarket.pos.common.security.CurrentUser;
import com.supermarket.pos.common.security.CurrentUserProvider;
import com.supermarket.pos.reports.dto.ReportDtos.CategoryRow;
import com.supermarket.pos.reports.dto.ReportDtos.ChannelRow;
import com.supermarket.pos.reports.dto.ReportDtos.DailySalesRow;
import com.supermarket.pos.reports.dto.ReportDtos.DashboardDto;
import com.supermarket.pos.reports.dto.ReportDtos.ExpiringRow;
import com.supermarket.pos.reports.dto.ReportDtos.HourRow;
import com.supermarket.pos.reports.dto.ReportDtos.LowStockRow;
import com.supermarket.pos.reports.dto.ReportDtos.SalesSummaryDto;
import com.supermarket.pos.reports.dto.ReportDtos.StockValueRow;
import com.supermarket.pos.reports.dto.ReportDtos.TenderRow;
import com.supermarket.pos.reports.dto.ReportDtos.TopProductRow;
import com.supermarket.pos.store.service.StoreService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only reporting over the transactional tables, always scoped to the caller's active store.
 * Days are the store's local calendar days: instants are shifted by the store's UTC offset in SQL
 * (portable; no database time-zone tables needed). For zones with daylight saving the offset at the
 * start of the range is used, which can misplace sales in the hour around a clock change.
 */
@Service
public class ReportService {

    private static final int MAX_DAYS = 366;

    private final JdbcTemplate jdbc;
    private final CurrentUserProvider currentUser;
    private final StoreService stores;
    private final Clock clock;

    public ReportService(JdbcTemplate jdbc, CurrentUserProvider currentUser, StoreService stores, Clock clock) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.stores = stores;
        this.clock = clock;
    }

    /** Resolved period: [start, end) as instants, plus the store's offset in minutes. */
    record Period(Long storeId, LocalDate from, LocalDate to, Timestamp start, Timestamp end, int offsetMinutes, ZoneId zone) {
    }

    Period period(LocalDate from, LocalDate to) {
        CurrentUser user = currentUser.require();
        Long storeId = user.requireActiveStore();
        ZoneId zone = ZoneId.of(stores.requireStore(storeId).getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate t = to == null ? today : to;
        LocalDate f = from == null ? t.minusDays(6) : from;
        if (f.isAfter(t)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The start date must be on or before the end date.");
        }
        if (ChronoUnit.DAYS.between(f, t) >= MAX_DAYS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose a period of at most " + MAX_DAYS + " days.");
        }
        Instant start = f.atStartOfDay(zone).toInstant();
        Instant end = t.plusDays(1).atStartOfDay(zone).toInstant();
        int offset = zone.getRules().getOffset(start).getTotalSeconds() / 60;
        return new Period(storeId, f, t, Timestamp.from(start), Timestamp.from(end), offset, zone);
    }

    private boolean canSeeCost() {
        return currentUser.require().hasAny("REPORT_FINANCIAL", "PRODUCT_VIEW_COST");
    }

    private static BigDecimal money(Object v) {
        return v == null ? MoneyPolicy.amount(BigDecimal.ZERO) : MoneyPolicy.amount((BigDecimal) v);
    }

    // ----- sales ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public SalesSummaryDto salesSummary(LocalDate from, LocalDate to) {
        Period p = period(from, to);
        boolean cost = canSeeCost();
        Map<LocalDate, Object[]> sales = new TreeMap<>();
        jdbc.query("""
                SELECT DATE(DATE_ADD(completed_at, INTERVAL ? MINUTE)) d, COUNT(*) n, SUM(grand_total) g,
                       SUM(discount_total) disc, SUM(tax_total) tax, SUM(cost_total) cost
                FROM sales WHERE store_id = ? AND completed_at >= ? AND completed_at < ?
                GROUP BY d""", rs -> {
            sales.put(rs.getDate("d").toLocalDate(), new Object[]{rs.getLong("n"), rs.getBigDecimal("g"),
                    rs.getBigDecimal("disc"), rs.getBigDecimal("tax"), rs.getBigDecimal("cost")});
        }, p.offsetMinutes(), p.storeId(), p.start(), p.end());
        Map<LocalDate, BigDecimal> refunds = new TreeMap<>();
        jdbc.query("""
                SELECT DATE(DATE_ADD(created_at, INTERVAL ? MINUTE)) d, SUM(refund_total) r
                FROM returns WHERE store_id = ? AND created_at >= ? AND created_at < ? GROUP BY d""",
                rs -> {
                    refunds.put(rs.getDate("d").toLocalDate(), rs.getBigDecimal("r"));
                }, p.offsetMinutes(), p.storeId(), p.start(), p.end());

        List<DailySalesRow> days = new ArrayList<>();
        long tn = 0;
        BigDecimal tg = BigDecimal.ZERO, td = BigDecimal.ZERO, tt = BigDecimal.ZERO, tr = BigDecimal.ZERO, tc = BigDecimal.ZERO;
        for (LocalDate d = p.from(); !d.isAfter(p.to()); d = d.plusDays(1)) {
            Object[] s = sales.get(d);
            long n = s == null ? 0 : (Long) s[0];
            BigDecimal g = money(s == null ? null : s[1]);
            BigDecimal disc = money(s == null ? null : s[2]);
            BigDecimal tax = money(s == null ? null : s[3]);
            BigDecimal c = money(s == null ? null : s[4]);
            BigDecimal r = money(refunds.get(d));
            days.add(row(d, n, g, disc, tax, r, c, cost));
            tn += n;
            tg = tg.add(g);
            td = td.add(disc);
            tt = tt.add(tax);
            tr = tr.add(r);
            tc = tc.add(c);
        }
        DailySalesRow totals = row(null, tn, tg, td, tt, tr, tc, cost);
        BigDecimal avg = tn == 0 ? money(null) : MoneyPolicy.amount(tg.divide(BigDecimal.valueOf(tn), 2, MoneyPolicy.ROUNDING));

        List<TenderRow> tenders = jdbc.query("""
                SELECT p.tender_type t, COUNT(*) n, SUM(p.amount) a FROM payments p JOIN sales s ON s.id = p.sale_id
                WHERE s.store_id = ? AND s.completed_at >= ? AND s.completed_at < ?
                GROUP BY p.tender_type ORDER BY a DESC""",
                (rs, i) -> new TenderRow(rs.getString("t"), rs.getLong("n"), money(rs.getBigDecimal("a"))),
                p.storeId(), p.start(), p.end());
        List<ChannelRow> channels = jdbc.query("""
                SELECT channel c, COUNT(*) n, SUM(grand_total) a FROM sales
                WHERE store_id = ? AND completed_at >= ? AND completed_at < ? GROUP BY channel ORDER BY a DESC""",
                (rs, i) -> new ChannelRow(rs.getString("c"), rs.getLong("n"), money(rs.getBigDecimal("a"))),
                p.storeId(), p.start(), p.end());
        return new SalesSummaryDto(p.from(), p.to(), days, totals, avg, tenders, channels, cost);
    }

    private static DailySalesRow row(LocalDate d, long n, BigDecimal g, BigDecimal disc, BigDecimal tax, BigDecimal r,
                                     BigDecimal c, boolean cost) {
        BigDecimal net = MoneyPolicy.amount(g.subtract(r));
        // Profit on the period's sales, excluding tax (returns are shown separately as refunds).
        BigDecimal profit = cost ? MoneyPolicy.amount(g.subtract(tax).subtract(c)) : null;
        return new DailySalesRow(d, n, MoneyPolicy.amount(g), MoneyPolicy.amount(disc), MoneyPolicy.amount(tax),
                MoneyPolicy.amount(r), net, cost ? MoneyPolicy.amount(c) : null, profit);
    }

    @Transactional(readOnly = true)
    public List<TopProductRow> topProducts(LocalDate from, LocalDate to, int limit) {
        Period p = period(from, to);
        return topProducts(p, Math.max(1, Math.min(limit, 100)));
    }

    private List<TopProductRow> topProducts(Period p, int limit) {
        return jdbc.query("""
                SELECT i.product_id id, MAX(i.sku_snapshot) sku, MAX(i.product_name_snapshot) name,
                       SUM(i.quantity - i.quantity_returned) q, SUM(i.line_total - i.refunded_amount) rev,
                       COUNT(DISTINCT i.sale_id) n
                FROM sale_items i JOIN sales s ON s.id = i.sale_id
                WHERE s.store_id = ? AND s.completed_at >= ? AND s.completed_at < ?
                GROUP BY i.product_id ORDER BY rev DESC LIMIT ?""",
                (rs, i) -> new TopProductRow(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                        MoneyPolicy.quantity(rs.getBigDecimal("q")), money(rs.getBigDecimal("rev")), rs.getLong("n")),
                p.storeId(), p.start(), p.end(), limit);
    }

    @Transactional(readOnly = true)
    public List<CategoryRow> categories(LocalDate from, LocalDate to) {
        Period p = period(from, to);
        return jdbc.query("""
                SELECT c.id id, c.name name, SUM(i.line_total - i.refunded_amount) rev, SUM(i.quantity - i.quantity_returned) q
                FROM sale_items i JOIN sales s ON s.id = i.sale_id JOIN categories c ON c.id = i.category_id
                WHERE s.store_id = ? AND s.completed_at >= ? AND s.completed_at < ?
                GROUP BY c.id, c.name ORDER BY rev DESC""",
                (rs, i) -> new CategoryRow(rs.getLong("id"), rs.getString("name"), money(rs.getBigDecimal("rev")),
                        MoneyPolicy.quantity(rs.getBigDecimal("q"))),
                p.storeId(), p.start(), p.end());
    }

    @Transactional(readOnly = true)
    public List<HourRow> hours(LocalDate from, LocalDate to) {
        return hours(period(from, to));
    }

    private List<HourRow> hours(Period p) {
        Map<Integer, HourRow> byHour = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) {
            byHour.put(h, new HourRow(h, 0, money(null)));
        }
        jdbc.query("""
                SELECT HOUR(DATE_ADD(completed_at, INTERVAL ? MINUTE)) h, COUNT(*) n, SUM(grand_total) a
                FROM sales WHERE store_id = ? AND completed_at >= ? AND completed_at < ? GROUP BY h""",
                rs -> {
                    int h = rs.getInt("h");
                    byHour.put(h, new HourRow(h, rs.getLong("n"), money(rs.getBigDecimal("a"))));
                }, p.offsetMinutes(), p.storeId(), p.start(), p.end());
        return new ArrayList<>(byHour.values());
    }

    // ----- stock ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<LowStockRow> lowStock() {
        Long storeId = currentUser.require().requireActiveStore();
        return jdbc.query("""
                SELECT p.id, p.sku, p.name, COALESCE(b.quantity_on_hand, 0) q, p.reorder_level r
                FROM products p LEFT JOIN inventory_balances b ON b.product_id = p.id AND b.store_id = ?
                WHERE p.active = TRUE AND p.reorder_level > 0 AND COALESCE(b.quantity_on_hand, 0) <= p.reorder_level
                ORDER BY (COALESCE(b.quantity_on_hand, 0) / p.reorder_level), p.name LIMIT 500""",
                (rs, i) -> new LowStockRow(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                        MoneyPolicy.quantity(rs.getBigDecimal("q")), MoneyPolicy.quantity(rs.getBigDecimal("r"))),
                storeId);
    }

    @Transactional(readOnly = true)
    public List<ExpiringRow> expiring(int days) {
        Long storeId = currentUser.require().requireActiveStore();
        ZoneId zone = ZoneId.of(stores.requireStore(storeId).getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return jdbc.query("""
                SELECT b.product_id, p.name, b.batch_number, b.expiry_date, b.quantity_remaining
                FROM inventory_batches b JOIN products p ON p.id = b.product_id
                WHERE b.store_id = ? AND b.status = 'ACTIVE' AND b.quantity_remaining > 0
                  AND b.expiry_date IS NOT NULL AND b.expiry_date <= ?
                ORDER BY b.expiry_date, p.name LIMIT 500""",
                (rs, i) -> new ExpiringRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDate(4).toLocalDate(),
                        MoneyPolicy.quantity(rs.getBigDecimal(5))),
                storeId, java.sql.Date.valueOf(today.plusDays(Math.max(0, Math.min(days, 90)))));
    }

    @Transactional(readOnly = true)
    public List<StockValueRow> stockValuation() {
        if (!canSeeCost()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Stock valuation shows cost prices and needs financial report access.");
        }
        Long storeId = currentUser.require().requireActiveStore();
        return jdbc.query("""
                SELECT c.id, c.name, COUNT(*) n, SUM(b.quantity_on_hand) u, SUM(b.quantity_on_hand * b.average_cost) v
                FROM inventory_balances b JOIN products p ON p.id = b.product_id JOIN categories c ON c.id = p.category_id
                WHERE b.store_id = ? AND b.quantity_on_hand > 0
                GROUP BY c.id, c.name ORDER BY v DESC""",
                (rs, i) -> new StockValueRow(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        MoneyPolicy.quantity(rs.getBigDecimal(4)), money(rs.getBigDecimal(5))),
                storeId);
    }

    // ----- dashboard ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        LocalDate today = period(null, null).to();
        Period todayP = period(today, today);
        SalesSummaryDto summary = salesSummary(today.minusDays(13), today);
        DailySalesRow t = summary.days().get(summary.days().size() - 1);
        BigDecimal avg = t.salesCount() == 0 ? money(null)
                : MoneyPolicy.amount(t.grossSales().divide(BigDecimal.valueOf(t.salesCount()), 2, MoneyPolicy.ROUNDING));

        // Yesterday up to the same time of day, for a fair "vs yesterday" comparison.
        Instant now = clock.instant();
        LocalTime nowLocal = LocalTime.ofInstant(now, todayP.zone());
        Instant yStart = today.minusDays(1).atStartOfDay(todayP.zone()).toInstant();
        Instant yNow = today.minusDays(1).atTime(nowLocal).atZone(todayP.zone()).toInstant();
        BigDecimal yesterday = money(jdbc.queryForObject(
                "SELECT SUM(grand_total) FROM sales WHERE store_id = ? AND completed_at >= ? AND completed_at < ?",
                BigDecimal.class, todayP.storeId(), Timestamp.from(yStart), Timestamp.from(yNow)));

        Long storeId = todayP.storeId();
        Map<String, Long> online = new LinkedHashMap<>();
        jdbc.query("""
                SELECT status, COUNT(*) n FROM online_orders WHERE store_id = ?
                  AND status IN ('PLACED','CONFIRMED','PACKING','READY_FOR_PICKUP') GROUP BY status""",
                rs -> {
                    online.put(rs.getString(1), rs.getLong(2));
                }, storeId);
        long arrived = count("""
                SELECT COUNT(*) FROM online_orders WHERE store_id = ? AND status = 'READY_FOR_PICKUP' AND arrived_at IS NOT NULL""",
                storeId);
        long low = count("""
                SELECT COUNT(*) FROM products p LEFT JOIN inventory_balances b ON b.product_id = p.id AND b.store_id = ?
                WHERE p.active = TRUE AND p.reorder_level > 0 AND COALESCE(b.quantity_on_hand, 0) <= p.reorder_level""",
                storeId);
        long expiring = count("""
                SELECT COUNT(*) FROM inventory_batches WHERE store_id = ? AND status = 'ACTIVE' AND quantity_remaining > 0
                  AND expiry_date IS NOT NULL AND expiry_date <= ?""",
                storeId, java.sql.Date.valueOf(today.plusDays(3)));
        long rescue = count("""
                SELECT COUNT(*) FROM product_promotions WHERE store_id = ? AND source = 'RESCUE' AND active = TRUE
                  AND starts_at <= ? AND ends_at > ?""",
                storeId, Timestamp.from(now), Timestamp.from(now));
        long shifts = count("SELECT COUNT(*) FROM cashier_shifts WHERE store_id = ? AND status = 'OPEN'", storeId);

        return new DashboardDto(today, t.salesCount(), t.grossSales(), avg, t.refunds(), yesterday, summary.days(),
                hours(todayP), topProducts(period(today.minusDays(6), today), 5), online, arrived, low, expiring,
                rescue, shifts);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
