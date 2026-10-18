package com.supermarket.pos.products.service;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;
import com.supermarket.pos.products.entity.BarcodeType;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes barcodes so the same item always maps to the same stored value:
 * <ul>
 *   <li>whitespace and dashes are removed;</li>
 *   <li>UPC-A (12) and UPC-E (8, expanded) become GTIN-13 with a leading 0;</li>
 *   <li>GTIN-14 with a leading 0 becomes GTIN-13;</li>
 *   <li>GS1 check digits are verified (a typo is rejected instead of silently not matching);</li>
 *   <li>other printable codes are kept as Code 128 / internal codes.</li>
 * </ul>
 * Scanning a barcode identifies a product only; price, stock and authorization always come from the server.
 */
public final class BarcodeNormalizer {

    public record Normalized(String value, BarcodeType type) {
    }

    private static final Pattern DIGITS = Pattern.compile("^\\d+$");
    private static final Pattern PRINTABLE = Pattern.compile("^[\\x21-\\x7E]{1,32}$");

    private BarcodeNormalizer() {
    }

    /**
     * @param raw        scanned or typed value
     * @param formatHint optional format reported by the scanner (e.g. "UPC_E"); resolves the
     *                   EAN-8 / UPC-E ambiguity of 8-digit codes
     */
    public static Normalized normalize(String raw, String formatHint) {
        if (raw == null) {
            throw invalid("Barcode is required.");
        }
        String code = raw.trim().replace(" ", "").replace("-", "");
        if (code.isEmpty()) {
            throw invalid("Barcode is required.");
        }
        String hint = formatHint == null ? "" : formatHint.trim().toUpperCase(Locale.ROOT).replace("-", "_");

        if (!DIGITS.matcher(code).matches()) {
            if (!PRINTABLE.matcher(code).matches()) {
                throw invalid("Barcode contains unsupported characters.");
            }
            return new Normalized(code, BarcodeType.CODE_128);
        }
        switch (code.length()) {
            case 8 -> {
                if ("UPC_E".equals(hint)) {
                    return upcE(code);
                }
                if (isValidGtin(code)) {
                    return new Normalized(code, BarcodeType.EAN_8);
                }
                if ((code.charAt(0) == '0' || code.charAt(0) == '1') && isValidUpcE(code)) {
                    return upcE(code);
                }
                throw invalid("Invalid EAN-8 / UPC-E check digit.");
            }
            case 12 -> {
                requireCheckDigit(code, "UPC-A");
                return new Normalized("0" + code, BarcodeType.UPC_A);
            }
            case 13 -> {
                requireCheckDigit(code, "EAN-13");
                return new Normalized(code, BarcodeType.EAN_13);
            }
            case 14 -> {
                requireCheckDigit(code, "GTIN-14");
                return code.charAt(0) == '0'
                        ? new Normalized(code.substring(1), BarcodeType.GTIN_14)
                        : new Normalized(code, BarcodeType.GTIN_14);
            }
            default -> {
                if (code.length() > 32) {
                    throw invalid("Barcode is too long.");
                }
                return new Normalized(code, BarcodeType.INTERNAL);
            }
        }
    }

    public static Normalized normalize(String raw) {
        return normalize(raw, null);
    }

    /** GS1 check digit (mod 10, weights 3/1 from the right) over all digits except the last. */
    public static int checkDigit(String withoutCheck) {
        int sum = 0;
        boolean weightThree = true;
        for (int i = withoutCheck.length() - 1; i >= 0; i--) {
            int d = withoutCheck.charAt(i) - '0';
            sum += weightThree ? d * 3 : d;
            weightThree = !weightThree;
        }
        return (10 - (sum % 10)) % 10;
    }

    public static boolean isValidGtin(String code) {
        if (code.length() < 2 || !DIGITS.matcher(code).matches()) {
            return false;
        }
        int expected = checkDigit(code.substring(0, code.length() - 1));
        return expected == code.charAt(code.length() - 1) - '0';
    }

    /** Expands an 8-digit UPC-E (number system + 6 digits + check) to the 12-digit UPC-A. */
    public static String expandUpcE(String upcE) {
        char ns = upcE.charAt(0);
        String d = upcE.substring(1, 7);
        char check = upcE.charAt(7);
        char last = d.charAt(5);
        String body = switch (last) {
            case '0', '1', '2' -> d.substring(0, 2) + last + "0000" + d.substring(2, 5);
            case '3' -> d.substring(0, 3) + "00000" + d.substring(3, 5);
            case '4' -> d.substring(0, 4) + "00000" + d.charAt(4);
            default -> d.substring(0, 5) + "0000" + last;
        };
        return ns + body + check;
    }

    private static boolean isValidUpcE(String code) {
        return isValidGtin(expandUpcE(code));
    }

    private static Normalized upcE(String code) {
        String upcA = expandUpcE(code);
        if (!isValidGtin(upcA)) {
            throw invalid("Invalid UPC-E check digit.");
        }
        return new Normalized("0" + upcA, BarcodeType.UPC_E);
    }

    private static void requireCheckDigit(String code, String label) {
        if (!isValidGtin(code)) {
            throw invalid("Invalid " + label + " check digit.");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.INVALID_BARCODE, message);
    }
}
