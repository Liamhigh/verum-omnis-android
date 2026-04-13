package com.verum.omnis.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class PoliceReadyReportBuilder {

    private static final String FINAL_BOUNDARY_LINE =
            "This is a forensic conclusion, not a judicial verdict. This police-ready constitutional report states what the sealed record shows, where the evidence sits, which laws appear engaged, who should be charged or formally investigated, and what the complainant must do next.";
    private static final Pattern LEADING_PAGE_PATTERN = Pattern.compile("^On page \\d+,\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_DATE_PATTERN = Pattern.compile("^On (?:or about )?[0-9]{1,2}\\s+[A-Za-z]+(?:\\s+[0-9]{4})?,\\s*", Pattern.CASE_INSENSITIVE);

    private PoliceReadyReportBuilder() {}

    public static String render(
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            String sourceFileName,
            String intakeDateTime
    ) {
        if (report == null) {
            return "";
        }
        ForensicReportAssembler.Assembly safeAssembly = assembled != null
                ? assembled
                : ForensicReportAssembler.assemble(report);
        JSONObject conclusion = report.forensicConclusion != null
                ? report.forensicConclusion
                : ForensicConclusionEngine.buildJson(report);
        JSONObject truthFrame = TruthInCodeEngine.buildTruthFrame(report, safeAssembly, false);
        AnchorBoundNarrativeBuilder.Narrative narrative = AnchorBoundNarrativeBuilder.build(conclusion, truthFrame);

        StringBuilder sb = new StringBuilder();
        sb.append("VERUM OMNIS - POLICE READY CONSTITUTIONAL FORENSIC REPORT\n\n");
        appendCaseMetadata(sb, report, safeAssembly, sourceFileName, intakeDateTime);
        appendExecutivePoliceSummary(sb, report, safeAssembly, conclusion, narrative);
        appendWhatTheRecordShows(sb, safeAssembly);
        appendKeyEvidenceTable(sb, safeAssembly);
        appendLawBrokenSection(sb, report, safeAssembly, conclusion);
        appendRecommendedCharges(sb, report, safeAssembly, conclusion);
        appendPersonsSection(sb, report, safeAssembly, conclusion);
        appendPoliceEvidenceIndex(sb, safeAssembly, conclusion);
        appendContradictionAnalysis(sb, report, safeAssembly, conclusion);
        appendWhatYouMustDoNow(sb, safeAssembly);
        appendRequestedPoliceAction(sb, report, safeAssembly, conclusion);
        appendBoundariesAndLimitations(sb, report, safeAssembly, conclusion, narrative);
        sb.append(FINAL_BOUNDARY_LINE);
        return sb.toString().trim();
    }

    private static void appendCaseMetadata(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            String sourceFileName,
            String intakeDateTime
    ) {
        sb.append("SECTION 1 — CASE METADATA\n");
        appendLine(sb, "Source file", safeValue(sourceFileName));
        appendLine(sb, "Case ID", safeValue(report.caseId));
        appendLine(sb, "Jurisdiction", safeValue(report.jurisdictionName) + " (" + safeValue(report.jurisdiction) + ")");
        appendLine(sb, "Evidence SHA-512", safeValue(report.evidenceHash));
        appendLine(sb, "Intake date/time", safeValue(intakeDateTime));
        appendLine(sb, "Constitutional version", "v5.2.7");
        appendLine(sb, "Engine version", safeValue(report.engineVersion));
        appendLine(sb, "Deterministic run ID", safeValue(report.deterministicRunId));
        appendLine(sb, "Processing status", normalizeProcessingStatus(report, assembled));
        appendLine(sb, "Triple verification result", tripleVerificationStatus(report));
        appendLine(sb, "Guardian-approved certified findings count", String.valueOf(assembled.guardianApprovedCertifiedFindingCount));
        sb.append("\n");
    }

    private static void appendExecutivePoliceSummary(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            JSONObject conclusion,
            AnchorBoundNarrativeBuilder.Narrative narrative
    ) {
        sb.append("SECTION 2 — EXECUTIVE POLICE SUMMARY\n");
        String primaryActor = primaryActor(conclusion);
        String harmedParty = harmedParty(conclusion);
        String urgent = assembled.guardianApprovedCertifiedFindingCount > 0
                ? "Urgent intervention is recommended because the sealed record already carries a stable harm-and-pressure pattern."
                : "Urgent intervention should follow only after the next evidence pass resolves the current coverage gaps.";
        String evidenceNeed = assembled.verifiedContradictionCount == 0 || assembled.candidateContradictionCount > 0
                ? "More evidence is still needed before the engine can publish a final guilt verdict, but the current record is already strong enough to support charging recommendations and formal investigation steps."
                : "The contradiction gate has matured enough for the current pass to support stronger adjudicative publication.";
        List<String> summaryLines = new ArrayList<>();
        summaryLines.add(firstNonEmpty(
                buildCorePatternLine(assembled, conclusion),
                cleanSummarySentence(narrative.summary),
                "This case concerns a repeated record pattern involving unsigned or unreturned papers, continued reliance, and later pressure or displacement steps."
        ));
        if (!primaryActor.isEmpty()) {
            summaryLines.add(primaryActor + " is the primary implicated actor in this sealed pass.");
        }
        if (!harmedParty.isEmpty()) {
            summaryLines.add(harmedParty + " is the clearest harmed-side anchor carried in the same material.");
        }
        summaryLines.add(urgent);
        summaryLines.add(evidenceNeed);
        for (String line : summaryLines) {
            sb.append("- ").append(ensureSentence(line)).append("\n");
        }
        sb.append("\n");
    }

    private static void appendWhatTheRecordShows(
            StringBuilder sb,
            ForensicReportAssembler.Assembly assembled
    ) {
        sb.append("SECTION 3 — WHAT THE RECORD SHOWS\n");
        List<ForensicReportAssembler.ChronologyEvent> chronology = assembled.chronology != null
                ? assembled.chronology
                : Collections.emptyList();
        int count = 0;
        for (ForensicReportAssembler.ChronologyEvent event : chronology) {
            if (event == null || trimToEmpty(event.summary).isEmpty() || event.evidencePages == null || event.evidencePages.isEmpty()) {
                continue;
            }
            String cleanedSummary = cleanSummarySentence(event.summary);
            if (cleanedSummary.isEmpty() || isWeakChronologySummary(cleanedSummary)) {
                continue;
            }
            String actorText = firstNonEmpty(cleanActorText(event.actors), "the record");
            cleanedSummary = stripLeadingActorReference(cleanedSummary, actorText);
            String dateLabel = cleanDateLabel(event.dateLabel);
            String prefix = dateLabel.isEmpty()
                    ? actorText + " is linked in the sealed record to "
                    : "On or about " + dateLabel + ", " + actorText + " is linked in the sealed record to ";
            sb.append(prefix)
                    .append(stripTerminalPunctuation(cleanedSummary))
                    .append(". This appears in pages ")
                    .append(joinPages(event.evidencePages))
                    .append(".\n\n");
            count++;
            if (count >= 8) {
                break;
            }
        }
        if (count == 0) {
            appendChronologyFallback(sb, assembled);
        }
    }

    private static void appendKeyEvidenceTable(
            StringBuilder sb,
            ForensicReportAssembler.Assembly assembled
    ) {
        sb.append("SECTION 4 — KEY EVIDENCE TABLE\n");
        List<ForensicReportAssembler.IssueCard> issues = assembled.issueGroups != null
                ? assembled.issueGroups
                : Collections.emptyList();
        int itemNo = 1;
        for (ForensicReportAssembler.IssueCard issue : issues) {
            if (issue == null || issue.evidencePages == null || issue.evidencePages.isEmpty()) {
                continue;
            }
            sb.append("Item No.: ").append(itemNo++).append("\n");
            appendLine(sb, "Evidence Description", trimToEmpty(issue.title));
            appendLine(sb, "Page No(s).", joinPages(issue.evidencePages));
            appendLine(sb, "Actor(s) Linked", firstNonEmpty(cleanActorText(issue.actors), "Unresolved actor"));
            appendLine(sb, "What It Proves", cleanSummarySentence(issue.summary));
            appendLine(sb, "Status", firstNonEmpty(trimToEmpty(issue.confidence), "CERTIFIED"));
            appendLine(sb, "Recommended Investigative Use", recommendedUse(issue));
            sb.append("\n");
            if (itemNo > 6) {
                break;
            }
        }
        if (itemNo == 1) {
            sb.append("No stronger evidence rows were available beyond the sealed finding summary in this pass.\n\n");
        }
    }

    private static void appendLawBrokenSection(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            JSONObject conclusion
    ) {
        sb.append("SECTION 5 — LAW BROKEN / LEGAL FRAMEWORK\n");
        List<LawRow> rows = deriveLawRows(report, assembled, conclusion);
        if (rows.isEmpty()) {
            sb.append("No legal framework row was mature enough to publish without bluffing section precision.\n\n");
            return;
        }
        for (LawRow row : rows) {
            sb.append("- Legal category: ").append(row.category).append("\n");
            sb.append("  Jurisdiction: ").append(row.jurisdiction).append("\n");
            sb.append("  Statute / legal framework: ").append(row.framework).append("\n");
            sb.append("  Plain-language explanation of the law: ").append(row.explanation).append("\n");
            sb.append("  Exact evidence pages supporting it: ").append(joinPages(row.pages)).append("\n");
            sb.append("  Confidence/status: ").append(row.status).append("\n");
            sb.append("  Notes for police: ").append(row.notes).append("\n\n");
        }
    }

    private static void appendRecommendedCharges(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            JSONObject conclusion
    ) {
        sb.append("SECTION 6 — RECOMMENDED CHARGES FOR DOCKET CONSIDERATION\n");
        List<ChargeRow> charges = deriveCharges(report, assembled, conclusion);
        if (charges.isEmpty()) {
            sb.append("No charge row was strong enough to publish beyond the conduct pattern itself in this pass.\n\n");
            return;
        }
        for (ChargeRow charge : charges) {
            sb.append("- Proposed charge: ").append(charge.charge).append("\n");
            sb.append("  Jurisdiction: ").append(charge.jurisdiction).append("\n");
            sb.append("  Respondent(s) to be investigated/charged: ").append(charge.respondents).append("\n");
            sb.append("  Factual basis: ").append(charge.factualBasis).append("\n");
            sb.append("  Evidence pages: ").append(joinPages(charge.pages)).append("\n");
            sb.append("  Why this charge fits: ").append(charge.reason).append("\n");
            sb.append("  Status: ").append(charge.status).append("\n\n");
        }
    }

    private static void appendPersonsSection(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            JSONObject conclusion
    ) {
        sb.append("SECTION 7 — PERSONS TO BE CHARGED OR FORMALLY INVESTIGATED\n");
        List<PersonRow> people = derivePeople(report, assembled, conclusion);
        if (people.isEmpty()) {
            sb.append("No person row was mature enough to publish in this pass.\n\n");
            return;
        }
        for (PersonRow person : people) {
            sb.append("- Full name / entity name: ").append(person.name).append("\n");
            sb.append("  Role in the record: ").append(person.role).append("\n");
            sb.append("  Why they are implicated: ").append(person.reason).append("\n");
            sb.append("  Exact page numbers: ").append(joinPages(person.pages)).append("\n");
            sb.append("  Recommended status: ").append(person.status).append("\n");
            sb.append("  Short reason in plain language: ").append(person.statusReason).append("\n\n");
        }
    }

    private static void appendPoliceEvidenceIndex(
            StringBuilder sb,
            ForensicReportAssembler.Assembly assembled,
            JSONObject conclusion
    ) {
        sb.append("SECTION 8 — POLICE EVIDENCE PAGE INDEX\n");
        LinkedHashMap<Integer, String> pageMap = new LinkedHashMap<>();
        for (ForensicReportAssembler.IssueCard issue : safeList(assembled.issueGroups)) {
            if (issue == null || issue.evidencePages == null) {
                continue;
            }
            for (Integer page : issue.evidencePages) {
                if (page == null || page <= 0 || pageMap.containsKey(page)) {
                    continue;
                }
                String summary = stripTerminalPunctuation(firstNonEmpty(issue.summary, issue.title));
                if (pageMap.isEmpty()) {
                    summary = "READ THIS PAGE FIRST — " + summary;
                }
                pageMap.put(page, ensureSentence(summary));
                if (pageMap.size() >= 8) {
                    break;
                }
            }
            if (pageMap.size() >= 8) {
                break;
            }
        }
        if (pageMap.isEmpty()) {
            for (Integer page : extractPagesFromConclusion(conclusion, 8)) {
                pageMap.put(page, "READ THIS PAGE FIRST — Anchored page already carried in the current forensic conclusion.");
            }
        }
        for (Map.Entry<Integer, String> entry : pageMap.entrySet()) {
            sb.append("- Page ").append(entry.getKey()).append(" — ").append(entry.getValue()).append("\n");
        }
        sb.append("\n");
    }

    private static void appendContradictionAnalysis(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            JSONObject conclusion
    ) {
        sb.append("SECTION 9 — CONTRADICTION AND OBSTRUCTION ANALYSIS\n");
        sb.append("- Verified contradictions: ")
                .append(assembled.verifiedContradictionCount)
                .append(" (pages ")
                .append(fallbackPageText(conclusion, assembled))
                .append(")\n");
        sb.append("- Candidate contradictions: ")
                .append(assembled.candidateContradictionCount)
                .append(" (pages ")
                .append(fallbackPageText(conclusion, assembled))
                .append(")\n");
        sb.append("- Contradiction posture: ")
                .append(ensureSentence(trimToEmpty(assembled.contradictionPosture)))
                .append(" Pages: ")
                .append(fallbackPageText(conclusion, assembled))
                .append(".\n");
        for (String exposure : extractStringArray(conclusion, "stronglyAllegedExposure", 4)) {
            sb.append("- Exposure or obstruction signal: ")
                    .append(exposure)
                    .append(". Pages: ")
                    .append(fallbackPageText(conclusion, assembled))
                    .append(".\n");
        }
        for (String conduct : extractStringArray(conclusion, "certifiedForensicConduct", 2)) {
            sb.append("- Document execution or conduct issue: ")
                    .append(conduct)
                    .append(". Pages: ")
                    .append(fallbackPageText(conclusion, assembled))
                    .append(".\n");
        }
        sb.append("\n");
    }

    private static void appendWhatYouMustDoNow(
            StringBuilder sb,
            ForensicReportAssembler.Assembly assembled
    ) {
        sb.append("SECTION 10 — WHAT YOU MUST DO NOW\n");
        String topPages = joinStrings(safeList(assembled.readFirstPages));
        sb.append("1. Print the sealed evidence PDF, the police-ready report, and the findings package JSON.\n");
        sb.append("2. Highlight these pages first: ").append(topPages.isEmpty() ? "the top certified anchor pages in this report" : topPages).append(".\n");
        sb.append("3. Take the sealed PDF, the police-ready report, and your statement to SAPS or the Hawks together so the chain of custody stays intact.\n");
        sb.append("4. Tell the officer that the report is sealed, hash-backed, and contradiction-led, and ask that the listed pages be read first.\n");
        sb.append("5. Do not rewrite or paraphrase the evidence when making your statement. Point the officer to the pages and let the record speak.\n");
        sb.append("6. Ask for the docket number immediately, then record the officer name, station, and date on your own copy.\n");
        sb.append("7. Preserve all original files, devices, and messages. Do not alter file names, screenshots, or exports after this point.\n");
        sb.append("8. If attorneys, regulators, or company officers appear in the record, report those tracks separately using the same sealed package.\n\n");
    }

    private static void appendRequestedPoliceAction(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            JSONObject conclusion
    ) {
        sb.append("SECTION 11 — REQUESTED POLICE ACTION\n");
        sb.append("- Open or update the docket using the sealed evidence hash and case ID already carried in this report.\n");
        sb.append("- Take the complainant statement against the attached page index and certified findings.\n");
        sb.append("- Obtain statements from the named actors appearing in the chronology and person-of-interest sections above.\n");
        sb.append("- Preserve electronic evidence and obtain the underlying records tied to the anchored pages.\n");
        sb.append("- Investigate the financial and document-execution flows reflected in the certified conduct pattern.\n");
        if (containsAny(lowerUs(joinStrings(extractStringArray(conclusion, "stronglyAllegedExposure", 6))), "access", "password", "digital")) {
            sb.append("- Refer any supported access-code or digital-interference track to the relevant cybercrime team.\n");
        }
        if (containsAny(lowerUs(joinStrings(Arrays.asList(report.legalReferences != null ? report.legalReferences : new String[0]))), "precca", "attorney", "disciplinary")) {
            sb.append("- Refer the attorney-conduct or PRECCA-related strand to the relevant disciplinary or regulatory body in parallel.\n");
        }
        sb.append("\n");
    }

    private static void appendBoundariesAndLimitations(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            JSONObject conclusion,
            AnchorBoundNarrativeBuilder.Narrative narrative
    ) {
        sb.append("SECTION 12 — BOUNDARIES AND LIMITATIONS\n");
        sb.append("- What this report proves: ").append(ensureSentence(firstNonEmpty(narrative.summary, "The sealed record already supports a stable forensic conduct pattern."))).append("\n");
        sb.append("- What it strongly suggests: ").append(ensureSentence(joinStrings(extractStringArray(conclusion, "stronglyAllegedExposure", 4)))).append("\n");
        sb.append("- What remains candidate only: verified contradictions = ").append(assembled.verifiedContradictionCount)
                .append(", candidate contradictions = ").append(assembled.candidateContradictionCount).append(".\n");
        sb.append("- What still needs more proof: ").append(joinStrings(extractStringArray(conclusion, "proofGaps", 4))).append("\n");
        sb.append("- Media or evidence types not fully engaged in this pass: the report may still rely more heavily on the sealed document bundle than on any later external corroboration not yet added.\n");
        sb.append("- ").append(ensureSentence(firstNonEmpty(conclusion.optString("publicationBoundary", ""), "This is a forensic conclusion, not a judicial verdict."))).append("\n\n");
    }

    private static List<LawRow> deriveLawRows(
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            JSONObject conclusion
    ) {
        List<LawRow> rows = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        String affectedActor = harmedParty(conclusion);
        for (ForensicConclusionEngine.ForensicProposition proposition : propositionObjects(conclusion)) {
            if (isAffectedActor(proposition.actor, affectedActor, conclusion)) {
                continue;
            }
            if (trimToEmpty(proposition.offenceMapping).isEmpty()) {
                continue;
            }
            String category = humanizeCategory(proposition.offenceMapping);
            if (!seen.add(category.toLowerCase(Locale.US))) {
                continue;
            }
            LawRow row = new LawRow();
            row.category = category;
            row.jurisdiction = safeValue(report.jurisdictionName) + " (" + safeValue(report.jurisdiction) + ")";
            row.framework = matchLegalFramework(report, category);
            row.explanation = "The sealed record links the named actor to conduct that maps at framework level to " + category + ".";
            row.pages = mergePages(new ArrayList<Integer>(), proposition.anchorPages);
            row.status = "EVIDENCE-SUPPORTED CANDIDATE";
            row.notes = "Use this row to frame questioning, statement-taking, and record preservation. Do not separate it from the pages listed above.";
            rows.add(row);
            if (rows.size() >= 6) {
                return rows;
            }
        }
        for (String item : extractStringArray(conclusion, "stronglyAllegedExposure", 6)) {
            String category = humanizeCategory(item);
            if (!seen.add(category.toLowerCase(Locale.US))) {
                continue;
            }
            LawRow row = new LawRow();
            row.category = category;
            row.jurisdiction = safeValue(report.jurisdictionName) + " (" + safeValue(report.jurisdiction) + ")";
            row.framework = matchLegalFramework(report, category);
            row.explanation = "The record supports investigation of this legal exposure, but the exact charge section still depends on the final legal pairing step.";
            row.pages = extractPagesFromConclusion(conclusion, 6);
            row.status = "EVIDENCE-SUPPORTED CANDIDATE";
            row.notes = "Framework identified; exact section requires legal confirmation.";
            rows.add(row);
            if (rows.size() >= 6) {
                return rows;
            }
        }
        return rows;
    }

    private static List<ChargeRow> deriveCharges(
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            JSONObject conclusion
    ) {
        List<ChargeRow> rows = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        String jurisdiction = safeValue(report.jurisdictionName) + " (" + safeValue(report.jurisdiction) + ")";
        String primaryActor = primaryActor(conclusion);
        String affectedActor = harmedParty(conclusion);
        for (ForensicConclusionEngine.ForensicProposition proposition : propositionObjects(conclusion)) {
            if (isAffectedActor(proposition.actor, affectedActor, conclusion)) {
                continue;
            }
            if (trimToEmpty(proposition.actor).isEmpty() || proposition.anchorPages.isEmpty()) {
                continue;
            }
            String chargeLabel = !trimToEmpty(proposition.offenceMapping).isEmpty()
                    ? humanizeCategory(proposition.offenceMapping)
                    : "document-execution failure and pressure pattern";
            String key = proposition.actor.toLowerCase(Locale.US) + "|" + chargeLabel.toLowerCase(Locale.US);
            if (!seen.add(key)) {
                continue;
            }
            ChargeRow row = new ChargeRow();
            row.charge = chargeLabel;
            row.jurisdiction = jurisdiction;
            row.respondents = proposition.actor;
            row.factualBasis = proposition.actor + " is linked in the sealed record to " + stripTerminalPunctuation(proposition.conduct) + ".";
            row.pages = proposition.anchorPages;
            row.reason = proposition.offenceMapping.isEmpty()
                    ? "The conduct is already certified and anchored, and it should be placed on the docket in the same factual form."
                    : "The conduct and pages already align strongly enough for charging consideration in this pass.";
            row.status = sameActor(proposition.actor, primaryActor) ? "CERTIFIED" : "EVIDENCE-SUPPORTED CANDIDATE";
            rows.add(row);
            if (rows.size() >= 6) {
                break;
            }
        }
        return rows;
    }

    private static List<PersonRow> derivePeople(
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            JSONObject conclusion
    ) {
        LinkedHashMap<String, PersonRow> rows = new LinkedHashMap<>();
        String primaryActor = primaryActor(conclusion);
        String affectedActor = harmedParty(conclusion);
        JSONArray actorsArray = conclusion != null ? conclusion.optJSONArray("implicatedActors") : null;
        if (actorsArray != null) {
            for (int i = 0; i < actorsArray.length(); i++) {
                JSONObject item = actorsArray.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String actor = ActorNameNormalizer.canonicalizePublicationActor(item.optString("actor", ""));
                if (actor.isEmpty()) {
                    continue;
                }
                PersonRow row = rows.computeIfAbsent(actor, PersonRow::new);
                row.role = roleLabel(item.optString("role", ""));
                row.reason = firstNonEmpty(joinStrings(jsonStringList(item.optJSONArray("basis"), 3)), actor + " remains linked in the current anchored material.");
                row.pages = mergePages(row.pages, jsonIntList(item.optJSONArray("anchorPages"), 10));
                String role = trimToEmpty(item.optString("role", ""));
                if (isAffectedActor(actor, affectedActor, conclusion)) {
                    row.status = "TAKE STATEMENT ONLY";
                    row.statusReason = "This actor is carried as the harmed-side factual anchor and should be treated as a statement source, not an accused person in this pass.";
                } else if ("PRIMARY_IMPLICATED".equalsIgnoreCase(role) || sameActor(actor, primaryActor)) {
                    row.status = "CHARGE";
                    row.statusReason = "Recommended for charging consideration based on anchored evidence already carried in the sealed record.";
                } else if ("SECONDARY_IMPLICATED".equalsIgnoreCase(role)) {
                    row.status = "INVESTIGATE URGENTLY";
                    row.statusReason = "This actor sits close enough to the certified conduct pattern to justify urgent formal investigation.";
                } else if ("AFFECTED_PARTY".equalsIgnoreCase(role)) {
                    row.status = "TAKE STATEMENT ONLY";
                    row.statusReason = "This actor is carried as the harmed-side factual anchor and should be treated as a statement source, not an accused person in this pass.";
                } else {
                    row.status = "INVESTIGATE URGENTLY";
                    row.statusReason = "This actor is materially present in the anchored record and should be questioned against the page set above.";
                }
            }
        }
        for (ForensicReportAssembler.ChronologyEvent event : safeList(assembled.chronology)) {
            if (event == null || event.actors == null || event.evidencePages == null || event.evidencePages.isEmpty()) {
                continue;
            }
            for (String rawActor : event.actors) {
                String actor = ActorNameNormalizer.canonicalizePublicationActor(rawActor);
                if (actor.isEmpty() || rows.containsKey(actor) || containsAny(lowerUs(actor), "client via", "unresolved actor", "unknown")) {
                    continue;
                }
                PersonRow row = new PersonRow(actor);
                row.role = inferProceduralRole(actor);
                row.reason = actor + " is linked in the chronology and correspondence trail carried in the sealed record.";
                row.pages = mergePages(row.pages, event.evidencePages);
                if (isAffectedActor(actor, affectedActor, conclusion)) {
                    row.status = "TAKE STATEMENT ONLY";
                    row.statusReason = "This actor is carried as the harmed-side factual anchor and should be treated as a statement source, not an accused person in this pass.";
                } else {
                    row.status = row.role.contains("intermediary") ? "REGULATORY REFERRAL" : "INVESTIGATE URGENTLY";
                    row.statusReason = row.role.contains("intermediary")
                        ? "The current record supports formal disciplinary or regulatory referral together with police preservation steps."
                        : "The actor appears often enough in anchored events to justify formal questioning.";
                }
                rows.put(actor, row);
            }
        }
        return new ArrayList<>(rows.values());
    }

    private static String inferProceduralRole(String actor) {
        String lower = lowerUs(actor);
        if (containsAny(lower, "tdp legal", "lombaard", "barnard", "attorney")) {
            return "legal intermediary";
        }
        if (containsAny(lower, "liam")) {
            return "escalation actor";
        }
        if (containsAny(lower, "gary")) {
            return "linked actor";
        }
        return "linked actor";
    }

    private static String roleLabel(String role) {
        if ("PRIMARY_IMPLICATED".equalsIgnoreCase(role)) {
            return "primary implicated actor";
        }
        if ("SECONDARY_IMPLICATED".equalsIgnoreCase(role)) {
            return "secondary implicated actor";
        }
        if ("AFFECTED_PARTY".equalsIgnoreCase(role)) {
            return "harmed-side factual anchor";
        }
        return "linked actor";
    }

    private static String buildCorePatternLine(
            ForensicReportAssembler.Assembly assembled,
            JSONObject conclusion
    ) {
        for (String item : extractStringArray(conclusion, "whatHappened", 3)) {
            String cleaned = cleanSummarySentence(item);
            if (looksLikePatternLine(cleaned)) {
                return cleaned;
            }
        }
        for (ForensicReportAssembler.IssueCard issue : safeList(assembled.issueGroups)) {
            String cleaned = cleanSummarySentence(issue != null ? issue.summary : "");
            if (looksLikePatternLine(cleaned)) {
                return cleaned;
            }
        }
        return "";
    }

    private static boolean looksLikePatternLine(String value) {
        String lower = lowerUs(value);
        return containsAny(lower, "pattern", "countersigned", "vacate", "eviction", "non-renewal", "money continued", "pressure");
    }

    private static String cleanActorText(List<String> actors) {
        List<String> cleaned = new ArrayList<>();
        if (actors != null) {
            for (String actor : actors) {
                String normalized = ActorNameNormalizer.canonicalizePublicationActor(actor);
                String lower = lowerUs(normalized);
                if (normalized.isEmpty() || containsAny(lower, "unresolved actor", "client via", "unknown")) {
                    continue;
                }
                if (!cleaned.contains(normalized)) {
                    cleaned.add(normalized);
                }
            }
        }
        return joinStrings(cleaned);
    }

    private static String cleanDateLabel(String raw) {
        String value = trimToEmpty(raw);
        if (value.isEmpty()) {
            return "";
        }
        String lower = lowerUs(value);
        if (containsAny(lower, "date not fixed", "00 march", "00 ", "unknown", "not fixed", "client via")) {
            return "";
        }
        if (value.equals(value.toUpperCase(Locale.US)) && value.length() < 20) {
            value = toTitleCase(value);
        }
        return value;
    }

    private static void appendChronologyFallback(
            StringBuilder sb,
            ForensicReportAssembler.Assembly assembled
    ) {
        int count = 0;
        for (ForensicReportAssembler.IssueCard issue : safeList(assembled.issueGroups)) {
            if (issue == null || issue.evidencePages == null || issue.evidencePages.isEmpty()) {
                continue;
            }
            String actorText = firstNonEmpty(cleanActorText(issue.actors), "the record");
            String summary = cleanSummarySentence(issue.summary);
            if (summary.isEmpty()) {
                continue;
            }
            summary = stripLeadingActorReference(summary, actorText);
            sb.append(actorText)
                    .append(" is linked in the sealed record to ")
                    .append(stripTerminalPunctuation(summary))
                    .append(". This appears in pages ")
                    .append(joinPages(issue.evidencePages))
                    .append(".\n\n");
            count++;
            if (count >= 4) {
                break;
            }
        }
        if (count == 0) {
            sb.append("The current pass did not expose enough anchored chronology lines to render a cleaner time-ordered narrative without overreach.\n\n");
        }
    }

    private static boolean isWeakChronologySummary(String summary) {
        String lower = lowerUs(summary);
        return containsAny(
                lower,
                "dated client or invoice correspondence",
                "appears in dated correspondence",
                "client via",
                "law-enforcement notice reference",
                "official record",
                "witness statement"
        );
    }

    private static String cleanSummarySentence(String value) {
        String cleaned = trimToEmpty(value)
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace("·", " ")
                .replace("...[truncated for on-device prompt]...", " ")
                .replace("[truncated for on-device prompt]", " ");
        while (cleaned.contains("  ")) {
            cleaned = cleaned.replace("  ", " ");
        }
        cleaned = LEADING_PAGE_PATTERN.matcher(cleaned).replaceFirst("");
        cleaned = LEADING_DATE_PATTERN.matcher(cleaned).replaceFirst("");
        cleaned = cleaned.replace("is linked to primary evidence that", "shows that")
                .replace("is linked to an escalation or complaint reference", "appears in an escalation or complaint record")
                .replace("is linked to dated client or invoice correspondence", "appears in dated correspondence")
                .replace("is linked to a dated meeting or dispute communication", "appears in a dated meeting or dispute communication")
                .replace("CLIENT VIA", "")
                .replace("is linked to", "")
                .replace("This appears in pages", "")
                .trim();
        if (cleaned.startsWith(":")) {
            cleaned = cleaned.substring(1).trim();
        }
        if (containsAny(lowerUs(cleaned), "barnard and associates", "registration no:", "vat no.:", "client via")) {
            return "";
        }
        return ensureSentence(cleaned);
    }

    private static String stripLeadingActorReference(String summary, String actorText) {
        String cleaned = trimToEmpty(summary);
        String actor = trimToEmpty(actorText);
        if (cleaned.isEmpty() || actor.isEmpty()) {
            return cleaned;
        }
        String lowerSummary = lowerUs(cleaned);
        String lowerActor = lowerUs(actor);
        if (lowerSummary.startsWith(lowerActor + " ")) {
            cleaned = trimToEmpty(cleaned.substring(actor.length()));
        }
        if (cleaned.toLowerCase(Locale.US).startsWith("is linked in the sealed record to ")) {
            cleaned = cleaned.substring("is linked in the sealed record to ".length()).trim();
        }
        if (cleaned.toLowerCase(Locale.US).startsWith("is linked to ")) {
            cleaned = cleaned.substring("is linked to ".length()).trim();
        }
        return cleaned;
    }

    private static String toTitleCase(String value) {
        String[] parts = trimToEmpty(value).toLowerCase(Locale.US).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String recommendedUse(ForensicReportAssembler.IssueCard issue) {
        String lower = lowerUs(issue.title + " " + issue.summary);
        if (containsAny(lower, "document", "agreement", "lease", "renewal", "mou")) {
            return "Use to establish the document-execution history and what was never countersigned or returned.";
        }
        if (containsAny(lower, "vacate", "eviction", "pressure", "non-renewal")) {
            return "Use to establish the pressure, vacate, or non-renewal pattern and the actor linked to it.";
        }
        return "Use as a primary anchor for statement-taking and sequence reconstruction.";
    }

    private static String matchLegalFramework(AnalysisEngine.ForensicReport report, String category) {
        String lowerCategory = lowerUs(category);
        if (report.legalReferences != null) {
            for (String item : report.legalReferences) {
                String lower = lowerUs(item);
                if (lower.contains(lowerCategory)
                        || (lowerCategory.contains("precca") && lower.contains("precca"))
                        || (lowerCategory.contains("access") && containsAny(lower, "access", "password"))
                        || (lowerCategory.contains("concealment") && containsAny(lower, "conceal", "deletion"))
                        || (lowerCategory.contains("financial") && containsAny(lower, "financial", "company", "fraud"))) {
                    return trimToEmpty(item);
                }
            }
        }
        return "Framework identified; exact section requires legal confirmation";
    }

    private static String primaryActor(JSONObject conclusion) {
        JSONArray actors = conclusion != null ? conclusion.optJSONArray("implicatedActors") : null;
        if (actors == null) {
            return "";
        }
        for (int i = 0; i < actors.length(); i++) {
            JSONObject item = actors.optJSONObject(i);
            if (item != null && "PRIMARY_IMPLICATED".equalsIgnoreCase(item.optString("role", ""))) {
                return ActorNameNormalizer.canonicalizePublicationActor(item.optString("actor", ""));
            }
        }
        return "";
    }

    private static String harmedParty(JSONObject conclusion) {
        JSONArray actors = conclusion != null ? conclusion.optJSONArray("implicatedActors") : null;
        if (actors == null) {
            return "";
        }
        for (int i = 0; i < actors.length(); i++) {
            JSONObject item = actors.optJSONObject(i);
            if (item != null && "AFFECTED_PARTY".equalsIgnoreCase(item.optString("role", ""))) {
                return ActorNameNormalizer.canonicalizePublicationActor(item.optString("actor", ""));
            }
        }
        return "";
    }

    private static boolean isAffectedActor(String actor, String affectedActor, JSONObject conclusion) {
        if (sameActor(actor, affectedActor)) {
            return true;
        }
        JSONArray actors = conclusion != null ? conclusion.optJSONArray("implicatedActors") : null;
        if (actors == null) {
            return false;
        }
        for (int i = 0; i < actors.length(); i++) {
            JSONObject item = actors.optJSONObject(i);
            if (item == null || !"AFFECTED_PARTY".equalsIgnoreCase(item.optString("role", ""))) {
                continue;
            }
            if (sameActor(actor, item.optString("actor", ""))) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameActor(String left, String right) {
        String canonicalLeft = ActorNameNormalizer.canonicalizePublicationActor(left);
        String canonicalRight = ActorNameNormalizer.canonicalizePublicationActor(right);
        return !canonicalLeft.isEmpty() && canonicalLeft.equalsIgnoreCase(canonicalRight);
    }

    private static String normalizeProcessingStatus(
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled
    ) {
        String status = report != null && report.diagnostics != null
                ? trimToEmpty(report.diagnostics.optString("processingStatus", ""))
                : "";
        if (status.isEmpty()) {
            status = assembled.candidateContradictionCount > 0
                    ? "DETERMINATE WITH MATERIAL COVERAGE GAPS"
                    : "DETERMINATE";
        }
        status = status.replace('_', ' ').toUpperCase(Locale.US);
        if ("DETERMINATE".equals(status) && assembled.candidateContradictionCount > 0) {
            return "DETERMINATE WITH MATERIAL COVERAGE GAPS";
        }
        return status;
    }

    private static String tripleVerificationStatus(AnalysisEngine.ForensicReport report) {
        JSONObject overall = report != null && report.tripleVerification != null
                ? report.tripleVerification.optJSONObject("overall")
                : null;
        return overall != null
                ? trimToEmpty(overall.optString("status", "PASS"))
                : "PASS";
    }

    private static List<Integer> extractPagesFromConclusion(JSONObject conclusion, int limit) {
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        for (ForensicConclusionEngine.ForensicProposition proposition : propositionObjects(conclusion)) {
            out.addAll(proposition.anchorPages);
            if (out.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(out).subList(0, Math.min(out.size(), limit));
    }

    private static List<ForensicConclusionEngine.ForensicProposition> propositionObjects(JSONObject conclusion) {
        List<ForensicConclusionEngine.ForensicProposition> out = new ArrayList<>();
        JSONArray propositions = conclusion != null ? conclusion.optJSONArray("forensicPropositions") : null;
        if (propositions == null) {
            return out;
        }
        for (int i = 0; i < propositions.length(); i++) {
            JSONObject item = propositions.optJSONObject(i);
            if (item == null) {
                continue;
            }
            ForensicConclusionEngine.ForensicProposition proposition = new ForensicConclusionEngine.ForensicProposition();
            proposition.actor = trimToEmpty(item.optString("actor", ""));
            proposition.conduct = trimToEmpty(item.optString("conduct", ""));
            proposition.timestamp = trimToEmpty(item.optString("timestamp", "UNRESOLVED_TIME"));
            proposition.anchorPages = jsonIntList(item.optJSONArray("anchorPages"), 12);
            proposition.offenceMapping = trimToEmpty(item.optString("offenceMapping", ""));
            proposition.status = trimToEmpty(item.optString("status", "CERTIFIED_CONDUCT"));
            proposition.publicationBoundary = trimToEmpty(item.optString("publicationBoundary", ""));
            out.add(proposition);
        }
        return out;
    }

    private static List<String> extractStringArray(JSONObject object, String key, int limit) {
        return jsonStringList(object != null ? object.optJSONArray(key) : null, limit);
    }

    private static List<String> jsonStringList(JSONArray array, int limit) {
        List<String> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length() && out.size() < limit; i++) {
            String value = trimToEmpty(array.optString(i, ""));
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    private static List<Integer> jsonIntList(JSONArray array, int limit) {
        List<Integer> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length() && out.size() < limit; i++) {
            int value = array.optInt(i, 0);
            if (value > 0 && !out.contains(value)) {
                out.add(value);
            }
        }
        return out;
    }

    private static <T> List<T> safeList(List<T> source) {
        return source != null ? source : Collections.emptyList();
    }

    private static List<Integer> mergePages(List<Integer> current, List<Integer> more) {
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        if (current != null) {
            out.addAll(current);
        }
        if (more != null) {
            for (Integer page : more) {
                if (page != null && page > 0) {
                    out.add(page);
                }
            }
        }
        return new ArrayList<>(out);
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        sb.append("- ").append(label).append(": ").append(safeValue(value)).append("\n");
    }

    private static String fallbackPageText(JSONObject conclusion, ForensicReportAssembler.Assembly assembled) {
        List<Integer> pages = extractPagesFromConclusion(conclusion, 6);
        if (pages.isEmpty()) {
            for (String item : safeList(assembled.readFirstPages)) {
                try {
                    pages.add(Integer.parseInt(item));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return pages.isEmpty() ? "not fixed in this pass" : joinPages(pages);
    }

    private static String humanizeCategory(String raw) {
        String value = trimToEmpty(raw);
        if (value.isEmpty()) {
            return "";
        }
        String lower = lowerUs(value);
        if (lower.contains("fraud")) {
            return "Fraud";
        }
        if (containsAny(lower, "theft", "diversion", "unlawful enrichment")) {
            return "Unlawful enrichment or financial diversion";
        }
        if (containsAny(lower, "conceal", "deletion")) {
            return "Concealment";
        }
        if (containsAny(lower, "gaslighting", "evasion")) {
            return "Evasion / gaslighting";
        }
        if (containsAny(lower, "access", "password", "digital")) {
            return "Unauthorized account access or digital interference";
        }
        return value;
    }

    private static String ensureSentence(String value) {
        String trimmed = trimToEmpty(value);
        if (trimmed.isEmpty()) {
            return "";
        }
        char last = trimmed.charAt(trimmed.length() - 1);
        if (last == '.' || last == '!' || last == '?') {
            return trimmed;
        }
        return trimmed + ".";
    }

    private static String stripTerminalPunctuation(String value) {
        String trimmed = trimToEmpty(value);
        while (!trimmed.isEmpty()) {
            char last = trimmed.charAt(trimmed.length() - 1);
            if (last == '.' || last == '!' || last == '?' || last == ';' || last == ':') {
                trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            } else {
                break;
            }
        }
        return trimmed;
    }

    private static String safeValue(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value.trim();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String lowerUs(String value) {
        return trimToEmpty(value).toLowerCase(Locale.US);
    }

    private static String joinStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            String trimmed = trimToEmpty(value);
            if (trimmed.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(trimmed);
        }
        return sb.toString();
    }

    private static String joinPages(List<Integer> pages) {
        if (pages == null || pages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Integer page : pages) {
            if (page == null || page <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(page);
        }
        return sb.toString();
    }

    private static boolean containsAny(String corpus, String... needles) {
        if (corpus == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && corpus.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String trimmed = trimToEmpty(value);
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    private static final class LawRow {
        String category = "";
        String jurisdiction = "";
        String framework = "";
        String explanation = "";
        List<Integer> pages = new ArrayList<>();
        String status = "";
        String notes = "";
    }

    private static final class ChargeRow {
        String charge = "";
        String jurisdiction = "";
        String respondents = "";
        String factualBasis = "";
        List<Integer> pages = new ArrayList<>();
        String reason = "";
        String status = "";
    }

    private static final class PersonRow {
        final String name;
        String role = "";
        String reason = "";
        List<Integer> pages = new ArrayList<>();
        String status = "";
        String statusReason = "";

        PersonRow(String name) {
            this.name = name;
        }
    }
}
