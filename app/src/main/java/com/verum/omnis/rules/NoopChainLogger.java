package com.verum.omnis.rules;

import android.util.Log;

import java.util.Map;

/**
 * No-op implementation of ChainLogger.
 * Records events to logcat for now.
 */
public class NoopChainLogger implements ChainLogger {

    private static final String TAG = "ChainLogger";

    @Override
    public void record(String eventType, String caseId, String payloadHash, Map<String, String> meta) {
        Log.d(TAG, "ChainLogger event → type=" + eventType
                + " caseId=" + caseId
                + " hash=" + payloadHash
                + " meta=" + (meta != null ? meta.toString() : "{}"));
    }
}
