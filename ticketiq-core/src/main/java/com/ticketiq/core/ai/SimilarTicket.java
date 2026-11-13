package com.ticketiq.core.ai;

/** A past ticket that looks like the new one. */
public record SimilarTicket(String ticketId, String title, double score) {
}
