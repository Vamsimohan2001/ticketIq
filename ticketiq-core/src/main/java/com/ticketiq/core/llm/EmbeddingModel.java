package com.ticketiq.core.llm;

/** Turns text into a unit-length vector so similar texts end up close together. */
public interface EmbeddingModel {

    String provider();

    String model();

    int dimensions();

    float[] embed(String text);
}
