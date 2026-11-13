package com.ticketiq.core.ai;

/** Where a fact in a suggested reply came from, so an agent can check it. */
public record SourceRef(String type, String id, String title, double score) {
    public String tag() {
        return type + ":" + id;
    }
}
