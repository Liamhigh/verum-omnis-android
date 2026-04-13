package com.verum.omnis.ai;

import com.verum.omnis.core.CurrencyConverter;
import com.verum.omnis.forensic.RecoveryLedger;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extract business related fraud signals from a document.
 *
 * <p>This class scans a text file for currency amounts and company names
 * using simple regular expressions.  It includes a helper to convert
 * amounts to a base currency via {@link CurrencyConverter}.</p>
 */
public class BusinessFraudExtractor {
    // Currency pattern and amount.  Double escape backslashes because this string is
    // a Java literal.
    private static final Pattern AMOUNT = Pattern.compile(
            "(?i)(USD|ZAR|EUR|GBP|AED|CAD|AUD|CHF|HKD|JPY|INR|R|\\$|€|£|AED)\\s*([0-9][0-9,\\.]{2,})"
    );

    // Rough company name detector using common suffixes.  Dots and parentheses are
    // escaped appropriately.  Hyphen and whitespace are escaped for Java string.
    private static final Pattern COMPANY = Pattern.compile(
            "(?i)([A-Z][A-Za-z0-9&\\-\\s]{1,60}?\\s(?:Ltd|LLC|Inc|Corp|GmbH|Sarl|BV|PLC|Pty|Pty\\s*Ltd|Limited|Company|Co\\.?|S\\.A\\.|AG|Oy|AB|Kft|S\\.p\\.A|SRL))"
    );

    /** Container for parsed extraction results. */
    public static class Extraction {
        public boolean isBusiness;
        public String company;
        public String currency;
        public double amount;
    }

    /**
     * Parse a file for currency and company indicators.
     *
     * @param f the file to parse
     * @return an {@link Extraction} object with parsed fields (may be defaulted)
     */
    public static Extraction parse(File f) {
        Extraction ex = new Extraction();
        try {
            // Read the file into a string without using java.nio APIs (not available on older Android).
            byte[] bytes;
            try (FileInputStream fis = new FileInputStream(f)) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, len);
                }
                bytes = bos.toByteArray();
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            Matcher m = AMOUNT.matcher(text);
            if (m.find()) {
                ex.currency = m.group(1).toUpperCase(Locale.ROOT);
                String raw = m.group(2).replaceAll(",", "");
                try {
                    ex.amount = Double.parseDouble(raw);
                } catch (Exception ignore) {
                    // leave as default 0.0
                }
            }
            Matcher c = COMPANY.matcher(text);
            if (c.find()) {
                ex.company = c.group(1).trim();
            }
            ex.isBusiness = RecoveryLedger.looksLikeBusiness(ex.company);
        } catch (Exception ignore) {
            // ignore
        }
        return ex;
    }

    /**
     * Convert the given amount in a particular currency to the app's base currency
     * (currently USD) using rates from the asset configuration.  This helper is
     * provided here for convenience.
     *
     * @param ctx      Android context used to load fx_table.json
     * @param currency Three‑letter currency code (e.g. "ZAR", "EUR") or symbol
     * @param amount   Amount in the specified currency
     * @return Equivalent amount in the base currency
     */
    public static double toBaseUsd(android.content.Context ctx, String currency, double amount) {
        return CurrencyConverter.toBase(ctx, currency, amount);
    }
}