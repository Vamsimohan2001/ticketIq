package com.ticketiq.core.redaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces personal data with tokens such as [EMAIL] before text goes anywhere near a model,
 * an embedding call or a log line. This is the privacy gate (integration point IP-2).
 *
 * <p>Order matters: more specific patterns run first so a card number is not mistaken for a phone.
 */
public final class PiiRedactor {

    /** Redacted text plus how many items of each kind were replaced. */
    public record Result(String text, Map<String, Integer> counts) {
        public int total() {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PAN = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");
    private static final Pattern CARD = Pattern.compile("(?<![\\d])(?:\\d[ -]?){12,18}\\d(?![\\d])");
    // +91 98765 43210, +44 20 7946 0958, +1 (415) 555-2671
    private static final Pattern PHONE_INTL = Pattern.compile(
            "(?<![\\w+])\\+\\d{1,3}[\\s.-]?\\(?\\d{2,5}\\)?[\\s.-]?\\d{3,5}[\\s.-]?\\d{3,5}(?!\\d)");
    // 91 98765 43210 or 91-9876543210 (no plus sign, so a separator is required after 91)
    private static final Pattern PHONE_IN_PREFIXED = Pattern.compile(
            "(?<!\\d)91[\\s-][6-9]\\d{4}[\\s-]?\\d{5}(?!\\d)");
    private static final Pattern AADHAAR = Pattern.compile("(?<!\\d)\\d{4}[ -]?\\d{4}[ -]?\\d{4}(?!\\d)");
    // Indian mobile: 10 digits starting with 6-9, optionally written 5+5
    private static final Pattern PHONE_IN_MOBILE = Pattern.compile("(?<!\\d)[6-9]\\d{4}[ -]?\\d{5}(?!\\d)");

    /** Redacts and returns only the cleaned text. */
    public String redactText(String input) {
        return redact(input).text();
    }

    public Result redact(String input) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (input == null || input.isEmpty()) {
            return new Result(input == null ? "" : input, counts);
        }
        String text = input;
        text = replace(text, EMAIL, "EMAIL", counts, false);
        text = replace(text, PAN, "PAN", counts, false);
        text = replace(text, CARD, "CARD", counts, true);
        text = replace(text, PHONE_INTL, "PHONE", counts, false);
        text = replace(text, PHONE_IN_PREFIXED, "PHONE", counts, false);
        text = replace(text, AADHAAR, "ID", counts, false);
        text = replace(text, PHONE_IN_MOBILE, "PHONE", counts, false);
        return new Result(text, counts);
    }

    private static String replace(String text, Pattern pattern, String token,
                                  Map<String, Integer> counts, boolean requireLuhn) {
        Matcher m = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String match = m.group();
            if (requireLuhn && !passesLuhn(match)) {
                m.appendReplacement(out, Matcher.quoteReplacement(match));
                continue;
            }
            counts.merge(token, 1, Integer::sum);
            m.appendReplacement(out, Matcher.quoteReplacement("[" + token + "]"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Card numbers are only treated as cards when the Luhn checksum holds, which avoids false hits. */
    static boolean passesLuhn(String digitsWithSeparators) {
        int sum = 0;
        boolean doubleIt = false;
        int count = 0;
        for (int i = digitsWithSeparators.length() - 1; i >= 0; i--) {
            char c = digitsWithSeparators.charAt(i);
            if (!Character.isDigit(c)) {
                continue;
            }
            int d = c - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
            count++;
        }
        return count >= 13 && sum % 10 == 0;
    }
}
