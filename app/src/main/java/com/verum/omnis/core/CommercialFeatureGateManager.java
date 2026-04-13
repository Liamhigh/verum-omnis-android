package com.verum.omnis.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.Locale;

public final class CommercialFeatureGateManager {

    private static final String PREFS = "commercial_feature_gate";
    private static final String KEY_INVESTIGATION_RECEIPT = "investigation_receipt_id";

    private CommercialFeatureGateManager() {}

    public static boolean isInvestigationUnlocked(Context context) {
        return !TextUtils.isEmpty(getInvestigationReceiptId(context));
    }

    public static boolean storeInvestigationReceipt(Context context, String receiptId) {
        String normalized = normalizeReceiptId(receiptId);
        if (TextUtils.isEmpty(normalized)) {
            return false;
        }
        prefs(context)
                .edit()
                .putString(KEY_INVESTIGATION_RECEIPT, normalized)
                .apply();
        return true;
    }

    public static void clearInvestigationReceipt(Context context) {
        prefs(context)
                .edit()
                .remove(KEY_INVESTIGATION_RECEIPT)
                .apply();
    }

    public static String getInvestigationReceiptId(Context context) {
        return prefs(context).getString(KEY_INVESTIGATION_RECEIPT, "");
    }

    public static String getInvestigationReceiptIdShort(Context context) {
        String receipt = getInvestigationReceiptId(context);
        if (TextUtils.isEmpty(receipt)) {
            return "";
        }
        if (receipt.length() <= 12) {
            return receipt;
        }
        return receipt.substring(0, 6) + "..." + receipt.substring(receipt.length() - 4);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String normalizeReceiptId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim()
                .replaceAll("[^A-Za-z0-9._\\-:]", "");
        if (normalized.length() < 8) {
            return "";
        }
        return normalized.toUpperCase(Locale.US);
    }
}
