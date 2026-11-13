package com.ticketiq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ticketiq.core.ai.InMemoryVectorStore;
import com.ticketiq.core.ai.SimilarTicket;
import com.ticketiq.core.ai.SimilarityService;
import com.ticketiq.core.ai.VectorDoc;
import com.ticketiq.core.ai.VectorHit;
import com.ticketiq.core.llm.HashingEmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.Test;

class VectorStoreAndSimilarityTest {

    private final HashingEmbeddingModel embed = new HashingEmbeddingModel(256);
    private final InMemoryVectorStore store = new InMemoryVectorStore();

    private void add(VectorDoc.Type type, String ref, String title) {
        store.upsert(new VectorDoc(VectorDoc.idFor(type, ref), type, ref, title, title, "fixed", "BUG", embed.embed(title)));
    }

    @Test
    void searchReturnsBestMatchesFirstAndRespectsTopK() {
        add(VectorDoc.Type.TICKET, "1", "Reports page blank screen error 500");
        add(VectorDoc.Type.TICKET, "2", "Dark mode request for the web app");
        add(VectorDoc.Type.TICKET, "3", "Reports page shows blank white screen");
        List<VectorHit> hits = store.search(embed.embed("blank reports page with 500 error"), 2, 0.1,
                VectorDoc.Type.TICKET, null);
        assertEquals(2, hits.size());
        assertTrue(hits.get(0).score() >= hits.get(1).score());
        assertTrue(List.of("1", "3").contains(hits.get(0).doc().refId()));
    }

    @Test
    void searchFiltersByTypeAndExcludesTheTicketItself() {
        add(VectorDoc.Type.TICKET, "1", "Cannot reset password email missing");
        add(VectorDoc.Type.KB, "kb-1", "How to reset your password");
        float[] q = embed.embed("Cannot reset password email missing");
        assertEquals(1, store.search(q, 5, 0.1, VectorDoc.Type.KB, null).size());
        assertTrue(store.search(q, 5, 0.1, VectorDoc.Type.TICKET, "1").isEmpty(), "a ticket never matches itself");
    }

    @Test
    void thresholdHidesWeakMatches() {
        add(VectorDoc.Type.TICKET, "1", "Dark mode request");
        assertTrue(store.search(embed.embed("invoice charged twice"), 5, 0.5, VectorDoc.Type.TICKET, null).isEmpty());
    }

    @Test
    void upsertReplacesAndDeleteRemoves() {
        add(VectorDoc.Type.KB, "kb-1", "Old title about passwords");
        add(VectorDoc.Type.KB, "kb-1", "New title about invoices");
        assertEquals(1, store.size());
        store.delete(VectorDoc.idFor(VectorDoc.Type.KB, "kb-1"));
        assertEquals(0, store.size());
    }

    @Test
    void similarityServiceReturnsRoundedScoresWithoutTheTicketItself() {
        add(VectorDoc.Type.TICKET, "R-1", "Nightly SFTP file did not arrive");
        add(VectorDoc.Type.TICKET, "NEW", "SFTP nightly file missing");
        SimilarityService svc = new SimilarityService(store, 0.3);
        List<SimilarTicket> similar = svc.findSimilar("NEW", embed.embed("SFTP nightly file missing"));
        assertEquals(1, similar.size());
        assertEquals("R-1", similar.get(0).ticketId());
        assertTrue(similar.get(0).score() > 0.3 && similar.get(0).score() <= 1.0);
    }
}
