package com.supermarket.pos.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ReportDtos {

    private ReportDtos() {
    }

    /** One store-local calendar day of trading. netSales = sales − refunds. */
    public record DailySalesRow(LocalDate day, long salesCount, BigDecimal grossSales, BigDecimal discounts,
                                BigDecimal tax, BigDecimal refunds, BigDecimal netSales, BigDecimal costOfSales,
                                BigDecimal grossProfit) {
    }

    public record TenderRow(String tenderType, long count, BigDecimal amount) {
    }

    public record ChannelRow(String channel, long count, BigDecimal amount) {
    }

    public record SalesSummaryDto(LocalDate from, LocalDate to, List<DailySalesRow> days, DailySalesRow totals,
                                  BigDecimal averageBasket, List<TenderRow> tenders, List<ChannelRow> channels,
                                  boolean includesCost) {
    }

    public record TopProductRow(Long productId, String sku, String name, BigDecimal quantity, BigDecimal revenue,
                                long receipts) {
    }

    public record CategoryRow(Long categoryId, String category, BigDecimal revenue, BigDecimal quantity) {
    }

    public record HourRow(int hour, long count, BigDecimal amount) {
    }

    public record LowStockRow(Long productId, String sku, String name, BigDecimal onHand, BigDecimal reorderLevel) {
    }

    public record ExpiringRow(Long productId, String name, String batchNumber, LocalDate expiryDate,
                              BigDecimal quantity) {
    }

    public record StockValueRow(Long categoryId, String category, long products, BigDecimal units, BigDecimal value) {
    }

    public record DashboardDto(LocalDate today, long salesToday, BigDecimal revenueToday, BigDecimal averageBasketToday,
                               BigDecimal refundsToday, BigDecimal revenueYesterdaySameTime,
                               List<DailySalesRow> last14Days, List<HourRow> hoursToday,
                               List<TopProductRow> topProducts7d, Map<String, Long> onlineOrders,
                               long onlineArrivedWaiting, long lowStockCount, long expiringBatches,
                               long rescueDeals, long openShifts) {
    }
}
