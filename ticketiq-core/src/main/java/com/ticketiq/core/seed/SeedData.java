package com.ticketiq.core.seed;

import com.ticketiq.core.json.Json;
import com.ticketiq.core.model.Category;
import com.ticketiq.core.model.Priority;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Demo knowledge-base articles, resolved tickets and the labelled evaluation set, loaded from resources. */
public final class SeedData {

    public record KbSeed(String id, String title, String content, String tags) {
    }

    public record ResolvedSeed(String id, String title, String description, Category category,
                               Priority priority, String resolution) {
    }

    public record EvalTicket(String id, String title, String description, Category category,
                             Priority priority, String duplicateOf, boolean injection) {
    }

    private SeedData() {
    }

    public static List<KbSeed> kbArticles() {
        List<KbSeed> out = new ArrayList<>();
        for (Object o : Json.parseArray(read("/seed/kb.json"))) {
            Map<String, Object> m = Json.asMap(o);
            out.add(new KbSeed(Json.string(m, "id"), Json.string(m, "title"), Json.string(m, "content"),
                    Json.string(m, "tags")));
        }
        return out;
    }

    public static List<ResolvedSeed> resolvedTickets() {
        List<ResolvedSeed> out = new ArrayList<>();
        for (Object o : Json.parseArray(read("/seed/resolved_tickets.json"))) {
            Map<String, Object> m = Json.asMap(o);
            out.add(new ResolvedSeed(Json.string(m, "id"), Json.string(m, "title"), Json.string(m, "description"),
                    Category.parse(Json.string(m, "category")), Priority.parse(Json.string(m, "priority")),
                    Json.string(m, "resolution")));
        }
        return out;
    }

    public static List<EvalTicket> evalTickets() {
        List<EvalTicket> out = new ArrayList<>();
        for (Object o : Json.parseArray(read("/eval/tickets_labeled.json"))) {
            Map<String, Object> m = Json.asMap(o);
            out.add(new EvalTicket(Json.string(m, "id"), Json.string(m, "title"), Json.string(m, "description"),
                    Category.parse(Json.string(m, "category")), Priority.parse(Json.string(m, "priority")),
                    Json.string(m, "duplicateOf"), Boolean.TRUE.equals(m.get("injection"))));
        }
        return out;
    }

    private static String read(String resource) {
        try (InputStream in = SeedData.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + resource, e);
        }
    }
}
