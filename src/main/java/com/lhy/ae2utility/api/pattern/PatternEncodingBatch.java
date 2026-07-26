package com.lhy.ae2utility.api.pattern;

import java.util.List;

/** Options shared by one server-authoritative batch operation. */
public record PatternEncodingBatch(
        List<PatternEncodingRequest> requests,
        int sessionId,
        boolean fullRecipeCategory) {

    public PatternEncodingBatch {
        if (requests == null) {
            throw new IllegalArgumentException("requests are required");
        }
        requests = List.copyOf(requests);
    }
}