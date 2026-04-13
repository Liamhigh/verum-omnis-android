package com.verum.omnis.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashSet
import java.util.Locale

/**
 * Deterministic truth-led summary layer adapted from the sealed contradiction-engine prototype.
 *
 * This helper does not invent new findings. It compresses the anchored contradiction,
 * chronology, and certified-issue outputs into a stable answer to the practical questions:
 * what happened, who appears linked, when it happened, and why it matters.
 */
object TruthInCodeEngine {

    @JvmStatic
    fun buildTruthFrame(
        report: AnalysisEngine.ForensicReport?,
        assembled: ForensicReportAssembler.Assembly?
    ): JSONObject = buildTruthFrame(report, assembled, false)

    @JvmStatic
    fun buildTruthFrame(
        report: AnalysisEngine.ForensicReport?,
        assembled: ForensicReportAssembler.Assembly?,
        contradictionOnly: Boolean
    ): JSONObject {
        val safeAssembly = assembled ?: if (report != null) ForensicReportAssembler.assemble(report) else null
        val out = JSONObject()
        if (report == null || safeAssembly == null) {
            out.put("whatHappened", "")
            out.put("whoDidWhat", JSONArray())
            out.put("when", JSONArray())
            out.put("whyItMatters", "")
            out.put("openQuestions", JSONArray())
            return out
        }

        out.put("whatHappened", buildWhatHappened(safeAssembly, contradictionOnly))
        out.put("whoDidWhat", buildWhoDidWhat(safeAssembly, contradictionOnly))
        out.put("when", buildWhen(safeAssembly))
        out.put("whyItMatters", buildWhyItMatters(safeAssembly, contradictionOnly))
        out.put("openQuestions", buildOpenQuestions(report, safeAssembly))
        return out
    }

    @JvmStatic
    fun renderTruthSection(
        report: AnalysisEngine.ForensicReport?,
        assembled: ForensicReportAssembler.Assembly?
    ): String = renderTruthSection(report, assembled, false)

    @JvmStatic
    fun renderContradictionTruthSection(
        report: AnalysisEngine.ForensicReport?,
        assembled: ForensicReportAssembler.Assembly?
    ): String = renderTruthSection(report, assembled, true)

    private fun renderTruthSection(
        report: AnalysisEngine.ForensicReport?,
        assembled: ForensicReportAssembler.Assembly?,
        contradictionOnly: Boolean
    ): String {
        val frame = buildTruthFrame(report, assembled, contradictionOnly)
        val sb = StringBuilder()
        sb.append("WHAT THE RECORD CURRENTLY SHOWS\n")
        val whatHappened = frame.optString("whatHappened", "").trim()
        if (whatHappened.isNotEmpty()) {
            sb.append(whatHappened).append("\n")
        } else {
            sb.append("The present record does not yet compress into one clean narrative line without overclaiming.\n")
        }

        sb.append("\nWHO APPEARS LINKED TO THE KEY EVENTS\n")
        appendList(sb, frame.optJSONArray("whoDidWhat"), "No actor-linked summary line was mature enough in this pass.")

        sb.append("\nWHEN THE KEY EVENTS APPEAR TO HAVE HAPPENED\n")
        appendList(sb, frame.optJSONArray("when"), "No anchored chronology line was mature enough in this pass.")

        sb.append("\nWHY THIS MATTERS\n")
        val whyItMatters = frame.optString("whyItMatters", "").trim()
        if (whyItMatters.isNotEmpty()) {
            sb.append(whyItMatters).append("\n")
        } else {
            sb.append("The current value of the record lies mainly in the sealed findings, not in a compressed narrative summary.\n")
        }

        sb.append("\nWHAT STILL NEEDS PROOF OR REVIEW\n")
        appendList(sb, frame.optJSONArray("openQuestions"), "No additional unresolved point was generated beyond the contradiction and audit sections.")
        return sb.toString().trim()
    }

    private fun buildWhatHappened(
        assembled: ForensicReportAssembler.Assembly,
        contradictionOnly: Boolean
    ): String {
        val parts = linkedSetOf<String>()
        if (!contradictionOnly) {
            val leadOffence = assembled.offenceFindings.firstOrNull()?.trim().orEmpty()
            if (leadOffence.isNotEmpty()) {
                parts.add(cleanSentence(leadOffence))
            }
        }
        val leadIssue = assembled.issueGroups.firstOrNull()
        if (leadIssue != null) {
            parts.add(cleanSentence(leadIssue.summary))
        }
        val secondIssue = assembled.issueGroups.drop(1).firstOrNull { isUsefulIssue(it) }
        if (secondIssue != null && secondIssue.summary != leadIssue?.summary) {
            parts.add(cleanSentence(secondIssue.summary))
        }
        val chronologyLead = assembled.chronology.firstOrNull { isUsefulChronology(it) }
        if (chronologyLead != null && chronologyLead.summary.isNotBlank()) {
            val dateLead = chronologyLead.dateLabel.trim()
            val chronologySentence = if (dateLead.equals("Date not fixed in this extract", ignoreCase = true)) {
                cleanSentence(chronologyLead.summary)
            } else {
                "$dateLead: ${cleanSentence(chronologyLead.summary)}"
            }
            parts.add(chronologySentence)
        }
        return parts.take(3).joinToString(" ")
    }

    private fun isUsefulIssue(issue: ForensicReportAssembler.IssueCard?): Boolean {
        if (issue == null) return false
        val corpus = (issue.title + " " + issue.summary).lowercase(Locale.US)
        return !containsAny(
            corpus,
            "document execution issue",
            "document-integrity issue that survived the primary extraction filter",
            "chronology point"
        )
    }

    private fun isUsefulChronology(event: ForensicReportAssembler.ChronologyEvent?): Boolean {
        if (event == null) return false
        val corpus = event.summary.lowercase(Locale.US)
        if (event.actors.any { it.equals("unresolved actor", ignoreCase = true) }) return false
        return !containsAny(
            corpus,
            "memorandum of association governing law",
            "witness statement",
            "law-enforcement notice reference",
            "dated client or invoice correspondence",
            "cryptographically sealed evidence",
            "official record"
        )
    }

    private fun buildWhoDidWhat(
        assembled: ForensicReportAssembler.Assembly,
        contradictionOnly: Boolean
    ): JSONArray {
        val lines = LinkedHashSet<String>()
        if (!contradictionOnly) {
            for (line in assembled.offenceFindings.take(4)) {
                if (line.isNotBlank()) {
                    lines.add(cleanSentence(line))
                }
            }
            for (line in assembled.behaviouralFindings.take(2)) {
                if (line.isNotBlank()) {
                    lines.add(cleanSentence(line))
                }
            }
            for (line in assembled.visualFindings.take(2)) {
                if (line.isNotBlank()) {
                    lines.add(cleanSentence(line))
                }
            }
            val actorConclusion = assembled.actorConclusion.trim()
            if (actorConclusion.isNotEmpty()) {
                lines.add(cleanSentence(actorConclusion))
            }
        }
        for (issue in assembled.issueGroups.take(4)) {
            val actorLine = if (issue.actors.isNotEmpty()) {
                val verb = if (issue.actors.size > 1) "appear" else "appears"
                "${issue.actors.joinToString(", ")} $verb in the record around ${issue.title.lowercase(Locale.US)}."
            } else {
                ""
            }
            if (actorLine.isNotBlank()) {
                lines.add(cleanSentence(actorLine))
            }
        }
        return toJsonArray(lines)
    }

    private fun buildWhen(assembled: ForensicReportAssembler.Assembly): JSONArray {
        val lines = LinkedHashSet<String>()
        for (event in assembled.chronology.take(6)) {
            val pageText = if (event.evidencePages.isNotEmpty()) {
                " (pages ${event.evidencePages.joinToString(", ")})"
            } else {
                ""
            }
            lines.add("${event.dateLabel}: ${cleanSentence(event.summary)}$pageText")
        }
        if (lines.isEmpty()) {
            for (issue in assembled.issueGroups.take(4)) {
                if (issue.evidencePages.isNotEmpty()) {
                    lines.add("Date not fixed in this extract: ${cleanSentence(issue.summary)} (pages ${issue.evidencePages.joinToString(", ")})")
                }
            }
        }
        return toJsonArray(lines)
    }

    private fun buildWhyItMatters(
        assembled: ForensicReportAssembler.Assembly,
        contradictionOnly: Boolean
    ): String {
        val parts = linkedSetOf<String>()
        val leadIssue = assembled.issueGroups.firstOrNull()
        if (leadIssue != null && leadIssue.whyItMatters.isNotBlank()) {
            parts.add(cleanSentence(leadIssue.whyItMatters))
        }
        if (!contradictionOnly) {
            val leadBehaviour = assembled.behaviouralFindings.firstOrNull()?.trim().orEmpty()
            if (leadBehaviour.isNotEmpty()) {
                parts.add(cleanSentence(leadBehaviour))
            }
        }
        val contradictionPosture = assembled.contradictionPosture.trim()
        if (contradictionPosture.isNotEmpty()) {
            parts.add(cleanSentence(contradictionPosture))
        }
        return parts.take(3).joinToString(" ")
    }

    private fun buildOpenQuestions(
        report: AnalysisEngine.ForensicReport,
        assembled: ForensicReportAssembler.Assembly
    ): JSONArray {
        val lines = LinkedHashSet<String>()
        if (assembled.verifiedContradictionCount <= 0 && assembled.candidateContradictionCount > 0) {
            lines.add("The contradiction pressure is real, but the strongest conflict still needs enough anchored pairing to mature into a verified contradiction.")
        }
        val diagnostics = report.diagnostics ?: JSONObject()
        val contradictions = diagnostics.optJSONArray("contradictionRegister")
        if (contradictions != null) {
            for (i in 0 until contradictions.length()) {
                val item = contradictions.optJSONObject(i) ?: continue
                val needed = cleanSentence(item.optString("neededEvidence", ""))
                if (needed.isNotBlank()) {
                    lines.add(needed)
                }
                if (lines.size >= 4) {
                    break
                }
            }
        }
        if (assembled.chronology.any { it.status.equals("INFERRED-LIMITED", ignoreCase = true) }) {
            lines.add("Parts of the chronology remain date-limited, so the timeline should still be read together with the page anchors.")
        }
        return toJsonArray(lines)
    }

    private fun appendList(sb: StringBuilder, items: JSONArray?, fallback: String) {
        if (items == null || items.length() == 0) {
            sb.append(fallback).append("\n")
            return
        }
        var emitted = 0
        for (i in 0 until items.length()) {
            val line = items.optString(i, "").trim()
            if (line.isEmpty()) {
                continue
            }
            sb.append("- ").append(line).append("\n")
            emitted++
        }
        if (emitted == 0) {
            sb.append(fallback).append("\n")
        }
    }

    private fun toJsonArray(items: Iterable<String>): JSONArray {
        val out = JSONArray()
        for (item in items) {
            if (item.isNotBlank()) {
                out.put(item)
            }
        }
        return out
    }

    private fun cleanSentence(value: String?): String {
        var text = value?.trim().orEmpty()
        while (text.contains("  ")) {
            text = text.replace("  ", " ")
        }
        text = text
            .replace("[truncated for on-device prompt]", "")
            .replace("...[truncated for on-device prompt]...", "")
            .replace("...[truncated for on-device prompt]", "")
            .trim()
        if (text.isEmpty()) {
            return ""
        }
        val clean = text.removePrefix("- ").trim()
        return if (clean.endsWith(".") || clean.endsWith("!") || clean.endsWith("?")) clean else "$clean."
    }

    private fun containsAny(corpus: String, vararg terms: String): Boolean {
        for (term in terms) {
            if (term.isNotBlank() && corpus.contains(term.lowercase(Locale.US))) {
                return true
            }
        }
        return false
    }
}
