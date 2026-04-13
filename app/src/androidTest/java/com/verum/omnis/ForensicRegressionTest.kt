package com.verum.omnis

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.verum.omnis.core.AnalysisEngine
import com.verum.omnis.forensic.ForensicPackageWriter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class ForensicRegressionTest {

    @Test(timeout = 900000)
    fun tdpiFixtureMatchesBenchmark() {
        runFixture(TDPI_FIXTURE)
    }

    @Test(timeout = 900000)
    fun greenskyFixtureMatchesBenchmark() {
        runFixture(GREENSKY_FIXTURE)
    }

    @Test(timeout = 900000)
    fun simpleContractFixtureMatchesBenchmark() {
        runFixture(SIMPLE_CONTRACT_FIXTURE)
    }

    private fun runFixture(fixture: Fixture) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val benchmark = loadJsonAsset(instrumentation.context, fixture.benchmarkAssetName)
        val evidenceFile = copyAssetToInternalStorage(
            context,
            instrumentation.context,
            fixture.pdfAssetName
        )
        val report = AnalysisEngine.analyze(context, evidenceFile)
        val payload = ForensicPackageWriter.buildPayload(evidenceFile, report, null)
        writeDebugPayload(context, fixture, payload)

        assertSchemaCriticals(fixture, payload, benchmark)
        assertNamedParties(fixture, payload, benchmark)
        assertSignalTerms(fixture, payload, benchmark)
        assertPrimaryEvidenceRegisters(fixture, payload, benchmark)
        assertFindingTypes(fixture, payload, benchmark)
        assertRichOracle(fixture, payload, benchmark)
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
        assertTrue("Failed to stage TDPI test asset into internal storage", destFile.exists() && destFile.length() > 0L)
        return destFile
    }

    private fun assertSchemaCriticals(fixture: Fixture, payload: JSONObject, benchmark: JSONObject) {
        val guardianDecision = payload.optJSONObject("guardianDecision")
        val tripleVerification = payload.optJSONObject("tripleVerification")
        val certifiedFindings = payload.optJSONArray("certifiedFindings")
        val forensicSynthesis = payload.optJSONObject("forensicSynthesis")
        val brainAnalysis = payload.optJSONObject("brainAnalysis")
        val consensus = brainAnalysis?.optJSONObject("consensus")
        val b1Synthesis = brainAnalysis?.optJSONObject("b1Synthesis")

        assertNotNull("${fixture.name}: guardianDecision should be present in payload", guardianDecision)
        assertNotNull("${fixture.name}: tripleVerification should be present in payload", tripleVerification)
        assertNotNull("${fixture.name}: certifiedFindings should be present in payload", certifiedFindings)
        assertNotNull("${fixture.name}: forensicSynthesis should be present in payload", forensicSynthesis)
        assertNotNull("${fixture.name}: brainAnalysis.consensus should be present in payload", consensus)
        assertNotNull("${fixture.name}: brainAnalysis.b1Synthesis should be present in payload", b1Synthesis)
        assertNotNull("${fixture.name}: tripleVerification.thesis should be present", tripleVerification?.optJSONObject("thesis"))
        assertNotNull("${fixture.name}: tripleVerification.antithesis should be present", tripleVerification?.optJSONObject("antithesis"))
        assertNotNull("${fixture.name}: tripleVerification.synthesis should be present", tripleVerification?.optJSONObject("synthesis"))
        assertNotNull("${fixture.name}: tripleVerification.overall should be present", tripleVerification?.optJSONObject("overall"))
        assertNotNull("${fixture.name}: forensicSynthesis.crossBrainContradictions should be present", forensicSynthesis?.optJSONArray("crossBrainContradictions"))
        assertNotNull("${fixture.name}: forensicSynthesis.actorDishonestyScores should be present", forensicSynthesis?.optJSONArray("actorDishonestyScores"))
        assertNotNull("${fixture.name}: forensicSynthesis.actorHeatmap should be present", forensicSynthesis?.optJSONObject("actorHeatmap"))
        assertNotNull("${fixture.name}: forensicSynthesis.promotionSupportMatrix should be present", forensicSynthesis?.optJSONArray("promotionSupportMatrix"))
        assertNotNull("${fixture.name}: forensicSynthesis.wrongfulActorProfile should be present", forensicSynthesis?.optJSONObject("wrongfulActorProfile"))

        val expectedGuardianApproved = when {
            benchmark.has("guardianApproved") -> benchmark.optBoolean("guardianApproved", false)
            benchmark.optJSONObject("guardianDecision")?.has("approved") == true ->
                benchmark.optJSONObject("guardianDecision")?.optBoolean("approved", false) ?: false
            else -> null
        }
        if (expectedGuardianApproved != null) {
            val actualGuardianApproved = guardianDecision?.optBoolean("approved", false) ?: false
            assertEquals("${fixture.name}: guardian approval mismatch", expectedGuardianApproved, actualGuardianApproved)
        }

        val actualCertifiedCount = certifiedFindings?.length() ?: 0
        val expectedCertifiedCount = when {
            benchmark.has("certifiedFindingsCount") -> benchmark.optInt("certifiedFindingsCount", 0)
            benchmark.optJSONArray("certifiedFindings") != null -> benchmark.optJSONArray("certifiedFindings")?.length() ?: 0
            else -> null
        }
        if (expectedCertifiedCount != null) {
            assertEquals("${fixture.name}: certified findings count mismatch", expectedCertifiedCount, actualCertifiedCount)
        }
        if (benchmark.has("minimumCertifiedFindingsCount")) {
            val minimumCertifiedCount = benchmark.optInt("minimumCertifiedFindingsCount", 0)
            assertTrue(
                "${fixture.name}: certified findings count should be at least $minimumCertifiedCount but was $actualCertifiedCount",
                actualCertifiedCount >= minimumCertifiedCount
            )
        }

        val verifiedFindingCount = consensus?.optInt("verifiedFindingCount", -1) ?: -1
        assertEquals(
            "${fixture.name}: consensus verifiedFindingCount should match certifiedFindings length",
            actualCertifiedCount,
            verifiedFindingCount
        )
    }

    private fun assertNamedParties(fixture: Fixture, payload: JSONObject, benchmark: JSONObject) {
        val extraction = payload.optJSONObject("constitutionalExtraction")
        val namedParties = extraction?.optJSONArray("namedParties") ?: JSONArray()
        val actualNames = extractNames(namedParties)

        for (expected in benchmark.optJSONArray("expectedNamedParties").orEmptyStrings()) {
            assertTrue(
                "${fixture.name}: expected named party missing: $expected. Actual named parties: $actualNames",
                actualNames.any { it.equals(expected, ignoreCase = true) }
            )
        }

        for (forbidden in benchmark.optJSONArray("forbiddenNamedParties").orEmptyStrings()) {
            assertFalse(
                "${fixture.name}: forbidden named party leaked into output: $forbidden. Actual named parties: $actualNames",
                actualNames.any { it.equals(forbidden, ignoreCase = true) }
            )
        }

        val actorTexts = collectActorFields(payload)
        for (forbidden in benchmark.optJSONArray("forbiddenNamedParties").orEmptyStrings()) {
            assertFalse(
                "${fixture.name}: forbidden actor leakage still present in finding actors: $forbidden. Actual actor fields: $actorTexts",
                actorTexts.any { it.equals(forbidden, ignoreCase = true) }
            )
        }
    }

    private fun assertSignalTerms(fixture: Fixture, payload: JSONObject, benchmark: JSONObject) {
        val primaryText = flattenRegisters(
            payload.optJSONObject("constitutionalExtraction"),
            benchmark.optJSONArray("primaryEvidenceRegisters").orEmptyStrings()
        )
        val allPayloadText = flattenJson(payload).lowercase()

        for (term in benchmark.optJSONArray("requiredSignalTerms").orEmptyStrings()) {
            val normalized = term.lowercase()
            assertTrue(
                "${fixture.name}: expected signal term missing: $term",
                primaryText.contains(normalized) || allPayloadText.contains(normalized)
            )
        }
    }

    private fun assertPrimaryEvidenceRegisters(fixture: Fixture, payload: JSONObject, benchmark: JSONObject) {
        val extraction = payload.optJSONObject("constitutionalExtraction")
        val primaryText = flattenRegisters(
            extraction,
            benchmark.optJSONArray("primaryEvidenceRegisters").orEmptyStrings()
        )

        assertTrue(
            "${fixture.name}: primary evidence registers should not be empty",
            primaryText.isNotBlank()
        )

        for (term in benchmark.optJSONArray("forbiddenPrimaryEvidenceTerms").orEmptyStrings()) {
            val normalized = term.lowercase()
            assertFalse(
                "${fixture.name}: synthetic or narrative term leaked into primary evidence registers: $term",
                primaryText.contains(normalized)
            )
        }
    }

    private fun assertFindingTypes(fixture: Fixture, payload: JSONObject, benchmark: JSONObject) {
        val findings = payload.optJSONArray("findings") ?: JSONArray()
        val certifiedFindings = payload.optJSONArray("certifiedFindings") ?: JSONArray()
        val findingTypes = collectFindingTypes(findings)
        val certifiedTypes = collectFindingTypes(certifiedFindings)

        for (required in benchmark.optJSONArray("requiredFindingTypes").orEmptyStrings()) {
            assertTrue(
                "${fixture.name}: required finding type missing: $required. Actual types: $findingTypes",
                findingTypes.any { it.equals(required, ignoreCase = true) }
            )
        }
        for (required in benchmark.optJSONArray("requiredCertifiedFindingTypes").orEmptyStrings()) {
            assertTrue(
                "${fixture.name}: required certified finding type missing: $required. Actual certified types: $certifiedTypes",
                certifiedTypes.any { it.equals(required, ignoreCase = true) }
            )
        }
    }

    private fun assertRichOracle(fixture: Fixture, payload: JSONObject, benchmark: JSONObject) {
        val expectedTripleStatus = benchmark
            .optJSONObject("tripleVerification")
            ?.optJSONObject("overall")
            ?.optString("status", "")
            ?.trim()
            .orEmpty()
        if (expectedTripleStatus.isNotEmpty()) {
            val actualTripleStatus = payload
                .optJSONObject("tripleVerification")
                ?.optJSONObject("overall")
                ?.optString("status", "")
                ?.trim()
                .orEmpty()
            assertEquals("${fixture.name}: tripleVerification overall status mismatch", expectedTripleStatus, actualTripleStatus)
        }

        val expectedSynthesis = benchmark.optJSONObject("forensicSynthesis") ?: return
        val actualSynthesis = payload.optJSONObject("forensicSynthesis") ?: JSONObject()

        val expectedWrongfulActor = expectedSynthesis
            .optJSONObject("wrongfulActorProfile")
            ?.optString("actor", "")
            ?.trim()
            .orEmpty()
        if (expectedWrongfulActor.isNotEmpty()) {
            val actualWrongfulActor = actualSynthesis
                .optJSONObject("wrongfulActorProfile")
                ?.optString("actor", "")
                ?.trim()
                .orEmpty()
            assertEquals("${fixture.name}: wrongful actor profile mismatch", expectedWrongfulActor, actualWrongfulActor)
        }

        val expectedContradictions = expectedSynthesis.optJSONArray("crossBrainContradictions") ?: JSONArray()
        val actualContradictions = actualSynthesis.optJSONArray("crossBrainContradictions") ?: JSONArray()
        for (index in 0 until expectedContradictions.length()) {
            val expected = expectedContradictions.optJSONObject(index) ?: continue
            val actor = expected.optString("actor", "").trim()
            val contradictionType = expected.optString("contradictionType", "").trim()
            if (actor.isEmpty() || contradictionType.isEmpty()) {
                continue
            }
            assertTrue(
                "${fixture.name}: missing cross-brain contradiction for $actor / $contradictionType",
                containsContradiction(actualContradictions, actor, contradictionType)
            )
        }

        val expectedScoreActors = collectJsonStringField(expectedSynthesis.optJSONArray("actorDishonestyScores"), "actor")
        val actualScoreActors = collectJsonStringField(actualSynthesis.optJSONArray("actorDishonestyScores"), "actor")
        for (actor in expectedScoreActors) {
            assertTrue(
                "${fixture.name}: missing actor dishonesty score for $actor. Actual actors: $actualScoreActors",
                actualScoreActors.any { it.equals(actor, ignoreCase = true) }
            )
        }

        val expectedCertified = benchmark.optJSONArray("certifiedFindings") ?: JSONArray()
        val actualCertified = payload.optJSONArray("certifiedFindings") ?: JSONArray()
        for (index in 0 until expectedCertified.length()) {
            val expected = expectedCertified.optJSONObject(index) ?: continue
            val type = expected.optString("type", "").trim()
            val actor = expected.optString("actor", "").trim()
            if (type.isEmpty()) {
                continue
            }
            assertTrue(
                "${fixture.name}: missing certified finding for type=$type actor=$actor",
                containsFinding(actualCertified, type, actor)
            )
        }
    }

    private fun collectActorFields(payload: JSONObject): List<String> {
        val output = linkedSetOf<String>()
        collectActorFieldsRecursive(payload, output)
        return output.toList()
    }

    private fun collectActorFieldsRecursive(value: Any?, output: MutableSet<String>) {
        when (value) {
            is JSONObject -> {
                val actor = value.optString("actor", "").trim()
                if (actor.isNotEmpty()) {
                    output.add(actor)
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    collectActorFieldsRecursive(value.opt(keys.next()), output)
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    collectActorFieldsRecursive(value.opt(index), output)
                }
            }
        }
    }

    private fun flattenRegisters(extraction: JSONObject?, registerNames: List<String>): String {
        if (extraction == null) {
            return ""
        }
        val sb = StringBuilder()
        for (register in registerNames) {
            sb.append(' ').append(flattenJson(extraction.opt(register)))
        }
        return sb.toString().lowercase()
    }

    private fun flattenJson(value: Any?): String {
        val sb = StringBuilder()
        flattenInto(sb, value)
        return sb.toString()
    }

    private fun flattenInto(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> return
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    flattenInto(sb, value.opt(keys.next()))
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    flattenInto(sb, value.opt(index))
                }
            }
            else -> {
                sb.append(' ').append(value.toString())
            }
        }
    }

    private fun extractNames(namedParties: JSONArray): List<String> {
        val names = mutableListOf<String>()
        for (index in 0 until namedParties.length()) {
            val item = namedParties.optJSONObject(index) ?: continue
            val name = item.optString("name", "").trim()
            if (name.isNotEmpty()) {
                names.add(name)
            }
        }
        return names
    }

    private fun collectStringField(array: JSONArray, fieldName: String): List<String> {
        val values = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val value = item.optString(fieldName, "").trim()
            if (value.isNotEmpty()) {
                values.add(value)
            }
        }
        return values
    }

    private fun collectFindingTypes(array: JSONArray): List<String> {
        val values = linkedSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val direct = item.optString("type", "").trim()
            if (direct.isNotEmpty()) {
                values.add(direct)
            }
            val nestedFinding = item.optJSONObject("finding")
            if (nestedFinding != null) {
                val nestedType = nestedFinding.optString("findingType", nestedFinding.optString("conflictType", "")).trim()
                if (nestedType.equals("PROPOSITION_CONFLICT", ignoreCase = true)) {
                    values.add("CONTRADICTION")
                } else if (nestedType.isNotEmpty()) {
                    values.add(nestedType)
                }
            }
        }
        return values.toList()
    }

    private fun collectJsonStringField(array: JSONArray?, fieldName: String): List<String> {
        if (array == null) {
            return emptyList()
        }
        val values = linkedSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val value = item.optString(fieldName, "").trim()
            if (value.isNotEmpty()) {
                values.add(value)
            }
        }
        return values.toList()
    }

    private fun containsContradiction(array: JSONArray, actor: String, contradictionType: String): Boolean {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (item.optString("actor", "").equals(actor, ignoreCase = true) &&
                item.optString("contradictionType", "").equals(contradictionType, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    private fun containsFinding(array: JSONArray, type: String, actor: String): Boolean {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val typeMatches = item.optString("type", "").equals(type, ignoreCase = true)
            if (!typeMatches) {
                continue
            }
            if (actor.isBlank() || item.optString("actor", "").equals(actor, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun loadJsonAsset(context: Context, name: String): JSONObject {
        context.assets.open(name).use { input ->
            val text = input.readBytes().toString(StandardCharsets.UTF_8)
            return JSONObject(text)
        }
    }

    private fun writeDebugPayload(context: Context, fixture: Fixture, payload: JSONObject) {
        val outDir = File(context.cacheDir, "forensic-regression")
        outDir.mkdirs()
        val out = File(outDir, "${fixture.name}-latest.json")
        out.writeText(payload.toString(2), StandardCharsets.UTF_8)
    }

    private fun JSONArray?.orEmptyStrings(): List<String> {
        if (this == null) {
            return emptyList()
        }
        val values = mutableListOf<String>()
        for (index in 0 until length()) {
            val value = optString(index, "").trim()
            if (value.isNotEmpty()) {
                values.add(value)
            }
        }
        return values
    }

    companion object {
        private data class Fixture(
            val name: String,
            val pdfAssetName: String,
            val benchmarkAssetName: String
        )

        private val TDPI_FIXTURE = Fixture(
            name = "tdpi",
            pdfAssetName = "tdpi_case.pdf",
            benchmarkAssetName = "tdpi_benchmark.json"
        )

        private val GREENSKY_FIXTURE = Fixture(
            name = "greensky",
            pdfAssetName = "greensky_case.pdf",
            benchmarkAssetName = "greensky_benchmark.json"
        )

        private val SIMPLE_CONTRACT_FIXTURE = Fixture(
            name = "simple-contract",
            pdfAssetName = "simple_contract_case.pdf",
            benchmarkAssetName = "simple_contract_benchmark.json"
        )

        private val FIXTURES = listOf(
            TDPI_FIXTURE,
            GREENSKY_FIXTURE,
            SIMPLE_CONTRACT_FIXTURE
        )

    }
}
