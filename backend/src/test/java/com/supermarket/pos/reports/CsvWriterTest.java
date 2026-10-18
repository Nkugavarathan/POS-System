package com.supermarket.pos.reports;

import com.supermarket.pos.reports.service.CsvWriter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void quotesSeparatorsAndNeutralisesFormulas() {
        String csv = new String(new CsvWriter()
                .row("Name", "Total")
                .row("Rice, 5kg", new BigDecimal("1250.50"))
                .row("=HYPERLINK(\"x\")", new BigDecimal("-3.00"))
                .row("@SUM(A1)", null)
                .bytes(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Name,Total\r\n");
        assertThat(csv).contains("\"Rice, 5kg\",1250.50\r\n");
        // Text that would start a formula is prefixed; negative numbers stay numbers.
        assertThat(csv).contains("\"'=HYPERLINK(\"\"x\"\")\",-3.00\r\n");
        assertThat(csv).contains("'@SUM(A1),\r\n");
    }
}
