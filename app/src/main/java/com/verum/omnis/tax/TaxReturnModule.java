package com.verum.omnis.tax;

import com.verum.omnis.core.RulesProvider;
import com.verum.omnis.core.JurisdictionManager;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Locale;

/**
 * Simple tax return estimator based on jurisdiction.
 *
 * <p>This module uses the current jurisdiction code to look up a baseline
 * income tax rate.  It then computes a discounted rate equal to half of the
 * baseline and estimates the tax due for a given income.  The rates are
 * illustrative only and should be replaced with jurisdiction‑specific tax
 * laws for real deployments.</p>
 */
public final class TaxReturnModule {
    public enum CustomerType {
        PRIVATE_INDIVIDUAL,
        BUSINESS
    }

    /** Container for a computed tax return estimate. */
    public static class TaxEstimate {
        public String jurisdiction;
        public String customerType;
        public double baseRate;
        public double discountedRate;
        public double localAccountantCost;
        public double verumServiceCost;
        public double taxDue;
        public boolean launchBetaActive;
        public int launchBetaDays;
        public String pricingRule;
        public String benchmarkRule;
        public String serviceName;
    }

    private static final HashMap<String, Double> BASE_RATES;
    static {
        BASE_RATES = new HashMap<>();
        BASE_RATES.put("ZAF", 0.20); // South Africa (illustrative)
        BASE_RATES.put("UAE", 0.00); // UAE no personal income tax
        BASE_RATES.put("EU",  0.25); // Generic EU baseline
    }

    private TaxReturnModule() {}

    public static TaxEstimate estimate(android.content.Context ctx, double income) {
        return estimate(ctx, income, 0.0, CustomerType.PRIVATE_INDIVIDUAL);
    }

    /**
     * Estimate tax calculations and the ClearMyNumbers service price.
     *
     * <p>The legal/business rule is:
     * private citizens pay nothing except tax work, and tax work is charged at
     * 50% of what a local accountant would charge. Businesses may also use the
     * same tax-return path at 50% of local accountant cost.</p>
     *
     * @param ctx Android context used to derive the current jurisdiction
     * @param income annual taxable income in the local currency
     * @param localAccountantCost the price a local accountant would charge
     * @param customerType whether the estimate is for a private user or business
     * @return a populated {@link TaxEstimate} describing the calculation
     */
    public static TaxEstimate estimate(
            android.content.Context ctx,
            double income,
            double localAccountantCost,
            CustomerType customerType
    ) {
        TaxEstimate est = new TaxEstimate();
        String code = JurisdictionManager.getCurrentJurisdictionCode(ctx);
        if (code == null || code.trim().isEmpty()) code = "ZAF";
        code = code.toUpperCase(Locale.US);
        est.jurisdiction = code;
        est.customerType = customerType.name();
        double base = BASE_RATES.getOrDefault(code, 0.20);
        est.baseRate = base;
        est.discountedRate = base / 2.0;
        est.taxDue = income * est.discountedRate;
        est.localAccountantCost = Math.max(0.0, localAccountantCost);
        TaxPolicy policy = loadPolicy(ctx);
        est.launchBetaActive = policy.launchBetaEnabled;
        est.launchBetaDays = policy.launchBetaDays;
        est.serviceName = policy.serviceName;
        est.benchmarkRule = policy.geographicBenchmark;
        if (policy.launchBetaEnabled) {
            est.verumServiceCost = 0.0;
            est.pricingRule = "launch_beta_free";
        } else {
            est.verumServiceCost = est.localAccountantCost * 0.5;
            est.pricingRule = customerType == CustomerType.PRIVATE_INDIVIDUAL
                    ? policy.privateCitizenPricing
                    : policy.businessPricing;
        }
        return est;
    }

    private static TaxPolicy loadPolicy(android.content.Context ctx) {
        TaxPolicy policy = new TaxPolicy();
        policy.serviceName = "ClearMyNumbers";
        policy.launchBetaEnabled = true;
        policy.launchBetaDays = 30;
        policy.privateCitizenPricing = "50_percent_of_lowest_comparable_local_accountant_cost_in_user_geography";
        policy.businessPricing = "paid_commercial_tax_module_benchmarked_from_local_accountant_cost";
        policy.geographicBenchmark = "same_city_or_nearest_relevant_accounting_market";
        try {
            JSONObject root = new JSONObject(RulesProvider.getBusinessConstitution(ctx));
            JSONObject taxReturnModule = root.optJSONObject("taxReturnModule");
            if (taxReturnModule != null) {
                policy.serviceName = taxReturnModule.optString("serviceName", policy.serviceName);
                policy.launchBetaEnabled = taxReturnModule.optBoolean("launchBetaEnabled", policy.launchBetaEnabled);
                policy.launchBetaDays = taxReturnModule.optInt("launchBetaDays", policy.launchBetaDays);
                policy.privateCitizenPricing = taxReturnModule.optString("privateCitizenPostBetaPricing", policy.privateCitizenPricing);
                policy.businessPricing = taxReturnModule.optString("businessPostBetaPricing", policy.businessPricing);
                policy.geographicBenchmark = taxReturnModule.optString("geographicBenchmark", policy.geographicBenchmark);
            }
        } catch (Exception ignored) {
        }
        return policy;
    }

    private static final class TaxPolicy {
        String serviceName;
        boolean launchBetaEnabled;
        int launchBetaDays;
        String privateCitizenPricing;
        String businessPricing;
        String geographicBenchmark;
    }
}
