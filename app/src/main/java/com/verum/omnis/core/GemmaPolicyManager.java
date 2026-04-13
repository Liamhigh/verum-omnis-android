package com.verum.omnis.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

public final class GemmaPolicyManager {

    public static final class Snapshot {
        public final String title;
        public final String summary;
        public final String runtimeBoundary;
        public final String accessWorkflow;
        public final String reportContract;
        public final String trialStatus;

        public Snapshot(
                String title,
                String summary,
                String runtimeBoundary,
                String accessWorkflow,
                String reportContract,
                String trialStatus
        ) {
            this.title = title;
            this.summary = summary;
            this.runtimeBoundary = runtimeBoundary;
            this.accessWorkflow = accessWorkflow;
            this.reportContract = reportContract;
            this.trialStatus = trialStatus;
        }
    }

    private GemmaPolicyManager() {}

    public static Snapshot load(Context context) {
        try {
            JSONObject root = new JSONObject(RulesProvider.getGemmaPolicy(context));
            JSONObject identity = root.getJSONObject("identity");
            JSONObject boundary = root.getJSONObject("runtimeBoundary");
            JSONObject comms = root.getJSONObject("communicationPolicy");
            JSONObject productRole = root.getJSONObject("productRole");
            JSONObject customerClasses = root.getJSONObject("customerClasses");
            JSONObject commercialRelease = root.getJSONObject("commercialReleasePolicy");
            JSONObject taxReturns = customerClasses.getJSONObject("taxReturns");
            JSONObject contract = root.getJSONObject("reportContract");
            JSONObject behaviourLibrary = new JSONObject(RulesProvider.getBehaviourPatternLibrary(context));
            JSONArray behaviourPatterns = behaviourLibrary.optJSONArray("patterns");
            int behaviourPatternCount = behaviourPatterns != null ? behaviourPatterns.length() : 0;
            int commercialTrialDays = commercialRelease.optInt(
                    "trialDays",
                    customerClasses.getJSONObject("commercialInstitutional").optInt("trialDays", 30)
            );
            CommercialAccessManager.Snapshot trialSnapshot =
                    CommercialAccessManager.load(context, commercialTrialDays);

            String title = identity.optString("name", "Gemma")
                    + " - "
                    + identity.optString("role", "Legal assistant");

            String summary = "Style: " + identity.optString("interactionStyle", "natural")
                    + "\nConversation: " + (comms.optBoolean("naturalConversationRequired", true) ? "Natural and helpful" : "Undefined")
                    + "\nAccess model: offline, vault-connected, constitution-bound"
                    + "\nCitizen access: " + productRole.optString("privateCitizenAccess", "free")
                    + " | Law enforcement: " + productRole.optString("lawEnforcementAccess", "free") + " globally"
                    + " | Business: " + productRole.optString("businessAccess", "paid")
                    + "\nBehaviour library: " + behaviourPatternCount + " reusable forensic patterns";

            String runtimeBoundary = "Internet: " + (boundary.optBoolean("internetAccess", false) ? "Enabled" : "Disabled")
                    + "\nRaw evidence: " + (boundary.optBoolean("rawEvidenceAllowed", false) ? "Allowed" : "Forbidden")
                    + "\nSealed findings only: " + (boundary.optBoolean("sealedFindingsRequired", true) ? "Yes" : "No")
                    + "\nForensic engine first: " + yesNo(boundary.optBoolean("forensicEngineFirst", true))
                    + "\nInvent facts/law: "
                    + ((boundary.optBoolean("inventFactsAllowed", false) || boundary.optBoolean("inventLawAllowed", false)) ? "Allowed" : "Forbidden");

            JSONObject commercialClass = customerClasses.getJSONObject("commercialInstitutional");
            boolean taxLaunchBetaEnabled = taxReturns.optBoolean("launchBetaEnabled", false);
            int taxLaunchBetaDays = taxReturns.optInt("launchBetaDays", commercialTrialDays);
            String accessWorkflow = "Private citizen release: "
                    + customerClasses.getJSONObject("privateCitizen").optString("releaseRule", "free")
                    + "\nPrivate tax work: "
                    + customerClasses.getJSONObject("privateCitizen").optString("taxWork", "50_percent_of_local_accountant_cost")
                    + "\nLaw enforcement release: "
                    + customerClasses.getJSONObject("lawEnforcement").optString("releaseRule", "free") + " globally"
                    + "\nCommercial release: "
                    + commercialClass.optString("releaseRule", "proof_of_payment_required")
                    + "\nCommercial pricing: "
                    + commercialClass.optString("pricingModel", "20_percent_of_local_lawyer_cost")
                    + "\nCommercial trial: "
                    + (commercialRelease.optBoolean("trialEnabled", true)
                    ? commercialTrialDays + " days from case delivery or recipient access"
                    : "Disabled")
                    + "\nClearMyNumbers tax pricing: private "
                    + taxReturns.optString("privateCitizenPricing", "50_percent_of_local_accountant_cost")
                    + " | business "
                    + taxReturns.optString("businessPricing", "50_percent_of_local_accountant_cost")
                    + "\nClearMyNumbers beta: "
                    + (taxLaunchBetaEnabled
                    ? "free for " + taxLaunchBetaDays + " days while release issues are ironed out"
                    : "disabled")
                    + "\nReceipt ID: "
                    + commercialRelease.optString("receiptIdMode", "sha512")
                    + " | Support uses receipt: "
                    + yesNo(commercialRelease.optBoolean("supportUsesReceiptId", true));

            String reportContract = "Report mode: " + contract.optString("mode", "forensic_report")
                    + "\nTriple verification: " + yesNo(contract.optBoolean("tripleVerificationRequired", true))
                    + "\nContradiction engine: " + yesNo(contract.optBoolean("contradictionEngineRequired", true))
                    + "\nAnchored incidents: " + yesNo(contract.optBoolean("anchoredIncidentsRequired", true))
                    + "\nCourt-style narrative: " + yesNo(contract.optBoolean("courtStyleNarrativeRequired", true));

            return new Snapshot(
                    title,
                    summary,
                    runtimeBoundary,
                    accessWorkflow,
                    reportContract,
                    trialSnapshot.statusLine
            );
        } catch (Exception e) {
            return new Snapshot(
                    "Gemma - Front-facing constitutional legal secretary",
                    "Natural, helpful, plain-language legal support from sealed findings.",
                    "Internet disabled. Raw evidence forbidden. Sealed findings only. No invention of facts or law.",
                    "Private citizen access: free. Law enforcement access: free globally. ClearMyNumbers tax beta: free for 30 days during release stabilisation. After beta, private citizen tax work is 50% of comparable local accountant cost and business tax work becomes paid commercial work. Commercial: proof of payment required after the recipient review window. Receipt ID uses SHA-512.",
                    "Triple verification, contradiction engine, anchored incidents, court-style narrative.",
                    "Commercial review-window status unavailable."
            );
        }
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }
}
