package com.supermarket.pos.reports.controller;

import com.supermarket.pos.audit.service.AuditService;
import com.supermarket.pos.reports.dto.ReportDtos.CategoryRow;
import com.supermarket.pos.reports.dto.ReportDtos.DashboardDto;
import com.supermarket.pos.reports.dto.ReportDtos.ExpiringRow;
import com.supermarket.pos.reports.dto.ReportDtos.HourRow;
import com.supermarket.pos.reports.dto.ReportDtos.LowStockRow;
import com.supermarket.pos.reports.dto.ReportDtos.SalesSummaryDto;
import com.supermarket.pos.reports.dto.ReportDtos.StockValueRow;
import com.supermarket.pos.reports.dto.ReportDtos.TopProductRow;
import com.supermarket.pos.reports.service.CsvWriter;
import com.supermarket.pos.reports.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Reports", description = "Dashboard, sales, products, stock; CSV export")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reports;
    private final AuditService audit;

    public ReportController(ReportService reports, AuditService audit) {
        this.reports = reports;
        this.audit = audit;
    }

    @PreAuthorize("hasAnyAuthority('DASHBOARD_VIEW','REPORT_VIEW')")
    @GetMapping("/dashboard")
    public DashboardDto dashboard() {
        return reports.dashboard();
    }

    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @GetMapping("/sales")
    public SalesSummaryDto sales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.salesSummary(from, to);
    }

    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @GetMapping("/top-products")
    public List<TopProductRow> topProducts(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "20") int limit) {
        return reports.topProducts(from, to, limit);
    }

    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @GetMapping("/categories")
    public List<CategoryRow> categories(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.categories(from, to);
    }

    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @GetMapping("/hours")
    public List<HourRow> hours(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.hours(from, to);
    }

    @PreAuthorize("hasAnyAuthority('REPORT_VIEW','INVENTORY_VIEW')")
    @GetMapping("/low-stock")
    public List<LowStockRow> lowStock() {
        return reports.lowStock();
    }

    @PreAuthorize("hasAnyAuthority('REPORT_VIEW','INVENTORY_VIEW')")
    @GetMapping("/expiring")
    public List<ExpiringRow> expiring(@RequestParam(defaultValue = "7") int days) {
        return reports.expiring(days);
    }

    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    @GetMapping("/stock-valuation")
    public List<StockValueRow> stockValuation() {
        return reports.stockValuation();
    }

    /** CSV download of one report. Cells are neutralised against spreadsheet formula injection. */
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    @GetMapping("/{report}.csv")
    public ResponseEntity<byte[]> csv(@PathVariable String report,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        CsvWriter csv = new CsvWriter();
        switch (report) {
            case "sales" -> {
                SalesSummaryDto s = reports.salesSummary(from, to);
                csv.row("Day", "Sales", "Gross sales", "Discounts", "Tax", "Refunds", "Net sales", "Cost", "Gross profit");
                s.days().forEach(d -> csv.row(d.day(), d.salesCount(), d.grossSales(), d.discounts(), d.tax(), d.refunds(),
                        d.netSales(), d.costOfSales(), d.grossProfit()));
                var t = s.totals();
                csv.row("Total", t.salesCount(), t.grossSales(), t.discounts(), t.tax(), t.refunds(), t.netSales(),
                        t.costOfSales(), t.grossProfit());
            }
            case "top-products" -> {
                csv.row("SKU", "Product", "Quantity", "Revenue", "Receipts");
                reports.topProducts(from, to, 100).forEach(r -> csv.row(r.sku(), r.name(), r.quantity(), r.revenue(), r.receipts()));
            }
            case "categories" -> {
                csv.row("Category", "Quantity", "Revenue");
                reports.categories(from, to).forEach(r -> csv.row(r.category(), r.quantity(), r.revenue()));
            }
            case "low-stock" -> {
                csv.row("SKU", "Product", "On hand", "Reorder level");
                reports.lowStock().forEach(r -> csv.row(r.sku(), r.name(), r.onHand(), r.reorderLevel()));
            }
            case "stock-valuation" -> {
                csv.row("Category", "Products", "Units", "Value at average cost");
                reports.stockValuation().forEach(r -> csv.row(r.category(), r.products(), r.units(), r.value()));
            }
            default -> {
                return ResponseEntity.notFound().build();
            }
        }
        audit.record(AuditService.Entry.success("REPORT_EXPORTED", "Report", report, "Exported " + report + " CSV")
                .withDetails(Map.of("from", String.valueOf(from), "to", String.valueOf(to))));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(report + (from != null ? "-" + from : "") + (to != null ? "-" + to : "") + ".csv").build());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(csv.bytes());
    }
}
