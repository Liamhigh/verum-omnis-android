package com.verum.omnis.core

import android.content.Context

data class BoundedRenderSettings(
    val boundedHumanBriefEnabled: Boolean = false,
    val boundedPoliceSummaryEnabled: Boolean = false,
    val boundedLegalStandingEnabled: Boolean = false,
    val boundedRenderAuditRequired: Boolean = true,
    val boundedRenderFailClosed: Boolean = true
) {
    fun persist(context: Context?) {
        if (context == null) {
            return
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HUMAN_BRIEF, boundedHumanBriefEnabled)
            .putBoolean(KEY_POLICE_SUMMARY, boundedPoliceSummaryEnabled)
            .putBoolean(KEY_LEGAL_STANDING, boundedLegalStandingEnabled)
            .putBoolean(KEY_AUDIT_REQUIRED, boundedRenderAuditRequired)
            .putBoolean(KEY_FAIL_CLOSED, boundedRenderFailClosed)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "bounded_render_settings"
        private const val KEY_HUMAN_BRIEF = "boundedHumanBriefEnabled"
        private const val KEY_POLICE_SUMMARY = "boundedPoliceSummaryEnabled"
        private const val KEY_LEGAL_STANDING = "boundedLegalStandingEnabled"
        private const val KEY_AUDIT_REQUIRED = "boundedRenderAuditRequired"
        private const val KEY_FAIL_CLOSED = "boundedRenderFailClosed"

        @JvmStatic
        fun load(context: Context?): BoundedRenderSettings {
            if (context == null) {
                return BoundedRenderSettings()
            }
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return BoundedRenderSettings(
                boundedHumanBriefEnabled = prefs.getBoolean(KEY_HUMAN_BRIEF, false),
                boundedPoliceSummaryEnabled = prefs.getBoolean(KEY_POLICE_SUMMARY, false),
                boundedLegalStandingEnabled = prefs.getBoolean(KEY_LEGAL_STANDING, false),
                boundedRenderAuditRequired = prefs.getBoolean(KEY_AUDIT_REQUIRED, true),
                boundedRenderFailClosed = prefs.getBoolean(KEY_FAIL_CLOSED, true)
            )
        }
    }
}
