package com.verum.omnis.ai;

import android.content.Context;
import android.util.Log;

import com.verum.omnis.core.ConstitutionalConfig;
import com.verum.omnis.core.RulesProvider;
import com.verum.omnis.forensic.ExtractedProposition;
import com.verum.omnis.forensic.NativeEvidenceResult;
import com.verum.omnis.forensic.OcrTextBlock;
import com.verum.omnis.forensic.VisualForgeryFinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verum Omnis Rules-Only Engine (v5.1.1-derived)
 * Deterministic, no-ML scorer using template rules:
 *  - Keyword/entity scanning
 *  - Contradiction heuristics
 *  - Omission/evasion markers
 *  - Concealment patterns
 *  - Financial irregularity flags
 *
 * Rule lists are loaded from assets via RulesProvider if available;
 * otherwise fall back to hardcoded defaults.
 */
public class RulesEngine {

    private static final String TAG = "RulesEngine";

    public static class Result {
        public double riskScore;
        public String[] topLiabilities;
        public JSONObject diagnostics;
        public JSONObject constitutionalExtraction;
    }

    private static final class CorpusSnapshot {
        final List<OcrTextBlock> blocks = new ArrayList<>();
        int contaminatedBlockCount;
        int secondaryNarrativeBlockCount;
    }

    private static final Pattern PERSON_PATTERN =
            Pattern.compile("\\b([A-Z][a-z]{2,}(?:\\s+[A-Z][a-z]{2,}){0,2})\\b");
    private static final Pattern EMAIL_NAME_PATTERN =
            Pattern.compile("([A-Z][A-Za-z]+(?:\\s+[A-Z][A-Za-z'\\-]+){0,2})\\s*<[^>]+>");
    private static final Pattern QUOTED_EMAIL_POSSESSIVE_AUTHOR_PATTERN =
            Pattern.compile("(?i)(?:quoted\\s+from|from)\\s+([\\p{L}][\\p{L}'’\\-]+(?:\\s+[\\p{L}][\\p{L}'’\\-]+){0,3})(?:['’]s\\s+email(?:\\s+dated)?)");
    private static final Pattern QUOTED_EMAIL_AUTHOR_PATTERN =
            Pattern.compile("(?i)(?:quoted\\s+from|from)\\s+([\\p{L}][\\p{L}'’\\-]+(?:\\s+[\\p{L}][\\p{L}'’\\-]+){1,3})");
    private static final Pattern EMAIL_MESSAGE_AUTHOR_PATTERN =
            Pattern.compile("(?i)(?:^|\\b)(?:message|from)\\s+([\\p{L}][\\p{L}'’\\-]+(?:\\s+[\\p{L}][\\p{L}'’\\-]+){1,3})\\s*<");
    private static final Pattern WROTE_AUTHOR_PATTERN =
            Pattern.compile("(?i)([\\p{L}][\\p{L}'’\\-]+(?:\\s+[\\p{L}][\\p{L}'’\\-]+){1,3})\\s+wrote\\s*:");
    private static final Pattern ADMISSION_EMAIL_AUTHOR_PATTERN =
            Pattern.compile("(?i)([\\p{L}][\\p{L}'’\\-]+(?:\\s+[\\p{L}][\\p{L}'’\\-]+){0,2})\\s+admission\\s+email");
    private static final Pattern FORMAL_NAME_PATTERN =
            Pattern.compile("(?i)(?:full\\s+name|name)\\s*:\\s*([A-Z][A-Za-z'\\-]+(?:\\s+[A-Z][A-Za-z'\\-]+){1,2})");
    private static final Pattern COMPLAINANT_NAME_PATTERN =
            Pattern.compile("(?i)(?:full\\s+names?\\s+of\\s+complainant|the\\s+complainant\\s+is|i,\\s*the\\s+undersigned,)\\s+([A-Z][A-Za-z'\\-]+(?:\\s+[A-Z][A-Za-z'\\-]+){1,3})");
    private static final Pattern DATE_LINE_PATTERN =
            Pattern.compile("(Mon|Tue|Wed|Thu|Fri|Sat|Sun),?\\s+\\d{1,2}\\s+[A-Z][a-z]+\\s+\\d{4}");
    private static final Pattern GENERIC_DATE_PATTERN =
            Pattern.compile("\\b\\d{1,2}\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern STRUCTURED_DATE_PATTERN =
            Pattern.compile("\\b\\d{4}/\\d{2}/\\d{2}\\b|\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b");
    private static final Pattern MONEY_PATTERN =
            Pattern.compile("\\bR\\s?\\d+(?:[\\.,]\\d+)?(?:\\s?[–-]\\s?R?\\d+(?:[\\.,]\\d+)?)?(?:\\s?(?:[mMbB]|million|billion))?\\+?\\b",
                    Pattern.CASE_INSENSITIVE);
    private static Set<String> NAME_STOPWORDS = new LinkedHashSet<>(Arrays.asList(
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
            "Branded Marketer", "Villiers Road", "Port Elizabeth", "South African",
            "Legal Point Application", "This Case Section", "Criminal Conduct",
            "Expired Leases", "Illegal Rent", "Forced Payments", "Vulnerable Person",
            "Evidence The", "Evidence The Petroleum", "When Des", "Asset Forfeiture Unit",
            "Negative Evidence", "Natal South"
    ));
    private static final Set<String> JUNK_ACTORS = new LinkedHashSet<>(Arrays.asList(
            "The", "You", "April", "All", "Date", "Fraud", "This", "March", "Page",
            "It", "That", "There", "Here", "Now", "Then", "Not", "Goodwill",
            "Pattern", "Outcome", "Behind", "Exposing", "Racketeering", "Which", "Section",
            "No", "Never", "Yes", "Legal Point", "Case Against", "Outcome Desmond Smith",
            "Arbitration Would Leave"
    ));
    private static List<String> SYSTEM_LINE_MARKERS = Arrays.asList(
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
    private static List<String> SECONDARY_NARRATIVE_MARKERS = Arrays.asList(
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
            "the greensky case -",
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
    private static final List<String> CONTRACT_ROLE_PHRASES = Arrays.asList(
            "complainant",
            "first respondent",
            "second respondent",
            "respondent",
            "applicant",
            "claimant",
            "ancillary profit centre",
            "austrian law",
            "austrian trade agents",
            "australian law",
            "australian competition",
            "emphasis added",
            "base schedule",
            "advertising fund contribution",
            "commission sales",
            "domicile attn",
            "legal manager",
            "environmental health",
            "safety book",
            "total sales",
            "gross retailer margin",
            "lease annexure",
            "equipment lease",
            "trade marks",
            "trade dress",
            "creditor",
            "debtor",
            "surety",
            "kerosene",
            "outlets",
            "tue",
            "motor fuel",
            "motor fuels",
            "astron product",
            "astron products",
            "astron motor fuels",
            "approved supplier",
            "dispute resolution procedure",
            "franchising code",
            "consumer price index",
            "designated area",
            "property maintenance fee",
            "advertising fund contributions",
            "financial services",
            "joint committee",
            "competitive foods australia",
            "conduct industry code",
            "consumer commission franchising",
            "astron intellectual property",
            "all fuels equipment",
            "full costs",
            "such costs",
            "costs borne",
            "palmbili properties",
            "palmbili property investments",
            "seton smith",
            "associates tel"
    );
    private static List<String> GENERATED_ANALYSIS_MARKERS = Arrays.asList(
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
    private static Set<String> ACTOR_NOISE_TOKENS = new LinkedHashSet<>(Arrays.asList(
            "offline", "deterministic", "analysis", "forensic", "engine", "constitutional",
            "custody", "confidential", "generated", "appendix", "summary", "subject",
            "agreement", "franchisee", "franchisor", "commencement", "division",
            "consultant", "report", "certificate", "seal", "locked",
            "deadline", "settlement", "reminder"
    ));
    private static Map<String, List<String>> SUBJECT_KEYWORDS = new LinkedHashMap<>();
    private static Map<String, List<String>> DOCUMENT_INTEGRITY_KEYWORDS = new LinkedHashMap<>();
    static {
        SUBJECT_KEYWORDS.put("Shareholder Oppression", Arrays.asList(
                "shareholder rights", "50% shareholder", "private meeting", "denied meeting", "excluded", "oppression"));
        SUBJECT_KEYWORDS.put("Breach of Fiduciary Duty", Arrays.asList(
                "fiduciary duty", "self-dealing", "conflict of interest", "profit diversion", "proceeded with the deal"));
        SUBJECT_KEYWORDS.put("Cybercrime", Arrays.asList(
                "unauthorized access", "archive request", "google archive", "gmail archive", "scaquaculture", "login alert", "cyber"));
        SUBJECT_KEYWORDS.put("Fraudulent Evidence", Arrays.asList(
                "forged", "forgery", "doctored", "cropped", "tampered", "fake"));
        SUBJECT_KEYWORDS.put("Emotional Exploitation", Arrays.asList(
                "harassment", "bullying", "gaslighting", "medical crisis", "emotional"));
        SUBJECT_KEYWORDS.put("Financial Irregularities", Arrays.asList(
                "invoice", "wire transfer", "bank transfer", "swift", "cash", "kickback", "goodwill", "rent paid", "payment due", "repayment"));
        SUBJECT_KEYWORDS.put("Concealment / Deletion", Arrays.asList(
                "delete", "off the record", "no email", "other phone", "keep it off"));

        DOCUMENT_INTEGRITY_KEYWORDS.put("MISSING_COUNTERSIGNATURE", Arrays.asList(
                "no countersigned", "never countersigned", "not countersigned", "countersigned copy", "blank",
                "spaces for", "neither operator has ever received a countersigned copy"));
        DOCUMENT_INTEGRITY_KEYWORDS.put("MISSING_EXECUTION_EVIDENCE", Arrays.asList(
                "unsigned mou", "unsigned 6-page mou", "unsigned contract", "no valid contract", "no valid renewal",
                "never signed back", "not legally valid", "not signed it back", "not signed back"));
        DOCUMENT_INTEGRITY_KEYWORDS.put("BACKDATING_RISK", Arrays.asList(
                "back-dated", "backdated", "produce \"signed\" copies now", "later-produced signed copies",
                "metadata will expose", "suddenly found signed", "signed copies now"));
        DOCUMENT_INTEGRITY_KEYWORDS.put("METADATA_ANOMALY", Arrays.asList(
                "metadata anomaly", "metadata shows", "created the day before submission", "timestamp",
                "hash mismatch", "mathematical fingerprint"));
        DOCUMENT_INTEGRITY_KEYWORDS.put("SIGNATURE_MISMATCH", Arrays.asList(
                "signature mismatch", "signature differs", "signature is present", "signature marks"));
        DOCUMENT_INTEGRITY_KEYWORDS.put("CHAIN_OF_CUSTODY_GAP", Arrays.asList(
                "continuity concern", "material continuity concerns", "chain of custody gap", "continuity is degraded"));
    }

    // Hardcoded fallback lists
    private static final List<String> KEYWORDS_FALLBACK = Arrays.asList(
            "admit","deny","forged","access","delete","refuse","invoice","profit",
            "unauthorized","breach","hack","seizure","shareholder","oppression","contract","cash"
    );
    private static final List<String> ENTITIES_FALLBACK = Arrays.asList(
            "RAKEZ","SAPS","Article 84","Greensky","UAE","EU","South Africa"
    );
    private static final List<String> EVASION_FALLBACK = Arrays.asList(
            "i don't recall","can't remember","not sure","later","stop asking","leave me alone"
    );
    private static final List<String> CONTRADICT_FALLBACK = Arrays.asList(
            "never happened","i never said","you forged","fake","that is not true","i paid","no deal","we had a deal",
            "fell through","didn't happen","did not happen","deal didn't happen","proceeded with the deal","order completed",
            "completed the order","no exclusivity","70/30","30% share"
    );
    private static final List<String> CONCEAL_FALLBACK = Arrays.asList(
            "delete this","use my other phone","no email","don't write","keep it off the record","use cash"
    );
    private static final List<String> FINANCIAL_FALLBACK = Arrays.asList(
            "invoice","wire","transfer","swift","bank","cash","under the table","kickback"
    );

    // Dynamic lists (overwritten if JSON asset loads)
    private static List<String> KEYWORDS = new ArrayList<>(KEYWORDS_FALLBACK);
    private static List<String> ENTITIES = new ArrayList<>(ENTITIES_FALLBACK);
    private static List<String> EVASION = new ArrayList<>(EVASION_FALLBACK);
    private static List<String> CONTRADICT = new ArrayList<>(CONTRADICT_FALLBACK);
    private static List<String> CONCEAL = new ArrayList<>(CONCEAL_FALLBACK);
    private static List<String> FINANCIAL = new ArrayList<>(FINANCIAL_FALLBACK);
    private static List<ConstitutionalConfig.ContradictionRule> CONTRADICTION_RULES = new ArrayList<>();

    private static boolean loadedFromAssets = false;

    /**
     * Attempt to load detection rules JSON from assets (once per process).
     */
    private static void ensureRulesLoaded(Context ctx) {
        if (loadedFromAssets) return;
        try {
            ConstitutionalConfig.Snapshot config = ConstitutionalConfig.load(ctx);
            KEYWORDS = new ArrayList<>(config.keywords);
            ENTITIES = new ArrayList<>(config.entities);
            EVASION = new ArrayList<>(config.evasion);
            CONTRADICT = new ArrayList<>(config.contradictions);
            CONCEAL = new ArrayList<>(config.concealment);
            FINANCIAL = new ArrayList<>(config.financial);
            SYSTEM_LINE_MARKERS = new ArrayList<>(config.systemLineMarkers);
            SECONDARY_NARRATIVE_MARKERS = new ArrayList<>(config.secondaryNarrativeMarkers);
            GENERATED_ANALYSIS_MARKERS = new ArrayList<>(config.generatedAnalysisMarkers);
            NAME_STOPWORDS = new LinkedHashSet<>(config.nameStopwords);
            ACTOR_NOISE_TOKENS = new LinkedHashSet<>(config.actorNoiseTokens);
            SUBJECT_KEYWORDS = new LinkedHashMap<>(config.subjectKeywords);
            DOCUMENT_INTEGRITY_KEYWORDS = new LinkedHashMap<>(config.documentIntegrityKeywords);
            CONTRADICTION_RULES = new ArrayList<>(config.contradictionRules);

            loadedFromAssets = true;
            System.out.println("RulesEngine: Loaded constitutional detection config from assets.");
        } catch (Exception e) {
            // fallback silently
            loadedFromAssets = true;
            System.out.println("RulesEngine: Using fallback hardcoded rules.");
        }
    }

    public static Result analyzeFile(Context ctx, File file) {
        return analyzeEvidence(ctx, file, null);
    }

    public static Result analyzeEvidence(Context ctx, File file, NativeEvidenceResult nativeEvidence) {
        ensureRulesLoaded(ctx);
        Result r = new Result();
        long analysisStartedAt = System.currentTimeMillis();
        Log.d(TAG, "Starting analysis for "
                + (file != null ? file.getName() : "nativeEvidence")
                + " nativeEvidence=" + (nativeEvidence != null));
        try {
            long phaseStartedAt = System.currentTimeMillis();
            int kw;
            int ent;
            int ev;
            int hid;
            int fin;
            if (nativeEvidence != null) {
                kw = countMatchesAcrossNativeEvidence(nativeEvidence, KEYWORDS);
                ent = countMatchesAcrossNativeEvidence(nativeEvidence, ENTITIES);
                ev = countMatchesAcrossNativeEvidence(nativeEvidence, EVASION);
                hid = countMatchesAcrossNativeEvidence(nativeEvidence, CONCEAL);
                fin = 0;
            } else {
                String text = buildAnalysisText(file, null);
                String lower = text.toLowerCase(Locale.ROOT);
                kw = countMatches(lower, KEYWORDS);
                ent = countMatches(lower, ENTITIES);
                ev = countMatches(lower, EVASION);
                hid = countMatches(lower, CONCEAL);
                fin = countMatches(lower, FINANCIAL);
            }
            logPhase("Signal counts", phaseStartedAt,
                    "kw=" + kw + " ent=" + ent + " ev=" + ev + " hid=" + hid + " fin=" + fin);

            phaseStartedAt = System.currentTimeMillis();
            JSONObject extraction = buildConstitutionalExtraction(nativeEvidence);
            logPhase("Constitutional extraction", phaseStartedAt,
                    "namedParties=" + safeLength(extraction.optJSONArray("namedParties"))
                            + " anchoredFindings=" + safeLength(extraction.optJSONArray("anchoredFindings"))
                            + " incidents=" + safeLength(extraction.optJSONArray("incidentRegister")));
            JSONArray financialExposureRegister = extraction.optJSONArray("financialExposureRegister");
            if (nativeEvidence != null) {
                fin = financialExposureRegister != null ? financialExposureRegister.length() : 0;
            }
            phaseStartedAt = System.currentTimeMillis();
            JSONArray contradictionRegister = extractContradictionRegister(nativeEvidence, extraction.optJSONArray("namedParties"));
            logPhase("Contradiction register", phaseStartedAt, "items=" + safeLength(contradictionRegister));
            int verifiedContradictions = countContradictionsByStatus(contradictionRegister, "VERIFIED");
            int candidateContradictions = countContradictionsByStatus(contradictionRegister, "CANDIDATE");
            int rejectedContradictions = countContradictionsByStatus(contradictionRegister, "REJECTED");
            int verifiedFinancialFindings = countItemsByStatus(financialExposureRegister, "VERIFIED");
            int con = verifiedContradictions;
            int namedPartyCount = extraction.optJSONArray("namedParties") != null
                    ? extraction.optJSONArray("namedParties").length() : 0;
            int criticalSubjectCount = extraction.optJSONArray("criticalLegalSubjects") != null
                    ? extraction.optJSONArray("criticalLegalSubjects").length() : 0;
            int anchoredFindingCount = extraction.optJSONArray("anchoredFindings") != null
                    ? extraction.optJSONArray("anchoredFindings").length() : 0;
            int incidentCount = extraction.optJSONArray("incidentRegister") != null
                    ? extraction.optJSONArray("incidentRegister").length() : 0;
            int documentIntegrityFindingCount = extraction.optJSONArray("documentIntegrityFindings") != null
                    ? extraction.optJSONArray("documentIntegrityFindings").length() : 0;
            int sourcePageCount = nativeEvidence != null ? nativeEvidence.sourcePageCount : 0;
            int renderedPageCount = nativeEvidence != null ? nativeEvidence.renderedPageCount : 0;
            phaseStartedAt = System.currentTimeMillis();
            CorpusSnapshot corpusSnapshot = buildCorpusSnapshot(nativeEvidence);
            logPhase("Corpus snapshot reuse", phaseStartedAt,
                    "blocks=" + corpusSnapshot.blocks.size()
                            + " contaminated=" + corpusSnapshot.contaminatedBlockCount
                            + " secondary=" + corpusSnapshot.secondaryNarrativeBlockCount);
            int textCoveragePageCount = distinctPageCount(corpusSnapshot.blocks);
            int ocrFailedCount = nativeEvidence != null ? nativeEvidence.ocrFailedCount : 0;
            int visualFindingCount = nativeEvidence != null ? nativeEvidence.visualFindings.size() : 0;
            double ocrFailureRatio = sourcePageCount > 0 ? ((double) ocrFailedCount / (double) sourcePageCount) : 0.0d;
            boolean weakExtraction = namedPartyCount == 0 || anchoredFindingCount == 0 || incidentCount == 0;
            boolean materialCoverageGap = sourcePageCount > 0
                    && Math.max(renderedPageCount, textCoveragePageCount) < sourcePageCount;
            boolean corpusContaminated = corpusSnapshot.contaminatedBlockCount > 0;
            boolean narrativeHeavy = sourcePageCount > 0
                    && corpusSnapshot.secondaryNarrativeBlockCount > Math.max(12, textCoveragePageCount / 5);
            boolean severeOcrDegradation = ocrFailedCount >= 12 && ocrFailureRatio >= 0.10d;
            boolean concealmentRisk = hid > 0 || ocrFailedCount > 0 || visualFindingCount >= 3
                    || materialCoverageGap || corpusContaminated;
            boolean evidenceManipulationDetected = verifiedContradictions > 0
                    || candidateContradictions > 0
                    || hid > 0
                    || visualFindingCount >= 3;
            boolean verifiedFindingsPresent = verifiedContradictions > 0 || verifiedFinancialFindings > 0;
            boolean technicalBlocker = corpusContaminated || severeOcrDegradation || materialCoverageGap;
            boolean indeterminateDueToConcealment = ((weakExtraction && concealmentRisk)
                    || corpusContaminated
                    || severeOcrDegradation)
                    && !(verifiedFindingsPresent && !technicalBlocker);
            String processingStatus = verifiedFindingsPresent && !technicalBlocker
                    ? "COMPLETED"
                    : indeterminateDueToConcealment
                    ? "INDETERMINATE DUE TO CONCEALMENT"
                    : "DETERMINATE";

            // Heuristic scoring
            double score = (kw*0.05 + ent*0.04 + ev*0.08 + con*0.1 + hid*0.12 + fin*0.06);
            score = Math.min(1.0, score);

            List<String> liab = new ArrayList<>();
            if (!narrativeHeavy && con >= 2) liab.add("Contradictions in statements");
            if (!narrativeHeavy && hid >= 1) liab.add("Patterns of concealment");
            if (!narrativeHeavy && ev  >= 2) liab.add("Evasion/Gaslighting indicators");
            if (!narrativeHeavy && fin >= 2) liab.add("Financial irregularity signals");
            if (!narrativeHeavy && kw  >= 3 && ent >= 1) liab.add("Legal subject flags present");

            JSONArray subjects = extraction.optJSONArray("criticalLegalSubjects");
            if (subjects != null) {
                for (int i = 0; i < subjects.length() && liab.size() < 5; i++) {
                    JSONObject subject = subjects.optJSONObject(i);
                    if (subject == null) continue;
                    String label = normalizeLiabilityLabel(subject.optString("subject", "").trim());
                    if (!label.isEmpty() && !liab.contains(label)) {
                        liab.add(label);
                    }
                }
            }

            if (indeterminateDueToConcealment) {
                liab.clear();
                liab.add("INDETERMINATE DUE TO CONCEALMENT");
            }
            liab = normalizeLiabilities(liab);
            if (liab.isEmpty()) liab.add("General risk");

            r.riskScore = score;
            r.topLiabilities = liab.toArray(new String[0]);
            r.constitutionalExtraction = extraction;

            JSONObject d = new JSONObject();
            d.put("keywords", kw);
            d.put("entities", ent);
            d.put("evasion", ev);
            d.put("contradictions", con);
            d.put("verifiedContradictionCount", verifiedContradictions);
            d.put("candidateContradictionCount", candidateContradictions);
            d.put("rejectedContradictionCount", rejectedContradictions);
            d.put("contradictionRegister", contradictionRegister);
            d.put("verifiedContradictions", filterContradictionsByStatus(contradictionRegister, "VERIFIED"));
            d.put("candidateContradictions", filterContradictionsByStatus(contradictionRegister, "CANDIDATE"));
            d.put("hasVerifiedContradiction", verifiedContradictions > 0);
            d.put("concealment", hid);
            d.put("financial", fin);
            d.put("verifiedFinancialFindingCount", verifiedFinancialFindings);
            d.put("analysisSource", nativeEvidence != null ? "native-ocr" : "raw-file");
            d.put("namedPartyCount", namedPartyCount);
            d.put("criticalSubjectCount", criticalSubjectCount);
            d.put("anchoredFindingCount", anchoredFindingCount);
            d.put("incidentCount", incidentCount);
            d.put("documentIntegrityFindingCount", documentIntegrityFindingCount);
            d.put("indeterminateDueToConcealment", indeterminateDueToConcealment);
            d.put("evidenceManipulationDetected", evidenceManipulationDetected);
            d.put("corpusContaminated", corpusContaminated);
            d.put("corpusContaminationCount", corpusSnapshot.contaminatedBlockCount);
            d.put("secondaryNarrativeBlockCount", corpusSnapshot.secondaryNarrativeBlockCount);
            d.put("usableEvidenceBlockCount", corpusSnapshot.blocks.size());
            d.put("ocrFailureRatio", ocrFailureRatio);
            d.put("severeOcrDegradation", severeOcrDegradation);
            d.put("processingStatus", processingStatus);
            r.diagnostics = d;
            logPhase("Analysis complete", analysisStartedAt,
                    "processingStatus=" + processingStatus
                            + " verifiedContradictions=" + verifiedContradictions
                            + " verifiedFinancialFindings=" + verifiedFinancialFindings
                            + " namedParties=" + namedPartyCount);

            return r;
        } catch (Exception e) {
            Log.e(TAG, "Analysis failed after " + (System.currentTimeMillis() - analysisStartedAt) + " ms", e);
            r.riskScore = 0.0;
            r.topLiabilities = new String[]{"Rules engine error: " + e.getMessage()};
            r.diagnostics = new JSONObject();
            r.constitutionalExtraction = new JSONObject();
            return r;
        }
    }

    private static int countMatches(String text, List<String> needles) {
        int total = 0;
        for (String n : needles) {
            int idx = 0;
            while (true) {
                idx = text.indexOf(n.toLowerCase(Locale.ROOT), idx);
                if (idx == -1) break;
                total++; idx += n.length();
            }
        }
        return total;
    }

    private static int countContradictionsByStatus(JSONArray contradictions, String status) {
        if (contradictions == null || status == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < contradictions.length(); i++) {
            JSONObject item = contradictions.optJSONObject(i);
            if (item != null && status.equalsIgnoreCase(item.optString("status", ""))) {
                count++;
            }
        }
        return count;
    }

    private static int countItemsByStatus(JSONArray items, String status) {
        if (items == null || status == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && status.equalsIgnoreCase(item.optString("status", ""))) {
                count++;
            }
        }
        return count;
    }

    private static int countMatchesAcrossNativeEvidence(NativeEvidenceResult nativeEvidence, List<String> needles) {
        if (nativeEvidence == null) {
            return 0;
        }
        int total = 0;
        for (OcrTextBlock block : allTextBlocks(nativeEvidence)) {
            if (block == null || block.text == null || block.confidence <= 0f) {
                continue;
            }
            String lower = block.text.toLowerCase(Locale.ROOT);
            total += countMatches(lower, needles);
        }
        return total;
    }

    private static JSONArray extractContradictionRegister(NativeEvidenceResult nativeEvidence, JSONArray namedParties) throws Exception {
        long startedAt = System.currentTimeMillis();
        JSONArray result = new JSONArray();
        if (nativeEvidence == null) {
            logPhase("extractContradictionRegister", startedAt, "nativeEvidence absent");
            return result;
        }

        List<OcrTextBlock> blocks = allTextBlocks(nativeEvidence);
        Log.d(TAG, "extractContradictionRegister scanning blocks=" + blocks.size());
        LinkedHashMap<String, JSONObject> deduped = new LinkedHashMap<>();
        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            if (blockIndex > 0 && blockIndex % 100 == 0) {
                Log.d(TAG, "extractContradictionRegister progress block=" + blockIndex + "/" + blocks.size());
            }
            OcrTextBlock block = blocks.get(blockIndex);
            if (block == null || block.text == null || block.confidence <= 0f) {
                continue;
            }
            if (isSecondaryNarrativeText(block.text)) {
                continue;
            }
            String lower = block.text.toLowerCase(Locale.ROOT);
            for (String term : CONTRADICT) {
                String needle = term.toLowerCase(Locale.ROOT);
                if (!lower.contains(needle)) {
                    continue;
                }

                String actor = inferSubjectActor(block.text, namedParties);
                if (!isEvidenceResolvedActor(actor)) {
                    continue;
                }
                JSONObject propositionB = findContradictionPair(blocks, blockIndex, actor, term);
                boolean materiallyConflicting = propositionB != null
                        && propositionsMateriallyConflict(block.text, propositionB.optString("text", ""), term);
                String status = classifyContradictionStatus(block.text, term, actor, materiallyConflicting);
                String conflictType = classifyConflictType(block.text, term, materiallyConflicting);
                List<ConstitutionalConfig.ContradictionRule> matchedRules =
                        matchContradictionRules(block.text, actor, materiallyConflicting, conflictType);
                status = applyContradictionRuleStatus(status, matchedRules, materiallyConflicting, actor);
                String confidence = applyContradictionRuleConfidence(ordinalConfidenceForContradiction(
                        status,
                        actor,
                        GENERIC_DATE_PATTERN.matcher(block.text).find(),
                        materiallyConflicting
                ), matchedRules, materiallyConflicting);

                JSONObject item = new JSONObject();
                item.put("page", block.pageIndex + 1);
                item.put("matchedPhrase", term);
                item.put("actor", actor);
                item.put("excerpt", truncate(block.text, 260));
                item.put("status", status);
                item.put("confidence", confidence);
                item.put("conflictType", conflictType);
                item.put("whyItConflicts", explainContradictionClassification(block.text, term, status, materiallyConflicting));
                item.put("primaryEvidence", materiallyConflicting);
                item.put("supportOnly", !materiallyConflicting);
                putContradictionRuleMetadata(item, matchedRules);
                if ("CANDIDATE".equalsIgnoreCase(status)) {
                    item.put("neededEvidence", neededEvidenceForContradictionTerm(term, conflictType, actor, matchedRules));
                }
                item.put("propositionA", buildPropositionObject(truncate(block.text, 220), block.pageIndex + 1));
                if (materiallyConflicting && propositionB != null) {
                    item.put("propositionB", propositionB);
                }
                item.put("layer", "REJECTED".equals(status) ? "NARRATIVE_THEME" : status);

                String dedupeKey = actor + "|" + term + "|" + status + "|" + conflictType;
                JSONObject existing = deduped.get(dedupeKey);
                if (existing == null) {
                    JSONArray anchors = new JSONArray();
                    anchors.put(buildAnchorObject(block.pageIndex + 1));
                    item.put("anchors", anchors);
                    deduped.put(dedupeKey, item);
                } else {
                    existing.optJSONArray("anchors").put(buildAnchorObject(block.pageIndex + 1));
                }
                break;
            }
        }
        appendStructuredContradictionFindings(deduped, nativeEvidence, blocks, namedParties);
        List<JSONObject> ordered = orderContradictionsDeterministically(deduped.values());
        int emitted = 0;
        for (JSONObject item : ordered) {
            result.put(item);
            emitted++;
            if (emitted >= 24) {
                break;
            }
        }
        logPhase("extractContradictionRegister", startedAt,
                "blocks=" + blocks.size() + " deduped=" + deduped.size() + " emitted=" + result.length());
        return result;
    }

    private static JSONArray filterContradictionsByStatus(JSONArray contradictions, String status) throws Exception {
        JSONArray out = new JSONArray();
        if (contradictions == null || status == null) {
            return out;
        }
        for (int i = 0; i < contradictions.length(); i++) {
            JSONObject item = contradictions.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (status.equalsIgnoreCase(item.optString("status", ""))) {
                out.put(new JSONObject(item.toString()));
            }
        }
        return out;
    }

    private static List<JSONObject> orderContradictionsDeterministically(Iterable<JSONObject> items) {
        List<JSONObject> ordered = new ArrayList<>();
        if (items != null) {
            for (JSONObject item : items) {
                if (item != null) {
                    ordered.add(item);
                }
            }
        }
        ordered.sort((left, right) -> {
            int pageCompare = Integer.compare(left.optInt("page", Integer.MAX_VALUE), right.optInt("page", Integer.MAX_VALUE));
            if (pageCompare != 0) {
                return pageCompare;
            }
            int actorCompare = left.optString("actor", "").compareToIgnoreCase(right.optString("actor", ""));
            if (actorCompare != 0) {
                return actorCompare;
            }
            int conflictCompare = left.optString("conflictType", "").compareToIgnoreCase(right.optString("conflictType", ""));
            if (conflictCompare != 0) {
                return conflictCompare;
            }
            return left.optString("excerpt", "").compareToIgnoreCase(right.optString("excerpt", ""));
        });
        return ordered;
    }

    private static String buildAnalysisText(File file, NativeEvidenceResult nativeEvidence) throws Exception {
        List<OcrTextBlock> blocks = allTextBlocks(nativeEvidence);
        if (!blocks.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (OcrTextBlock block : blocks) {
                if (block == null || block.text == null) continue;
                if (block.confidence <= 0f) continue;
                sb.append("PAGE ").append(block.pageIndex + 1).append(": ")
                        .append(block.text)
                        .append("\n");
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return readAll(file);
    }

    private static JSONObject buildConstitutionalExtraction(NativeEvidenceResult nativeEvidence) throws Exception {
        long startedAt = System.currentTimeMillis();
        JSONObject root = new JSONObject();
        long phaseStartedAt = System.currentTimeMillis();
        JSONArray namedParties = extractNamedParties(nativeEvidence);
        logPhase("buildConstitutionalExtraction.namedParties", phaseStartedAt, "count=" + namedParties.length());
        phaseStartedAt = System.currentTimeMillis();
        JSONArray anchoredFindings = extractAnchoredFindings(nativeEvidence);
        logPhase("buildConstitutionalExtraction.anchoredFindings", phaseStartedAt, "count=" + anchoredFindings.length());
        phaseStartedAt = System.currentTimeMillis();
        JSONArray incidentRegister = extractIncidentRegister(nativeEvidence, namedParties);
        logPhase("buildConstitutionalExtraction.incidentRegister", phaseStartedAt, "count=" + incidentRegister.length());
        phaseStartedAt = System.currentTimeMillis();
        JSONArray documentIntegrityFindings = extractDocumentIntegrityFindings(nativeEvidence, namedParties);
        logPhase("buildConstitutionalExtraction.documentIntegrityFindings", phaseStartedAt, "count=" + documentIntegrityFindings.length());
        phaseStartedAt = System.currentTimeMillis();
        JSONArray timelineAnchorRegister = extractTimelineAnchorRegister(nativeEvidence, namedParties);
        logPhase("buildConstitutionalExtraction.timelineAnchorRegister", phaseStartedAt, "count=" + timelineAnchorRegister.length());
        phaseStartedAt = System.currentTimeMillis();
        JSONArray financialExposureRegister = extractFinancialExposureRegister(nativeEvidence, namedParties);
        logPhase("buildConstitutionalExtraction.financialExposureRegister", phaseStartedAt, "count=" + financialExposureRegister.length());
        phaseStartedAt = System.currentTimeMillis();
        JSONArray actorConductRegister = extractActorConductRegister(
                timelineAnchorRegister,
                documentIntegrityFindings,
                incidentRegister,
                financialExposureRegister
        );
        logPhase("buildConstitutionalExtraction.actorConductRegister", phaseStartedAt, "count=" + actorConductRegister.length());
        phaseStartedAt = System.currentTimeMillis();
        JSONArray narrativeThemeRegister = extractNarrativeThemeRegister(nativeEvidence);
        logPhase("buildConstitutionalExtraction.narrativeThemeRegister", phaseStartedAt, "count=" + narrativeThemeRegister.length());

        root.put("namedParties", namedParties);
        root.put("propositionRegister", extractPropositionRegister(nativeEvidence));
        root.put("criticalLegalSubjects", extractCriticalLegalSubjects(nativeEvidence));
        root.put("anchoredFindings", anchoredFindings);
        root.put("incidentRegister", incidentRegister);
        root.put("documentIntegrityFindings", documentIntegrityFindings);
        root.put("timelineAnchorRegister", timelineAnchorRegister);
        root.put("actorConductRegister", actorConductRegister);
        root.put("financialExposureRegister", financialExposureRegister);
        root.put("narrativeThemeRegister", narrativeThemeRegister);
        logPhase("buildConstitutionalExtraction", startedAt,
                "namedParties=" + namedParties.length()
                        + " anchoredFindings=" + anchoredFindings.length()
                        + " incidentRegister=" + incidentRegister.length());
        return root;
    }

    private static JSONArray extractNamedParties(NativeEvidenceResult nativeEvidence) throws Exception {
        long startedAt = System.currentTimeMillis();
        JSONArray result = new JSONArray();
        if (nativeEvidence == null) {
            logPhase("extractNamedParties", startedAt, "nativeEvidence absent");
            return result;
        }

        List<OcrTextBlock> blocks = allTextBlocks(nativeEvidence);
        Log.d(TAG, "extractNamedParties scanning blocks=" + blocks.size());
        LinkedHashMap<String, Set<Integer>> pagesByName = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> occurrenceCounts = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> headerEvidenceCounts = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> victimCueCounts = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> offenceCueCounts = new LinkedHashMap<>();
        int blockIndex = 0;
        for (OcrTextBlock block : blocks) {
            blockIndex++;
            if (blockIndex % 100 == 0) {
                Log.d(TAG, "extractNamedParties progress block=" + blockIndex + "/" + blocks.size());
            }
            if (block == null || block.text == null || block.confidence <= 0f) continue;
            if (isSecondaryNarrativeText(block.text)) continue;
            String text = block.text.replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) continue;
            Matcher emailMatcher = EMAIL_NAME_PATTERN.matcher(block.text);
            while (emailMatcher.find()) {
                String name = sanitizeActorCandidate(emailMatcher.group(1));
                if (!looksLikePersonName(name)) continue;
                recordNameCandidate(name, block.pageIndex + 1, pagesByName, occurrenceCounts);
                headerEvidenceCounts.put(name, headerEvidenceCounts.getOrDefault(name, 0) + 1);
            }
            Matcher formalMatcher = FORMAL_NAME_PATTERN.matcher(text);
            while (formalMatcher.find()) {
                String name = sanitizeActorCandidate(formalMatcher.group(1));
                if (!looksLikePersonName(name)) continue;
                recordNameCandidate(name, block.pageIndex + 1, pagesByName, occurrenceCounts);
            }
            Matcher complainantMatcher = COMPLAINANT_NAME_PATTERN.matcher(text);
            while (complainantMatcher.find()) {
                String name = sanitizeActorCandidate(complainantMatcher.group(1));
                if (!looksLikePersonName(name)) continue;
                recordNameCandidate(name, block.pageIndex + 1, pagesByName, occurrenceCounts);
                headerEvidenceCounts.put(name, headerEvidenceCounts.getOrDefault(name, 0) + 2);
                victimCueCounts.put(name, victimCueCounts.getOrDefault(name, 0) + 2);
            }
            for (String headerName : extractHeaderPersonNames(text)) {
                if (!looksLikePersonName(headerName)) continue;
                recordNameCandidate(headerName, block.pageIndex + 1, pagesByName, occurrenceCounts);
                headerEvidenceCounts.put(headerName, headerEvidenceCounts.getOrDefault(headerName, 0) + 1);
            }
            for (String victimName : extractVictimRoleNames(text)) {
                if (!looksLikeVictimName(victimName)) {
                    continue;
                }
                recordNameCandidate(victimName, block.pageIndex + 1, pagesByName, occurrenceCounts);
                victimCueCounts.put(victimName, victimCueCounts.getOrDefault(victimName, 0) + 1);
            }
            if (containsPrimaryActorRoleSignal(text)) {
                Matcher genericMatcher = PERSON_PATTERN.matcher(text);
                while (genericMatcher.find()) {
                    String name = sanitizeActorCandidate(genericMatcher.group(1));
                    if (!looksLikePersonName(name)) {
                        continue;
                    }
                    recordNameCandidate(name, block.pageIndex + 1, pagesByName, occurrenceCounts);
                    offenceCueCounts.put(name, offenceCueCounts.getOrDefault(name, 0) + 1);
                }
            }
        }
        if (pagesByName.size() <= 3) {
            collectSmallDocumentNameCandidates(blocks, pagesByName, occurrenceCounts);
        }

        List<Map.Entry<String, Set<Integer>>> ranked = new ArrayList<>(pagesByName.entrySet());
        ranked.sort((left, right) -> {
            int pageCompare = Integer.compare(right.getValue().size(), left.getValue().size());
            if (pageCompare != 0) return pageCompare;
            int occurrenceCompare = Integer.compare(
                    occurrenceCounts.getOrDefault(right.getKey(), 0),
                    occurrenceCounts.getOrDefault(left.getKey(), 0)
            );
            if (occurrenceCompare != 0) return occurrenceCompare;
            return Integer.compare(left.getValue().iterator().next(), right.getValue().iterator().next());
        });

        int emitted = 0;
        boolean lowCandidateMode = pagesByName.size() <= 3;
        for (Map.Entry<String, Set<Integer>> entry : ranked) {
            int occurrences = occurrenceCounts.getOrDefault(entry.getKey(), 0);
            int headerEvidenceCount = headerEvidenceCounts.getOrDefault(entry.getKey(), 0);
            int victimCueCount = victimCueCounts.getOrDefault(entry.getKey(), 0);
            int offenceCueCount = offenceCueCounts.getOrDefault(entry.getKey(), 0);
            boolean victimRoleCandidate = looksLikeVictimRoleCandidate(entry.getKey())
                    && (victimCueCount > 0 || hasAnchoredVictimUsageForParty(nativeEvidence, entry.getKey()));
            boolean reliableIdentity = lowCandidateMode
                    ? isUsableActorName(entry.getKey())
                        && !looksLikeEmailHeader(entry.getKey())
                        && !isJunkActor(entry.getKey())
                        && (headerEvidenceCount >= 1
                            || victimRoleCandidate
                            || offenceCueCount >= 1
                            || occurrences >= 2
                            || hasAnchoredUsageForParty(nativeEvidence, entry.getKey()))
                    : headerEvidenceCount >= 1
                        || victimRoleCandidate
                        || offenceCueCount >= 1
                        || (entry.getValue().size() >= 2 && occurrences >= 2);
            boolean anchoredUsage = lowCandidateMode
                    || victimRoleCandidate
                    || offenceCueCount >= 1
                    || hasAnchoredUsageForParty(nativeEvidence, entry.getKey());
            if (!reliableIdentity || !anchoredUsage) {
                continue;
            }
            if (occurrences <= 1
                    && entry.getValue().size() <= 1
                    && !looksLikePersonName(entry.getKey())
                    && !looksLikeVictimAlias(entry.getKey())) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("name", entry.getKey());
            item.put("type", "PERSON");
            item.put("role", victimRoleCandidate ? "VICTIM" : "ACTOR");
            item.put("actorClass", victimRoleCandidate ? "VICTIM" : "ACTOR");
            JSONArray pages = new JSONArray();
            for (Integer page : entry.getValue()) {
                pages.put(page);
            }
            item.put("pages", pages);
            item.put("firstPage", entry.getValue().iterator().next());
            item.put("occurrences", occurrences);
            item.put("headerEvidenceCount", headerEvidenceCount);
            item.put("victimCueCount", victimCueCount);
            item.put("offenceCueCount", offenceCueCount);
            result.put(item);
            emitted++;
            if (emitted >= 25) break;
        }
        logPhase("extractNamedParties", startedAt,
                "blocks=" + blocks.size() + " candidates=" + pagesByName.size() + " emitted=" + result.length());
        return result;
    }

    private static boolean hasAnchoredUsageForParty(NativeEvidenceResult nativeEvidence, String name) {
        if (nativeEvidence == null || name == null || name.trim().isEmpty()) {
            return false;
        }
        String normalizedName = name.trim().toLowerCase(Locale.ROOT);
        if (nativeEvidence.propositions != null) {
            for (ExtractedProposition proposition : nativeEvidence.propositions) {
                if (proposition == null) {
                    continue;
                }
                if (proposition.actor != null && proposition.actor.trim().equalsIgnoreCase(name)) {
                    return true;
                }
                if (proposition.text != null && proposition.text.toLowerCase(Locale.ROOT).contains(normalizedName)) {
                    return true;
                }
            }
        }
        for (OcrTextBlock block : allTextBlocks(nativeEvidence)) {
            if (block == null || block.text == null) {
                continue;
            }
            String lower = block.text.toLowerCase(Locale.ROOT);
            if (!lower.contains(normalizedName)) {
                continue;
            }
            if (lower.contains("from:")
                    || lower.contains("to:")
                    || lower.contains("subject:")
                    || containsPrimaryActorRoleSignal(lower)
                    || containsAny(lower,
                    "victim",
                    "vulnerable client",
                    "lost his site",
                    "goodwill was stolen",
                    "goodwill stolen",
                    "forced off site",
                    "forced off",
                    "stolen from")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnchoredVictimUsageForParty(NativeEvidenceResult nativeEvidence, String name) {
        if (nativeEvidence == null || name == null || name.trim().isEmpty()) {
            return false;
        }
        String normalizedName = name.trim().toLowerCase(Locale.ROOT);
        for (OcrTextBlock block : allTextBlocks(nativeEvidence)) {
            if (block == null || block.text == null || block.confidence <= 0f) {
                continue;
            }
            String lower = block.text.toLowerCase(Locale.ROOT);
            if (!lower.contains(normalizedName)) {
                continue;
            }
            if (containsAny(lower,
                    "vulnerable client",
                    "victim",
                    "lost his site",
                    "lost her site",
                    "site taken",
                    "goodwill was stolen",
                    "goodwill stolen",
                    "goodwill withheld",
                    "forced off site",
                    "forced off the site",
                    "being told to vacate",
                    "notice to vacate",
                    "mentally broken",
                    "taken advantage of",
                    "exploited")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPrimaryActorRoleSignal(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return containsAny(lower,
                "vulnerable client",
                "victim",
                "goodwill was stolen",
                "goodwill stolen",
                "lost his site",
                "lost her site",
                "forced off site",
                "forced off",
                "stolen from",
                "stolen vessel",
                "theft of",
                "theft",
                "fraudulent registration",
                "unlawful charter",
                "chartering",
                "permit holder",
                "unlawful possession",
                "damage to",
                "damages of",
                "loss of",
                "misappropriat",
                "converted",
                "samsa",
                "dffe");
    }

    private static void collectSmallDocumentNameCandidates(
            List<OcrTextBlock> blocks,
            Map<String, Set<Integer>> pagesByName,
            Map<String, Integer> occurrenceCounts
    ) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        for (OcrTextBlock block : blocks) {
            if (block == null || block.text == null || block.confidence <= 0f) {
                continue;
            }
            if (isSecondaryNarrativeText(block.text)) {
                continue;
            }
            String text = block.text.replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if (!containsAny(lower,
                    "parties:",
                    "party ",
                    "agreement",
                    "contract",
                    "invoice",
                    "email ",
                    "email from",
                    "meeting",
                    "complaint",
                    "payment",
                    "between ",
                    "signed by")) {
                continue;
            }
            Matcher genericMatcher = PERSON_PATTERN.matcher(text);
            while (genericMatcher.find()) {
                String candidate = sanitizeActorCandidate(genericMatcher.group(1));
                if (!looksLikePersonName(candidate)
                        || !isUsableActorName(candidate)
                        || looksLikeEmailHeader(candidate)
                        || isJunkActor(candidate)) {
                    continue;
                }
                recordNameCandidate(candidate, block.pageIndex + 1, pagesByName, occurrenceCounts);
            }
        }
    }

    private static List<String> extractHeaderPersonNames(String text) {
        List<String> names = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return names;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String[] labels = new String[]{"from:", "to:", "cc:", "bcc:"};
        for (String label : labels) {
            int idx = lower.indexOf(label);
            if (idx < 0) {
                continue;
            }
            int end = Math.min(text.length(), idx + 180);
            String slice = text.substring(idx, end);
            Matcher matcher = PERSON_PATTERN.matcher(slice);
            while (matcher.find()) {
                String candidate = sanitizeActorCandidate(matcher.group(1));
                if (looksLikePersonName(candidate) && !names.contains(candidate)) {
                    names.add(candidate);
                }
            }
        }
        return names;
    }

    private static List<String> extractVictimRoleNames(String text) {
        List<String> names = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return names;
        }
        Matcher matcher = PERSON_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = sanitizeActorCandidate(matcher.group(1));
            if (!looksLikeVictimName(candidate) || isAbstractVictimNoise(candidate)) {
                continue;
            }
            int start = Math.max(0, matcher.start() - 80);
            int end = Math.min(text.length(), matcher.end() + 80);
            String window = text.substring(start, end).toLowerCase(Locale.ROOT);
            if (containsAny(window,
                    "vulnerable client",
                    "victim",
                    "lost his site",
                    "lost her site",
                    "forced off site",
                    "forced off",
                    "goodwill was stolen",
                    "goodwill stolen",
                    "stolen from",
                    "on behalf of",
                    "exploited",
                    "harm to",
                    "harm caused to",
                    "your father is not alone",
                    "same method",
                    "same pattern",
                    "same outcome",
                    "make it five",
                    "that is three")) {
                if (!names.contains(candidate)) {
                    names.add(candidate);
                }
            }
        }
        return names;
    }

    private static boolean looksLikeVictimName(String name) {
        return looksLikePersonName(name) || looksLikeVictimAlias(name);
    }

    private static boolean looksLikeVictimRoleCandidate(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return false;
        }
        if (isAbstractVictimNoise(name) || isNamedPartyCandidateNoise(name)) {
            return false;
        }
        if (containsAny(lower,
                "all fuels",
                "bright idea projects",
                "astron",
                "section",
                "which",
                "not",
                "goodwill",
                "outcome",
                "pattern",
                "behind",
                "exposing",
                "racketeering",
                "arbitration",
                "pricing",
                "lessor",
                "lessee",
                "franchisee",
                "franchisor",
                "retail licence",
                "site licence",
                "mublishing",
                "one shell",
                "crompton motors",
                "former way trade",
                "trade and invest",
                "now port edward",
                "port edward garage",
                "operator",
                "permit holder",
                "individuals",
                "matters",
                "common purpose",
                "general manager",
                "legal department")) {
            return false;
        }
        if (containsContractRolePhrase(lower)) {
            return false;
        }
        return looksLikeVictimName(name);
    }

    private static boolean isAbstractVictimNoise(String name) {
        if (name == null) {
            return true;
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        return lower.isEmpty()
                || lower.equals("individuals")
                || lower.equals("matters")
                || lower.equals("common purpose")
                || lower.equals("general manager")
                || lower.equals("legal department")
                || lower.equals("screenshot")
                || lower.equals("account")
                || lower.equals("your phone number");
    }

    private static boolean looksLikeVictimAlias(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.length() < 3 || trimmed.length() > 20 || trimmed.contains(" ")) {
            return false;
        }
        if (looksLikeEmailHeader(trimmed) || NAME_STOPWORDS.contains(trimmed)) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (containsActorNoisePhrase(lower) || containsContractRolePhrase(lower)) {
            return false;
        }
        return !(lower.equals("and")
                || lower.equals("your")
                || lower.equals("yes")
                || lower.equals("now")
                || lower.equals("tue")
                || lower.equals("not")
                || lower.equals("which")
                || lower.equals("section")
                || lower.equals("goodwill")
                || lower.equals("outcome")
                || lower.equals("pattern")
                || lower.equals("behind")
                || lower.equals("exposing")
                || lower.equals("racketeering")
                || lower.equals("arbitration")
                || lower.equals("one")
                || lower.equals("shell")
                || lower.equals("astron")
                || lower.equals("pricing")
                || lower.equals("lessor")
                || lower.equals("lessee")
                || lower.equals("mublishing")
                || lower.equals("crompton")
                || lower.equals("motors")
                || lower.equals("former")
                || lower.equals("trade")
                || lower.equals("invest")
                || lower.equals("port")
                || lower.equals("edward")
                || lower.equals("the")
                || lower.equals("operator")
                || lower.equals("franchisor")
                || lower.equals("franchisee")
                || lower.equals("court")
                || lower.equals("former")
                || lower.equals("trade")
                || lower.equals("invest")
                || lower.equals("case")
                || lower.equals("same"));
    }

    private static String classifyContradictionStatus(String text, String term, String actor, boolean pairedConflict) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        boolean actorResolved = actor != null && !actor.trim().isEmpty() && !"unresolved actor".equalsIgnoreCase(actor);
        boolean propositionConflict = containsAny(lower,
                "never happened", "i never said", "that is not true", "no deal", "we had a deal",
                "fell through", "didn't happen", "did not happen", "deal didn't happen", "proceeded with the deal",
                "order completed", "completed the order", "no exclusivity", "70/30", "30% share",
                "admitted", "denied", "claimed", "stated", "wrote", "responded", "from:", "subject:");
        boolean technicalForgeryContext = containsAny(lower,
                "detect", "detection", "metadata anomaly", "preventing falsification", "forgery detection",
                "anti-fraud", "forensic engine", "verum omnis", "integrity", "tamper", "protocol");
        boolean fakeOnly = "fake".equalsIgnoreCase(term) || "forged".equalsIgnoreCase(term) || "you forged".equalsIgnoreCase(term);

        if (fakeOnly && technicalForgeryContext && !propositionConflict) {
            return "REJECTED";
        }
        if (pairedConflict && actorResolved) {
            return "VERIFIED";
        }
        if (propositionConflict && actorResolved) {
            return "CANDIDATE";
        }
        return "REJECTED";
    }

    private static String classifyConflictType(String text, String term, boolean pairedConflict) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (!pairedConflict && containsAny(lower, "forged", "forgery", "fake", "doctored", "cropped", "tampered")) {
            return "NARRATIVE_THEME";
        }
        if (containsAny(lower, "forged", "forgery", "fake", "doctored", "cropped", "tampered")) {
            return "DOCUMENT_VS_STATEMENT_CONFLICT";
        }
        if (containsAny(lower, "never happened", "i never said", "that is not true", "no deal", "we had a deal")) {
            return "PROPOSITION_CONFLICT";
        }
        if (containsAny(lower, "date", "dated", "timeline", "before", "after", "expired")) {
            return "TIMELINE_CONFLICT";
        }
        if (containsAny(lower, "delete this", "off the record", "other phone", "keep it off")) {
            return "SOURCE_VS_SOURCE_CONFLICT";
        }
        return "METADATA_VS_CONTENT_CONFLICT";
    }

    private static String ordinalConfidenceForContradiction(String status, String actor, boolean hasDate, boolean pairedConflict) {
        boolean actorResolved = actor != null && !actor.trim().isEmpty() && !"unresolved actor".equalsIgnoreCase(actor);
        if ("VERIFIED".equals(status)) {
            return pairedConflict && hasDate && actorResolved ? "HIGH" : "MODERATE";
        }
        if ("CANDIDATE".equals(status)) {
            return actorResolved && hasDate ? "MODERATE" : "LOW";
        }
        return "LOW";
    }

    private static String explainContradictionClassification(String text, String term, String status, boolean pairedConflict) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if ("REJECTED".equals(status)) {
            return "This entry appears to be explanatory or technical anti-forgery language rather than a proven contradiction between two anchored propositions.";
        }
        if ("VERIFIED".equals(status)) {
        if (containsAny(lower, "fell through", "never happened", "deal didn't happen", "didn't happen", "did not happen", "proceeded with the deal", "order completed", "completed the order")) {
                return "Anchored deal-status language conflicts with a separately anchored admission or execution statement elsewhere in the record.";
            }
            if (containsAny(lower, "no exclusivity", "70/30", "30% share", "profit share")) {
                return "Anchored agreement-scope language conflicts with a separately anchored profit-share or split statement elsewhere in the record.";
            }
            return "This entry survives contradiction testing because two anchored propositions materially conflict in an actor-attributed context.";
        }
        if (containsAny(lower, "fake", "forged", "forgery")) {
            return "This entry is a contradiction candidate only. It refers to alleged fake or forged material, but a direct conflicting proposition still needs to be paired and anchored.";
        }
        return "This entry contains contradiction-linked language that still requires surrounding context and a paired conflicting proposition before it can be treated as verified.";
    }

    private static boolean containsAny(String text, String... needles) {
        if (text == null || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static JSONObject buildPropositionObject(String text, int page) throws Exception {
        JSONObject proposition = new JSONObject();
        proposition.put("text", text);
        proposition.put("anchor", buildAnchorObject(page));
        proposition.put("actor", "unresolved actor");
        proposition.put("target", "");
        proposition.put("dateOrRange", "");
        proposition.put("amount", "");
        proposition.put("currency", "");
        proposition.put("isNegated", false);
        proposition.put("confidence", "");
        return proposition;
    }

    private static JSONObject buildAnchorObject(int page) throws Exception {
        JSONObject anchor = new JSONObject();
        anchor.put("page", page);
        if (page > 0) {
            anchor.put("blockId", "page-" + page);
            anchor.put("exhibitId", String.format(Locale.US, "EX-%03d", page));
            anchor.put("fileOffset", (page - 1L) * 1_000_000L);
        }
        return anchor;
    }

    private static JSONObject buildPropositionObject(ExtractedProposition extracted) throws Exception {
        JSONObject proposition = buildPropositionObject(
                truncate(extracted != null ? extracted.text : "", 220),
                extracted != null && !extracted.anchors.isEmpty() ? extracted.anchors.get(0).page : 0
        );
        if (extracted == null) {
            return proposition;
        }
        proposition.put("actor", extracted.actor);
        proposition.put("target", extracted.target);
        proposition.put("dateOrRange", extracted.dateOrRange);
        proposition.put("amount", extracted.amount);
        proposition.put("currency", extracted.currency);
        proposition.put("locationHint", extractLocationHint(extracted.text));
        proposition.put("isNegated", extracted.isNegated);
        proposition.put("confidence", extracted.confidence);
        if (!extracted.anchors.isEmpty()) {
            proposition.put("anchor", extracted.anchors.get(0).toJson());
        }
        return proposition;
    }

    private static void appendStructuredContradictionFindings(
            LinkedHashMap<String, JSONObject> deduped,
            NativeEvidenceResult nativeEvidence,
            List<OcrTextBlock> blocks,
            JSONArray namedParties
    ) throws Exception {
        List<JSONObject> propositions = buildStructuredPropositions(nativeEvidence, blocks, namedParties);
        if (propositions.isEmpty()) {
            return;
        }

        for (int i = 0; i < propositions.size(); i++) {
            JSONObject left = propositions.get(i);
            for (int j = i + 1; j < propositions.size(); j++) {
                JSONObject right = propositions.get(j);
                if (!belongsToSameComparisonGroup(left, right)) {
                    continue;
                }
                String conflictType = detectDeterministicConflictType(left, right);
                if (conflictType.isEmpty()) {
                    continue;
                }

                String leftActor = left.optString("actor", "");
                String rightActor = right.optString("actor", "");
                boolean sameActor = !leftActor.isEmpty()
                        && !rightActor.isEmpty()
                        && !"unresolved actor".equalsIgnoreCase(leftActor)
                        && leftActor.equalsIgnoreCase(rightActor);
                boolean interActor = !sameActor && isInterActorComparable(left, right);
                int pageA = left.optInt("page", 0);
                int pageB = right.optInt("page", 0);
                boolean actorAttributed = sameActor
                        || interActor
                        || refersToActor(left.optString("text", ""), rightActor)
                        || refersToActor(right.optString("text", ""), leftActor);
                boolean distinctAnchors = pageA > 0 && pageB > 0 && pageA != pageB;
                boolean compactCorroboration = isCompactEvidenceSet(nativeEvidence, blocks)
                        && sameActor
                        && actorAttributed
                        && hasCompactContradictionCorroboration(left, right);
                boolean verified = actorAttributed && (distinctAnchors || compactCorroboration);
                if (interActor) {
                    verified = distinctAnchors
                            && isCompatibleInterActorTypePair(left.optString("type", ""), right.optString("type", ""))
                            && interActorTopicStrength(left, right) >= 0.40d;
                }
                String status = verified ? "VERIFIED" : "CANDIDATE";
                String actor = sameActor ? leftActor : resolvePrimaryContradictionActor(left, right);
                String target = interActor
                        ? resolveSecondaryContradictionActor(left, right)
                        : firstNonEmptyNonBlank(left.optString("target", ""), right.optString("target", ""));
                List<ConstitutionalConfig.ContradictionRule> matchedRules = matchContradictionRules(
                        left.optString("text", "") + " " + right.optString("text", ""),
                        actor,
                        verified,
                        conflictType
                );
                status = applyContradictionRuleStatus(status, matchedRules, verified, actor);
                String confidence = applyContradictionRuleConfidence(
                        deriveTypedContradictionConfidence(conflictType, left, right, verified, distinctAnchors),
                        matchedRules,
                        verified
                );
                JSONObject item = new JSONObject();
                item.put("page", Math.min(nonZeroOrMax(pageA), nonZeroOrMax(pageB)) == Integer.MAX_VALUE ? pageA : Math.min(nonZeroOrMax(pageA), nonZeroOrMax(pageB)));
                item.put("matchedPhrase", conflictType.toLowerCase(Locale.ROOT).replace('_', ' '));
                item.put("actor", actor);
                item.put("target", target);
                item.put("excerpt", truncate(left.optString("text", "") + " || " + right.optString("text", ""), 260));
                item.put("status", status);
                item.put("confidence", confidence);
                item.put("conflictType", conflictType);
                item.put("findingType", "CONTRADICTION");
                item.put("whyItConflicts", explainTypedContradiction(conflictType));
                if (interActor) {
                    item.put("secondaryActor", target);
                    item.put("topicOverlap", semanticTopicOverlap(left, right));
                }
                putContradictionRuleMetadata(item, matchedRules);
                if ("CANDIDATE".equalsIgnoreCase(status)) {
                    item.put("neededEvidence", neededEvidenceForTypedContradiction(conflictType, actor));
                }
                item.put("propositionA", new JSONObject(left.toString()));
                item.put("propositionB", new JSONObject(right.toString()));
                item.put("layer", status);
                JSONArray anchors = new JSONArray();
                JSONObject anchorA = left.optJSONObject("anchor");
                JSONObject anchorB = right.optJSONObject("anchor");
                if (anchorA != null) {
                    anchors.put(anchorA);
                } else if (pageA > 0) {
                    anchors.put(buildAnchorObject(pageA));
                }
                if (anchorB != null) {
                    if (!sameAnchor(anchorA, anchorB)) {
                        anchors.put(anchorB);
                    }
                } else if (pageB > 0 && pageB != pageA) {
                    anchors.put(buildAnchorObject(pageB));
                }
                item.put("anchors", anchors);
                item.put("primaryEvidence", verified);
                item.put("supportOnly", !verified);

                String dedupeKey = "structured|"
                        + actor.toLowerCase(Locale.ROOT) + "|"
                        + target.toLowerCase(Locale.ROOT) + "|"
                        + conflictType + "|"
                        + normalizeComparisonText(left.optString("text", "")) + "|"
                        + normalizeComparisonText(right.optString("text", ""));
                JSONObject existing = deduped.get(dedupeKey);
                if (existing == null || ("VERIFIED".equals(status) && !"VERIFIED".equalsIgnoreCase(existing.optString("status", "")))) {
                    deduped.put(dedupeKey, item);
                }
            }
        }
    }

    private static boolean belongsToSameComparisonGroup(JSONObject left, JSONObject right) {
        if (left == null || right == null) {
            return false;
        }
        String leftActor = left.optString("actor", "").trim();
        String rightActor = right.optString("actor", "").trim();
        if (leftActor.isEmpty()
                || rightActor.isEmpty()
                || "unresolved actor".equalsIgnoreCase(leftActor)
                || "unresolved actor".equalsIgnoreCase(rightActor)) {
            return false;
        }
        if (!leftActor.equalsIgnoreCase(rightActor)) {
            return isInterActorComparable(left, right);
        }
        String leftTarget = left.optString("target", "").trim();
        String rightTarget = right.optString("target", "").trim();
        if (leftTarget.isEmpty() || rightTarget.isEmpty()) {
            return true;
        }
        return leftTarget.equalsIgnoreCase(rightTarget)
                || left.optString("text", "").toLowerCase(Locale.ROOT).contains(rightTarget.toLowerCase(Locale.ROOT))
                || right.optString("text", "").toLowerCase(Locale.ROOT).contains(leftTarget.toLowerCase(Locale.ROOT));
    }

    private static String detectDeterministicConflictType(JSONObject left, JSONObject right) {
        if (isInterActorComparable(left, right)) {
            return "INTER_ACTOR_CONFLICT";
        }
        if (isNegationConflict(left, right)) {
            return "NEGATION";
        }
        if (isNumericConflict(left, right)) {
            return "NUMERIC";
        }
        if (isTimelineConflict(left, right)) {
            return "TIMELINE";
        }
        if (isLocationConflict(left, right)) {
            return "LOCATION";
        }
        return "";
    }

    private static boolean isInterActorComparable(JSONObject left, JSONObject right) {
        if (left == null || right == null) {
            return false;
        }
        String leftActor = left.optString("actor", "").trim();
        String rightActor = right.optString("actor", "").trim();
        if (leftActor.isEmpty()
                || rightActor.isEmpty()
                || "unresolved actor".equalsIgnoreCase(leftActor)
                || "unresolved actor".equalsIgnoreCase(rightActor)
                || leftActor.equalsIgnoreCase(rightActor)) {
            return false;
        }
        String leftType = left.optString("type", "");
        String rightType = right.optString("type", "");
        boolean denialAdmissionPair =
                (isDenialPropositionType(leftType) && isAdmissionPropositionType(rightType))
                        || (isAdmissionPropositionType(leftType) && isDenialPropositionType(rightType));
        if (!denialAdmissionPair) {
            return false;
        }
        if (!isCompatibleInterActorTypePair(left.optString("type", ""), right.optString("type", ""))) {
            return false;
        }
        return interActorTopicStrength(left, right) >= 0.40d;
    }

    private static boolean isCompatibleInterActorTypePair(String leftType, String rightType) {
        if (!(isDenialPropositionType(leftType) && isAdmissionPropositionType(rightType))
                && !(isAdmissionPropositionType(leftType) && isDenialPropositionType(rightType))) {
            return false;
        }
        return ("DEAL_DENIAL".equalsIgnoreCase(leftType) && "DEAL_EXECUTION".equalsIgnoreCase(rightType))
                || ("DEAL_EXECUTION".equalsIgnoreCase(leftType) && "DEAL_DENIAL".equalsIgnoreCase(rightType))
                || ("NO_EXCLUSIVITY".equalsIgnoreCase(leftType) && "DEAL_EXECUTION".equalsIgnoreCase(rightType))
                || ("DEAL_EXECUTION".equalsIgnoreCase(leftType) && "NO_EXCLUSIVITY".equalsIgnoreCase(rightType))
                || ("NO_EXCLUSIVITY".equalsIgnoreCase(leftType) && "PROFIT_SHARE".equalsIgnoreCase(rightType))
                || ("PROFIT_SHARE".equalsIgnoreCase(leftType) && "NO_EXCLUSIVITY".equalsIgnoreCase(rightType))
                || ("NON_PAYMENT".equalsIgnoreCase(leftType) && "PAYMENT_CLAIM".equalsIgnoreCase(rightType))
                || ("PAYMENT_CLAIM".equalsIgnoreCase(leftType) && "NON_PAYMENT".equalsIgnoreCase(rightType));
    }

    private static double interActorTopicStrength(JSONObject left, JSONObject right) {
        double overlap = semanticTopicOverlap(left, right);
        if (sharesDeterministicTopicFamily(left, right)) {
            overlap = Math.max(overlap, 0.50d);
        }
        String combined = (left.optString("text", "") + " " + right.optString("text", "")).toLowerCase(Locale.ROOT);
        if (containsAny(combined,
                "deal",
                "agreement",
                "exclusive",
                "exclusivity",
                "export",
                "invoice",
                "payment",
                "profit share")) {
            overlap = Math.max(overlap, 0.45d);
        }
        return overlap;
    }

    private static boolean isDenialPropositionType(String type) {
        return "DEAL_DENIAL".equalsIgnoreCase(type)
                || "NO_EXCLUSIVITY".equalsIgnoreCase(type)
                || "NON_PAYMENT".equalsIgnoreCase(type);
    }

    private static boolean isAdmissionPropositionType(String type) {
        return "DEAL_EXECUTION".equalsIgnoreCase(type)
                || "PROFIT_SHARE".equalsIgnoreCase(type)
                || "PAYMENT_CLAIM".equalsIgnoreCase(type);
    }

    private static String resolvePrimaryContradictionActor(JSONObject left, JSONObject right) {
        String leftType = left.optString("type", "");
        String rightType = right.optString("type", "");
        if (isDenialPropositionType(leftType) && isAdmissionPropositionType(rightType)) {
            return left.optString("actor", "unresolved actor");
        }
        if (isDenialPropositionType(rightType) && isAdmissionPropositionType(leftType)) {
            return right.optString("actor", "unresolved actor");
        }
        return firstNonEmptyNonBlank(left.optString("actor", ""), right.optString("actor", ""), "unresolved actor");
    }

    private static String resolveSecondaryContradictionActor(JSONObject left, JSONObject right) {
        String primary = resolvePrimaryContradictionActor(left, right);
        String leftActor = left.optString("actor", "").trim();
        String rightActor = right.optString("actor", "").trim();
        if (!leftActor.isEmpty() && !leftActor.equalsIgnoreCase(primary)) {
            return leftActor;
        }
        if (!rightActor.isEmpty() && !rightActor.equalsIgnoreCase(primary)) {
            return rightActor;
        }
        return "";
    }

    private static boolean isNegationConflict(JSONObject left, JSONObject right) {
        boolean leftNegated = left.optBoolean("isNegated", false);
        boolean rightNegated = right.optBoolean("isNegated", false);
        if (leftNegated == rightNegated) {
            return false;
        }
        return textSimilarity(normalizeComparisonText(left.optString("text", "")),
                normalizeComparisonText(right.optString("text", ""))) >= 0.45d;
    }

    private static boolean isNumericConflict(JSONObject left, JSONObject right) {
        double leftAmount = parseAmountValue(left.optString("amount", ""));
        double rightAmount = parseAmountValue(right.optString("amount", ""));
        if (leftAmount <= 0d || rightAmount <= 0d) {
            return false;
        }
        String leftCurrency = left.optString("currency", "");
        String rightCurrency = right.optString("currency", "");
        if (!leftCurrency.isEmpty() && !rightCurrency.isEmpty() && !leftCurrency.equalsIgnoreCase(rightCurrency)) {
            return false;
        }
        return Math.abs(leftAmount - rightAmount) > Math.max(1.0d, Math.max(leftAmount, rightAmount) * 0.05d);
    }

    private static boolean isTimelineConflict(JSONObject left, JSONObject right) {
        long leftDate = parseComparableDate(left.optString("dateOrRange", ""));
        long rightDate = parseComparableDate(right.optString("dateOrRange", ""));
        if (leftDate <= 0L || rightDate <= 0L || leftDate == rightDate) {
            return false;
        }
        return textSimilarity(normalizeComparisonText(left.optString("text", "")),
                normalizeComparisonText(right.optString("text", ""))) >= 0.35d;
    }

    private static boolean isLocationConflict(JSONObject left, JSONObject right) {
        String leftActor = left.optString("actor", "").trim();
        String rightActor = right.optString("actor", "").trim();
        if (leftActor.isEmpty()
                || rightActor.isEmpty()
                || "unresolved actor".equalsIgnoreCase(leftActor)
                || !leftActor.equalsIgnoreCase(rightActor)) {
            return false;
        }
        String leftLocation = firstNonEmptyNonBlank(
                left.optString("locationHint", ""),
                extractLocationHint(left.optString("text", ""))
        );
        String rightLocation = firstNonEmptyNonBlank(
                right.optString("locationHint", ""),
                extractLocationHint(right.optString("text", ""))
        );
        if (leftLocation.isEmpty() || rightLocation.isEmpty() || leftLocation.equalsIgnoreCase(rightLocation)) {
            return false;
        }
        String combined = (left.optString("text", "") + " " + right.optString("text", "")).toLowerCase(Locale.ROOT);
        if (containsAny(combined, "moved to", "relocated", "transfer", "transferred", "later at", "later moved")) {
            return false;
        }
        return containsAny(combined,
                "site",
                "service station",
                "petrol station",
                "garage",
                "goodwill",
                "rent",
                "owner",
                "operator",
                "forced off");
    }

    private static String extractLocationHint(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("umtentweni")) {
            return "Umtentweni";
        }
        if (lower.contains("port edward garage") || lower.contains("port edward")) {
            return "Port Edward";
        }
        if (lower.contains("uxbridge")) {
            return "Uxbridge";
        }
        if (lower.contains("thongasi")) {
            return "Thongasi";
        }
        if (lower.contains("wayne's world")) {
            return "Wayne's World";
        }
        return "";
    }

    private static String deriveTypedContradictionConfidence(
            String conflictType,
            JSONObject left,
            JSONObject right,
            boolean verified,
            boolean distinctAnchors
    ) {
        if (!verified) {
            return "MODERATE";
        }
        if ("NUMERIC".equalsIgnoreCase(conflictType) || "TIMELINE".equalsIgnoreCase(conflictType)) {
            return distinctAnchors ? "HIGH" : "MODERATE";
        }
        if ("LOCATION".equalsIgnoreCase(conflictType)) {
            return distinctAnchors ? "HIGH" : "MODERATE";
        }
        if ("NEGATION".equalsIgnoreCase(conflictType)) {
            double similarity = textSimilarity(
                    normalizeComparisonText(left.optString("text", "")),
                    normalizeComparisonText(right.optString("text", ""))
            );
            return similarity >= 0.60d && distinctAnchors ? "HIGH" : "MODERATE";
        }
        if ("INTER_ACTOR_CONFLICT".equalsIgnoreCase(conflictType)) {
            return interActorTopicStrength(left, right) >= 0.60d && distinctAnchors ? "VERY_HIGH" : "HIGH";
        }
        return distinctAnchors ? "HIGH" : "LOW";
    }

    private static String explainTypedContradiction(String conflictType) {
        if ("INTER_ACTOR_CONFLICT".equalsIgnoreCase(conflictType)) {
            return "Two different named actors are anchored to materially conflicting admission-versus-denial statements on the same topic.";
        }
        if ("NEGATION".equalsIgnoreCase(conflictType)) {
            return "Two anchored propositions describe the same event or claim, but one affirms it while the other negates it.";
        }
        if ("NUMERIC".equalsIgnoreCase(conflictType)) {
            return "Two anchored propositions describe the same financial or quantitative event but report materially different amounts.";
        }
        if ("TIMELINE".equalsIgnoreCase(conflictType)) {
            return "Two anchored propositions describe the same event chain but place it on materially different dates or time ranges.";
        }
        if ("LOCATION".equalsIgnoreCase(conflictType)) {
            return "Two anchored propositions bind the same actor to materially different locations without a clear transition record.";
        }
        return "Two anchored propositions materially conflict.";
    }

    private static String neededEvidenceForTypedContradiction(String conflictType, String actor) {
        if ("INTER_ACTOR_CONFLICT".equalsIgnoreCase(conflictType)) {
            return "Obtain the surrounding primary correspondence or contract record that fixes which actor's account is true on the disputed topic for " + actor + ".";
        }
        if ("NEGATION".equalsIgnoreCase(conflictType)) {
            return "Obtain an additional primary record that fixes whether the disputed event did or did not occur for " + actor + ".";
        }
        if ("NUMERIC".equalsIgnoreCase(conflictType)) {
            return "Obtain a contract schedule, invoice, ledger, or bank proof that fixes the amount for " + actor + ".";
        }
        if ("TIMELINE".equalsIgnoreCase(conflictType)) {
            return "Obtain a dated primary record that fixes the sequence or date of the disputed event for " + actor + ".";
        }
        if ("LOCATION".equalsIgnoreCase(conflictType)) {
            return "Obtain a primary record that fixes the correct site, premises, or geographic anchor for " + actor + ".";
        }
        return "Obtain an additional anchored primary record to resolve the contradiction.";
    }

    private static boolean sameAnchor(JSONObject left, JSONObject right) {
        if (left == null || right == null) {
            return false;
        }
        return left.optInt("page", 0) == right.optInt("page", 0)
                && left.optString("blockId", "").equalsIgnoreCase(right.optString("blockId", ""));
    }

    private static String normalizeComparisonText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(no|not|never|none|without|did not|didn't|cannot|can't|won't|refused|denied|deny|failed)\\b", " ")
                .replaceAll("\\b(r|zar|usd|aed|eur|gbp|million|billion|jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)\\b", " ")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static double textSimilarity(String left, String right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0d;
        }
        LinkedHashSet<String> leftTokens = new LinkedHashSet<>(Arrays.asList(left.split(" ")));
        LinkedHashSet<String> rightTokens = new LinkedHashSet<>(Arrays.asList(right.split(" ")));
        leftTokens.remove("");
        rightTokens.remove("");
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0d;
        }
        LinkedHashSet<String> union = new LinkedHashSet<>(leftTokens);
        union.addAll(rightTokens);
        LinkedHashSet<String> intersection = new LinkedHashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        return union.isEmpty() ? 0d : ((double) intersection.size() / (double) union.size());
    }

    private static double semanticTopicOverlap(JSONObject left, JSONObject right) {
        LinkedHashSet<String> leftTokens = extractTopicTokens(left != null ? left.optString("text", "") : "");
        LinkedHashSet<String> rightTokens = extractTopicTokens(right != null ? right.optString("text", "") : "");
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0d;
        }
        LinkedHashSet<String> union = new LinkedHashSet<>(leftTokens);
        union.addAll(rightTokens);
        LinkedHashSet<String> intersection = new LinkedHashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        return union.isEmpty() ? 0d : ((double) intersection.size() / (double) union.size());
    }

    private static boolean sharesDeterministicTopicFamily(JSONObject left, JSONObject right) {
        LinkedHashSet<String> leftFamilies = extractTopicFamilies(left);
        LinkedHashSet<String> rightFamilies = extractTopicFamilies(right);
        if (leftFamilies.isEmpty() || rightFamilies.isEmpty()) {
            return false;
        }
        leftFamilies.retainAll(rightFamilies);
        return !leftFamilies.isEmpty();
    }

    private static LinkedHashSet<String> extractTopicFamilies(JSONObject proposition) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (proposition == null) {
            return out;
        }
        String type = proposition.optString("type", "");
        if ("DEAL_EXECUTION".equalsIgnoreCase(type) || "DEAL_DENIAL".equalsIgnoreCase(type)) {
            out.add("deal");
            out.add("transaction");
        }
        if ("NO_EXCLUSIVITY".equalsIgnoreCase(type)) {
            out.add("deal");
            out.add("agreement");
        }
        if ("PROFIT_SHARE".equalsIgnoreCase(type)) {
            out.add("payment");
            out.add("profit_share");
        }
        if ("PAYMENT_CLAIM".equalsIgnoreCase(type) || "NON_PAYMENT".equalsIgnoreCase(type)) {
            out.add("payment");
        }
        for (String token : extractTopicTokens(proposition.optString("text", ""))) {
            String family = canonicalTopicFamily(token);
            if (!family.isEmpty()) {
                out.add(family);
            }
        }
        return out;
    }

    private static LinkedHashSet<String> extractTopicTokens(String text) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String normalized = normalizeComparisonText(text);
        if (normalized.isEmpty()) {
            return out;
        }
        for (String token : normalized.split(" ")) {
            String trimmed = token == null ? "" : token.trim();
            if (trimmed.length() < 3) {
                continue;
            }
            if (!canonicalTopicFamily(trimmed).isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static String canonicalTopicFamily(String token) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT).trim();
        if (lower.isEmpty()) {
            return "";
        }
        if (containsAny(lower, "deal", "order", "shipment", "export", "transaction")) {
            return "deal";
        }
        if (containsAny(lower, "agreement", "contract", "exclusive", "exclusivity")) {
            return "agreement";
        }
        if (containsAny(lower, "invoice", "payment", "paid", "profit", "share")) {
            return "payment";
        }
        if (containsAny(lower, "meeting", "access", "email", "camera", "theft", "fraud")) {
            return lower;
        }
        return "";
    }

    private static double parseAmountValue(String amount) {
        if (amount == null || amount.trim().isEmpty()) {
            return 0d;
        }
        String normalized = amount.toLowerCase(Locale.ROOT).replaceAll("[^0-9.,]", "");
        if (normalized.isEmpty()) {
            return 0d;
        }
        normalized = normalized.replace(",", "");
        try {
            return Double.parseDouble(normalized);
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private static long parseComparableDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return -1L;
        }
        String trimmed = value.trim();
        try {
            Matcher structured = STRUCTURED_DATE_PATTERN.matcher(trimmed);
            if (structured.find()) {
                String[] parts = structured.group().trim().split("/");
                if (parts.length == 3) {
                    if (parts[0].length() == 4) {
                        return Long.parseLong(parts[0] + padDatePart(parts[1]) + padDatePart(parts[2]));
                    }
                    return normalizeDayMonthYear(parts[0], parts[1], parts[2]);
                }
            }
            Matcher generic = Pattern.compile("(\\d{1,2})\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\s*(\\d{4})?",
                    Pattern.CASE_INSENSITIVE).matcher(trimmed.toLowerCase(Locale.ROOT).replace(",", " "));
            if (generic.find()) {
                String day = generic.group(1);
                String month = monthNumber(generic.group(2));
                String year = generic.group(3);
                if (year == null || year.trim().isEmpty()) {
                    year = "0000";
                }
                return Long.parseLong(year + month + padDatePart(day));
            }
        } catch (Exception ignored) {
        }
        return -1L;
    }

    private static String padDatePart(String value) {
        if (value == null) {
            return "00";
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() == 1) {
            return "0" + digits;
        }
        return digits.length() >= 2 ? digits.substring(0, 2) : "00";
    }

    private static long normalizeDayMonthYear(String day, String month, String year) {
        String yyyy = year != null && year.trim().length() == 2 ? "20" + year.trim() : year.trim();
        return Long.parseLong(yyyy + padDatePart(month) + padDatePart(day));
    }

    private static String monthNumber(String month) {
        String lower = month == null ? "" : month.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jan")) return "01";
        if (lower.startsWith("feb")) return "02";
        if (lower.startsWith("mar")) return "03";
        if (lower.startsWith("apr")) return "04";
        if (lower.equals("may")) return "05";
        if (lower.startsWith("jun")) return "06";
        if (lower.startsWith("jul")) return "07";
        if (lower.startsWith("aug")) return "08";
        if (lower.startsWith("sep")) return "09";
        if (lower.startsWith("oct")) return "10";
        if (lower.startsWith("nov")) return "11";
        if (lower.startsWith("dec")) return "12";
        return "00";
    }

    private static int nonZeroOrMax(int value) {
        return value > 0 ? value : Integer.MAX_VALUE;
    }

    private static boolean hasCompactContradictionCorroboration(JSONObject left, JSONObject right) {
        if (left == null || right == null) {
            return false;
        }
        String leftText = left.optString("text", "").replaceAll("\\s+", " ").trim();
        String rightText = right.optString("text", "").replaceAll("\\s+", " ").trim();
        if (leftText.isEmpty() || rightText.isEmpty()) {
            return false;
        }
        if (leftText.equalsIgnoreCase(rightText)) {
            return false;
        }
        return left.optInt("page", 0) > 0 && right.optInt("page", 0) > 0;
    }

    private static boolean isCompactEvidenceSet(NativeEvidenceResult nativeEvidence, List<OcrTextBlock> blocks) {
        if (nativeEvidence != null) {
            int sourcePages = nativeEvidence.sourcePageCount > 0 ? nativeEvidence.sourcePageCount : nativeEvidence.pageCount;
            if (sourcePages > 0 && sourcePages <= 3) {
                return true;
            }
            if (nativeEvidence.renderedPageCount > 0 && nativeEvidence.renderedPageCount <= 3) {
                return true;
            }
        }
        return blocks != null && blocks.size() > 0 && blocks.size() <= 8;
    }

    private static List<JSONObject> buildStructuredPropositions(
            NativeEvidenceResult nativeEvidence,
            List<OcrTextBlock> blocks,
            JSONArray namedParties
    ) throws Exception {
        LinkedHashMap<String, JSONObject> propositions = new LinkedHashMap<>();
        if (nativeEvidence != null && !nativeEvidence.propositions.isEmpty()) {
            for (ExtractedProposition extracted : nativeEvidence.propositions) {
                if (extracted == null || extracted.text == null || extracted.text.trim().isEmpty()) {
                    continue;
                }
                String type = classifyPropositionType(extracted.text);
                if (type.isEmpty()) {
                    continue;
                }
                JSONObject proposition = new JSONObject();
                proposition.put("type", type);
                proposition.put("text", truncate(extracted.text, 220));
                proposition.put("page", extracted.anchors.isEmpty() ? 0 : extracted.anchors.get(0).page);
                proposition.put("actor", resolvePropositionActor(extracted.actor, extracted.text, namedParties));
                proposition.put("target", extracted.target);
                proposition.put("dateOrRange", extracted.dateOrRange);
                proposition.put("amount", extracted.amount);
                proposition.put("currency", extracted.currency);
                proposition.put("locationHint", extractLocationHint(extracted.text));
                proposition.put("isNegated", extracted.isNegated);
                proposition.put("confidence", extracted.confidence);
                proposition.put("anchor", extracted.anchors.isEmpty() ? buildAnchorObject(0) : extracted.anchors.get(0).toJson());
                appendStructuredProposition(propositions, proposition);
            }
        }

        if (blocks == null || blocks.isEmpty()) {
            return new ArrayList<>(propositions.values());
        }
        for (OcrTextBlock block : blocks) {
            if (block == null || block.text == null || block.confidence <= 0f) {
                continue;
            }
            if (isSecondaryNarrativeText(block.text)) {
                continue;
            }
            String type = classifyPropositionType(block.text);
            if (type.isEmpty()) {
                continue;
            }
            JSONObject proposition = new JSONObject();
            proposition.put("type", type);
            proposition.put("text", truncate(block.text, 220));
            proposition.put("page", block.pageIndex + 1);
            proposition.put("actor", inferSubjectActor(block.text, namedParties));
            proposition.put("target", "");
            proposition.put("dateOrRange", extractAnchorDate(block.text));
            proposition.put("amount", extractMoneyAmount(block.text));
            proposition.put("currency", extractMoneyCurrency(block.text));
            proposition.put("locationHint", extractLocationHint(block.text));
            proposition.put("isNegated", containsAny(block.text.toLowerCase(Locale.ROOT), " not ", " never ", " no ", " denied ", " refused ", " did not ", " didn't "));
            proposition.put("confidence", block.confidence >= 0.9f ? "HIGH" : (block.confidence >= 0.75f ? "MODERATE" : "LOW"));
            proposition.put("anchor", buildAnchorObject(block.pageIndex + 1));
            appendStructuredProposition(propositions, proposition);
        }
        return new ArrayList<>(propositions.values());
    }

    private static void appendStructuredProposition(
            LinkedHashMap<String, JSONObject> propositions,
            JSONObject proposition
    ) throws Exception {
        if (propositions == null || proposition == null || !isUsableStructuredProposition(proposition)) {
            return;
        }
        String actor = proposition.optString("actor", "").trim().toLowerCase(Locale.ROOT);
        String type = proposition.optString("type", "").trim();
        int page = proposition.optInt("page", 0);
        String text = normalizeComparisonText(proposition.optString("text", ""));
        String key = type + "|" + actor + "|" + page + "|" + text;
        JSONObject existing = propositions.get(key);
        if (existing == null) {
            propositions.put(key, proposition);
            return;
        }
        if (confidenceRank(proposition.optString("confidence", "")) > confidenceRank(existing.optString("confidence", ""))) {
            propositions.put(key, proposition);
        }
    }

    private static boolean isUsableStructuredProposition(JSONObject proposition) {
        if (proposition == null) {
            return false;
        }
        String type = proposition.optString("type", "").trim();
        String text = proposition.optString("text", "").replaceAll("\\s+", " ").trim();
        String actor = proposition.optString("actor", "").trim();
        if (type.isEmpty() || text.isEmpty() || text.length() < 18) {
            return false;
        }
        if (!isEvidenceResolvedActor(actor)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (isSecondaryNarrativeText(text)
                || containsAny(lower,
                "shareholder dispute case file",
                "breach of fiduciary duty",
                "cybercrime",
                "fraudulent evidence",
                "emotional exploitation",
                "relevant legal violations",
                "court ready declaration")) {
            return false;
        }
        return containsAny(lower,
                "proceeded with the deal",
                "went ahead with the deal",
                "executed the deal",
                "order completed",
                "completed the order",
                "thanks for the invoice",
                "thank you for the invoice",
                "no exclusivity",
                "exclusive agreement",
                "agreement ever existed",
                "deal didn't happen",
                "never happened",
                "fell through",
                "profit share",
                "share of profits",
                "30% share",
                "30 percent",
                "already paid",
                "paid in full",
                "not paid",
                "never paid",
                "unpaid");
    }

    private static int confidenceRank(String confidence) {
        String upper = confidence == null ? "" : confidence.trim().toUpperCase(Locale.ROOT);
        if ("VERY_HIGH".equals(upper)) {
            return 4;
        }
        if ("HIGH".equals(upper)) {
            return 3;
        }
        if ("MODERATE".equals(upper)) {
            return 2;
        }
        if ("LOW".equals(upper)) {
            return 1;
        }
        return 0;
    }

    private static String firstNonEmptyNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static JSONArray extractPropositionRegister(NativeEvidenceResult nativeEvidence) throws Exception {
        JSONArray result = new JSONArray();
        if (nativeEvidence == null || nativeEvidence.propositions.isEmpty()) {
            return result;
        }
        for (ExtractedProposition proposition : nativeEvidence.propositions) {
            result.put(proposition.toJson());
        }
        return result;
    }

    private static boolean refersToActor(String text, String actor) {
        if (text == null || actor == null || actor.trim().isEmpty() || "unresolved actor".equalsIgnoreCase(actor)) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(actor.toLowerCase(Locale.ROOT));
    }

    private static String classifyPropositionType(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (containsAny(lower,
                "fell through",
                "never happened",
                "deal didn't happen",
                "didn't happen",
                "did not happen",
                "no deal",
                "did not proceed",
                "never proceeded")) {
            return "DEAL_DENIAL";
        }
        if (containsAny(lower,
                "proceeded with the deal",
                "went ahead with the deal",
                "executed the deal",
                "order completed",
                "completed the order",
                "shipment",
                "thanks for the invoice",
                "thank you for the invoice",
                "invoice accepted")) {
            return "DEAL_EXECUTION";
        }
        if (containsAny(lower,
                "no exclusivity",
                "not exclusive",
                "no exclusivity agreement",
                "no exclusivity agreement ever existed",
                "exclusive agreement never existed")) {
            return "NO_EXCLUSIVITY";
        }
        if (containsAny(lower, "70/30", "30% share", "30 percent", "profit share", "share of profits")) {
            return "PROFIT_SHARE";
        }
        if (containsAny(lower, "i paid", "paid in full", "already paid")) {
            return "PAYMENT_CLAIM";
        }
        if (containsAny(lower, "no payment", "not paid", "never paid", "withheld", "unpaid")) {
            return "NON_PAYMENT";
        }
        return "";
    }

    private static String contradictionTypeForPair(String leftType, String rightType) {
        if (("DEAL_DENIAL".equals(leftType) && "DEAL_EXECUTION".equals(rightType))
                || ("DEAL_EXECUTION".equals(leftType) && "DEAL_DENIAL".equals(rightType))) {
            return "DEAL_STATUS_CONFLICT";
        }
        if (("NO_EXCLUSIVITY".equals(leftType) && "PROFIT_SHARE".equals(rightType))
                || ("PROFIT_SHARE".equals(leftType) && "NO_EXCLUSIVITY".equals(rightType))) {
            return "AGREEMENT_SCOPE_CONFLICT";
        }
        if (("PAYMENT_CLAIM".equals(leftType) && "NON_PAYMENT".equals(rightType))
                || ("NON_PAYMENT".equals(leftType) && "PAYMENT_CLAIM".equals(rightType))) {
            return "PAYMENT_STATUS_CONFLICT";
        }
        return "";
    }

    private static String explainStructuredContradiction(String conflictType, String actor, String status) {
        String subject = actor == null || actor.trim().isEmpty() ? "A named party" : actor;
        if (!"VERIFIED".equalsIgnoreCase(status)) {
            return subject + " is linked to materially conflicting propositions, but the actor attribution or corroboration path is still incomplete.";
        }
        if ("DEAL_STATUS_CONFLICT".equals(conflictType)) {
            return subject + " is linked to anchored deal-failure language and later execution or admission language that materially conflict.";
        }
        if ("AGREEMENT_SCOPE_CONFLICT".equals(conflictType)) {
            return subject + " is linked to anchored statements about non-exclusivity that conflict with an anchored profit-share or split clause.";
        }
        if ("PAYMENT_STATUS_CONFLICT".equals(conflictType)) {
            return subject + " is linked to anchored payment-confirmation language that conflicts with anchored non-payment language.";
        }
        return subject + " is linked to two anchored propositions that materially conflict.";
    }

    private static List<ConstitutionalConfig.ContradictionRule> matchContradictionRules(
            String text,
            String actor,
            boolean pairedConflict,
            String conflictType
    ) {
        List<ConstitutionalConfig.ContradictionRule> matches = new ArrayList<>();
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        String conflictLower = conflictType == null ? "" : conflictType.toLowerCase(Locale.ROOT);
        boolean actorResolved = isEvidenceResolvedActor(actor);
        boolean hasTimestamp = DATE_LINE_PATTERN.matcher(text == null ? "" : text).find()
                || GENERIC_DATE_PATTERN.matcher(text == null ? "" : text).find()
                || STRUCTURED_DATE_PATTERN.matcher(text == null ? "" : text).find()
                || containsAny(lower, "before", "after", "dated", "effective", "timeline");
        boolean hasForgerySignal = containsAny(lower, "forged", "forgery", "fake", "tampered", "metadata anomaly", "hash mismatch");
        boolean hasFinancialSignal = hasFinancialAnchor(lower);
        boolean hasLegalSignal = containsAny(lower, "article", "section", "statute", "precedent", "court", "law");
        boolean hasVoiceSignal = containsAny(lower, "voice", "audio", "voiceprint", "spoofed");
        boolean hasSignatureSignal = containsAny(lower, "signature", "signed", "countersigned", "handwriting");
        boolean anomalySignal = containsAny(lower, "unknown", "unclassified", "novel anomaly", "anomaly");

        for (ConstitutionalConfig.ContradictionRule rule : CONTRADICTION_RULES) {
            if (rule == null) {
                continue;
            }
            boolean applies = true;
            for (ConstitutionalConfig.ContradictionCondition condition : rule.conditions) {
                if (condition == null) {
                    continue;
                }
                if (!evaluateContradictionCondition(
                        condition,
                        actorResolved,
                        hasTimestamp,
                        pairedConflict,
                        hasForgerySignal,
                        hasFinancialSignal,
                        hasLegalSignal,
                        hasVoiceSignal,
                        hasSignatureSignal,
                        anomalySignal,
                        conflictLower
                )) {
                    applies = false;
                    break;
                }
            }
            if (applies) {
                matches.add(rule);
            }
        }
        return matches;
    }

    private static boolean evaluateContradictionCondition(
            ConstitutionalConfig.ContradictionCondition condition,
            boolean actorResolved,
            boolean hasTimestamp,
            boolean pairedConflict,
            boolean hasForgerySignal,
            boolean hasFinancialSignal,
            boolean hasLegalSignal,
            boolean hasVoiceSignal,
            boolean hasSignatureSignal,
            boolean anomalySignal,
            String conflictLower
    ) {
        String field = condition.field == null ? "" : condition.field.toLowerCase(Locale.ROOT);
        String op = condition.op == null ? "" : condition.op.toLowerCase(Locale.ROOT);
        if ("actor".equals(field) && "eq".equals(op)) {
            return actorResolved;
        }
        if ("actor".equals(field) && "neq".equals(op)) {
            return pairedConflict && containsAny(conflictLower, "source_vs_source", "document_vs_statement");
        }
        if ("actor".equals(field) && "missing".equals(op)) {
            return !actorResolved;
        }
        if ("timestamp".equals(field) && ("eq".equals(op) || "overlaps".equals(op))) {
            return hasTimestamp;
        }
        if ("timestamp".equals(field) && "missing".equals(op)) {
            return !hasTimestamp;
        }
        if ("statement".equals(field) && "contradicts".equals(op)) {
            return pairedConflict;
        }
        if ("document_hash".equals(field) && "neq".equals(op)) {
            return hasForgerySignal;
        }
        if ("amount".equals(field) && "outlier".equals(op)) {
            return hasFinancialSignal;
        }
        if ("counterparty".equals(field) && "anomalous".equals(op)) {
            return hasFinancialSignal;
        }
        if ("citation".equals(field) && "contradicts".equals(op)) {
            return hasLegalSignal;
        }
        if ("voiceprint".equals(field) && "neq".equals(op)) {
            return hasVoiceSignal;
        }
        if ("signature".equals(field) && "neq".equals(op)) {
            return hasSignatureSignal;
        }
        if ("anomaly_score".equals(field) && "gt".equals(op)) {
            return anomalySignal;
        }
        if ("category".equals(field) && "unknown".equals(op)) {
            return anomalySignal;
        }
        if ("source".equals(field) && "missing".equals(op)) {
            return false;
        }
        return true;
    }

    private static String applyContradictionRuleStatus(
            String status,
            List<ConstitutionalConfig.ContradictionRule> matchedRules,
            boolean pairedConflict,
            String actor
    ) {
        if ("VERIFIED".equalsIgnoreCase(status) || !"CANDIDATE".equalsIgnoreCase(status)) {
            return status;
        }
        if (pairedConflict && isEvidenceResolvedActor(actor) && hasRuleSeverity(matchedRules, "CRITICAL", "HIGH")) {
            return "VERIFIED";
        }
        return status;
    }

    private static String applyContradictionRuleConfidence(
            String confidence,
            List<ConstitutionalConfig.ContradictionRule> matchedRules,
            boolean pairedConflict
    ) {
        if (hasRuleSeverity(matchedRules, "CRITICAL")) {
            return "HIGH";
        }
        if (hasRuleSeverity(matchedRules, "HIGH") && pairedConflict) {
            return "HIGH";
        }
        if ("LOW".equalsIgnoreCase(confidence) && hasRuleSeverity(matchedRules, "HIGH", "MEDIUM")) {
            return "MODERATE";
        }
        return confidence;
    }

    private static boolean hasRuleSeverity(List<ConstitutionalConfig.ContradictionRule> rules, String... severities) {
        if (rules == null || severities == null) {
            return false;
        }
        for (ConstitutionalConfig.ContradictionRule rule : rules) {
            if (rule == null || rule.severity == null) {
                continue;
            }
            for (String severity : severities) {
                if (severity != null && severity.equalsIgnoreCase(rule.severity)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void putContradictionRuleMetadata(
            JSONObject item,
            List<ConstitutionalConfig.ContradictionRule> matchedRules
    ) throws Exception {
        if (item == null || matchedRules == null || matchedRules.isEmpty()) {
            return;
        }
        JSONArray ruleIds = new JSONArray();
        JSONArray brains = new JSONArray();
        JSONArray recovery = new JSONArray();
        LinkedHashSet<String> seenIds = new LinkedHashSet<>();
        LinkedHashSet<String> seenBrains = new LinkedHashSet<>();
        LinkedHashSet<String> seenRecovery = new LinkedHashSet<>();
        String strongestSeverity = "LOW";
        String recommendedAction = "";
        for (ConstitutionalConfig.ContradictionRule rule : matchedRules) {
            if (rule == null) {
                continue;
            }
            if (rule.id != null && !rule.id.trim().isEmpty() && seenIds.add(rule.id.trim())) {
                ruleIds.put(rule.id.trim());
            }
            if (rule.brain != null && !rule.brain.trim().isEmpty()) {
                for (String part : rule.brain.split("\\+")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty() && seenBrains.add(trimmed)) {
                        brains.put(trimmed);
                    }
                }
            }
            if (rule.recoverySteps != null) {
                for (String step : rule.recoverySteps) {
                    if (step != null && !step.trim().isEmpty() && seenRecovery.add(step.trim())) {
                        recovery.put(step.trim());
                    }
                }
            }
            if (severityRank(rule.severity) > severityRank(strongestSeverity)) {
                strongestSeverity = rule.severity;
            }
            if (recommendedAction.isEmpty() && rule.action != null && !rule.action.trim().isEmpty()) {
                recommendedAction = rule.action.trim();
            }
        }
        item.put("ruleIds", ruleIds);
        item.put("supportingBrains", brains);
        item.put("ruleSeverity", strongestSeverity);
        if (!recommendedAction.isEmpty()) {
            item.put("recommendedAction", recommendedAction);
        }
        if (recovery.length() > 0) {
            item.put("recoveryPath", recovery);
        }
    }

    private static int severityRank(String severity) {
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            return 4;
        }
        if ("HIGH".equalsIgnoreCase(severity)) {
            return 3;
        }
        if ("MEDIUM".equalsIgnoreCase(severity)) {
            return 2;
        }
        if ("LOW".equalsIgnoreCase(severity)) {
            return 1;
        }
        return 0;
    }

    private static String neededEvidenceFromRules(List<ConstitutionalConfig.ContradictionRule> matchedRules) {
        if (matchedRules == null || matchedRules.isEmpty()) {
            return "";
        }
        for (ConstitutionalConfig.ContradictionRule rule : matchedRules) {
            if (rule == null || rule.recoverySteps == null) {
                continue;
            }
            for (String step : rule.recoverySteps) {
                if (step == null || step.trim().isEmpty()) {
                    continue;
                }
                String lower = step.toLowerCase(Locale.ROOT);
                if (lower.contains("witness_pool")) {
                    return "Cross-check with independent witness statements or third-party confirmations.";
                }
                if (lower.contains("bank_records")) {
                    return "Obtain bank records, transfer confirmations, or counterparty account entries.";
                }
                if (lower.contains("case_law_database") || lower.contains("legal_review")) {
                    return "Cross-reference the anchored point against the applicable statute or precedent record.";
                }
                if (lower.contains("notary_database") || lower.contains("handwriting_expert")) {
                    return "Obtain original signed copies and, if required, a handwriting or execution comparison.";
                }
                if (lower.contains("blockchain_anchor") || lower.contains("rehash_document")) {
                    return "Obtain the original file and hash chain needed to verify integrity end to end.";
                }
            }
        }
        return "";
    }

    private static String neededEvidenceForContradictionTerm(
            String term,
            String conflictType,
            String actor,
            List<ConstitutionalConfig.ContradictionRule> matchedRules
    ) {
        String ruleDriven = neededEvidenceFromRules(matchedRules);
        if (!ruleDriven.isEmpty()) {
            return ruleDriven;
        }
        return neededEvidenceForContradictionTerm(term, conflictType, actor);
    }

    private static String neededEvidenceForContradictionTerm(String term, String conflictType, String actor) {
        String subject = contradictionSubject(actor);
        String lower = term == null ? "" : term.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "never happened", "no deal", "fell through", "didn't happen", "did not happen", "deal didn't happen",
                "proceeded with the deal", "order completed", "completed the order")) {
            return "To resolve this for " + subject + ", obtain an original execution record, invoice, shipment proof, or contemporaneous communication showing whether the deal actually proceeded or failed.";
        }
        if (containsAny(lower, "no exclusivity", "70/30", "30% share", "profit share", "share of profits")) {
            return "To resolve this for " + subject + ", obtain the executed agreement, addendum, or payment ledger that fixes the operative exclusivity or profit-share terms.";
        }
        if (containsAny(lower, "i paid", "paid in full", "already paid", "no payment", "not paid", "never paid", "withheld", "unpaid")) {
            return "To resolve this for " + subject + ", obtain original transfer proof, bank confirmation, or a dated ledger entry tied to the claimed payment and counterparty.";
        }
        if (containsAny(lower, "that is not true", "i never said")) {
            return "To resolve this for " + subject + ", obtain the full original communication thread, signed statement, or export showing what was actually said and by whom.";
        }
        if (containsAny(lower, "fake", "forged", "you forged")) {
            return "To resolve this for " + subject + ", obtain the original source file, uncropped image, metadata record, or chain-of-custody log establishing authenticity.";
        }
        if ("TIMELINE_CONFLICT".equalsIgnoreCase(conflictType)) {
            return "To resolve this for " + subject + ", obtain original timestamped source material or metadata that fixes the true event order.";
        }
        if ("SOURCE_VS_SOURCE_CONFLICT".equalsIgnoreCase(conflictType)
                || "METADATA_VS_CONTENT_CONFLICT".equalsIgnoreCase(conflictType)) {
            return "To resolve this for " + subject + ", obtain the original unedited source, full thread, or metadata record showing which version is authentic.";
        }
        return "To resolve this for " + subject + ", obtain a second anchored proposition or original source record that directly settles the contradiction.";
    }

    private static String neededEvidenceForStructuredContradiction(
            String conflictType,
            String actor,
            List<ConstitutionalConfig.ContradictionRule> matchedRules
    ) {
        String ruleDriven = neededEvidenceFromRules(matchedRules);
        if (!ruleDriven.isEmpty()) {
            return ruleDriven;
        }
        return neededEvidenceForStructuredContradiction(conflictType, actor);
    }

    private static String neededEvidenceForStructuredContradiction(String conflictType, String actor) {
        String subject = contradictionSubject(actor);
        if ("DEAL_STATUS_CONFLICT".equalsIgnoreCase(conflictType)) {
            return "To resolve this for " + subject + ", obtain a contemporaneous execution record, invoice, shipment record, or original communication confirming whether the deal went ahead or fell through.";
        }
        if ("AGREEMENT_SCOPE_CONFLICT".equalsIgnoreCase(conflictType)) {
            return "To resolve this for " + subject + ", obtain the fully executed agreement, addendum, or ledger that fixes the actual exclusivity and profit-share terms.";
        }
        if ("PAYMENT_STATUS_CONFLICT".equalsIgnoreCase(conflictType)) {
            return "To resolve this for " + subject + ", obtain original transfer proof, bank confirmation, or an account ledger showing whether payment was made or remained outstanding.";
        }
        return "To resolve this for " + subject + ", obtain original anchored material that directly resolves the conflicting propositions.";
    }

    private static String contradictionSubject(String actor) {
        if (actor == null) {
            return "the named party at issue";
        }
        String trimmed = actor.trim();
        if (trimmed.isEmpty() || "unresolved actor".equalsIgnoreCase(trimmed)) {
            return "the named party at issue";
        }
        return trimmed;
    }

    private static JSONObject findContradictionPair(List<OcrTextBlock> blocks, int currentIndex, String actor, String term) throws Exception {
        if (blocks == null || currentIndex < 0 || currentIndex >= blocks.size()) {
            return null;
        }
        OcrTextBlock current = blocks.get(currentIndex);
        if (current == null || actor == null || actor.trim().isEmpty() || "unresolved actor".equalsIgnoreCase(actor)) {
            return null;
        }
        String currentLower = current.text == null ? "" : current.text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < blocks.size(); i++) {
            if (i == currentIndex) {
                continue;
            }
            OcrTextBlock candidate = blocks.get(i);
            if (candidate == null || candidate.text == null || candidate.confidence <= 0f) {
                continue;
            }
            if (Math.abs(candidate.pageIndex - current.pageIndex) > 260) {
                continue;
            }
            if (!actor.equalsIgnoreCase(inferActor(candidate.text, nullSafeNamedParties(actor)))) {
                if (!candidate.text.toLowerCase(Locale.ROOT).contains(actor.toLowerCase(Locale.ROOT))) {
                    continue;
                }
            }
            String candidateLower = candidate.text.toLowerCase(Locale.ROOT);
            if (isPairedConflict(currentLower, candidateLower, term)) {
                return buildPropositionObject(truncate(candidate.text, 220), candidate.pageIndex + 1);
            }
        }
        return null;
    }

    private static boolean isPairedConflict(String currentLower, String candidateLower, String term) {
        String termLower = term == null ? "" : term.toLowerCase(Locale.ROOT);
        if (containsAny(termLower, "never happened", "no deal", "fell through", "didn't happen", "did not happen", "deal didn't happen",
                "proceeded with the deal", "order completed", "completed the order")) {
            boolean leftIsDenial = containsAny(termLower, "never happened", "no deal", "fell through", "didn't happen", "did not happen", "deal didn't happen");
            return leftIsDenial
                    ? containsAny(candidateLower, "proceeded with the deal", "order completed", "completed the order", "shipment", "thank you for the invoice", "thanks for the invoice", "invoice")
                    : containsAny(candidateLower, "never happened", "no deal", "fell through", "didn't happen", "did not happen", "deal didn't happen");
        }
        if (containsAny(termLower, "no exclusivity", "70/30", "30% share")) {
            boolean leftDeniesScope = containsAny(termLower, "no exclusivity");
            return leftDeniesScope
                    ? containsAny(candidateLower, "70/30", "30% share", "30 percent", "profit share", "share of profits")
                    : containsAny(candidateLower, "no exclusivity", "not exclusive");
        }
        if (containsAny(termLower, "i paid")) {
            return containsAny(candidateLower, "outstanding", "unpaid", "not paid", "payment due", "default");
        }
        if (containsAny(termLower, "that is not true", "i never said")) {
            return containsAny(candidateLower, "said", "wrote", "stated", "admitted", "confirmed", "email", "message");
        }
        if (containsAny(termLower, "fake", "forged", "you forged")) {
            return containsAny(candidateLower, "original", "metadata", "authentic", "uncropped", "unaltered");
        }
        return false;
    }

    private static boolean propositionsMateriallyConflict(String propositionA, String propositionB, String term) {
        String left = propositionA == null ? "" : propositionA.toLowerCase(Locale.ROOT);
        String right = propositionB == null ? "" : propositionB.toLowerCase(Locale.ROOT);
        String termLower = term == null ? "" : term.toLowerCase(Locale.ROOT);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        if (containsAny(termLower, "never happened", "no deal", "fell through", "didn't happen", "did not happen", "deal didn't happen",
                "proceeded with the deal", "order completed", "completed the order")) {
            boolean leftDenial = containsAny(left, "never happened", "no deal", "fell through", "didn't happen", "did not happen", "deal didn’t happen", "deal didn't happen");
            boolean leftExecution = containsAny(left, "proceeded with the deal", "order completed", "completed the order", "shipment", "thank you for the invoice", "thanks for the invoice", "invoice");
            boolean rightDenial = containsAny(right, "never happened", "no deal", "fell through", "didn't happen", "did not happen", "deal didn’t happen", "deal didn't happen");
            boolean rightExecution = containsAny(right, "proceeded with the deal", "order completed", "completed the order", "shipment", "thank you for the invoice", "thanks for the invoice", "invoice");
            return (leftDenial && rightExecution) || (leftExecution && rightDenial);
        }
        if (containsAny(termLower, "no exclusivity", "70/30", "30% share")) {
            boolean leftNoExclusivity = containsAny(left, "no exclusivity", "not exclusive");
            boolean leftProfitShare = containsAny(left, "70/30", "30% share", "30 percent", "profit share", "share of profits");
            boolean rightNoExclusivity = containsAny(right, "no exclusivity", "not exclusive");
            boolean rightProfitShare = containsAny(right, "70/30", "30% share", "30 percent", "profit share", "share of profits");
            return (leftNoExclusivity && rightProfitShare) || (leftProfitShare && rightNoExclusivity);
        }
        if (containsAny(termLower, "i paid")) {
            return containsAny(left, "i paid", "paid")
                    && containsAny(right, "outstanding", "unpaid", "not paid", "payment due", "default");
        }
        if (containsAny(termLower, "that is not true", "i never said")) {
            return containsAny(left, "that is not true", "i never said")
                    && containsAny(right, "said", "wrote", "stated", "admitted", "confirmed", "email", "message");
        }
        if (containsAny(termLower, "fake", "forged", "you forged")) {
            return containsAny(left, "fake", "forged", "you forged")
                    && containsAny(right, "original", "metadata", "authentic", "uncropped", "unaltered");
        }
        return false;
    }

    private static JSONArray nullSafeNamedParties(String actor) throws Exception {
        JSONArray named = new JSONArray();
        if (actor != null && !actor.trim().isEmpty()) {
            JSONObject party = new JSONObject();
            party.put("name", actor);
            named.put(party);
        }
        return named;
    }

    private static JSONArray extractCriticalLegalSubjects(NativeEvidenceResult nativeEvidence) throws Exception {
        JSONArray result = new JSONArray();
        if (nativeEvidence == null) return result;

        LinkedHashMap<String, JSONObject> subjects = new LinkedHashMap<>();
        for (OcrTextBlock block : allTextBlocks(nativeEvidence)) {
            if (block == null || block.text == null || block.confidence <= 0f) continue;
            if (isSecondaryNarrativeText(block.text)) continue;
            String lower = block.text.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, List<String>> subject : SUBJECT_KEYWORDS.entrySet()) {
                List<String> matchedTerms = new ArrayList<>();
                for (String keyword : subject.getValue()) {
                    if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                        matchedTerms.add(keyword);
                    }
                }
                if (matchedTerms.isEmpty()) continue;
                if (!isSubjectMatchUsable(subject.getKey(), lower, matchedTerms)) continue;

                JSONObject item = subjects.containsKey(subject.getKey())
                        ? subjects.get(subject.getKey()) : new JSONObject();
                item.put("subject", subject.getKey());
                item.put("page", block.pageIndex + 1);
                item.put("excerpt", truncate(block.text, 240));
                item.put("matchedTerms", new JSONArray(matchedTerms));
                item.put("hitCount", item.optInt("hitCount", 0) + matchedTerms.size());
                subjects.put(subject.getKey(), item);
            }
        }

        for (JSONObject item : subjects.values()) {
            result.put(item);
        }
        return result;
    }

    private static JSONArray extractAnchoredFindings(NativeEvidenceResult nativeEvidence) throws Exception {
        long startedAt = System.currentTimeMillis();
        JSONArray result = new JSONArray();
        if (nativeEvidence == null) {
            logPhase("extractAnchoredFindings", startedAt, "nativeEvidence absent");
            return result;
        }

        List<OcrTextBlock> blocks = allTextBlocks(nativeEvidence);
        Log.d(TAG, "extractAnchoredFindings scanning blocks=" + blocks.size());
        int emitted = 0;
        int blockIndex = 0;
        for (OcrTextBlock block : blocks) {
            blockIndex++;
            if (blockIndex % 100 == 0) {
                Log.d(TAG, "extractAnchoredFindings progress block=" + blockIndex + "/" + blocks.size());
            }
            if (block == null || block.text == null || block.confidence <= 0f) continue;
            if (isSecondaryNarrativeText(block.text)) continue;
            String lower = block.text.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, List<String>> subject : SUBJECT_KEYWORDS.entrySet()) {
                List<String> matchedTerms = new ArrayList<>();
                for (String keyword : subject.getValue()) {
                    if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                        matchedTerms.add(keyword);
                    }
                }
                if (matchedTerms.isEmpty()) continue;
                if (!isSubjectMatchUsable(subject.getKey(), lower, matchedTerms)) continue;

                JSONObject finding = new JSONObject();
                finding.put("page", block.pageIndex + 1);
                finding.put("category", subject.getKey());
                finding.put("matchedTerms", new JSONArray(matchedTerms));
                finding.put("excerpt", truncate(block.text, 260));
                result.put(finding);
                emitted++;
                if (emitted >= 24) {
                    logPhase("extractAnchoredFindings", startedAt,
                            "blocks=" + blocks.size() + " emitted=" + result.length() + " capped=true");
                    return result;
                }
            }
        }
        logPhase("extractAnchoredFindings", startedAt,
                "blocks=" + blocks.size() + " emitted=" + result.length());
        return result;
    }

    private static JSONArray extractIncidentRegister(NativeEvidenceResult nativeEvidence, JSONArray namedParties) throws Exception {
        JSONArray result = new JSONArray();
        if (nativeEvidence == null) return result;

        int emitted = 0;

        JSONArray emailEvents = extractEmailEventRegister(nativeEvidence, namedParties);
        for (int i = 0; i < emailEvents.length() && emitted < 24; i++) {
            JSONObject item = emailEvents.optJSONObject(i);
            if (item == null) continue;
            result.put(item);
            emitted++;
        }

        for (VisualForgeryFinding finding : nativeEvidence.visualFindings) {
            if (finding == null) continue;
            if ("info".equalsIgnoreCase(finding.severity)
                    && !"SIGNATURE_MARKS_PRESENT".equals(finding.type)) {
                continue;
            }

            JSONObject item = new JSONObject();
            item.put("page", finding.pageIndex + 1);
            item.put("incidentType", finding.type);
            item.put("severity", finding.severity);
            item.put("region", finding.region);
            item.put("description", finding.description);
            item.put("source", "visual-forensics");
            item.put("pageAnchor", "page_" + (finding.pageIndex + 1));
            item.put("actor", inferActor(finding.description, namedParties));
            item.put("narrative", buildIncidentNarrative(
                    inferActor(finding.description, namedParties),
                    finding.type,
                    finding.description,
                    finding.pageIndex + 1
            ));
            result.put(item);
            emitted++;
            if (emitted >= 32) {
                return result;
            }
        }

        JSONArray anchored = extractAnchoredFindings(nativeEvidence);
        for (int i = 0; i < anchored.length() && emitted < 48; i++) {
            JSONObject finding = anchored.optJSONObject(i);
            if (finding == null) continue;

            JSONObject item = new JSONObject();
            item.put("page", finding.optInt("page", 0));
            item.put("incidentType", finding.optString("category", "TEXTUAL_FINDING"));
            item.put("severity", "text");
            item.put("region", "ocr-block");
            item.put("description", finding.optString("excerpt", ""));
            item.put("matchedTerms", finding.optJSONArray("matchedTerms"));
            item.put("source", "ocr-analysis");
            item.put("pageAnchor", "page_" + finding.optInt("page", 0));
            String actor = inferActor(finding.optString("excerpt", ""), namedParties);
            item.put("actor", actor);
            item.put("narrative", buildIncidentNarrative(
                    actor,
                    finding.optString("category", "TEXTUAL_FINDING"),
                    finding.optString("excerpt", ""),
                    finding.optInt("page", 0)
            ));
            result.put(item);
            emitted++;
        }

        return result;
    }

    private static JSONArray extractDocumentIntegrityFindings(NativeEvidenceResult nativeEvidence, JSONArray namedParties) throws Exception {
        JSONArray result = new JSONArray();
        if (nativeEvidence == null) return result;

        LinkedHashMap<String, JSONObject> deduped = new LinkedHashMap<>();
        LinkedHashMap<Integer, StringBuilder> pageText = new LinkedHashMap<>();
        for (OcrTextBlock block : allTextBlocks(nativeEvidence)) {
            if (block == null || block.text == null || block.confidence <= 0f) continue;
            if (isSecondaryNarrativeText(block.text)) continue;
            int pageNumber = block.pageIndex + 1;
            pageText.computeIfAbsent(pageNumber, ignored -> new StringBuilder()).append(' ').append(block.text);
            String lower = block.text.toLowerCase(Locale.ROOT);

            for (Map.Entry<String, List<String>> entry : DOCUMENT_INTEGRITY_KEYWORDS.entrySet()) {
                List<String> matchedTerms = new ArrayList<>();
                for (String keyword : entry.getValue()) {
                    if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                        matchedTerms.add(keyword);
                    }
                }
                if (matchedTerms.isEmpty()) continue;

                String type = entry.getKey();
                if (!isIntegrityMatchUsable(type, lower)) continue;
                String actor = inferActor(block.text, namedParties);
                String dedupeKey = type + "|" + actor;
                JSONObject existing = deduped.get(dedupeKey);
                if (existing == null) {
                    JSONObject item = new JSONObject();
                    item.put("type", type);
                    item.put("page", block.pageIndex + 1);
                    item.put("actor", actor);
                    item.put("excerpt", truncate(block.text, 260));
                    item.put("matchedTerms", new JSONArray(matchedTerms));
                    item.put("confidence", ordinalConfidenceForIntegrity(type, actor, block.text));
                    JSONArray anchors = new JSONArray();
                    anchors.put(buildAnchorObject(block.pageIndex + 1));
                    item.put("anchors", anchors);
                    deduped.put(dedupeKey, item);
                } else {
                    existing.optJSONArray("anchors").put(buildAnchorObject(block.pageIndex + 1));
                }
            }
        }

        for (VisualForgeryFinding finding : nativeEvidence.visualFindings) {
            if (finding == null || finding.type == null) {
                continue;
            }
            String type = finding.type.trim();
            if (!"SIGNATURE_REGION_EMPTY".equalsIgnoreCase(type)
                    && !"SIGNATURE_MARKS_NOT_FOUND".equalsIgnoreCase(type)) {
                continue;
            }
            int page = finding.pageIndex + 1;
            String pageCorpus = pageText.containsKey(page) ? pageText.get(page).toString() : "";
            String actor = inferActor(pageCorpus + " " + safeText(finding.description), namedParties);
            String dedupeKey = type + "|" + actor + "|" + page;
            if (deduped.containsKey(dedupeKey)) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("type", type);
            item.put("page", page);
            item.put("actor", actor);
            item.put("excerpt", truncate(safeText(finding.description) + " " + truncate(pageCorpus, 180), 260));
            JSONArray matchedTerms = new JSONArray();
            matchedTerms.put(type);
            if ("SIGNATURE_REGION_EMPTY".equalsIgnoreCase(type)) {
                matchedTerms.put("missing signature block");
            } else {
                matchedTerms.put("signature marks not found");
            }
            item.put("matchedTerms", matchedTerms);
            item.put("confidence", ordinalConfidenceForIntegrity(type, actor, pageCorpus + " " + safeText(finding.description)));
            JSONArray anchors = new JSONArray();
            anchors.put(buildAnchorObject(page));
            item.put("anchors", anchors);
            deduped.put(dedupeKey, item);
        }

        int emitted = 0;
        for (JSONObject item : deduped.values()) {
            result.put(item);
            emitted++;
            if (emitted >= 20) {
                break;
            }
        }
        return result;
    }

    private static JSONArray extractTimelineAnchorRegister(NativeEvidenceResult nativeEvidence, JSONArray namedParties) throws Exception {
        JSONArray result = new JSONArray();
        if (nativeEvidence == null) {
            return result;
        }

        LinkedHashMap<String, JSONObject> deduped = new LinkedHashMap<>();
        for (OcrTextBlock block : allTextBlocks(nativeEvidence)) {
            if (block == null || block.text == null || block.confidence <= 0f) continue;
            if (isSecondaryNarrativeText(block.text)) continue;

            String text = block.text.replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) continue;
            String lower = text.toLowerCase(Locale.ROOT);
            String eventType = classifyTimelineEvent(lower);
            String date = extractAnchorDate(text);
            if (eventType.isEmpty() && date.isEmpty()) {
                continue;
            }
            if (eventType.isEmpty()) {
                eventType = "DATED_COMMUNICATION";
            }
            if ("DATED_COMMUNICATION".equals(eventType) && !isSubstantiveDatedCommunication(text)) {
                continue;
            }
            boolean primaryEvidence = isPrimaryTimelineEvidence(text, eventType);
            if (!primaryEvidence && isSupportOnlyCorrespondenceText(text)) {
                continue;
            }
            if (!primaryEvidence && isNarrativeTimelineEvent(eventType)) {
                continue;
            }

            String actor = inferActor(text, namedParties);
            if (isNamedPartyCandidateNoise(actor)) {
                continue;
            }
            if (containsAny(lower,
                    "forensic correction & addendum",
                    "please ensure this judgment",
                    "fraud docket",
                    "recused protection-order case file",
                    "i am copying mr.",
                    "official record")) {
                continue;
            }
            String summary = buildTimelineSummary(actor, eventType, text, date, block.pageIndex + 1);
            if (summary.isEmpty() || summary.toLowerCase(Locale.ROOT).contains("survived the primary extraction filter")) {
                continue;
            }

            String dedupeKey = eventType + "|" + actor + "|" + normalizeDedupeText(summary);
            JSONObject existing = deduped.get(dedupeKey);
            if (existing == null) {
                JSONObject item = new JSONObject();
                item.put("page", block.pageIndex + 1);
                item.put("actor", actor);
                item.put("eventType", eventType);
                item.put("date", date);
                item.put("summary", summary);
                item.put("narrative", summary);
                item.put("excerpt", truncate(text, 260));
                item.put("confidence", ordinalConfidenceForTimeline(eventType, actor, date));
                item.put("primaryEvidence", primaryEvidence);
                item.put("supportOnly", !primaryEvidence);
                JSONArray anchors = new JSONArray();
                anchors.put(buildAnchorObject(block.pageIndex + 1));
                item.put("anchors", anchors);
                deduped.put(dedupeKey, item);
            } else {
                existing.optJSONArray("anchors").put(buildAnchorObject(block.pageIndex + 1));
            }
        }

        appendProfitShareReconciliationFinding(deduped, allTextBlocks(nativeEvidence), namedParties, nativeEvidence);

        int emitted = 0;
        for (JSONObject item : deduped.values()) {
            result.put(item);
            emitted++;
            if (emitted >= 24) {
                break;
            }
        }
        return result;
    }

    private static JSONArray extractActorConductRegister(
            JSONArray timelineAnchors,
            JSONArray documentIntegrityFindings,
            JSONArray incidentRegister,
            JSONArray financialExposureRegister
    ) throws Exception {
        JSONArray result = new JSONArray();
        LinkedHashMap<String, JSONObject> deduped = new LinkedHashMap<>();

        mergeActorConductEvidence(deduped, timelineAnchors, false);
        mergeActorConductEvidence(deduped, documentIntegrityFindings, true);
        mergeIncidentConductEvidence(deduped, incidentRegister);
        mergeFinancialConductEvidence(deduped, financialExposureRegister);

        int emitted = 0;
        for (JSONObject item : deduped.values()) {
            result.put(item);
            emitted++;
            if (emitted >= 18) {
                break;
            }
        }
        return result;
    }

    private static void mergeIncidentConductEvidence(
            LinkedHashMap<String, JSONObject> deduped,
            JSONArray source
    ) throws Exception {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String actor = item.optString("actor", "").trim();
            if (actor.isEmpty() || "unresolved actor".equalsIgnoreCase(actor)) {
                continue;
            }
            String conductType = conductTypeForIncident(item);
            if (conductType.isEmpty()) {
                continue;
            }
            String summary = buildActorConductSummary(actor, new JSONObject()
                    .put("conductType", conductType)
                    .put("date", item.optString("date", "")));
            if (summary.isEmpty()) {
                continue;
            }
            String dedupeKey = actor + "|" + conductType;
            JSONObject existing = deduped.get(dedupeKey);
            if (existing == null) {
                JSONObject record = new JSONObject();
                record.put("actor", actor);
                record.put("page", item.optInt("page", 0));
                record.put("conductType", conductType);
                record.put("summary", summary);
                record.put("narrative", summary);
                record.put("excerpt", firstNonEmptyNonBlank(
                        item.optString("description", ""),
                        item.optString("excerpt", "")
                ));
                record.put("confidence", "MODERATE");
                record.put("primaryEvidence", true);
                record.put("supportOnly", false);
                JSONArray anchors = new JSONArray();
                copyAnchors(anchors, item.optJSONArray("anchors"), item.optInt("page", 0));
                record.put("anchors", anchors);
                deduped.put(dedupeKey, record);
            } else {
                copyAnchors(existing.optJSONArray("anchors"), item.optJSONArray("anchors"), item.optInt("page", 0));
            }
        }
    }

    private static void mergeFinancialConductEvidence(
            LinkedHashMap<String, JSONObject> deduped,
            JSONArray source
    ) throws Exception {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String actor = item.optString("actor", "").trim();
            if (actor.isEmpty() || "unresolved actor".equalsIgnoreCase(actor)) {
                continue;
            }
            String conductType = "FINANCIAL_POSITION";
            String summary = item.optString("summary", "").trim();
            if (summary.isEmpty()) {
                summary = buildActorConductSummary(actor, new JSONObject()
                        .put("conductType", conductType)
                        .put("date", item.optString("date", "")));
            }
            String dedupeKey = actor + "|" + conductType;
            JSONObject existing = deduped.get(dedupeKey);
            if (existing == null) {
                JSONObject record = new JSONObject();
                record.put("actor", actor);
                record.put("page", item.optInt("page", 0));
                record.put("conductType", conductType);
                record.put("summary", summary);
                record.put("narrative", summary);
                record.put("excerpt", item.optString("excerpt", ""));
                record.put("confidence", item.optString("confidence", "HIGH"));
                record.put("primaryEvidence", item.optBoolean("primaryEvidence", true));
                record.put("supportOnly", item.optBoolean("supportOnly", false));
                JSONArray anchors = new JSONArray();
                copyAnchors(anchors, item.optJSONArray("anchors"), item.optInt("page", 0));
                record.put("anchors", anchors);
                deduped.put(dedupeKey, record);
            } else {
                copyAnchors(existing.optJSONArray("anchors"), item.optJSONArray("anchors"), item.optInt("page", 0));
            }
        }
    }

    private static void mergeActorConductEvidence(
            LinkedHashMap<String, JSONObject> deduped,
            JSONArray source,
            boolean integritySource
    ) throws Exception {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            String actor = item.optString("actor", "").trim();
            if (actor.isEmpty() || "unresolved actor".equalsIgnoreCase(actor)) continue;

            String conductType = integritySource
                    ? "DOCUMENT_EXECUTION_STATE"
                    : conductTypeForEvent(item.optString("eventType", ""));
            if (conductType.isEmpty()) {
                continue;
            }
            boolean primaryEvidence = integritySource || item.optBoolean("primaryEvidence", false);
            if (!integritySource && (!primaryEvidence || !isPromotableConductType(conductType))) {
                continue;
            }

            String summary = integritySource
                    ? buildActorIntegritySummary(actor, item)
                    : buildActorConductSummary(actor, item);
            if (summary.isEmpty()) {
                continue;
            }

            String dedupeKey = actor + "|" + conductType;
            JSONObject existing = deduped.get(dedupeKey);
            if (existing == null) {
                JSONObject record = new JSONObject();
                record.put("actor", actor);
                record.put("page", item.optInt("page", 0));
                record.put("conductType", conductType);
                record.put("summary", summary);
                record.put("narrative", summary);
                record.put("excerpt", item.optString("excerpt", ""));
                record.put("confidence", item.optString("confidence", integritySource ? "MODERATE" : "HIGH"));
                record.put("primaryEvidence", primaryEvidence);
                record.put("supportOnly", !primaryEvidence);
                JSONArray anchors = new JSONArray();
                copyAnchors(anchors, item.optJSONArray("anchors"), item.optInt("page", 0));
                record.put("anchors", anchors);
                deduped.put(dedupeKey, record);
            } else {
                copyAnchors(existing.optJSONArray("anchors"), item.optJSONArray("anchors"), item.optInt("page", 0));
            }
        }
    }

    private static JSONArray extractFinancialExposureRegister(NativeEvidenceResult nativeEvidence, JSONArray namedParties) throws Exception {
        long startedAt = System.currentTimeMillis();
        JSONArray result = new JSONArray();
        if (nativeEvidence == null) {
            logPhase("extractFinancialExposureRegister", startedAt, "nativeEvidence absent");
            return result;
        }

        List<OcrTextBlock> blocks = allTextBlocks(nativeEvidence);
        Log.d(TAG, "extractFinancialExposureRegister scanning blocks=" + blocks.size());
        LinkedHashMap<String, JSONObject> deduped = new LinkedHashMap<>();
        int blockIndex = 0;
        for (OcrTextBlock block : blocks) {
            blockIndex++;
            if (blockIndex % 100 == 0) {
                Log.d(TAG, "extractFinancialExposureRegister progress block=" + blockIndex + "/" + blocks.size());
            }
            if (block == null || block.text == null || block.confidence <= 0f) continue;
            if (isSecondaryNarrativeText(block.text)) continue;
            String text = block.text.replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) continue;
            String lower = text.toLowerCase(Locale.ROOT);
            boolean primaryEvidence = isPrimaryFinancialEvidence(text);
            if (!primaryEvidence && isDemandOrSettlementFraming(lower)) {
                continue;
            }
            if (!hasFinancialAnchor(lower)) {
                continue;
            }
            String amount = extractMoneyAmount(text);
            if (amount.isEmpty() && !containsAny(lower, "million", "billion", "goodwill", "rent paid", "upgrade cost", "extension fee", "invoice", "repayment")) {
                continue;
            }

            String category = classifyFinancialCategory(lower);
            String actor = inferSubjectActor(text, namedParties);
            String date = extractAnchorDate(text);
            String basis = extractFinancialBasis(lower);
            String counterparty = inferFinancialCounterparty(text, namedParties, actor);
            if (!isStructuredFinancialEvidence(actor, amount, basis, counterparty)) {
                continue;
            }
            String summary = buildFinancialSummary(actor, category, amount, text, date, block.pageIndex + 1);
            String dedupeKey = category + "|" + actor + "|" + counterparty + "|" + amount;
            JSONObject existing = deduped.get(dedupeKey);
            if (existing == null) {
                JSONObject item = new JSONObject();
                item.put("page", block.pageIndex + 1);
                item.put("actor", actor);
                item.put("amountCategory", category);
                item.put("amount", amount);
                item.put("basis", basis);
                item.put("counterparty", counterparty);
                item.put("date", date);
                item.put("summary", summary);
                item.put("narrative", summary);
                item.put("excerpt", truncate(text, 260));
                item.put("confidence", "HIGH");
                item.put("status", "CANDIDATE");
                item.put("primaryEvidence", primaryEvidence);
                item.put("supportOnly", !primaryEvidence);
                JSONArray anchors = new JSONArray();
                anchors.put(buildAnchorObject(block.pageIndex + 1));
                item.put("anchors", anchors);
                deduped.put(dedupeKey, item);
            } else {
                existing.optJSONArray("anchors").put(buildAnchorObject(block.pageIndex + 1));
            }
        }

        appendProfitShareReconciliationFinding(deduped, blocks, namedParties, nativeEvidence);
        appendInvoiceNonPaymentFinding(deduped, blocks, namedParties, nativeEvidence);

        int emitted = 0;
        for (JSONObject item : deduped.values()) {
            result.put(item);
            emitted++;
            if (emitted >= 18) {
                break;
            }
        }
        logPhase("extractFinancialExposureRegister", startedAt,
                "blocks=" + blocks.size() + " deduped=" + deduped.size() + " emitted=" + result.length());
        return result;
    }

    private static boolean isStructuredFinancialEvidence(String actor, String amount, String basis, String counterparty) {
        if (actor == null || actor.trim().isEmpty() || "unresolved actor".equalsIgnoreCase(actor)) {
            return false;
        }
        if (amount == null || amount.trim().isEmpty()) {
            return false;
        }
        if (basis == null || basis.trim().isEmpty() || "DEMAND_ONLY".equalsIgnoreCase(basis)) {
            return false;
        }
        if (counterparty != null
                && !counterparty.trim().isEmpty()
                && !"unresolved actor".equalsIgnoreCase(counterparty)
                && !counterparty.equalsIgnoreCase(actor)) {
            return true;
        }
        return "PROFIT_SHARE_AGREEMENT".equalsIgnoreCase(basis)
                || "INVOICE".equalsIgnoreCase(basis)
                || "LEASE_OR_CHARGE".equalsIgnoreCase(basis)
                || "ASSET_THEFT".equalsIgnoreCase(basis)
                || "UNLAWFUL_CHARTER".equalsIgnoreCase(basis)
                || "FRAUDULENT_REGISTRATION".equalsIgnoreCase(basis)
                || "GOODWILL_LOSS".equalsIgnoreCase(basis)
                || "LOSS_AND_DAMAGES".equalsIgnoreCase(basis);
    }

    private static String extractFinancialBasis(String lower) {
        if (lower == null || lower.trim().isEmpty()) {
            return "";
        }
        if (containsAny(lower, "70/30", "30% share", "30 percent", "profit share", "shareholder agreement")) {
            return "PROFIT_SHARE_AGREEMENT";
        }
        if (containsAny(lower, "invoice no", "invoice number", "invoice sl", "invoice attached", "thanks for the invoice", "thank you for the invoice")) {
            return "INVOICE";
        }
        if (containsAny(lower, "bank transfer", "wire transfer", "swift", "account number", "branch code", "bank name")) {
            return "BANK_TRANSFER";
        }
        if (containsAny(lower, "settlement amount", "amount due", "proposal for the transfer")) {
            return "SETTLEMENT_PROPOSAL";
        }
        if (containsAny(lower, "rent paid", "rental", "upgrade cost", "upgrade costs", "extension fee", "5-year extension")) {
            return "LEASE_OR_CHARGE";
        }
        if (containsAny(lower, "goodwill was stolen", "goodwill stolen", "goodwill theft")) {
            return "GOODWILL_LOSS";
        }
        if (containsAny(lower,
                "fraudulent registration",
                "fraudulent permit",
                "fraudulently registered",
                "registration was changed")) {
            return "FRAUDULENT_REGISTRATION";
        }
        if (containsAny(lower,
                "unlawful charter",
                "charter operations",
                "chartering",
                "chartered without authority")) {
            return "UNLAWFUL_CHARTER";
        }
        if (containsAny(lower,
                "stolen vessel",
                "theft of vessel",
                "boat was stolen",
                "vessel was stolen",
                "stolen from",
                "stole the boat",
                "theft")) {
            return "ASSET_THEFT";
        }
        if (containsAny(lower,
                "damages",
                "financial loss",
                "loss of income",
                "losses of",
                "loss amount",
                "prejudice",
                "million")) {
            return "LOSS_AND_DAMAGES";
        }
        if (containsAny(lower, "payment due", "due immediately", "repayment", "demand")) {
            return "DEMAND_ONLY";
        }
        return "";
    }

    private static String inferFinancialCounterparty(String text, JSONArray namedParties, String actor) {
        if (namedParties == null || namedParties.length() == 0) {
            return "";
        }
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        String actorLower = actor == null ? "" : actor.toLowerCase(Locale.ROOT);
        for (int i = 0; i < namedParties.length(); i++) {
            JSONObject party = namedParties.optJSONObject(i);
            if (party == null) {
                continue;
            }
            String name = party.optString("name", "").trim();
            if (!isUsableActorName(name)) {
                continue;
            }
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (normalizedName.equals(actorLower)) {
                continue;
            }
            if (lower.contains(normalizedName)) {
                return name;
            }
        }
        return "";
    }

    private static JSONArray extractNarrativeThemeRegister(NativeEvidenceResult nativeEvidence) throws Exception {
        JSONArray result = new JSONArray();
        if (nativeEvidence == null) {
            return result;
        }

        LinkedHashMap<String, JSONObject> deduped = new LinkedHashMap<>();
        for (OcrTextBlock block : allTextBlocks(nativeEvidence)) {
            if (block == null || block.text == null || block.confidence <= 0f) continue;
            if (isSecondaryNarrativeText(block.text)) continue;
            String text = block.text.replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) continue;
            String lower = text.toLowerCase(Locale.ROOT);

            String theme = classifyNarrativeTheme(lower);
            if (theme.isEmpty()) {
                continue;
            }

            JSONObject existing = deduped.get(theme);
            if (existing == null) {
                JSONObject item = new JSONObject();
                item.put("page", block.pageIndex + 1);
                item.put("theme", theme);
                item.put("summary", buildNarrativeThemeSummary(theme));
                item.put("narrative", buildNarrativeThemeSummary(theme));
                item.put("excerpt", truncate(text, 220));
                item.put("confidence", "MODERATE");
                JSONArray anchors = new JSONArray();
                anchors.put(buildAnchorObject(block.pageIndex + 1));
                item.put("anchors", anchors);
                deduped.put(theme, item);
            } else {
                existing.optJSONArray("anchors").put(buildAnchorObject(block.pageIndex + 1));
            }
        }

        int emitted = 0;
        for (JSONObject item : deduped.values()) {
            result.put(item);
            emitted++;
            if (emitted >= 12) {
                break;
            }
        }
        return result;
    }

    private static JSONArray extractEmailEventRegister(NativeEvidenceResult nativeEvidence, JSONArray namedParties) throws Exception {
        JSONArray result = new JSONArray();
        if (nativeEvidence == null) {
            return result;
        }

        int emitted = 0;
        for (OcrTextBlock block : allTextBlocks(nativeEvidence)) {
            if (block == null || block.text == null || block.confidence <= 0f) continue;
            String text = block.text.replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) continue;
            if (isSecondaryNarrativeText(text)) continue;
            String lower = text.toLowerCase(Locale.ROOT);

            boolean looksLikeEmailEvent =
                    text.contains("<") && text.contains(">") && DATE_LINE_PATTERN.matcher(text).find();
            boolean looksLikeKeyTimeline =
                    lower.contains("key events:")
                            || lower.contains("24 feb:")
                            || lower.contains("8 mar:")
                            || lower.contains("10 mar:")
                            || lower.contains("1 apr:")
                            || lower.contains("6 apr:");
            boolean looksLikeInstitutionalResponse =
                    lower.contains("thank you for your correspondence")
                            || lower.contains("we request a case evaluation fee")
                            || lower.contains("the matter falls under commercial jurisdiction");
            boolean looksLikeDatedNarrative =
                    GENERIC_DATE_PATTERN.matcher(text).find()
                            && (lower.contains("subject:")
                            || lower.contains("from:")
                            || lower.contains("to:")
                            || lower.contains("dear ")
                            || lower.contains("goodwill")
                            || lower.contains("lease")
                            || lower.contains("criminal docket"));

            if (!looksLikeEmailEvent && !looksLikeKeyTimeline && !looksLikeInstitutionalResponse && !looksLikeDatedNarrative) {
                continue;
            }
            if ((looksLikeEmailEvent || looksLikeDatedNarrative) && !looksLikeKeyTimeline
                    && !looksLikeInstitutionalResponse && !isSubstantiveDatedCommunication(text)) {
                continue;
            }
            boolean primaryEvidence = looksLikeKeyTimeline || isPrimaryTimelineEvidence(text, "DATED_COMMUNICATION");
            if (!primaryEvidence && isSupportOnlyCorrespondenceText(text)) {
                continue;
            }

            JSONObject item = new JSONObject();
            item.put("page", block.pageIndex + 1);
            item.put("incidentType", "COMMUNICATION_EVENT");
            item.put("severity", "text");
            item.put("region", "ocr-block");
            item.put("description", truncate(text, 240));
            item.put("source", "ocr-analysis");
            item.put("pageAnchor", "page_" + (block.pageIndex + 1));
            String actor = inferActor(text, namedParties);
            item.put("actor", actor);
            item.put("narrative", buildCommunicationNarrative(actor, text, block.pageIndex + 1));
            item.put("primaryEvidence", primaryEvidence);
            item.put("supportOnly", !primaryEvidence);
            result.put(item);
            emitted++;
            if (emitted >= 16) {
                return result;
            }
        }
        return result;
    }

    private static String inferActor(String text, JSONArray namedParties) {
        if (text == null) {
            return "unresolved actor";
        }
        String quotedAuthor = extractQuotedOrFromActor(text, namedParties);
        if (!quotedAuthor.isEmpty()) {
            return quotedAuthor;
        }
        Matcher emailMatcher = EMAIL_NAME_PATTERN.matcher(text);
        while (emailMatcher.find()) {
            String candidate = emailMatcher.group(1).trim();
            String resolved = resolveActorCandidate(candidate, namedParties);
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int fromIndex = lower.indexOf("from:");
        if (fromIndex >= 0) {
            int endIndex = Math.min(text.length(), fromIndex + 180);
            Matcher fromMatcher = PERSON_PATTERN.matcher(text.substring(fromIndex, endIndex));
            while (fromMatcher.find()) {
                String candidate = fromMatcher.group(1).trim();
                String resolved = resolveActorCandidate(candidate, namedParties);
                if (!resolved.isEmpty()) {
                    return resolved;
                }
            }
        }
        String directPartyMatch = findNamedPartyMatch(text, namedParties);
        if (!directPartyMatch.isEmpty()) {
            return directPartyMatch;
        }
        if (containsPrimaryActorRoleSignal(text)) {
            Matcher matcher = PERSON_PATTERN.matcher(text);
            while (matcher.find()) {
                String candidate = matcher.group(1).trim();
                String resolved = resolveActorCandidate(candidate, namedParties);
                if (!resolved.isEmpty()) {
                    return resolved;
                }
            }
        }
        return "unresolved actor";
    }

    private static String resolvePropositionActor(String candidate, String text, JSONArray namedParties) {
        String explicitAuthor = extractQuotedOrFromActor(text, namedParties);
        if (isEvidenceResolvedActor(explicitAuthor)) {
            return explicitAuthor;
        }
        String resolved = resolveActorCandidate(candidate, namedParties);
        if (!resolved.isEmpty()) {
            return resolved;
        }
        String inferred = inferSubjectActor(text, namedParties);
        if (isEvidenceResolvedActor(inferred)) {
            return inferred;
        }
        return "unresolved actor";
    }

    private static String resolveActorCandidate(String candidate, JSONArray namedParties) {
        String sanitized = sanitizeActorCandidate(candidate);
        if (!isUsableActorName(sanitized)) {
            return "";
        }
        String matchedParty = findNamedPartyAlias(sanitized, namedParties);
        if (!matchedParty.isEmpty()) {
            return matchedParty;
        }
        return looksLikePersonName(sanitized) && !looksLikeEmailHeader(sanitized) && !isJunkActor(sanitized)
                ? sanitized
                : "";
    }

    private static String findNamedPartyMatch(String text, JSONArray namedParties) {
        if (text == null || namedParties == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String mappedActor = inferActorFromKnownContext(lower, namedParties);
        if (!mappedActor.isEmpty()) {
            return mappedActor;
        }
        String bestMatch = "";
        int bestIndex = Integer.MAX_VALUE;
        int bestLength = -1;
        for (int i = 0; i < namedParties.length(); i++) {
            JSONObject party = namedParties.optJSONObject(i);
            if (party == null) {
                continue;
            }
            String name = party.optString("name", "").trim();
            if (!isUsableActorName(name)) {
                continue;
            }
            String normalizedName = name.toLowerCase(Locale.ROOT);
            int index = lower.indexOf(normalizedName);
            if (index < 0) {
                continue;
            }
            if (index < bestIndex || (index == bestIndex && name.length() > bestLength)) {
                bestMatch = name;
                bestIndex = index;
                bestLength = name.length();
            }
        }
        return bestMatch;
    }

    private static String findNamedPartyAlias(String candidate, JSONArray namedParties) {
        if (candidate == null || namedParties == null) {
            return "";
        }
        String normalizedCandidate = candidate.trim().toLowerCase(Locale.ROOT);
        if (normalizedCandidate.isEmpty()) {
            return "";
        }
        if (normalizedCandidate.equals("des smith") || normalizedCandidate.equals("mr des smith")) {
            String resolved = resolveNamedPartyByNeedle(namedParties, "desmond smith");
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }
        for (int i = 0; i < namedParties.length(); i++) {
            JSONObject party = namedParties.optJSONObject(i);
            if (party == null) {
                continue;
            }
            String name = party.optString("name", "").trim();
            if (!isUsableActorName(name)) {
                continue;
            }
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (normalizedName.equals(normalizedCandidate)
                    || normalizedName.contains(normalizedCandidate)
                    || normalizedCandidate.contains(normalizedName)) {
                return name;
            }
        }
        return "";
    }

    private static String inferActorFromKnownContext(String lower, JSONArray namedParties) {
        if (lower == null || namedParties == null) {
            return "";
        }
        if (lower.contains("umtentweni")) {
            return firstNonEmptyNonBlank(
                    resolveNamedPartyByNeedle(namedParties, "desmond smith"),
                    resolveNamedPartyByNeedle(namedParties, "des smith")
            );
        }
        if (lower.contains("port edward garage") || lower.contains("port edward")) {
            return firstNonEmptyNonBlank(
                    resolveNamedPartyByNeedle(namedParties, "gary highcock"),
                    resolveNamedPartyByNeedle(namedParties, "gary")
            );
        }
        if (lower.contains("thongasi") || lower.contains("wayne's world")) {
            return firstNonEmptyNonBlank(
                    resolveNamedPartyByNeedle(namedParties, "wayne nel"),
                    resolveNamedPartyByNeedle(namedParties, "wayne")
            );
        }
        if (lower.contains("des smith")) {
            return resolveNamedPartyByNeedle(namedParties, "desmond smith");
        }
        if (lower.contains("desmond smith")) {
            return firstNonEmptyNonBlank(
                    resolveNamedPartyByNeedle(namedParties, "desmond smith"),
                    resolveNamedPartyByNeedle(namedParties, "des smith")
            );
        }
        if (lower.contains("morne olivier")) {
            return firstNonEmptyNonBlank(
                    resolveNamedPartyByNeedle(namedParties, "morne olivier"),
                    resolveNamedPartyByNeedle(namedParties, "morne")
            );
        }
        if (lower.contains("niven naidoo")) {
            return firstNonEmptyNonBlank(
                    resolveNamedPartyByNeedle(namedParties, "niven naidoo"),
                    resolveNamedPartyByNeedle(namedParties, "niven")
            );
        }
        if (lower.contains("harris naidoo")) {
            return firstNonEmptyNonBlank(
                    resolveNamedPartyByNeedle(namedParties, "harris naidoo"),
                    resolveNamedPartyByNeedle(namedParties, "harris")
            );
        }
        if (lower.contains("terry hardouin")) {
            return firstNonEmptyNonBlank(
                    resolveNamedPartyByNeedle(namedParties, "terry hardouin"),
                    resolveNamedPartyByNeedle(namedParties, "terry")
            );
        }
        if (lower.contains("corrie henn")) {
            return firstNonEmptyNonBlank(
                    resolveNamedPartyByNeedle(namedParties, "corrie henn"),
                    resolveNamedPartyByNeedle(namedParties, "corrie")
            );
        }
        return "";
    }

    private static String resolveNamedPartyByNeedle(JSONArray namedParties, String needle) {
        if (namedParties == null || needle == null || needle.trim().isEmpty()) {
            return "";
        }
        String normalizedNeedle = needle.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < namedParties.length(); i++) {
            JSONObject party = namedParties.optJSONObject(i);
            if (party == null) {
                continue;
            }
            String name = party.optString("name", "").trim();
            if (!isUsableActorName(name)) {
                continue;
            }
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (normalizedName.equals(normalizedNeedle)
                    || normalizedName.contains(normalizedNeedle)
                    || normalizedNeedle.contains(normalizedName)) {
                return name;
            }
        }
        return "";
    }

    private static String sanitizeActorCandidate(String candidate) {
        if (candidate == null) {
            return "";
        }
        String cleaned = candidate.replace('\u2019', '\'').replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("(?i)^(am|pm|from|to|cc|bcc|subject|sent|date|message|reply|forwarded|received|attachment|attached|file|footer|disclaimer)\\b\\s+", "").trim();
        cleaned = cleaned.replaceAll("(?i)\\b(sent|from|to|cc|bcc|subject|date|message|reply|forwarded|received|attachment|attached|file|footer|disclaimer)\\b\\s*$", "").trim();
        cleaned = cleaned.replaceAll("(?i)['`]s\\s+email(?:\\s+dated)?\\b.*$", "").trim();
        cleaned = cleaned.replaceAll("(?i)\\b(admission\\s+email|email\\s+dated|email|wrote)\\b.*$", "").trim();
        cleaned = cleaned.replaceAll("(?i)\\bmobile\\b\\s*$", "").trim();
        cleaned = cleaned.replaceAll("(?i)^[\\-:,;\\s]+|[\\-:,;\\s]+$", "").trim();
        return cleaned;
    }

    private static String buildIncidentNarrative(String actor, String type, String description, int page) {
        String safeActor = actor == null || actor.trim().isEmpty() ? "An unresolved actor" : actor;
        String safeType = type == null ? "incident" : type.replace('_', ' ').toLowerCase(Locale.ROOT);
        String safeDescription = truncate(description, 180);
        if (safeDescription.isEmpty()) {
            return safeActor + " is linked to a " + safeType + " finding on page " + page + ".";
        }
        return safeActor + " is linked to " + safeType + " on page " + page + ": " + safeDescription;
    }

    private static String buildCommunicationNarrative(String actor, String text, int page) {
        String safeActor = actor == null || actor.trim().isEmpty() ? "An unresolved actor" : actor;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("evaluation fee")) {
            return safeActor + " requests an evaluation fee on page " + page + ": " + truncate(text, 180);
        }
        if (lower.contains("commercial jurisdiction")) {
            return safeActor + " states the matter falls under commercial jurisdiction on page " + page + ": " + truncate(text, 180);
        }
        if (lower.contains("criminal matter")) {
            return safeActor + " frames the matter as criminal rather than purely commercial on page " + page + ": " + truncate(text, 180);
        }
        if (lower.contains("google meeting") || lower.contains("meeting link")) {
            return safeActor + " schedules or confirms a meeting on page " + page + ": " + truncate(text, 180);
        }
        return safeActor + " is linked to anchored correspondence on page " + page + ": " + truncate(text, 180);
    }

    private static String classifyTimelineEvent(String lower) {
        if (lower == null) {
            return "";
        }
        if (containsAny(lower, "mandate", "no saps mandate", "not acting under", "whistleblower")) {
            return "MANDATE_OR_AUTHORITY";
        }
        if (containsAny(lower, "legal practice council", "lpc", "formal complaint", "complaint file")) {
            return "COMPLAINT_ESCALATION";
        }
        if (containsAny(lower, "hawks", "saps", "dpci", "criminal docket", "commercial crime", "precca")) {
            return "LAW_ENFORCEMENT_NOTICE";
        }
        if (containsAny(lower, "vacate the premises", "not to renew my contract", "evict", "end of june", "end of july")) {
            return "EVICTION_PRESSURE";
        }
        if (containsAny(lower, "no countersigned", "never countersigned", "not countersigned", "never signed back",
                "unsigned mou", "no valid contract", "no valid renewal", "not signed back")) {
            return "EXECUTION_STATUS";
        }
        if (containsAny(lower, "subject:", "from:", "dear ", "sent:", "wrote:")) {
            return "DATED_COMMUNICATION";
        }
        return "";
    }

    private static String extractAnchorDate(String text) {
        if (text == null) {
            return "";
        }
        Matcher structured = STRUCTURED_DATE_PATTERN.matcher(text);
        if (structured.find()) {
            return structured.group().trim();
        }
        Matcher dated = DATE_LINE_PATTERN.matcher(text);
        if (dated.find()) {
            return dated.group().trim();
        }
        Matcher generic = GENERIC_DATE_PATTERN.matcher(text);
        if (generic.find()) {
            return generic.group().trim();
        }
        return "";
    }

    private static boolean isSubstantiveDatedCommunication(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return containsAny(lower,
                "admit",
                "admission",
                "complaint",
                "termination",
                "invoice",
                "shipment",
                "order",
                "fraud",
                "oppression",
                "meeting",
                "request",
                "denied",
                "excluded",
                "payment",
                "withheld",
                "proceeded",
                "deal",
                "contract",
                "hawks",
                "saps",
                "lpc",
                "precca",
                "court",
                "case",
                "evidence",
                "witness",
                "statement",
                "accused",
                "guilty",
                "settlement",
                "goodwill");
    }

    private static boolean isPrimaryTimelineEvidence(String text, String eventType) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return false;
        }
        if (isSupportOnlyCorrespondenceText(text)) {
            return false;
        }
        if ("EXECUTION_STATUS".equals(eventType) || "EVICTION_PRESSURE".equals(eventType)) {
            return true;
        }
        return containsAny(lower,
                "memorandum of understanding",
                "lease agreement",
                "signature page",
                "signed at",
                "invoice no",
                "invoice sl",
                "thanks for the invoice",
                "thank you for the invoice",
                "termination notice",
                "termination email",
                "shipment date",
                "proceeded with the deal",
                "order completed",
                "group exit",
                "chat export",
                "whatsapp forensic analysis",
                "screenshots are forged",
                "message manipulation",
                "no countersigned",
                "never countersigned",
                "unsigned mou",
                "no valid renewal",
                "owner/lessor",
                "lessor",
                "lessee");
    }

    private static boolean isSupportOnlyCorrespondenceText(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean correspondenceShape = containsAny(lower,
                "subject:",
                "from:",
                "to:",
                "cc:",
                "bcc:",
                "dear ",
                "yours faithfully",
                "kind regards");
        boolean supportFraming = containsAny(lower,
                "formal notice",
                "demand for payment",
                "follow-up:",
                "follow up:",
                "final window",
                "settlement proposal",
                "settlement offer",
                "request that the council",
                "legal practice council",
                "complaint file",
                "your next move",
                "final opportunity",
                "consult your legal counsel",
                "this email serves as",
                "i refer to my complaint",
                "formal complaint",
                "attached two further documents",
                "this letter serves as formal notification");
        return supportFraming || (correspondenceShape && containsAny(lower,
                "complaint",
                "council",
                "settlement",
                "goodwill payment",
                "formal notice",
                "follow-up"));
    }

    private static boolean isNarrativeTimelineEvent(String eventType) {
        return "COMPLAINT_ESCALATION".equals(eventType)
                || "LAW_ENFORCEMENT_NOTICE".equals(eventType)
                || "MANDATE_OR_AUTHORITY".equals(eventType);
    }

    private static String buildTimelineSummary(String actor, String eventType, String text, String date, int page) {
        String safeActor = actor == null || actor.trim().isEmpty() || "unresolved actor".equalsIgnoreCase(actor)
                ? "the record"
                : actor;
        String prefix = date.isEmpty() ? "On page " + page + ", " : "On " + date + ", ";
        String lower = text.toLowerCase(Locale.ROOT);
        String excerpt = truncate(sanitizeTimelineExcerpt(text), 180);
        if (excerpt.isEmpty()) {
            excerpt = "the record contains anchored correspondence that requires manual review";
        }

        if ("MANDATE_OR_AUTHORITY".equals(eventType)) {
            if (containsAny(lower, "no saps mandate exists", "no saps mandate", "not acting under", "no mandate exists", "no mandate") &&
                    containsAny(lower, "required", "in law", "whistleblower", "reporting party")) {
                return prefix + safeActor + " stated that no SAPS mandate existed or was legally required for the disclosure.";
            }
            return prefix + safeActor + " is linked to an authority or mandate statement: " + excerpt;
        }
        if ("COMPLAINT_ESCALATION".equals(eventType)) {
            return prefix + safeActor + " is linked to an escalation or complaint reference: " + excerpt;
        }
        if ("LAW_ENFORCEMENT_NOTICE".equals(eventType)) {
            return prefix + safeActor + " is linked to a law-enforcement notice reference: " + excerpt;
        }
        if ("EVICTION_PRESSURE".equals(eventType)) {
            return prefix + safeActor + " is linked to an eviction or vacate-related statement: " + excerpt;
        }
        if ("EXECUTION_STATUS".equals(eventType)) {
            return prefix + safeActor + " is linked to an execution-status statement: " + excerpt;
        }
        if ("FINANCIAL_POSITION".equals(eventType)) {
            return prefix + safeActor + " is linked to a financial-position statement: " + excerpt;
        }
        if (containsAny(lower, "invoice", "thanks for the invoice", "thank you for the invoice", "purchase order", "order confirmed")) {
            return prefix + safeActor + " is linked to dated client or invoice correspondence.";
        }
        if (containsAny(lower, "private meeting", "meeting", "attorneys", "legal advice", "google meet")) {
            return prefix + safeActor + " is linked to a dated meeting or dispute communication.";
        }
        return prefix + safeActor + " is linked to anchored correspondence: " + excerpt;
    }

    private static String ordinalConfidenceForTimeline(String eventType, String actor, String date) {
        boolean actorResolved = actor != null && !actor.trim().isEmpty() && !"unresolved actor".equalsIgnoreCase(actor);
        if (!date.isEmpty() && actorResolved) {
            return "HIGH";
        }
        if (!date.isEmpty() || actorResolved) {
            return "MODERATE";
        }
        if ("DATED_COMMUNICATION".equals(eventType)) {
            return "LOW";
        }
        return "MODERATE";
    }

    private static String conductTypeForEvent(String eventType) {
        if ("EXECUTION_STATUS".equals(eventType)) {
            return "CONTRACT_EXECUTION_POSITION";
        }
        if ("EVICTION_PRESSURE".equals(eventType)) {
            return "EVICTION_OR_PRESSURE_POSITION";
        }
        if ("FINANCIAL_POSITION".equals(eventType)) {
            return "FINANCIAL_POSITION";
        }
        if ("LAW_ENFORCEMENT_NOTICE".equals(eventType) || "COMPLAINT_ESCALATION".equals(eventType)) {
            return "NOTICE_AND_ESCALATION";
        }
        if ("MANDATE_OR_AUTHORITY".equals(eventType)) {
            return "AUTHORITY_AND_STANDING";
        }
        return "";
    }

    private static String conductTypeForIncident(JSONObject item) {
        if (item == null) {
            return "";
        }
        String corpus = (item.optString("incidentType", "") + " "
                + item.optString("description", "") + " "
                + item.optString("excerpt", "")).toLowerCase(Locale.ROOT);
        if (containsAny(corpus,
                "fraudulent registration",
                "unlawful charter",
                "stolen vessel",
                "theft of vessel",
                "vessel was stolen",
                "stolen from",
                "permit holder",
                "unlawful possession",
                "chartering",
                "boat")) {
            return "UNLAWFUL_CONTROL_POSITION";
        }
        if (containsAny(corpus, "forced off site", "forced off", "vacate", "evict", "eviction", "lock out", "locked out")) {
            return "EVICTION_OR_PRESSURE_POSITION";
        }
        if (containsAny(corpus,
                "samsa",
                "dffe",
                "permit holder",
                "authority",
                "mandate",
                "standing",
                "license",
                "licence")) {
            return "AUTHORITY_AND_STANDING";
        }
        if (containsAny(corpus,
                "financial loss",
                "loss of income",
                "damages",
                "goodwill",
                "r ",
                "zar",
                "usd",
                "million",
                "payment")) {
            return "FINANCIAL_POSITION";
        }
        return "";
    }

    private static boolean isPromotableConductType(String conductType) {
        return "DOCUMENT_EXECUTION_STATE".equals(conductType)
                || "CONTRACT_EXECUTION_POSITION".equals(conductType)
                || "EVICTION_OR_PRESSURE_POSITION".equals(conductType)
                || "FINANCIAL_POSITION".equals(conductType)
                || "AUTHORITY_AND_STANDING".equals(conductType)
                || "UNLAWFUL_CONTROL_POSITION".equals(conductType);
    }

    private static String buildActorConductSummary(String actor, JSONObject item) {
        String conductType = item.optString("conductType", conductTypeForEvent(item.optString("eventType", "")));
        String date = item.optString("date", "");
        String prefix = date.isEmpty() ? actor : "On " + date + ", " + actor;
        if ("CONTRACT_EXECUTION_POSITION".equals(conductType)) {
            return prefix + " is linked to the no-countersignature or unsigned-execution pattern.";
        }
        if ("EVICTION_OR_PRESSURE_POSITION".equals(conductType)) {
            return prefix + " is linked to non-renewal, vacate, or eviction-pressure events.";
        }
        if ("FINANCIAL_POSITION".equals(conductType)) {
            return prefix + " is linked to an anchored financial-position reference.";
        }
        if ("UNLAWFUL_CONTROL_POSITION".equals(conductType)) {
            return prefix + " is linked to unlawful control, vessel-possession, charter, or registration misconduct.";
        }
        if ("NOTICE_AND_ESCALATION".equals(conductType)) {
            return prefix + " is linked to complaint, SAPS, Hawks, or LPC escalation correspondence.";
        }
        if ("AUTHORITY_AND_STANDING".equals(conductType)) {
            return prefix + " is linked to a dispute about mandate, authority, or whistleblower standing.";
        }
        return prefix + " is linked to anchored correspondence in the primary evidence record.";
    }

    private static String buildActorIntegritySummary(String actor, JSONObject item) {
        String corpus = (item.optString("summary", "") + " "
                + item.optString("excerpt", "") + " "
                + item.optString("narrative", "") + " "
                + item.optString("text", "")).toLowerCase(Locale.ROOT);
        if (looksLikeThreadOnlyIntegrityNoise(corpus)) {
            return "";
        }
        String type = item.optString("type", "DOCUMENT_INTEGRITY_FLAG");
        if ("MISSING_COUNTERSIGNATURE".equals(type)) {
            return actor + " is linked to primary evidence that a lease, MOU, or renewal was not countersigned or returned.";
        }
        if ("MISSING_EXECUTION_EVIDENCE".equals(type)) {
            return actor + " is linked to primary evidence that the operative document state remained unsigned or otherwise not validly executed.";
        }
        if ("BACKDATING_RISK".equals(type)) {
            return actor + " is linked to a document-integrity concern involving possible later-produced or backdated execution evidence.";
        }
        return actor + " is linked to a document-integrity issue that survived the primary extraction filter.";
    }

    private static boolean looksLikeThreadOnlyIntegrityNoise(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return false;
        }
        boolean hasThreadNoise = containsAny(lower,
                "confidential and/or privileged",
                "this email and any attachments",
                "liamhigh78@gmail.com",
                "re:",
                "from:",
                "subject:",
                "sent:",
                "to:");
        boolean hasHardExecutionSignal = containsAny(lower,
                "not countersigned",
                "never countersigned",
                "unsigned",
                "signature",
                "execution",
                "signed by",
                "blank signature");
        return hasThreadNoise && !hasHardExecutionSignal;
    }

    private static void copyAnchors(JSONArray target, JSONArray source, int fallbackPage) throws Exception {
        Set<Integer> seenPages = new LinkedHashSet<>();
        for (int i = 0; i < target.length(); i++) {
            JSONObject anchor = target.optJSONObject(i);
            if (anchor != null) {
                seenPages.add(anchor.optInt("page", 0));
            }
        }
        if (source != null) {
            for (int i = 0; i < source.length(); i++) {
                JSONObject anchor = source.optJSONObject(i);
                if (anchor == null) continue;
                int page = anchor.optInt("page", 0);
                if (page > 0 && seenPages.add(page)) {
                    target.put(buildAnchorObject(page));
                }
            }
        } else if (fallbackPage > 0 && seenPages.add(fallbackPage)) {
            target.put(buildAnchorObject(fallbackPage));
        }
    }

    private static String extractMoneyAmount(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = MONEY_PATTERN.matcher(text);
        while (matcher.find()) {
            String amount = matcher.group().replaceAll("\\s+", " ").trim();
            if (looksLikeAddressAmount(text, matcher.start(), matcher.end(), amount)) {
                continue;
            }
            return amount;
        }
        return "";
    }

    private static boolean looksLikeAddressAmount(String text, int start, int end, String amount) {
        if (text == null || amount == null) {
            return false;
        }
        String normalized = amount.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!normalized.matches("R\\d{1,3}")) {
            return false;
        }
        int windowStart = Math.max(0, start - 80);
        int windowEnd = Math.min(text.length(), end + 80);
        String window = text.substring(windowStart, windowEnd).toLowerCase(Locale.ROOT);
        String trailing = text.substring(end, Math.min(text.length(), end + 24)).toLowerCase(Locale.ROOT);
        return containsAny(window,
                "physical address",
                "address",
                "cnr ",
                "corner",
                "drive",
                "road",
                "street",
                "avenue",
                "premises",
                "port edward",
                "owen ellis",
                "r61",
                "r 61")
                || trailing.contains("&")
                || containsAny(trailing, " drive", " road", " street", " avenue", " lane", " highway");
    }

    private static String extractMoneyCurrency(String text) {
        String amount = extractMoneyAmount(text).toUpperCase(Locale.ROOT);
        if (amount.startsWith("ZAR") || amount.startsWith("R")) {
            return "ZAR";
        }
        if (amount.startsWith("USD") || amount.startsWith("$")) {
            return "USD";
        }
        if (amount.startsWith("AED")) {
            return "AED";
        }
        if (amount.startsWith("EUR")) {
            return "EUR";
        }
        if (amount.startsWith("GBP")) {
            return "GBP";
        }
        return "";
    }

    private static boolean hasFinancialAnchor(String lower) {
        if (lower == null || lower.trim().isEmpty()) {
            return false;
        }
        boolean hasAmount = !extractMoneyAmount(lower).isEmpty()
                || containsAny(lower, "million", "billion", "aed ", "zar ", "usd ", "$");
        boolean invoiceContext = containsAny(lower,
                "invoice no", "invoice number", "invoice sl", "thanks for the invoice",
                "thank you for the invoice", "invoice attached")
                || (hasAmount && containsAny(lower, "invoice"));
        boolean bankTransferContext = hasAmount && containsAny(lower,
                "bank transfer", "wire transfer", "swift", "account number",
                "branch code", "payment must reflect", "bank name");
        boolean settlementContext = hasAmount && containsAny(lower,
                "payment due", "due immediately", "repayment", "proposal for the transfer",
                "settlement amount", "amount due");
        boolean leaseOrChargeContext = hasAmount && containsAny(lower,
                "goodwill", "extension fee", "upgrade cost", "upgrade costs",
                "rent paid", "monthly rent", "rental");
        boolean shareDistributionContext = hasAmount
                && containsAny(lower, "30% share", "share of profits", "profit share", "70/30", "30 percent")
                && containsAny(lower, "deal", "transaction", "shipment", "export", "invoice");
        return invoiceContext
                || bankTransferContext
                || settlementContext
                || leaseOrChargeContext
                || shareDistributionContext;
    }

    private static boolean isPrimaryFinancialEvidence(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return false;
        }
        if (isDemandOrSettlementFraming(lower)) {
            return false;
        }
        return containsAny(lower,
                "invoice no",
                "invoice sl",
                "thanks for the invoice",
                "thank you for the invoice",
                "70/30",
                "30% share",
                "profit share",
                "share of profits",
                "monthly rent",
                "rent paid",
                "upgrade contributions",
                "upgrade cost",
                "goodwill waiver",
                "goodwill remains the property",
                "countersigned lease",
                "not countersigned",
                "never countersigned");
    }

    private static boolean isDemandOrSettlementFraming(String lower) {
        return containsAny(lower,
                "formal notice",
                "demand for payment",
                "deadline",
                "final window",
                "settlement proposal",
                "settlement offer",
                "final reminder",
                "your next move",
                "final opportunity",
                "civil legal proceedings",
                "court costs and interest",
                "payment due immediately");
    }

    private static void appendProfitShareReconciliationFinding(
            LinkedHashMap<String, JSONObject> deduped,
            List<OcrTextBlock> blocks,
            JSONArray namedParties,
            NativeEvidenceResult nativeEvidence
    ) throws Exception {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        OcrTextBlock basisBlock = null;
        OcrTextBlock executionBlock = null;
        OcrTextBlock amountBlock = null;
        OcrTextBlock nonPaymentBlock = null;

        for (OcrTextBlock block : blocks) {
            if (block == null || block.text == null || block.confidence <= 0f) {
                continue;
            }
            if (isSecondaryNarrativeText(block.text)) {
                continue;
            }
            String lower = block.text.toLowerCase(Locale.ROOT);
            if (basisBlock == null && isProfitShareBasis(lower)) {
                basisBlock = block;
            }
            if (isDealExecutionSignal(lower)
                    && (executionBlock == null || scoreExecutionSignal(block.text) > scoreExecutionSignal(executionBlock.text))) {
                executionBlock = block;
            }
            if (isAmountBearingDealSignal(lower)
                    && (amountBlock == null || scoreAmountSignal(block.text) > scoreAmountSignal(amountBlock.text))) {
                amountBlock = block;
            }
            if (isNonPaymentSignal(lower)
                    && (nonPaymentBlock == null || scoreNonPaymentSignal(block.text) > scoreNonPaymentSignal(nonPaymentBlock.text))) {
                nonPaymentBlock = block;
            }
        }

        if (basisBlock == null || executionBlock == null || nonPaymentBlock == null) {
            return;
        }
        if (amountBlock == null) {
            amountBlock = executionBlock;
        }

        String executedAmount = extractMoneyAmount(amountBlock.text);
        double executedValue = parseMoneyValue(executedAmount);
        double shareFraction = extractProfitShareFraction(basisBlock.text);
        if (executedValue <= 0.0d || shareFraction <= 0.0d) {
            return;
        }

        double expectedShareValue = executedValue * shareFraction;
        String expectedShareText = formatDerivedAmount(executedAmount, expectedShareValue);
        String actor = inferReconciliationActor(nonPaymentBlock, executionBlock, basisBlock, namedParties);

        LinkedHashSet<Integer> pages = new LinkedHashSet<>();
        pages.add(basisBlock.pageIndex + 1);
        pages.add(executionBlock.pageIndex + 1);
        pages.add(nonPaymentBlock.pageIndex + 1);
        pages.add(amountBlock.pageIndex + 1);

        boolean compactEvidenceSet = isCompactEvidenceSet(nativeEvidence, blocks);
        String status = pages.size() >= 3 || (compactEvidenceSet && pages.size() >= 2) ? "VERIFIED" : "CANDIDATE";
        JSONObject item = new JSONObject();
        item.put("page", firstPage(pages));
        item.put("actor", actor);
        item.put("amountCategory", "FINANCIAL_RECONCILIATION");
        item.put("findingType", "UNPAID_PROFIT_SHARE");
        item.put("amount", executedAmount);
        item.put("expectedShare", expectedShareText);
        item.put("basis", buildProfitShareBasisLabel(basisBlock.text, shareFraction));
        String counterparty = inferKnownCounterparty(actor, namedParties,
                mergeText(basisBlock, executionBlock, amountBlock, nonPaymentBlock));
        item.put("counterparty", isUsableActorName(counterparty) ? counterparty : "");
        item.put("reconciled", false);
        item.put("discrepancy", expectedShareText);
        item.put("status", status);
        item.put("confidence", "VERIFIED".equals(status) ? "HIGH" : "MODERATE");
        item.put("primaryEvidence", true);
        item.put("supportOnly", false);
        item.put("summary", buildReconciliationSummary(actor, executedAmount, expectedShareText, shareFraction, status));
        item.put("narrative", buildReconciliationSummary(actor, executedAmount, expectedShareText, shareFraction, status));
        item.put("excerpt", truncate(selectFinancialFindingExcerptText(
                amountBlock,
                executionBlock,
                basisBlock,
                nonPaymentBlock
        ), 260));
        JSONArray anchors = new JSONArray();
        for (Integer page : pages) {
            anchors.put(buildAnchorObject(page));
        }
        item.put("anchors", anchors);
        String dedupeKey = "FINANCIAL_RECONCILIATION|" + executedAmount + "|" + expectedShareText;
        deduped.put(dedupeKey, item);
    }

    private static void appendInvoiceNonPaymentFinding(
            LinkedHashMap<String, JSONObject> deduped,
            List<OcrTextBlock> blocks,
            JSONArray namedParties,
            NativeEvidenceResult nativeEvidence
    ) throws Exception {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        OcrTextBlock contractBlock = null;
        OcrTextBlock invoiceBlock = null;
        OcrTextBlock paymentCommitmentBlock = null;
        OcrTextBlock nonPaymentBlock = null;

        for (OcrTextBlock block : blocks) {
            if (block == null || block.text == null || block.confidence <= 0f) {
                continue;
            }
            if (isSecondaryNarrativeText(block.text)) {
                continue;
            }
            String lower = block.text.toLowerCase(Locale.ROOT);
            if (contractBlock == null && isContractInvoiceBasis(lower)) {
                contractBlock = block;
            }
            if (invoiceBlock == null && isInvoiceDocumentSignal(lower)) {
                invoiceBlock = block;
            }
            if (isInvoiceAcceptanceSignal(lower)
                    && (paymentCommitmentBlock == null
                    || scoreInvoiceAcceptanceSignal(block.text) > scoreInvoiceAcceptanceSignal(paymentCommitmentBlock.text))) {
                paymentCommitmentBlock = block;
            }
            if (isInvoiceNonPaymentSignal(lower)
                    && (nonPaymentBlock == null
                    || scoreInvoiceNonPaymentSignal(block.text) > scoreInvoiceNonPaymentSignal(nonPaymentBlock.text))) {
                nonPaymentBlock = block;
            }
        }

        if ((contractBlock == null && invoiceBlock == null) || nonPaymentBlock == null) {
            return;
        }

        OcrTextBlock amountBlock = invoiceBlock != null ? invoiceBlock : contractBlock;
        String amount = amountBlock == null ? "" : extractMoneyAmount(amountBlock.text);
        if (amount.isEmpty()) {
            return;
        }

        String actor = inferInvoiceNonPaymentActor(paymentCommitmentBlock, nonPaymentBlock, contractBlock, namedParties);
        if (!isUsableActorName(actor)) {
            return;
        }
        String counterparty = inferKnownCounterparty(
                actor,
                namedParties,
                mergeText(contractBlock, invoiceBlock, paymentCommitmentBlock, nonPaymentBlock)
        );
        if (!isUsableActorName(counterparty)) {
            return;
        }

        LinkedHashSet<Integer> pages = new LinkedHashSet<>();
        addPage(pages, contractBlock);
        addPage(pages, invoiceBlock);
        addPage(pages, paymentCommitmentBlock);
        addPage(pages, nonPaymentBlock);

        boolean compactEvidenceSet = isCompactEvidenceSet(nativeEvidence, blocks);
        boolean verified = paymentCommitmentBlock != null && pages.size() >= 2 && compactEvidenceSet;
        if (!verified && paymentCommitmentBlock != null && pages.size() >= 3) {
            verified = true;
        }

        String basis = invoiceBlock != null ? "ACCEPTED_INVOICE" : "CONTRACT_PRICE_OBLIGATION";
        JSONObject item = new JSONObject();
        item.put("page", firstPage(pages));
        item.put("actor", actor);
        item.put("amountCategory", "INVOICE_NON_PAYMENT");
        item.put("findingType", "UNPAID_INVOICE");
        item.put("amount", amount);
        item.put("basis", basis);
        item.put("counterparty", counterparty);
        item.put("reconciled", false);
        item.put("discrepancy", amount);
        item.put("status", verified ? "VERIFIED" : "CANDIDATE");
        item.put("confidence", verified ? "HIGH" : "MODERATE");
        item.put("primaryEvidence", true);
        item.put("supportOnly", false);
        item.put("summary", buildInvoiceNonPaymentSummary(actor, amount, verified));
        item.put("narrative", buildInvoiceNonPaymentSummary(actor, amount, verified));
        item.put("excerpt", truncate(nonPaymentBlock.text.replaceAll("\\s+", " ").trim(), 260));
        JSONArray anchors = new JSONArray();
        for (Integer page : pages) {
            anchors.put(buildAnchorObject(page));
        }
        item.put("anchors", anchors);
        String dedupeKey = "UNPAID_INVOICE|" + actor.toLowerCase(Locale.ROOT) + "|" + amount;
        deduped.put(dedupeKey, item);
    }

    private static int firstPage(LinkedHashSet<Integer> pages) {
        for (Integer page : pages) {
            if (page != null && page > 0) {
                return page;
            }
        }
        return 0;
    }

    private static boolean isProfitShareBasis(String lower) {
        return containsAny(lower, "70/30", "30% share", "30 percent", "profit share", "share of profits", "30% to")
                && containsAny(lower, "agreement", "export", "profits", "split", "transaction", "client");
    }

    private static boolean isDealExecutionSignal(String lower) {
        return containsAny(lower,
                "proceeded with the deal", "order completed", "completed the order", "shipment",
                "thanks for the invoice", "thank you for the invoice", "invoice sl", "invoice")
                && containsAny(lower, "deal", "order", "invoice", "shipment", "transaction", "export", "client");
    }

    private static boolean isAmountBearingDealSignal(String lower) {
        return MONEY_PATTERN.matcher(lower).find()
                && containsAny(lower, "invoice", "order", "shipment", "deal", "export", "thanks for the invoice", "thank you for the invoice");
    }

    private static boolean isNonPaymentSignal(String lower) {
        return containsAny(lower,
                "no payment", "not paid", "never paid", "withheld", "no 30% payment", "no 30% paid",
                "no 30% was paid", "30% not paid", "unpaid share", "profits withheld");
    }

    private static int scoreExecutionSignal(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        int score = 0;
        if (containsAny(lower, "proceeded with the deal", "order completed", "completed the order")) {
            score += 8;
        }
        if (containsAny(lower, "thanks for the invoice", "thank you for the invoice")) {
            score += 6;
        }
        if (isAmountBearingDealSignal(lower)) {
            score += 4;
        }
        return score;
    }

    private static int scoreAmountSignal(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        int score = 0;
        if (MONEY_PATTERN.matcher(lower).find()) {
            score += 4;
        }
        if (containsAny(lower, "invoice", "shipment", "order", "export")) {
            score += 3;
        }
        if (containsAny(lower, "thanks for the invoice", "thank you for the invoice")) {
            score += 4;
        }
        return score;
    }

    private static int scoreNonPaymentSignal(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        int score = 0;
        if (containsAny(lower, "no 30% payment", "30% not paid", "profits withheld")) {
            score += 8;
        }
        if (containsAny(lower, "not paid", "no payment", "never paid", "withheld")) {
            score += 4;
        }
        return score;
    }

    private static double extractProfitShareFraction(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "70/30", "30% share", "30 percent", "30% to")) {
            return 0.30d;
        }
        Matcher matcher = Pattern.compile("(\\d{1,2})\\s*%").matcher(lower);
        while (matcher.find()) {
            try {
                double value = Double.parseDouble(matcher.group(1));
                if (value > 0.0d && value < 100.0d) {
                    return value / 100.0d;
                }
            } catch (Exception ignore) {
                // ignore malformed percentages
            }
        }
        return 0.0d;
    }

    private static double parseMoneyValue(String amount) {
        if (amount == null || amount.trim().isEmpty()) {
            return 0.0d;
        }
        String normalized = amount.replaceAll("[^0-9.,]", "");
        if (normalized.isEmpty()) {
            return 0.0d;
        }
        normalized = normalized.replace(",", "");
        try {
            return Double.parseDouble(normalized);
        } catch (Exception ignore) {
            return 0.0d;
        }
    }

    private static String formatDerivedAmount(String sourceAmount, double derivedValue) {
        String prefix = "$";
        if (sourceAmount != null) {
            if (sourceAmount.trim().startsWith("R")) {
                prefix = "R";
            } else if (sourceAmount.trim().startsWith("AED")) {
                prefix = "AED ";
            } else if (sourceAmount.trim().startsWith("USD")) {
                prefix = "USD ";
            } else if (sourceAmount.contains("$")) {
                prefix = "$";
            }
        }
        long rounded = Math.round(derivedValue);
        return prefix + String.format(Locale.US, "%,.0f", (double) rounded);
    }

    private static String buildProfitShareBasisLabel(String text, double shareFraction) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "70/30")) {
            return "70/30 profit-share basis";
        }
        if (containsAny(lower, "profit share")) {
            return String.format(Locale.US, "%.0f%% profit-share basis", shareFraction * 100.0d);
        }
        return String.format(Locale.US, "%.0f%% share basis", shareFraction * 100.0d);
    }

    private static String selectFinancialFindingExcerptText(
            OcrTextBlock amountBlock,
            OcrTextBlock executionBlock,
            OcrTextBlock basisBlock,
            OcrTextBlock nonPaymentBlock
    ) {
        OcrTextBlock[] preferred = new OcrTextBlock[]{amountBlock, executionBlock, basisBlock, nonPaymentBlock};
        String bestExcerpt = "";
        int bestScore = Integer.MIN_VALUE;
        for (OcrTextBlock block : preferred) {
            if (block == null || block.text == null) {
                continue;
            }
            String cleaned = block.text.replaceAll("\\s+", " ").trim();
            if (cleaned.isEmpty() || looksLikeSealedSourceWrapper(cleaned)) {
                continue;
            }
            int score = scoreFinancialExcerptCandidate(cleaned);
            if (score > bestScore) {
                bestScore = score;
                bestExcerpt = cleaned;
            }
        }
        if (!bestExcerpt.isEmpty()) {
            return bestExcerpt;
        }
        return nonPaymentBlock != null && nonPaymentBlock.text != null
                ? nonPaymentBlock.text.replaceAll("\\s+", " ").trim()
                : "";
    }

    private static int scoreFinancialExcerptCandidate(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        if (MONEY_PATTERN.matcher(text).find()) {
            score += 8;
        }
        if (containsAny(lower,
                "70/30",
                "30% share",
                "30 percent",
                "profit share",
                "invoice",
                "payment",
                "withheld",
                "unpaid",
                "repayment",
                "initial investment",
                "indebted",
                "owed")) {
            score += 6;
        }
        if (containsAny(lower,
                "wanted to withdraw from the business",
                "withdraw from the business",
                "shareholder exclusion",
                "shareholder oppression")) {
            score += 3;
        }
        if (looksLikeEmotionalCorrespondence(text)) {
            score -= 10;
        }
        if (containsAny(lower,
                "personal meeting",
                "i called you names",
                "you are hurting",
                "please be honest")) {
            score -= 4;
        }
        return score;
    }

    private static boolean looksLikeEmotionalCorrespondence(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return containsAny(lower,
                "i asked you how many times",
                "personal meeting",
                "you are hurting",
                "i trusted you",
                "please be honest",
                "my family",
                "emotionally");
    }

    private static boolean looksLikeSealedSourceWrapper(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return containsAny(lower,
                "verum omnis sealed evidence source:",
                "verify seal",
                "page 1 of",
                "page 2 of",
                "page 3 of");
    }

    private static String inferReconciliationActor(
            OcrTextBlock nonPaymentBlock,
            OcrTextBlock executionBlock,
            OcrTextBlock basisBlock,
            JSONArray namedParties
    ) {
        String actor = nonPaymentBlock == null ? "" : inferSubjectActor(nonPaymentBlock.text, namedParties);
        if (!actor.isEmpty() && !"unresolved actor".equalsIgnoreCase(actor)) {
            return actor;
        }
        actor = executionBlock == null ? "" : inferSubjectActor(executionBlock.text, namedParties);
        if (!actor.isEmpty() && !"unresolved actor".equalsIgnoreCase(actor)) {
            return actor;
        }
        actor = basisBlock == null ? "" : inferSubjectActor(basisBlock.text, namedParties);
        return actor == null || actor.trim().isEmpty() ? "unresolved actor" : actor;
    }

    private static String inferInvoiceNonPaymentActor(
            OcrTextBlock paymentCommitmentBlock,
            OcrTextBlock nonPaymentBlock,
            OcrTextBlock contractBlock,
            JSONArray namedParties
    ) {
        String actor = paymentCommitmentBlock == null ? "" : inferSubjectActor(paymentCommitmentBlock.text, namedParties);
        if (isUsableActorName(actor)) {
            return actor;
        }
        actor = nonPaymentBlock == null ? "" : inferSubjectActor(nonPaymentBlock.text, namedParties);
        if (isUsableActorName(actor)) {
            return actor;
        }
        actor = contractBlock == null ? "" : inferSubjectActor(contractBlock.text, namedParties);
        return isUsableActorName(actor) ? actor : "unresolved actor";
    }

    private static String inferKnownCounterparty(String actor, JSONArray namedParties, String text) {
        String counterparty = inferFinancialCounterparty(text, namedParties, actor);
        if (isUsableActorName(counterparty)) {
            return counterparty;
        }
        if (namedParties == null) {
            return "";
        }
        for (int i = 0; i < namedParties.length(); i++) {
            JSONObject party = namedParties.optJSONObject(i);
            if (party == null) {
                continue;
            }
            String name = party.optString("name", "").trim();
            if (!isUsableActorName(name) || name.equalsIgnoreCase(actor)) {
                continue;
            }
            return name;
        }
        return "";
    }

    private static String mergeText(OcrTextBlock... blocks) {
        StringBuilder sb = new StringBuilder();
        if (blocks == null) {
            return "";
        }
        for (OcrTextBlock block : blocks) {
            if (block == null || block.text == null || block.text.trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(block.text.trim());
        }
        return sb.toString();
    }

    private static void addPage(LinkedHashSet<Integer> pages, OcrTextBlock block) {
        if (pages == null || block == null) {
            return;
        }
        pages.add(block.pageIndex + 1);
    }

    private static String buildReconciliationSummary(
            String actor,
            String executedAmount,
            String expectedShareText,
            double shareFraction,
            String status
    ) {
        String subject = actor == null || actor.trim().isEmpty() || "unresolved actor".equalsIgnoreCase(actor)
                ? "The record"
                : actor;
        String percent = String.format(Locale.US, "%.0f%%", shareFraction * 100.0d);
        if ("VERIFIED".equalsIgnoreCase(status)) {
            return subject + " is linked to a verified profit-share reconciliation gap: an executed transaction of "
                    + executedAmount + " under a " + percent + " share basis leaves an unpaid share of "
                    + expectedShareText + ".";
        }
        return subject + " is linked to a candidate profit-share reconciliation gap involving "
                + executedAmount + " and an expected share of " + expectedShareText + ".";
    }

    private static String classifyFinancialCategory(String lower) {
        if (containsAny(lower, "70/30", "30% share", "30 percent", "profit share")) {
            return "SHARE_DISTRIBUTION";
        }
        if (containsAny(lower, "goodwill")) {
            return "GOODWILL";
        }
        if (containsAny(lower, "rent paid", "rental")) {
            return "RENT_EXTRACTION";
        }
        if (containsAny(lower, "upgrade cost", "upgrade costs")) {
            return "UPGRADE_COSTS";
        }
        if (containsAny(lower, "extension fee", "5-year extension")) {
            return "EXTENSION_FEE";
        }
        if (containsAny(lower, "payment due", "proposal for the transfer", "due immediately", "repayment", "invoice", "bank transfer", "wire transfer")) {
            return "PAYMENT_DEMAND";
        }
        return "FINANCIAL_REFERENCE";
    }

    private static boolean isContractInvoiceBasis(String lower) {
        if (lower == null || lower.trim().isEmpty()) {
            return false;
        }
        return MONEY_PATTERN.matcher(lower).find()
                && containsAny(lower, "contract price", "payable within", "binding once the invoice is accepted", "contract ref", "agreement date");
    }

    private static boolean isInvoiceDocumentSignal(String lower) {
        if (lower == null || lower.trim().isEmpty()) {
            return false;
        }
        return MONEY_PATTERN.matcher(lower).find()
                && containsAny(lower, "invoice", "issued on", "invoice attached");
    }

    private static boolean isInvoiceAcceptanceSignal(String lower) {
        if (lower == null || lower.trim().isEmpty()) {
            return false;
        }
        return containsAny(lower,
                "thanks for the invoice",
                "thank you for the invoice",
                "we proceeded with the deal",
                "payment will be released",
                "invoice accepted",
                "accepted invoice",
                "agreed to a review meeting");
    }

    private static boolean isInvoiceNonPaymentSignal(String lower) {
        if (lower == null || lower.trim().isEmpty()) {
            return false;
        }
        return containsAny(lower,
                "withheld payment",
                "payment withheld",
                "not paid",
                "never paid",
                "payment denial",
                "denied authorising payment",
                "contract breach",
                "complaint about withheld payment");
    }

    private static int scoreInvoiceAcceptanceSignal(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        int score = 0;
        if (containsAny(lower, "payment will be released")) {
            score += 8;
        }
        if (containsAny(lower, "we proceeded with the deal")) {
            score += 6;
        }
        if (containsAny(lower, "thanks for the invoice", "thank you for the invoice")) {
            score += 5;
        }
        if (containsAny(lower, "review meeting")) {
            score += 2;
        }
        return score;
    }

    private static int scoreInvoiceNonPaymentSignal(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        int score = 0;
        if (containsAny(lower, "withheld payment", "payment withheld")) {
            score += 8;
        }
        if (containsAny(lower, "denied authorising payment")) {
            score += 7;
        }
        if (containsAny(lower, "contract breach")) {
            score += 4;
        }
        if (containsAny(lower, "not paid", "never paid")) {
            score += 3;
        }
        return score;
    }

    private static String buildInvoiceNonPaymentSummary(String actor, String amount, boolean verified) {
        String subject = actor == null || actor.trim().isEmpty() || "unresolved actor".equalsIgnoreCase(actor)
                ? "A named party"
                : actor;
        if (verified) {
            return subject + " is linked to a verified invoice non-payment pattern: an acknowledged invoice of "
                    + amount + " remained unpaid after acceptance or payment assurance.";
        }
        return subject + " is linked to a candidate invoice non-payment pattern involving an unpaid amount of "
                + amount + ".";
    }

    private static String buildFinancialSummary(String actor, String category, String amount, String text, String date, int page) {
        String subject = actor == null || actor.trim().isEmpty() || "unresolved actor".equalsIgnoreCase(actor)
                ? "the record"
                : actor;
        String prefix = date.isEmpty() ? "On page " + page + ", " : "On " + date + ", ";
        String amountPart = amount.isEmpty() ? "" : " " + amount;
        if ("GOODWILL".equals(category)) {
            return prefix + subject + " is linked to a goodwill reference" + amountPart + ".";
        }
        if ("RENT_EXTRACTION".equals(category)) {
            return prefix + subject + " is linked to a rent reference" + amountPart + ".";
        }
        if ("UPGRADE_COSTS".equals(category)) {
            return prefix + subject + " is linked to an upgrade-cost reference" + amountPart + ".";
        }
        if ("EXTENSION_FEE".equals(category)) {
            return prefix + subject + " is linked to an extension-fee or renewal-payment reference" + amountPart + ".";
        }
        if ("SHARE_DISTRIBUTION".equals(category)) {
            return prefix + subject + " is linked to a profit-share or split reference" + amountPart + ".";
        }
        if ("PAYMENT_DEMAND".equals(category)) {
            return prefix + subject + " is linked to a payment-demand reference" + amountPart + ".";
        }
        return prefix + subject + " is linked to a financial reference" + amountPart + ".";
    }

    private static String sanitizeTimelineExcerpt(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("(?i)^old woman angelfish\\s+", "");
        cleaned = cleaned.replaceAll("(?i)^re:\\s*meeting\\s*\\d+\\s*messages\\s*", "");
        cleaned = cleaned.replaceAll("(?i)^criminal complaint lodged\\s*[–-]\\s*saps margate\\s*\\d+\\s*messages\\s*", "");
        cleaned = cleaned.replaceAll("(?i)\\[quoted text hidden\\]", " ");
        cleaned = cleaned.replaceAll("(?i)\\bfrom:\\s*[^\\s]+", " ");
        cleaned = cleaned.replaceAll("(?i)\\bto:\\s*[^\\s]+", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private static String classifyNarrativeTheme(String lower) {
        if (lower == null) {
            return "";
        }
        if (containsAny(lower, "no countersigned", "never countersigned", "never signed back", "no valid contract", "unsigned mou")) {
            return "NO_COUNTERSIGNATURE_PATTERN";
        }
        if (containsAny(lower, "goodwill", "rent", "upgrade", "unlawful rent", "unlawful enrichment")) {
            return "CONTINUED_VALUE_EXTRACTION";
        }
        if (containsAny(lower, "not a civil", "criminal matter", "criminal docket", "precca", "racketeering")) {
            return "CRIMINAL_FRAMING_AFTER_NOTICE";
        }
        if (containsAny(lower, "sat on the evidence", "failed to act", "delay", "two months", "lpc complaint")) {
            return "NOTICE_DELAY_AND_ESCALATION";
        }
        if (containsAny(lower, "vulnerable person", "mentally broken", "extreme personal trauma", "traumatic family issues")) {
            return "VULNERABLE_PERSON_PATTERN";
        }
        if (containsAny(lower, "back-dated", "backdated", "produce signed copies now", "metadata will expose")) {
            return "BACKDATING_RISK_THEME";
        }
        return "";
    }

    private static String buildNarrativeThemeSummary(String theme) {
        if ("NO_COUNTERSIGNATURE_PATTERN".equals(theme)) {
            return "Multiple primary passages describe operators signing instruments that were never countersigned, returned, or validly executed.";
        }
        if ("CONTINUED_VALUE_EXTRACTION".equals(theme)) {
            return "Multiple primary passages describe continued rent, upgrade, or goodwill-value extraction despite disputed execution state.";
        }
        if ("CRIMINAL_FRAMING_AFTER_NOTICE".equals(theme)) {
            return "Repeated notices frame the matter as criminal rather than purely civil once the evidence bundle is said to have been received.";
        }
        if ("NOTICE_DELAY_AND_ESCALATION".equals(theme)) {
            return "Repeated passages describe delay after notice and escalation to LPC, SAPS, Hawks, or related bodies.";
        }
        if ("VULNERABLE_PERSON_PATTERN".equals(theme)) {
            return "The record repeatedly links one strand of the case to vulnerability or exploitation of a distressed person.";
        }
        if ("BACKDATING_RISK_THEME".equals(theme)) {
            return "The record repeatedly warns that any later-produced signed copy would need to be checked for backdating or metadata inconsistency.";
        }
        return "A repeated narrative theme survived the primary extraction filter.";
    }

    private static String normalizeDedupeText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String inferSubjectActor(String text, JSONArray namedParties) {
        if (text == null) {
            return "unresolved actor";
        }
        String quotedAuthor = extractQuotedOrFromActor(text, namedParties);
        if (!quotedAuthor.isEmpty()) {
            return quotedAuthor;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String mappedActor = inferActorFromKnownContext(lower, namedParties);
        if (!mappedActor.isEmpty()) {
            return mappedActor;
        }
        String inferredActor = inferActor(text, namedParties);
        if (isEvidenceResolvedActor(inferredActor)) {
            return inferredActor;
        }
        if (namedParties != null) {
            String bestMatch = "";
            int bestIndex = Integer.MAX_VALUE;
            int bestLength = -1;
            for (int i = 0; i < namedParties.length(); i++) {
                JSONObject party = namedParties.optJSONObject(i);
                if (party == null) continue;
                String name = party.optString("name", "").trim();
                if (!isUsableActorName(name)) {
                    continue;
                }
                String normalizedName = name.toLowerCase(Locale.ROOT);
                int index = lower.indexOf(normalizedName);
                if (index < 0) {
                    continue;
                }
                if (index < bestIndex || (index == bestIndex && name.length() > bestLength)) {
                    bestMatch = name;
                    bestIndex = index;
                    bestLength = name.length();
                }
            }
            if (!bestMatch.isEmpty()) {
                return bestMatch;
            }
        }
        return inferredActor;
    }

    private static String extractQuotedOrFromActor(String text, JSONArray namedParties) {
        if (text == null) {
            return "";
        }
        Pattern[] patterns = new Pattern[] {
                QUOTED_EMAIL_POSSESSIVE_AUTHOR_PATTERN,
                EMAIL_MESSAGE_AUTHOR_PATTERN,
                QUOTED_EMAIL_AUTHOR_PATTERN,
                WROTE_AUTHOR_PATTERN,
                ADMISSION_EMAIL_AUTHOR_PATTERN
        };
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                String candidate = matcher.group(1).trim();
                String resolved = resolveActorCandidate(candidate, namedParties);
                if (!resolved.isEmpty()) {
                    return resolved;
                }
            }
        }
        return "";
    }

    private static String ordinalConfidenceForIntegrity(String type, String actor, String text) {
        boolean actorResolved = actor != null && !actor.trim().isEmpty() && !"unresolved actor".equalsIgnoreCase(actor);
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if ("MISSING_COUNTERSIGNATURE".equals(type) || "MISSING_EXECUTION_EVIDENCE".equals(type)) {
            return actorResolved ? "HIGH" : "MODERATE";
        }
        if ("SIGNATURE_REGION_EMPTY".equals(type) || "SIGNATURE_MARKS_NOT_FOUND".equals(type)) {
            return actorResolved ? "MODERATE" : "LOW";
        }
        if ("BACKDATING_RISK".equals(type) || "METADATA_ANOMALY".equals(type)) {
            return containsAny(lower, "metadata", "hash", "timestamp") ? "MODERATE" : "LOW";
        }
        if ("CHAIN_OF_CUSTODY_GAP".equals(type)) {
            return "MODERATE";
        }
        return actorResolved ? "MODERATE" : "LOW";
    }

    private static boolean looksLikePersonName(String name) {
        if (name.length() < 4 || name.length() > 40) return false;
        if (looksLikeEmailHeader(name)) return false;
        if (NAME_STOPWORDS.contains(name)) return false;
        String[] parts = name.trim().split("\\s+");
        if (parts.length < 2) return false;
        if (parts.length >= 2 && parts[0].equalsIgnoreCase(parts[parts.length - 1])) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("page") || lower.contains("zone")) return false;
        if (containsActorNoisePhrase(lower) || containsContractRolePhrase(lower)) return false;
        if (lower.contains("invoice") || lower.contains("regards") || lower.contains("summary")
                || lower.contains("message") || lower.contains("operations") || lower.contains("generative")
                || lower.contains("good day") || lower.contains("thank you") || lower.contains("contact")
                || lower.contains("act") || lower.contains("manual") || lower.contains("terms")
                || lower.contains("business") || lower.contains("standards") || lower.contains("interest")
                || lower.contains("products") || lower.contains("road")
                || lower.contains("memorandum") || lower.contains("mobile")
                || lower.contains("settlement offer") || lower.contains("financial irregularities")
                || lower.contains("shareholder oppression") || lower.contains("unauthorized transfers")
                || lower.contains("gulf standard time") || lower.contains("angelfish")
                || lower.contains("butterflyfish") || lower.contains("goodwill theft")
                || lower.contains("cryptographic sealing") || lower.contains("registered address")
                || lower.contains("port edward garage") || lower.contains("wayne's world")
                || lower.contains("trade secrets") || lower.contains("service station")
                || lower.contains("property investments") || lower.contains("unlawful conduct")
                || lower.contains("civil remedies") || lower.contains("criminal remedies")
                || lower.contains("fraudulent misrepresentation") || lower.contains("final window")
                || lower.contains("bright idea projects") || lower.contains("ronnie moir travel")
                || lower.contains("astron marks") || lower.contains("owen ellis drive")
                || lower.contains("evidence the") || lower.contains("legal point application")
                || lower.contains("this case section") || lower.contains("expired leases")
                || lower.contains("illegal rent") || lower.contains("forced payments")
                || lower.contains("vulnerable person") || lower.contains("criminal conduct")
                || lower.contains("asset forfeiture unit") || lower.contains("negative evidence")
                || lower.contains("natal south") || lower.contains("when des")
                || lower.contains("verum omnis")
                || lower.contains("general manager")
                || lower.contains("legal department")
                || lower.contains("common purpose")) {
            return false;
        }
        if (name.startsWith("The ")) return false;
        for (String token : parts) {
            String clean = token.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z]", "");
            if (clean.isEmpty()) {
                continue;
            }
            if (ACTOR_NOISE_TOKENS.contains(clean)) {
                return false;
            }
        }
        return Character.isUpperCase(name.charAt(0));
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isUsableActorName(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty() || NAME_STOPWORDS.contains(trimmed) || looksLikeEmailHeader(trimmed)) {
            return false;
        }
        if (isNamedPartyCandidateNoise(trimmed)) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if ((lower.length() < 4 && !looksLikeVictimAlias(trimmed))
                || lower.equals("verum omnis")
                || lower.equals("yes")
                || lower.equals("not")
                || lower.equals("which")
                || lower.equals("screenshot")
                || lower.equals("account")
                || lower.equals("common purpose")
                || lower.equals("individuals")
                || lower.equals("matters")
                || lower.equals("general manager")
                || lower.equals("legal department")
                || lower.equals("your phone number")
                || lower.equals("april liam highcock")
                || lower.equals("explanation thank")
                || lower.equals("let kevin")
                || lower.equals("liam give kevin")
                || lower.equals("emphasis added")
                || lower.equals("austrian law")
                || lower.equals("austrian trade agents")
                || lower.equals("australian law")
                || lower.equals("australian competition")
                || lower.equals("section")
                || lower.equals("goodwill")
                || lower.equals("outcome")
                || lower.equals("pattern")
                || lower.equals("behind")
                || lower.equals("exposing")
                || lower.equals("racketeering")
                || lower.equals("arbitration")
                || lower.equals("from")
                || lower.equals("pm")
                || lower.equals("am")
                || lower.equals("to")
                || lower.equals("cc")
                || lower.equals("bcc")
                || lower.equals("subject")
                || lower.equals("evidence source")
                || lower.equals("source file")
                || lower.equals("case references")
                || lower.equals("case reference")
                || lower.equals("human report")
                || lower.equals("forensic report")
                || lower.equals("report generated")
                || lower.equals("mode")
                || lower.equals("case id")
                || lower.equals("jurisdiction")
                || lower.startsWith("evidence ")
                || lower.startsWith("legal point")
                || lower.startsWith("this case")
                || lower.startsWith("expired leases")
                || lower.startsWith("illegal rent")
                || lower.startsWith("forced payments")
                || lower.startsWith("vulnerable person")
                || lower.startsWith("criminal conduct")
                || lower.startsWith("when des")
                || lower.startsWith("asset forfeiture")
                || lower.startsWith("negative evidence")
                || lower.startsWith("natal south")) {
            return false;
        }
        if (containsActorNoisePhrase(lower) || containsContractRolePhrase(lower)) {
            return false;
        }
        return !(lower.contains("act")
                || lower.contains("manual")
                || lower.contains("terms")
                || lower.contains("business")
                || lower.contains("standards")
                || lower.contains("interest")
                || lower.contains("products")
                || lower.contains("pattern")
                || lower.contains("racketeering")
                || lower.contains("arbitration")
                || lower.contains("exposing")
                || lower.contains("behind")
                || lower.equals("south african")
                || lower.equals("the lessee")
                || lower.equals("the franchisee")
                || lower.equals("the franchisor"));
    }

    private static boolean isEvidenceResolvedActor(String actor) {
        return actor != null
                && isUsableActorName(actor)
                && !isJunkActor(actor)
                && !"unresolved actor".equalsIgnoreCase(actor.trim());
    }

    private static boolean looksLikeEmailHeader(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("from:")
                || lower.startsWith("to:")
                || lower.startsWith("cc:")
                || lower.startsWith("bcc:")
                || lower.startsWith("subject:")
                || lower.startsWith("sent:")
                || lower.startsWith("date:")
                || lower.equals("from")
                || lower.equals("to")
                || lower.equals("cc")
                || lower.equals("bcc")
                || lower.equals("subject")
                || lower.equals("sent")
                || lower.equals("date")
                || lower.contains(":")
                || lower.matches("^\\d{1,2}/\\d{1,2}/\\d{4}$");
    }

    private static boolean isJunkActor(String name) {
        if (name == null) {
            return true;
        }
        return JUNK_ACTORS.contains(name.trim());
    }

    private static boolean containsActorNoisePhrase(String lower) {
        if (lower == null || lower.trim().isEmpty()) {
            return false;
        }
        return lower.contains("meeting")
                || lower.contains("dear ")
                || lower.contains(" sir")
                || lower.contains("sirs")
                || lower.contains("screenshot")
                || lower.contains("your phone number")
                || lower.contains("april liam highcock")
                || lower.contains("explanation thank")
                || lower.contains("let kevin")
                || lower.contains("liam give kevin")
                || lower.contains("emphasis added")
                || lower.contains("austrian law")
                || lower.contains("austrian trade agents")
                || lower.contains("australian law")
                || lower.contains("australian competition")
                || lower.contains("common purpose")
                || lower.contains("evidence the")
                || lower.contains("petroleum products act")
                || lower.contains("legal point application")
                || lower.contains("this case section")
                || lower.contains("expired leases")
                || lower.contains("illegal rent")
                || lower.contains("forced payments")
                || lower.contains("vulnerable person")
                || lower.contains("criminal conduct")
                || lower.contains("when des")
                || lower.contains("asset forfeiture unit")
                || lower.contains("negative evidence")
                || lower.contains("natal south")
                || lower.contains("outcome")
                || lower.contains("pattern")
                || lower.contains("racketeering")
                || lower.contains("exposing")
                || lower.contains("arbitration")
                || lower.contains(" behind")
                || lower.contains("complaint")
                || lower.contains("complaints")
                || lower.contains("submission")
                || lower.contains("form")
                || lower.contains("crime")
                || lower.contains("intelligence")
                || lower.contains("franchise")
                || lower.contains("letter")
                || lower.contains("beach")
                || lower.contains("from:")
                || lower.contains("to:")
                || lower.contains("registration")
                || lower.contains("company")
                || lower.contains("declaration")
                || lower.contains("attachment")
                || lower.contains("gmail")
                || lower.contains("adobe")
                || lower.contains("cloud storage")
                || lower.contains("pty")
                || lower.contains("ltd")
                || lower.contains("llc")
                || lower.contains("inc")
                || lower.contains("final window")
                || lower.contains("goodwill payment")
                || lower.contains("settlement deadline")
                || lower.contains("final reminder")
                || lower.contains("deadline for payment")
                || lower.contains("settlement")
                || lower.contains("hong kong")
                || lower.contains("national crime")
                || lower.contains("complaints form")
                || lower.contains("glenmore")
                || lower.contains("memorandum")
                || lower.contains("mobile")
                || lower.contains("settlement offer")
                || lower.contains("financial irregularities")
                || lower.contains("shareholder oppression")
                || lower.contains("unauthorized transfers")
                || lower.contains("gulf standard time")
                || lower.contains("angelfish")
                || lower.contains("butterflyfish")
                || lower.contains("reeftribe")
                || lower.contains("goodwill theft")
                || lower.contains("cryptographic sealing")
                || lower.contains("registered address")
                || lower.contains("port edward garage")
                || lower.contains("wayne's world")
                || lower.contains("trade secrets")
                || lower.contains("service station")
                || lower.contains("property investments")
                || lower.contains("unlawful conduct")
                || lower.contains("civil remedies")
                || lower.contains("criminal remedies")
                || lower.contains("fraudulent misrepresentation")
                || lower.contains("final window")
                || lower.contains("bright idea projects")
                || lower.contains("ronnie moir travel")
                || lower.contains("astron marks")
                || lower.contains("owen ellis drive")
                || lower.contains("verum omnis");
    }

    private static boolean containsContractRolePhrase(String lower) {
        if (lower == null || lower.trim().isEmpty()) {
            return false;
        }
        for (String phrase : CONTRACT_ROLE_PHRASES) {
            if (lower.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSubjectMatchUsable(String subject, String lower, List<String> matchedTerms) {
        if (subject == null || lower == null || matchedTerms == null || matchedTerms.isEmpty()) {
            return false;
        }
        if ("Cybercrime".equals(subject)) {
            return containsAny(lower, "unauthorized access", "google archive", "archive request", "scaquaculture", "login", "gmail");
        }
        if ("Breach of Fiduciary Duty".equals(subject)) {
            return containsAny(lower, "fiduciary", "conflict of interest", "self-dealing", "proceeded with the deal", "profit diversion");
        }
        if ("Shareholder Oppression".equals(subject)) {
            return containsAny(lower, "shareholder", "private meeting", "excluded", "oppression");
        }
        if ("Financial Irregularities".equals(subject)) {
            return hasFinancialAnchor(lower);
        }
        return true;
    }

    private static boolean isIntegrityMatchUsable(String type, String lower) {
        if (type == null || lower == null) {
            return false;
        }
        if ("MISSING_COUNTERSIGNATURE".equals(type) || "MISSING_EXECUTION_EVIDENCE".equals(type)) {
            return containsAny(lower, "countersigned", "signed back", "unsigned", "mou", "lease", "renewal", "blank", "signature page", "lessor");
        }
        if ("BACKDATING_RISK".equals(type)) {
            return containsAny(lower, "back-dated", "backdated", "signed copies now", "later-produced", "metadata will expose");
        }
        if ("METADATA_ANOMALY".equals(type)) {
            return containsAny(lower, "metadata", "timestamp", "hash", "fingerprint");
        }
        if ("SIGNATURE_MISMATCH".equals(type)) {
            return containsAny(lower, "signature", "sign", "signature page");
        }
        if ("CHAIN_OF_CUSTODY_GAP".equals(type)) {
            return containsAny(lower, "continuity", "chain of custody", "custody gap");
        }
        return false;
    }

    private static void recordNameCandidate(
            String name,
            int page,
            Map<String, Set<Integer>> pagesByName,
            Map<String, Integer> occurrenceCounts
    ) {
        if (isNamedPartyCandidateNoise(name)) {
            return;
        }
        pagesByName.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(page);
        occurrenceCounts.put(name, occurrenceCounts.getOrDefault(name, 0) + 1);
    }

    private static boolean isNamedPartyCandidateNoise(String name) {
        if (name == null) {
            return true;
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return true;
        }
        return isAbstractVictimNoise(name)
                || lower.equals("trade license")
                || lower.equals("dual liability")
                || lower.equals("commit fraud")
                || lower.equals("cyber forgery")
                || lower.equals("investigating officer")
                || lower.equals("south african")
                || lower.equals("south african law")
                || lower.equals("google drive")
                || lower.equals("doc ref")
                || lower.equals("fabricate liability")
                || lower.equals("company")
                || lower.equals("breach")
                || lower.equals("was")
                || lower.startsWith("add ")
                || lower.startsWith("commit ")
                || lower.startsWith("fabricate ");
    }

    private static CorpusSnapshot buildCorpusSnapshot(NativeEvidenceResult nativeEvidence) {
        long startedAt = System.currentTimeMillis();
        CorpusSnapshot snapshot = new CorpusSnapshot();
        if (nativeEvidence == null) {
            logPhase("buildCorpusSnapshot", startedAt, "nativeEvidence absent");
            return snapshot;
        }
        if (nativeEvidence.sanitizedCorpusBlocks != null) {
            snapshot.blocks.addAll(nativeEvidence.sanitizedCorpusBlocks);
            snapshot.contaminatedBlockCount = nativeEvidence.sanitizedCorpusContaminatedBlockCount;
            snapshot.secondaryNarrativeBlockCount = nativeEvidence.sanitizedCorpusSecondaryNarrativeBlockCount;
            logPhase("buildCorpusSnapshot", startedAt,
                    "reusedCachedBlocks=" + snapshot.blocks.size()
                            + " contaminated=" + snapshot.contaminatedBlockCount
                            + " secondary=" + snapshot.secondaryNarrativeBlockCount);
            return snapshot;
        }
        Set<String> seen = new LinkedHashSet<>();
        if (nativeEvidence.documentTextBlocks != null) {
            appendSanitizedBlocks(snapshot, seen, nativeEvidence.documentTextBlocks);
        }
        if (nativeEvidence.ocrBlocks != null) {
            appendSanitizedBlocks(snapshot, seen, nativeEvidence.ocrBlocks);
        }
        if (nativeEvidence.secondaryNarrativeSource && snapshot.blocks.size() > 120) {
            List<OcrTextBlock> focused = selectFocusedEvidenceBlocks(snapshot.blocks, 120);
            snapshot.blocks.clear();
            snapshot.blocks.addAll(focused);
            Log.w(TAG, "buildCorpusSnapshot truncated mixed corpus to 120 focused blocks");
        }
        nativeEvidence.sanitizedCorpusBlocks = new ArrayList<>(snapshot.blocks);
        nativeEvidence.sanitizedCorpusContaminatedBlockCount = snapshot.contaminatedBlockCount;
        nativeEvidence.sanitizedCorpusSecondaryNarrativeBlockCount = snapshot.secondaryNarrativeBlockCount;
        logPhase("buildCorpusSnapshot", startedAt,
                "blocks=" + snapshot.blocks.size()
                        + " contaminated=" + snapshot.contaminatedBlockCount
                        + " secondary=" + snapshot.secondaryNarrativeBlockCount);
        return snapshot;
    }

    private static List<OcrTextBlock> selectFocusedEvidenceBlocks(List<OcrTextBlock> blocks, int limit) {
        if (blocks == null || blocks.size() <= limit) {
            return blocks == null ? new ArrayList<>() : new ArrayList<>(blocks);
        }
        List<OcrTextBlock> ranked = new ArrayList<>(blocks);
        ranked.sort((left, right) -> {
            int scoreCompare = Integer.compare(scoreEvidenceBlock(right), scoreEvidenceBlock(left));
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int pageCompare = Integer.compare(left.pageIndex, right.pageIndex);
            if (pageCompare != 0) {
                return pageCompare;
            }
            return Integer.compare(right.text != null ? right.text.length() : 0, left.text != null ? left.text.length() : 0);
        });
        List<OcrTextBlock> focused = new ArrayList<>();
        for (OcrTextBlock block : ranked) {
            focused.add(block);
            if (focused.size() >= limit) {
                break;
            }
        }
        focused.sort((left, right) -> Integer.compare(left.pageIndex, right.pageIndex));
        return focused;
    }

    private static int scoreEvidenceBlock(OcrTextBlock block) {
        if (block == null || block.text == null) {
            return 0;
        }
        String lower = block.text.toLowerCase(Locale.ROOT);
        int score = 0;
        if (containsAny(lower, "from:", "to:", "cc:", "bcc:", "subject:", "date:")) {
            score += 8;
        }
        if (containsAny(lower,
                "gary highcock", "liam highcock", "wayne nel", "desmond smith",
                "hawks", "saps", "whistleblower", "mandatory reporting", "precca",
                "termination", "terminate", "goodwill", "rent", "upgrade",
                "countersigned", "signed", "lease", "agreement", "complaint",
                "fraud", "extortion", "forged", "back-dated", "backdated")) {
            score += 6;
        }
        if (containsAny(lower,
                "legal practice council", "criminal docket", "asset forfeiture",
                "investigating officer", "goodwill payment", "professional negligence",
                "failure of legal duty", "all fuels", "operator", "affected operators")) {
            score += 4;
        }
        int trimmedLength = block.text.trim().length();
        if (trimmedLength >= 800) {
            score += 3;
        } else if (trimmedLength >= 250) {
            score += 2;
        } else if (trimmedLength >= 120) {
            score += 1;
        }
        return score;
    }

    private static List<OcrTextBlock> allTextBlocks(NativeEvidenceResult nativeEvidence) {
        return buildCorpusSnapshot(nativeEvidence).blocks;
    }

    private static void appendSanitizedBlocks(CorpusSnapshot snapshot, Set<String> seen, List<OcrTextBlock> source) {
        if (source == null) {
            return;
        }
        for (OcrTextBlock block : source) {
            if (block == null || block.text == null || block.confidence <= 0f) {
                continue;
            }
            if (containsGeneratedAnalysis(block.text)) {
                snapshot.contaminatedBlockCount++;
                continue;
            }
            String cleaned = sanitizeEvidenceText(block.text);
            if (cleaned.isEmpty()) {
                continue;
            }
            if (isSecondaryNarrativeText(cleaned)) {
                snapshot.secondaryNarrativeBlockCount++;
                continue;
            }
            String dedupeKey = block.pageIndex + "::" + cleaned;
            if (!seen.add(dedupeKey)) {
                continue;
            }
            snapshot.blocks.add(new OcrTextBlock(block.pageIndex, cleaned, block.confidence));
        }
    }

    private static String sanitizeEvidenceText(String text) {
        if (text == null) {
            return "";
        }
        String[] lines = text.split("\\r?\\n");
        StringBuilder kept = new StringBuilder();
        for (String line : lines) {
            String normalized = line == null ? "" : line.replaceAll("\\s+", " ").trim();
            String stripped = stripSystemMarkers(normalized);
            if (stripped.isEmpty() || shouldDropEvidenceLine(stripped)) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append('\n');
            }
            kept.append(stripped);
        }
        return kept.toString().replaceAll("\\s+", " ").trim();
    }

    private static String stripSystemMarkers(String line) {
        if (line == null) {
            return "";
        }
        String stripped = line;
        for (String marker : SYSTEM_LINE_MARKERS) {
            stripped = stripped.replaceAll("(?i)" + Pattern.quote(marker), " ");
        }
        return stripped.replaceAll("\\s+", " ").trim();
    }

    private static boolean shouldDropEvidenceLine(String line) {
        if (line == null) {
            return true;
        }
        String lower = line.toLowerCase(Locale.ROOT).replace('•', ' ').trim();
        if (lower.isEmpty()) {
            return true;
        }
        return lower.equals("verum omnis")
                || lower.equals("forensic engine")
                || lower.equals("constitutional forensic report")
                || lower.equals("offline deterministic analysis")
                || lower.startsWith("source:")
                || lower.startsWith("verum omnis sealed evidence source:")
                || lower.startsWith("page ")
                || lower.equals("verify seal")
                || lower.startsWith("confidential")
                || lower.startsWith("verum omnis seal |");
    }

    private static boolean isSecondaryNarrativeText(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsGeneratedAnalysis(lower)
                || looksLikeAdvocacyPage(lower)
                || looksLikeDeadlineOrSettlementLanguage(lower)) {
            return true;
        }
        if (looksLikeSyntheticForensicSummary(lower)) {
            return true;
        }
        for (String marker : SECONDARY_NARRATIVE_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeDeadlineOrSettlementLanguage(String lower) {
        if (lower == null || lower.trim().isEmpty()) {
            return false;
        }
        return containsAny(lower,
                "final window",
                "sealing & final window",
                "goodwill payment",
                "settlement deadline",
                "final reminder",
                "deadline for payment")
                || (lower.contains("deadline") && lower.contains("payment"))
                || (lower.contains("settlement") && lower.contains("deadline"));
    }

    private static boolean containsGeneratedAnalysis(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String marker : GENERATED_ANALYSIS_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeAdvocacyPage(String lower) {
        if (lower == null) {
            return false;
        }
        if (containsAny(lower, "from:", "to:", "sent:", "subject:")) {
            return false;
        }
        return containsAny(lower,
                "legal claims:",
                "supporting claims:",
                "settlement proposal",
                "this section contains",
                "this section includes",
                "prepared for rakez",
                "final reflection",
                "executive summary",
                "chronology of events",
                "forensic correction & addendum",
                "please ensure this judgment",
                "fraud docket",
                "recused protection-order case file",
                "i am copying mr.",
                "official record");
    }

    private static boolean looksLikeSyntheticForensicSummary(String lower) {
        if (lower == null || lower.trim().isEmpty()) {
            return false;
        }
        int matches = 0;
        String[] markers = new String[]{
                "executive forensic conclusion",
                "triple verification result",
                "findings by",
                "contradiction engine",
                "nine-brain style analysis",
                "financial reconciliation",
                "fraud confirmed",
                "very_high",
                "very high",
                "this is not a judicial ruling",
                "structured evidentiary assessment",
                "confidence:"
        };
        for (String marker : markers) {
            if (lower.contains(marker) && ++matches >= 3) {
                return true;
            }
        }
        return false;
    }

    private static List<String> normalizeLiabilities(List<String> liabilities) {
        List<String> normalized = new ArrayList<>();
        if (liabilities == null) {
            return normalized;
        }
        for (String liability : liabilities) {
            String label = normalizeLiabilityLabel(liability);
            if (!label.isEmpty() && !normalized.contains(label)) {
                normalized.add(label);
            }
        }
        return normalized;
    }

    private static String normalizeLiabilityLabel(String label) {
        if (label == null) {
            return "";
        }
        String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if ("financial irregularities".equals(lower) || "financial irregularity signals".equals(lower)) {
            return "Financial irregularity signals";
        }
        if ("evasion/gaslighting indicators".equals(lower) || "evasion / gaslighting indicators".equals(lower)) {
            return "Evasion/Gaslighting indicators";
        }
        if ("shareholder oppression".equals(lower)) {
            return "Shareholder Oppression";
        }
        if ("breach of fiduciary duty".equals(lower)) {
            return "Breach of Fiduciary Duty";
        }
        if ("legal subject flags present".equals(lower)) {
            return "Legal subject flags present";
        }
        return trimmed;
    }

    private static int distinctPageCount(List<OcrTextBlock> blocks) {
        Set<Integer> pages = new LinkedHashSet<>();
        for (OcrTextBlock block : blocks) {
            if (block == null || block.confidence <= 0f) {
                continue;
            }
            pages.add(block.pageIndex);
        }
        return pages.size();
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String readAll(File f) throws Exception {
        byte[] bytes;
        try (FileInputStream fis = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            bytes = bos.toByteArray();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void logPhase(String phase, long startedAt, String details) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - startedAt);
        if (details == null || details.trim().isEmpty()) {
            Log.d(TAG, phase + " took " + elapsed + " ms");
            return;
        }
        Log.d(TAG, phase + " took " + elapsed + " ms | " + details);
    }

    private static int safeLength(JSONArray array) {
        return array == null ? 0 : array.length();
    }

    private static List<String> toList(JSONArray arr, List<String> fallback) {
        if (arr == null) return fallback;
        List<String> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.optString(i));
        }
        return list.isEmpty() ? fallback : list;
    }
}
