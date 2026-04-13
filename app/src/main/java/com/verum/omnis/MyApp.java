package com.verum.omnis;

import android.app.Application;
import com.verum.omnis.security.ConstitutionGate;

public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ConstitutionGate.VerificationResult res = ConstitutionGate.verifyAll(this);
        if (!res.ok) {
            throw new IllegalStateException("HARD_STOP: INTEGRITY_BREACH => " + res.details.toString());
        }
    }
}