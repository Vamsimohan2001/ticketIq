package com.ticketiq.core.ai;

/**
 * One searchable item: a knowledge-base article or a resolved ticket.
 *
 * @param refId      the article id or ticket id (without the type prefix)
 * @param resolution how a resolved ticket was fixed (empty for articles)
 */
public record VectorDoc(String id, Type type, String refId, String title, String text,
                        String resolution, String category, float[] vector) {

    public enum Type { TICKET, KB }

    public static String idFor(Type type, String refId) {
        return type.name().toLowerCase() + ":" + refId;
    }
}
