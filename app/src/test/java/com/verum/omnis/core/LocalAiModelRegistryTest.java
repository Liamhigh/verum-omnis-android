package com.verum.omnis.core;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LocalAiModelRegistryTest {

    @Test
    public void supportedModelsIncludeGemmaAndPhi3Mini() {
        List<LocalAiModelRegistry.ModelDescriptor> supported = LocalAiModelRegistry.getSupportedModels();
        assertEquals(2, supported.size());
        assertEquals(LocalAiModelRegistry.MODEL_GEMMA_3_1B, supported.get(0).id);
        assertEquals(LocalAiModelRegistry.MODEL_PHI3_MINI, supported.get(1).id);
    }

    @Test
    public void defaultModelRemainsGemma() {
        LocalAiModelRegistry.ModelDescriptor descriptor = LocalAiModelRegistry.getDefaultModel();
        assertNotNull(descriptor);
        assertEquals(LocalAiModelRegistry.MODEL_GEMMA_3_1B, descriptor.id);
        assertTrue(descriptor.bundledByDefault);
    }

    @Test
    public void phi3MiniIsOptionalAndNamedClearly() {
        LocalAiModelRegistry.ModelDescriptor descriptor = LocalAiModelRegistry.get(LocalAiModelRegistry.MODEL_PHI3_MINI);
        assertNotNull(descriptor);
        assertFalse(descriptor.bundledByDefault);
        assertTrue(descriptor.displayName.contains("Phi-3 Mini"));
        assertEquals("Phi-3-mini-4k-instruct-q4.gguf", descriptor.fileName);
        assertEquals(LocalAiModelRegistry.Backend.EXTERNAL_GGUF, descriptor.backend);
    }
}
