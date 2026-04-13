package com.verum.omnis.core

import org.json.JSONArray
import org.json.JSONObject

class ConstitutionalReportBundleFactory {

    fun build(report: AnalysisEngine.ForensicReport): ReportRenderInput {
        val assembled = ForensicReportAssembler.assemble(report)
        val triple = report.tripleVerification ?: JSONObject()
        val overall = triple.optJSONObject("overall") ?: JSONObject()
        require(overall.optString("status", "PASS").equals("PASS", ignoreCase = true)) {
            "Triple verification must pass before report rendering."
        }

        var certified = assembled.certifiedFindings
            .filter {
                it.publicationStatus.equals("CERTIFIED", ignoreCase = true) ||
                    it.publicationStatus.isBlank()
            }
            .map {
                RenderCertifiedFinding(
                    actor = it.actor,
                    summary = it.summary,
                    primaryPage = it.primaryPage.takeIf { page -> page > 0 }
                        ?: it.anchorPages.firstOrNull()
                        ?: -1,
                    anchorPages = it.anchorPages.filter { page -> page > 0 },
                    contradictionStatus = it.contradictionStatus,
                    publicationStatus = if (it.publicationStatus.isBlank()) "CERTIFIED" else it.publicationStatus
                )
            }
        if (certified.isEmpty()) {
            certified = fallbackCertifiedFindings(report.normalizedCertifiedFindings)
        }

        return ReportRenderInput(
            caseId = report.caseId.orEmpty(),
            evidenceHash = report.evidenceHash.orEmpty(),
            engineVersion = report.engineVersion.orEmpty(),
            deterministicRunId = report.deterministicRunId.orEmpty(),
            jurisdiction = report.jurisdictionName.orEmpty(),
            tripleVerificationStatus = overall.optString("status", "PASS"),
            thesisReason = describeBranch(triple.optJSONObject("thesis")),
            antithesisReason = describeBranch(triple.optJSONObject("antithesis")),
            synthesisReason = describeBranch(triple.optJSONObject("synthesis")),
            tieBreaker = triple.optString("tieBreaker", "").ifBlank { null },
            certifiedFindings = certified,
            legalMappings = buildLegalMappings(report),
            legalIssueHints = buildLegalHints(report, assembled),
            boundaryNote = "This is a forensic conclusion, not a judicial verdict."
        )
    }

    private fun buildLegalMappings(report: AnalysisEngine.ForensicReport): List<String> {
        val out = linkedSetOf<String>()
        report.legalReferences?.forEach {
            val cleaned = it?.trim().orEmpty()
            if (cleaned.isNotEmpty()) {
                out += cleaned
            }
        }
        if (out.isEmpty()) {
            out += "Separate company-law relief from criminal referral"
        }
        return out.toList()
    }

    private fun buildLegalHints(
        report: AnalysisEngine.ForensicReport,
        assembled: ForensicReportAssembler.Assembly
    ): List<String> {
        val raw = buildCorpus(report, assembled)
        val hints = mutableListOf<String>()
        if (raw.contains("not countersigned or returned", ignoreCase = true)
            || raw.contains("countersigned", ignoreCase = true)
            || raw.contains("unsigned", ignoreCase = true)
        ) {
            hints += "If no countersigned transfer is anchored, do not state lawful transfer occurred."
        }
        if (raw.contains("goodwill", ignoreCase = true)) {
            hints += "If goodwill surrender language appears, explain it as evidence the asset was treated as having value."
            hints += "Express any goodwill contradiction as legal positioning, not adjudication."
        }
        if (raw.contains("pressure", ignoreCase = true) || raw.contains("vacate", ignoreCase = true)) {
            hints += "Treat pressure, vacate, or non-renewal patterns as investigation-supporting interpretation only."
        }
        return hints
    }

    private fun fallbackCertifiedFindings(items: JSONArray?): List<RenderCertifiedFinding> {
        if (items == null) return emptyList()
        val out = mutableListOf<RenderCertifiedFinding>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val publicationStatus = item.optString("publicationStatus", "CERTIFIED").trim()
            if (!publicationStatus.equals("CERTIFIED", ignoreCase = true)) {
                continue
            }
            val anchorPages = mutableListOf<Int>()
            val anchorArray = item.optJSONArray("anchorPages")
            if (anchorArray != null) {
                for (anchorIndex in 0 until anchorArray.length()) {
                    val page = anchorArray.optInt(anchorIndex, -1)
                    if (page > 0) anchorPages += page
                }
            }
            val primaryPage = item.optInt("primaryPage", item.optInt("page", -1))
            out += RenderCertifiedFinding(
                actor = item.optString("actor", "").trim(),
                summary = item.optString("summary", "").trim(),
                primaryPage = primaryPage,
                anchorPages = anchorPages,
                contradictionStatus = item.optString("contradictionStatus", "").trim(),
                publicationStatus = publicationStatus
            )
        }
        return out
    }

    private fun buildCorpus(
        report: AnalysisEngine.ForensicReport,
        assembled: ForensicReportAssembler.Assembly
    ): String {
        val parts = mutableListOf<String>()
        parts += report.summary.orEmpty()
        parts += jsonArrayStrings(report.normalizedCertifiedFindings)
        parts += jsonArrayStrings(report.forensicConclusion?.optJSONArray("whatHappened"))
        assembled.certifiedFindings.forEach { parts += it.summary }
        assembled.issueGroups.forEach { issue ->
            parts += issue.summary
            parts += issue.whyItMatters
        }
        return parts.joinToString(" ")
    }

    private fun jsonArrayStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val item = array.opt(index)
            when (item) {
                is JSONObject -> {
                    val summary = item.optString("summary", "").trim()
                    if (summary.isNotEmpty()) out += summary
                }
                else -> {
                    val text = array.optString(index, "").trim()
                    if (text.isNotEmpty()) out += text
                }
            }
        }
        return out
    }

    private fun describeBranch(branch: JSONObject?): String {
        if (branch == null) return ""
        val summary = branch.optString("summary", "").trim()
        if (summary.isNotEmpty()) return summary
        return buildString {
            append("verified=")
            append(branch.optInt("verifiedCount", 0))
            append(", candidate=")
            append(branch.optInt("candidateCount", 0))
            append(", certified=")
            append(branch.optInt("certifiedFindingCount", 0))
        }
    }
}
