package com.ticketiq.core.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Loads prompt files from src/main/resources/prompts and fills in {{placeholders}}. */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    public static String load(String name) {
        try (InputStream in = PromptTemplates.class.getResourceAsStream("/prompts/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Prompt template not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read prompt template " + name, e);
        }
    }

    public static String render(String name, Map<String, String> values) {
        String text = load(name);
        for (Map.Entry<String, String> e : values.entrySet()) {
            text = text.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return text;
    }

    /**
     * Ticket text is data, not instructions. Remove our own delimiter markers from it so a ticket
     * cannot "close" the data block and start giving orders (prompt-injection defence).
     */
    public static String neutralise(String untrusted) {
        if (untrusted == null) {
            return "";
        }
        return untrusted.replace("<<<", "< < <").replace(">>>", "> > >");
    }
}
