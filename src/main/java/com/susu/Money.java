package com.susu;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Converts Ghana cedi (GHS) amounts between text and whole pesewas (1 GHS = 100 pesewas).
 *
 * <p>All money in the program is a {@code long} number of pesewas, so adding and comparing
 * amounts is exact. {@link BigDecimal} is used only to turn checked text into pesewas and
 * back; {@code double} and {@code float} are never used.
 */
public final class Money {

    /** Optional sign, digits, then optionally a dot and one or two digits: "50", "50.5", "-3.00". */
    private static final Pattern VALID = Pattern.compile("[+-]?\\d+(\\.\\d{1,2})?");

    /** Same shape but with three or more decimals, so we can give a clearer message. */
    private static final Pattern TOO_MANY_DECIMALS = Pattern.compile("[+-]?\\d+\\.\\d{3,}");

    private Money() {
    }

    /**
     * Parses text such as "50", "50.5" or "50.00" into pesewas (5000, 5050, 5000).
     *
     * <p>Rejects empty text, letters, "NaN", "Infinity", scientific notation ("1e3"),
     * thousands separators ("1,000"), and more than two decimal places. The sign is
     * accepted here; whether zero or negative amounts are allowed is decided by the caller.
     *
     * @throws SusuException if the text is not a valid GHS amount
     */
    public static long parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new SusuException("Amount is required, for example 50.00.");
        }
        String trimmed = text.trim();
        if (TOO_MANY_DECIMALS.matcher(trimmed).matches()) {
            throw new SusuException("Amount '" + trimmed + "' has more than 2 decimal places.");
        }
        if (!VALID.matcher(trimmed).matches()) {
            throw new SusuException("'" + trimmed + "' is not a valid amount. Use digits like 50 or 50.00.");
        }
        try {
            return new BigDecimal(trimmed).movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new SusuException("Amount '" + trimmed + "' is too large.");
        }
    }

    /** Formats pesewas as GHS text with exactly two decimals: 5000 becomes "50.00". */
    public static String format(long pesewas) {
        return BigDecimal.valueOf(pesewas, 2).toPlainString();
    }
}
