package com.verum.omnis.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConstitutionalConfig {

    public static final class Snapshot {
        public final List<ContradictionRule> contradictionRules;
        public final List<String> keywords;
        public final List<String> entities;
        public final List<String> evasion;
        public final List<String> contradictions;
        public final List<String> concealment;
        public final List<String> financial;
        public final List<String> systemLineMarkers;
        public final List<String> secondaryNarrativeMarkers;
        public final List<String> generatedAnalysisMarkers;
        public final Set<String> nameStopwords;
        public final Set<String> actorNoiseTokens;
        public final Map<String, List<String>> subjectKeywords;
        public final Map<String, List<String>> documentIntegrityKeywords;

        private Snapshot(
                List<ContradictionRule> contradictionRules,
                List<String> keywords,
                List<String> entities,
                List<String> evasion,
                List<String> contradictions,
                List<String> concealment,
                List<String> financial,
                List<String> systemLineMarkers,
                List<String> secondaryNarrativeMarkers,
                List<String> generatedAnalysisMarkers,
                Set<String> nameStopwords,
                Set<String> actorNoiseTokens,
                Map<String, List<String>> subjectKeywords,
                Map<String, List<String>> documentIntegrityKeywords
        ) {
            this.contradictionRules = contradictionRules;
            this.keywords = keywords;
            this.entities = entities;
            this.evasion = evasion;
            this.contradictions = contradictions;
            this.concealment = concealment;
            this.financial = financial;
            this.systemLineMarkers = systemLineMarkers;
            this.secondaryNarrativeMarkers = secondaryNarrativeMarkers;
            this.generatedAnalysisMarkers = generatedAnalysisMarkers;
            this.nameStopwords = nameStopwords;
            this.actorNoiseTokens = actorNoiseTokens;
            this.subjectKeywords = subjectKeywords;
            this.documentIntegrityKeywords = documentIntegrityKeywords;
        }
    }

    public static final class ContradictionCondition {
        public final String field;
        public final String op;
        public final String ref;

        private ContradictionCondition(String field, String op, String ref) {
            this.field = field;
            this.op = op;
            this.ref = ref;
        }
    }

    public static final class ContradictionRule {
        public final String id;
        public final String brain;
        public final String description;
        public final String logicType;
        public final List<ContradictionCondition> conditions;
        public final String severity;
        public final String action;
        public final List<String> recoverySteps;

        private ContradictionRule(
                String id,
                String brain,
                String description,
                String logicType,
                List<ContradictionCondition> conditions,
                String severity,
                String action,
                List<String> recoverySteps
        ) {
            this.id = id;
            this.brain = brain;
            this.description = description;
            this.logicType = logicType;
            this.conditions = conditions;
            this.severity = severity;
            this.action = action;
            this.recoverySteps = recoverySteps;
        }
    }

    private static final List<String> KEYWORDS_FALLBACK = Arrays.asList(
            "admit", "deny", "forged", "access", "delete", "refuse", "invoice", "profit",
            "unauthorized", "breach", "hack", "seizure", "shareholder", "oppression", "contract", "cash"
    );
    private static final List<String> ENTITIES_FALLBACK = Arrays.asList(
            "RAKEZ", "SAPS", "Article 84", "UAE", "EU", "South Africa"
    );
    private static final List<String> EVASION_FALLBACK = Arrays.asList(
            "i don't recall", "can't remember", "not sure", "later", "stop asking", "leave me alone"
    );
    private static final List<String> CONTRADICTIONS_FALLBACK = Arrays.asList(
            "never happened", "i never said", "you forged", "fake", "that is not true", "i paid", "no deal", "we had a deal",
            "fell through", "didn't happen", "deal didn't happen", "proceeded with the deal", "order completed",
            "completed the order", "no exclusivity", "70/30", "30% share"
    );
    private static final List<String> CONCEALMENT_FALLBACK = Arrays.asList(
            "delete this", "use my other phone", "no email", "don't write", "keep it off the record", "use cash"
    );
    private static final List<String> FINANCIAL_FALLBACK = Arrays.asList(
            "invoice", "wire", "transfer", "swift", "bank", "cash", "under the table", "kickback"
    );
    private static final List<String> SYSTEM_LINE_MARKERS_FALLBACK = Arrays.asList(
            "verified - verum omnis forensic engine",
            "verum omnis forensic engine",
            "verum omnis constitutional forensic engine",
            "generated by verum omnis",
            "evidence sealed with sha-512",
            "evidence sealed with sha",
            "offline deterministic analysis",
            "chain-of-custody locked",
            "chain of custody locked",
            "custody locked confidential",
            "blockchain anchor",
            "forensic integrity division"
    );
    private static final List<String> SECONDARY_NARRATIVE_MARKERS_FALLBACK = Arrays.asList(
            "executive case status report",
            "structural repair declaration",
            "master forensic archive",
            "founders archive",
            "constitutional forensic report",
            "certification & forensic seal",
            "certification and forensic seal",
            "checksum summary",
            "prompt pack",
            "readme",
            "specification",
            "forensic appendix",
            "generated narrative",
            "shareholder dispute case file",
            "relevant legal violations",
            "full account",
            "evidence index",
            "chronology of events",
            "supporting claims",
            "final reflection",
            "settlement proposal",
            "final window",
            "sealing & final window",
            "goodwill payment",
            "settlement deadline",
            "final reminder",
            "deadline for payment",
            "old woman angelfish",
            "butterflyfish",
            "reeftribe",
            "new company registration",
            "consequences of refusal",
            "legal context",
            "next steps",
            "this section contains",
            "this section includes",
            "signal outage evidence",
            "client misrepresentation",
            "criminal defense evidence",
            "meeting requests & shareholder oppression",
            "prepared for rakez"
    );
    private static final List<String> GENERATED_ANALYSIS_MARKERS_FALLBACK = Arrays.asList(
            "here's the clean forensic read of what you provided",
            "this intake reads as",
            "what the intake is really saying",
            "most important findings from your payload",
            "bottom-line forensic assessment",
            "best next move",
            "practical conclusion",
            "if i had to classify the evidentiary posture",
            "based only on the data you pasted",
            "clean, high-signal forensic intake",
            "i'll break it down in verum omnis terms",
            "i’ll break it down in verum omnis terms",
            "just what the data actually proves",
            "case status — verified state",
            "primary legal exposure",
            "core forensic finding",
            "final classification (verum omnis)",
            "bottom line",
            "if you want, next step i can do"
    );
    private static final Set<String> NAME_STOPWORDS_FALLBACK = new LinkedHashSet<>(Arrays.asList(
            "South Africa", "Ras Al", "Economic Zone", "Page", "Utc", "Local Time",
            "Jurisdiction", "Case Id", "Evidence Sha", "Verum Omnis", "Seal Certificate",
            "Constitutional Forensic", "Forensic Report", "World Bank", "Port Shepstone",
            "Regards", "Invoice", "Operations", "Executive Summary", "Message From",
            "Google Meet", "Legal Consultant", "Logo", "Logo Jpg", "Generative",
            "Good Day", "Contact Good", "Final Window", "Cryptographic Sealing",
            "Goodwill Payment", "Settlement Deadline", "Final Reminder", "Deadline For Payment",
            "Settlement", "Old Woman Angelfish", "Butterflyfish", "Reeftribe", "New Company Registration",
            "Only", "Learn", "There", "Thanks", "Urgently", "Explanation",
            "Offline Deterministic Analysis", "Forensic Engine", "Constitutional Forensic Engine",
            "Verum Omnis Constitutional", "Chain Of Custody", "Custody Locked Confidential",
            "Structural Repair Declaration", "Franchise Agreement", "Commencement Date",
            "The Franchisee", "The Franchisor", "Generated Narrative", "Forensic Integrity Division",
            "Petroleum Products Act", "Particular Terms", "Astron Manual", "Franchised Business",
            "Franchise Interest", "Retail Outlet Standards", "The Lessee", "Astron Products",
            "Branded Marketer", "Villiers Road", "Port Elizabeth", "South African"
    ));
    private static final Set<String> ACTOR_NOISE_TOKENS_FALLBACK = new LinkedHashSet<>(Arrays.asList(
            "offline", "deterministic", "analysis", "forensic", "engine", "constitutional",
            "custody", "confidential", "generated", "appendix", "summary", "subject",
            "agreement", "franchisee", "franchisor", "commencement", "division",
            "consultant", "report", "certificate", "seal", "locked",
            "deadline", "settlement", "reminder"
    ));

    private static final Map<String, List<String>> SUBJECT_KEYWORDS_FALLBACK = new LinkedHashMap<>();
    private static final Map<String, List<String>> DOCUMENT_INTEGRITY_KEYWORDS_FALLBACK = new LinkedHashMap<>();
    private static final List<ContradictionRule> CONTRADICTION_RULES_FALLBACK = new ArrayList<>();

    static {
        SUBJECT_KEYWORDS_FALLBACK.put("Shareholder Oppression", Arrays.asList(
                "shareholder rights", "50% shareholder", "private meeting", "denied meeting", "excluded", "oppression"));
        SUBJECT_KEYWORDS_FALLBACK.put("Breach of Fiduciary Duty", Arrays.asList(
                "fiduciary duty", "self-dealing", "conflict of interest", "profit diversion", "proceeded with the deal"));
        SUBJECT_KEYWORDS_FALLBACK.put("Cybercrime", Arrays.asList(
                "unauthorized access", "archive request", "google archive", "gmail archive", "scaquaculture", "login alert", "cyber"));
        SUBJECT_KEYWORDS_FALLBACK.put("Fraudulent Evidence", Arrays.asList(
                "forged", "forgery", "doctored", "cropped", "tampered", "fake"));
        SUBJECT_KEYWORDS_FALLBACK.put("Emotional Exploitation", Arrays.asList(
                "harassment", "bullying", "gaslighting", "medical crisis", "emotional"));
        SUBJECT_KEYWORDS_FALLBACK.put("Financial Irregularities", Arrays.asList(
                "invoice", "wire transfer", "bank transfer", "swift", "cash", "kickback", "goodwill", "rent paid", "payment due", "repayment"));
        SUBJECT_KEYWORDS_FALLBACK.put("Concealment / Deletion", Arrays.asList(
                "delete", "off the record", "no email", "other phone", "keep it off"));

        DOCUMENT_INTEGRITY_KEYWORDS_FALLBACK.put("MISSING_COUNTERSIGNATURE", Arrays.asList(
                "no countersigned", "never countersigned", "not countersigned", "countersigned copy", "blank",
                "spaces for", "neither operator has ever received a countersigned copy"));
        DOCUMENT_INTEGRITY_KEYWORDS_FALLBACK.put("MISSING_EXECUTION_EVIDENCE", Arrays.asList(
                "unsigned mou", "unsigned 6-page mou", "unsigned contract", "no valid contract", "no valid renewal",
                "never signed back", "not legally valid", "not signed it back", "not signed back"));
        DOCUMENT_INTEGRITY_KEYWORDS_FALLBACK.put("BACKDATING_RISK", Arrays.asList(
                "back-dated", "backdated", "produce \"signed\" copies now", "later-produced signed copies",
                "metadata will expose", "suddenly found signed", "signed copies now"));
        DOCUMENT_INTEGRITY_KEYWORDS_FALLBACK.put("METADATA_ANOMALY", Arrays.asList(
                "metadata anomaly", "metadata shows", "created the day before submission", "timestamp",
                "hash mismatch", "mathematical fingerprint"));
        DOCUMENT_INTEGRITY_KEYWORDS_FALLBACK.put("SIGNATURE_MISMATCH", Arrays.asList(
                "signature mismatch", "signature differs", "signature is present", "signature marks"));
        DOCUMENT_INTEGRITY_KEYWORDS_FALLBACK.put("CHAIN_OF_CUSTODY_GAP", Arrays.asList(
                "continuity concern", "material continuity concerns", "chain of custody gap", "continuity is degraded"));

        CONTRADICTION_RULES_FALLBACK.add(new ContradictionRule(
                "contradiction-basic-1",
                "B1_Contradiction_Engine",
                "Flag contradictions across statements with identical actors and timestamps.",
                "all",
                Arrays.asList(
                        new ContradictionCondition("actor", "eq", "actor"),
                        new ContradictionCondition("timestamp", "eq", "timestamp"),
                        new ContradictionCondition("statement", "contradicts", "statement")
                ),
                "CRITICAL",
                "FLAG_AND_FREEZE",
                Arrays.asList("cross_check_external:witness_pool", "escalate:human_review")
        ));
        CONTRADICTION_RULES_FALLBACK.add(new ContradictionRule(
                "multi-actor-conflict-1",
                "B1_Contradiction_Engine+B4_Linguistics",
                "Flags contradictory statements across different actors about the same timestamp or event.",
                "all",
                Arrays.asList(
                        new ContradictionCondition("timestamp", "eq", "timestamp"),
                        new ContradictionCondition("statement", "contradicts", "statement"),
                        new ContradictionCondition("actor", "neq", "actor")
                ),
                "HIGH",
                "FLAG",
                Arrays.asList("rank_sources:credibility|chain_strength", "auto_select_strongest", "if_conflict_persists:escalate")
        ));
        CONTRADICTION_RULES_FALLBACK.add(new ContradictionRule(
                "timestamp-drift-1",
                "B4_Linguistics",
                "Detect inconsistent timestamps for the same actor within an impossible overlap window.",
                "all",
                Arrays.asList(
                        new ContradictionCondition("actor", "eq", "actor"),
                        new ContradictionCondition("timestamp", "overlaps", "timestamp")
                ),
                "HIGH",
                "FLAG",
                Arrays.asList("align_timestamps:5m", "if_unresolved:WARN")
        ));
    }

    private static Snapshot cached;

    private ConstitutionalConfig() {}

    public static synchronized Snapshot load(Context context) {
        if (cached != null) {
            return cached;
        }

        Snapshot defaults = new Snapshot(
                copyContradictionRules(CONTRADICTION_RULES_FALLBACK),
                new ArrayList<>(KEYWORDS_FALLBACK),
                new ArrayList<>(ENTITIES_FALLBACK),
                new ArrayList<>(EVASION_FALLBACK),
                new ArrayList<>(CONTRADICTIONS_FALLBACK),
                new ArrayList<>(CONCEALMENT_FALLBACK),
                new ArrayList<>(FINANCIAL_FALLBACK),
                new ArrayList<>(SYSTEM_LINE_MARKERS_FALLBACK),
                new ArrayList<>(SECONDARY_NARRATIVE_MARKERS_FALLBACK),
                new ArrayList<>(GENERATED_ANALYSIS_MARKERS_FALLBACK),
                new LinkedHashSet<>(NAME_STOPWORDS_FALLBACK),
                new LinkedHashSet<>(ACTOR_NOISE_TOKENS_FALLBACK),
                copyMap(SUBJECT_KEYWORDS_FALLBACK),
                copyMap(DOCUMENT_INTEGRITY_KEYWORDS_FALLBACK)
        );

        try {
            JSONObject detection = new JSONObject(RulesProvider.getDetectionRules(context));
            JSONObject contradictionRules = new JSONObject(RulesProvider.getContradictionRules(context));
            JSONObject subjectKeywords = new JSONObject(RulesProvider.getSubjectKeywords(context));
            JSONObject integrityKeywords = new JSONObject(RulesProvider.getIntegrityKeywords(context));

            cached = new Snapshot(
                    extractContradictionRules(contradictionRules, defaults.contradictionRules),
                    extractArray(detection, "keywords", defaults.keywords),
                    extractArray(detection, "entities", defaults.entities),
                    extractArray(detection, "evasion", defaults.evasion),
                    extractArray(detection, "contradictions", defaults.contradictions),
                    extractArray(detection, "concealment", defaults.concealment),
                    extractArray(detection, "financial", defaults.financial),
                    extractArray(detection, "systemLineMarkers", defaults.systemLineMarkers),
                    extractArray(detection, "secondaryNarrativeMarkers", defaults.secondaryNarrativeMarkers),
                    extractArray(detection, "generatedAnalysisMarkers", defaults.generatedAnalysisMarkers),
                    new LinkedHashSet<>(extractArray(detection, "nameStopwords", new ArrayList<>(defaults.nameStopwords))),
                    new LinkedHashSet<>(extractArray(detection, "actorNoiseTokens", new ArrayList<>(defaults.actorNoiseTokens))),
                    extractKeywordMap(subjectKeywords, "subjects", defaults.subjectKeywords),
                    extractKeywordMap(integrityKeywords, "integrityFindings", defaults.documentIntegrityKeywords)
            );
        } catch (Exception ignored) {
            cached = defaults;
        }
        return cached;
    }

    private static List<ContradictionRule> extractContradictionRules(JSONObject root, List<ContradictionRule> fallback) {
        JSONArray array = root.optJSONArray("rules");
        if (array == null) {
            return copyContradictionRules(fallback);
        }
        List<ContradictionRule> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject logic = item.optJSONObject("logic");
            JSONArray conditionsJson = logic != null ? logic.optJSONArray("conditions") : null;
            List<ContradictionCondition> conditions = new ArrayList<>();
            if (conditionsJson != null) {
                for (int j = 0; j < conditionsJson.length(); j++) {
                    JSONObject condition = conditionsJson.optJSONObject(j);
                    if (condition == null) {
                        continue;
                    }
                    conditions.add(new ContradictionCondition(
                            condition.optString("field", "").trim(),
                            condition.optString("op", "").trim(),
                            condition.optString("ref", "").trim()
                    ));
                }
            }
            JSONArray recoveryJson = item.optJSONArray("recovery");
            List<String> recovery = new ArrayList<>();
            if (recoveryJson != null) {
                for (int j = 0; j < recoveryJson.length(); j++) {
                    JSONObject step = recoveryJson.optJSONObject(j);
                    if (step == null) {
                        continue;
                    }
                    StringBuilder summary = new StringBuilder(step.optString("step", "").trim());
                    appendRecoveryField(summary, step, "target");
                    appendRecoveryField(summary, step, "next");
                    appendRecoveryField(summary, step, "window");
                    if (step.optJSONArray("fields") != null) {
                        appendRecoveryField(summary, step, "fields");
                    }
                    if (step.optJSONArray("criteria") != null) {
                        appendRecoveryField(summary, step, "criteria");
                    }
                    String normalized = summary.toString().trim();
                    if (!normalized.isEmpty()) {
                        recovery.add(normalized);
                    }
                }
            }
            out.add(new ContradictionRule(
                    item.optString("id", "").trim(),
                    item.optString("brain", "").trim(),
                    item.optString("description", "").trim(),
                    logic != null ? logic.optString("type", "").trim() : "",
                    conditions,
                    item.optString("severity", "").trim(),
                    item.optString("action", "").trim(),
                    recovery
            ));
        }
        return out.isEmpty() ? copyContradictionRules(fallback) : out;
    }

    private static List<String> extractArray(JSONObject obj, String key, List<String> fallback) {
        JSONArray array = obj.optJSONArray(key);
        if (array == null) {
            return new ArrayList<>(fallback);
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) {
                out.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return out.isEmpty() ? new ArrayList<>(fallback) : out;
    }

    private static Map<String, List<String>> extractKeywordMap(JSONObject obj, String key, Map<String, List<String>> fallback) {
        JSONObject source = obj.optJSONObject(key);
        if (source == null) {
            return copyMap(fallback);
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        JSONArray names = source.names();
        if (names == null) {
            return copyMap(fallback);
        }
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i, "").trim();
            if (name.isEmpty()) {
                continue;
            }
            JSONArray items = source.optJSONArray(name);
            List<String> values = new ArrayList<>();
            if (items != null) {
                for (int j = 0; j < items.length(); j++) {
                    String value = items.optString(j, "").trim();
                    if (!value.isEmpty()) {
                        values.add(value.toLowerCase(Locale.ROOT));
                    }
                }
            }
            if (!values.isEmpty()) {
                out.put(name, values);
            }
        }
        return out.isEmpty() ? copyMap(fallback) : out;
    }

    private static Map<String, List<String>> copyMap(Map<String, List<String>> source) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            out.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return out;
    }

    private static List<ContradictionRule> copyContradictionRules(List<ContradictionRule> source) {
        List<ContradictionRule> out = new ArrayList<>();
        for (ContradictionRule rule : source) {
            out.add(new ContradictionRule(
                    rule.id,
                    rule.brain,
                    rule.description,
                    rule.logicType,
                    new ArrayList<>(rule.conditions),
                    rule.severity,
                    rule.action,
                    new ArrayList<>(rule.recoverySteps)
            ));
        }
        return out;
    }

    private static void appendRecoveryField(StringBuilder summary, JSONObject step, String key) {
        if (!step.has(key) || step.isNull(key)) {
            return;
        }
        Object value = step.opt(key);
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                String item = array.optString(i, "").trim();
                if (!item.isEmpty()) {
                    parts.add(item);
                }
            }
            if (!parts.isEmpty()) {
                summary.append(':').append(String.join("|", parts));
            }
        } else {
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                summary.append(':').append(text);
            }
        }
    }
}
