package com.verum.legal;

import android.content.Context;
import android.content.res.AssetManager;

import com.verum.omnis.core.AnalysisEngine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LegalGrounding {

    private static final String ROOT = "legal_packs";
    private static final int MAX_FINDINGS = 6;
    private static final int MAX_DOCS = 8;
    private final Store store;

    public LegalGrounding(Context context) {
        this(new AssetStore(context.getApplicationContext().getAssets()));
    }

    public LegalGrounding(File rootDir) {
        this(new FileStore(rootDir));
    }

    LegalGrounding(Store store) {
        this.store = store;
    }

    public JurisdictionPack loadJurisdictionPacks(String jurisdiction) throws Exception {
        JSONObject rules = readObject("jurisdiction_rules.json").getJSONObject("jurisdictions");
        String code = normalizeJurisdiction(jurisdiction);
        JSONObject descriptor = rules.optJSONObject(code);
        if (descriptor == null && rules.has("ALIASES")) {
            String alias = rules.getJSONObject("ALIASES").optString(code, "ZAF");
            descriptor = rules.optJSONObject(alias);
            code = alias;
        }
        if (descriptor == null) {
            descriptor = rules.getJSONObject("ZAF");
            code = "ZAF";
        }

        JSONArray composite = descriptor.optJSONArray("composite");
        JurisdictionPack pack = new JurisdictionPack(code, descriptor.optString("name", readableName(code)));
        if (composite != null && composite.length() > 0) {
            for (int i = 0; i < composite.length(); i++) {
                pack.merge(loadJurisdictionPacks(composite.optString(i)));
            }
        } else {
            pack.statutes.addAll(loadItems(descriptor.optString("statutes"), "statutes"));
            pack.offenceElements.addAll(loadItems(descriptor.optString("offence_elements"), "offence_elements"));
            pack.proceduralRules.addAll(loadItems(descriptor.optString("procedural_rules"), "procedural_rules"));
            pack.institutions.addAll(loadItems(descriptor.optString("institutions"), "institutions"));
            pack.precedents.addAll(loadItems(descriptor.optString("precedent_summaries"), "precedent_summaries"));
        }
        pack.offenceFrameworks.addAll(loadGlobalFrameworks());
        pack.playbooks.addAll(loadGlobalPlaybooks(code));
        pack.sortAndDedupe();
        return pack;
    }

    public GroundingData extractGroundingData(AnalysisEngine.ForensicReport report) {
        GroundingData data = new GroundingData();
        data.caseId = blank(report.caseId, "case-unknown");
        data.evidenceHash = blank(report.evidenceHash, "hash-unavailable");
        data.jurisdiction = normalizeJurisdiction(report.jurisdiction);
        data.jurisdictionName = blank(report.jurisdictionName, readableName(data.jurisdiction));
        data.summary = blank(report.summary, "No sealed summary supplied.");
        data.legalReferences = dedupe(report.legalReferences);
        data.topLiabilities = dedupe(report.topLiabilities);
        data.certifiedFindings = findings(report.certifiedFindings);
        data.diagnostics = diagnostics(report.diagnostics);
        data.guardianApproved = report.guardianDecision != null && report.guardianDecision.optBoolean("approved", false);
        data.guardianReason = report.guardianDecision != null
                ? blank(report.guardianDecision.optString("reason", ""), "Guardian review details unavailable.")
                : "Guardian review details unavailable.";
        return data;
    }

    public List<String> buildRetrievalQueries(GroundingData data) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(data.jurisdictionName);
        queries.addAll(data.topLiabilities);
        queries.addAll(data.legalReferences);
        for (Finding finding : data.certifiedFindings) {
            if (queries.size() >= 12) {
                break;
            }
            queries.add(finding.summary);
        }
        return new ArrayList<>(queries);
    }

    public List<RetrievedDocument> retrieveDocuments(GroundingData data, JurisdictionPack pack) {
        List<String> queries = buildRetrievalQueries(data);
        List<RetrievedDocument> docs = new ArrayList<>();
        for (RankedDocument item : pack.all()) {
            int score = item.rank;
            boolean matched = item.rank >= 95;
            String haystack = (item.title + " " + item.text + " " + String.join(" ", item.tags)).toLowerCase(Locale.ROOT);
            for (String liability : data.topLiabilities) {
                if (matches(haystack, liability)) {
                    score += 40;
                    matched = true;
                }
            }
            for (String ref : data.legalReferences) {
                if (matches(haystack, ref)) {
                    score += 35;
                    matched = true;
                }
            }
            for (String query : queries) {
                if (matches(haystack, query)) {
                    score += 12;
                    matched = true;
                }
            }
            if (matched) {
                docs.add(new RetrievedDocument(item, score));
            }
        }
        docs.sort(Comparator.comparingInt((RetrievedDocument it) -> it.score).reversed()
                .thenComparing(Comparator.comparingInt((RetrievedDocument it) -> it.rank).reversed())
                .thenComparing(it -> it.title));
        return docs.size() > MAX_DOCS ? new ArrayList<>(docs.subList(0, MAX_DOCS)) : docs;
    }

    public String buildPrompt(GroundingData data, List<RetrievedDocument> docs) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Gemma, the Verum Omnis legal secretary layer.\n");
        sb.append("Use sealed findings and retrieved legal materials only.\n");
        sb.append("Do not inspect raw evidence. Do not invent facts or law. Do not override the forensic engine.\n\n");
        sb.append("CASE\n");
        sb.append("Case ID: ").append(data.caseId).append('\n');
        sb.append("Evidence hash: ").append(data.evidenceHash).append('\n');
        sb.append("Jurisdiction: ").append(data.jurisdictionName).append(" [").append(data.jurisdiction).append("]\n");
        sb.append("Guardian approval: ").append(data.guardianApproved ? "Approved" : "Denied").append('\n');
        sb.append("Guardian reason: ").append(data.guardianReason).append('\n');
        sb.append("Summary: ").append(data.summary).append("\n\n");
        sb.append("OFFENCE FRAMEWORKS\n");
        appendList(sb, data.topLiabilities, "No strong offence framework asserted yet.");
        sb.append("\nLEGAL REFERENCES\n");
        appendList(sb, data.legalReferences, "No legal references supplied by the engine.");
        sb.append("\nCERTIFIED FINDINGS\n");
        if (data.certifiedFindings.isEmpty()) {
            sb.append("- No certified findings supplied.\n");
        } else {
            for (Finding finding : data.certifiedFindings) {
                sb.append("- ").append(finding.summary);
                if (!finding.anchor.isEmpty()) {
                    sb.append(" [").append(finding.anchor).append("]");
                }
                if (!finding.excerpt.isEmpty()) {
                    sb.append(": ").append(finding.excerpt);
                }
                sb.append('\n');
            }
        }
        sb.append("\nDIAGNOSTICS\n");
        appendList(sb, data.diagnostics, "No diagnostic summary available.");
        sb.append("\nRETRIEVED LEGAL MATERIALS\n");
        if (docs.isEmpty()) {
            sb.append("- No jurisdiction-pack documents matched.\n");
        } else {
            for (RetrievedDocument doc : docs) {
                sb.append("- ").append(doc.title).append(" [").append(doc.category).append(" | ")
                        .append(doc.source).append(" | rank ").append(doc.rank).append("]\n");
                sb.append("  ").append(doc.text).append('\n');
            }
        }
        sb.append("\nRESPONSE CONTRACT\n");
        sb.append("1. Lead with the strongest likely offence and contradiction.\n");
        sb.append("2. Give the forum-specific next step.\n");
        sb.append("3. Separate grounded points from gaps.\n");
        sb.append("4. Keep the answer compact, direct, and plain-English.\n");
        return sb.toString();
    }

    public PromptPackage buildPromptPackage(AnalysisEngine.ForensicReport report) throws Exception {
        GroundingData data = extractGroundingData(report);
        JurisdictionPack pack = loadJurisdictionPacks(data.jurisdiction);
        List<String> queries = buildRetrievalQueries(data);
        List<RetrievedDocument> docs = retrieveDocuments(data, pack);
        return new PromptPackage(pack, data, queries, docs, buildPrompt(data, docs));
    }

    public JSONObject buildBrainSevenContext(AnalysisEngine.ForensicReport report) throws Exception {
        GroundingData data = extractGroundingData(report);
        JurisdictionPack pack = loadJurisdictionPacks(data.jurisdiction);
        List<String> queries = buildRetrievalQueries(data);
        List<RetrievedDocument> docs = retrieveDocuments(data, pack);

        List<RetrievedDocument> frameworks = filterDocs(docs, "offence_frameworks", "offence_elements");
        List<RetrievedDocument> statutes = filterDocs(docs, "statutes");
        List<RetrievedDocument> procedures = filterDocs(docs, "procedural_rules");
        List<RetrievedDocument> institutions = filterDocs(docs, "institutions", "institution_playbooks");
        List<RetrievedDocument> precedents = filterDocs(docs, "precedent_summaries");

        JSONObject root = new JSONObject();
        root.put("jurisdictionCode", pack.code);
        root.put("jurisdictionName", pack.name);
        root.put("sourceQueries", new JSONArray(queries));
        root.put("legalReferences", new JSONArray(data.legalReferences));
        root.put("topLiabilities", new JSONArray(data.topLiabilities));
        root.put("criticalLegalSubjects", extractCriticalSubjects(report));
        root.put("matchedOffenceFrameworks", documentsToJson(frameworks, 4));
        root.put("matchedStatutes", documentsToJson(statutes, 4));
        root.put("matchedProceduralRules", documentsToJson(procedures, 4));
        root.put("matchedInstitutions", documentsToJson(institutions, 4));
        root.put("matchedPrecedents", documentsToJson(precedents, 3));
        root.put("authorities", new JSONArray(collectTitles(institutions, 4)));
        root.put("recommendedActions", buildRecommendedActions(institutions, procedures));
        root.put("summary", buildBrainSevenSummary(data, frameworks, institutions, procedures));
        return root;
    }

    private List<RankedDocument> loadGlobalFrameworks() throws Exception {
        JSONArray items = readObject("offence_frameworks.json").optJSONArray("frameworks");
        List<RankedDocument> docs = new ArrayList<>();
        if (items == null) {
            return docs;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            docs.add(new RankedDocument(
                    item.optString("id", "framework-" + i),
                    item.optString("title", item.optString("name", "Offence framework")),
                    "Elements: " + join(item.optJSONArray("elements")) + ". Defences: " + join(item.optJSONArray("defences"))
                            + ". Remedies: " + join(item.optJSONArray("remedies")),
                    item.optString("source", "Gemma offence frameworks"),
                    item.optInt("rank", 88),
                    "offence_frameworks",
                    list(item.optJSONArray("tags"))
            ));
        }
        return docs;
    }

    private List<RankedDocument> loadGlobalPlaybooks(String jurisdictionCode) throws Exception {
        JSONArray items = readObject("institution_playbooks.json").optJSONArray("playbooks");
        List<RankedDocument> docs = new ArrayList<>();
        if (items == null) {
            return docs;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (!playbookMatchesJurisdiction(item, jurisdictionCode)) {
                continue;
            }
            docs.add(new RankedDocument(
                    item.optString("id", "playbook-" + i),
                    item.optString("title", item.optString("institution", "Institution playbook")),
                    "Steps: " + join(item.optJSONArray("filing_steps")) + ". Required documents: "
                            + join(item.optJSONArray("required_documents")) + ". Timeline: "
                            + item.optString("typical_timeline", "Not specified"),
                    item.optString("source", "Gemma institution playbooks"),
                    item.optInt("rank", 84),
                    "institution_playbooks",
                    list(item.optJSONArray("tags"))
            ));
        }
        return docs;
    }

    private boolean playbookMatchesJurisdiction(JSONObject item, String jurisdictionCode) {
        if (item == null) {
            return false;
        }
        String normalized = normalizeJurisdiction(jurisdictionCode);
        if (normalized.isEmpty() || "MULTI".equals(normalized)) {
            return true;
        }
        JSONArray tags = item.optJSONArray("tags");
        if (tags == null || tags.length() == 0) {
            return true;
        }
        boolean hasJurisdictionTag = false;
        for (int i = 0; i < tags.length(); i++) {
            String tag = tags.optString(i, "").trim();
            if (tag.isEmpty()) {
                continue;
            }
            String upper = tag.toUpperCase(Locale.ROOT);
            if ("MULTI-JURISDICTION".equals(upper) || "HANDOFF".equals(upper) || "COUNSEL".equals(upper)) {
                return true;
            }
            if ("UAE".equals(upper) || "ZAF".equals(upper) || "MULTI".equals(upper)) {
                hasJurisdictionTag = true;
                if (upper.equals(normalized)) {
                    return true;
                }
            }
        }
        return !hasJurisdictionTag;
    }

    private List<RankedDocument> loadItems(String path, String category) throws Exception {
        if (path == null || path.trim().isEmpty()) {
            return Collections.emptyList();
        }
        JSONArray items = readObject(path).optJSONArray("items");
        List<RankedDocument> docs = new ArrayList<>();
        if (items == null) {
            return docs;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            docs.add(new RankedDocument(
                    item.optString("id", category + "-" + i),
                    item.optString("title", "Untitled"),
                    item.optString("text", "").trim(),
                    item.optString("source", "Unknown source"),
                    item.optInt("rank", 50),
                    category,
                    list(item.optJSONArray("tags"))
            ));
        }
        return docs;
    }

    private JSONArray extractCriticalSubjects(AnalysisEngine.ForensicReport report) {
        JSONArray out = new JSONArray();
        if (report == null || report.constitutionalExtraction == null) {
            return out;
        }
        JSONArray subjects = report.constitutionalExtraction.optJSONArray("criticalLegalSubjects");
        if (subjects == null) {
            return out;
        }
        for (int i = 0; i < subjects.length(); i++) {
            JSONObject item = subjects.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String subject = blank(
                    item.optString("subject"),
                    blank(item.optString("type"), item.optString("summary"))
            );
            if (!subject.isEmpty()) {
                out.put(subject);
            }
        }
        return out;
    }

    private List<RetrievedDocument> filterDocs(List<RetrievedDocument> docs, String... categories) {
        LinkedHashSet<String> wanted = new LinkedHashSet<>();
        Collections.addAll(wanted, categories);
        List<RetrievedDocument> out = new ArrayList<>();
        for (RetrievedDocument doc : docs) {
            if (wanted.contains(doc.category)) {
                out.add(doc);
            }
        }
        return out;
    }

    private JSONArray documentsToJson(List<RetrievedDocument> docs, int limit) throws JSONException {
        JSONArray array = new JSONArray();
        for (int i = 0; i < docs.size() && i < limit; i++) {
            RetrievedDocument doc = docs.get(i);
            JSONObject item = new JSONObject();
            item.put("id", doc.id);
            item.put("title", doc.title);
            item.put("category", doc.category);
            item.put("source", doc.source);
            item.put("rank", doc.rank);
            item.put("score", doc.score);
            item.put("text", trim(doc.text));
            array.put(item);
        }
        return array;
    }

    private List<String> collectTitles(List<RetrievedDocument> docs, int limit) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (int i = 0; i < docs.size() && out.size() < limit; i++) {
            String title = blank(docs.get(i).title, "");
            if (!title.isEmpty()) {
                out.add(title);
            }
        }
        return new ArrayList<>(out);
    }

    private JSONArray buildRecommendedActions(List<RetrievedDocument> institutions, List<RetrievedDocument> procedures) {
        JSONArray out = new JSONArray();
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (RetrievedDocument doc : institutions) {
            String summary = blank(doc.title, "Institution track");
            String text = trim(doc.text);
            if (!text.isEmpty()) {
                actions.add(summary + ": " + text);
            } else {
                actions.add(summary);
            }
            if (actions.size() >= 4) {
                break;
            }
        }
        for (RetrievedDocument doc : procedures) {
            if (actions.size() >= 6) {
                break;
            }
            String summary = blank(doc.title, "Procedural track");
            String text = trim(doc.text);
            if (!text.isEmpty()) {
                actions.add(summary + ": " + text);
            } else {
                actions.add(summary);
            }
        }
        for (String action : actions) {
            out.put(action);
        }
        return out;
    }

    private String buildBrainSevenSummary(
            GroundingData data,
            List<RetrievedDocument> frameworks,
            List<RetrievedDocument> institutions,
            List<RetrievedDocument> procedures
    ) {
        String leadFramework = frameworks.isEmpty() ? "no dominant offence framework" : frameworks.get(0).title;
        String leadInstitution = institutions.isEmpty() ? "no institution track resolved yet" : institutions.get(0).title;
        String leadProcedure = procedures.isEmpty() ? "no procedure track resolved yet" : procedures.get(0).title;
        return "B7 mapped " + data.jurisdictionName
                + " legal pathways with "
                + leadFramework
                + " as the leading offence framework, "
                + leadInstitution
                + " as the lead institution track, and "
                + leadProcedure
                + " as the next procedural path.";
    }

    private JSONObject readObject(String path) throws Exception {
        return new JSONObject(store.read(path));
    }

    private List<Finding> findings(JSONArray array) {
        List<Finding> findings = new ArrayList<>();
        if (array == null) {
            return findings;
        }
        for (int i = 0; i < array.length() && findings.size() < MAX_FINDINGS; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            findings.add(new Finding(
                    blank(item.optString("summary"), blank(item.optString("title"), "Certified finding")),
                    trim(blank(item.optString("excerpt"), item.optString("description"))),
                    anchor(item)
            ));
        }
        return findings;
    }

    private List<String> diagnostics(JSONObject object) {
        List<String> out = new ArrayList<>();
        if (object == null) {
            return out;
        }
        addDiagnostic(out, object, "processingStatus", "Processing status");
        addDiagnostic(out, object, "verifiedContradictionCount", "Verified contradictions");
        addDiagnostic(out, object, "candidateContradictionCount", "Candidate contradictions");
        addDiagnostic(out, object, "anchoredFindingCount", "Anchored findings");
        addDiagnostic(out, object, "namedPartyCount", "Named parties");
        return out;
    }

    private void addDiagnostic(List<String> out, JSONObject object, String key, String label) {
        if (object.has(key) && !object.isNull(key)) {
            out.add(label + ": " + object.opt(key));
        }
    }

    private String anchor(JSONObject item) {
        List<String> parts = new ArrayList<>();
        if (item.has("page")) {
            parts.add("page " + item.optInt("page"));
        }
        JSONObject anchor = item.optJSONObject("anchor");
        if (anchor != null) {
            if (anchor.has("page")) {
                parts.add("page " + anchor.optInt("page"));
            }
            String exhibit = anchor.optString("exhibitId", "").trim();
            if (!exhibit.isEmpty()) {
                parts.add("exhibit " + exhibit);
            }
        }
        return String.join(", ", new LinkedHashSet<>(parts));
    }

    private boolean matches(String haystack, String needle) {
        String normalized = needle == null ? "" : needle.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        if (normalized.isEmpty()) {
            return false;
        }
        for (String token : normalized.split(" ")) {
            if (token.length() >= 4 && haystack.contains(token)) {
                return true;
            }
        }
        return haystack.contains(normalized);
    }

    private void appendList(StringBuilder sb, List<String> values, String fallback) {
        if (values.isEmpty()) {
            sb.append("- ").append(fallback).append('\n');
            return;
        }
        for (String value : values) {
            sb.append("- ").append(value).append('\n');
        }
    }

    private List<String> dedupe(String[] values) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    out.add(value.trim());
                }
            }
        }
        return new ArrayList<>(out);
    }

    private List<String> list(JSONArray array) {
        List<String> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    private String join(JSONArray array) {
        List<String> values = list(array);
        return values.isEmpty() ? "None specified" : String.join("; ", values);
    }

    private String trim(String value) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() > 280 ? text.substring(0, 280).trim() + "..." : text;
    }

    private String blank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String normalizeJurisdiction(String jurisdiction) {
        if (jurisdiction == null || jurisdiction.trim().isEmpty()) {
            return "ZAF";
        }
        String normalized = jurisdiction.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("SOUTH_AFRICA".equals(normalized) || "SA".equals(normalized)) {
            return "ZAF";
        }
        if ("MULTI_JURISDICTION".equals(normalized) || "MULTI".equals(normalized)) {
            return "MULTI";
        }
        return normalized;
    }

    private String readableName(String code) {
        switch (normalizeJurisdiction(code)) {
            case "UAE":
                return "United Arab Emirates";
            case "MULTI":
                return "Multi-jurisdiction (UAE and South Africa)";
            case "ZAF":
            default:
                return "South Africa";
        }
    }

    interface Store {
        String read(String relativePath) throws Exception;
    }

    static final class AssetStore implements Store {
        private final AssetManager assets;
        AssetStore(AssetManager assets) { this.assets = assets; }
        @Override
        public String read(String relativePath) throws Exception {
            try (InputStream in = assets.open(ROOT + "/" + relativePath);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        }
    }

    static final class FileStore implements Store {
        private final File root;
        FileStore(File root) { this.root = root; }
        @Override
        public String read(String relativePath) throws Exception {
            File file = new File(root, relativePath.replace("/", File.separator));
            try (InputStream in = new FileInputStream(file);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        }
    }

    static final class RankedDocument {
        final String id;
        final String title;
        final String text;
        final String source;
        final int rank;
        final String category;
        final List<String> tags;
        RankedDocument(String id, String title, String text, String source, int rank, String category, List<String> tags) {
            this.id = id;
            this.title = title;
            this.text = text;
            this.source = source;
            this.rank = rank;
            this.category = category;
            this.tags = tags;
        }
    }

    public static final class RetrievedDocument {
        public final String id;
        public final String title;
        public final String text;
        public final String source;
        public final int rank;
        public final String category;
        public final int score;
        RetrievedDocument(RankedDocument item, int score) {
            this.id = item.id;
            this.title = item.title;
            this.text = item.text;
            this.source = item.source;
            this.rank = item.rank;
            this.category = item.category;
            this.score = score;
        }
    }

    public static final class Finding {
        public final String summary;
        public final String excerpt;
        public final String anchor;
        Finding(String summary, String excerpt, String anchor) {
            this.summary = summary;
            this.excerpt = excerpt;
            this.anchor = anchor;
        }
    }

    public static final class GroundingData {
        public String caseId;
        public String evidenceHash;
        public String jurisdiction;
        public String jurisdictionName;
        public String summary;
        public boolean guardianApproved;
        public String guardianReason;
        public List<String> legalReferences = new ArrayList<>();
        public List<String> topLiabilities = new ArrayList<>();
        public List<Finding> certifiedFindings = new ArrayList<>();
        public List<String> diagnostics = new ArrayList<>();
    }

    public static final class JurisdictionPack {
        public final String code;
        public final String name;
        public final List<RankedDocument> statutes = new ArrayList<>();
        public final List<RankedDocument> offenceElements = new ArrayList<>();
        public final List<RankedDocument> proceduralRules = new ArrayList<>();
        public final List<RankedDocument> institutions = new ArrayList<>();
        public final List<RankedDocument> precedents = new ArrayList<>();
        public final List<RankedDocument> offenceFrameworks = new ArrayList<>();
        public final List<RankedDocument> playbooks = new ArrayList<>();
        JurisdictionPack(String code, String name) {
            this.code = code;
            this.name = name;
        }
        void merge(JurisdictionPack other) {
            statutes.addAll(other.statutes);
            offenceElements.addAll(other.offenceElements);
            proceduralRules.addAll(other.proceduralRules);
            institutions.addAll(other.institutions);
            precedents.addAll(other.precedents);
            offenceFrameworks.addAll(other.offenceFrameworks);
            playbooks.addAll(other.playbooks);
        }
        void sortAndDedupe() {
            dedupe(statutes);
            dedupe(offenceElements);
            dedupe(proceduralRules);
            dedupe(institutions);
            dedupe(precedents);
            dedupe(offenceFrameworks);
            dedupe(playbooks);
        }
        private void dedupe(List<RankedDocument> docs) {
            docs.sort(Comparator.comparingInt((RankedDocument it) -> it.rank).reversed().thenComparing(it -> it.id));
            List<RankedDocument> unique = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (RankedDocument doc : docs) {
                if (seen.add(doc.id)) {
                    unique.add(doc);
                }
            }
            docs.clear();
            docs.addAll(unique);
        }
        List<RankedDocument> all() {
            List<RankedDocument> docs = new ArrayList<>();
            docs.addAll(statutes);
            docs.addAll(offenceElements);
            docs.addAll(proceduralRules);
            docs.addAll(institutions);
            docs.addAll(precedents);
            docs.addAll(offenceFrameworks);
            docs.addAll(playbooks);
            return docs;
        }
    }

    public static final class PromptPackage {
        public final JurisdictionPack pack;
        public final GroundingData data;
        public final List<String> queries;
        public final List<RetrievedDocument> docs;
        public final String prompt;
        PromptPackage(JurisdictionPack pack, GroundingData data, List<String> queries, List<RetrievedDocument> docs, String prompt) {
            this.pack = pack;
            this.data = data;
            this.queries = queries;
            this.docs = docs;
            this.prompt = prompt;
        }
    }
}
