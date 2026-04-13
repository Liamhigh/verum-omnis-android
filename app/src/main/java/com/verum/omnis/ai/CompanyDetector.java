
package com.verum.omnis.ai;

import java.util.Locale;
import java.util.regex.Pattern;

public class CompanyDetector {
    private static final String[] SUFFIXES = new String[]{
        "ltd","pty ltd","(pty) ltd","proprietary limited","llc","inc","corp","corporation","gmbh","ug","sarl","bv","plc",
        "limited","company","co.","s.a.","ag","oy","ab","kft","s.p.a","srl","as","aps","kk","kabushiki kaisha","pte","llp",
        "sas","sl","sa","oyj","nv","sp z o.o.","zrt","spółka","pte. ltd.","pte ltd","bvba","sro","doo","oy ab","pteltd"
    };

    // Very simple registry patterns per jurisdiction (offline, heuristic)
    private static final Pattern UAECOM = Pattern.compile("(?i)(LLC|PJSC|FZE|FZ-LLC|DMCC)\\b");
    private static final Pattern ZARCOM = Pattern.compile("(?i)(\\(Pty\\) Ltd|Pty Ltd|RF)\\b");
    private static final Pattern EUTAGS = Pattern.compile("(?i)(GmbH|S\\.A\\.|SARL|BV|NV|PLC|AB|OY|S\\.r\\.l\\.|S\\.p\\.A)\\b");

    public static boolean looksLikeBusiness(String name) {
        if (name == null) return false;
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (String s : SUFFIXES) {
            if (n.endsWith(" " + s) || n.contains(" " + s + " ")) return true;
        }
        return UAECOM.matcher(n).find() || ZARCOM.matcher(n).find() || EUTAGS.matcher(n).find();
    }
}
