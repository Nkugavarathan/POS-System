package com.supermarket.pos.customers.service;

import com.supermarket.pos.common.error.ApiException;
import com.supermarket.pos.common.error.ErrorCode;

import java.util.regex.Pattern;

/** Normalizes phone numbers to E.164. Local Sri Lankan forms (07X XXX XXXX) become +947XXXXXXXX. */
public final class PhoneNumbers {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.trim().replaceAll("[\\s().-]", "");
        if (digits.startsWith("00")) {
            digits = "+" + digits.substring(2);
        } else if (digits.startsWith("0") && digits.length() == 10) {
            digits = "+94" + digits.substring(1);
        } else if (digits.startsWith("94") && digits.length() == 11) {
            digits = "+" + digits;
        } else if (!digits.startsWith("+") && digits.length() == 9) {
            digits = "+94" + digits;
        }
        if (!E164.matcher(digits).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Enter a valid phone number, e.g. 077 123 4567.");
        }
        return digits;
    }

    public static String mask(String e164) {
        if (e164 == null || e164.length() < 6) {
            return e164;
        }
        return e164.substring(0, 3) + " *** *** " + e164.substring(e164.length() - 4);
    }
}
