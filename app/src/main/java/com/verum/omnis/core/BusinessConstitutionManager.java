package com.verum.omnis.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

public final class BusinessConstitutionManager {

    public static final class Snapshot {
        public final String identityName;
        public final String operator;
        public final String status;
        public final String assetValueLabel;
        public final String courtValidationLabel;
        public final String citizenAccess;
        public final String commercialTrialPolicy;
        public final String commercialTrialStatus;
        public final String institutionalModel;
        public final String recoveryModel;
        public final String taxModel;
        public final String taxLaunchPolicy;
        public final String activeCasesLabel;
        public final boolean acknowledgementRequired;

        public Snapshot(
                String identityName,
                String operator,
                String status,
                String assetValueLabel,
                String courtValidationLabel,
                String citizenAccess,
                String commercialTrialPolicy,
                String commercialTrialStatus,
                String institutionalModel,
                String recoveryModel,
                String taxModel,
                String taxLaunchPolicy,
                String activeCasesLabel,
                boolean acknowledgementRequired
        ) {
            this.identityName = identityName;
            this.operator = operator;
            this.status = status;
            this.assetValueLabel = assetValueLabel;
            this.courtValidationLabel = courtValidationLabel;
            this.citizenAccess = citizenAccess;
            this.commercialTrialPolicy = commercialTrialPolicy;
            this.commercialTrialStatus = commercialTrialStatus;
            this.institutionalModel = institutionalModel;
            this.recoveryModel = recoveryModel;
            this.taxModel = taxModel;
            this.taxLaunchPolicy = taxLaunchPolicy;
            this.activeCasesLabel = activeCasesLabel;
            this.acknowledgementRequired = acknowledgementRequired;
        }
    }

    private BusinessConstitutionManager() {}

    public static Snapshot load(Context context) {
        try {
            JSONObject root = new JSONObject(RulesProvider.getBusinessConstitution(context));
            JSONObject identity = root.getJSONObject("identity");
            JSONObject legalValidation = root.getJSONObject("legalValidation");
            JSONObject southAfrica = legalValidation.getJSONObject("southAfrica");
            JSONObject asset = root.getJSONObject("asset");
            JSONObject revenue = root.getJSONObject("revenueModel");
            JSONObject commercialAccessPolicy = root.optJSONObject("commercialAccessPolicy");
            JSONObject taxReturnModule = root.optJSONObject("taxReturnModule");
            JSONObject activeCases = root.getJSONObject("activeCases");
            JSONObject acknowledgement = root.getJSONObject("acknowledgement");

            String activeCaseText = summarizeCases(activeCases);
            String assetValue = "$" + String.format("%,d", asset.optLong("declaredAssetValueUSD", 0L));
            String courtValidation = southAfrica.optString("court", "Port Shepstone Magistrate's Court")
                    + " "
                    + southAfrica.optString("case", "H208/25")
                    + " - "
                    + southAfrica.optString("result", "forensic evidence accepted");
            int trialDays = commercialAccessPolicy != null
                    ? commercialAccessPolicy.optInt("trialDays", 30)
                    : 30;
            CommercialAccessManager.Snapshot trialSnapshot =
                    CommercialAccessManager.load(context, trialDays);
            String trialPolicy = revenue.optString(
                    "commercialTrial",
                    "Commercial and institutional recipients receive a 30-day review window from case delivery or recipient access."
            );
            String taxModel = revenue.optString("taxReturns", "Tax return pricing undefined");
            String taxLaunchPolicy = buildTaxLaunchPolicy(taxReturnModule);

            return new Snapshot(
                    identity.optString("name", "Verum Omnis"),
                    identity.optString("operator", "Unknown"),
                    identity.optString("status", "Status unavailable"),
                    assetValue,
                    courtValidation,
                    revenue.optString("citizenAccess", "Citizen access undefined"),
                    trialPolicy,
                    trialSnapshot.statusLine,
                    revenue.optString("institutionalLicensing", "Institutional model undefined"),
                    revenue.optString("fraudRecovery", "Recovery model undefined"),
                    taxModel,
                    taxLaunchPolicy,
                    activeCaseText,
                    acknowledgement.optBoolean("required", false)
            );
        } catch (Exception e) {
            return new Snapshot(
                "Verum Omnis",
                "Unknown",
                "Business constitution unavailable",
                "$0",
                "Port Shepstone Magistrate's Court H208/25",
                "Citizen access undefined",
                "Commercial trial policy unavailable",
                "Commercial trial status unavailable",
                "Institutional model undefined",
                "Recovery model undefined",
                "Tax return pricing undefined",
                "Tax module launch policy unavailable",
                "No active case summary",
                false
            );
        }
    }

    private static String buildTaxLaunchPolicy(JSONObject taxReturnModule) {
        if (taxReturnModule == null) {
            return "Tax module launch policy unavailable";
        }
        boolean betaEnabled = taxReturnModule.optBoolean("launchBetaEnabled", false);
        int betaDays = taxReturnModule.optInt("launchBetaDays", 30);
        String serviceName = taxReturnModule.optString("serviceName", "ClearMyNumbers");
        String citizenPricing = taxReturnModule.optString(
                "privateCitizenPostBetaPricing",
                "50_percent_of_lowest_comparable_local_accountant_cost_in_user_geography"
        );
        String businessPricing = taxReturnModule.optString(
                "businessPostBetaPricing",
                "paid_commercial_tax_module_benchmarked_from_local_accountant_cost"
        );
        if (betaEnabled) {
            return serviceName
                    + " is in a "
                    + betaDays
                    + "-day free release beta. After beta, private citizens move to "
                    + citizenPricing
                    + " and businesses move to "
                    + businessPricing
                    + ".";
        }
        return serviceName
                + " launch beta is disabled. Private citizens use "
                + citizenPricing
                + " and businesses use "
                + businessPricing
                + ".";
    }

    private static String summarizeCases(JSONObject activeCases) {
        StringBuilder sb = new StringBuilder();
        sb.append("Port Shepstone Magistrate's Court H208/25 (Forensic evidence accepted)");
        appendCaseList(sb, activeCases.optJSONArray("southAfrica"));
        appendCaseList(sb, activeCases.optJSONArray("uae"));
        appendCaseList(sb, activeCases.optJSONArray("international"));
        return sb.length() == 0 ? "No active cases listed" : sb.toString();
    }

    private static void appendCaseList(StringBuilder sb, JSONArray array) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(item.optString("authority", "Authority"))
                    .append(" ")
                    .append(item.optString("reference", "Reference"))
                    .append(" ")
                    .append("(")
                    .append(item.optString("status", "Status"))
                    .append(")");
        }
    }
}
