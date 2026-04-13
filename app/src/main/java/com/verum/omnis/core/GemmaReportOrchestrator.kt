package com.verum.omnis.core

import android.content.Context
import org.json.JSONObject

class GemmaReportOrchestrator(
    private val gemma4: LocalModelEngine,
    private val gemma2: LocalModelEngine
) {

    data class BoundedFeatureFlags(
        val humanBriefBoundedEnabled: Boolean = false,
        val policeSummaryBoundedEnabled: Boolean = false,
        val legalStandingBoundedEnabled: Boolean = false,
        val boundedRenderAuditRequired: Boolean = true,
        val boundedRenderFailClosed: Boolean = true
    )

    fun render(bundle: ReportRenderInput): RenderedReport {
        val humanBrief = renderSection(Prompts.HUMAN_BRIEF_PROMPT, bundle)
        val legalStanding = renderSection(Prompts.LEGAL_STANDING_PROMPT, bundle)
        val policeSummary = renderSection(Prompts.POLICE_SUMMARY_PROMPT, bundle)
        val combined = listOf(humanBrief, legalStanding, policeSummary).joinToString("\n\n")
        val audit = auditReport(ReportAuditInput(combined, bundle))

        return RenderedReport(
            humanBrief = humanBrief,
            legalStanding = legalStanding,
            policeSummary = policeSummary,
            auditPassed = audit.auditPassed,
            auditNotes = audit.notes
        )
    }

    private fun renderSection(prompt: String, input: ReportRenderInput): String {
        val response = gemma4.run(
            ModelRequest(
                role = ModelRole.REPORT_RENDER,
                prompt = prompt,
                inputJson = input.toJson(),
                maxTokens = 1400,
                temperature = 0.1f
            )
        )
        if (!response.ok) error(response.error ?: "Gemma 4 render failed")
        return ReportSectionOutput.fromJson(response.rawText).report
    }

    private fun auditReport(input: ReportAuditInput): ReportAuditOutput {
        val response = gemma2.run(
            ModelRequest(
                role = ModelRole.REPORT_AUDIT,
                prompt = Prompts.REPORT_AUDIT_PROMPT,
                inputJson = input.toJson(),
                maxTokens = 900,
                temperature = 0f
            )
        )
        if (!response.ok) {
            return ReportAuditOutput(
                auditPassed = false,
                notes = listOf(response.error ?: "Gemma 2 audit failed")
            )
        }
        return ReportAuditOutput.fromJson(response.rawText)
    }

    companion object {
        @Volatile
        private var testFactoryOverride: ((Context) -> GemmaReportOrchestrator)? = null

        @JvmStatic
        fun create(context: Context): GemmaReportOrchestrator {
            testFactoryOverride?.let { factory ->
                return factory(context.applicationContext)
            }
            return GemmaReportOrchestrator(
                gemma4 = Gemma4Engine(context.applicationContext),
                gemma2 = Gemma2AuditEngine(context.applicationContext)
            )
        }

        @JvmStatic
        fun installTestFactory(factory: ((Context) -> GemmaReportOrchestrator)?) {
            testFactoryOverride = factory
        }

        @JvmStatic
        fun renderBoundedHumanBriefOnly(
            context: Context?,
            report: AnalysisEngine.ForensicReport,
            flags: BoundedFeatureFlags,
            legacyFallback: String
        ): String {
            val bundleFactory = ConstitutionalReportBundleFactory()
            return try {
                val appContext = context?.applicationContext
                if (!flags.humanBriefBoundedEnabled || appContext == null) {
                    legacyFallback
                } else {
                    val orchestrator = create(appContext)
                    val bundle = bundleFactory.build(report)
                    val renderResponse = orchestrator.gemma4.run(
                        ModelRequest(
                            role = ModelRole.REPORT_RENDER,
                            prompt = Prompts.HUMAN_BRIEF_PROMPT,
                            inputJson = bundle.toJson(),
                            maxTokens = 1400,
                            temperature = 0.1f
                        )
                    )
                    val humanBrief = if (renderResponse.ok) {
                        ReportSectionOutput.fromJson(renderResponse.rawText).report
                    } else {
                        ""
                    }
                    val policeSummary = if (flags.policeSummaryBoundedEnabled) {
                        orchestrator.renderSection(Prompts.POLICE_SUMMARY_PROMPT, bundle)
                    } else {
                        ""
                    }
                    val legalStanding = if (flags.legalStandingBoundedEnabled) {
                        orchestrator.renderSection(Prompts.LEGAL_STANDING_PROMPT, bundle)
                    } else {
                        ""
                    }
                    val combined = listOf(humanBrief, legalStanding, policeSummary)
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")
                    val audit = orchestrator.auditReport(ReportAuditInput(combined, bundle))
                    val auditAccepted = if (flags.boundedRenderAuditRequired) audit.auditPassed else true
                    val success = renderResponse.ok && auditAccepted && humanBrief.isNotBlank()
                    val output = if (success) {
                        buildCombinedReport(
                            RenderedReport(
                                humanBrief = humanBrief,
                                legalStanding = legalStanding,
                                policeSummary = policeSummary,
                                auditPassed = audit.auditPassed,
                                auditNotes = audit.notes
                            ),
                            bundle
                        )
                    } else {
                        if (flags.boundedRenderFailClosed) legacyFallback else humanBrief.ifBlank { legacyFallback }
                    }
                    ModelAuditLogger.log(
                        appContext,
                        ModelAuditLogger.buildEntry(
                            caseId = report.caseId.orEmpty(),
                            model = renderResponse.modelName,
                            role = "REPORT_RENDER_HUMAN_BRIEF",
                            inputJson = bundle.toJson(),
                            outputText = output,
                            success = success,
                            latencyMs = renderResponse.latencyMs,
                            promptVersion = "HUMAN_BRIEF_V1",
                            fallbackUsed = !success,
                            notes = audit.notes
                        )
                    )
                    ModelAuditLogger.log(
                        appContext,
                        ModelAuditLogger.buildEntry(
                            caseId = report.caseId.orEmpty(),
                            model = if (orchestrator.gemma2.isAvailable()) "gemma-2-audit" else "gemma-2-audit-unavailable",
                            role = "REPORT_AUDIT",
                            inputJson = ReportAuditInput(combined, bundle).toJson(),
                            outputText = audit.notes.joinToString(" | "),
                            success = audit.auditPassed,
                            latencyMs = 0L,
                            promptVersion = "REPORT_AUDIT_V1",
                            fallbackUsed = flags.boundedRenderFailClosed && !success,
                            notes = audit.notes
                        )
                    )
                    output
                }
            } catch (_: Throwable) {
                context?.applicationContext?.let { appContext ->
                    ModelAuditLogger.log(
                        appContext,
                        ModelAuditLogger.buildEntry(
                            caseId = report.caseId.orEmpty(),
                            model = "bounded-render-exception",
                            role = "REPORT_RENDER_HUMAN_BRIEF",
                            inputJson = report.caseId.orEmpty(),
                            outputText = legacyFallback,
                            success = false,
                            latencyMs = 0L,
                            promptVersion = "HUMAN_BRIEF_V1",
                            fallbackUsed = true,
                            notes = listOf("Bounded render threw and legacy fallback was used.")
                        )
                    )
                }
                legacyFallback
            }
        }

        @JvmStatic
        fun renderLegacyFallback(
            context: Context,
            report: AnalysisEngine.ForensicReport
        ): String {
            val legacy = LegalAttorneyAnalyzer.analyze(context.applicationContext, report)
            val analysis = legacy.optString("analysis", "").trim()
            val nextSteps = legacy.optJSONArray("nextSteps")
            val confidence = legacy.optString("confidence", "").trim()
            val boundary = legacy.optString(
                "boundary",
                "This section remains downstream from the forensic engine and subordinate to the sealed record."
            ).trim()
            return buildString {
                append("HUMAN NARRATIVE LAYER [INTERPRETIVE]\n")
                append("LEGAL ADVISORY LAYER [INTERPRETIVE]\n\n")
                if (analysis.isNotEmpty()) {
                    append(analysis).append("\n\n")
                }
                if (nextSteps != null && nextSteps.length() > 0) {
                    append("NEXT PROCEDURAL STEPS\n")
                    for (index in 0 until nextSteps.length()) {
                        val step = nextSteps.optString(index, "").trim()
                        if (step.isNotEmpty()) {
                            append("- ").append(step).append("\n")
                        }
                    }
                    append("\n")
                }
                if (confidence.isNotEmpty()) {
                    append("Confidence: ").append(confidence).append("\n")
                }
                append(boundary)
            }.trim()
        }

        private fun buildCombinedReport(rendered: RenderedReport, bundle: ReportRenderInput): String {
            return buildString {
                append("HUMAN NARRATIVE LAYER [INTERPRETIVE]\n")
                append("LEGAL ADVISORY LAYER [INTERPRETIVE]\n")
                append("CONSTITUTIONAL BOUNDARY [VISIBLE]\n\n")
                append(rendered.humanBrief.trim()).append("\n\n")
                append(rendered.legalStanding.trim()).append("\n\n")
                append(rendered.policeSummary.trim()).append("\n\n")
                append("Audit passed: ").append(if (rendered.auditPassed) "YES" else "NO").append("\n")
                if (rendered.auditNotes.isNotEmpty()) {
                    append("Audit notes:\n")
                    rendered.auditNotes.forEach { note ->
                        if (note.isNotBlank()) {
                            append("- ").append(note.trim()).append("\n")
                        }
                    }
                }
                append(bundle.boundaryNote).append("\n")
                append("This section is downstream interpretive output generated from governed findings only.\n")
                append("It remains subordinate to the sealed record, certified findings, and contradiction ledger.\n")
                append("It does not constitute a judicial finding or engine-certified verdict.")
            }.trim()
        }
    }
}
