package com.ticketiq.core.llm;

/** A text-generation model. Implementations: mock, Gemini, Ollama. */
public interface ChatModel {

    String provider();

    String model();

    /**
     * @param system   instructions for the model
     * @param user     the task and the data
     * @param jsonOnly true when the answer must be a single JSON object
     */
    String generate(String system, String user, boolean jsonOnly);
}
