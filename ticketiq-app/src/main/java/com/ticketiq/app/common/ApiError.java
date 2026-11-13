package com.ticketiq.app.common;

import java.time.Instant;
import java.util.Map;

/** The one error shape every failing request returns. */
public record ApiError(Instant timestamp, int status, String error, String message, Map<String, String> fieldErrors) {
}
