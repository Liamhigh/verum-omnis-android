package com.verum.omnis.rules;

import java.util.Map;

/**
 * ChainLogger – placeholder interface for blockchain anchoring.
 * Currently wired to NoopChainLogger. Later, swap with a real chain provider.
 */
public interface ChainLogger {
    void record(String eventType, String caseId, String payloadHash, Map<String, String> meta);
}
