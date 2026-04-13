package com.verum.omnis.core;

import java.util.List;
import java.util.Locale;

public final class ConclusionTemplates {

    private ConclusionTemplates() {}

    public static String buildStrongestConclusion(ForensicConclusionEngine.ForensicConclusion conclusion) {
        if (conclusion == null) {
            return "";
        }
        if (conclusion.forensicPropositions != null) {
            for (ForensicConclusionEngine.ForensicProposition proposition : conclusion.forensicPropositions) {
                if (proposition == null) {
                    continue;
                }
                String actor = trimToEmpty(proposition.actor);
                String conduct = trimToEmpty(proposition.conduct);
                if (!actor.isEmpty() && !conduct.isEmpty()) {
                    return cleanSentence(actor + " is linked in the sealed record to " + stripTerminalPunctuation(conduct));
                }
            }
        }
        String primaryActor = ForensicConclusionEngine.primaryImplicatedActorName(conclusion);
        String whatHappened = firstUseful(conclusion.whatHappened);
        switch (trimToEmpty(conclusion.narrativeType)) {
            case "GUILT_READY":
                return cleanSentence(firstNonEmpty(
                        "The engine has reached a guilt-ready conclusion against " + primaryActor + ": " + whatHappened,
                        whatHappened
                ));
            case "PROVEN_MISCONDUCT":
                return cleanSentence(firstNonEmpty(
                        "The sealed record proves misconduct in a way that centers on " + primaryActor + ": " + whatHappened,
                        whatHappened
                ));
            case "IMPLICATION_PATTERN":
                return cleanSentence(firstNonEmpty(
                        "The sealed record shows " + whatHappened + " The current record points mainly to " + primaryActor + " as the primary implicated actor.",
                        "The current record points mainly to " + primaryActor + " as the primary implicated actor."
                ));
            case "FACT_PATTERN":
            default:
                return cleanSentence(firstNonEmpty(
                        "The sealed record shows " + whatHappened,
                        whatHappened
                ));
        }
    }

    public static String buildPublicationBoundary(ForensicConclusionEngine.ForensicConclusion conclusion) {
        if (conclusion == null || conclusion.guiltGate == null) {
            return "This is a forensic conclusion, not a judicial verdict.";
        }
        if (conclusion.guiltGate.allowed) {
            return "This pass met the adjudicative publication gate.";
        }
        return cleanSentence(
                "This pass lets the report say what the sealed record shows and who it links most strongly. "
                        + trimToEmpty(conclusion.guiltGate.reason)
                        + " This is a forensic conclusion, not a judicial verdict."
        );
    }

    private static String firstUseful(List<String> values) {
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

    private static String cleanSentence(String value) {
        String cleaned = trimToEmpty(value)
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace("  ", " ")
                .trim();
        while (cleaned.contains("  ")) {
            cleaned = cleaned.replace("  ", " ");
        }
        if (cleaned.isEmpty()) {
            return "";
        }
        char last = cleaned.charAt(cleaned.length() - 1);
        if (last == '.' || last == '!' || last == '?') {
            return cleaned;
        }
        return cleaned + ".";
    }

    private static String stripTerminalPunctuation(String value) {
        String cleaned = trimToEmpty(value);
        while (!cleaned.isEmpty()) {
            char last = cleaned.charAt(cleaned.length() - 1);
            if (last == '.' || last == '!' || last == '?') {
                cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
                continue;
            }
            break;
        }
        return cleaned;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
