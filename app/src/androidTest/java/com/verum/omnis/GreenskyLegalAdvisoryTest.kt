package com.verum.omnis

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.verum.legal.LegalGrounding
import com.verum.omnis.core.AnalysisEngine
import com.verum.omnis.core.JurisdictionManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class GreenskyLegalAdvisoryTest {

    @Test(timeout = 1_200_000)
    fun greenskyLegalGroundingUsesMixedJurisdictionPacks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val assetContext = instrumentation.context

        JurisdictionManager.initialize(context, "MULTI")
        val evidenceFile = copyAssetToInternalStorage(context, assetContext, "greensky_case.pdf")
        val report = AnalysisEngine.analyze(context, evidenceFile)

        assertEquals("MULTI", report.jurisdiction)
        assertEquals("Multi-jurisdiction (UAE and South Africa)", report.jurisdictionName)

        val grounding = LegalGrounding(context)
        val promptPackage = grounding.buildPromptPackage(report)

        assertEquals("MULTI", promptPackage.pack.code)
        assertTrue(promptPackage.prompt.contains(report.caseId))
        assertTrue(promptPackage.prompt.contains(report.evidenceHash))
        assertTrue(
            "Expected UAE legal materials in mixed-jurisdiction retrieval",
            promptPackage.docs.any {
                it.id.lowercase().startsWith("uae-")
                        || it.source.contains("UAE", ignoreCase = true)
                        || it.title.contains("UAE", ignoreCase = true)
            }
        )
        assertTrue(
            "Expected South Africa legal materials in mixed-jurisdiction retrieval",
            promptPackage.docs.any {
                it.id.lowercase().startsWith("zaf-")
                        || it.source.contains("South Africa", ignoreCase = true)
                        || it.title.contains("South Africa", ignoreCase = true)
            }
        )
        assertFalse(
            "Grounding prompt should not embed raw document blocks",
            promptPackage.prompt.contains("documentTextBlocks", ignoreCase = true)
        )
        val artifact = writeDebugArtifact(context, report, promptPackage)
        assertTrue(artifact.exists())
        assertTrue(artifact.length() > 0L)
    }

    private fun copyAssetToInternalStorage(
        context: Context,
        assetContext: Context,
        assetPath: String
    ): File {
        val destFile = File(context.filesDir, assetPath.substringAfterLast('/'))
        assetContext.assets.open(assetPath).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    private fun writeDebugArtifact(
        context: Context,
        report: AnalysisEngine.ForensicReport,
        promptPackage: LegalGrounding.PromptPackage
    ): File {
        val outDir = File(context.cacheDir, "gemma-regression")
        outDir.mkdirs()
        val outFile = File(outDir, "greensky-legal.json")

        val root = JSONObject()
        root.put("caseId", report.caseId)
        root.put("evidenceHash", report.evidenceHash)
        root.put("jurisdiction", report.jurisdiction)
        root.put("jurisdictionName", report.jurisdictionName)
        root.put("advisoryText", "grounding-validation-only")
        root.put("grounding", buildGroundingJson(promptPackage))

        outFile.writeText(root.toString(2), StandardCharsets.UTF_8)
        return outFile
    }

    private fun buildGroundingJson(promptPackage: LegalGrounding.PromptPackage): JSONObject {
        val root = JSONObject()
        root.put("caseId", promptPackage.data.caseId)
        root.put("evidenceHash", promptPackage.data.evidenceHash)
        root.put("jurisdictionCode", promptPackage.pack.code)
        root.put("jurisdictionName", promptPackage.pack.name)
        root.put("guardianApproved", promptPackage.data.guardianApproved)
        root.put("guardianReason", promptPackage.data.guardianReason)
        root.put("summary", promptPackage.data.summary)
        root.put("topLiabilities", JSONArray(promptPackage.data.topLiabilities))
        root.put("legalReferences", JSONArray(promptPackage.data.legalReferences))
        root.put("diagnostics", JSONArray(promptPackage.data.diagnostics))
        root.put("retrievalQueries", JSONArray(promptPackage.queries))

        val certifiedFindings = JSONArray()
        for (finding in promptPackage.data.certifiedFindings) {
            val item = JSONObject()
            item.put("summary", finding.summary)
            item.put("excerpt", finding.excerpt)
            item.put("anchor", finding.anchor)
            certifiedFindings.put(item)
        }
        root.put("certifiedFindings", certifiedFindings)

        val docs = JSONArray()
        for (doc in promptPackage.docs) {
            val item = JSONObject()
            item.put("id", doc.id)
            item.put("title", doc.title)
            item.put("category", doc.category)
            item.put("source", doc.source)
            item.put("rank", doc.rank)
            item.put("score", doc.score)
            docs.put(item)
        }
        root.put("retrievedDocuments", docs)
        return root
    }
}
