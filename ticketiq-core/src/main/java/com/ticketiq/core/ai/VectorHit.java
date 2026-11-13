package com.ticketiq.core.ai;

/** A search result: the document and how close it is (1.0 is identical). */
public record VectorHit(VectorDoc doc, double score) {
}
