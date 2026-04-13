package com.verum.omnis.promotion;

import android.content.Context;

import com.verum.omnis.core.AnalysisEngine;
import com.verum.omnis.core.FindingAudit;
import com.verum.omnis.core.FindingCertification;
import com.verum.omnis.core.GuardianDecision;
import com.verum.omnis.core.HashUtil;
import com.verum.omnis.core.PromotionDecision;
import com.verum.omnis.core.RulesProvider;

import org.json.JSONObject;

import java.time.OffsetDateTime;

public class CertificationService {

    public FindingCertification generate(
            Context context,
            JSONObject finding,
            FindingAudit audit,
            PromotionDecision decision,
            GuardianDecision guardianDecision,
            AnalysisEngine.ForensicReport report
    ) throws Exception {
        String constitutionHash = HashUtil.sha512(RulesProvider.getConstitution(context).getBytes("UTF-8"));
        String findingHash = HashUtil.sha256(finding.toString());
        String promotionHash = HashUtil.sha256(
                finding.toString()
                        + audit.toJson().toString()
                        + decision.toJson().toString()
                        + guardianDecision.toJson().toString()
        );
        String reproducibility = "This certified finding is reproducible from the evidence bundle hash, the constitution asset hash, the rule version, the engine version, and the recorded promotion audit.";
        return new FindingCertification(
                constitutionHash,
                report.rulesVersion,
                report.engineVersion,
                report.deterministicRunId,
                report.evidenceBundleHash,
                findingHash,
                promotionHash,
                guardianDecision.approved,
                guardianDecision.reason,
                reproducibility,
                OffsetDateTime.now().toString()
        );
    }
}
