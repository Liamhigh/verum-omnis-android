
package com.verum.omnis.core;

import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class CurrencyConverter {
    private static JSONObject table;

    private static void ensure(Context ctx) throws Exception {
        if (table != null) return;
        InputStream is = ctx.getAssets().open("config/fx_table.json");
        // Read the stream manually into a byte array.  Avoid InputStream.readAllBytes()
        // which is unavailable on Android API levels below 26.
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            byte[] b = bos.toByteArray();
            table = new JSONObject(new String(b, StandardCharsets.UTF_8));
        } finally {
            is.close();
        }
    }

    public static double toBase(Context ctx, String currency, double amount) {
        try {
            ensure(ctx);
            String base = table.optString("base", "USD").toUpperCase(Locale.ROOT);
            JSONObject rates = table.optJSONObject("rates");
            if (rates == null) return amount;
            double rate = 1.0;
            if (currency == null) return amount;
            String cur = currency.toUpperCase(Locale.ROOT);
            if (rates.has(cur)) {
                double r = rates.optDouble(cur, 1.0);
                // rates are "units per USD" or "USD per unit"? We stored USD base => rate is units per USD.
                // If 1 USD = 18.50 ZAR, then amount in ZAR / 18.50 = USD.
                if (!"USD".equals(cur)) {
                    return amount / r;
                } else {
                    return amount;
                }
            }
            return amount;
        } catch (Exception e) {
            return amount;
        }
    }
}
