package com.ticketiq.core.ai;

import java.util.List;

/** A draft reply and the sources it was built from. A human always reviews it. */
public record SuggestedReply(String text, List<SourceRef> sources) {
}
