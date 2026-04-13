package com.verum.omnis.core

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

data class ModelAuditLogEntry(
    val timestampIso: String,
    val caseId: String,
    val model: String,
    val role: String,
    val inputHash: String,
    val outputHash: String,
    val success: Boolean,
    val latencyMs: Long,
    val promptVersion: String,
    val fallbackUsed: Boolean,
    val notes: List<String>
) {
    companion object {
        @JvmStatic
        fun fromJson(json: JSONObject): ModelAuditLogEntry {
            val notesJson = json.optJSONArray("notes")
            val notes = mutableListOf<String>()
            if (notesJson != null) {
                for (index in 0 until notesJson.length()) {
                    val note = notesJson.optString(index, "").trim()
                    if (note.isNotEmpty()) {
                        notes += note
                    }
                }
            }
            return ModelAuditLogEntry(
                timestampIso = json.optString("timestampIso", ""),
                caseId = json.optString("caseId", ""),
                model = json.optString("model", ""),
                role = json.optString("role", ""),
                inputHash = json.optString("inputHash", ""),
                outputHash = json.optString("outputHash", ""),
                success = json.optBoolean("success", false),
                latencyMs = json.optLong("latencyMs", 0L),
                promptVersion = json.optString("promptVersion", ""),
                fallbackUsed = json.optBoolean("fallbackUsed", false),
                notes = notes
            )
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("timestampIso", timestampIso)
        put("caseId", caseId)
        put("model", model)
        put("role", role)
        put("inputHash", inputHash)
        put("outputHash", outputHash)
        put("success", success)
        put("latencyMs", latencyMs)
        put("promptVersion", promptVersion)
        put("fallbackUsed", fallbackUsed)
        put("notes", org.json.JSONArray(notes))
    }
}

object ModelAuditLogger {
    private const val DIR_NAME = "model_audit"

    @JvmStatic
    fun log(context: Context, entry: ModelAuditLogEntry): File? {
        return try {
            val dir = File(context.filesDir, DIR_NAME)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(
                dir,
                "${entry.caseId.ifBlank { "unknown-case" }}-${entry.role.lowercase()}-${UUID.randomUUID()}.json"
            )
            FileOutputStream(file).use { fos ->
                fos.write(entry.toJson().toString(2).toByteArray(StandardCharsets.UTF_8))
            }
            file
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun readEntries(context: Context?, caseId: String): List<ModelAuditLogEntry> {
        if (context == null || caseId.isBlank()) {
            return emptyList()
        }
        val dir = File(context.applicationContext.filesDir, DIR_NAME)
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }
        return dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.mapNotNull { file ->
                try {
                    val json = JSONObject(file.readText(StandardCharsets.UTF_8))
                    val entry = ModelAuditLogEntry.fromJson(json)
                    if (entry.caseId == caseId) entry else null
                } catch (_: Throwable) {
                    null
                }
            }
            ?.sortedBy { it.timestampIso }
            ?.toList()
            ?: emptyList()
    }

    @JvmStatic
    fun buildVisibleAppendix(context: Context?, caseId: String): String {
        val entries = readEntries(context, caseId)
        if (entries.isEmpty()) {
            return ""
        }
        val renderEntry = entries.lastOrNull { it.role.startsWith("REPORT_RENDER") }
        val auditEntry = entries.lastOrNull { it.role == "REPORT_AUDIT" }
        return buildString {
            append("BOUNDED MODEL RENDERING RECORD\n")
            append("Human brief rendered from governed publication-safe bundle only.\n")
            if (renderEntry != null) {
                append("Render model: ").append(renderEntry.model).append("\n")
                append("Render prompt version: ").append(renderEntry.promptVersion).append("\n")
                append("Render input hash: ").append(renderEntry.inputHash).append("\n")
                append("Render output hash: ").append(renderEntry.outputHash).append("\n")
                append("Render latency ms: ").append(renderEntry.latencyMs).append("\n")
            }
            if (auditEntry != null) {
                append("Audit model: ").append(auditEntry.model).append("\n")
                append("Audit prompt version: ").append(auditEntry.promptVersion).append("\n")
                append("Audit passed: ").append(if (auditEntry.success) "PASS" else "FAIL").append("\n")
                append("Audit fallback triggered: ").append(if (auditEntry.fallbackUsed) "YES" else "NO").append("\n")
            }
            val latest = entries.last()
            append("Latest bounded run at: ").append(latest.timestampIso).append("\n")
            append("Case ID: ").append(latest.caseId)
        }.trim()
    }

    @JvmStatic
    fun buildLedgerJson(context: Context?, caseId: String): String {
        val entries = readEntries(context, caseId)
        if (entries.isEmpty()) {
            return ""
        }
        val root = JSONObject()
        root.put("caseId", caseId)
        root.put("entries", org.json.JSONArray(entries.map { it.toJson() }))
        root.put("summary", buildVisibleAppendix(context, caseId))
        return root.toString(2)
    }

    @JvmStatic
    fun buildEntry(
        caseId: String,
        model: String,
        role: String,
        inputJson: String,
        outputText: String,
        success: Boolean,
        latencyMs: Long,
        promptVersion: String,
        fallbackUsed: Boolean,
        notes: List<String>
    ): ModelAuditLogEntry {
        return ModelAuditLogEntry(
            timestampIso = Instant.now().toString(),
            caseId = caseId,
            model = model,
            role = role,
            inputHash = sha512String(inputJson),
            outputHash = sha512String(outputText),
            success = success,
            latencyMs = latencyMs,
            promptVersion = promptVersion,
            fallbackUsed = fallbackUsed,
            notes = notes
        )
    }

    private fun sha512String(value: String): String {
        return try {
            HashUtil.sha512(value.toByteArray(StandardCharsets.UTF_8))
        } catch (_: Throwable) {
            ""
        }
    }
}
