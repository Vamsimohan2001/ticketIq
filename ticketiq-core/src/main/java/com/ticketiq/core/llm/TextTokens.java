package com.ticketiq.core.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Tokenising used by the offline mock models: lowercase words, no stop words, light stemming. */
final class TextTokens {

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "of", "to", "in", "on", "at", "for", "with", "from",
            "by", "is", "are", "was", "were", "be", "been", "am", "it", "its", "this", "that", "these", "those",
            "i", "me", "my", "we", "our", "you", "your", "they", "them", "their", "he", "she", "his", "her",
            "do", "does", "did", "can", "could", "would", "should", "will", "have", "has", "had", "not", "no",
            "so", "as", "than", "then", "there", "here", "just", "also", "very", "please", "hi", "hello",
            "thanks", "thank", "regards", "get", "got", "any", "some", "all", "when", "what", "which", "who",
            "how", "why", "into", "out", "up", "about", "again", "still", "even", "after", "before", "now");

    private TextTokens() {
    }

    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        // Redaction tokens like [EMAIL] carry no meaning for similarity.
        String cleaned = text.replaceAll("\\[[A-Z]+]", " ").toLowerCase();
        for (String raw : cleaned.split("[^a-z0-9]+")) {
            if (raw.length() < 2 || STOP.contains(raw)) {
                continue;
            }
            out.add(stem(raw));
        }
        return out;
    }

    static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ing")) {
            return w.substring(0, w.length() - 3);
        }
        if (w.length() > 4 && w.endsWith("ed")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.length() > 4 && w.endsWith("ies")) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.length() > 3 && w.endsWith("es") && !w.endsWith("ses")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }
}
