package com.verum.omnis.core;

import java.util.Locale;

public final class ActorNameNormalizer {

    private ActorNameNormalizer() {}

    public static String canonicalizePublicationActor(String value) {
        String trimmed = trimToEmpty(value);
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.isEmpty()) {
            return "";
        }
        if ("des".equals(lower)
                || "desmond".equals(lower)
                || "desmond smith".equals(lower)
                || "des smith".equals(lower)
                || "dez".equals(lower)
                || lower.startsWith("desmond owen smith")) {
            return "Desmond Smith";
        }
        if ("wayne".equals(lower) || "wayne nel".equals(lower) || "wayne nell".equals(lower)) {
            return "Wayne Nel";
        }
        if ("gary highcock".equals(lower) || "your dad".equals(lower)) {
            return "Gary Highcock";
        }
        if ("marius".equals(lower) || "marius nor".equals(lower) || "marius nortje".equals(lower) || "marius nortjé".equals(lower)) {
            return "Marius Nortje";
        }
        if ("liam".equals(lower) || "liam highcock".equals(lower) || "liam harcock".equals(lower) || "liam highcock founder".equals(lower)) {
            return "Liam Highcock";
        }
        if ("kevin".equals(lower) || "kevin lappeman".equals(lower)) {
            return "Kevin Lappeman";
        }
        if ("nealy lombaard".equals(lower) || "nealy lombard".equals(lower)) {
            return "Nealy Lombaard";
        }
        if ("ritz louw".equals(lower)) {
            return "Ritz Louw";
        }
        if ("morne olivier".equals(lower)) {
            return "Morne Olivier";
        }
        if ("silicia".equals(lower) || "silicia scheppel-barnard".equals(lower) || "silicia barnard".equals(lower)) {
            return "Silicia Scheppel-Barnard";
        }
        return trimmed;
    }

    public static boolean isCanonicalAliasMatch(String left, String right) {
        String canonicalLeft = canonicalizePublicationActor(left);
        String canonicalRight = canonicalizePublicationActor(right);
        return !canonicalLeft.isEmpty() && canonicalLeft.equalsIgnoreCase(canonicalRight);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
