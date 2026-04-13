package com.verum.omnis.rules;

public class VerumRulesRegistry {
    private static volatile byte[] rules;
    private static volatile int version;

    public static void install(byte[] json, int ver) {
        rules = json;
        version = ver;
    }
    public static byte[] get() {
        if (rules == null) throw new IllegalStateException("Rules not installed");
        return rules;
    }
    public static int version() { return version; }
}
