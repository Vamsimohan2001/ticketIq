package com.ticketiq.core.ai;

import java.util.List;

/** Duplicate detection: finds earlier tickets whose embedding is close to this one (IP-4). */
public final class SimilarityService {

    private static final int TOP_K = 5;

    private final VectorStore store;
    private final double threshold;

    public SimilarityService(VectorStore store, double threshold) {
        this.store = store;
        this.threshold = threshold;
    }

    public List<SimilarTicket> findSimilar(String ticketId, float[] vector) {
        return store.search(vector, TOP_K, threshold, VectorDoc.Type.TICKET, ticketId).stream()
                .map(h -> new SimilarTicket(h.doc().refId(), h.doc().title(), round(h.score())))
                .toList();
    }

    static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
