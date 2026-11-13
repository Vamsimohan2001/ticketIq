package com.ticketiq.core.llm;

import com.ticketiq.core.json.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** POSTs JSON and reads JSON back, turning HTTP failures into the right kind of exception. */
final class HttpJson {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final Duration requestTimeout;

    HttpJson(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    Map<String, Object> post(String url, Map<String, String> headers, Object body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        HttpResponse<String> response;
        try {
            response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling the model", e);
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach the model endpoint: " + e.getMessage(), e);
        }
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return Json.parseObject(response.body());
        }
        String detail = "Model endpoint answered HTTP " + status;
        // 4xx (except 408 and 429) means our request is wrong; retrying will not help.
        if (status >= 400 && status < 500 && status != 408 && status != 429) {
            throw new NonRetryableAiException(detail);
        }
        throw new IllegalStateException(detail);
    }
}
