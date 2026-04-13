package com.verum.omnis.core;

import android.content.Context;
public final class CommercialAccessManager {

    public static final class Snapshot {
        public final boolean trialActive;
        public final int trialDays;
        public final long daysRemaining;
        public final long installTimeMillis;
        public final long trialEndMillis;
        public final String installDateLabel;
        public final String expiryDateLabel;
        public final String statusLine;

        public Snapshot(
                boolean trialActive,
                int trialDays,
                long daysRemaining,
                long installTimeMillis,
                long trialEndMillis,
                String installDateLabel,
                String expiryDateLabel,
                String statusLine
        ) {
            this.trialActive = trialActive;
            this.trialDays = trialDays;
            this.daysRemaining = daysRemaining;
            this.installTimeMillis = installTimeMillis;
            this.trialEndMillis = trialEndMillis;
            this.installDateLabel = installDateLabel;
            this.expiryDateLabel = expiryDateLabel;
            this.statusLine = statusLine;
        }
    }

    private CommercialAccessManager() {}

    public static Snapshot load(Context context, int trialDays) {
        int normalizedTrialDays = Math.max(0, trialDays);
        boolean active = normalizedTrialDays > 0;
        long daysRemaining = normalizedTrialDays;
        long installTime = 0L;
        long trialEnd = 0L;
        String installDate = "Triggered per certified handoff";
        String expiryDate = normalizedTrialDays + " day review window from recipient access";
        String statusLine = active
                ? "Commercial and institutional recipients receive a " + normalizedTrialDays + "-day review window from case delivery or recipient access. Private citizens and law enforcement stay free globally."
                : "Commercial review window is disabled. Private citizens and law enforcement stay free globally.";

        return new Snapshot(
                active,
                normalizedTrialDays,
                daysRemaining,
                installTime,
                trialEnd,
                installDate,
                expiryDate,
                statusLine
        );
    }
}
