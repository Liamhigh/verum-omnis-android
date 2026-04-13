package com.verum.omnis.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.verum.omnis.R

data class MainScreenUiState(
    val selectedEvidenceText: String = "No file selected",
    val caseStatusText: String = "",
    val gemmaStatusText: String = "",
    val legalAdvisoryEnabled: Boolean = false,
    val legalAdvisoryToggleEnabled: Boolean = true,
    val boundedHumanBriefEnabled: Boolean = false,
    val boundedPoliceSummaryEnabled: Boolean = false,
    val boundedLegalStandingEnabled: Boolean = false,
    val boundedControlsEnabled: Boolean = true,
    val boundedStatusLabel: String = "LEGACY",
    val canSelectEvidence: Boolean = true,
    val canRunAnalysis: Boolean = false,
    val canSealDocument: Boolean = true,
    val canScanQr: Boolean = true,
    val canOpenVault: Boolean = true,
    val canOpenGemma: Boolean = true,
    val canReadConstitution: Boolean = true
)

interface MainScreenController {
    fun onSelectEvidence()
    fun onRunAnalysis()
    fun onSealDocument()
    fun onScanQr()
    fun onOpenVault()
    fun onOpenGemma()
    fun onReadConstitution()
    fun onLegalAdvisoryChanged(enabled: Boolean)
    fun onBoundedHumanBriefChanged(enabled: Boolean)
    fun onBoundedPoliceSummaryChanged(enabled: Boolean)
    fun onBoundedLegalStandingChanged(enabled: Boolean)
}

class MainScreenBridge {
    var state by mutableStateOf(MainScreenUiState())
        private set

    fun setSelectedEvidenceText(value: String) {
        state = state.copy(selectedEvidenceText = value)
    }

    fun setCaseStatusText(value: String) {
        state = state.copy(caseStatusText = value)
    }

    fun setGemmaStatusText(value: String) {
        state = state.copy(gemmaStatusText = value)
    }

    fun setLegalAdvisoryEnabled(value: Boolean) {
        state = state.copy(legalAdvisoryEnabled = value)
    }

    fun setLegalAdvisoryToggleEnabled(value: Boolean) {
        state = state.copy(legalAdvisoryToggleEnabled = value)
    }

    fun setBoundedRenderState(
        humanBriefEnabled: Boolean,
        policeSummaryEnabled: Boolean,
        legalStandingEnabled: Boolean,
        controlsEnabled: Boolean,
        statusLabel: String
    ) {
        state = state.copy(
            boundedHumanBriefEnabled = humanBriefEnabled,
            boundedPoliceSummaryEnabled = policeSummaryEnabled,
            boundedLegalStandingEnabled = legalStandingEnabled,
            boundedControlsEnabled = controlsEnabled,
            boundedStatusLabel = statusLabel
        )
    }

    fun setActionAvailability(
        canSelectEvidence: Boolean,
        canRunAnalysis: Boolean,
        canSealDocument: Boolean,
        canScanQr: Boolean,
        canOpenVault: Boolean,
        canOpenGemma: Boolean,
        canReadConstitution: Boolean
    ) {
        state = state.copy(
            canSelectEvidence = canSelectEvidence,
            canRunAnalysis = canRunAnalysis,
            canSealDocument = canSealDocument,
            canScanQr = canScanQr,
            canOpenVault = canOpenVault,
            canOpenGemma = canOpenGemma,
            canReadConstitution = canReadConstitution
        )
    }
}

object MainScreenHost {
    @JvmStatic
    fun bind(
        composeView: ComposeView,
        bridge: MainScreenBridge,
        controller: MainScreenController
    ) {
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            val colorScheme = lightColorScheme(
                primary = Color(0xFF348BFF),
                onPrimary = Color.White,
                secondary = Color(0xFFF7D27A),
                background = Color(0xFF03152D),
                surface = Color(0xFF0A2244),
                onSurface = Color.White
            )
            MaterialTheme(colorScheme = colorScheme) {
                MainScreen(
                    state = bridge.state,
                    controller = controller
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    state: MainScreenUiState,
    controller: MainScreenController
) {
    val scrollState = rememberScrollState()
    val background = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF03152D),
            Color(0xFF072347),
            Color(0xFF061A37)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_verum_home_banner),
                contentDescription = "Verum Omnis",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.FillWidth
            )

            HeroPanel()
            EvidencePanel(state = state, controller = controller)
            ActionGrid(state = state, controller = controller)
            IntroVideoCard()
            ExplanationPanel(
                title = "What happens after you select evidence",
                body = "Verum Omnis copies the file into the app, records intake context, then runs the forensic engine locally. OCR, signatures, overlays, dates, contradictions, money trails, and behavior patterns are pulled into a sealed report so each claim can be anchored back to the source."
            )
            ExplanationPanel(
                title = "Privacy, trust, and constitutional framing",
                body = "Every run starts from the evidence you select now. There is no live internet dependency and no hidden cross-session memory deciding the outcome. Vault artifacts and exports are written only when you choose to continue."
            )
            StatusPanel(
                caseStatusText = state.caseStatusText,
                gemmaStatusText = state.gemmaStatusText
            )
            PricingPanel()
            Text(
                text = "On-device by default | Stateless each run | Private citizen access free | Commercial terms governed in-app",
                color = Color(0xFFD4E6FF),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HeroPanel() {
    GlassCard {
        Text(
            text = "Cross-border forensic analysis with a clearer first step.",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Evidence selection now stands on its own. The user can confirm the file, review the intake details, and then decide when to run the forensic pass instead of triggering analysis the moment an upload starts.",
            color = Color(0xFFD4E6FF),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        VerumChip("Offline")
        Spacer(modifier = Modifier.height(8.dp))
        VerumChip("Stateless")
        Spacer(modifier = Modifier.height(8.dp))
        VerumChip("Constitution-first")
    }
}

@Composable
private fun EvidencePanel(
    state: MainScreenUiState,
    controller: MainScreenController
) {
    GlassCard {
        Text(
            text = "Evidence Intake",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pick one or more evidence files first. Analysis becomes available after selection, so the user can confirm the upload and context before the forensic pass begins.",
            color = Color(0xFFD4E6FF),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = controller::onSelectEvidence,
            enabled = state.canSelectEvidence,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF348BFF),
                contentColor = Color.White
            )
        ) {
            Text("Select Evidence", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0x221FD9FF),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = state.selectedEvidenceText,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(14.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.legalAdvisoryEnabled,
                onCheckedChange = controller::onLegalAdvisoryChanged,
                enabled = state.legalAdvisoryToggleEnabled
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Add Gemma legal advisory",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Gemma writes from sealed findings after the forensic run, not from raw evidence.",
                    color = Color(0xFFD4E6FF),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        BoundedRenderingPanel(
            state = state,
            controller = controller
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = controller::onRunAnalysis,
            enabled = state.canRunAnalysis,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF7D27A),
                contentColor = Color(0xFF03152D)
            )
        ) {
            Text("Run Forensic Analysis", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BoundedRenderingPanel(
    state: MainScreenUiState,
    controller: MainScreenController
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0x16000000),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Bounded Local Model Rendering",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Deterministic analysis remains authoritative. Bounded rendering is downstream only. Audit pass is required before publication. Legacy fallback remains active.",
                color = Color(0xFFD4E6FF),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            VerumChip(state.boundedStatusLabel)
            Spacer(modifier = Modifier.height(10.dp))
            BoundedToggleRow(
                checked = state.boundedHumanBriefEnabled,
                enabled = state.boundedControlsEnabled && state.legalAdvisoryEnabled,
                title = "Enable bounded human brief",
                description = "Activation-ready lane. Rendered from governed findings only, then audited before publication.",
                onCheckedChange = controller::onBoundedHumanBriefChanged
            )
            Spacer(modifier = Modifier.height(8.dp))
            BoundedToggleRow(
                checked = state.boundedPoliceSummaryEnabled,
                enabled = false,
                title = "Enable bounded police summary",
                description = "Present in code, intentionally held back until the human brief lane is field-tested.",
                onCheckedChange = controller::onBoundedPoliceSummaryChanged
            )
            Spacer(modifier = Modifier.height(8.dp))
            BoundedToggleRow(
                checked = state.boundedLegalStandingEnabled,
                enabled = false,
                title = "Enable bounded legal standing",
                description = "Present in code, intentionally held back while legal framing remains under tighter review.",
                onCheckedChange = controller::onBoundedLegalStandingChanged
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Audit required: On",
                color = Color(0xFFF7D27A),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Fail closed: On",
                color = Color(0xFFF7D27A),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BoundedToggleRow(
    checked: Boolean,
    enabled: Boolean,
    title: String,
    description: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                color = if (enabled) Color.White else Color(0xFFB2C7E5),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                text = description,
                color = Color(0xFFD4E6FF),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun ActionGrid(
    state: MainScreenUiState,
    controller: MainScreenController
) {
    GlassCard {
        Text(
            text = "Tools and Next Steps",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedButton(
            onClick = controller::onSealDocument,
            enabled = state.canSealDocument,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("Seal Document", textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = controller::onScanQr,
            enabled = state.canScanQr,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("Scan & Verify QR", textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = controller::onOpenVault,
            enabled = state.canOpenVault,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("Open Vault", textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = controller::onOpenGemma,
            enabled = state.canOpenGemma,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("Open Gemma", textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = controller::onReadConstitution,
            enabled = state.canReadConstitution,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("Read Constitution", textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun IntroVideoCard() {
    var actionLabel by mutableStateOf("Loading Overview Video")
    var prepared by mutableStateOf(false)
    var videoViewRef by mutableStateOf<VideoView?>(null)

    DisposableEffect(Unit) {
        onDispose {
            videoViewRef?.stopPlayback()
        }
    }

    GlassCard {
        Text(
            text = "Watch the walkthrough",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The overview video stays on the front page, but it now sits after the main actions so the user can begin immediately or watch the wider explanation first.",
            color = Color(0xFFD4E6FF),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x22000000)),
            factory = { context ->
                VideoView(context).apply {
                    videoViewRef = this
                    setVideoURI(Uri.parse("android.resource://${context.packageName}/${R.raw.landingpage_intro}"))
                    setOnPreparedListener { player ->
                        prepared = true
                        player.isLooping = false
                        seekTo(1)
                        actionLabel = "Play Overview Video"
                    }
                    setOnCompletionListener {
                        seekTo(1)
                        actionLabel = "Replay Overview Video"
                    }
                    setOnErrorListener { _, _, _ ->
                        prepared = false
                        actionLabel = "Overview Video Unavailable"
                        true
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                val view = videoViewRef ?: return@Button
                if (!prepared) {
                    return@Button
                }
                if (view.isPlaying) {
                    view.pause()
                    actionLabel = "Resume Overview Video"
                } else {
                    view.start()
                    actionLabel = "Pause Overview Video"
                }
            },
            enabled = prepared,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF173B72),
                contentColor = Color.White
            )
        ) {
            Text(actionLabel, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExplanationPanel(
    title: String,
    body: String
) {
    GlassCard {
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            color = Color(0xFFD4E6FF),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun StatusPanel(
    caseStatusText: String,
    gemmaStatusText: String
) {
    GlassCard {
        Text(
            text = "Validation and advisory context",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (caseStatusText.isBlank()) {
                "Court validation and tracked matters are loaded from the existing build metadata."
            } else {
                caseStatusText
            },
            color = Color(0xFFF7D27A),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (gemmaStatusText.isBlank()) {
                "Gemma remains anchored to the sealed case package stored on this device."
            } else {
                gemmaStatusText
            },
            color = Color(0xFFD4E6FF),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun PricingPanel() {
    GlassCard {
        Text(
            text = "Pricing, access, and retention",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Private citizens and law enforcement stay free. Commercial and institutional work follows the governed release model already built into the app. Sealed outputs remain tied to the exact evidence set that created them, so later changes break the seal.",
            color = Color(0xFFD4E6FF),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0x221FD9FF)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color(0x3348B8FF),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(18.dp),
            content = content
        )
    }
}

@Composable
private fun VerumChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x1AF7D27A))
            .border(1.dp, Color(0x55F7D27A), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = Color(0xFFF7D27A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
