package com.verum.omnis.forensic;

import android.content.Context;

import com.verum.omnis.core.ConstitutionalConfig;
import com.verum.omnis.core.HashUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PropositionExtractor {

    private static final Pattern EMAIL_NAME_PATTERN =
            Pattern.compile("([A-Z][A-Za-z]+(?:\\s+[A-Z][A-Za-z'\\-]+){0,2})\\s*<[^>]+>");
    private static final Pattern PERSON_PATTERN =
            Pattern.compile("\\b([A-Z][a-z]{2,}(?:\\s+[A-Z][a-z]{2,}){0,2})\\b");
    private static final Pattern DATE_PATTERN =
            Pattern.compile("\\b(?:\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}[/-]\\d{2}[/-]\\d{2}|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\s+\\d{1,2},?\\s+\\d{4})\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern MONEY_PATTERN =
            Pattern.compile("\\b(?:R|ZAR|USD|AED|EUR|GBP|\\$)\\s?\\d[\\d,]*(?:\\.\\d{2})?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TARGET_PATTERN =
            Pattern.compile("\\b(?:to|from|against|with|for)\\s+([A-Z][A-Za-z]+(?:\\s+[A-Z][A-Za-z'\\-]+){0,2})\\b");
    private static final Pattern NEGATION_PATTERN =
            Pattern.compile("\\b(?:no|not|never|none|without|did not|didn't|cannot|can't|won't|refused|denied|deny|failed)\\b",
                    Pattern.CASE_INSENSITIVE);

    private PropositionExtractor() {}

    public static List<ExtractedProposition> extract(Context context, NativeEvidenceResult nativeEvidence) {
        List<ExtractedProposition> propositions = new ArrayList<>();
        if (nativeEvidence == null) {
            return propositions;
        }

        ConstitutionalConfig.Snapshot config = ConstitutionalConfig.load(context);
        List<OcrTextBlock> blocks = preferredBlocks(nativeEvidence);
        List<String> namedParties = extractNamedParties(blocks, config);
        Set<String> triggers = collectTriggers(config);
        Set<String> seen = new LinkedHashSet<>();

        for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
            OcrTextBlock block = blocks.get(blockIndex);
            if (block == null || block.text == null || block.text.trim().isEmpty()) {
                continue;
            }
            if (containsAny(block.text, config.generatedAnalysisMarkers)
                    || containsAny(block.text, config.systemLineMarkers)) {
                continue;
            }

            List<String> sentences = splitIntoSentences(block.text);
            for (int sentenceIndex = 0; sentenceIndex < sentences.size(); sentenceIndex++) {
                String sentence = sentences.get(sentenceIndex);
                String normalized = normalize(sentence);
                if (normalized.length() < 24) {
                    continue;
                }
                if (!containsAny(normalized, triggers)) {
                    continue;
                }
                String actor = inferActor(normalized, namedParties, config);
                String category = classifyCategory(normalized, config);
                String dedupeKey = actor.toLowerCase(Locale.ROOT) + "|" + Integer.toHexString(normalized.toLowerCase(Locale.ROOT).hashCode());
                if (!seen.add(dedupeKey)) {
                    continue;
                }

                ExtractedProposition proposition = new ExtractedProposition(
                        normalized,
                        actor,
                        inferTarget(normalized, actor, namedParties),
                        inferDateOrRange(normalized),
                        inferAmount(normalized),
                        inferCurrency(normalized),
                        isNegated(normalized),
                        ordinalConfidence(block.confidence, normalized),
                        category,
                        "TEXT_BLOCK"
                );
                int[] lineRange = estimateLineRange(block.text, normalized);
                proposition.anchors.add(new EvidenceAnchor(
                        "PR-" + HashUtil.truncate(nativeEvidence.evidenceHash, 8) + "-" + String.format(Locale.US, "%03d", block.pageIndex + 1),
                        nativeEvidence.fileName,
                        block.pageIndex + 1,
                        lineRange[0],
                        lineRange[1],
                        "",
                        "",
                        "block-" + (block.pageIndex + 1) + "-" + blockIndex + "-" + sentenceIndex,
                        approximateFileOffset(block.pageIndex, sentenceIndex),
                        "EX-" + String.format(Locale.US, "%03d", block.pageIndex + 1),
                        truncate(normalized, 220),
                        "PROPOSITION"
                ));
                propositions.add(proposition);
            }
        }
        return propositions;
    }

    private static List<OcrTextBlock> preferredBlocks(NativeEvidenceResult nativeEvidence) {
        if (!nativeEvidence.documentTextBlocks.isEmpty()) {
            return nativeEvidence.documentTextBlocks;
        }
        return nativeEvidence.ocrBlocks;
    }

    private static List<String> splitIntoSentences(String text) {
        String[] raw = text.replace('\r', '\n').split("(?<=[.!?])\\s+|\\n+");
        List<String> out = new ArrayList<>();
        for (String item : raw) {
            String normalized = normalize(item);
            if (!normalized.isEmpty()) {
                out.add(normalized);
            }
        }
        return out;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String text, Iterable<String> needles) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && lower.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> extractNamedParties(List<OcrTextBlock> blocks, ConstitutionalConfig.Snapshot config) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (OcrTextBlock block : blocks) {
            if (block == null || block.text == null) {
                continue;
            }
            Matcher emailMatcher = EMAIL_NAME_PATTERN.matcher(block.text);
            while (emailMatcher.find()) {
                recordName(emailMatcher.group(1), counts, config);
            }
            Matcher personMatcher = PERSON_PATTERN.matcher(block.text);
            while (personMatcher.find()) {
                recordName(personMatcher.group(1), counts, config);
            }
        }
        List<String> parties = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= 2) {
                parties.add(entry.getKey());
            }
        }
        return parties;
    }

    private static void recordName(String candidate, Map<String, Integer> counts, ConstitutionalConfig.Snapshot config) {
        String clean = sanitizeActorCandidate(candidate);
        if (clean.isEmpty() || config.nameStopwords.contains(clean)) {
            return;
        }
        String lower = clean.toLowerCase(Locale.ROOT);
        if (config.actorNoiseTokens.contains(lower) || containsDiscardedActorPhrase(lower)) {
            return;
        }
        counts.put(clean, counts.getOrDefault(clean, 0) + 1);
    }

    private static String inferActor(String text, List<String> namedParties, ConstitutionalConfig.Snapshot config) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String party : namedParties) {
            if (lower.contains(party.toLowerCase(Locale.ROOT))) {
                return party;
            }
        }
        Matcher emailMatcher = EMAIL_NAME_PATTERN.matcher(text);
        if (emailMatcher.find()) {
            String candidate = sanitizeActorCandidate(emailMatcher.group(1));
            if (!config.nameStopwords.contains(candidate)
                    && !containsDiscardedActorPhrase(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        int fromIndex = lower.indexOf("from:");
        if (fromIndex >= 0) {
            Matcher personMatcher = PERSON_PATTERN.matcher(text.substring(fromIndex));
            if (personMatcher.find()) {
                String candidate = sanitizeActorCandidate(personMatcher.group(1));
                if (!config.nameStopwords.contains(candidate)
                        && !containsDiscardedActorPhrase(candidate.toLowerCase(Locale.ROOT))) {
                    return candidate;
                }
            }
        }
        return "unresolved actor";
    }

    private static boolean containsDiscardedActorPhrase(String lower) {
        if (lower == null || lower.trim().isEmpty()) {
            return false;
        }
        return lower.contains("dear ")
                || lower.contains(" sir")
                || lower.contains("sirs")
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
                || lower.endsWith(" sent")
                || lower.endsWith(" from")
                || lower.endsWith(" to")
                || lower.endsWith(" subject")
                || lower.endsWith(" date")
                || lower.contains("national crime")
                || lower.contains("glenmore");
    }

    private static String sanitizeActorCandidate(String candidate) {
        if (candidate == null) {
            return "";
        }
        String clean = candidate.replaceAll("\\s+", " ").trim();
        clean = clean.replaceAll("(?i)\\b(sent|from|to|cc|bcc|subject|date|message|reply|forwarded|received|attachment|attached|file|footer|disclaimer)\\b\\s*$", "").trim();
        clean = clean.replaceAll("(?i)^[\\-:,;\\s]+|[\\-:,;\\s]+$", "").trim();
        return clean;
    }

    private static Set<String> collectTriggers(ConstitutionalConfig.Snapshot config) {
        Set<String> triggers = new LinkedHashSet<>();
        triggers.addAll(config.keywords);
        triggers.addAll(config.contradictions);
        triggers.addAll(config.financial);
        for (List<String> values : config.subjectKeywords.values()) {
            triggers.addAll(values);
        }
        for (List<String> values : config.documentIntegrityKeywords.values()) {
            triggers.addAll(values);
        }
        return triggers;
    }

    private static String classifyCategory(String text, ConstitutionalConfig.Snapshot config) {
        for (Map.Entry<String, List<String>> entry : config.subjectKeywords.entrySet()) {
            if (containsAny(text, entry.getValue())) {
                return entry.getKey();
            }
        }
        if (containsAny(text, config.financial)) {
            return "Financial";
        }
        if (containsAny(text, config.contradictions)) {
            return "Contradiction";
        }
        return "General";
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String inferTarget(String text, String actor, List<String> namedParties) {
        String lowerActor = actor == null ? "" : actor.toLowerCase(Locale.ROOT);
        for (String party : namedParties) {
            if (party == null || party.trim().isEmpty()) {
                continue;
            }
            String lowerParty = party.toLowerCase(Locale.ROOT);
            if (!lowerParty.equals(lowerActor) && text.toLowerCase(Locale.ROOT).contains(lowerParty)) {
                return party;
            }
        }
        Matcher matcher = TARGET_PATTERN.matcher(text);
        if (matcher.find()) {
            String candidate = sanitizeActorCandidate(matcher.group(1));
            if (!candidate.equalsIgnoreCase(actor)) {
                return candidate;
            }
        }
        return "";
    }

    private static String inferDateOrRange(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        return matcher.find() ? matcher.group().trim() : "";
    }

    private static String inferAmount(String text) {
        Matcher matcher = MONEY_PATTERN.matcher(text);
        return matcher.find() ? matcher.group().replaceAll("\\s+", " ").trim() : "";
    }

    private static String inferCurrency(String text) {
        String amount = inferAmount(text).toUpperCase(Locale.ROOT);
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

    private static boolean isNegated(String text) {
        return NEGATION_PATTERN.matcher(text).find();
    }

    private static String ordinalConfidence(float blockConfidence, String text) {
        if (blockConfidence >= 0.95f && text != null && text.length() >= 80) {
            return "HIGH";
        }
        if (blockConfidence >= 0.75f) {
            return "MODERATE";
        }
        return "LOW";
    }

    private static int[] estimateLineRange(String blockText, String sentence) {
        if (blockText == null || blockText.trim().isEmpty()) {
            return new int[]{0, 0};
        }
        String[] lines = blockText.split("\\r?\\n");
        String sentenceLower = sentence == null ? "" : sentence.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lines.length; i++) {
            String line = normalize(lines[i]);
            if (!line.isEmpty() && sentenceLower.contains(line.toLowerCase(Locale.ROOT))) {
                return new int[]{i + 1, i + 1};
            }
        }
        return new int[]{1, Math.max(1, lines.length)};
    }

    private static long approximateFileOffset(int pageIndex, int sentenceIndex) {
        return Math.max(0, pageIndex) * 1_000_000L + Math.max(0, sentenceIndex) * 1_000L;
    }
}
