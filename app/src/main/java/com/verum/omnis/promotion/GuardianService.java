package com.verum.omnis.promotion;

import android.content.Context;

import com.verum.omnis.core.AnalysisEngine;
import com.verum.omnis.core.FindingAudit;
import com.verum.omnis.core.GuardianDecision;
import com.verum.omnis.core.PromotionDecision;
import com.verum.omnis.security.ConstitutionGate;

import org.json.JSONObject;

public class GuardianService {

    public GuardianDecision approve(Context context, JSONObject finding, FindingAudit audit, PromotionDecision decision, AnalysisEngine.ForensicReport report) {
        if (!decision.promoted) {
            return new GuardianDecision(false, "Promotion decision did not pass P1-P7.");
        }
        ConstitutionGate.VerificationResult constitution = ConstitutionGate.verifyAll(context);
        if (!constitution.ok) {
            return new GuardianDecision(false, "Constitution asset verification failed.");
        }
        if (report == null || report.evidenceHash == null || report.evidenceHash.trim().isEmpty()) {
            return new GuardianDecision(false, "Evidence hash was missing.");
        }
        String summary = finding.optString("summary", "").toLowerCase();
        String narrative = finding.optString("narrative", "").toLowerCase();
        if (summary.contains("fraud confirmed") || narrative.contains("fraud confirmed")) {
            return new GuardianDecision(false, "Guardian blocked verdict-style overclaim.");
        }
        if (audit.sourceAnchors.isEmpty()) {
            return new GuardianDecision(false, "Guardian blocked uncertified finding without source anchors.");
        }
        return new GuardianDecision(true, "Guardian approved the promoted finding.");
    }
}
