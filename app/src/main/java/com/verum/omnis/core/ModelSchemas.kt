package com.verum.omnis.core

import org.json.JSONArray
import org.json.JSONObject

data class ExtractionAssistInput(
    val artifactId: String,
    val artifactType: String,
    val sha512: String,
    val pageNumber: Int? = null,
    val nativeText: String? = null,
    val ocrText: String? = null,
    val languageHint: String? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("artifactId", artifactId)
        put("artifactType", artifactType)
        put("sha512", sha512)
        putIfNotNull("pageNumber", pageNumber)
        putIfNotNull("nativeText", nativeText)
        putIfNotNull("ocrText", ocrText)
        putIfNotNull("languageHint", languageHint)
    }.toString()
}

data class ExtractedFact(
    val text: String,
    val actor: String? = null,
    val dateText: String? = null,
    val page: Int? = null
)

data class ClauseHit(
    val clauseType: String,
    val text: String,
    val page: Int? = null
)

data class SignatureObservation(
    val present: Boolean,
    val roleLabel: String? = null,
    val page: Int? = null
)

data class ExtractionAssistOutput(
    val facts: List<ExtractedFact>,
    val clauses: List<ClauseHit>,
    val signatures: List<SignatureObservation>,
    val warnings: List<String>
) {
    companion object {
        @JvmStatic
        fun fromJson(raw: String): ExtractionAssistOutput {
            val root = JSONObject(extractJson(raw))
            return ExtractionAssistOutput(
                facts = root.optJSONArray("facts").toFactList(),
                clauses = root.optJSONArray("clauses").toClauseList(),
                signatures = root.optJSONArray("signatures").toSignatureList(),
                warnings = root.optJSONArray("warnings").toStringList()
            )
        }
    }
}

data class RenderCertifiedFinding(
    val actor: String,
    val summary: String,
    val primaryPage: Int,
    val anchorPages: List<Int>,
    val contradictionStatus: String,
    val publicationStatus: String
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("actor", actor)
        put("summary", summary)
        put("primaryPage", primaryPage)
        put("anchorPages", JSONArray(anchorPages))
        put("contradictionStatus", contradictionStatus)
        put("publicationStatus", publicationStatus)
    }
}

data class ReportRenderInput(
    val caseId: String,
    val evidenceHash: String,
    val engineVersion: String,
    val deterministicRunId: String,
    val jurisdiction: String,
    val tripleVerificationStatus: String,
    val thesisReason: String,
    val antithesisReason: String,
    val synthesisReason: String,
    val tieBreaker: String? = null,
    val certifiedFindings: List<RenderCertifiedFinding>,
    val legalMappings: List<String>,
    val legalIssueHints: List<String>,
    val boundaryNote: String
) {
    fun toJson(): String = JSONObject().apply {
        put("caseId", caseId)
        put("evidenceHash", evidenceHash)
        put("engineVersion", engineVersion)
        put("deterministicRunId", deterministicRunId)
        put("jurisdiction", jurisdiction)
        put("tripleVerificationStatus", tripleVerificationStatus)
        put("thesisReason", thesisReason)
        put("antithesisReason", antithesisReason)
        put("synthesisReason", synthesisReason)
        putIfNotNull("tieBreaker", tieBreaker)
        put("certifiedFindings", JSONArray().apply {
            certifiedFindings.forEach { put(it.toJsonObject()) }
        })
        put("legalMappings", JSONArray(legalMappings))
        put("legalIssueHints", JSONArray(legalIssueHints))
        put("boundaryNote", boundaryNote)
    }.toString()
}

data class ReportSectionOutput(
    val report: String
) {
    companion object {
        @JvmStatic
        fun fromJson(raw: String): ReportSectionOutput {
            val root = JSONObject(extractJson(raw))
            return ReportSectionOutput(report = root.optString("report", "").trim())
        }
    }
}

data class ReportAuditInput(
    val reportText: String,
    val sourceBundle: ReportRenderInput
) {
    fun toJson(): String = JSONObject().apply {
        put("reportText", reportText)
        put("sourceBundle", JSONObject(sourceBundle.toJson()))
    }.toString()
}

data class ReportAuditOutput(
    val auditPassed: Boolean,
    val notes: List<String>
) {
    companion object {
        @JvmStatic
        fun fromJson(raw: String): ReportAuditOutput {
            val root = JSONObject(extractJson(raw))
            return ReportAuditOutput(
                auditPassed = root.optBoolean("auditPassed", false),
                notes = root.optJSONArray("notes").toStringList()
            )
        }
    }
}

data class RenderedReport(
    val humanBrief: String,
    val legalStanding: String,
    val policeSummary: String,
    val auditPassed: Boolean,
    val auditNotes: List<String>
)

private fun JSONObject.putIfNotNull(key: String, value: Any?) {
    if (value != null) {
        put(key, value)
    }
}

private fun JSONArray?.toFactList(): List<ExtractedFact> {
    if (this == null) return emptyList()
    val out = mutableListOf<ExtractedFact>()
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        out += ExtractedFact(
            text = item.optString("text", "").trim(),
            actor = item.optString("actor", "").trim().ifEmpty { null },
            dateText = item.optString("dateText", "").trim().ifEmpty { null },
            page = item.optInt("page", -1).takeIf { it > 0 }
        )
    }
    return out
}

private fun JSONArray?.toClauseList(): List<ClauseHit> {
    if (this == null) return emptyList()
    val out = mutableListOf<ClauseHit>()
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        out += ClauseHit(
            clauseType = item.optString("clauseType", "").trim(),
            text = item.optString("text", "").trim(),
            page = item.optInt("page", -1).takeIf { it > 0 }
        )
    }
    return out
}

private fun JSONArray?.toSignatureList(): List<SignatureObservation> {
    if (this == null) return emptyList()
    val out = mutableListOf<SignatureObservation>()
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        out += SignatureObservation(
            present = item.optBoolean("present", false),
            roleLabel = item.optString("roleLabel", "").trim().ifEmpty { null },
            page = item.optInt("page", -1).takeIf { it > 0 }
        )
    }
    return out
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = mutableListOf<String>()
    for (index in 0 until length()) {
        val value = optString(index, "").trim()
        if (value.isNotEmpty()) {
            out += value
        }
    }
    return out
}

private fun extractJson(raw: String): String {
    val cleaned = raw.trim()
    val start = cleaned.indexOf('{')
    val end = cleaned.lastIndexOf('}')
    if (start >= 0 && end > start) {
        return cleaned.substring(start, end + 1)
    }
    return cleaned
}
