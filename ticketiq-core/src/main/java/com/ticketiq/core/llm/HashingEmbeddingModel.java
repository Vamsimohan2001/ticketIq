package com.ticketiq.core.llm;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Offline embedding model: a hashed bag of words (plus word pairs), normalised to unit length.
 *
 * <p>It is not smart, but it is deterministic and similar texts really do produce similar vectors,
 * so duplicate detection and retrieval work end to end without an API key.
 */
public final class HashingEmbeddingModel implements EmbeddingModel {

    private final int dimensions;

    public HashingEmbeddingModel(int dimensions) {
        if (dimensions < 16) {
            throw new IllegalArgumentException("dimensions must be at least 16");
        }
        this.dimensions = dimensions;
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String model() {
        return "hashing-bow-" + dimensions;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[dimensions];
        List<String> tokens = TextTokens.tokens(text);
        for (int i = 0; i < tokens.size(); i++) {
            add(v, tokens.get(i), 1.0f);
            if (i + 1 < tokens.size()) {
                add(v, tokens.get(i) + "_" + tokens.get(i + 1), 0.5f);
            }
        }
        return Vectors.normalise(v);
    }

    private void add(float[] v, String token, float weight) {
        int h = fnv1a(token);
        int index = Math.floorMod(h, dimensions);
        float sign = ((h >>> 31) == 0) ? 1f : -1f;
        v[index] += sign * weight;
    }

    private static int fnv1a(String s) {
        int hash = 0x811c9dc5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= 0x01000193;
        }
        return hash;
    }
}
