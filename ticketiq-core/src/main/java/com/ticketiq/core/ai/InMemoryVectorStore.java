package com.ticketiq.core.ai;

import com.ticketiq.core.llm.Vectors;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Brute-force cosine search over a map. Plenty fast for thousands of tickets. */
public final class InMemoryVectorStore implements VectorStore {

    private final Map<String, VectorDoc> docs = new ConcurrentHashMap<>();

    @Override
    public void upsert(VectorDoc doc) {
        docs.put(doc.id(), doc);
    }

    @Override
    public void delete(String id) {
        docs.remove(id);
    }

    @Override
    public List<VectorHit> search(float[] query, int topK, double threshold, VectorDoc.Type type, String excludeRefId) {
        return docs.values().stream()
                .filter(d -> type == null || d.type() == type)
                .filter(d -> excludeRefId == null || !excludeRefId.equals(d.refId()))
                .filter(d -> d.vector().length == query.length)
                .map(d -> new VectorHit(d, Vectors.cosine(query, d.vector())))
                .filter(h -> h.score() >= threshold)
                .sorted(Comparator.comparingDouble(VectorHit::score).reversed())
                .limit(topK)
                .toList();
    }

    @Override
    public int size() {
        return docs.size();
    }
}
