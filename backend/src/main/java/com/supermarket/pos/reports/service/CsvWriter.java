package com.supermarket.pos.reports.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Minimal RFC 4180 CSV builder (UTF-8 with BOM so spreadsheet apps detect the encoding).
 * Text starting with =, +, -, @, tab or CR is prefixed with an apostrophe so a spreadsheet never
 * evaluates it as a formula (CSV injection). Numbers are written as plain decimals.
 */
public final class CsvWriter {

    private final StringBuilder out = new StringBuilder("﻿");

    public CsvWriter row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells[i]));
        }
        out.append("\r\n");
        return this;
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal d) {
            return d.toPlainString();
        }
        if (value instanceof Number) {
            return value.toString();
        }
        String s = value.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public byte[] bytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
