package com.verum.omnis.core;

import org.json.JSONException;
import org.json.JSONObject;

public class FindingCertification {
    public final String constitutionHash;
    public final String rulePackVersion;
    public final String engineVersion;
    public final String deterministicRunId;
    public final String evidenceBundleHash;
    public final String findingHash;
    public final String promotionHash;
    public final boolean guardianApproval;
    public final String guardianReason;
    public final String reproducibilityStatement;
    public final String certifiedAtIso;

    public FindingCertification(
            String constitutionHash,
            String rulePackVersion,
            String engineVersion,
            String deterministicRunId,
            String evidenceBundleHash,
            String findingHash,
            String promotionHash,
            boolean guardianApproval,
            String guardianReason,
            String reproducibilityStatement,
            String certifiedAtIso
    ) {
        this.constitutionHash = constitutionHash;
        this.rulePackVersion = rulePackVersion;
        this.engineVersion = engineVersion;
        this.deterministicRunId = deterministicRunId;
        this.evidenceBundleHash = evidenceBundleHash;
        this.findingHash = findingHash;
        this.promotionHash = promotionHash;
        this.guardianApproval = guardianApproval;
        this.guardianReason = guardianReason;
        this.reproducibilityStatement = reproducibilityStatement;
        this.certifiedAtIso = certifiedAtIso;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject out = new JSONObject();
        out.put("constitutionHash", constitutionHash);
        out.put("rulePackVersion", rulePackVersion);
        out.put("engineVersion", engineVersion);
        out.put("deterministicRunId", deterministicRunId);
        out.put("evidenceBundleHash", evidenceBundleHash);
        out.put("findingHash", findingHash);
        out.put("promotionHash", promotionHash);
        out.put("guardianApproval", guardianApproval);
        out.put("guardianReason", guardianReason);
        out.put("reproducibilityStatement", reproducibilityStatement);
        out.put("certifiedAtIso", certifiedAtIso);
        return out;
    }
}
