package com.ticketiq.core.llm;

import com.ticketiq.core.json.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline stand-in for a real LLM so the whole pipeline runs with no API key and no cost.
 *
 * <p>It reads a TASK marker in the prompt (TASK:TRIAGE or TASK:REPLY) and answers with keyword rules.
 * The numbers it produces are NOT real model performance; swap in Gemini or Ollama for that.
 */
public final class MockChatModel implements ChatModel {

    private static final Pattern TICKET_BLOCK = Pattern.compile("<<<TICKET\\s*(.*?)\\s*TICKET>>>", Pattern.DOTALL);
    private static final Pattern CONTEXT_BLOCK = Pattern.compile("<<<CONTEXT\\s*(.*?)\\s*CONTEXT>>>", Pattern.DOTALL);
    private static final Pattern SOURCE_HEADER = Pattern.compile("^\\[SOURCE ([a-z]+:[^\\]]+)]\\s*(.*)$", Pattern.MULTILINE);
    // Sentences that try to boss the model around are ignored, like a well-behaved real model would.
    private static final Pattern INJECTION = Pattern.compile(
            "(ignore|disregard|forget)\\s+(all\\s+|any\\s+|the\\s+)?(previous|prior|above|earlier|your)?\\s*"
                    + "(instructions?|rules?|prompt)|system prompt|you are now|set (the )?priority|mark (this )?(ticket )?as|"
                    + "override|reveal (your|the) (prompt|instructions)",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, List<String>> CATEGORY_WORDS = new LinkedHashMap<>();
    static {
        CATEGORY_WORDS.put("ACCESS", List.of("password", "log in", "login", "sign in", "locked", "lockout", "2fa",
                "mfa", "two-factor", "two factor", "otp", "access denied", "permission", "sso", "single sign",
                "authenticat", "forgot", "reset link", "verification code", "invited"));
        CATEGORY_WORDS.put("BILLING", List.of("invoice", "charged", "refund", "payment", "billing", "subscription",
                "receipt", "credit card", "overcharg", "gst", "tax", "plan upgrade", "upgrade my plan", "pricing", "debited"));
        CATEGORY_WORDS.put("BUG", List.of("error", "crash", "exception", "bug", "broken", "not working", "fails",
                "failing", "stack trace", "500", "blank", "freez", "wrong", "incorrect", "glitch", "does not load",
                "doesn't load", "down", "outage", "timeout", "time out", "mismatch", "do not match", "slow"));
        CATEGORY_WORDS.put("HOW_TO", List.of("how do i", "how to", "how can i", "where can i", "where do i",
                "steps to", "instructions", "tutorial", "is it possible", "guide me", "what is the way",
                "how should i", "which setting"));
        CATEGORY_WORDS.put("INTEGRATION", List.of("api", "webhook", "sftp", "integration", "sync", "connector",
                "oauth", "endpoint", "salesforce", "slack", "csv feed", "file feed", "rest call",
                "api key", "bearer", "payload"));
        CATEGORY_WORDS.put("FEATURE_REQUEST", List.of("feature request", "would be nice", "please add", "could you add",
                "enhancement", "support for", "dark mode", "wish", "suggestion", "it would be great", "new feature",
                "would love", "ability to"));
    }

    private static final List<String> CRITICAL = List.of("outage", "everyone", "all users", "entire platform",
            "completely down", "system down", "production down", "data loss", "lost data", "breach", "data leak",
            "leaked", "nobody can");
    private static final List<String> HIGH = List.of("urgent", "asap", "blocked", "cannot", "can't", "cant ",
            "unable to", "locked out", "locked", "deadline", "payroll", "charged twice", "double charge",
            "twice", "immediately", "not received", "never arrive", "500", "401", "failing");
    private static final List<String> LOW = List.of("no rush", "when you get a chance", "minor", "cosmetic",
            "nice to have", "would be nice", "suggestion", "just curious", "whenever", "small");

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String model() {
        return "keyword-rules-v1";
    }

    @Override
    public String generate(String system, String user, boolean jsonOnly) {
        if (user != null && user.contains("TASK:REPLY")) {
            return reply(user);
        }
        return triage(user == null ? "" : user);
    }

    // ------------------------------------------------------------------ triage

    private String triage(String prompt) {
        Matcher m = TICKET_BLOCK.matcher(prompt);
        String ticket = m.find() ? m.group(1) : prompt;

        // Drop sentences that look like orders to the model, then classify what is left.
        StringBuilder kept = new StringBuilder();
        boolean sawInjection = false;
        for (String sentence : ticket.split("(?<=[.!?\\n])\\s*")) {
            if (INJECTION.matcher(sentence).find()) {
                sawInjection = true;
            } else {
                kept.append(sentence).append(' ');
            }
        }
        String text = kept.toString().toLowerCase(Locale.ROOT);

        String bestCategory = "OTHER";
        int best = 0;
        int second = 0;
        for (Map.Entry<String, List<String>> e : CATEGORY_WORDS.entrySet()) {
            int score = 0;
            for (String w : e.getValue()) {
                if (text.contains(w)) {
                    score += w.contains(" ") ? 2 : 1;
                }
            }
            if (score > best) {
                second = best;
                best = score;
                bestCategory = e.getKey();
            } else if (score > second) {
                second = score;
            }
        }

        String priority = priority(text, bestCategory);
        double confidence = best == 0 ? 0.35 : Math.min(0.95, 0.5 + 0.1 * best + 0.08 * (best - second));
        String firstLine = ticket.strip().lines().findFirst().orElse("").replaceFirst("^(?i)title:\\s*", "");
        String summary = firstLine.length() > 200 ? firstLine.substring(0, 197) + "..." : firstLine;
        if (summary.isBlank()) {
            summary = "Support request";
        }
        String rationale = best == 0
                ? "No strong keywords found, so the category is a guess."
                : "Matched " + best + " " + bestCategory.toLowerCase(Locale.ROOT).replace('_', ' ')
                + " keyword signal(s) and urgency cues for " + priority.toLowerCase(Locale.ROOT) + " priority.";
        if (sawInjection) {
            rationale += " Ignored instructions embedded in the ticket text.";
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("category", bestCategory);
        out.put("priority", priority);
        out.put("summary", summary);
        out.put("confidence", Math.round(confidence * 100.0) / 100.0);
        out.put("rationale", rationale);
        return Json.write(out);
    }

    private static String priority(String text, String category) {
        if (containsAny(text, CRITICAL)) {
            return "CRITICAL";
        }
        if (containsAny(text, HIGH)) {
            return "HIGH";
        }
        if (containsAny(text, LOW) || category.equals("FEATURE_REQUEST") || category.equals("HOW_TO")) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private static boolean containsAny(String text, List<String> words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------- reply

    private String reply(String prompt) {
        Matcher m = CONTEXT_BLOCK.matcher(prompt);
        String context = m.find() ? m.group(1) : "";
        Matcher h = SOURCE_HEADER.matcher(context);
        StringBuilder reply = new StringBuilder("Hi,\n\nThanks for getting in touch. ");
        int used = 0;
        int searchFrom = 0;
        while (h.find(searchFrom) && used < 2) {
            String id = h.group(1);
            String title = h.group(2).trim();
            int bodyStart = h.end();
            Matcher next = SOURCE_HEADER.matcher(context);
            int bodyEnd = next.find(bodyStart) ? next.start() : context.length();
            String body = context.substring(bodyStart, bodyEnd).strip().replaceFirst("^(?i)resolution:\\s*", "");
            String firstSentences = firstSentences(body, 2);
            if (!firstSentences.isBlank()) {
                reply.append(used == 0 ? "Here is what usually helps with this: " : "\n\nAlso worth checking: ")
                        .append(firstSentences).append(" [").append(id).append("]");
                used++;
            }
            searchFrom = bodyEnd;
            if (title.isEmpty() && bodyEnd >= context.length()) {
                break;
            }
        }
        if (used == 0) {
            return "";
        }
        reply.append("\n\nIf that does not solve it, reply here and an agent will pick it up.\n\nRegards,\nSupport team");
        return reply.toString();
    }

    private static String firstSentences(String text, int count) {
        String flat = text.replaceAll("\\s+", " ").trim();
        String[] parts = flat.split("(?<=[.!?])\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(count, parts.length); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(parts[i]);
        }
        String out = sb.toString();
        return out.length() > 420 ? out.substring(0, 417) + "..." : out;
    }
}
