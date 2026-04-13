package com.verum.omnis

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.verum.omnis.core.AnalysisCoordinator
import com.verum.omnis.core.AnalysisEngine
import com.verum.omnis.core.AnalyzeCaseUseCase
import com.verum.omnis.core.BoundedRenderSettings
import com.verum.omnis.core.GemmaReportOrchestrator
import com.verum.omnis.core.LocalModelEngine
import com.verum.omnis.core.ModelRequest
import com.verum.omnis.core.ModelResponse
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class BoundedHumanBriefActivationInstrumentationTest {

    @After
    fun tearDown() {
        GemmaReportOrchestrator.installTestFactory(null)
    }

    @Test(timeout = 900_000)
    fun fixedCase_boundedHumanBriefSuccess_writesAppendixAndLedger() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val evidenceFile = copyAssetToInternalStorage(context, "simple_contract_case.pdf")

        GemmaReportOrchestrator.installTestFactory { appContext ->
            GemmaReportOrchestrator(
                gemma4 = FakeModelEngine(
                    """{"report":"What happened

Bounded human brief generated from governed findings only."}"""
                ),
                gemma2 = FakeModelEngine(
                    """{"auditPassed":true,"notes":["bounded pass"]}"""
                )
            )
        }

        withConfiguredActivity(context, evidenceFile) { harness ->
            val analysisResult = AnalysisCoordinator().run(
                AnalysisCoordinator.Request().apply {
                    evidenceFiles = listOf(evidenceFile)
                    primaryEvidenceFile = evidenceFile
                },
                harness.analysisGateway
            )

            val result = AnalyzeCaseUseCase().run(
                AnalyzeCaseUseCase.Request().apply {
                    mode = AnalyzeCaseUseCase.Mode.FULL_SCAN
                    report = analysisResult.report
                    integrityResults = LinkedHashMap(analysisResult.integrityResults)
                    primaryEvidenceMeta = LinkedHashMap(analysisResult.primaryEvidenceMeta)
                    findingsPayload = JSONObject(analysisResult.findingsPayload.toString())
                    includeLegalAdvisory = true
                    boundedHumanBriefEnabled = true
                    boundedPoliceSummaryEnabled = false
                    boundedLegalStandingEnabled = false
                    boundedRenderAuditRequired = true
                    boundedRenderFailClosed = true
                },
                harness.analyzeGateway
            )

            assertTrue(result.legalAdvisory.contains("HUMAN NARRATIVE LAYER [INTERPRETIVE]"))
            assertTrue(result.legalAdvisory.contains("Audit passed: YES"))
            assertTrue(result.legalAdvisory.contains("BOUNDED MODEL RENDERING RECORD"))
            assertNotNull(result.modelAuditLedgerFile)
            assertTrue(result.modelAuditLedgerFile!!.exists())
            val ledgerText = result.modelAuditLedgerFile!!.readText(StandardCharsets.UTF_8)
            assertTrue(ledgerText.contains("REPORT_RENDER_HUMAN_BRIEF"))
            assertTrue(ledgerText.contains("REPORT_AUDIT"))
            assertTrue(ledgerText.contains("bounded pass"))
        }
    }

    @Test(timeout = 900_000)
    fun fixedCase_boundedHumanBriefAuditRejection_fallsBackCleanly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val evidenceFile = copyAssetToInternalStorage(context, "simple_contract_case.pdf")

        GemmaReportOrchestrator.installTestFactory { appContext ->
            GemmaReportOrchestrator(
                gemma4 = FakeModelEngine(
                    """{"report":"What happened

This bounded output should be rejected by audit."}"""
                ),
                gemma2 = FakeModelEngine(
                    """{"auditPassed":false,"notes":["forced audit rejection"]}"""
                )
            )
        }

        withConfiguredActivity(context, evidenceFile) { harness ->
            val analysisResult = AnalysisCoordinator().run(
                AnalysisCoordinator.Request().apply {
                    evidenceFiles = listOf(evidenceFile)
                    primaryEvidenceFile = evidenceFile
                },
                harness.analysisGateway
            )

            val result = AnalyzeCaseUseCase().run(
                AnalyzeCaseUseCase.Request().apply {
                    mode = AnalyzeCaseUseCase.Mode.FULL_SCAN
                    report = analysisResult.report
                    integrityResults = LinkedHashMap(analysisResult.integrityResults)
                    primaryEvidenceMeta = LinkedHashMap(analysisResult.primaryEvidenceMeta)
                    findingsPayload = JSONObject(analysisResult.findingsPayload.toString())
                    includeLegalAdvisory = true
                    boundedHumanBriefEnabled = true
                    boundedPoliceSummaryEnabled = false
                    boundedLegalStandingEnabled = false
                    boundedRenderAuditRequired = true
                    boundedRenderFailClosed = true
                },
                harness.analyzeGateway
            )

            assertFalse(result.legalAdvisory.contains("Audit passed: YES"))
            assertTrue(result.legalAdvisory.contains("HUMAN NARRATIVE LAYER [INTERPRETIVE]"))
            assertTrue(result.legalAdvisory.contains("BOUNDED MODEL RENDERING RECORD"))
            assertNotNull(result.modelAuditLedgerFile)
            assertTrue(result.modelAuditLedgerFile!!.exists())
            val ledgerText = result.modelAuditLedgerFile!!.readText(StandardCharsets.UTF_8)
            assertTrue(ledgerText.contains("forced audit rejection"))
            assertTrue(ledgerText.contains("\"fallbackUsed\": true"))
        }
    }

    private fun withConfiguredActivity(
        context: Context,
        evidenceFile: File,
        block: (ActivationHarness) -> Unit
    ) {
        BoundedRenderSettings(
            boundedHumanBriefEnabled = true,
            boundedPoliceSummaryEnabled = false,
            boundedLegalStandingEnabled = false,
            boundedRenderAuditRequired = true,
            boundedRenderFailClosed = true
        ).persist(context)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val harnessRef = AtomicReference<ActivationHarness>()
            scenario.onActivity { activity ->
                setPrivateField(activity, "selectedFile", evidenceFile)
                setPrivateField(activity, "includeLegalAdvisoryRequested", true)
                harnessRef.set(
                    ActivationHarness(
                        analysisGateway = invokePrivateMethod(activity, "buildAnalysisCoordinatorGateway")
                                as AnalysisCoordinator.Gateway,
                        analyzeGateway = invokePrivateMethod(activity, "buildAnalyzeCaseGateway")
                                as AnalyzeCaseUseCase.Gateway
                    )
                )
            }
            block(harnessRef.get())
        } finally {
            scenario.close()
        }
    }

    private fun copyAssetToInternalStorage(context: Context, assetName: String): File {
        val sourceContext = InstrumentationRegistry.getInstrumentation().context
        val stem = assetName.substringBeforeLast('.')
        val ext = assetName.substringAfterLast('.', "")
        val destFile = File(
            context.filesDir,
            "activation-${stem}-${System.nanoTime()}.$ext"
        )
        sourceContext.assets.open(assetName).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    private fun invokePrivateMethod(target: Any, methodName: String): Any? {
        val method = target.javaClass.getDeclaredMethod(methodName)
        method.isAccessible = true
        return method.invoke(target)
    }

    private fun setPrivateField(target: Any, fieldName: String, value: Any?) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private data class ActivationHarness(
        val analysisGateway: AnalysisCoordinator.Gateway,
        val analyzeGateway: AnalyzeCaseUseCase.Gateway
    )

    private class FakeModelEngine(
        private val response: String
    ) : LocalModelEngine {
        override fun warmUp() = Unit
        override fun isAvailable(): Boolean = true
        override fun run(request: ModelRequest): ModelResponse {
            return ModelResponse(
                ok = true,
                modelName = "fake-bounded-model",
                rawText = response,
                latencyMs = 5L,
                error = null
            )
        }
    }
}
