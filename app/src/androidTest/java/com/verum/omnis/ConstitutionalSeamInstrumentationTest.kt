package com.verum.omnis

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.verum.omnis.core.BoundedRenderSettings
import com.verum.omnis.core.AnalysisCoordinator
import com.verum.omnis.core.AnalysisEngine
import com.verum.omnis.core.AnalyzeCaseUseCase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ConstitutionalSeamInstrumentationTest {

    @Test(timeout = 900_000)
    fun fullAnalysisFlow_runsThroughCoordinatorThenGovernedPublication_andWritesSealedArtifacts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val evidenceFile = copyAssetToInternalStorage(context, "simple_contract_case.pdf")

        withConfiguredActivity(evidenceFile) { harness ->
            val orderedCalls = mutableListOf<String>()
            val analysisGateway = RecordingAnalysisGateway(harness.analysisGateway, orderedCalls)
            val publicationGateway = RecordingAnalyzeGateway(harness.analyzeGateway, orderedCalls)

            val analysisResult = AnalysisCoordinator().run(
                AnalysisCoordinator.Request().apply {
                    evidenceFiles = listOf(evidenceFile)
                    primaryEvidenceFile = evidenceFile
                },
                analysisGateway
            )

            val useCaseResult = runPublicationFlow(
                analysisResult = analysisResult,
                mode = AnalyzeCaseUseCase.Mode.FULL_SCAN,
                includeLegalAdvisory = false,
                gateway = publicationGateway
            )

            assertTrue(
                "Expected deterministic coordinator calls before governed publication. Actual order: $orderedCalls",
                orderedCalls.indexOf("analysis:buildFindingsPayload")
                        < orderedCalls.indexOf("publication:buildAuditReport")
            )
            assertTrue(
                "Expected governed packet to be prepared before any human report write. Actual order: $orderedCalls",
                orderedCalls.indexOf("publication:generateHumanReadableReport")
                        < orderedCalls.indexOf("publication:writeForensicReportToVault")
            )
            assertEquals("", publicationGateway.readableBriefLegalAdvisorySeen)
            assertNotNull(useCaseResult.auditorPdf)
            assertNotNull(useCaseResult.forensicPdf)
            assertNotNull(useCaseResult.readableBriefFile)
            assertNotNull(useCaseResult.policeReadyReportFile)
            assertNotNull(useCaseResult.constitutionalNarrativeFile)
            assertNotNull(useCaseResult.contradictionEngineFile)
            assertNotNull(useCaseResult.visualFindingsFile)
            assertExistingArtifactWithSeal(useCaseResult.auditorPdf)
            assertExistingArtifactWithSeal(useCaseResult.forensicPdf)
            assertExistingArtifactWithSeal(useCaseResult.readableBriefFile)
            assertExistingArtifactWithSeal(useCaseResult.policeReadyReportFile)
            assertExistingArtifactWithSeal(useCaseResult.constitutionalNarrativeFile)
            assertExistingArtifactWithSeal(useCaseResult.contradictionEngineFile)
            assertExistingArtifactWithSeal(useCaseResult.visualFindingsFile)

            val pendingNarrative = loadPendingGemmaNarrative(context)
            assertTrue(
                "Expected deferred Gemma prompt to be built from a constitutional packet.",
                pendingNarrative.optString("gemmaPrompt").contains("Constitutional Narrative Packet")
            )
            assertTrue(
                "Expected deferred Gemma prompt to carry deterministic engine output.",
                pendingNarrative.optString("gemmaPrompt").contains("Deterministic Engine Output")
            )
        }
    }

    @Test(timeout = 900_000)
    fun pdfOnlyFlow_reusesGovernedPublicationPath_andKeepsSharedOutputsStable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val evidenceFile = copyAssetToInternalStorage(context, "simple_contract_case.pdf")

        withConfiguredActivity(evidenceFile) { harness ->
            val analysisResult = AnalysisCoordinator().run(
                AnalysisCoordinator.Request().apply {
                    evidenceFiles = listOf(evidenceFile)
                    primaryEvidenceFile = evidenceFile
                },
                harness.analysisGateway
            )

            val fullGateway = RecordingAnalyzeGateway(harness.analyzeGateway, mutableListOf())
            val fullResult = runPublicationFlow(
                analysisResult = analysisResult,
                mode = AnalyzeCaseUseCase.Mode.FULL_SCAN,
                includeLegalAdvisory = false,
                gateway = fullGateway
            )

            val pdfGateway = RecordingAnalyzeGateway(harness.analyzeGateway, mutableListOf())
            val pdfOnlyResult = runPublicationFlow(
                analysisResult = analysisResult,
                mode = AnalyzeCaseUseCase.Mode.PDF_ONLY,
                includeLegalAdvisory = false,
                gateway = pdfGateway
            )

            assertEquals(fullResult.auditReport, pdfOnlyResult.auditReport)
            assertEquals(fullResult.humanReadableReport, pdfOnlyResult.humanReadableReport)
            assertEquals(fullResult.readableBriefReport, pdfOnlyResult.readableBriefReport)
            assertEquals(fullResult.policeReadyReport, pdfOnlyResult.policeReadyReport)
            assertEquals(fullResult.constitutionalNarrativeReport, pdfOnlyResult.constitutionalNarrativeReport)
            assertNull(pdfOnlyResult.contradictionEngineFile)
            assertNull(pdfOnlyResult.legalAdvisoryFile)
            assertFalse(pdfOnlyResult.vaultReferences.containsKey("contradictionEnginePath"))
            assertFalse(pdfOnlyResult.vaultReferences.containsKey("legalAdvisoryPath"))
            assertTrue(
                "PDF-only flow should still use the governed publication path.",
                pdfGateway.calls.contains("publication:generateHumanReadableReport")
                        && pdfGateway.calls.contains("publication:generateReadableFindingsBriefReport")
                        && pdfGateway.calls.contains("publication:generateConstitutionalVaultReport")
            )
            assertTrue(pdfOnlyResult.auditorPdf?.name?.contains("forensic-audit-report") == true)
            assertTrue(pdfOnlyResult.forensicPdf?.name?.contains("human-forensic-report") == true)
            assertTrue(pdfOnlyResult.readableBriefFile?.name?.contains("readable-forensic-brief") == true)
        }
    }

    @Test(timeout = 120_000)
    fun readableBriefGeneration_usesGovernedInputsOnly_andNeverReceivesLegalAdvisorySource() {
        val useCase = AnalyzeCaseUseCase()
        val gateway = ToggleAwareGateway(modelsEnabled = false)

        val result = useCase.run(
            AnalyzeCaseUseCase.Request().apply {
                mode = AnalyzeCaseUseCase.Mode.FULL_SCAN
                report = buildSyntheticReport()
                findingsPayload = JSONObject().put("seed", "android-runtime")
                integrityResults = linkedMapOf("hash" to "ok")
                primaryEvidenceMeta = linkedMapOf("mime" to "application/pdf")
                includeLegalAdvisory = true
            },
            gateway
        )

        assertEquals("", gateway.readableBriefLegalAdvisorySeen)
        assertTrue(gateway.readableBriefFindingsJsonSeen.contains("\"seed\": \"android-runtime\""))
        assertTrue(result.readableBriefReport.contains("READABLE BRIEF"))
        assertFalse(result.readableBriefReport.contains("LEGAL ADVISORY"))
    }

    @Test(timeout = 120_000)
    fun policeReadyGeneration_keepsExplicitNonVerdictBoundary_underAndroidRuntime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val evidenceFile = copyAssetToInternalStorage(context, "simple_contract_case.pdf")

        withConfiguredActivity(evidenceFile) { harness ->
            val analysisResult = AnalysisCoordinator().run(
                AnalysisCoordinator.Request().apply {
                    evidenceFiles = listOf(evidenceFile)
                    primaryEvidenceFile = evidenceFile
                },
                harness.analysisGateway
            )

            val policeReadyReport = harness.analyzeGateway.generatePoliceReadyReport(analysisResult.report)
            assertTrue(policeReadyReport.contains("This is a forensic conclusion, not a judicial verdict."))
            assertFalse(policeReadyReport.lowercase().contains("is guilty"))
            assertFalse(policeReadyReport.lowercase().contains("convicted"))
        }
    }

    @Test(timeout = 120_000)
    fun vaultPersistence_createsExpectedArtifactsAndSealManifests_endToEnd() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val evidenceFile = copyAssetToInternalStorage(context, "simple_contract_case.pdf")

        withConfiguredActivity(evidenceFile) { harness ->
            val analysisResult = AnalysisCoordinator().run(
                AnalysisCoordinator.Request().apply {
                    evidenceFiles = listOf(evidenceFile)
                    primaryEvidenceFile = evidenceFile
                },
                harness.analysisGateway
            )

            val publicationGateway = RecordingAnalyzeGateway(harness.analyzeGateway, mutableListOf())
            val result = runPublicationFlow(
                analysisResult = analysisResult,
                mode = AnalyzeCaseUseCase.Mode.FULL_SCAN,
                includeLegalAdvisory = false,
                gateway = publicationGateway
            )

            val persisted = listOfNotNull(
                result.auditorPdf,
                result.forensicPdf,
                result.readableBriefFile,
                result.policeReadyReportFile,
                result.constitutionalNarrativeFile,
                result.contradictionEngineFile,
                result.visualFindingsFile
            )
            assertTrue("Expected multiple vault artifacts to be persisted.", persisted.size >= 7)
            persisted.forEach { artifact ->
                assertExistingArtifactWithSeal(artifact)
                assertTrue(
                    "Expected vault artifact to live under the vault directory: ${artifact.absolutePath}",
                    artifact.parentFile?.name == "vault"
                )
            }
            assertTrue(
                "Expected human-facing artifacts to be persisted only after governed generation calls.",
                publicationGateway.calls.indexOf("publication:generateHumanReadableReport")
                        < publicationGateway.calls.indexOf("publication:writeForensicReportToVault")
            )
            assertTrue(
                "Expected readable brief to be persisted only after governed generation calls.",
                publicationGateway.calls.indexOf("publication:generateReadableFindingsBriefReport")
                        < publicationGateway.calls.indexOf("publication:writeReadableFindingsBriefToVault")
            )
        }
    }

    @Test(timeout = 120_000)
    fun modelToggleRegression_changesInterpretiveWordingOnly_andLeavesDeterministicInputsStable() {
        val useCase = AnalyzeCaseUseCase()
        val request = AnalyzeCaseUseCase.Request().apply {
            mode = AnalyzeCaseUseCase.Mode.FULL_SCAN
            report = buildSyntheticReport()
            findingsPayload = JSONObject().put("seed", "stable-packet")
            integrityResults = linkedMapOf("hash" to "ok")
            primaryEvidenceMeta = linkedMapOf("mime" to "application/pdf")
            includeLegalAdvisory = true
        }

        val modelsOffGateway = ToggleAwareGateway(modelsEnabled = false)
        val modelsOffResult = useCase.run(request, modelsOffGateway)

        val modelsOnGateway = ToggleAwareGateway(modelsEnabled = true)
        val modelsOnResult = useCase.run(request, modelsOnGateway)

        assertEquals(modelsOffGateway.humanReadableFindingsJsonSeen, modelsOnGateway.humanReadableFindingsJsonSeen)
        assertEquals(modelsOffGateway.readableBriefFindingsJsonSeen, modelsOnGateway.readableBriefFindingsJsonSeen)
        assertEquals(modelsOffResult.auditReport, modelsOnResult.auditReport)
        assertEquals(modelsOffResult.policeReadyReport, modelsOnResult.policeReadyReport)
        assertEquals(modelsOffResult.constitutionalNarrativeReport, modelsOnResult.constitutionalNarrativeReport)
        assertNotEquals(modelsOffResult.humanReadableReport, modelsOnResult.humanReadableReport)
        assertNotEquals(modelsOffResult.readableBriefReport, modelsOnResult.readableBriefReport)
        assertEquals("", modelsOffGateway.readableBriefLegalAdvisorySeen)
        assertEquals("", modelsOnGateway.readableBriefLegalAdvisorySeen)
    }

    private fun runPublicationFlow(
        analysisResult: AnalysisCoordinator.Result,
        mode: AnalyzeCaseUseCase.Mode,
        includeLegalAdvisory: Boolean,
        gateway: AnalyzeCaseUseCase.Gateway
    ): AnalyzeCaseUseCase.Result {
        return AnalyzeCaseUseCase().run(
            AnalyzeCaseUseCase.Request().apply {
                this.mode = mode
                report = analysisResult.report
                integrityResults = LinkedHashMap(analysisResult.integrityResults)
                primaryEvidenceMeta = LinkedHashMap(analysisResult.primaryEvidenceMeta)
                findingsPayload = JSONObject(analysisResult.findingsPayload.toString())
                this.includeLegalAdvisory = includeLegalAdvisory
                boundedHumanBriefEnabled = false
                boundedPoliceSummaryEnabled = false
                boundedLegalStandingEnabled = false
                boundedRenderAuditRequired = true
                boundedRenderFailClosed = true
            },
            gateway
        )
    }

    private fun withConfiguredActivity(
        evidenceFile: File,
        block: (ActivityHarness) -> Unit
    ) {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val harnessRef = AtomicReference<ActivityHarness>()
            scenario.onActivity { activity ->
                setPrivateField(activity, "selectedFile", evidenceFile)
                setPrivateField(activity, "includeLegalAdvisoryRequested", false)
                BoundedRenderSettings().persist(activity.applicationContext)
                File(activity.filesDir, PENDING_GEMMA_REPORT_FILE).delete()
                harnessRef.set(
                    ActivityHarness(
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

    private fun assertExistingArtifactWithSeal(file: File?) {
        assertNotNull(file)
        assertTrue("Expected persisted artifact file.", file!!.exists())
        assertTrue("Expected persisted artifact to be non-empty.", file.length() > 0L)
        val sealFile = File(file.parentFile, "${file.name.substringBeforeLast('.')}.seal.json")
        assertTrue("Expected seal manifest for ${file.name}", sealFile.exists())
        assertTrue("Expected non-empty seal manifest for ${file.name}", sealFile.length() > 0L)
    }

    private fun loadPendingGemmaNarrative(context: Context): JSONObject {
        val file = File(context.filesDir, PENDING_GEMMA_REPORT_FILE)
        assertTrue("Expected pending Gemma narrative file to exist.", file.exists())
        return JSONObject(file.readText(StandardCharsets.UTF_8))
    }

    private fun copyAssetToInternalStorage(context: Context, assetName: String): File {
        val sourceContext = InstrumentationRegistry.getInstrumentation().context
        val stem = assetName.substringBeforeLast('.')
        val ext = assetName.substringAfterLast('.', "")
        val destFile = File(
            context.filesDir,
            "instrumentation-${stem}-${System.nanoTime()}.${ext}"
        )
        sourceContext.assets.open(assetName).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        assertTrue(destFile.exists() && destFile.length() > 0L)
        return destFile
    }

    private fun buildSyntheticReport(): AnalysisEngine.ForensicReport {
        return AnalysisEngine.ForensicReport().apply {
            caseId = "case-instrumentation"
            evidenceHash = "evidence-hash"
            evidenceHashShort = "evidence-hash-short"
            blockchainAnchor = "anchor"
            jurisdiction = "ZA"
            jurisdictionName = "South Africa"
            summary = "Synthetic report for instrumentation seam testing."
            generatedAt = "2026-04-12T10:00:00Z"
        }
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

    private data class ActivityHarness(
        val analysisGateway: AnalysisCoordinator.Gateway,
        val analyzeGateway: AnalyzeCaseUseCase.Gateway
    )

    private class RecordingAnalysisGateway(
        private val delegate: AnalysisCoordinator.Gateway,
        private val order: MutableList<String>
    ) : AnalysisCoordinator.Gateway {

        override fun runIntegrityChecks(): MutableMap<String, String> {
            order += "analysis:runIntegrityChecks"
            return LinkedHashMap(delegate.runIntegrityChecks())
        }

        override fun analyzeEvidenceSet(evidenceFiles: MutableList<File>?): AnalysisEngine.ForensicReport {
            order += "analysis:analyzeEvidenceSet"
            return delegate.analyzeEvidenceSet(evidenceFiles)
        }

        override fun analyzeSingleEvidence(evidenceFile: File): AnalysisEngine.ForensicReport {
            order += "analysis:analyzeSingleEvidence"
            return delegate.analyzeSingleEvidence(evidenceFile)
        }

        override fun applyInvestigatorContext(report: AnalysisEngine.ForensicReport) {
            order += "analysis:applyInvestigatorContext"
            delegate.applyInvestigatorContext(report)
        }

        override fun inspectPrimaryEvidence(primaryEvidenceFile: File): MutableMap<String, String> {
            order += "analysis:inspectPrimaryEvidence"
            return LinkedHashMap(delegate.inspectPrimaryEvidence(primaryEvidenceFile))
        }

        override fun buildFindingsPayload(
            primaryEvidenceFile: File,
            report: AnalysisEngine.ForensicReport
        ): JSONObject {
            order += "analysis:buildFindingsPayload"
            return delegate.buildFindingsPayload(primaryEvidenceFile, report)
        }
    }

    private class RecordingAnalyzeGateway(
        private val delegate: AnalyzeCaseUseCase.Gateway,
        val calls: MutableList<String>
    ) : AnalyzeCaseUseCase.Gateway {

        var readableBriefLegalAdvisorySeen: String = ""

        override fun buildAuditReport(
            report: AnalysisEngine.ForensicReport,
            integrityResults: MutableMap<String, String>?
        ): String {
            calls += "publication:buildAuditReport"
            return delegate.buildAuditReport(report, integrityResults)
        }

        override fun generateHumanReadableReport(
            report: AnalysisEngine.ForensicReport,
            integrityResults: MutableMap<String, String>?,
            findingsJson: String,
            auditReport: String
        ): String {
            calls += "publication:generateHumanReadableReport"
            return delegate.generateHumanReadableReport(report, integrityResults, findingsJson, auditReport)
        }

        override fun getApplicationContext(): Context {
            return delegate.applicationContext
        }

        override fun buildLegacyLegalAdvisory(report: AnalysisEngine.ForensicReport): String {
            calls += "publication:buildLegacyLegalAdvisory"
            return delegate.buildLegacyLegalAdvisory(report)
        }

        override fun appendLegalAdvisorySection(humanReadableReport: String, legalAdvisory: String): String {
            calls += "publication:appendLegalAdvisorySection"
            return delegate.appendLegalAdvisorySection(humanReadableReport, legalAdvisory)
        }

        override fun writeModelAuditLedgerToVault(
            report: AnalysisEngine.ForensicReport,
            modelAuditLedger: String
        ): File? {
            calls += "publication:writeModelAuditLedgerToVault"
            return delegate.writeModelAuditLedgerToVault(report, modelAuditLedger)
        }

        override fun buildVisualFindingsMemo(report: AnalysisEngine.ForensicReport): String {
            calls += "publication:buildVisualFindingsMemo"
            return delegate.buildVisualFindingsMemo(report)
        }

        override fun generateReadableFindingsBriefReport(
            report: AnalysisEngine.ForensicReport,
            findingsJson: String,
            auditReport: String,
            humanReadableReport: String,
            legalAdvisory: String,
            visualFindingsMemo: String
        ): String {
            calls += "publication:generateReadableFindingsBriefReport"
            readableBriefLegalAdvisorySeen = legalAdvisory
            return delegate.generateReadableFindingsBriefReport(
                report,
                findingsJson,
                auditReport,
                humanReadableReport,
                legalAdvisory,
                visualFindingsMemo
            )
        }

        override fun generatePoliceReadyReport(report: AnalysisEngine.ForensicReport): String {
            calls += "publication:generatePoliceReadyReport"
            return delegate.generatePoliceReadyReport(report)
        }

        override fun generateConstitutionalVaultReport(
            report: AnalysisEngine.ForensicReport,
            integrityResults: MutableMap<String, String>?,
            primaryEvidenceMeta: MutableMap<String, String>?,
            auditReport: String,
            humanReadableReport: String,
            readableBriefReport: String
        ): String {
            calls += "publication:generateConstitutionalVaultReport"
            return delegate.generateConstitutionalVaultReport(
                report,
                integrityResults,
                primaryEvidenceMeta,
                auditReport,
                humanReadableReport,
                readableBriefReport
            )
        }

        override fun generateContradictionEngineReport(report: AnalysisEngine.ForensicReport): String {
            calls += "publication:generateContradictionEngineReport"
            return delegate.generateContradictionEngineReport(report)
        }

        override fun writeAuditReportToVault(report: AnalysisEngine.ForensicReport, auditReport: String): File? {
            calls += "publication:writeAuditReportToVault"
            return delegate.writeAuditReportToVault(report, auditReport)
        }

        override fun writeForensicReportToVault(
            report: AnalysisEngine.ForensicReport,
            humanReadableReport: String
        ): File? {
            calls += "publication:writeForensicReportToVault"
            return delegate.writeForensicReportToVault(report, humanReadableReport)
        }

        override fun writeReadableFindingsBriefToVault(
            report: AnalysisEngine.ForensicReport,
            readableBriefReport: String
        ): File? {
            calls += "publication:writeReadableFindingsBriefToVault"
            return delegate.writeReadableFindingsBriefToVault(report, readableBriefReport)
        }

        override fun writePoliceReadyReportToVault(
            report: AnalysisEngine.ForensicReport,
            policeReadyReport: String
        ): File? {
            calls += "publication:writePoliceReadyReportToVault"
            return delegate.writePoliceReadyReportToVault(report, policeReadyReport)
        }

        override fun writeConstitutionalVaultReportToVault(
            report: AnalysisEngine.ForensicReport,
            constitutionalNarrativeReport: String
        ): File? {
            calls += "publication:writeConstitutionalVaultReportToVault"
            return delegate.writeConstitutionalVaultReportToVault(report, constitutionalNarrativeReport)
        }

        override fun writeContradictionEngineReportToVault(
            report: AnalysisEngine.ForensicReport,
            contradictionEngineReport: String
        ): File? {
            calls += "publication:writeContradictionEngineReportToVault"
            return delegate.writeContradictionEngineReportToVault(report, contradictionEngineReport)
        }

        override fun writeLegalAdvisoryToVault(report: AnalysisEngine.ForensicReport, legalAdvisory: String): File? {
            calls += "publication:writeLegalAdvisoryToVault"
            return delegate.writeLegalAdvisoryToVault(report, legalAdvisory)
        }

        override fun writeVisualFindingsMemoToVault(
            report: AnalysisEngine.ForensicReport,
            visualFindingsMemo: String
        ): File? {
            calls += "publication:writeVisualFindingsMemoToVault"
            return delegate.writeVisualFindingsMemoToVault(report, visualFindingsMemo)
        }
    }

    private class ToggleAwareGateway(
        private val modelsEnabled: Boolean
    ) : AnalyzeCaseUseCase.Gateway {

        var humanReadableFindingsJsonSeen: String = ""
        var readableBriefFindingsJsonSeen: String = ""
        var readableBriefLegalAdvisorySeen: String = ""

        override fun buildAuditReport(
            report: AnalysisEngine.ForensicReport,
            integrityResults: MutableMap<String, String>?
        ): String {
            return "AUDIT REPORT"
        }

        override fun generateHumanReadableReport(
            report: AnalysisEngine.ForensicReport,
            integrityResults: MutableMap<String, String>?,
            findingsJson: String,
            auditReport: String
        ): String {
            humanReadableFindingsJsonSeen = findingsJson
            return if (modelsEnabled) "HUMAN REPORT - MODELS ON" else "HUMAN REPORT - MODELS OFF"
        }

        override fun getApplicationContext(): Context {
            return InstrumentationRegistry.getInstrumentation().targetContext
        }

        override fun buildLegacyLegalAdvisory(report: AnalysisEngine.ForensicReport): String {
            return if (modelsEnabled) "LEGAL ADVISORY - MODELS ON" else ""
        }

        override fun appendLegalAdvisorySection(humanReadableReport: String, legalAdvisory: String): String {
            return if (legalAdvisory.isBlank()) humanReadableReport else "$humanReadableReport\n\n$legalAdvisory"
        }

        override fun writeModelAuditLedgerToVault(
            report: AnalysisEngine.ForensicReport,
            modelAuditLedger: String
        ): File? {
            return null
        }

        override fun buildVisualFindingsMemo(report: AnalysisEngine.ForensicReport): String {
            return "VISUAL MEMO"
        }

        override fun generateReadableFindingsBriefReport(
            report: AnalysisEngine.ForensicReport,
            findingsJson: String,
            auditReport: String,
            humanReadableReport: String,
            legalAdvisory: String,
            visualFindingsMemo: String
        ): String {
            readableBriefFindingsJsonSeen = findingsJson
            readableBriefLegalAdvisorySeen = legalAdvisory
            return if (modelsEnabled) "READABLE BRIEF - MODELS ON" else "READABLE BRIEF - MODELS OFF"
        }

        override fun generatePoliceReadyReport(report: AnalysisEngine.ForensicReport): String {
            return "This is a forensic conclusion, not a judicial verdict."
        }

        override fun generateConstitutionalVaultReport(
            report: AnalysisEngine.ForensicReport,
            integrityResults: MutableMap<String, String>?,
            primaryEvidenceMeta: MutableMap<String, String>?,
            auditReport: String,
            humanReadableReport: String,
            readableBriefReport: String
        ): String {
            return "CONSTITUTIONAL VAULT REPORT"
        }

        override fun generateContradictionEngineReport(report: AnalysisEngine.ForensicReport): String {
            return "CONTRADICTION REPORT"
        }

        override fun writeAuditReportToVault(report: AnalysisEngine.ForensicReport, auditReport: String): File {
            return fakeFile("audit.pdf")
        }

        override fun writeForensicReportToVault(
            report: AnalysisEngine.ForensicReport,
            humanReadableReport: String
        ): File {
            return fakeFile("human.pdf")
        }

        override fun writeReadableFindingsBriefToVault(
            report: AnalysisEngine.ForensicReport,
            readableBriefReport: String
        ): File {
            return fakeFile("brief.pdf")
        }

        override fun writePoliceReadyReportToVault(
            report: AnalysisEngine.ForensicReport,
            policeReadyReport: String
        ): File {
            return fakeFile("police.pdf")
        }

        override fun writeConstitutionalVaultReportToVault(
            report: AnalysisEngine.ForensicReport,
            constitutionalNarrativeReport: String
        ): File {
            return fakeFile("vault.pdf")
        }

        override fun writeContradictionEngineReportToVault(
            report: AnalysisEngine.ForensicReport,
            contradictionEngineReport: String
        ): File {
            return fakeFile("contradiction.pdf")
        }

        override fun writeLegalAdvisoryToVault(report: AnalysisEngine.ForensicReport, legalAdvisory: String): File {
            return fakeFile("legal.pdf")
        }

        override fun writeVisualFindingsMemoToVault(
            report: AnalysisEngine.ForensicReport,
            visualFindingsMemo: String
        ): File {
            return fakeFile("visual.pdf")
        }

        private fun fakeFile(name: String): File {
            return File(name)
        }
    }

    private fun assertNotEquals(first: String, second: String) {
        assertFalse("Expected values to differ but both were <$first>.", first == second)
    }

    companion object {
        private const val PENDING_GEMMA_REPORT_FILE = "pending_gemma_report.json"
    }
}
