package com.ticketiq.core.ai;

import java.util.List;

/**
 * Where embeddings live. The default is in memory; the interface is small so a pgvector
 * implementation can replace it without touching the pipeline.
 */
public interface VectorStore {

    void upsert(VectorDoc doc);

    void delete(String id);

    /**
     * @param excludeRefId skip this document (so a ticket never matches itself); may be null
     */
    List<VectorHit> search(float[] query, int topK, double threshold, VectorDoc.Type type, String excludeRefId);

    int size();
}
