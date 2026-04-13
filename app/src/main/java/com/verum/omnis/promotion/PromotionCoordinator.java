package com.verum.omnis.promotion;

import android.content.Context;

import com.verum.omnis.core.AnalysisEngine;
import com.verum.omnis.core.CertifiedFinding;
import com.verum.omnis.core.FindingAudit;
import com.verum.omnis.core.FindingCertification;
import com.verum.omnis.core.GuardianDecision;
import com.verum.omnis.core.HashUtil;
import com.verum.omnis.core.PromotionDecision;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PromotionCoordinator {

    private PromotionCoordinator() {}

    public static void run(Context context, AnalysisEngine.ForensicReport report) {
        if (report == null) {
            return;
        }
        report.certifiedFindings = new JSONArray();
        report.guardianDecision = new JSONObject();
        try {
            report.evidenceBundleHash = buildEvidenceBundleHash(report);
            report.deterministicRunId = HashUtil.truncate(
                    HashUtil.sha256(report.caseId + "|" + report.evidenceHash + "|" + report.engineVersion + "|" + report.rulesVersion),
                    24
            );

            PromotionService promotionService = new PromotionService();
            AuditService auditService = new AuditService();
            GuardianService guardianService = new GuardianService();
            CertificationService certificationService = new CertificationService();

            JSONArray candidates = collectPromotableFindings(report);
            List<CertifiedFinding> certified = new ArrayList<>();
            JSONArray guardianLog = new JSONArray();
            for (int i = 0; i < candidates.length(); i++) {
                JSONObject finding = candidates.optJSONObject(i);
                if (finding == null) {
                    continue;
                }
                PromotionDecision decision = promotionService.decide(finding, report);
                FindingAudit audit = auditService.build(finding, decision);
                GuardianDecision guardianDecision = guardianService.approve(context, finding, audit, decision, report);

                JSONObject guardianItem = new JSONObject();
                guardianItem.put("findingType", finding.optString("findingType", finding.optString("conflictType", "FINDING")));
                guardianItem.put("actor", finding.optString("actor", ""));
                guardianItem.put("approved", guardianDecision.approved);
                guardianItem.put("reason", guardianDecision.reason);
                guardianLog.put(guardianItem);

                if (!guardianDecision.approved) {
                    continue;
                }

                FindingCertification certification = certificationService.generate(
                        context,
                        finding,
                        audit,
                        decision,
                        guardianDecision,
                        report
                );
                certified.add(new CertifiedFinding(finding, audit, certification));
            }

            for (CertifiedFinding item : certified) {
                report.certifiedFindings.put(item.toJson());
            }
            int deniedCount = Math.max(0, candidates.length() - certified.size());
            report.guardianDecision.put("approved", certified.size() > 0);
            report.guardianDecision.put("reason", buildGuardianReason(certified.size(), candidates.length(), guardianLog));
            report.guardianDecision.put("approvedCount", certified.size());
            report.guardianDecision.put("reviewedCount", candidates.length());
            report.guardianDecision.put("deniedCount", deniedCount);
            report.guardianDecision.put("findings", guardianLog);
        } catch (Exception e) {
            try {
                report.guardianDecision.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } catch (Exception ignored) {
            }
        }
    }

    private static JSONArray collectPromotableFindings(AnalysisEngine.ForensicReport report) throws Exception {
        JSONArray out = new JSONArray();
        if (report.diagnostics != null) {
            appendEligibleContradictions(out, report.diagnostics.optJSONArray("contradictionRegister"));
        }
        if (report.constitutionalExtraction != null) {
            appendEligibleFinancial(out, report.constitutionalExtraction.optJSONArray("financialExposureRegister"));
            appendEligibleTimeline(out, report.constitutionalExtraction.optJSONArray("timelineAnchorRegister"));
            appendEligibleOversight(out, report.constitutionalExtraction.optJSONArray("actorConductRegister"));
            appendEligibleIncidents(out, report.constitutionalExtraction.optJSONArray("incidentRegister"));
        }
        return out;
    }

    private static void appendEligibleContradictions(JSONArray target, JSONArray source) throws Exception {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            String status = item.optString("status", "CANDIDATE").toUpperCase(Locale.ROOT);
            if (!"VERIFIED".equals(status)) continue;
            if (item.optBoolean("supportOnly", false)) continue;
            if (!hasPromotableActor(item)) continue;
            if (!item.has("propositionA") || !item.has("propositionB")) continue;
            target.put(item);
        }
    }

    private static void appendEligibleFinancial(JSONArray target, JSONArray source) throws Exception {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            String status = item.optString("status", "CANDIDATE").toUpperCase(Locale.ROOT);
            if ("REJECTED".equals(status)) continue;
            String findingType = item.optString("findingType", "");
            if (item.optBoolean("supportOnly", false)) continue;
            if ("UNPAID_PROFIT_SHARE".equalsIgnoreCase(findingType)
                    || "UNPAID_INVOICE".equalsIgnoreCase(findingType)) {
                target.put(item);
            }
        }
    }

    private static void appendEligibleTimeline(JSONArray target, JSONArray source) throws Exception {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            if (item.optBoolean("supportOnly", false) || !item.optBoolean("primaryEvidence", false)) {
                continue;
            }
            if (item.optJSONArray("anchors") == null || item.optJSONArray("anchors").length() == 0) {
                continue;
            }
            if (item.optString("summary", "").trim().isEmpty() && item.optString("narrative", "").trim().isEmpty()) {
                continue;
            }
            String timelineType = item.optString("eventType", "DATED_COMMUNICATION");
            if (!isPromotableTimelineType(timelineType)) {
                continue;
            }
            JSONObject finding = new JSONObject(item.toString());
            if (!isPromotableTimelineItem(finding, timelineType)) {
                continue;
            }
            finding.put("findingType", "TIMELINE_EVENT");
            finding.put("timelineType", timelineType);
            finding.put("brainId", "B5");
            if (!finding.has("status")) {
                finding.put("status", "CANDIDATE");
            }
            if (!finding.has("actor")) {
                finding.put("actor", item.optString("actor", "unresolved actor"));
            }
            target.put(finding);
        }
    }

    private static void appendEligibleOversight(JSONArray target, JSONArray source) throws Exception {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            if (item.optBoolean("supportOnly", false) || !item.optBoolean("primaryEvidence", false)) {
                continue;
            }
            if (item.optJSONArray("anchors") == null || item.optJSONArray("anchors").length() == 0) {
                continue;
            }
            if (item.optString("summary", "").trim().isEmpty() && item.optString("narrative", "").trim().isEmpty()) {
                continue;
            }
            String oversightType = item.optString("conductType", "");
            if (!isPromotableOversightType(oversightType)) {
                continue;
            }
            JSONObject finding = new JSONObject(item.toString());
            if (!isPromotableOversightItem(finding, oversightType)) {
                continue;
            }
            finding.put("findingType", "OVERSIGHT_PATTERN");
            finding.put("oversightType", oversightType);
            finding.put("brainId", brainIdForOversightType(oversightType));
            if (!finding.has("status")) {
                finding.put("status", "CANDIDATE");
            }
            target.put(finding);
        }
    }

    private static String brainIdForOversightType(String oversightType) {
        String type = oversightType == null ? "" : oversightType.toUpperCase(Locale.ROOT);
        if (type.contains("DOCUMENT")
                || type.contains("EXECUTION")
                || type.contains("COUNTERSIGNATURE")) {
            return "B2";
        }
        if (type.contains("FINANCIAL")
                || type.contains("VALUE_EXTRACTION")
                || type.contains("UNLAWFUL_CONTROL")) {
            return "B6";
        }
        return "B5";
    }

    private static void appendEligibleIncidents(JSONArray target, JSONArray source) throws Exception {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String actor = item.optString("actor", "").trim();
            if (actor.isEmpty() || "unresolved actor".equalsIgnoreCase(actor) || !hasPromotableActor(item)) {
                continue;
            }
            int page = item.optInt("page", 0);
            String narrative = item.optString("narrative", item.optString("description", "")).trim();
            if (page <= 0 || narrative.isEmpty()) {
                continue;
            }
            String signalCorpus = (narrative + " " + item.optString("incidentType", "") + " " + item.optString("source", ""))
                    .toLowerCase(Locale.ROOT);
            if (isSupportOnlyPromotionNarrative(signalCorpus)) {
                continue;
            }
            if (!containsPromotableIncidentSignal(signalCorpus)) {
                continue;
            }
            JSONObject finding = new JSONObject(item.toString());
            JSONArray anchors = finding.optJSONArray("anchors");
            if (anchors == null || anchors.length() == 0) {
                anchors = new JSONArray();
                JSONObject anchor = new JSONObject();
                anchor.put("page", page);
                String blockId = item.optString("pageAnchor", "").trim();
                if (!blockId.isEmpty()) {
                    anchor.put("blockId", blockId);
                }
                String exhibitId = item.optString("source", "").trim();
                if (!exhibitId.isEmpty()) {
                    anchor.put("exhibitId", exhibitId);
                }
                anchor.put("excerpt", narrative);
                anchors.put(anchor);
                finding.put("anchors", anchors);
            }
            finding.put("findingType", "INCIDENT_EVENT");
            finding.put("brainId", "B5");
            finding.put("primaryEvidence", true);
            finding.put("supportOnly", false);
            finding.put("summary", item.optString("description", narrative));
            if (!finding.has("status")) {
                finding.put("status", "CANDIDATE");
            }
            target.put(finding);
        }
    }

    private static boolean hasPromotableActor(JSONObject item) {
        String actor = item == null ? "" : item.optString("actor", "").trim();
        if (actor.isEmpty()) {
            return false;
        }
        String lower = actor.toLowerCase(Locale.ROOT);
        return !"unresolved actor".equals(lower)
                && !"the".equals(lower)
                && !"you".equals(lower)
                && !"this".equals(lower)
                && !"all".equals(lower)
                && !isForbiddenActorLabel(lower);
    }

    private static boolean isForbiddenActorLabel(String lower) {
        return "march".equals(lower)
                || "april".equals(lower)
                || "may".equals(lower)
                || "june".equals(lower)
                || "july".equals(lower)
                || "august".equals(lower)
                || "september".equals(lower)
                || "october".equals(lower)
                || "november".equals(lower)
                || "december".equals(lower)
                || "january".equals(lower)
                || "february".equals(lower)
                || "fraud".equals(lower)
                || "complaint".equals(lower)
                || "agreement".equals(lower)
                || "contract".equals(lower)
                || "business".equals(lower)
                || "deal".equals(lower)
                || "invoice".equals(lower)
                || "records".equals(lower)
                || "shipment".equals(lower);
    }

    private static boolean isPromotableTimelineType(String timelineType) {
        return "EXECUTION_STATUS".equalsIgnoreCase(timelineType)
                || "EVICTION_PRESSURE".equalsIgnoreCase(timelineType)
                || "DATED_COMMUNICATION".equalsIgnoreCase(timelineType);
    }

    private static boolean isPromotableOversightType(String oversightType) {
        return "DOCUMENT_EXECUTION_STATE".equalsIgnoreCase(oversightType)
                || "CONTRACT_EXECUTION_POSITION".equalsIgnoreCase(oversightType)
                || "EVICTION_OR_PRESSURE_POSITION".equalsIgnoreCase(oversightType)
                || "FINANCIAL_POSITION".equalsIgnoreCase(oversightType);
    }

    private static boolean containsPromotableIncidentSignal(String corpus) {
        if (corpus == null || corpus.trim().isEmpty()) {
            return false;
        }
        String lower = corpus.toLowerCase(Locale.ROOT);
        return lower.contains("fraud")
                || lower.contains("theft")
                || lower.contains("forg")
                || lower.contains("fabricat")
                || lower.contains("alter")
                || lower.contains("unauthor")
                || lower.contains("hack")
                || lower.contains("archive")
                || lower.contains("delete")
                || lower.contains("conceal")
                || lower.contains("misappropr")
                || lower.contains("refus")
                || lower.contains("denial")
                || lower.contains("pressure")
                || lower.contains("evict")
                || lower.contains("illegal")
                || lower.contains("unlawful")
                || lower.contains("corrupt")
                || lower.contains("misconduct")
                || lower.contains("oppression")
                || lower.contains("harass")
                || lower.contains("threat")
                || lower.contains("interference");
    }

    private static boolean isPromotableTimelineItem(JSONObject finding, String timelineType) {
        if (finding == null) {
            return false;
        }
        if ("EXECUTION_STATUS".equalsIgnoreCase(timelineType) || "EVICTION_PRESSURE".equalsIgnoreCase(timelineType)) {
            return true;
        }
        String corpus = buildPromotionCorpus(finding);
        if (isSupportOnlyPromotionNarrative(corpus)) {
            return false;
        }
        return containsAny(corpus,
                "proceeded with the deal",
                "thanks for the invoice",
                "thank you for the invoice",
                "termination",
                "deal completed",
                "no exclusivity",
                "shareholder agreement",
                "invoice",
                "30%",
                "profit share",
                "archive request",
                "scaquaculture",
                "whatsapp",
                "screenshot",
                "forged",
                "countersigned",
                "not countersigned",
                "sealife");
    }

    private static boolean isPromotableOversightItem(JSONObject finding, String oversightType) {
        if (finding == null) {
            return false;
        }
        String corpus = buildPromotionCorpus(finding);
        if (isSupportOnlyPromotionNarrative(corpus) && !containsAny(corpus,
                "invoice",
                "profit share",
                "withheld",
                "termination",
                "countersigned",
                "evict",
                "vacate",
                "forged",
                "archive request")) {
            return false;
        }
        if ("DOCUMENT_EXECUTION_STATE".equalsIgnoreCase(oversightType)
                || "CONTRACT_EXECUTION_POSITION".equalsIgnoreCase(oversightType)) {
            if (containsAny(corpus,
                    "confidential and/or privileged",
                    "this email and any attachments",
                    "liamhigh78@gmail.com",
                    "re:",
                    "from:",
                    "subject:",
                    "sent:",
                    "to:") && !containsAny(corpus,
                    "not countersigned",
                    "never countersigned",
                    "unsigned",
                    "signature",
                    "signed by",
                    "blank signature",
                    "execution copy")) {
                return false;
            }
            return containsAny(corpus,
                    "not countersigned",
                    "never countersigned",
                    "unsigned",
                    "signature",
                    "signed by",
                    "blank signature",
                    "execution copy",
                    "not validly executed");
        }
        if ("EVICTION_OR_PRESSURE_POSITION".equalsIgnoreCase(oversightType)) {
            return containsAny(corpus, "evict", "vacate", "pressure", "forced", "excluded");
        }
        if ("FINANCIAL_POSITION".equalsIgnoreCase(oversightType)) {
            return containsAny(corpus,
                    "invoice",
                    "profit share",
                    "unpaid",
                    "withheld",
                    "salary",
                    "payment",
                    "deal",
                    "order");
        }
        return false;
    }

    private static String buildPromotionCorpus(JSONObject finding) {
        if (finding == null) {
            return "";
        }
        return finding.optString("summary", "") + " "
                + finding.optString("excerpt", "") + " "
                + finding.optString("narrative", "") + " "
                + finding.optString("text", "") + " "
                + finding.optString("label", "");
    }

    private static boolean isSupportOnlyPromotionNarrative(String corpus) {
        return containsAny(corpus,
                "formal complaint",
                "complaint file",
                "legal practice council",
                "follow-up",
                "follow up",
                "meeting request",
                "private meeting",
                "goodwill payment",
                "settlement",
                "formal notice",
                "law-enforcement notice",
                "escalation",
                "dear ",
                "subject:",
                "from:",
                "to:",
                "cc:",
                "bcc:",
                "petroleum products act",
                "what allfuels did",
                "what this means for your case",
                "what you can use this for",
                "the conclusion is clear",
                "this is not a civil dispute",
                "summary for the report",
                "legal point application",
                "the question you can now ask");
    }

    private static boolean containsAny(String corpus, String... needles) {
        if (corpus == null || needles == null) {
            return false;
        }
        String lower = corpus.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && lower.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String buildGuardianReason(int approvedCount, int reviewedCount, JSONArray guardianLog) {
        if (reviewedCount <= 0) {
            return "No promotable findings entered guardian review in this run.";
        }
        if (approvedCount <= 0) {
            return firstGuardianDenialReason(guardianLog, "Guardian review denied certification for all reviewed findings.");
        }
        if (approvedCount == reviewedCount) {
            return "All promoted findings passed guardian review.";
        }
        return approvedCount + " finding(s) were certified while other reviewed findings were denied by guardian review.";
    }

    private static String firstGuardianDenialReason(JSONArray guardianLog, String fallback) {
        if (guardianLog == null) {
            return fallback;
        }
        for (int i = 0; i < guardianLog.length(); i++) {
            JSONObject item = guardianLog.optJSONObject(i);
            if (item == null || item.optBoolean("approved", false)) {
                continue;
            }
            String reason = item.optString("reason", "").trim();
            if (!reason.isEmpty()) {
                return reason;
            }
        }
        return fallback;
    }

    private static String buildEvidenceBundleHash(AnalysisEngine.ForensicReport report) throws Exception {
        String payload = report.caseId + "|" + report.evidenceHash + "|" + report.engineVersion + "|" + report.rulesVersion;
        if (report.nativeEvidence != null) {
            payload += "|" + report.nativeEvidence.toString();
        }
        return HashUtil.sha512(payload.getBytes(StandardCharsets.UTF_8));
    }
}
