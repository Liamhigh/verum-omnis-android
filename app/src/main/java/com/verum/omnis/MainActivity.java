package com.verum.omnis;



import android.Manifest;

import android.content.Context;
import android.content.Intent;

import android.content.pm.PackageManager;

import android.media.MediaPlayer;

import android.net.Uri;

import android.os.Build;

import android.os.Bundle;
import android.text.InputType;
import android.util.Log;

import android.provider.OpenableColumns;

import android.view.View;
import android.webkit.MimeTypeMap;

import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import android.widget.ScrollView;

import android.widget.TextView;

import android.widget.VideoView;



import androidx.activity.result.ActivityResultLauncher;

import androidx.activity.result.contract.ActivityResultContracts;

import androidx.compose.ui.platform.ComposeView;

import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import androidx.core.content.ContextCompat;

import androidx.core.content.FileProvider;



import com.google.mlkit.vision.barcode.common.Barcode;

import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;

import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;

import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import com.verum.omnis.ai.RnDController;

import com.verum.omnis.ai.RnDMeshExchange;

import com.verum.omnis.ai.RulesEngine;
import com.verum.omnis.casefile.CaseFile;
import com.verum.omnis.casefile.CaseFileManager;
import com.verum.omnis.casefile.ScanFolder;
import com.verum.omnis.casefile.ScanFolderManager;

import com.verum.omnis.core.AnalysisEngine;
import com.verum.omnis.core.AnalysisCoordinator;
import com.verum.omnis.core.AnalyzeCaseUseCase;
import com.verum.omnis.core.AnchorBoundNarrativeBuilder;

import com.verum.omnis.core.AssistanceRestrictionManager;

import com.verum.omnis.core.BusinessConstitutionManager;
import com.verum.omnis.core.BoundedRenderSettings;
import com.verum.omnis.core.ConstitutionalNarrativePacketBuilder;
import com.verum.omnis.core.ContradictionReportBuilder;
import com.verum.omnis.core.ContradictionReportModel;
import com.verum.omnis.core.FindingPublicationNormalizer;
import com.verum.omnis.core.ForensicConclusionEngine;
import com.verum.omnis.core.ForensicReportAssembler;
import com.verum.omnis.core.GemmaReportOrchestrator;
import com.verum.omnis.core.HumanFindingsReportBuilder;

import com.verum.omnis.core.EvidenceIntakeCapture;

import com.verum.omnis.core.GemmaCaseContextStore;

import com.verum.omnis.core.GemmaRuntime;

import com.verum.omnis.core.GemmaPolicyManager;

import com.verum.omnis.core.HashUtil;

import com.verum.omnis.core.JurisdictionManager;

import com.verum.omnis.core.MediaForensics;

import com.verum.omnis.core.PDFSealer;
import com.verum.omnis.core.PoliceReadyReportBuilder;
import com.verum.omnis.core.ReadableBriefBuilder;
import com.verum.omnis.core.ReadableBriefModel;
import com.verum.omnis.core.TruthInCodeEngine;
import com.verum.omnis.core.VaultReportBuilder;

import com.verum.omnis.forensic.ForensicPackageWriter;

import com.verum.omnis.forensic.VaultManager;
import com.verum.omnis.ui.MainScreenBridge;
import com.verum.omnis.ui.MainScreenController;
import com.verum.omnis.ui.MainScreenHost;
import com.verum.legal.LegalGrounding;

import com.verum.omnis.rules.ChainLogger;

import com.verum.omnis.rules.NoopChainLogger;

import com.verum.omnis.rules.RulesVerifier;

import com.verum.omnis.rules.VerumMailer;

import com.verum.omnis.rules.VerumRulesRegistry;

import com.verum.omnis.security.IntegrityChecker;



import org.json.JSONArray;

import org.json.JSONException;

import org.json.JSONObject;



import java.io.File;

import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.io.InputStream;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import java.util.Locale;

import java.util.Map;
import java.util.Set;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;



public class MainActivity extends AppCompatActivity implements MainScreenController {

    private static final int MAX_ANALYSIS_BATCH_SIZE = 10;
    private static final String PENDING_GEMMA_REPORT_FILE = "pending_gemma_report.json";
    private static final String LAST_VAULT_RESULT_FILE = "last_vault_result.json";
    private static final boolean ENABLE_GEMMA_SCAN_PIPELINE_GENERATION = false;

    private static final Pattern HUMAN_EMAIL_NAME_PATTERN =
            Pattern.compile("([A-Z][a-z]+(?:\\s+[A-Z][a-z'\\-]+){1,3})\\s*<[^>]+>");
    private static final Pattern HUMAN_QUOTED_AUTHOR_PATTERN =
            Pattern.compile("(?i)(?:quoted\\s+from|from)\\s+([\\p{L}][\\p{L}'\\-]+(?:\\s+[\\p{L}][\\p{L}'\\-]+){1,3})");

    private static final Pattern HUMAN_EMAIL_PATTERN =
            Pattern.compile("([a-zA-Z0-9._%+\\-]{3,})@[a-zA-Z0-9.\\-]+");

    private static final Pattern HUMAN_DATE_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:Mon(?:day)?|Tue(?:sday)?|Wed(?:nesday)?|Thu(?:rsday)?|Fri(?:day)?|Sat(?:urday)?|Sun(?:day)?),?\\s+\\d{1,2}\\s+(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{4}\\b"
                            + "|\\b\\d{1,2}\\s+(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{4}\\b"
            );



    private enum PendingAction {

        NONE,

        SELECT_EVIDENCE,

        RUN_ANALYSIS,

        CREATE_SCAN,

        SEAL_DOCUMENT,

        GENERATE_FORENSIC_PDF,

        VERIFY_SCANNED_SEAL

    }



    private static final class SealPayload {

        String rawValue;

        String fullHash;

        String shortHash;

        String sealId;

        String mode;

        String sourceFileName;

        String jurisdiction;

    }

    private static final class ArchivedScanPackage {
        File manifestFile;
        File scanFolderRoot;
        File sealedEvidenceFile;
        File auditorPdf;
        File findingsFile;
        File forensicPdf;
        File readableBriefFile;
        File policeReadyReportFile;
        File constitutionalNarrativeFile;
        File contradictionEngineFile;
        File legalAdvisoryFile;
        File visualFindingsFile;
        File meshFile;
    }



    private File selectedFile;

    private ScrollView mainScrollView;
    private MainScreenBridge mainScreenBridge;
    private volatile boolean composeUiBusy;

    private TextView selectedFileView;

    private TextView boundaryView;

    private TextView heroHeadlineView;

    private TextView constitutionStatusView;

    private TextView businessModelView;

    private TextView casesView;

    private TextView gemmaStatusView;
    private TextView scanFoldersStatusView;
    private TextView caseFilesStatusView;

    private EvidenceIntakeCapture.Snapshot intakeSnapshot;
    private String investigatorSuppliedFactsInput = "";

    private ActivityResultLauncher<String[]> filePickerLauncher;
    private ActivityResultLauncher<String[]> multiFilePickerLauncher;

    private Button gemmaBtn;
    private CheckBox legalAdvisoryToggle;

    private Button constitutionBtn;

    private Button selectBtn;
    private Button newScanBtn;
    private Button mergeScansBtn;

    private Button sealBtn;

    private Button verifyQrBtn;

    private Button vaultBtn;

    private Button introVideoButton;

    private VideoView introVideoView;
    private AlertDialog boundedHumanBriefDialog;
    private LinearLayout scanFoldersContainer;
    private LinearLayout caseFilesContainer;

    private boolean introVideoPrepared;

    private SealPayload scannedSealPayload;

    private ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    private PendingAction pendingAction = PendingAction.NONE;
    private volatile boolean recoveringPendingGemmaReport = false;
    private volatile boolean includeLegalAdvisoryRequested = false;
    private BoundedRenderSettings boundedRenderSettings = new BoundedRenderSettings();
    private boolean lastVaultResultShownThisSession = false;
    private ScanFolderManager scanFolderManager;
    private CaseFileManager caseFileManager;
    private final LinkedHashSet<String> selectedScanFolderPaths = new LinkedHashSet<>();
    private final ArrayList<File> selectedEvidenceFiles = new ArrayList<>();
    private String pendingScanFolderName;
    private final AnalysisCoordinator analysisCoordinator = new AnalysisCoordinator();
    private final AnalyzeCaseUseCase analyzeCaseUseCase = new AnalyzeCaseUseCase();



    private synchronized ExecutorService getBackgroundExecutor() {

        if (backgroundExecutor == null

                || backgroundExecutor.isShutdown()

                || backgroundExecutor.isTerminated()) {

            backgroundExecutor = Executors.newSingleThreadExecutor();

        }

        return backgroundExecutor;

    }



    @Override

    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);



        RulesVerifier.Result result = RulesVerifier.verifyAndLoad(this);

        if (!result.ok) {

            TextView tv = new TextView(this);

            tv.setText(getString(R.string.init_blocked_format, result.reason));

            tv.setTextSize(18f);

            setContentView(tv);

            return;

        }



        VerumRulesRegistry.install(result.rulesJson, result.version);

        JurisdictionManager.initialize(this);



        setContentView(R.layout.activity_main);
        ComposeView mainComposeView = findViewById(R.id.mainComposeView);
        mainScreenBridge = new MainScreenBridge();
        MainScreenHost.bind(mainComposeView, mainScreenBridge, this);



        scanFolderManager = new ScanFolderManager(this);
        caseFileManager = new CaseFileManager(this);
        boundedRenderSettings = BoundedRenderSettings.load(getApplicationContext());

        mainScrollView = findViewById(R.id.mainScroll);

        gemmaBtn = findViewById(R.id.gemmaBtn);
        legalAdvisoryToggle = findViewById(R.id.legalAdvisoryToggle);

        constitutionBtn = findViewById(R.id.constitutionBtn);

        selectBtn = findViewById(R.id.selectBtn);

        sealBtn = findViewById(R.id.sealBtn);

        verifyQrBtn = findViewById(R.id.verifyQrBtn);

        vaultBtn = findViewById(R.id.vaultBtn);

        introVideoButton = findViewById(R.id.introVideoButton);

        introVideoView = findViewById(R.id.introVideoView);

        selectedFileView = findViewById(R.id.selectedFileValue);

        boundaryView = findViewById(R.id.boundaryText);

        heroHeadlineView = findViewById(R.id.heroHeadline);

        constitutionStatusView = findViewById(R.id.constitutionStatus);

        businessModelView = findViewById(R.id.businessModel);

        casesView = findViewById(R.id.caseStatus);

        gemmaStatusView = findViewById(R.id.gemmaStatus);



        updateBoundaryText();

        updateBusinessConstitution();

        updateGemmaPolicy();

        setupIntroVideo();

        applyRestrictionState();

        syncComposeState();

        scrollLandingPageToTop();



        ActivityResultLauncher<String[]> locationPermissionLauncher =

                registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), resultMap -> {

                    if (selectedFile != null) {

                        intakeSnapshot = EvidenceIntakeCapture.capture(this);

                        updateSelectedFileText();

                    }

                });



        multiFilePickerLauncher =
                registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {

                    if (uris != null && !uris.isEmpty()) {

                        ArrayList<Uri> limitedUris = new ArrayList<>(uris);
                        if (limitedUris.size() > MAX_ANALYSIS_BATCH_SIZE) {
                            limitedUris = new ArrayList<>(limitedUris.subList(0, MAX_ANALYSIS_BATCH_SIZE));
                            showDialog(
                                    getString(R.string.selection_limit_title),
                                    getString(R.string.selection_limit_message_format, MAX_ANALYSIS_BATCH_SIZE)
                            );
                        }

                        PendingAction actionToRun = pendingAction;
                        pendingAction = PendingAction.NONE;

                        setBusy(true, getString(R.string.loading_files));

                        ArrayList<Uri> finalUris = limitedUris;
                        getBackgroundExecutor().execute(() -> {
                            try {
                                ArrayList<File> copiedFiles = new ArrayList<>();
                                for (Uri uri : finalUris) {
                                    copiedFiles.add(copyUriToCache(uri));
                                }

                                runOnUiThread(() -> {
                                    selectedEvidenceFiles.clear();
                                    selectedEvidenceFiles.addAll(copiedFiles);
                                    selectedFile = copiedFiles.isEmpty() ? null : copiedFiles.get(0);
                                    investigatorSuppliedFactsInput = "";
                                    intakeSnapshot = captureOrRequestLocation(locationPermissionLauncher);
                                    updateSelectedFileText();
                                    setBusy(false, null);
                                    promptForSupplementalFactsThen(actionToRun);
                                });

                            } catch (Exception e) {

                                runOnUiThread(() -> {

                                    setBusy(false, null);

                                    showDialog(getString(R.string.file_load_failed), e.getMessage());

                                });

                            }
                        });
                    }
                });

        filePickerLauncher =

                registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {

                    if (uri != null) {

                        PendingAction actionToRun = pendingAction;

                        pendingAction = PendingAction.NONE;

                        setBusy(true, getString(R.string.loading_file));

                        getBackgroundExecutor().execute(() -> {

                            try {

                                File copied = copyUriToCache(uri);

                                runOnUiThread(() -> {

                                    selectedEvidenceFiles.clear();
                                    selectedEvidenceFiles.add(copied);
                                    selectedFile = copied;
                                    investigatorSuppliedFactsInput = "";

                                    intakeSnapshot = captureOrRequestLocation(locationPermissionLauncher);

                                    updateSelectedFileText();

                                    setBusy(false, null);

                                    promptForSupplementalFactsThen(actionToRun);

                                });

                            } catch (Exception e) {

                                runOnUiThread(() -> {

                                    setBusy(false, null);

                                    showDialog(getString(R.string.file_load_failed), e.getMessage());

                                });

                            }

                        });

                    }

                });



        gemmaBtn.setOnClickListener(v -> startActivity(new Intent(this, GemmaActivity.class)));

        constitutionBtn.setOnClickListener(v -> openBundledConstitution());



        selectBtn.setOnClickListener(v -> {

            pendingAction = PendingAction.RUN_ANALYSIS;

            multiFilePickerLauncher.launch(new String[]{"*/*"});

        });



        sealBtn.setOnClickListener(v -> {

            pendingAction = PendingAction.SEAL_DOCUMENT;

            filePickerLauncher.launch(new String[]{"*/*"});

        });



        verifyQrBtn.setOnClickListener(v -> scanVerumQr());

        vaultBtn.setOnClickListener(v -> openVault());

    }



    private void runUnifiedVerification() {

        AssistanceRestrictionManager.Snapshot restriction = AssistanceRestrictionManager.load(this);

        if (restriction.restricted) {

            showDialog(

                    getString(R.string.assistance_suspended),

                    getString(R.string.assistance_suspended_reason_format,

                            restriction.caseId, restriction.evidenceHashShort, restriction.reason)

            );

            return;

        }

        if (selectedFile == null) {

            showDialog(getString(R.string.unified_report), getString(R.string.no_file_selected_analysis_skipped));

            return;

        }



        setBusy(true, getString(R.string.running_forensic_analysis));
        includeLegalAdvisoryRequested = legalAdvisoryToggle != null && legalAdvisoryToggle.isChecked();

        getBackgroundExecutor().execute(this::runUnifiedVerificationInBackground);

    }

    private AnalyzeCaseUseCase.Gateway buildAnalyzeCaseGateway() {
        return new AnalyzeCaseUseCase.Gateway() {
            @Override
            public String buildAuditReport(AnalysisEngine.ForensicReport report, Map<String, String> integrityResults) {
                return buildFallbackHumanReport(report, integrityResults);
            }

            @Override
            public String generateHumanReadableReport(
                    AnalysisEngine.ForensicReport report,
                    Map<String, String> integrityResults,
                    String findingsJson,
                    String auditReport
            ) {
                return MainActivity.this.generateHumanReadableReport(report, integrityResults, findingsJson, auditReport);
            }

            @Override
            public Context getApplicationContext() {
                return MainActivity.this.getApplicationContext();
            }

            @Override
            public String buildLegacyLegalAdvisory(AnalysisEngine.ForensicReport report) {
                return MainActivity.this.buildLegacyLegalAdvisory(report);
            }

            @Override
            public String appendLegalAdvisorySection(String humanReadableReport, String legalAdvisory) {
                return MainActivity.this.appendLegalAdvisorySection(humanReadableReport, legalAdvisory);
            }

            @Override
            public File writeModelAuditLedgerToVault(AnalysisEngine.ForensicReport report, String modelAuditLedger) throws Exception {
                return MainActivity.this.writeModelAuditLedgerToVault(report, modelAuditLedger);
            }

            @Override
            public String buildVisualFindingsMemo(AnalysisEngine.ForensicReport report) {
                return MainActivity.this.buildVisualFindingsMemo(report);
            }

            @Override
            public String generateReadableFindingsBriefReport(
                    AnalysisEngine.ForensicReport report,
                    String findingsJson,
                    String auditReport,
                    String humanReadableReport,
                    String legalAdvisory,
                    String visualFindingsMemo
            ) {
                return MainActivity.this.generateReadableFindingsBriefReport(
                        report,
                        findingsJson,
                        auditReport,
                        humanReadableReport,
                        legalAdvisory,
                        visualFindingsMemo
                );
            }

            @Override
            public String generatePoliceReadyReport(AnalysisEngine.ForensicReport report) {
                return MainActivity.this.generatePoliceReadyReport(report);
            }

            @Override
            public String generateConstitutionalVaultReport(
                    AnalysisEngine.ForensicReport report,
                    Map<String, String> integrityResults,
                    Map<String, String> primaryEvidenceMeta,
                    String auditReport,
                    String humanReadableReport,
                    String readableBriefReport
            ) {
                return MainActivity.this.generateConstitutionalVaultReport(
                        report,
                        integrityResults,
                        primaryEvidenceMeta != null ? new HashMap<>(primaryEvidenceMeta) : new HashMap<>(),
                        auditReport,
                        humanReadableReport,
                        readableBriefReport
                );
            }

            @Override
            public String generateContradictionEngineReport(AnalysisEngine.ForensicReport report) {
                return MainActivity.this.generateContradictionEngineReport(report);
            }

            @Override
            public File writeAuditReportToVault(AnalysisEngine.ForensicReport report, String auditReport) throws Exception {
                return MainActivity.this.writeAuditReportToVault(report, auditReport);
            }

            @Override
            public File writeForensicReportToVault(AnalysisEngine.ForensicReport report, String humanReadableReport) throws Exception {
                return MainActivity.this.writeForensicReportToVault(report, humanReadableReport);
            }

            @Override
            public File writeReadableFindingsBriefToVault(
                    AnalysisEngine.ForensicReport report,
                    String readableBriefReport
            ) throws Exception {
                return MainActivity.this.writeReadableFindingsBriefToVault(report, readableBriefReport);
            }

            @Override
            public File writePoliceReadyReportToVault(
                    AnalysisEngine.ForensicReport report,
                    String policeReadyReport
            ) throws Exception {
                return MainActivity.this.writePoliceReadyReportToVault(report, policeReadyReport);
            }

            @Override
            public File writeConstitutionalVaultReportToVault(
                    AnalysisEngine.ForensicReport report,
                    String constitutionalNarrativeReport
            ) throws Exception {
                return MainActivity.this.writeConstitutionalVaultReportToVault(report, constitutionalNarrativeReport);
            }

            @Override
            public File writeContradictionEngineReportToVault(
                    AnalysisEngine.ForensicReport report,
                    String contradictionEngineReport
            ) throws Exception {
                return MainActivity.this.writeContradictionEngineReportToVault(report, contradictionEngineReport);
            }

            @Override
            public File writeLegalAdvisoryToVault(AnalysisEngine.ForensicReport report, String legalAdvisory) throws Exception {
                return MainActivity.this.writeLegalAdvisoryToVault(report, legalAdvisory);
            }

            @Override
            public File writeVisualFindingsMemoToVault(
                    AnalysisEngine.ForensicReport report,
                    String visualFindingsMemo
            ) throws Exception {
                return MainActivity.this.writeVisualFindingsMemoToVault(report, visualFindingsMemo);
            }
        };
    }

    private AnalysisCoordinator.Gateway buildAnalysisCoordinatorGateway() {
        return new AnalysisCoordinator.Gateway() {
            @Override
            public Map<String, String> runIntegrityChecks() {
                return IntegrityChecker.runChecks(MainActivity.this);
            }

            @Override
            public AnalysisEngine.ForensicReport analyzeEvidenceSet(List<File> evidenceFiles) {
                return AnalysisEngine.analyzeEvidenceSet(MainActivity.this, evidenceFiles, MainActivity.this::publishBusyProgress);
            }

            @Override
            public AnalysisEngine.ForensicReport analyzeSingleEvidence(File evidenceFile) {
                return AnalysisEngine.analyze(MainActivity.this, evidenceFile, MainActivity.this::publishBusyProgress);
            }

            @Override
            public void applyInvestigatorContext(AnalysisEngine.ForensicReport report) {
                MainActivity.this.applyInvestigatorContext(report);
            }

            @Override
            public Map<String, String> inspectPrimaryEvidence(File primaryEvidenceFile) {
                return MediaForensics.inspectFile(primaryEvidenceFile);
            }

            @Override
            public JSONObject buildFindingsPayload(File primaryEvidenceFile, AnalysisEngine.ForensicReport report) throws Exception {
                return ForensicPackageWriter.buildPayload(primaryEvidenceFile, report, intakeSnapshot);
            }
        };
    }

    private void updatePendingArtifactPaths(String caseId, AnalyzeCaseUseCase.Result result) {
        if (caseId == null || caseId.trim().isEmpty() || result == null) {
            return;
        }
        if (result.forensicPdf != null) {
            updatePendingGemmaNarrativeHumanReportPath(caseId, result.forensicPdf);
        }
        for (Map.Entry<String, File> entry : result.vaultReferences.entrySet()) {
            updatePendingGemmaNarrativeArtifactPath(caseId, entry.getKey(), entry.getValue());
        }
    }



    private void runUnifiedVerificationInBackground() {

        ChainLogger logger = new NoopChainLogger();

        HashMap<String, String> meta = buildMeta();
        List<File> evidenceFiles = getSelectedEvidenceFilesSnapshot();
        File primaryEvidenceFile = evidenceFiles.isEmpty() ? selectedFile : evidenceFiles.get(0);



        try {

            AnalysisCoordinator.Request analysisRequest = new AnalysisCoordinator.Request();
            analysisRequest.evidenceFiles = evidenceFiles;
            analysisRequest.primaryEvidenceFile = primaryEvidenceFile;
            AnalysisCoordinator.Result analysisResult = analysisCoordinator.run(
                    analysisRequest,
                    buildAnalysisCoordinatorGateway()
            );
            Map<String, String> results = analysisResult.integrityResults;
            AnalysisEngine.ForensicReport report = analysisResult.report;

            AssistanceRestrictionManager.Snapshot restrictionPreview =

                    AssistanceRestrictionManager.preview(report);

            HashMap<String, String> primaryEvidenceMeta = new HashMap<>(analysisResult.primaryEvidenceMeta);

            JSONObject findingsPayload = analysisResult.findingsPayload;

            File sealedEvidenceFile = writeSealedEvidenceToVault(report);
            Log.d("MainActivity", "Sealed evidence written: " + (sealedEvidenceFile != null ? sealedEvidenceFile.getAbsolutePath() : "null"));
            updatePendingGemmaNarrativeArtifactPath(report.caseId, "sealedEvidencePath", sealedEvidenceFile);

            AnalyzeCaseUseCase.Request caseRequest = new AnalyzeCaseUseCase.Request();
            caseRequest.mode = AnalyzeCaseUseCase.Mode.FULL_SCAN;
            caseRequest.report = report;
            caseRequest.integrityResults = results;
            caseRequest.primaryEvidenceMeta = primaryEvidenceMeta;
            caseRequest.findingsPayload = findingsPayload;
            caseRequest.includeLegalAdvisory = includeLegalAdvisoryRequested;
            caseRequest.boundedHumanBriefEnabled = boundedRenderSettings.getBoundedHumanBriefEnabled();
            caseRequest.boundedPoliceSummaryEnabled = boundedRenderSettings.getBoundedPoliceSummaryEnabled();
            caseRequest.boundedLegalStandingEnabled = boundedRenderSettings.getBoundedLegalStandingEnabled();
            caseRequest.boundedRenderAuditRequired = boundedRenderSettings.getBoundedRenderAuditRequired();
            caseRequest.boundedRenderFailClosed = boundedRenderSettings.getBoundedRenderFailClosed();

            AnalyzeCaseUseCase.Result caseResult = analyzeCaseUseCase.run(caseRequest, buildAnalyzeCaseGateway());
            updatePendingArtifactPaths(report.caseId, caseResult);
            Log.d("MainActivity", "Audit report written: " + (caseResult.auditorPdf != null ? caseResult.auditorPdf.getAbsolutePath() : "null"));
            Log.d("MainActivity", "Human report written: " + (caseResult.forensicPdf != null ? caseResult.forensicPdf.getAbsolutePath() : "null"));
            Log.d("MainActivity", "Readable brief written: " + (caseResult.readableBriefFile != null ? caseResult.readableBriefFile.getAbsolutePath() : "null"));
            Log.d("MainActivity", "Police-ready report written: " + (caseResult.policeReadyReportFile != null ? caseResult.policeReadyReportFile.getAbsolutePath() : "null"));
            Log.d("MainActivity", "Constitutional vault report written: " + (caseResult.constitutionalNarrativeFile != null ? caseResult.constitutionalNarrativeFile.getAbsolutePath() : "null"));
            Log.d("MainActivity", "Contradiction engine report written: " + (caseResult.contradictionEngineFile != null ? caseResult.contradictionEngineFile.getAbsolutePath() : "null"));
            Log.d("MainActivity", "Legal advisory written: " + (caseResult.legalAdvisoryFile != null ? caseResult.legalAdvisoryFile.getAbsolutePath() : "null"));
            Log.d("MainActivity", "Model audit ledger written: " + (caseResult.modelAuditLedgerFile != null ? caseResult.modelAuditLedgerFile.getAbsolutePath() : "null"));
            Log.d("MainActivity", "Visual findings memo written: " + (caseResult.visualFindingsFile != null ? caseResult.visualFindingsFile.getAbsolutePath() : "null"));

            String auditReport = caseResult.auditReport;
            String humanReadableReport = caseResult.humanReadableReport;
            String legalAdvisory = caseResult.legalAdvisory;
            String visualFindingsMemo = caseResult.visualFindingsMemo;
            String readableBriefReport = caseResult.readableBriefReport;
            String policeReadyReport = caseResult.policeReadyReport;
            String constitutionalNarrativeReport = caseResult.constitutionalNarrativeReport;
            String contradictionEngineReport = caseResult.contradictionEngineReport;
            File auditorPdf = caseResult.auditorPdf;
            File forensicPdf = caseResult.forensicPdf;
            File readableBriefFile = caseResult.readableBriefFile;
            File policeReadyReportFile = caseResult.policeReadyReportFile;
            File constitutionalNarrativeFile = caseResult.constitutionalNarrativeFile;
            File contradictionEngineFile = caseResult.contradictionEngineFile;
            File legalAdvisoryFile = caseResult.legalAdvisoryFile;
            File modelAuditLedgerFile = caseResult.modelAuditLedgerFile;
            File visualFindingsFile = caseResult.visualFindingsFile;

            findingsPayload.put("auditorReport", auditReport);
            findingsPayload.put("legalAdvisory", legalAdvisory);
            findingsPayload.put("legalAdvisoryIncluded", !safeValue(legalAdvisory).isEmpty());
            findingsPayload.put("humanReadableReport", humanReadableReport);
            findingsPayload.put("policeReadyReport", policeReadyReport);
            findingsPayload.put("guardianDecision", report.guardianDecision != null ? report.guardianDecision : new JSONObject());
            findingsPayload.put("forensicConclusion", report.forensicConclusion != null ? report.forensicConclusion : new JSONObject());
            findingsPayload.put("certifiedFindings", report.certifiedFindings != null ? report.certifiedFindings : new JSONArray());
            FindingPublicationNormalizer.applyToReport(report);
            findingsPayload.put("normalizedCertifiedFindings",
                    report.normalizedCertifiedFindings != null ? report.normalizedCertifiedFindings : new JSONArray());
            findingsPayload.put("normalizedCertifiedFindingCount", report.normalizedCertifiedFindingCount);
            findingsPayload.put("jurisdictionResolution", report.jurisdictionResolution != null ? report.jurisdictionResolution : new JSONObject());
            findingsPayload.put("legalAttorneyAnalysis", report.legalAttorneyAnalysis != null ? report.legalAttorneyAnalysis : new JSONObject());
            findingsPayload.put("consensusReview", report.consensusReview != null ? report.consensusReview : new JSONObject());
            findingsPayload.put("evidenceBundleHash", safeValue(report.evidenceBundleHash));
            findingsPayload.put("deterministicRunId", safeValue(report.deterministicRunId));
            File findingsFile = ForensicPackageWriter.write(this, findingsPayload);
            Log.d("MainActivity", "Findings payload written: " + (findingsFile != null ? findingsFile.getAbsolutePath() : "null"));
            updatePendingGemmaNarrativeArtifactPath(report.caseId, "findingsPath", findingsFile);



            if (report.topLiabilities != null) {

                logger.record("contradiction",

                        report.ledgerEntry != null ? report.ledgerEntry.caseId : "N/A",

                        report.evidenceHash,

                        meta);

                VerumMailer.sendContradiction(

                        report.ledgerEntry != null ? report.ledgerEntry.caseId : "N/A",

                        String.join(", ", report.topLiabilities),

                        report.evidenceHash,

                        findingsPayload

                );

            }



            RnDController.Feedback fb =

                    RnDController.synthesize(this, buildRulesResultFromReport(report));

            File meshFile = RnDMeshExchange.exportPacketToFile(this, fb);

            ArchivedScanPackage archivedScan = archiveCompletedRunAsScan(
                    report,
                    findingsPayload,
                    auditReport,
                    humanReadableReport,
                    sealedEvidenceFile,
                    auditorPdf,
                    findingsFile,
                    forensicPdf,
                    readableBriefFile,
                    policeReadyReportFile,
                    constitutionalNarrativeFile,
                    contradictionEngineFile,
                    legalAdvisoryFile,
                    visualFindingsFile,
                    meshFile
            );
            Log.d("MainActivity", "Scan folder archived: " + (archivedScan != null && archivedScan.manifestFile != null ? archivedScan.manifestFile.getAbsolutePath() : "null"));

            File preferredSealedEvidenceFile = archivedScan != null && archivedScan.sealedEvidenceFile != null
                    ? archivedScan.sealedEvidenceFile
                    : sealedEvidenceFile;
            File preferredAuditorPdf = archivedScan != null && archivedScan.auditorPdf != null
                    ? archivedScan.auditorPdf
                    : auditorPdf;
            File preferredFindingsFile = archivedScan != null && archivedScan.findingsFile != null
                    ? archivedScan.findingsFile
                    : findingsFile;
            File preferredForensicPdf = archivedScan != null && archivedScan.forensicPdf != null
                    ? archivedScan.forensicPdf
                    : forensicPdf;
            File preferredReadableBriefFile = archivedScan != null && archivedScan.readableBriefFile != null
                    ? archivedScan.readableBriefFile
                    : readableBriefFile;
            File preferredPoliceReadyReportFile = archivedScan != null && archivedScan.policeReadyReportFile != null
                    ? archivedScan.policeReadyReportFile
                    : policeReadyReportFile;
            File preferredConstitutionalNarrativeFile = archivedScan != null && archivedScan.constitutionalNarrativeFile != null
                    ? archivedScan.constitutionalNarrativeFile
                    : constitutionalNarrativeFile;
            File preferredContradictionEngineFile = archivedScan != null && archivedScan.contradictionEngineFile != null
                    ? archivedScan.contradictionEngineFile
                    : contradictionEngineFile;
            File preferredLegalAdvisoryFile = archivedScan != null && archivedScan.legalAdvisoryFile != null
                    ? archivedScan.legalAdvisoryFile
                    : legalAdvisoryFile;
            File preferredVisualFindingsFile = archivedScan != null && archivedScan.visualFindingsFile != null
                    ? archivedScan.visualFindingsFile
                    : visualFindingsFile;
            File preferredMeshFile = archivedScan != null && archivedScan.meshFile != null
                    ? archivedScan.meshFile
                    : meshFile;

            if (archivedScan != null) {
                deleteLooseVaultArtifact(sealedEvidenceFile, preferredSealedEvidenceFile);
                deleteLooseVaultArtifact(auditorPdf, preferredAuditorPdf);
                deleteLooseVaultArtifact(findingsFile, preferredFindingsFile);
                deleteLooseVaultArtifact(forensicPdf, preferredForensicPdf);
                deleteLooseVaultArtifact(readableBriefFile, preferredReadableBriefFile);
                deleteLooseVaultArtifact(policeReadyReportFile, preferredPoliceReadyReportFile);
                deleteLooseVaultArtifact(constitutionalNarrativeFile, preferredConstitutionalNarrativeFile);
                deleteLooseVaultArtifact(contradictionEngineFile, preferredContradictionEngineFile);
                deleteLooseVaultArtifact(legalAdvisoryFile, preferredLegalAdvisoryFile);
                deleteLooseVaultArtifact(visualFindingsFile, preferredVisualFindingsFile);
                deleteLooseVaultArtifact(meshFile, preferredMeshFile);
            }

            saveLastVaultResult(report, preferredReadableBriefFile, preferredPoliceReadyReportFile, preferredForensicPdf, preferredAuditorPdf, preferredFindingsFile, preferredSealedEvidenceFile, preferredLegalAdvisoryFile, preferredConstitutionalNarrativeFile, preferredContradictionEngineFile, null);

            GemmaCaseContextStore.save(
                    this,
                    report.caseId,
                    buildEvidenceSelectionLabel(evidenceFiles),
                    buildGemmaChatContext(report, readableBriefReport, auditReport, findingsPayload.toString(2))
            );

            AssistanceRestrictionManager.Snapshot restriction =

                    AssistanceRestrictionManager.persistIfRestricted(this, report);



            logger.record("upload_received",

                    report.ledgerEntry != null ? report.ledgerEntry.caseId : "N/A",

                    report.evidenceHash,

                    meta);

            VerumMailer.sendUploadReceived(

                    report.ledgerEntry != null ? report.ledgerEntry.caseId : "N/A",

                    selectedFile.getName(),

                    selectedFile.length() + " bytes",

                    report.evidenceHash,

                    findingsPayload

            );


            final String reportText = buildVaultResultNotice(
                    report,
                    preferredSealedEvidenceFile,
                    preferredReadableBriefFile,
                    preferredForensicPdf,
                    preferredAuditorPdf,
                    preferredFindingsFile,
                    preferredLegalAdvisoryFile,
                    modelAuditLedgerFile,
                    preferredVisualFindingsFile,
                    preferredMeshFile,
                    restriction
            );



            runOnUiThread(() -> {

                setBusy(false, null);

                applyRestrictionState();

                showVaultAwareDialog(

                        getString(R.string.unified_report),

                        reportText,

                        preferredReadableBriefFile != null ? preferredReadableBriefFile : preferredForensicPdf,

                        getString(R.string.open_report),

                        preferredFindingsFile

                );

            });

        } catch (Throwable t) {

            logger.record("error", "N/A", "N/A", meta);

            String errorMessage = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            VerumMailer.sendError("N/A", "Verification failed: " + errorMessage);

            runOnUiThread(() -> {

                setBusy(false, null);

                showDialog(getString(R.string.verify_failed), errorMessage);

            });

        }

    }



    private void appendConstitutionalReport(

            StringBuilder sb,

            AnalysisEngine.ForensicReport report,

            Map<String, String> integrityResults,

            HashMap<String, String> fileMeta,

            AssistanceRestrictionManager.Snapshot restrictionPreview

    ) {

        sb.append("=== Constitutional Mandate ===\n");

        sb.append("Truth over probability. Evidence before narrative. Local-only analysis. No invention of facts.\n");

        sb.append("Modernist, novel, or visually unconventional documents are reported as findings and do not halt the forensic process.\n");

        sb.append("Case ID: ").append(report.caseId)

                .append("\nEvidence Hash: ").append(report.evidenceHash)

                .append("\nJurisdiction: ").append(report.jurisdictionName)

                .append(" (").append(report.jurisdiction).append(")")

                .append("\nJurisdiction Anchor: ").append(report.jurisdictionAnchor)

                .append("\nBlockchain Anchor: ").append(report.blockchainAnchor)

                .append("\nSummary: ").append(report.summary);



        sb.append("\n\n=== Intake Record ===\n");

        sb.append("Source File: ").append(selectedFile != null ? selectedFile.getName() : "unknown");

        if (intakeSnapshot != null) {

            sb.append("\nUTC: ").append(intakeSnapshot.capturedAtUtc)

                    .append("\nLocal Time: ").append(intakeSnapshot.localTime)

                    .append("\nTimezone: ").append(intakeSnapshot.timezoneId)

                    .append("\nGPS: ").append(intakeSnapshot.coordinatesLabel());

        }



        sb.append("\n\n=== Evidence Integrity ===\n");

        for (Map.Entry<String, String> entry : integrityResults.entrySet()) {

            sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");

        }

        if (fileMeta != null && !fileMeta.isEmpty()) {

            for (Map.Entry<String, String> entry : fileMeta.entrySet()) {

                sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");

            }

        }



        appendCriticalLegalSubjectsSection(sb, report.constitutionalExtraction);

        appendContradictionRegisterSection(sb, report.diagnostics);

        appendDishonestyDetectionMatrix(sb, report);

        appendEnforcementOutcomeSection(sb, restrictionPreview);

        appendNamedPartiesSection(sb, report.constitutionalExtraction);

        appendIncidentRegisterSection(sb, report.constitutionalExtraction);

        appendActionableOutputSection(sb, report);

        appendAnchoredFindingsSection(sb, report.constitutionalExtraction);



        if (report.legalReferences != null && report.legalReferences.length > 0) {

            sb.append("\n=== Applicable Law / Jurisdiction Basis ===\n");

            for (String reference : report.legalReferences) {

                sb.append("• ").append(reference).append("\n");

            }

        }



        if (report.nativeEvidence != null) {

            appendNativeEvidenceSection(sb, report.nativeEvidence);

        }



        try {

            if (report.diagnostics != null) {

                sb.append("\n=== Diagnostics ===\n").append(report.diagnostics.toString(2));

            }

            appendBehavioralProfileSummary(sb, report.behavioralProfile);

        } catch (JSONException ignored) {

            // Fallback if pretty print fails

            if (report.diagnostics != null) {

                sb.append("\n=== Diagnostics ===\n").append(report.diagnostics.toString());

            }

            appendBehavioralProfileSummary(sb, report.behavioralProfile);

        }

    }



    private void appendDishonestyDetectionMatrix(StringBuilder sb, AnalysisEngine.ForensicReport report) {

        sb.append("\n=== Dishonesty Detection Matrix ===\n");

        sb.append("Truth Score: ").append(report.truthScore).append("/100\n");

        sb.append("Dishonesty Threshold: ").append(report.dishonestyThreshold).append("\n");

        sb.append("Status: ").append(report.dishonestyStatus).append("\n");

        if (report.dishonestyFindings != null && report.dishonestyFindings.length > 0) {

            sb.append("Key Findings:\n");

            for (String finding : report.dishonestyFindings) {

                sb.append("• ").append(finding).append("\n");

            }

        }

    }



    private void appendContradictionRegisterSection(StringBuilder sb, JSONObject diagnostics) {

        sb.append("\n=== Contradiction Register ===\n");

        JSONArray contradictions = diagnostics != null ? diagnostics.optJSONArray("contradictionRegister") : null;
        int verifiedCount = diagnostics != null ? diagnostics.optInt("verifiedContradictionCount", 0) : 0;
        int candidateCount = diagnostics != null ? diagnostics.optInt("candidateContradictionCount", 0) : 0;
        int rejectedCount = diagnostics != null ? diagnostics.optInt("rejectedContradictionCount", 0) : 0;

        if (contradictions == null || contradictions.length() == 0) {

            sb.append("No contradiction records extracted in this pass.\n");

            return;

        }

        sb.append("Verified: ").append(verifiedCount)
                .append(" | Candidate: ").append(candidateCount)
                .append(" | Rejected: ").append(rejectedCount)
                .append("\n");
        appendContradictionEntriesByStatus(sb, contradictions, "VERIFIED", "Verified findings");
        appendContradictionEntriesByStatus(sb, contradictions, "CANDIDATE", "Candidate findings needing review");
        appendContradictionEntriesByStatus(sb, contradictions, "REJECTED", "Rejected findings appendix");

    }

    private void appendContradictionEntriesByStatus(StringBuilder sb, JSONArray contradictions, String status, String title) {
        if (contradictions == null || contradictions.length() == 0) {
            return;
        }
        boolean emittedHeader = false;
        for (int i = 0; i < contradictions.length(); i++) {
            JSONObject contradiction = contradictions.optJSONObject(i);
            if (contradiction == null || !status.equalsIgnoreCase(contradiction.optString("status", ""))) {
                continue;
            }
            if (!emittedHeader) {
                sb.append("\n").append(title).append(":\n");
                emittedHeader = true;
            }
            sb.append("• Page ").append(contradiction.optInt("page", 0))
                    .append(" / actor ").append(contradiction.optString("actor", "unresolved actor"))
                    .append(" / confidence ").append(contradiction.optString("confidence", "LOW"))
                    .append("\n  Conflict type: ").append(contradiction.optString("conflictType", "CONTEXT_REVIEW"));
            JSONObject propositionA = contradiction.optJSONObject("propositionA");
            JSONObject propositionB = contradiction.optJSONObject("propositionB");
            if (propositionA != null) {
                sb.append("\n  Proposition A: ").append(propositionA.optString("text", ""));
            }
            if (propositionB != null) {
                sb.append("\n  Proposition B: ").append(propositionB.optString("text", ""));
            }
            sb.append("\n  Why it conflicts: ").append(contradiction.optString("whyItConflicts", ""))
                    .append("\n");
            String neededEvidence = contradiction.optString("neededEvidence", "").trim();
            if (!neededEvidence.isEmpty()) {
                sb.append("  What would resolve this: ").append(neededEvidence).append("\n");
            }
        }
    }

    private void appendBehavioralProfileSummary(StringBuilder sb, JSONObject behavioralProfile) {
        if (behavioralProfile == null) {
            return;
        }
        sb.append("\n=== Behavioral Profile ===\n");
        String consensus = behavioralProfile.optString("consensus", "").trim();
        if (!consensus.isEmpty()) {
            sb.append("Consensus: ").append(consensus).append("\n");
        }
        appendOrdinalBehaviorMetric(sb, "Language anomalies", behavioralProfile, "languageAnomalies");
        appendOrdinalBehaviorMetric(sb, "Timeline conflicts", behavioralProfile, "timelineConflicts");
        appendOrdinalBehaviorMetric(sb, "Metadata suspicion", behavioralProfile, "metadataSuspicion");
    }

    private void appendOrdinalBehaviorMetric(StringBuilder sb, String label, JSONObject profile, String key) {
        if (profile == null || !profile.has(key)) {
            return;
        }
        sb.append(label).append(": ").append(toOrdinalMetric(profile.optDouble(key, 0.0d))).append("\n");
    }

    private String toOrdinalMetric(double value) {
        if (value >= 0.75d) return "HIGH";
        if (value >= 0.35d) return "MODERATE";
        if (value > 0d) return "LOW";
        return "INSUFFICIENT";
    }



    private void appendCriticalLegalSubjectsSection(StringBuilder sb, JSONObject extraction) {

        sb.append("\n=== Critical Legal Subjects ===\n");

        JSONArray subjects = extraction != null ? extraction.optJSONArray("criticalLegalSubjects") : null;

        if (subjects == null || subjects.length() == 0) {

            sb.append("No critical legal subjects extracted with confidence.\n");

            return;

        }

        for (int i = 0; i < subjects.length(); i++) {

            JSONObject subject = subjects.optJSONObject(i);

            if (subject == null) continue;

            sb.append("• Page ").append(subject.optInt("page", 0))

                    .append(" / ").append(subject.optString("subject", "Unknown"))

                    .append("\n");

            JSONArray matchedTerms = subject.optJSONArray("matchedTerms");

            if (matchedTerms != null && matchedTerms.length() > 0) {

                sb.append("  Matched terms: ").append(joinJsonArray(matchedTerms, matchedTerms.length())).append("\n");

            }

            String excerpt = subject.optString("excerpt", "").trim();

            if (!excerpt.isEmpty()) {

                sb.append("  Excerpt: ").append(excerpt).append("\n");

            }

        }

    }



    private void appendNamedPartiesSection(StringBuilder sb, JSONObject extraction) {

        sb.append("\n=== Named Parties ===\n");

        JSONArray namedParties = extraction != null ? extraction.optJSONArray("namedParties") : null;

        if (namedParties == null || namedParties.length() == 0) {

            sb.append("No named parties extracted with confidence.\n");

            return;

        }

        for (int i = 0; i < namedParties.length(); i++) {

            JSONObject party = namedParties.optJSONObject(i);

            if (party == null) continue;
            String name = party.optString("name", "Unknown");
            if (!isAllowedPrincipalActorName(name)) {
                continue;
            }

            sb.append("• ").append(name)

                    .append(" / first page ").append(party.optInt("firstPage", 0))

                    .append(" / pages ").append(party.optJSONArray("pages"))

                    .append("\n");

        }

    }



    private void appendActionableOutputSection(StringBuilder sb, AnalysisEngine.ForensicReport report) {

        sb.append("\n=== Actionable Output ===\n");

        if (report.topLiabilities != null && report.topLiabilities.length > 0) {

            sb.append("Top Liabilities:\n");

            for (String liability : report.topLiabilities) {

                sb.append("• ").append(liability).append("\n");

            }

        }

        JSONArray incidents = report.constitutionalExtraction != null

                ? report.constitutionalExtraction.optJSONArray("incidentRegister") : null;

        if (incidents != null && incidents.length() > 0) {

            sb.append("Incident register listed below.\n");

        }

        sb.append("Risk Score: ").append(report.riskScore).append("\n");

        sb.append("Recommended Route: ");

        if ("ZAF".equals(report.jurisdiction)) {

            sb.append("SAPS / court-ready complaint path");

        } else if ("UAE".equals(report.jurisdiction)) {

            sb.append("RAKEZ / UAE institutional route");

        } else {

            sb.append("jurisdiction review required");

        }

        sb.append("\n");

    }



    private void appendEnforcementOutcomeSection(

            StringBuilder sb,

            AssistanceRestrictionManager.Snapshot restrictionPreview

    ) {

        sb.append("\n=== Constitutional Enforcement Outcome ===\n");

        sb.append("Forensic processing completed without a hard stop.\n");

        if (restrictionPreview != null && restrictionPreview.restricted) {

            sb.append("Assistance suspension triggered after the report was produced.\n");

            sb.append("Reason: ").append(restrictionPreview.reason).append("\n");

        } else {

            sb.append("No post-analysis assistance suspension was triggered in this pass.\n");

        }

    }



    private void appendIncidentRegisterSection(StringBuilder sb, JSONObject extraction) {

        sb.append("\n=== Incident Register ===\n");

        JSONArray incidents = extraction != null ? extraction.optJSONArray("incidentRegister") : null;

        if (incidents == null || incidents.length() == 0) {

            sb.append("No concrete incidents resolved from the current extraction pass.\n");

            return;

        }

        for (int i = 0; i < incidents.length(); i++) {

            JSONObject incident = incidents.optJSONObject(i);

            if (incident == null) continue;

            sb.append("• Page ").append(incident.optInt("page", 0))

                    .append(" / ").append(incident.optString("incidentType", "INCIDENT"))

                    .append(" / ").append(incident.optString("severity", "unknown"))

                    .append("\n");

            String actor = incident.optString("actor", "").trim();

            if (!actor.isEmpty()) {

                sb.append("  Actor: ").append(actor).append("\n");

            }

            String narrative = incident.optString("narrative", "").trim();

            if (!narrative.isEmpty()) {

                sb.append("  Narrative: ").append(narrative).append("\n");

            }

            sb.append("  Region: ").append(incident.optString("region", "full-page")).append("\n");

            String description = incident.optString("description", "").trim();

            if (!description.isEmpty()) {

                sb.append("  What happened: ").append(description).append("\n");

            }

        }

    }



    private void appendAnchoredFindingsSection(StringBuilder sb, JSONObject extraction) {

        sb.append("\n=== Anchored Findings ===\n");

        JSONArray findings = extraction != null ? extraction.optJSONArray("anchoredFindings") : null;

        if (findings == null || findings.length() == 0) {

            sb.append("No anchored findings extracted with confidence.\n");

            return;

        }

        for (int i = 0; i < findings.length(); i++) {

            JSONObject finding = findings.optJSONObject(i);

            if (finding == null) continue;

            sb.append("• Page ").append(finding.optInt("page", 0))

                    .append(" / ").append(finding.optString("category", "Finding"))

                    .append("\n");

            JSONArray matchedTerms = finding.optJSONArray("matchedTerms");

            if (matchedTerms != null && matchedTerms.length() > 0) {

                sb.append("  Matched terms: ").append(joinJsonArray(matchedTerms, matchedTerms.length())).append("\n");

            }

            sb.append("  Excerpt: ").append(finding.optString("excerpt", "")).append("\n");

        }

    }



    private void generatePdf() {

        if (selectedFile == null) {

            showDialog(getString(R.string.pdf_generation_failed), getString(R.string.select_file_before_pdf));

            return;

        }



        setBusy(true, getString(R.string.generating_forensic_pdf));

        getBackgroundExecutor().execute(() -> {

            try {

                AnalysisCoordinator.Request analysisRequest = new AnalysisCoordinator.Request();
                analysisRequest.evidenceFiles = getSelectedEvidenceFilesSnapshot();
                analysisRequest.primaryEvidenceFile = selectedFile;
                AnalysisCoordinator.Result analysisResult = analysisCoordinator.run(
                        analysisRequest,
                        buildAnalysisCoordinatorGateway()
                );
                Map<String, String> results = analysisResult.integrityResults;
                AnalysisEngine.ForensicReport report = analysisResult.report;

                JSONObject findingsPayload = analysisResult.findingsPayload;

                AnalyzeCaseUseCase.Request caseRequest = new AnalyzeCaseUseCase.Request();
                caseRequest.mode = AnalyzeCaseUseCase.Mode.PDF_ONLY;
                caseRequest.report = report;
                caseRequest.integrityResults = results;
                caseRequest.primaryEvidenceMeta = analysisResult.primaryEvidenceMeta;
                caseRequest.findingsPayload = findingsPayload;
                caseRequest.includeLegalAdvisory = false;

                AnalyzeCaseUseCase.Result caseResult = analyzeCaseUseCase.run(caseRequest, buildAnalyzeCaseGateway());
                updatePendingArtifactPaths(report.caseId, caseResult);
                File auditorPdf = caseResult.auditorPdf;
                File outFile = caseResult.forensicPdf;
                File readableBriefFile = caseResult.readableBriefFile;
                File policeReadyReportFile = caseResult.policeReadyReportFile;
                File constitutionalNarrativeFile = caseResult.constitutionalNarrativeFile;
                File visualFindingsFile = caseResult.visualFindingsFile;
                File modelAuditLedgerFile = caseResult.modelAuditLedgerFile;
                String auditReport = caseResult.auditReport;
                String humanReadableReport = caseResult.humanReadableReport;
                String readableBriefReport = caseResult.readableBriefReport;
                String policeReadyReport = caseResult.policeReadyReport;
                String visualFindingsMemo = caseResult.visualFindingsMemo;

                saveLastVaultResult(report, readableBriefFile, policeReadyReportFile, outFile, auditorPdf, null, null, null, constitutionalNarrativeFile, null, modelAuditLedgerFile);

                findingsPayload.put("humanReadableReport", humanReadableReport);
                findingsPayload.put("auditorReport", auditReport);
                findingsPayload.put("policeReadyReport", policeReadyReport);
            findingsPayload.put("guardianDecision", report.guardianDecision != null ? report.guardianDecision : new JSONObject());
            findingsPayload.put("forensicConclusion", report.forensicConclusion != null ? report.forensicConclusion : new JSONObject());
            findingsPayload.put("certifiedFindings", report.certifiedFindings != null ? report.certifiedFindings : new JSONArray());
                FindingPublicationNormalizer.applyToReport(report);
                findingsPayload.put("normalizedCertifiedFindings",
                        report.normalizedCertifiedFindings != null ? report.normalizedCertifiedFindings : new JSONArray());
                findingsPayload.put("normalizedCertifiedFindingCount", report.normalizedCertifiedFindingCount);
                findingsPayload.put("jurisdictionResolution", report.jurisdictionResolution != null ? report.jurisdictionResolution : new JSONObject());
                findingsPayload.put("legalAttorneyAnalysis", report.legalAttorneyAnalysis != null ? report.legalAttorneyAnalysis : new JSONObject());
                findingsPayload.put("consensusReview", report.consensusReview != null ? report.consensusReview : new JSONObject());
                findingsPayload.put("evidenceBundleHash", safeValue(report.evidenceBundleHash));
                findingsPayload.put("deterministicRunId", safeValue(report.deterministicRunId));

                GemmaCaseContextStore.save(
                        this,
                        report.caseId,
                        selectedFile != null ? selectedFile.getName() : "unknown",
                        buildGemmaChatContext(report, readableBriefReport, auditReport, findingsPayload.toString(2))
                );

                runOnUiThread(() -> {

                    setBusy(false, null);

                    showVaultAwareDialog(

                            getString(R.string.forensic_report_stored),

                            buildVaultResultNotice(
                                    report,
                                    null,
                                    readableBriefFile,
                                    outFile,
                                    auditorPdf,
                                    null,
                                    null,
                                    visualFindingsFile,
                                    modelAuditLedgerFile,
                                    null,
                                    AssistanceRestrictionManager.load(this)
                            ),

                            readableBriefFile != null ? readableBriefFile : outFile,

                            getString(R.string.open_report),

                            null

                    );

                });

            } catch (Throwable t) {

                String errorMessage = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();

                runOnUiThread(() -> {

                    setBusy(false, null);

                    showDialog(getString(R.string.pdf_generation_failed), errorMessage);

                });

            }

        });

    }



    private void generateSealCertificate() {

        AssistanceRestrictionManager.Snapshot restriction = AssistanceRestrictionManager.load(this);

        if (restriction.restricted) {

            showDialog(

                    getString(R.string.assistance_suspended),

                    getString(R.string.seal_assistance_suspended_format,

                            restriction.caseId, restriction.evidenceHashShort, restriction.reason)

            );

            return;

        }

        if (selectedFile == null) {

            showDialog(getString(R.string.seal_document), getString(R.string.select_source_before_seal));

            return;

        }



        setBusy(true, getString(R.string.sealing_document));

        getBackgroundExecutor().execute(() -> {

            try {

                File outFile = VaultManager.createVaultFile(
                        this,
                        "sealed-evidence",
                        ".pdf",
                        selectedFile != null ? selectedFile.getName() : null
                );

                PDFSealer.SealRequest req = new PDFSealer.SealRequest();

                req.title = "Verum Omnis Sealed Document";

                req.summary = "Original document sealed with constitutional watermark, QR verification, and SHA-512 integrity lock.";

                req.includeQr = true;

                req.includeHash = true;

                req.mode = PDFSealer.DocumentMode.SEAL_ONLY;

                req.evidenceHash = HashUtil.sha512File(selectedFile);

                req.caseId = "seal-" + HashUtil.truncate(req.evidenceHash, 24);

                req.sourceFileName = selectedFile.getName();

                if (intakeSnapshot != null) {

                    req.intakeMetadata = "UTC " + intakeSnapshot.capturedAtUtc

                            + " | Local " + intakeSnapshot.localTime

                            + " | TZ " + intakeSnapshot.timezoneId

                            + " | GPS " + intakeSnapshot.coordinatesLabel();

                }



                boolean renderedSeal = canSealSourceDocument(selectedFile);

                if (renderedSeal) {

                    PDFSealer.generateSealedSourceDocument(this, req, selectedFile, outFile);

                } else {

                    req.legalSummary = "Source type cannot be visually re-rendered on-device, so this output falls back to a seal certificate.";

                    PDFSealer.generateSealedPdf(this, req, outFile);

                }

                runOnUiThread(() -> {

                    setBusy(false, null);

                    showVaultAwareDialog(

                            renderedSeal ? getString(R.string.sealed_document_stored) : getString(R.string.seal_certificate_stored),

                            getString(R.string.seal_output_vault_format,

                                    VaultManager.getVaultDir(this).getAbsolutePath(),

                                    outFile.getAbsolutePath(),

                                    renderedSeal ? getString(R.string.mode_sealed_copy) : getString(R.string.mode_seal_fallback)),

                            outFile,

                            renderedSeal ? getString(R.string.open_sealed_document) : getString(R.string.open_seal),

                            null

                    );

                });

            } catch (Throwable t) {

                String errorMessage = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();

                runOnUiThread(() -> {

                    setBusy(false, null);

                    showDialog(getString(R.string.seal_certificate_failed), errorMessage);

                });

            }

        });

    }



    private boolean canSealSourceDocument(File file) {
        return PDFSealer.canRenderSourceDocument(file);

    }



    private File copyUriToCache(Uri uri) {

        try {

            String name = getFileName(uri);

            File outFile = new File(getCacheDir(), name);

            try (InputStream in = getContentResolver().openInputStream(uri);

                 FileOutputStream out = new FileOutputStream(outFile)) {

                if (in == null) throw new RuntimeException("InputStream is null");

                byte[] buf = new byte[8192];

                int len;

                while ((len = in.read(buf)) != -1) {

                    out.write(buf, 0, len);

                }

            }

            return outFile;

        } catch (Exception e) {

            throw new RuntimeException("Failed to copy file: " + e.getMessage(), e);

        }

    }



    private String getFileName(Uri uri) {

        String result = "unknown";

        try (android.database.Cursor cursor =

                     getContentResolver().query(uri, null, null, null, null)) {

            if (cursor != null && cursor.moveToFirst()) {

                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);

                if (nameIndex >= 0) {

                    result = cursor.getString(nameIndex);

                }

            }

        }

        return result;

    }



    private void showDialog(String title, String message) {

        TextView textView = new TextView(this);

        textView.setText(message);

        textView.setPadding(40, 40, 40, 40);

        textView.setTextIsSelectable(true);



        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);

        scrollView.addView(textView);



        new AlertDialog.Builder(this)

                .setTitle(title)

                .setView(scrollView)

                .setPositiveButton(R.string.ok, null)

                .setCancelable(true)

                .show();

    }



    private void showVaultAwareDialog(

            String title,

            String message,

            File primaryFile,

            String primaryLabel,

            File secondaryFile

    ) {

        TextView textView = new TextView(this);

        textView.setText(message);

        textView.setPadding(40, 40, 40, 40);

        textView.setTextIsSelectable(true);



        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);

        scrollView.addView(textView);



        AlertDialog.Builder builder = new AlertDialog.Builder(this)

                .setTitle(title)

                .setView(scrollView)

                .setPositiveButton(R.string.ok, null)

                .setNeutralButton(R.string.action_open_vault, (dialog, which) -> openVault());



        if (primaryFile != null) {

            builder.setNegativeButton(primaryLabel, (dialog, which) -> openFile(primaryFile));

        } else if (secondaryFile != null) {

            builder.setNegativeButton(R.string.open_package, (dialog, which) -> openFile(secondaryFile));

        }



        builder.setCancelable(true).show();

    }



    private void updateBoundaryText() {

        boundaryView.setText(R.string.boundary_text_plain);

    }



    private void updateBusinessConstitution() {

        BusinessConstitutionManager.Snapshot snapshot = BusinessConstitutionManager.load(this);

        heroHeadlineView.setText(R.string.hero_headline);

        constitutionStatusView.setText(R.string.constitution_status_plain);

        businessModelView.setText(R.string.business_model_plain);

        casesView.setText(getString(

                R.string.front_page_status_format,

                snapshot.courtValidationLabel,

                snapshot.activeCasesLabel

        ));
        if (mainScreenBridge != null) {
            mainScreenBridge.setCaseStatusText(casesView.getText().toString());
        }

    }



    private void updateGemmaPolicy() {

        StringBuilder sb = new StringBuilder(getString(R.string.gemma_front_page_plain));

        AssistanceRestrictionManager.Snapshot restriction = AssistanceRestrictionManager.load(this);

        if (restriction.restricted) {

            sb.append("\n\n").append(getString(R.string.assistance_suspended_footer_format,

                    restriction.caseId, restriction.reason));

        }

        gemmaStatusView.setText(sb.toString());
        if (mainScreenBridge != null) {
            mainScreenBridge.setGemmaStatusText(sb.toString());
        }

    }



    private void setupIntroVideo() {

        if (introVideoView == null || introVideoButton == null) {

            return;

        }

        introVideoPrepared = false;

        introVideoButton.setEnabled(false);

        introVideoButton.setText(R.string.loading_intro_video);

        introVideoView.setMediaController(null);

        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.landingpage_intro);

        introVideoView.setVideoURI(uri);

        introVideoView.setOnPreparedListener(mediaPlayer -> {

            introVideoPrepared = true;

            mediaPlayer.setLooping(false);

            introVideoView.seekTo(1);

            introVideoButton.setEnabled(true);

            introVideoButton.setText(R.string.action_play_intro_video);

        });

        introVideoView.setOnCompletionListener(mediaPlayer -> {

            introVideoView.seekTo(1);

            introVideoButton.setText(R.string.action_replay_intro_video);

        });

        introVideoView.setOnErrorListener((mediaPlayer, what, extra) -> {

            introVideoPrepared = false;

            introVideoButton.setEnabled(false);

            introVideoButton.setText(R.string.intro_video_unavailable);

            return true;

        });

        introVideoButton.setOnClickListener(v -> toggleIntroVideoPlayback());

        introVideoView.setOnClickListener(v -> toggleIntroVideoPlayback());

    }



    private void toggleIntroVideoPlayback() {

        if (introVideoView == null || !introVideoPrepared) {

            return;

        }

        if (introVideoView.isPlaying()) {

            introVideoView.pause();

            introVideoButton.setText(R.string.action_resume_intro_video);

        } else {

            introVideoView.start();

            introVideoButton.setText(R.string.action_pause_intro_video);

        }

    }

    private void appendDocumentIntegrityFindingsSection(StringBuilder sb, JSONArray findings) {
        if (findings == null || findings.length() == 0) {
            sb.append("No first-class document integrity findings were extracted in this pass.\n");
            return;
        }
        for (int i = 0; i < findings.length(); i++) {
            JSONObject finding = findings.optJSONObject(i);
            if (finding == null) continue;
            sb.append("• Page ").append(finding.optInt("page", 0))
                    .append(" / ").append(finding.optString("type", "DOCUMENT_INTEGRITY_FLAG"))
                    .append(" / actor ").append(finding.optString("actor", "unresolved actor"))
                    .append(" / confidence ").append(finding.optString("confidence", "LOW"));
            JSONArray anchors = finding.optJSONArray("anchors");
            if (anchors != null && anchors.length() > 1) {
                sb.append(" / anchors ").append(anchors.length());
            }
            sb.append("\n  Excerpt: ").append(finding.optString("excerpt", ""));
            JSONArray matchedTerms = finding.optJSONArray("matchedTerms");
            if (matchedTerms != null && matchedTerms.length() > 0) {
                sb.append("\n  Matched terms: ").append(joinJsonArray(matchedTerms, 4));
            }
            sb.append("\n");
        }
    }

    private void appendTimelineAnchorRegisterSection(StringBuilder sb, JSONArray timelineAnchors) {
        appendEvidenceRegisterSection(
                sb,
                timelineAnchors,
                14,
                "No compact timeline anchor register was extracted in this pass."
        );
    }

    private void appendActorConductRegisterSection(StringBuilder sb, JSONArray actorConduct) {
        appendEvidenceRegisterSection(
                sb,
                actorConduct,
                12,
                "No actor-conduct register entry survived the current extraction filter."
        );
    }

    private void appendFinancialExposureRegisterSection(StringBuilder sb, JSONArray financialExposure) {
        appendEvidenceRegisterSection(
                sb,
                filterStructuredFinancialExposure(financialExposure),
                12,
                "No compact financial-exposure register entry survived the current extraction filter."
        );
    }

    private JSONArray filterStructuredFinancialExposure(JSONArray financialExposure) {
        JSONArray result = new JSONArray();
        if (financialExposure == null) {
            return result;
        }
        for (int i = 0; i < financialExposure.length(); i++) {
            JSONObject item = financialExposure.optJSONObject(i);
            if (isStructuredFinancialEvidenceItem(item)) {
                result.put(item);
            }
        }
        return result;
    }

    private boolean isStructuredFinancialEvidenceItem(JSONObject item) {
        if (item == null) {
            return false;
        }
        String actor = item.optString("actor", "").trim();
        String amount = item.optString("amount", "").trim();
        String basis = item.optString("basis", "").trim();
        String counterparty = item.optString("counterparty", "").trim();
        if (actor.isEmpty() || amount.isEmpty() || basis.isEmpty() || counterparty.isEmpty()) {
            return false;
        }
        if ("unresolved actor".equalsIgnoreCase(actor) || "unresolved actor".equalsIgnoreCase(counterparty)) {
            return false;
        }
        return !"DEMAND_ONLY".equalsIgnoreCase(basis);
    }

    private void appendNarrativeThemeRegisterSection(StringBuilder sb, JSONArray narrativeThemes) {
        appendEvidenceRegisterSection(
                sb,
                narrativeThemes,
                10,
                "No distilled narrative theme survived the current extraction filter."
        );
    }

    private void appendEvidenceRegisterSection(StringBuilder sb, JSONArray items, int limit, String emptyMessage) {
        if (items == null || items.length() == 0) {
            sb.append(emptyMessage).append("\n");
            return;
        }
        int emitted = 0;
        for (int i = 0; i < items.length() && emitted < limit; i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String summary = firstNonEmpty(
                    item.optString("summary", null),
                    item.optString("narrative", null),
                    item.optString("excerpt", null),
                    item.optString("description", null)
            );
            if (summary == null || summary.trim().isEmpty()) {
                continue;
            }
            sb.append("• Page ").append(item.optInt("page", 0));
            String actor = item.optString("actor", "").trim();
            if (!actor.isEmpty() && !"unresolved actor".equalsIgnoreCase(actor)) {
                sb.append(" / actor ").append(actor);
            }
            String label = firstNonEmpty(
                    item.optString("eventType", null),
                    item.optString("conductType", null),
                    item.optString("amountCategory", null),
                    item.optString("theme", null),
                    item.optString("type", null),
                    item.optString("category", null)
            );
            if (label != null && !label.trim().isEmpty()) {
                sb.append(" / ").append(label);
            }
            String confidence = item.optString("confidence", "").trim();
            if (!confidence.isEmpty()) {
                sb.append(" / ").append(confidence);
            }
            sb.append("\n  ").append(summary.trim());
            JSONArray anchors = item.optJSONArray("anchors");
            if (anchors != null && anchors.length() > 1) {
                sb.append("\n  Anchors: ").append(anchors.length());
            }
            String amount = item.optString("amount", "").trim();
            if (!amount.isEmpty()) {
                sb.append("\n  Amount: ").append(amount);
            }
            sb.append("\n");
            emitted++;
        }
        if (emitted == 0) {
            sb.append(emptyMessage).append("\n");
        }
    }

    private void scrollLandingPageToTop() {

        if (mainScrollView == null) {

            return;

        }

        mainScrollView.post(() -> {

            mainScrollView.scrollTo(0, 0);

            mainScrollView.fullScroll(View.FOCUS_UP);

        });

    }



    private void openBundledConstitution() {

        try {

            File file = materializeAssetToCache(

                    "docs/VERUM_OMNIS_CONSTITUTIONAL_CHARTER_WITH_STATEMENT_20260320.PDF",

                    "verum-omnis-constitutional-charter.pdf"

            );

            openFile(file);

        } catch (Exception e) {

            showDialog(

                    getString(R.string.open_failed),

                    getString(

                            R.string.could_not_open_file_format,

                            "VERUM_OMNIS_CONSTITUTIONAL_CHARTER_WITH_STATEMENT_20260320.PDF",

                            e.getMessage()

                    )

            );

        }

    }



    private File materializeAssetToCache(String assetPath, String outputName) throws Exception {

        File outFile = new File(getCacheDir(), outputName);

        try (InputStream in = getAssets().open(assetPath);

             FileOutputStream out = new FileOutputStream(outFile)) {

            byte[] buffer = new byte[8192];

            int read;

            while ((read = in.read(buffer)) != -1) {

                out.write(buffer, 0, read);

            }

        }

        return outFile;

    }



    private EvidenceIntakeCapture.Snapshot captureOrRequestLocation(

            ActivityResultLauncher<String[]> locationPermissionLauncher

    ) {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

                == PackageManager.PERMISSION_GRANTED

                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

                == PackageManager.PERMISSION_GRANTED) {

            return EvidenceIntakeCapture.capture(this);

        }



        locationPermissionLauncher.launch(new String[]{

                Manifest.permission.ACCESS_FINE_LOCATION,

                Manifest.permission.ACCESS_COARSE_LOCATION

        });



        return EvidenceIntakeCapture.capture(this);

    }



    private void updateSelectedFileText() {

        if (selectedFile == null) {

            selectedFileView.setText(R.string.no_file_selected);
            if (mainScreenBridge != null) {
                mainScreenBridge.setSelectedEvidenceText(getString(R.string.no_file_selected));
            }
            syncComposeState();

            return;

        }



        List<File> evidenceFiles = getSelectedEvidenceFilesSnapshot();
        StringBuilder sb = new StringBuilder();
        if (evidenceFiles.size() > 1) {
            sb.append(getString(
                    R.string.selected_files_summary_format,
                    evidenceFiles.size(),
                    selectedFile.getName()
            ));
        } else {
            sb.append(selectedFile.getName());
        }

        if (intakeSnapshot != null) {

            sb.append("\nUTC: ").append(intakeSnapshot.capturedAtUtc);

            sb.append("\nLocal: ").append(intakeSnapshot.localTime)

                    .append(" (").append(intakeSnapshot.timezoneId).append(")");

            sb.append("\nGPS: ").append(intakeSnapshot.coordinatesLabel());

        }

        selectedFileView.setText(sb.toString());
        if (mainScreenBridge != null) {
            mainScreenBridge.setSelectedEvidenceText(sb.toString());
        }
        syncComposeState();

    }



    private List<File> getSelectedEvidenceFilesSnapshot() {

        ArrayList<File> snapshot = new ArrayList<>();
        for (File file : selectedEvidenceFiles) {
            if (file != null && file.exists()) {
                snapshot.add(file);
            }
        }
        if (snapshot.isEmpty() && selectedFile != null && selectedFile.exists()) {
            snapshot.add(selectedFile);
        }
        return snapshot;

    }

    private void applyInvestigatorContext(AnalysisEngine.ForensicReport report) {
        if (report == null) {
            return;
        }
        String raw = investigatorSuppliedFactsInput == null ? "" : investigatorSuppliedFactsInput.trim();
        if (raw.isEmpty()) {
            report.investigatorContext = null;
            report.investigatorSuppliedFacts = null;
            return;
        }

        JSONArray facts = new JSONArray();
        try {
            String[] parts = raw.split("\\r?\\n+");
            for (String part : parts) {
                String fact = part == null ? "" : part.trim();
                if (fact.isEmpty()) {
                    continue;
                }
                JSONObject item = new JSONObject();
                item.put("statement", fact);
                item.put("sourceType", "INVESTIGATOR_SUPPLIED");
                item.put("sourceDisclosure", "Provided manually during intake by the investigator/user and not treated as sealed-source proof unless separately anchored.");
                item.put("anchorStatus", "UNANCHORED_IN_CURRENT_ARTIFACT");
                item.put("certificationEligible", false);
                facts.put(item);
            }
            if (facts.length() == 0) {
                report.investigatorContext = null;
                report.investigatorSuppliedFacts = null;
                return;
            }

            JSONObject context = new JSONObject();
            context.put("summary", "Investigator-supplied context was added at intake and is disclosed separately from document-proven findings.");
            context.put("disclosure", "These statements may guide review, but they do not become certified findings unless anchored in the sealed record or corroborated by later evidence.");
            context.put("capturedAtLocal", intakeSnapshot != null ? intakeSnapshot.localTime : "");
            context.put("factsCount", facts.length());

            report.investigatorContext = context;
            report.investigatorSuppliedFacts = facts;
        } catch (Exception e) {
            Log.w("MainActivity", "Failed to attach investigator-supplied context", e);
            report.investigatorContext = null;
            report.investigatorSuppliedFacts = null;
        }
    }



    private String buildEvidenceSelectionLabel(List<File> evidenceFiles) {

        if (evidenceFiles == null || evidenceFiles.isEmpty()) {
            return "unknown";
        }
        if (evidenceFiles.size() == 1) {
            return evidenceFiles.get(0).getName();
        }
        return evidenceFiles.get(0).getName() + " +" + (evidenceFiles.size() - 1) + " more";

    }



    private void refreshCaseManagementSection() {

        if (scanFolderManager == null || caseFileManager == null) {
            return;
        }

        try {
            List<ScanFolder> folders = scanFolderManager.listScanFolders();
            List<CaseFile> caseFiles = caseFileManager.listCaseFiles();

            if (scanFoldersStatusView != null) {
                scanFoldersStatusView.setText(getString(R.string.scan_folders_count_format, folders.size()));
            }

            if (caseFilesStatusView != null) {
                caseFilesStatusView.setText(getString(R.string.case_files_count_format, caseFiles.size()));
            }

            renderScanFolders(folders);
            renderCaseFiles(caseFiles);
        } catch (Exception e) {
            if (scanFoldersStatusView != null) {
                scanFoldersStatusView.setText(safeMessage(e));
            }
        }

    }



    private void promptForNewScan() {

        EditText input = new EditText(this);
        input.setHint(R.string.new_scan_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        new AlertDialog.Builder(this)
                .setTitle(R.string.new_scan_title)
                .setMessage(R.string.new_scan_message)
                .setView(input)
                .setPositiveButton(R.string.action_new_scan, (dialog, which) -> {
                    pendingScanFolderName = input.getText() != null ? input.getText().toString().trim() : "";
                    pendingAction = PendingAction.CREATE_SCAN;
                    filePickerLauncher.launch(new String[]{"*/*"});
                })
                .setNegativeButton(R.string.ok, null)
                .show();

    }



    private void promptForMergeSelectedScans() {

        if (getSelectedScanFolders().isEmpty()) {
            showDialog(getString(R.string.merge_scans_title), getString(R.string.merge_scans_none_selected));
            return;
        }

        EditText input = new EditText(this);
        input.setHint(R.string.merge_scans_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        new AlertDialog.Builder(this)
                .setTitle(R.string.merge_scans_title)
                .setMessage(R.string.merge_scans_message)
                .setView(input)
                .setPositiveButton(R.string.action_merge_selected_scans, (dialog, which) -> {
                    List<ScanFolder> selectedFolders = getSelectedScanFolders();
                    if (selectedFolders.isEmpty()) {
                        showDialog(getString(R.string.merge_scans_title), getString(R.string.merge_scans_none_selected));
                        return;
                    }
                    String caseName = input.getText() != null ? input.getText().toString().trim() : "";
                    if (caseName.isEmpty()) {
                        caseName = "Merged Case";
                    }
                    mergeSelectedScansInBackground(selectedFolders, caseName);
                })
                .setNegativeButton(R.string.ok, null)
                .show();

    }



    private void createScanFolderFromSelectedFile() {

        if (selectedFile == null) {
            showDialog(getString(R.string.new_scan_title), getString(R.string.no_file_selected));
            return;
        }

        final String scanName = (pendingScanFolderName != null && !pendingScanFolderName.trim().isEmpty())
                ? pendingScanFolderName.trim()
                : selectedFile.getName();
        pendingScanFolderName = null;

        setBusy(true, getString(R.string.creating_scan_folder));
        getBackgroundExecutor().execute(() -> {
            try {
                ScanFolder folder = scanFolderManager.createScanFolder(scanName);
                File evidenceCopy = scanFolderManager.copyEvidenceIntoScanFolder(selectedFile, folder);
                AnalysisEngine.ForensicReport report = AnalysisEngine.analyze(this, evidenceCopy, this::publishBusyProgress);
                applyInvestigatorContext(report);
                Map<String, String> results = IntegrityChecker.runChecks(this);
                JSONObject findingsPayload = ForensicPackageWriter.buildPayload(evidenceCopy, report, intakeSnapshot);
                String auditReport = buildFallbackHumanReport(report, results);
                String humanReadableReport = generateHumanReadableReport(
                        report,
                        results,
                        findingsPayload.toString(2),
                        auditReport
                );
                findingsPayload.put("humanReadableReport", humanReadableReport);
                findingsPayload.put("scanFolderName", folder.getName());
                findingsPayload.put("scanFolderPath", folder.getFolderPath());
                writeJsonToFile(new File(folder.getFolderPath(), "forensic_report.json"), findingsPayload);
                writeTextToFile(new File(folder.getFolderPath(), "audit_report.txt"), auditReport);
                writeTextToFile(new File(folder.getFolderPath(), "human_report.txt"), humanReadableReport);
                if (report.nativeEvidence != null) {
                    writeJsonToFile(new File(folder.getFolderPath(), "native_evidence.json"), report.nativeEvidence);
                }
                if (report.constitutionalExtraction != null) {
                    writeJsonToFile(new File(folder.getFolderPath(), "constitutional_extraction.json"), report.constitutionalExtraction);
                }
                scanFolderManager.saveScanFolder(folder);
                runOnUiThread(() -> {
                    setBusy(false, null);
                    refreshCaseManagementSection();
                    showDialog(
                            getString(R.string.scan_created_title),
                            getString(
                                    R.string.scan_created_message_format,
                                    folder.getFolderPath(),
                                    evidenceCopy.getAbsolutePath()
                            )
                    );
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    setBusy(false, null);
                    showDialog(getString(R.string.scan_creation_failed), safeMessage(e));
                });
            }
        });

    }



    private void handlePostSelectionAction(PendingAction action) {

        switch (action) {

            case RUN_ANALYSIS:

                runUnifiedVerification();

                break;

            case CREATE_SCAN:

                createScanFolderFromSelectedFile();

                break;

            case SEAL_DOCUMENT:

                generateSealCertificate();

                break;

            case GENERATE_FORENSIC_PDF:

                generatePdf();

                break;

            case VERIFY_SCANNED_SEAL:

                verifyScannedSealAgainstSelectedFile();

                break;

            case SELECT_EVIDENCE:

                break;

            case NONE:

            default:

                runUnifiedVerification();

                break;

        }

    }

    private void promptForSupplementalFactsThen(PendingAction action) {
        if (action != PendingAction.RUN_ANALYSIS && action != PendingAction.CREATE_SCAN) {
            handlePostSelectionAction(action);
            return;
        }

        EditText input = new EditText(this);
        input.setHint("Optional: add facts or context that are not fully documented in this file");
        input.setMinLines(4);
        input.setMaxLines(8);
        input.setText(investigatorSuppliedFactsInput);
        input.setSelection(input.getText() != null ? input.getText().length() : 0);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        new AlertDialog.Builder(this)
                .setTitle("Investigator Context")
                .setMessage("Add optional investigator-supplied facts. These will be disclosed as external or partially anchored context and will not be certified unless separately anchored.")
                .setView(input)
                .setPositiveButton("Continue", (dialog, which) -> {
                    investigatorSuppliedFactsInput = input.getText() != null ? input.getText().toString().trim() : "";
                    handlePostSelectionAction(action);
                })
                .setNeutralButton("Skip", (dialog, which) -> {
                    investigatorSuppliedFactsInput = "";
                    handlePostSelectionAction(action);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }



    private void mergeSelectedScansInBackground(List<ScanFolder> folders, String caseName) {

        setBusy(true, getString(R.string.merging_scan_folders));
        getBackgroundExecutor().execute(() -> {
            try {
                CaseFile caseFile = caseFileManager.mergeScanFolders(folders, caseName, this::publishBusyProgress);
                runOnUiThread(() -> {
                    selectedScanFolderPaths.clear();
                    setBusy(false, null);
                    refreshCaseManagementSection();
                    showDialog(
                            getString(R.string.case_file_created_title),
                            getString(
                                    R.string.case_file_created_message_format,
                                    caseFile.getName(),
                                    caseFile.getMergedFolderPath()
                            )
                    );
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, null);
                    showDialog(getString(R.string.case_file_creation_failed), safeMessage(e));
                });
            }
        });

    }



    private void sealCaseFileInBackground(CaseFile caseFile) {

        if (caseFile == null) {
            return;
        }

        setBusy(true, getString(R.string.sealing_case_file));
        getBackgroundExecutor().execute(() -> {
            try {
                CaseFile sealed = caseFileManager.sealCaseFile(caseFile);
                runOnUiThread(() -> {
                    setBusy(false, null);
                    refreshCaseManagementSection();
                    showVaultAwareDialog(
                            getString(R.string.case_file_sealed_title),
                            getString(
                                    R.string.case_file_sealed_message_format,
                                    safeValue(sealed.getSealedNarrativePath()),
                                    safeValue(sealed.getNarrativeHash()),
                                    safeValue(sealed.getBlockchainAnchor())
                            ),
                            sealed.getSealedNarrativePath() != null ? new File(sealed.getSealedNarrativePath()) : null,
                            getString(R.string.action_view_case_file),
                            sealed.getMergedForensicJsonPath() != null ? new File(sealed.getMergedForensicJsonPath()) : null
                    );
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, null);
                    showDialog(getString(R.string.case_file_seal_failed), safeMessage(e));
                });
            }
        });

    }



    private List<ScanFolder> getSelectedScanFolders() {

        List<ScanFolder> selectedFolders = new ArrayList<>();
        if (scanFolderManager == null) {
            return selectedFolders;
        }

        for (ScanFolder folder : scanFolderManager.listScanFolders()) {
            if (folder != null
                    && folder.getFolderPath() != null
                    && selectedScanFolderPaths.contains(folder.getFolderPath())) {
                selectedFolders.add(folder);
            }
        }

        return selectedFolders;

    }



    private void renderScanFolders(List<ScanFolder> folders) {

        if (scanFoldersContainer == null) {
            return;
        }

        scanFoldersContainer.removeAllViews();
        if (folders == null || folders.isEmpty()) {
            scanFoldersContainer.addView(buildSectionHintView(R.string.scan_folders_empty));
            return;
        }

        for (ScanFolder folder : folders) {
            CheckBox box = new CheckBox(this);
            box.setTextColor(ContextCompat.getColor(this, R.color.pure_white));
            box.setButtonTintList(ContextCompat.getColorStateList(this, R.color.verum_gold));
            String dateLabel = formatCaseDate(folder.getScanDate());
            int fileCount = folder.getFilePaths() != null ? folder.getFilePaths().size() : 0;
            boolean valid = scanFolderManager.validateScanFolder(folder);
            String label = getString(
                    R.string.scan_folder_item_format,
                    safeValue(folder.getName()),
                    dateLabel,
                    fileCount
            );
            if (!valid) {
                label = label + "\nInvalid or empty scan folder.";
            }
            box.setText(label);
            box.setChecked(folder.getFolderPath() != null && selectedScanFolderPaths.contains(folder.getFolderPath()));
            String folderPath = folder.getFolderPath();
            box.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (folderPath == null) {
                    return;
                }
                if (isChecked) {
                    selectedScanFolderPaths.add(folderPath);
                } else {
                    selectedScanFolderPaths.remove(folderPath);
                }
            });
            scanFoldersContainer.addView(box);
        }

    }



    private void renderCaseFiles(List<CaseFile> caseFiles) {

        if (caseFilesContainer == null) {
            return;
        }

        caseFilesContainer.removeAllViews();
        if (caseFiles == null || caseFiles.isEmpty()) {
            caseFilesContainer.addView(buildSectionHintView(R.string.case_files_empty));
            return;
        }

        for (CaseFile caseFile : caseFiles) {
            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            wrapperParams.bottomMargin = 18;
            wrapper.setLayoutParams(wrapperParams);

            TextView summary = new TextView(this);
            summary.setTextColor(ContextCompat.getColor(this, R.color.pure_white));
            String summaryText = getString(
                    R.string.case_file_item_format,
                    safeValue(caseFile.getName()),
                    formatCaseDate(caseFile.getCreationDate()),
                    caseFile.getScanFolders() != null ? caseFile.getScanFolders().size() : 0
            );
            String bundleStatus = caseFile.getSealedBundlePath() != null && !caseFile.getSealedBundlePath().trim().isEmpty()
                    ? "Ready"
                    : getString(R.string.not_sealed_yet);
            String narrativeStatus = caseFile.getSealedNarrativePath() != null && !caseFile.getSealedNarrativePath().trim().isEmpty()
                    ? "Ready"
                    : getString(R.string.not_sealed_yet);
            summaryText += "\nBundle: " + bundleStatus
                    + "\nNarrative: " + narrativeStatus;
            summary.setText(summaryText);
            wrapper.addView(summary);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            actionsParams.topMargin = 10;
            actions.setLayoutParams(actionsParams);

            Button sealButton = new Button(this);
            sealButton.setText(R.string.action_make_case_file);
            sealButton.setAllCaps(false);
            sealButton.setOnClickListener(v -> sealCaseFileInBackground(caseFile));
            LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            leftParams.rightMargin = 8;
            actions.addView(sealButton, leftParams);

            Button viewButton = new Button(this);
            viewButton.setText(R.string.action_view_case_file);
            viewButton.setAllCaps(false);
            viewButton.setOnClickListener(v -> openCaseFileArtifact(caseFile));
            LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            actions.addView(viewButton, rightParams);

            wrapper.addView(actions);
            caseFilesContainer.addView(wrapper);
        }

    }



    private TextView buildSectionHintView(int textRes) {

        TextView view = new TextView(this);
        view.setText(textRes);
        view.setTextColor(ContextCompat.getColor(this, R.color.verum_text_soft));
        view.setLineSpacing(0f, 1.2f);
        return view;

    }



    private void openCaseFileArtifact(CaseFile caseFile) {

        if (caseFile == null) {
            return;
        }

        List<File> files = collectCaseFileArtifacts(caseFile);
        if (files.isEmpty() && caseFile != null && caseFile.getMergedForensicJsonPath() != null) {
            try {
                caseFileManager.sealCaseFile(caseFile);
                files = collectCaseFileArtifacts(caseFile);
            } catch (Exception ignored) {
            }
        }
        if (files.isEmpty()) {
            showDialog(getString(R.string.open_failed), getString(R.string.case_file_unavailable));
            return;
        }
        File primaryBundle = findPrimaryCaseBundle(caseFile, files);
        if (primaryBundle != null) {
            openFile(primaryBundle);
            return;
        }
        final List<File> resolvedFiles = files;
        CharSequence[] labels = new CharSequence[files.size()];
        for (int i = 0; i < files.size(); i++) {
            labels[i] = caseArtifactLabel(files.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle(caseFile.getName())
                .setItems(labels, (dialog, which) -> openFile(resolvedFiles.get(which)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();

    }

    private List<File> collectCaseFileArtifacts(CaseFile caseFile) {
        List<File> files = new ArrayList<>();
        if (caseFile == null) {
            return files;
        }
        addCaseArtifactIfExists(files, caseFile.getSealedBundlePath());
        addCaseArtifactIfExists(files, caseFile.getSealedNarrativePath());
        if (caseFile.getMergedFolderPath() != null && !caseFile.getMergedFolderPath().trim().isEmpty()) {
            File root = new File(caseFile.getMergedFolderPath());
            collectCasePdfArtifacts(root, files);
        }
        return files;
    }

    private File findPrimaryCaseBundle(CaseFile caseFile, List<File> files) {
        if (caseFile != null && caseFile.getSealedBundlePath() != null && !caseFile.getSealedBundlePath().trim().isEmpty()) {
            File bundle = new File(caseFile.getSealedBundlePath());
            if (bundle.exists() && !bundle.isDirectory()) {
                return bundle;
            }
        }
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file != null && "sealed_case_bundle.pdf".equalsIgnoreCase(file.getName())) {
                return file;
            }
        }
        return null;
    }

    private void collectCasePdfArtifacts(File file, List<File> files) {
        if (file == null || files == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                collectCasePdfArtifacts(child, files);
            }
            return;
        }
        if (file.getName().toLowerCase(Locale.US).endsWith(".pdf")) {
            addCaseArtifactIfExists(files, file.getAbsolutePath());
        }
    }

    private void addCaseArtifactIfExists(List<File> files, String path) {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        File file = new File(path);
        if (!file.exists() || file.isDirectory()) {
            return;
        }
        for (File existing : files) {
            if (existing.getAbsolutePath().equalsIgnoreCase(file.getAbsolutePath())) {
                return;
            }
        }
        files.add(file);
    }

    private String caseArtifactLabel(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        if (name.equals("sealed_case_bundle.pdf")) {
            return "Case Bundle PDF";
        }
        if (name.equals("sealed_narrative.pdf")) {
            return "Case Narrative PDF";
        }
        if (name.contains("human") && name.endsWith(".pdf")) {
            return "Human Report PDF";
        }
        if (name.contains("audit") && name.endsWith(".pdf")) {
            return "Audit Report PDF";
        }
        if (name.contains("sealed") && name.endsWith(".pdf")) {
            return "Sealed Evidence PDF";
        }
        return file.getName();
    }



    private String formatCaseDate(Object value) {

        if (value == null) {
            return "Unknown";
        }
        if (value instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) value)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
        return String.valueOf(value);

    }



    private void writeJsonToFile(File file, JSONObject object) throws Exception {

        writeTextToFile(file, object != null ? object.toString(2) : "{}");

    }



    private void writeTextToFile(File file, String content) throws Exception {

        if (file == null) {
            return;
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write((content != null ? content : "").getBytes(StandardCharsets.UTF_8));
        }

    }



    private ArchivedScanPackage archiveCompletedRunAsScan(
            AnalysisEngine.ForensicReport report,
            JSONObject findingsPayload,
            String auditReport,
            String humanReadableReport,
            File sealedEvidenceFile,
            File auditorPdf,
            File findingsFile,
            File forensicPdf,
            File readableBriefFile,
            File policeReadyReportFile,
            File constitutionalNarrativeFile,
            File contradictionEngineFile,
            File legalAdvisoryFile,
            File visualFindingsFile,
            File meshFile
    ) {

        List<File> evidenceFilesForScan = getSelectedEvidenceFilesSnapshot();
        if (evidenceFilesForScan.isEmpty() || scanFolderManager == null) {
            return null;
        }

        try {
            ArchivedScanPackage archived = new ArchivedScanPackage();
            File primaryEvidence = evidenceFilesForScan.get(0);
            String sourceName = primaryEvidence.getName();
            int dot = sourceName.lastIndexOf('.');
            String scanName = dot > 0 ? sourceName.substring(0, dot) : sourceName;
            if (evidenceFilesForScan.size() > 1) {
                scanName = scanName + "_batch_" + evidenceFilesForScan.size();
            }
            ScanFolder folder = scanFolderManager.createScanFolder(scanName);

            JSONArray evidenceFiles = new JSONArray();
            for (File sourceEvidence : evidenceFilesForScan) {
                File copiedEvidence = scanFolderManager.copyEvidenceIntoScanFolder(sourceEvidence, folder);
                JSONObject evidenceItem = new JSONObject();
                evidenceItem.put("name", copiedEvidence.getName());
                evidenceItem.put("path", copiedEvidence.getAbsolutePath());
                evidenceItem.put("sha512", HashUtil.sha512File(copiedEvidence));
                evidenceFiles.put(evidenceItem);
            }

            JSONArray artifacts = new JSONArray();
            archived.scanFolderRoot = new File(folder.getFolderPath());
            archived.sealedEvidenceFile = copyArtifactIntoScanFolder(folder, sealedEvidenceFile, "sealed-evidence", artifacts);
            archived.auditorPdf = copyArtifactIntoScanFolder(folder, auditorPdf, "forensic-audit-report", artifacts);
            archived.findingsFile = copyArtifactIntoScanFolder(folder, findingsFile, "forensic-findings", artifacts);
            archived.forensicPdf = copyArtifactIntoScanFolder(folder, forensicPdf, "human-forensic-report", artifacts);
            archived.readableBriefFile = copyArtifactIntoScanFolder(folder, readableBriefFile, "readable-forensic-brief", artifacts);
            archived.policeReadyReportFile = copyArtifactIntoScanFolder(folder, policeReadyReportFile, "police-ready-forensic-report", artifacts);
            archived.constitutionalNarrativeFile = copyArtifactIntoScanFolder(folder, constitutionalNarrativeFile, "constitutional-vault-report", artifacts);
            archived.contradictionEngineFile = copyArtifactIntoScanFolder(folder, contradictionEngineFile, "contradiction-engine-report", artifacts);
            archived.legalAdvisoryFile = copyArtifactIntoScanFolder(folder, legalAdvisoryFile, "legal-advisory", artifacts);
            archived.visualFindingsFile = copyArtifactIntoScanFolder(folder, visualFindingsFile, "visual-findings", artifacts);
            archived.meshFile = copyArtifactIntoScanFolder(folder, meshFile, "rnd-mesh", artifacts);

            File manifestFile = new File(folder.getFolderPath(), "scan_manifest.json");
            JSONObject manifest = new JSONObject();
            manifest.put("scanName", folder.getName());
            manifest.put("scanDate", folder.getScanDate() != null ? folder.getScanDate().toString() : "");
            manifest.put("folderPath", folder.getFolderPath());
            manifest.put("sourceCaseId", safeValue(report != null ? report.caseId : null));
            manifest.put("sourceEvidenceHash", safeValue(report != null ? report.evidenceHash : null));
            manifest.put("evidenceFiles", evidenceFiles);
            manifest.put("artifacts", artifacts);
            manifest.put("auditReportSummary", auditReport != null ? auditReport : "");
            manifest.put("humanReadableReportPath", forensicPdf != null ? forensicPdf.getAbsolutePath() : "");
            manifest.put("readableBriefPath", readableBriefFile != null ? readableBriefFile.getAbsolutePath() : "");
            manifest.put("policeReadyReportPath", policeReadyReportFile != null ? policeReadyReportFile.getAbsolutePath() : "");
            manifest.put("constitutionalNarrativePath", constitutionalNarrativeFile != null ? constitutionalNarrativeFile.getAbsolutePath() : "");
            manifest.put("contradictionEnginePath", contradictionEngineFile != null ? contradictionEngineFile.getAbsolutePath() : "");
            manifest.put("findingsPayloadPath", findingsFile != null ? findingsFile.getAbsolutePath() : "");
            if (findingsPayload != null) {
                manifest.put("guardianDecision", findingsPayload.optJSONObject("guardianDecision"));
                manifest.put("certifiedFindings", findingsPayload.optJSONArray("certifiedFindings"));
            }
            writeJsonToFile(manifestFile, manifest);
            archived.manifestFile = manifestFile;
            writeTextToFile(new File(folder.getFolderPath(), "human_report_snapshot.txt"), humanReadableReport);
            String archivedFolderPath = folder.getFolderPath();
            folder.setFilePaths(scanFolderManager.listScanFolders().stream()
                    .filter(item -> item != null && archivedFolderPath.equals(item.getFolderPath()))
                    .findFirst()
                    .map(ScanFolder::getFilePaths)
                    .orElse(folder.getFilePaths()));
            scanFolderManager.saveScanFolder(folder);
            return archived;
        } catch (Exception e) {
            Log.w("MainActivity", "Failed to archive completed run as scan folder", e);
            return null;
        }

    }



    private File copyArtifactIntoScanFolder(
            ScanFolder folder,
            File source,
            String baseName,
            JSONArray manifestArray
    ) throws Exception {

        if (folder == null || source == null || !source.exists()) {
            return null;
        }

        String extension = "";
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            extension = name.substring(dot);
        }
        File target = scanFolderManager.createArtifactFile(folder, baseName, extension);
        copyFile(source, target);
        copySealManifestIntoScanFolder(folder, source, baseName, manifestArray);

        if (manifestArray != null) {
            JSONObject item = new JSONObject();
            item.put("label", baseName);
            item.put("path", target.getAbsolutePath());
            item.put("sha512", HashUtil.sha512File(target));
            manifestArray.put(item);
        }

        return target;

    }

    private void copySealManifestIntoScanFolder(
            ScanFolder folder,
            File source,
            String baseName,
            JSONArray manifestArray
    ) throws Exception {
        File sealManifest = companionSealManifest(source);
        if (folder == null || sealManifest == null || !sealManifest.exists()) {
            return;
        }
        File target = scanFolderManager.createArtifactFile(folder, baseName + "-seal-manifest", ".json");
        copyFile(sealManifest, target);
        if (manifestArray != null) {
            JSONObject item = new JSONObject();
            item.put("label", baseName + "-seal-manifest");
            item.put("path", target.getAbsolutePath());
            item.put("sha512", HashUtil.sha512File(target));
            manifestArray.put(item);
        }
    }

    private File companionSealManifest(File source) {
        if (source == null) {
            return null;
        }
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(source.getParentFile(), base + ".seal.json");
    }

    private void deleteLooseVaultArtifact(File original, File archivedCopy) {
        if (original == null || archivedCopy == null) {
            return;
        }
        if (original.getAbsolutePath().equals(archivedCopy.getAbsolutePath())) {
            return;
        }
        if (original.exists() && !original.delete()) {
            Log.w("MainActivity", "Failed to delete loose vault artifact: " + original.getAbsolutePath());
        }
        File sealManifest = companionSealManifest(original);
        if (sealManifest != null && sealManifest.exists() && !sealManifest.delete()) {
            Log.w("MainActivity", "Failed to delete loose seal manifest: " + sealManifest.getAbsolutePath());
        }
    }



    private void copyFile(File source, File target) throws Exception {

        if (source == null || target == null) {
            return;
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }

    }



    private String safeMessage(Throwable e) {

        if (e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()) {
            return e == null ? "unknown error" : e.getClass().getSimpleName();
        }
        return e.getMessage().trim();

    }



    private void scanVerumQr() {

        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()

                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)

                .enableAutoZoom()

                .build();



        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);

        scanner.startScan()

                .addOnSuccessListener(barcode -> {

                    String rawValue = barcode.getRawValue();

                    if (rawValue == null || rawValue.trim().isEmpty()) {

                        showDialog(getString(R.string.scan_qr_title), getString(R.string.scan_qr_empty));

                        return;

                    }

                    scannedSealPayload = parseSealPayload(rawValue.trim());

                    if (selectedFile != null) {

                        verifyScannedSealAgainstSelectedFile();

                    } else {

                        pendingAction = PendingAction.VERIFY_SCANNED_SEAL;

                        selectedFileView.setText(getString(R.string.scan_qr_pick_file));

                        filePickerLauncher.launch(new String[]{"*/*"});

                    }

                })

                .addOnCanceledListener(() ->

                        showDialog(getString(R.string.scan_qr_title), getString(R.string.scan_qr_canceled)))

                .addOnFailureListener(e ->

                        showDialog(

                                getString(R.string.scan_qr_title),

                                getString(R.string.scan_qr_failed_format, e.getMessage())

                        ));

    }



    private SealPayload parseSealPayload(String rawValue) {

        SealPayload payload = new SealPayload();

        payload.rawValue = rawValue;

        try {

            JSONObject obj = new JSONObject(rawValue);

            if ("verum-omnis-seal".equalsIgnoreCase(obj.optString("scheme", ""))) {

                payload.fullHash = obj.optString("sha512", "");

                payload.shortHash = HashUtil.truncate(payload.fullHash, 8);

                payload.sealId = obj.optString("sealId", "");

                payload.mode = obj.optString("mode", "");

                payload.sourceFileName = obj.optString("sourceFileName", "");

                payload.jurisdiction = obj.optString("jurisdiction", "");

                return payload;

            }

        } catch (Exception ignored) {

        }



        if (rawValue.startsWith("verum://hash/")) {

            payload.shortHash = rawValue.substring("verum://hash/".length()).trim();

            payload.mode = "LEGACY_SHORT_HASH";

            return payload;

        }



        if (rawValue.startsWith("verum://seal?")) {

            String[] pairs = rawValue.substring("verum://seal?".length()).split("&");

            for (String pair : pairs) {

                String[] split = pair.split("=", 2);

                if (split.length != 2) continue;

                if ("sha512".equalsIgnoreCase(split[0])) {

                    payload.fullHash = split[1];

                    payload.shortHash = HashUtil.truncate(payload.fullHash, 8);

                } else if ("sealId".equalsIgnoreCase(split[0])) {

                    payload.sealId = split[1];

                } else if ("mode".equalsIgnoreCase(split[0])) {

                    payload.mode = split[1];

                }

            }

        }

        return payload;

    }



    private void verifyScannedSealAgainstSelectedFile() {

        if (selectedFile == null) {

            showDialog(getString(R.string.seal_verification_result), getString(R.string.no_file_selected));

            return;

        }

        if (scannedSealPayload == null) {

            showDialog(getString(R.string.seal_verification_result), getString(R.string.scan_qr_empty));

            return;

        }



        setBusy(true, "Verifying scanned seal...");

        getBackgroundExecutor().execute(() -> {

            try {

                String actualHash = HashUtil.sha512File(selectedFile);

                String message;

                if (scannedSealPayload.fullHash != null && !scannedSealPayload.fullHash.isEmpty()) {

                    if (actualHash.equalsIgnoreCase(scannedSealPayload.fullHash)) {

                        message = getString(

                                R.string.seal_verified_format,

                                emptyFallback(scannedSealPayload.mode, "UNKNOWN"),

                                emptyFallback(scannedSealPayload.sealId, "Unspecified"),

                                emptyFallback(scannedSealPayload.sourceFileName, selectedFile.getName()),

                                emptyFallback(scannedSealPayload.jurisdiction, "Unspecified")

                        );

                    } else {

                        message = getString(

                                R.string.seal_mismatch_format,

                                emptyFallback(scannedSealPayload.sealId, "Unspecified"),

                                scannedSealPayload.fullHash,

                                actualHash

                        );

                    }

                } else if (scannedSealPayload.shortHash != null && !scannedSealPayload.shortHash.isEmpty()

                        && HashUtil.truncate(actualHash, 8).equalsIgnoreCase(scannedSealPayload.shortHash)) {

                    message = getString(

                            R.string.seal_legacy_match_format,

                            scannedSealPayload.shortHash,

                            actualHash

                    );

                } else {

                    message = getString(

                            R.string.seal_payload_invalid_format,

                            scannedSealPayload.rawValue == null ? "" : scannedSealPayload.rawValue

                    );

                }



                runOnUiThread(() -> {

                    setBusy(false, null);

                    showDialog(getString(R.string.seal_verification_result), message);

                });

            } catch (Exception e) {

                runOnUiThread(() -> {

                    setBusy(false, null);

                    showDialog(

                            getString(R.string.seal_verification_result),

                            getString(R.string.scan_qr_failed_format, e.getMessage())

                    );

                });

            }

        });

    }



    private String emptyFallback(String value, String fallback) {

        return value == null || value.trim().isEmpty() ? fallback : value;

    }



    private void syncComposeState() {

        if (mainScreenBridge == null) {
            return;
        }

        AssistanceRestrictionManager.Snapshot restriction = AssistanceRestrictionManager.load(this);
        mainScreenBridge.setLegalAdvisoryEnabled(
                legalAdvisoryToggle != null && legalAdvisoryToggle.isChecked()
        );
        mainScreenBridge.setLegalAdvisoryToggleEnabled(!composeUiBusy && !restriction.restricted);
        mainScreenBridge.setBoundedRenderState(
                boundedRenderSettings.getBoundedHumanBriefEnabled(),
                boundedRenderSettings.getBoundedPoliceSummaryEnabled(),
                boundedRenderSettings.getBoundedLegalStandingEnabled(),
                !composeUiBusy && !restriction.restricted,
                buildBoundedRenderStatusLabel()
        );
        mainScreenBridge.setActionAvailability(
                !composeUiBusy && !restriction.restricted,
                !composeUiBusy && !restriction.restricted && selectedFile != null,
                !composeUiBusy && !restriction.restricted,
                !composeUiBusy,
                !composeUiBusy,
                !composeUiBusy && !restriction.restricted,
                true
        );
        if (selectedFileView != null && selectedFileView.getText() != null) {
            mainScreenBridge.setSelectedEvidenceText(selectedFileView.getText().toString());
        }
    }



    @Override
    public void onSelectEvidence() {

        if (multiFilePickerLauncher == null) {
            return;
        }
        pendingAction = PendingAction.SELECT_EVIDENCE;
        multiFilePickerLauncher.launch(new String[]{"*/*"});
    }



    @Override
    public void onRunAnalysis() {

        if (selectedFile == null) {
            showDialog(getString(R.string.unified_report), getString(R.string.no_file_selected_analysis_skipped));
            return;
        }
        promptForSupplementalFactsThen(PendingAction.RUN_ANALYSIS);
    }



    @Override
    public void onSealDocument() {

        if (filePickerLauncher == null) {
            return;
        }
        pendingAction = PendingAction.SEAL_DOCUMENT;
        filePickerLauncher.launch(new String[]{"*/*"});
    }



    @Override
    public void onScanQr() {

        scanVerumQr();
    }



    @Override
    public void onOpenVault() {

        openVault();
    }



    @Override
    public void onOpenGemma() {

        startActivity(new Intent(this, GemmaActivity.class));
    }



    @Override
    public void onReadConstitution() {

        openBundledConstitution();
    }



    @Override
    public void onLegalAdvisoryChanged(boolean enabled) {

        if (legalAdvisoryToggle != null) {
            legalAdvisoryToggle.setChecked(enabled);
        }
        if (mainScreenBridge != null) {
            mainScreenBridge.setLegalAdvisoryEnabled(enabled);
        }
        syncComposeState();
    }

    @Override
    public void onBoundedHumanBriefChanged(boolean enabled) {

        if (enabled) {
            boundedHumanBriefDialog = new AlertDialog.Builder(this)
                    .setTitle("Enable bounded human brief")
                    .setMessage(
                            "This enables offline bounded rendering for the human brief only.\n" +
                                    "Deterministic findings remain authoritative.\n" +
                                    "Rendered output will be audited and logged.\n" +
                                    "If rendering fails or audit fails, legacy output will be used automatically."
                    )
                    .setPositiveButton("Enable", (dialog, which) -> {
                        boundedRenderSettings = new BoundedRenderSettings(
                                true,
                                false,
                                false,
                                boundedRenderSettings.getBoundedRenderAuditRequired(),
                                boundedRenderSettings.getBoundedRenderFailClosed()
                        );
                        boundedRenderSettings.persist(getApplicationContext());
                        syncComposeState();
                    })
                    .setNegativeButton("Cancel", null)
                    .create();
            boundedHumanBriefDialog.setOnDismissListener(dialog -> boundedHumanBriefDialog = null);
            boundedHumanBriefDialog.show();
            return;
        }

        boundedRenderSettings = new BoundedRenderSettings(
                false,
                false,
                false,
                boundedRenderSettings.getBoundedRenderAuditRequired(),
                boundedRenderSettings.getBoundedRenderFailClosed()
        );
        boundedRenderSettings.persist(getApplicationContext());
        syncComposeState();
    }

    @Override
    public void onBoundedPoliceSummaryChanged(boolean enabled) {

        if (enabled) {
            showDialog(
                    "Bounded police summary",
                    "This lane is present structurally but remains held back until the bounded human brief has been field-tested."
            );
        }
        syncComposeState();
    }

    @Override
    public void onBoundedLegalStandingChanged(boolean enabled) {

        if (enabled) {
            showDialog(
                    "Bounded legal standing",
                    "This lane is present structurally but remains held back while legal framing stays under tighter constitutional review."
            );
        }
        syncComposeState();
    }

    private String buildBoundedRenderStatusLabel() {

        if (boundedRenderSettings.getBoundedHumanBriefEnabled()) {
            return boundedRenderSettings.getBoundedRenderAuditRequired()
                    ? "BOUNDED + AUDITED"
                    : "BOUNDED";
        }
        return "LEGACY";
    }



    private void openVault() {

        startActivity(new Intent(this, VaultActivity.class));

    }



    private void applyRestrictionState() {

        AssistanceRestrictionManager.Snapshot restriction = AssistanceRestrictionManager.load(this);

        boolean restricted = restriction.restricted;

        if (gemmaBtn != null) gemmaBtn.setEnabled(!restricted);
        if (legalAdvisoryToggle != null) legalAdvisoryToggle.setEnabled(!restricted);

        if (selectBtn != null) selectBtn.setEnabled(!restricted);
        if (newScanBtn != null) newScanBtn.setEnabled(!restricted);
        if (mergeScansBtn != null) mergeScansBtn.setEnabled(!restricted);

        if (sealBtn != null) sealBtn.setEnabled(!restricted);

        if (verifyQrBtn != null) verifyQrBtn.setEnabled(true);

        if (vaultBtn != null) vaultBtn.setEnabled(true);



        if (restricted) {

            selectedFileView.setText(getString(R.string.assistance_suspended_short_format,

                    restriction.caseId, restriction.evidenceHashShort, restriction.reason));

        } else {

            updateSelectedFileText();

        }

        updateGemmaPolicy();
        refreshCaseManagementSection();
        syncComposeState();

    }



    private void openFile(File file) {

        try {

            Uri uri = FileProvider.getUriForFile(

                    this,

                    getPackageName() + ".fileprovider",

                    file

            );

            Intent intent = new Intent(Intent.ACTION_VIEW);

            intent.setDataAndType(uri, getMimeType(file));

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(intent, "Open with"));

        } catch (Exception e) {

            showDialog(getString(R.string.open_failed),

                    getString(R.string.could_not_open_file_format, file.getAbsolutePath(), e.getMessage()));

        }

    }



    private String getMimeType(File file) {

        String name = file.getName().toLowerCase(Locale.US);
        if (name.endsWith(".json") || name.endsWith(".txt")) {
            return "text/plain";
        }

        String extension = MimeTypeMap.getFileExtensionFromUrl(file.getName());

        if (extension != null) {

            String mime = MimeTypeMap.getSingleton()

                    .getMimeTypeFromExtension(extension.toLowerCase(Locale.US));

            if (mime != null) {

                return mime;

            }

        }

        return "application/octet-stream";

    }



    private String generateHumanReadableReport(

            AnalysisEngine.ForensicReport report,

            Map<String, String> integrityResults,

            String findingsJson,

            String auditorReport

    ) {

        String fallbackReport = buildHumanForensicReport(report, integrityResults);
        String gemmaPrompt = buildGemmaForensicPrompt(report, integrityResults, findingsJson, auditorReport);
        if (gemmaPrompt == null || gemmaPrompt.trim().isEmpty()) {
            Log.d("MainActivity", "Gemma human report skipped: prompt was empty, using fallback report.");
            return fallbackReport;
        }

        if (shouldDeferGemmaNarrativeGeneration()) {
            savePendingGemmaNarrative(report, fallbackReport, gemmaPrompt, auditorReport, findingsJson);
            Log.d("MainActivity", "Gemma human report deferred for recovery after scan.");
            return fallbackReport;
        }

        ExecutorService gemmaExecutor = Executors.newSingleThreadExecutor();
        long timeoutSeconds = computeGemmaReportTimeoutSeconds(report);
        try {
            Log.d("MainActivity", "Gemma human report generation started with timeoutSeconds=" + timeoutSeconds);
            Future<String> gemmaFuture = gemmaExecutor.submit(() ->
                    GemmaRuntime.getInstance().generateResponseBlocking(getApplicationContext(), gemmaPrompt));
            String gemmaResponse = gemmaFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            String sanitized = sanitizeGemmaReport(gemmaResponse);
            if (looksLikeUsableGemmaReport(sanitized)) {
                Log.d("MainActivity", "Gemma human report accepted.");
                return ensureHumanReportForensicConclusion(report, sanitized);
            }
            Log.w("MainActivity", "Gemma human report rejected after validation, using fallback report.");
        } catch (TimeoutException timeout) {
            Log.w("MainActivity", "Gemma human report timed out, using fallback report.", timeout);
        } catch (Throwable error) {
            Log.e("MainActivity", "Gemma human report failed, using fallback report.", error);
            // Fall back to the deterministic report builder if the local Gemma runtime errors.
        } finally {
            gemmaExecutor.shutdownNow();
        }

        Log.d("MainActivity", "Using deterministic fallback human report.");
        return ensureHumanReportForensicConclusion(report, fallbackReport);

    }

    private String generateReadableFindingsBriefReport(
            AnalysisEngine.ForensicReport report,
            String findingsJson,
            String auditReport,
            String humanReadableReport,
            String legalAdvisory,
            String visualFindingsMemo
    ) {
        String fallbackBrief = buildReadableFindingsBriefFallback(
                report,
                humanReadableReport,
                auditReport,
                legalAdvisory,
                visualFindingsMemo
        );
        if (!ENABLE_GEMMA_SCAN_PIPELINE_GENERATION) {
            Log.i("MainActivity", "Using deterministic readable brief in scan pipeline to avoid Gemma runtime crashes.");
            return fallbackBrief;
        }
        String prompt = buildGemmaReadableBriefPrompt(
                report,
                findingsJson,
                auditReport,
                humanReadableReport,
                legalAdvisory,
                visualFindingsMemo
        );
        if (prompt == null || prompt.trim().isEmpty()) {
            return fallbackBrief;
        }

        ExecutorService gemmaExecutor = Executors.newSingleThreadExecutor();
        long timeoutSeconds = Math.max(90L, Math.min(240L, computeGemmaReportTimeoutSeconds(report)));
        try {
            Future<String> gemmaFuture = gemmaExecutor.submit(() ->
                    GemmaRuntime.getInstance().generateResponseBlocking(getApplicationContext(), prompt));
            String response = gemmaFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            String sanitized = sanitizeGemmaReport(response);
            if (looksLikeUsableReadableFindingsBrief(sanitized)) {
                return ensureReadableBriefForensicConclusion(report, sanitized);
            }
            Log.w("MainActivity", "Readable findings brief rejected after validation, using fallback brief.");
        } catch (TimeoutException timeout) {
            Log.w("MainActivity", "Readable findings brief timed out, using fallback brief.", timeout);
        } catch (Throwable error) {
            Log.e("MainActivity", "Readable findings brief generation failed, using fallback brief.", error);
        } finally {
            gemmaExecutor.shutdownNow();
        }
        return ensureReadableBriefForensicConclusion(report, fallbackBrief);
    }

    private String generatePoliceReadyReport(AnalysisEngine.ForensicReport report) {
        if (report == null) {
            return "";
        }
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        String intakeDateTime = intakeSnapshot != null
                ? "UTC " + intakeSnapshot.capturedAtUtc + " | Local " + intakeSnapshot.localTime
                : safeValue(report.generatedAt);
        return PoliceReadyReportBuilder.render(
                report,
                assembled,
                selectedFile != null ? selectedFile.getName() : "unknown",
                intakeDateTime
        );
    }

    private String buildGemmaReadableBriefPrompt(
            AnalysisEngine.ForensicReport report,
            String findingsJson,
            String auditReport,
            String humanReadableReport,
            String legalAdvisory,
            String visualFindingsMemo
    ) {
        if (report == null) {
            return "";
        }
        String narrativePacket = ConstitutionalNarrativePacketBuilder.buildReadableBriefPacket(
                report,
                selectedFile != null ? selectedFile.getName() : "unknown",
                auditReport,
                findingsJson,
                visualFindingsMemo
        );
        if (narrativePacket.trim().isEmpty()) {
            return "";
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("Write a plain-language Verum Omnis readable findings brief for the vault.\n")
                .append("This brief is generated from deterministic forensic engine outputs and must remain constitutional.\n")
                .append("Use only facts from the constitutional narrative packet below.\n")
                .append("Do not invent facts, law, actors, dates, or outcomes.\n")
                .append("Do not state money totals, rent totals, goodwill values, monthly calculations, or extrapolations unless the exact figure is explicitly present in the truth packet.\n")
                .append("Do not say how a pattern was discovered unless that explanation is explicitly present in the packet below.\n")
                .append("Do not use any legal-advisory narrative, email draft, or other downstream narrative layer as source material.\n")
                .append("If a fact is investigator-supplied or unanchored, label it as DISCLOSED EXTERNAL CONTEXT.\n")
                .append("Keep the language easy for a police officer, prosecutor, or ordinary user to understand.\n")
                .append("Do not talk about AI, model behavior, or internal engine mechanics.\n")
                .append("Use short paragraphs and concise bullets only when they help clarity.\n")
                .append("Use these headings exactly:\n")
                .append("Executive Summary\n")
                .append("What Happened\n")
                .append("Who Was Harmed and Who Is Implicated\n")
                .append("What Is Verified\n")
                .append("What Still Needs Review\n")
                .append("Evidence Pages to Read First\n")
                .append("Immediate Next Actions\n")
                .append("Integrity and Vault Record\n\n")
                .append(narrativePacket);
        return prompt.toString();
    }

    private boolean looksLikeUsableReadableFindingsBrief(String reportText) {
        if (reportText == null || reportText.trim().length() < 900) {
            return false;
        }
        String lower = reportText.toLowerCase(Locale.ROOT);
        return lower.contains("executive summary")
                && lower.contains("what happened")
                && lower.contains("what is verified")
                && lower.contains("integrity and vault record");
    }

    private String buildReadableFindingsBriefFallback(
            AnalysisEngine.ForensicReport report,
            String humanReadableReport,
            String auditReport,
            String legalAdvisory,
            String visualFindingsMemo
    ) {
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        JSONObject truthFrame = TruthInCodeEngine.buildTruthFrame(report, assembled);
        JSONObject forensicConclusion = preferredForensicConclusion(report);
        AnchorBoundNarrativeBuilder.Narrative anchorNarrative =
                AnchorBoundNarrativeBuilder.build(forensicConclusion, truthFrame);
        List<JSONObject> readableEvidence = certifiedFindingNarratives(report, 6);
        ReadableBriefModel.Input input = new ReadableBriefModel.Input();
        input.caseId = safeValue(report != null ? report.caseId : null);
        input.jurisdictionName = safeValue(report != null ? report.jurisdictionName : null);
        input.jurisdictionCode = safeValue(report != null ? report.jurisdiction : null);
        input.evidenceHashPrefix = safeValue(report != null ? report.evidenceHashShort : null);
        input.guardianApprovedCertifiedFindingCount = assembled.guardianApprovedCertifiedFindingCount;
        input.verifiedContradictionCount = assembled.verifiedContradictionCount;
        input.candidateContradictionCount = assembled.candidateContradictionCount;
        input.truthSummary = firstNonEmpty(anchorNarrative.summary, truthFrame.optString("whatHappened", ""));
        input.conclusionWhatHappened = extractConclusionWhatHappened(forensicConclusion);
        input.conclusionPrimaryImplicatedActor = extractPrimaryImplicatedActor(forensicConclusion);
        input.conclusionWhy = !anchorNarrative.keyFindings.isEmpty()
                ? new ArrayList<>(anchorNarrative.keyFindings)
                : extractConclusionWhy(forensicConclusion, 4);
        input.conclusionTimelineHighlights = new ArrayList<>(anchorNarrative.timelineHighlights);
        input.conclusionOtherLinkedActors = extractOtherLinkedActors(forensicConclusion, input.conclusionPrimaryImplicatedActor);
        input.conclusionProven = extractConclusionProven(forensicConclusion, 5);
        input.conclusionBoundary = forensicConclusion.optString("publicationBoundary", "");
        input.conclusionPages = extractConclusionPages(forensicConclusion, assembled.readFirstPages, 6);
        input.patternLine = firstNonEmpty(anchorNarrative.summary, buildHumanRepeatPatternLine(readableEvidence));
        input.completedHarmLine = firstNonEmpty(
                anchorNarrative.implicationSummary,
                buildHumanCompletedHarmLine(report != null ? report.forensicSynthesis : null, readableEvidence)
        );
        input.patternOriginLine = !anchorNarrative.timelineHighlights.isEmpty()
                ? anchorNarrative.timelineHighlights.get(0)
                : buildHumanPatternOriginLine(readableEvidence);
        input.fallbackSummary = clipReportText(firstNonEmpty(report != null ? report.summary : null, truthFrame.optString("whatHappened", ""), input.patternLine), 1200);
        input.suppressRoleNarration = shouldSuppressCoreRoleNarration(assembled);
        input.primaryHarmedParty = assembled.primaryHarmedParty;
        input.actorConclusion = assembled.actorConclusion;
        input.contradictionPosture = assembled.contradictionPosture;
        input.otherAffectedParties = new ArrayList<>(assembled.otherAffectedParties);
        input.offenceFindings = new ArrayList<>(assembled.offenceFindings);
        input.behaviouralFindings = new ArrayList<>(assembled.behaviouralFindings);
        input.visualFindings = new ArrayList<>(assembled.visualFindings);
        input.certifiedFindings = collectReadableBriefFindingEntries(assembled, 5);
        input.reviewItems = collectReadableBriefReviewItems(report);
        input.readFirstPages = new ArrayList<>(assembled.readFirstPages);
        input.evidencePageHints = collectReadableBriefEvidenceHints(assembled);
        input.immediateNextActions = buildReadableBriefNextActions();
        input.visualExcerpt = clipReportText(visualFindingsMemo, 1200);
        input.auditExcerpt = clipReportText(auditReport, 1200);

        ReadableBriefModel model = ReadableBriefBuilder.build(input);
        return finalizePublishedNarrative(report, ReadableBriefBuilder.render(model));
    }

    private List<ReadableBriefModel.FindingEntry> collectReadableBriefFindingEntries(
            ForensicReportAssembler.Assembly assembled,
            int limit
    ) {
        List<ReadableBriefModel.FindingEntry> out = new ArrayList<>();
        if (assembled == null || assembled.issueGroups == null) {
            return out;
        }
        for (ForensicReportAssembler.IssueCard card : assembled.issueGroups) {
            if (card == null) {
                continue;
            }
            out.add(new ReadableBriefModel.FindingEntry(
                    safeValue(card.title),
                    safeValue(card.summary),
                    safeValue(card.whyItMatters),
                    card.evidencePages
            ));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private JSONObject preferredForensicConclusion(AnalysisEngine.ForensicReport report) {
        if (report == null) {
            return new JSONObject();
        }
        return report.forensicConclusion != null
                ? report.forensicConclusion
                : ForensicConclusionEngine.buildJson(report);
    }

    private String extractPrimaryImplicatedActor(JSONObject forensicConclusion) {
        JSONArray actors = forensicConclusion != null ? forensicConclusion.optJSONArray("implicatedActors") : null;
        if (actors == null) {
            return "";
        }
        for (int i = 0; i < actors.length(); i++) {
            JSONObject actor = actors.optJSONObject(i);
            if (actor == null) {
                continue;
            }
            if ("PRIMARY_IMPLICATED".equalsIgnoreCase(actor.optString("role", ""))) {
                return safeValue(actor.optString("actor", ""));
            }
        }
        return "";
    }

    private String extractConclusionWhatHappened(JSONObject forensicConclusion) {
        JSONObject proposition = extractPrimaryProposition(forensicConclusion);
        if (proposition != null) {
            String rendered = renderForensicPropositionLine(proposition);
            if (!rendered.isEmpty()) {
                return rendered;
            }
        }
        return forensicConclusion != null && forensicConclusion.optJSONArray("whatHappened") != null
                ? forensicConclusion.optJSONArray("whatHappened").optString(0, "")
                : forensicConclusion != null ? forensicConclusion.optString("strongestConclusion", "") : "";
    }

    private List<String> extractConclusionWhy(JSONObject forensicConclusion, int limit) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        JSONArray propositions = forensicConclusion != null ? forensicConclusion.optJSONArray("forensicPropositions") : null;
        if (propositions != null) {
            for (int i = 0; i < propositions.length() && out.size() < limit; i++) {
                JSONObject proposition = propositions.optJSONObject(i);
                if (proposition == null) {
                    continue;
                }
                String conduct = clipReportText(proposition.optString("conduct", ""), 220);
                if (!conduct.trim().isEmpty()) {
                    out.add(conduct.trim());
                }
            }
        }
        JSONArray certifiedConduct = forensicConclusion != null ? forensicConclusion.optJSONArray("certifiedForensicConduct") : null;
        if (certifiedConduct != null) {
            for (int i = 0; i < certifiedConduct.length() && out.size() < limit; i++) {
                String line = clipReportText(certifiedConduct.optString(i, ""), 220);
                if (!line.trim().isEmpty()) {
                    out.add(line.trim());
                }
            }
        }
        JSONArray actors = forensicConclusion != null ? forensicConclusion.optJSONArray("implicatedActors") : null;
        if (actors == null) {
            return new ArrayList<>(out);
        }
        for (int i = 0; i < actors.length(); i++) {
            JSONObject actor = actors.optJSONObject(i);
            if (actor == null || !"PRIMARY_IMPLICATED".equalsIgnoreCase(actor.optString("role", ""))) {
                continue;
            }
            JSONArray basis = actor.optJSONArray("basis");
            if (basis == null) {
                break;
            }
            for (int j = 0; j < basis.length() && out.size() < limit; j++) {
                String line = clipReportText(basis.optString(j, ""), 220);
                if (!line.trim().isEmpty() && !looksLikeGenericActorBasis(line)) {
                    out.add(line.trim());
                }
            }
            break;
        }
        return new ArrayList<>(out);
    }

    private List<String> extractOtherLinkedActors(JSONObject forensicConclusion, String primaryActor) {
        List<String> out = new ArrayList<>();
        JSONArray actors = forensicConclusion != null ? forensicConclusion.optJSONArray("implicatedActors") : null;
        if (actors == null) {
            return out;
        }
        for (int i = 0; i < actors.length() && out.size() < 5; i++) {
            JSONObject actor = actors.optJSONObject(i);
            if (actor == null) {
                continue;
            }
            String name = safeValue(actor.optString("actor", ""));
            if (name.equals("unknown") || name.equalsIgnoreCase(primaryActor)) {
                continue;
            }
            out.add(name);
        }
        return out;
    }

    private List<String> extractConclusionProven(JSONObject forensicConclusion, int limit) {
        List<String> out = new ArrayList<>();
        JSONArray whatHappened = forensicConclusion != null ? forensicConclusion.optJSONArray("whatHappened") : null;
        if (whatHappened != null) {
            for (int i = 0; i < whatHappened.length() && out.size() < limit; i++) {
                String line = clipReportText(whatHappened.optString(i, ""), 220);
                if (!line.trim().isEmpty()) {
                    out.add(line.trim());
                }
            }
        }
        return out;
    }

    private List<String> extractConclusionList(JSONObject forensicConclusion, String key, int limit) {
        List<String> out = new ArrayList<>();
        JSONArray values = forensicConclusion != null ? forensicConclusion.optJSONArray(key) : null;
        if (values == null) {
            return out;
        }
        for (int i = 0; i < values.length() && out.size() < limit; i++) {
            String line = clipReportText(values.optString(i, ""), 220);
            if (!line.trim().isEmpty()) {
                out.add(line.trim());
            }
        }
        return out;
    }

    private boolean looksLikeGenericActorBasis(String line) {
        String lower = lowerUs(line);
        return lower.startsWith("the sealed record links this actor to")
                || lower.startsWith("this party is separately carried as an affected party")
                || lower.contains("current publication layer");
    }

    private List<String> extractConclusionPages(
            JSONObject forensicConclusion,
            List<String> fallbackPages,
            int limit
    ) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        JSONArray propositions = forensicConclusion != null ? forensicConclusion.optJSONArray("forensicPropositions") : null;
        if (propositions != null) {
            for (int i = 0; i < propositions.length() && out.size() < limit; i++) {
                JSONObject proposition = propositions.optJSONObject(i);
                if (proposition == null) {
                    continue;
                }
                JSONArray pages = proposition.optJSONArray("anchorPages");
                if (pages == null) {
                    continue;
                }
                for (int j = 0; j < pages.length() && out.size() < limit; j++) {
                    int page = pages.optInt(j, 0);
                    if (page > 0) {
                        out.add(String.valueOf(page));
                    }
                }
            }
        }
        JSONArray actors = forensicConclusion != null ? forensicConclusion.optJSONArray("implicatedActors") : null;
        if (actors != null) {
            for (int i = 0; i < actors.length() && out.size() < limit; i++) {
                JSONObject actor = actors.optJSONObject(i);
                if (actor == null) {
                    continue;
                }
                JSONArray pages = actor.optJSONArray("anchorPages");
                if (pages == null) {
                    continue;
                }
                for (int j = 0; j < pages.length() && out.size() < limit; j++) {
                    int page = pages.optInt(j, 0);
                    if (page > 0) {
                        out.add(String.valueOf(page));
                    }
                }
            }
        }
        if (out.isEmpty() && fallbackPages != null) {
            for (String page : fallbackPages) {
                if (page != null && !page.trim().isEmpty()) {
                    out.add(page.trim());
                }
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return new ArrayList<>(out);
    }

    private JSONObject extractPrimaryProposition(JSONObject forensicConclusion) {
        JSONArray propositions = forensicConclusion != null ? forensicConclusion.optJSONArray("forensicPropositions") : null;
        if (propositions == null) {
            return null;
        }
        for (int i = 0; i < propositions.length(); i++) {
            JSONObject proposition = propositions.optJSONObject(i);
            if (proposition == null) {
                continue;
            }
            String actor = safeValue(proposition.optString("actor", ""));
            String conduct = safeValue(proposition.optString("conduct", ""));
            JSONArray pages = proposition.optJSONArray("anchorPages");
            if (!actor.isEmpty() && !conduct.isEmpty() && pages != null && pages.length() > 0) {
                return proposition;
            }
        }
        return null;
    }

    private String renderForensicPropositionLine(JSONObject proposition) {
        if (proposition == null) {
            return "";
        }
        String actor = safeValue(proposition.optString("actor", ""));
        String conduct = safeValue(proposition.optString("conduct", ""));
        if (actor.isEmpty() || conduct.isEmpty()) {
            return "";
        }
        return actor + " is linked in the sealed record to " + stripTerminalPunctuation(conduct) + ".";
    }

    private String stripTerminalPunctuation(String value) {
        String cleaned = safeValue(value);
        while (!cleaned.isEmpty()) {
            char last = cleaned.charAt(cleaned.length() - 1);
            if (last == '.' || last == '!' || last == '?') {
                cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
                continue;
            }
            break;
        }
        return cleaned;
    }

    private List<String> assembledReadFirstPages(AnalysisEngine.ForensicReport report) {
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        return assembled != null ? new ArrayList<>(assembled.readFirstPages) : new ArrayList<>();
    }

    private String joinReadableList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        if (values.size() == 2) {
            return values.get(0) + " and " + values.get(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(i == values.size() - 1 ? ", and " : ", ");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private List<String> collectReadableBriefReviewItems(AnalysisEngine.ForensicReport report) {
        List<String> out = new ArrayList<>();
        if (report != null && report.investigatorSuppliedFacts != null && report.investigatorSuppliedFacts.length() > 0) {
            for (int i = 0; i < report.investigatorSuppliedFacts.length() && i < 4; i++) {
                JSONObject item = report.investigatorSuppliedFacts.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                out.add("DISCLOSED EXTERNAL CONTEXT: "
                        + safeValue(item.optString("statement", ""))
                        + " ["
                        + safeValue(item.optString("anchorStatus", "UNANCHORED"))
                        + "]");
            }
        }
        if (out.isEmpty()) {
            out.add("Review the sealed audit report and findings package for unresolved items and supporting gaps.");
        }
        return out;
    }

    private List<String> collectReadableBriefEvidenceHints(ForensicReportAssembler.Assembly assembled) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (assembled != null && assembled.issueGroups != null) {
            for (ForensicReportAssembler.IssueCard issue : assembled.issueGroups) {
                if (issue == null || seen.size() >= 6) {
                    break;
                }
                if (issue.evidencePages == null || issue.evidencePages.isEmpty()) {
                    continue;
                }
                int page = issue.evidencePages.get(0);
                if (page <= 0) {
                    continue;
                }
                String summary = cleanNarrativeText(issue.toPlainLine()).trim();
                if (!summary.isEmpty()) {
                    seen.add("p. " + page + " - " + summary);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    private List<String> buildReadableBriefNextActions() {
        List<String> out = new ArrayList<>();
        out.add("Read the sealed evidence pages listed above before external escalation.");
        out.add("Check the audit report for the full contradiction chain and any failure disclosures.");
        out.add("If a fact is not anchored in the sealed record, treat it as context until supporting evidence is added.");
        return out;
    }

    private void appendBriefLines(StringBuilder sb, AnalysisEngine.ForensicReport report, int limit) {
        if (sb == null) {
            return;
        }
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        int written = 0;
        for (ForensicReportAssembler.IssueCard card : assembled.issueGroups) {
            if (written >= limit) {
                break;
            }
            String line = card.toPlainLine();
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            sb.append("- ").append(line.trim()).append("\n");
            if (!card.whyItMatters.isEmpty()) {
                sb.append("  Why this matters: ").append(card.whyItMatters).append("\n");
            }
            written++;
        }
        if (written == 0) {
            sb.append("- No guardian-approved certified finding summary was available in this pass.\n");
        }
    }

    private JSONArray renderableCertifiedFindings(AnalysisEngine.ForensicReport report) {
        return FindingPublicationNormalizer.renderableCertifiedFindings(report);
    }

    private boolean isRenderableCertifiedEntry(JSONObject normalized) {
        return FindingPublicationNormalizer.isPublicationCertified(normalized);
    }

    private int normalizedCertifiedFindingCount(AnalysisEngine.ForensicReport report) {
        FindingPublicationNormalizer.applyToReport(report);
        return report != null ? report.normalizedCertifiedFindingCount : 0;
    }

    private String buildPublishedFindingNarrative(JSONObject normalizedFinding, boolean technical) {
        if (normalizedFinding == null) {
            return "";
        }
        JSONObject finding = normalizedFinding.optJSONObject("finding");
        if (finding != null) {
            try {
                finding = new JSONObject(finding.toString());
            } catch (JSONException ignored) {
            }
            if (safeValue(finding.optString("summary", "")).isEmpty()) {
                try {
                    finding.put("summary", normalizedFinding.optString("primarySummary", ""));
                } catch (JSONException ignored) {
                }
            }
            if (finding.optInt("page", 0) <= 0 && normalizedFinding.optInt("primaryPage", 0) > 0) {
                try {
                    finding.put("page", normalizedFinding.optInt("primaryPage", 0));
                } catch (JSONException ignored) {
                }
            }
            if (safeValue(finding.optString("actor", "")).isEmpty()) {
                try {
                    finding.put("actor", normalizedFinding.optString("actor", ""));
                } catch (JSONException ignored) {
                }
            }
            String narrative = buildCertifiedFindingNarrative(finding);
            if (narrative != null && !narrative.trim().isEmpty()) {
                return narrative.trim();
            }
        }
        String actor = firstNonEmpty(
                normalizedFinding.optString("actor", null),
                finding != null ? finding.optString("actor", null) : null
        );
        String type = firstNonEmpty(
                normalizedFinding.optString("type", null),
                finding != null ? finding.optString("findingType", null) : null,
                finding != null ? finding.optString("timelineType", null) : null,
                finding != null ? finding.optString("oversightType", null) : null,
                finding != null ? finding.optString("conflictType", null) : null,
                "CERTIFIED_FINDING"
        );
        String summary = firstNonEmpty(
                normalizedFinding.optString("primarySummary", null),
                normalizedFinding.optString("summary", null),
                finding != null ? finding.optString("summary", null) : null,
                finding != null ? finding.optString("narrative", null) : null,
                normalizedFinding.optString("excerpt", null),
                finding != null ? finding.optString("excerpt", null) : null
        );
        StringBuilder line = new StringBuilder();
        if (!actor.isEmpty()) {
            line.append(actor);
        }
        String cleanType = humanizeCertifiedFindingType(type);
        if (!cleanType.isEmpty()) {
            if (line.length() > 0) {
                line.append(" | ");
            }
            line.append(cleanType);
        }
        if (!summary.isEmpty()) {
            if (line.length() > 0) {
                line.append(" | ");
            }
            line.append(summary);
        }
        if (technical) {
            String rawStatus = normalizedFinding.optString("rawFindingStatus", "").trim();
            if (!rawStatus.isEmpty()) {
                if (line.length() > 0) {
                    line.append(" | ");
                }
                line.append("raw status ").append(rawStatus);
            }
        }
        return line.length() == 0 ? "Guardian-approved certified finding" : line.toString();
    }

    private String humanizeCertifiedFindingType(String rawType) {
        if (rawType == null) {
            return "";
        }
        String cleaned = rawType.trim().replace('_', ' ');
        if (cleaned.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1).toLowerCase(Locale.US);
    }

    private String finalizePublishedNarrative(AnalysisEngine.ForensicReport report, String rendered) {
        List<String> errors = FindingPublicationNormalizer.collectRenderConsistencyErrors(report, rendered);
        if (errors.isEmpty()) {
            return rendered;
        }
        StringBuilder out = new StringBuilder();
        out.append("BLOCKING RENDER WARNING\n");
        for (String error : errors) {
            out.append("- ").append(error).append("\n");
        }
        out.append("\n").append(rendered);
        return out.toString().trim();
    }

    private void appendEvidencePageHints(StringBuilder sb, AnalysisEngine.ForensicReport report) {
        if (sb == null) {
            return;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        if (assembled != null && assembled.issueGroups != null) {
            for (ForensicReportAssembler.IssueCard issue : assembled.issueGroups) {
                if (issue == null || seen.size() >= 6) {
                    break;
                }
                if (issue.evidencePages == null || issue.evidencePages.isEmpty()) {
                    continue;
                }
                int page = issue.evidencePages.get(0);
                if (page <= 0) {
                    continue;
                }
                String summary = cleanNarrativeText(issue.toPlainLine()).trim();
                if (!summary.isEmpty()) {
                    seen.add("p. " + page + " - " + summary);
                }
            }
        }
        if (seen.isEmpty()) {
            sb.append("- Read the executive summary and certified findings in the sealed human report first.\n");
            return;
        }
        for (String line : seen) {
            sb.append("- ").append(line).append("\n");
        }
    }

    private boolean shouldDeferGemmaNarrativeGeneration() {
        return true;
    }

    private long computeGemmaReportTimeoutSeconds(AnalysisEngine.ForensicReport report) {
        JSONObject nativeEvidence = report != null && report.nativeEvidence != null
                ? report.nativeEvidence
                : new JSONObject();
        int sourcePages = nativeEvidence.optInt("sourcePageCount", nativeEvidence.optInt("pageCount", 0));
        if (sourcePages <= 0) {
            return 120L;
        }
        long scaledTimeout = 90L + Math.round(sourcePages * 0.5d);
        return Math.max(120L, Math.min(600L, scaledTimeout));
    }

    private void savePendingGemmaNarrative(
            AnalysisEngine.ForensicReport report,
            String fallbackReport,
            String gemmaPrompt,
            String auditReport,
            String findingsJson
    ) {
        if (report == null || gemmaPrompt == null || gemmaPrompt.trim().isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject();
            root.put("caseId", safeValue(report.caseId));
            root.put("sourceFileName", selectedFile != null ? selectedFile.getName() : "unknown");
            root.put("evidenceHash", safeValue(report.evidenceHash));
            root.put("jurisdictionName", safeValue(report.jurisdictionName));
            root.put("jurisdictionCode", safeValue(report.jurisdiction));
            root.put("blockchainAnchor", safeValue(report.blockchainAnchor));
            root.put("summary", safeValue(report.summary));
            root.put("fallbackReport", fallbackReport == null ? "" : fallbackReport);
            root.put("gemmaPrompt", gemmaPrompt);
            root.put("auditReport", auditReport == null ? "" : auditReport);
            root.put("findingsJson", findingsJson == null ? "" : findingsJson);
            root.put("humanReportPath", "");
            root.put("sealedEvidencePath", "");
            root.put("auditReportPath", "");
            root.put("findingsPath", "");
            root.put("readableBriefPath", "");
            root.put("constitutionalNarrativePath", "");
            root.put("contradictionEnginePath", "");
            root.put("visualFindingsPath", "");
            root.put("legalAdvisoryPath", "");
            JSONArray selectedPaths = new JSONArray();
            for (File evidenceFile : getSelectedEvidenceFilesSnapshot()) {
                if (evidenceFile != null && evidenceFile.exists()) {
                    selectedPaths.put(evidenceFile.getAbsolutePath());
                }
            }
            root.put("selectedEvidencePaths", selectedPaths);
            File file = new File(getFilesDir(), PENDING_GEMMA_REPORT_FILE);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to save pending Gemma narrative.", e);
        }
    }

    private void updatePendingGemmaNarrativeHumanReportPath(String caseId, File humanReportFile) {
        updatePendingGemmaNarrativeArtifactPath(caseId, "humanReportPath", humanReportFile);
    }

    private void updatePendingGemmaNarrativeArtifactPath(String caseId, String fieldName, File artifactFile) {
        if (artifactFile == null || fieldName == null || fieldName.trim().isEmpty()) {
            return;
        }
        try {
            JSONObject root = loadPendingGemmaNarrativeJson();
            if (root == null) {
                return;
            }
            if (!safeValue(caseId).equals(root.optString("caseId", ""))) {
                return;
            }
            root.put(fieldName, artifactFile.getAbsolutePath());
            File file = new File(getFilesDir(), PENDING_GEMMA_REPORT_FILE);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to update pending Gemma narrative artifact path.", e);
        }
    }

    private JSONObject loadPendingGemmaNarrativeJson() {
        File file = new File(getFilesDir(), PENDING_GEMMA_REPORT_FILE);
        if (!file.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = fis.readAllBytes();
            String json = new String(buffer, StandardCharsets.UTF_8);
            return new JSONObject(json);
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to load pending Gemma narrative.", e);
            return null;
        }
    }

    private void clearPendingGemmaNarrative() {
        File file = new File(getFilesDir(), PENDING_GEMMA_REPORT_FILE);
        if (file.exists() && !file.delete()) {
            Log.w("MainActivity", "Could not delete pending Gemma narrative file.");
        }
    }

    private String buildLegacyLegalAdvisory(AnalysisEngine.ForensicReport report) {
        if (!includeLegalAdvisoryRequested) {
            return null;
        }
        try {
            publishBusyProgress("Preparing legacy downstream legal advisory fallback...");
            String rendered = GemmaReportOrchestrator.renderLegacyFallback(this, report);
            return rendered != null && !rendered.trim().isEmpty() ? rendered.trim() : null;
        } catch (Exception e) {
            Log.e("MainActivity", "Legacy legal advisory fallback generation failed.", e);
            return null;
        }
    }

    private String appendLegalAdvisorySection(String reportText, String legalAdvisory) {
        String base = reportText != null ? reportText.trim() : "";
        if (legalAdvisory == null || legalAdvisory.trim().isEmpty()) {
            return base;
        }
        return base
                + "\n\n=== Optional Downstream Interpretive Layer ===\n"
                + legalAdvisory.trim();
    }

    private void saveLastVaultResult(
            AnalysisEngine.ForensicReport report,
            File readableBriefFile,
            File policeReadyReportFile,
            File humanReportFile,
            File auditReportFile,
            File findingsFile,
            File sealedEvidenceFile,
            File legalAdvisoryFile,
            File constitutionalNarrativeFile,
            File contradictionEngineFile,
            File modelAuditLedgerFile
    ) {
        if (report == null) {
            return;
        }
        try {
            JSONObject root = new JSONObject();
            root.put("caseId", safeValue(report.caseId));
            root.put("sourceFileName", selectedFile != null ? selectedFile.getName() : "unknown");
            root.put("processingStatus", report.diagnostics != null
                    ? report.diagnostics.optString("processingStatus", "DETERMINATE")
                    : "DETERMINATE");
            root.put("readableBriefPath", readableBriefFile != null ? readableBriefFile.getAbsolutePath() : "");
            root.put("policeReadyReportPath", policeReadyReportFile != null ? policeReadyReportFile.getAbsolutePath() : "");
            root.put("humanReportPath", humanReportFile != null ? humanReportFile.getAbsolutePath() : "");
            root.put("auditReportPath", auditReportFile != null ? auditReportFile.getAbsolutePath() : "");
            root.put("findingsPath", findingsFile != null ? findingsFile.getAbsolutePath() : "");
            root.put("sealedEvidencePath", sealedEvidenceFile != null ? sealedEvidenceFile.getAbsolutePath() : "");
            root.put("legalAdvisoryPath", legalAdvisoryFile != null ? legalAdvisoryFile.getAbsolutePath() : "");
            root.put("constitutionalNarrativePath", constitutionalNarrativeFile != null ? constitutionalNarrativeFile.getAbsolutePath() : "");
            root.put("contradictionEnginePath", contradictionEngineFile != null ? contradictionEngineFile.getAbsolutePath() : "");
            root.put("modelAuditLedgerPath", modelAuditLedgerFile != null ? modelAuditLedgerFile.getAbsolutePath() : "");
            root.put("savedAtUtc", java.time.Instant.now().toString());
            root.put(
                    "message",
                    "The latest generated case package is available in the vault. "
                            + "Open the human report directly or open the vault if you need the full package."
            );
            File file = new File(getFilesDir(), LAST_VAULT_RESULT_FILE);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
            lastVaultResultShownThisSession = false;
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to save last vault result.", e);
        }
    }

    private JSONObject loadLastVaultResultJson() {
        File file = new File(getFilesDir(), LAST_VAULT_RESULT_FILE);
        if (!file.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = fis.readAllBytes();
            return new JSONObject(new String(buffer, StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to load last vault result.", e);
            return null;
        }
    }

    private File existingFileOrNull(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        File file = new File(path.trim());
        return file.exists() ? file : null;
    }

    private void showLastVaultResultIfNeeded() {
        if (lastVaultResultShownThisSession || recoveringPendingGemmaReport || loadPendingGemmaNarrativeJson() != null) {
            return;
        }
        JSONObject root = loadLastVaultResultJson();
        if (root == null) {
            return;
        }
        File primaryFile = existingFileOrNull(root.optString("policeReadyReportPath", ""));
        File readableBriefFile = existingFileOrNull(root.optString("readableBriefPath", ""));
        File constitutionalNarrativeFile = existingFileOrNull(root.optString("constitutionalNarrativePath", ""));
        File secondaryFile = existingFileOrNull(root.optString("humanReportPath", ""));
        if (primaryFile == null) {
            primaryFile = readableBriefFile;
        } else if (secondaryFile == null) {
            secondaryFile = readableBriefFile;
        }
        if (primaryFile != null && constitutionalNarrativeFile != null) {
            secondaryFile = constitutionalNarrativeFile;
        } else if (primaryFile == null && constitutionalNarrativeFile != null) {
            primaryFile = constitutionalNarrativeFile;
        }
        if (primaryFile == null) {
            primaryFile = secondaryFile;
            secondaryFile = existingFileOrNull(root.optString("auditReportPath", ""));
        } else if (secondaryFile == null) {
            secondaryFile = existingFileOrNull(root.optString("auditReportPath", ""));
        }
        if (primaryFile == null) {
            primaryFile = secondaryFile;
            secondaryFile = existingFileOrNull(root.optString("findingsPath", ""));
        }
        if (primaryFile == null && secondaryFile == null) {
            return;
        }
        lastVaultResultShownThisSession = true;
        String caseId = root.optString("caseId", "unknown");
        String sourceFileName = root.optString("sourceFileName", "unknown");
        String processingStatus = root.optString("processingStatus", "DETERMINATE");
        String message = root.optString("message", "").trim();
        if (message.isEmpty()) {
            message = "The latest generated case package is available in the vault.";
        }
        showVaultAwareDialog(
                "Last Report Available",
                "Case ID: " + caseId
                        + "\nSource file: " + sourceFileName
                        + "\nProcessing status: " + processingStatus
                        + "\n\n" + message,
                primaryFile,
                getString(R.string.open_report),
                secondaryFile
        );
    }

    private void recoverPendingGemmaNarrativeIfNeeded() {
        if (recoveringPendingGemmaReport) {
            return;
        }
        JSONObject pending = loadPendingGemmaNarrativeJson();
        if (pending == null) {
            return;
        }
        recoveringPendingGemmaReport = true;
        getBackgroundExecutor().execute(() -> {
            try {
                String reportText = pending.optString("fallbackReport", "");
                Log.w("MainActivity", "Skipping deferred Gemma recovery on resume and restoring fallback report only.");
                File outFile = restorePendingHumanReportFile(pending, reportText);
                ArchivedScanPackage recoveredScan = archiveRecoveredPendingRun(pending, outFile, reportText);
                GemmaCaseContextStore.save(
                        this,
                        pending.optString("caseId", ""),
                        pending.optString("sourceFileName", "unknown"),
                        buildRecoveredGemmaChatContext(pending, reportText)
                );
                clearPendingGemmaNarrative();
                runOnUiThread(() -> {
                    recoveringPendingGemmaReport = false;
                    refreshCaseManagementSection();
                    if (outFile != null) {
                        showVaultAwareDialog(
                                "Report Restored",
                                recoveredScan != null && recoveredScan.scanFolderRoot != null
                                        ? "The fallback forensic report was restored and re-archived into the vault scan folder."
                                        : "The fallback forensic report has been restored to the vault without rerunning Gemma.",
                                recoveredScan != null && recoveredScan.policeReadyReportFile != null
                                        ? recoveredScan.policeReadyReportFile
                                        : recoveredScan != null && recoveredScan.readableBriefFile != null
                                        ? recoveredScan.readableBriefFile
                                        : recoveredScan != null && recoveredScan.forensicPdf != null
                                        ? recoveredScan.forensicPdf
                                        : outFile,
                                getString(R.string.open_report),
                                null
                        );
                    }
                });
            } catch (Exception e) {
                recoveringPendingGemmaReport = false;
                Log.e("MainActivity", "Deferred Gemma recovery failed.", e);
            }
        });
    }

    private File restorePendingHumanReportFile(JSONObject pending, String reportText) throws Exception {
        String sourceFileName = pending.optString("sourceFileName", "unknown");
        String caseId = pending.optString("caseId", "");
        String evidenceHash = pending.optString("evidenceHash", "");
        String jurisdictionName = pending.optString("jurisdictionName", "");
        String jurisdictionCode = pending.optString("jurisdictionCode", "");
        String existingPath = pending.optString("humanReportPath", "").trim();
        File outFile = existingPath.isEmpty()
                ? VaultManager.createVaultFile(this, "human-forensic-report", ".pdf", sourceFileName)
                : new File(existingPath);
        return writeForensicReportToPath(outFile, sourceFileName, caseId, evidenceHash, jurisdictionName, jurisdictionCode, reportText);
    }

    private ArchivedScanPackage archiveRecoveredPendingRun(
            JSONObject pending,
            File humanReportFile,
            String reportText
    ) {
        if (pending == null || humanReportFile == null || !humanReportFile.exists() || scanFolderManager == null) {
            return null;
        }
        try {
            String sourceFileName = pending.optString("sourceFileName", "recovered");
            List<File> selectedEvidenceSources = collectRecoverySelectedEvidenceFiles(pending);
            String scanName = buildRecoveredScanName(sourceFileName, pending.optString("caseId", ""));
            ScanFolder folder = findExistingScanFolderForRecovery(pending);
            boolean reusingExistingFolder = folder != null;
            if (folder == null) {
                folder = scanFolderManager.createScanFolder(scanName);
            } else {
                prepareScanFolderForRecovery(folder, !selectedEvidenceSources.isEmpty());
            }
            ArchivedScanPackage archived = new ArchivedScanPackage();
            archived.scanFolderRoot = new File(folder.getFolderPath());

            JSONArray evidenceManifest = new JSONArray();
            File primaryEvidence = null;
            for (File sourceEvidence : selectedEvidenceSources) {
                if (primaryEvidence == null) {
                    primaryEvidence = sourceEvidence;
                }
                File copiedEvidence = scanFolderManager.copyEvidenceIntoScanFolder(sourceEvidence, folder);
                JSONObject item = new JSONObject();
                item.put("name", copiedEvidence.getName());
                item.put("path", copiedEvidence.getAbsolutePath());
                item.put("sha512", HashUtil.sha512File(copiedEvidence));
                evidenceManifest.put(item);
            }
            if (evidenceManifest.length() == 0) {
                primaryEvidence = appendExistingEvidenceManifest(folder, evidenceManifest);
            }

            JSONArray artifacts = new JSONArray();
            File sealedEvidenceFile = existingFileOrNull(pending.optString("sealedEvidencePath", ""));
            if (sealedEvidenceFile == null && primaryEvidence != null) {
                sealedEvidenceFile = writeRecoveredSealedEvidenceToVault(pending, primaryEvidence);
            }
            archived.sealedEvidenceFile = copyArtifactIntoScanFolder(folder, sealedEvidenceFile, "sealed-evidence", artifacts);

            File auditPdf = existingFileOrNull(pending.optString("auditReportPath", ""));
            String auditText = pending.optString("auditReport", "").trim();
            if (auditPdf == null && !auditText.isEmpty()) {
                auditPdf = writeRecoveredAuditReportToVault(pending, auditText);
            }
            archived.auditorPdf = copyArtifactIntoScanFolder(folder, auditPdf, "forensic-audit-report", artifacts);

            File findingsFile = existingFileOrNull(pending.optString("findingsPath", ""));
            String findingsText = pending.optString("findingsJson", "").trim();
            if (findingsFile == null && !findingsText.isEmpty()) {
                findingsFile = writeRecoveredFindingsSnapshotToVault(pending, findingsText);
            }
            archived.findingsFile = copyArtifactIntoScanFolder(folder, findingsFile, "forensic-findings", artifacts);

            archived.forensicPdf = copyArtifactIntoScanFolder(folder, humanReportFile, "human-forensic-report", artifacts);

            File readableBriefFile = existingFileOrNull(pending.optString("readableBriefPath", ""));
            if (readableBriefFile == null) {
                String recoveredReadableBrief = buildRecoveredReadableFindingsBrief(pending, reportText, auditText, findingsText);
                if (!recoveredReadableBrief.trim().isEmpty()) {
                    readableBriefFile = writeRecoveredReadableFindingsBriefToVault(pending, recoveredReadableBrief);
                }
            }
            archived.readableBriefFile = copyArtifactIntoScanFolder(folder, readableBriefFile, "readable-forensic-brief", artifacts);

            File policeReadyReportFile = existingFileOrNull(pending.optString("policeReadyReportPath", ""));
            if (policeReadyReportFile == null) {
                String recoveredPoliceReadyReport = buildRecoveredPoliceReadyReport(pending, findingsText);
                if (!recoveredPoliceReadyReport.trim().isEmpty()) {
                    policeReadyReportFile = writeRecoveredPoliceReadyReportToVault(pending, recoveredPoliceReadyReport);
                }
            }
            archived.policeReadyReportFile = copyArtifactIntoScanFolder(folder, policeReadyReportFile, "police-ready-forensic-report", artifacts);

            File constitutionalNarrativeFile = existingFileOrNull(pending.optString("constitutionalNarrativePath", ""));
            if (constitutionalNarrativeFile == null) {
                String recoveredConstitutionalReport = buildRecoveredConstitutionalVaultReport(pending, reportText, auditText, findingsText);
                if (!recoveredConstitutionalReport.trim().isEmpty()) {
                    constitutionalNarrativeFile = writeRecoveredConstitutionalVaultReportToVault(pending, recoveredConstitutionalReport);
                }
            }
            archived.constitutionalNarrativeFile = copyArtifactIntoScanFolder(folder, constitutionalNarrativeFile, "constitutional-vault-report", artifacts);

            File contradictionEngineFile = existingFileOrNull(pending.optString("contradictionEnginePath", ""));
            if (contradictionEngineFile == null) {
                String recoveredContradictionReport = buildRecoveredContradictionEngineReport(pending, findingsText);
                if (!recoveredContradictionReport.trim().isEmpty()) {
                    contradictionEngineFile = writeRecoveredContradictionEngineReportToVault(pending, recoveredContradictionReport);
                }
            }
            archived.contradictionEngineFile = copyArtifactIntoScanFolder(folder, contradictionEngineFile, "contradiction-engine-report", artifacts);

            File legalAdvisoryFile = existingFileOrNull(pending.optString("legalAdvisoryPath", ""));
            archived.legalAdvisoryFile = copyArtifactIntoScanFolder(folder, legalAdvisoryFile, "legal-advisory", artifacts);

            File visualFindingsFile = existingFileOrNull(pending.optString("visualFindingsPath", ""));
            if (visualFindingsFile == null && !findingsText.isEmpty()) {
                String recoveredVisualMemo = buildRecoveredVisualFindingsMemo(pending, findingsText);
                if (!recoveredVisualMemo.trim().isEmpty()) {
                    visualFindingsFile = writeRecoveredVisualFindingsMemoToVault(pending, recoveredVisualMemo);
                }
            }
            archived.visualFindingsFile = copyArtifactIntoScanFolder(folder, visualFindingsFile, "visual-findings", artifacts);

            if (!auditText.isEmpty()) {
                writeTextToFile(new File(folder.getFolderPath(), "audit_report.txt"), auditText);
            }
            if (!findingsText.isEmpty()) {
                writeTextToFile(new File(folder.getFolderPath(), "findings_snapshot.json"), findingsText);
            }
            writeTextToFile(new File(folder.getFolderPath(), "human_report_snapshot.txt"), reportText == null ? "" : reportText);

            JSONObject manifest = new JSONObject();
            manifest.put("scanName", folder.getName());
            manifest.put("scanDate", folder.getScanDate() != null ? folder.getScanDate().toString() : "");
            manifest.put("folderPath", folder.getFolderPath());
            manifest.put("sourceCaseId", pending.optString("caseId", ""));
            manifest.put("sourceEvidenceHash", pending.optString("evidenceHash", ""));
            manifest.put("recoveredFromPendingNarrative", true);
            manifest.put("reusedExistingScanFolder", reusingExistingFolder);
            manifest.put("evidenceFiles", evidenceManifest);
            manifest.put("artifacts", artifacts);
            manifest.put("humanReadableReportPath", humanReportFile.getAbsolutePath());
            manifest.put("readableBriefPath", readableBriefFile != null ? readableBriefFile.getAbsolutePath() : "");
            manifest.put("policeReadyReportPath", policeReadyReportFile != null ? policeReadyReportFile.getAbsolutePath() : "");
            manifest.put("constitutionalNarrativePath", constitutionalNarrativeFile != null ? constitutionalNarrativeFile.getAbsolutePath() : "");
            manifest.put("contradictionEnginePath", contradictionEngineFile != null ? contradictionEngineFile.getAbsolutePath() : "");
            writeJsonToFile(new File(folder.getFolderPath(), "scan_manifest.json"), manifest);

            String recoveredFolderPath = folder.getFolderPath();
            folder.setFilePaths(scanFolderManager.listScanFolders().stream()
                    .filter(item -> item != null && recoveredFolderPath.equals(item.getFolderPath()))
                    .findFirst()
                    .map(ScanFolder::getFilePaths)
                    .orElse(folder.getFilePaths()));
            scanFolderManager.saveScanFolder(folder);
            return archived;
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to archive recovered pending narrative as scan folder.", e);
            return null;
        }
    }

    private List<File> collectRecoverySelectedEvidenceFiles(JSONObject pending) {
        List<File> evidenceFiles = new ArrayList<>();
        if (pending == null) {
            return evidenceFiles;
        }
        JSONArray selectedPaths = pending.optJSONArray("selectedEvidencePaths");
        if (selectedPaths == null) {
            return evidenceFiles;
        }
        for (int i = 0; i < selectedPaths.length(); i++) {
            String path = selectedPaths.optString(i, "").trim();
            if (path.isEmpty()) {
                continue;
            }
            File sourceEvidence = new File(path);
            if (sourceEvidence.exists() && sourceEvidence.isFile()) {
                evidenceFiles.add(sourceEvidence);
            }
        }
        return evidenceFiles;
    }

    private File appendExistingEvidenceManifest(ScanFolder folder, JSONArray evidenceManifest) throws Exception {
        if (folder == null || folder.getFolderPath() == null || evidenceManifest == null) {
            return null;
        }
        File evidenceDir = new File(folder.getFolderPath(), "evidence");
        File[] evidenceFiles = evidenceDir.listFiles(File::isFile);
        if (evidenceFiles == null || evidenceFiles.length == 0) {
            return null;
        }
        Arrays.sort(evidenceFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        File primaryEvidence = null;
        for (File file : evidenceFiles) {
            if (primaryEvidence == null) {
                primaryEvidence = file;
            }
            JSONObject item = new JSONObject();
            item.put("name", file.getName());
            item.put("path", file.getAbsolutePath());
            item.put("sha512", HashUtil.sha512File(file));
            evidenceManifest.put(item);
        }
        return primaryEvidence;
    }

    private ScanFolder findExistingScanFolderForRecovery(JSONObject pending) {
        if (pending == null || scanFolderManager == null) {
            return null;
        }
        String targetCaseId = pending.optString("caseId", "").trim();
        String targetEvidenceHash = pending.optString("evidenceHash", "").trim();
        String targetSourceName = pending.optString("sourceFileName", "").trim();
        if (targetCaseId.isEmpty() && targetEvidenceHash.isEmpty() && targetSourceName.isEmpty()) {
            return null;
        }
        for (ScanFolder candidate : scanFolderManager.listScanFolders()) {
            if (candidate == null || candidate.getFolderPath() == null || candidate.getFolderPath().trim().isEmpty()) {
                continue;
            }
            File manifestFile = new File(candidate.getFolderPath(), "scan_manifest.json");
            JSONObject manifest = loadJsonObject(manifestFile);
            if (manifest == null || manifest.optBoolean("recoveredFromPendingNarrative", false)) {
                continue;
            }
            boolean caseMatch = !targetCaseId.isEmpty()
                    && targetCaseId.equals(manifest.optString("sourceCaseId", "").trim());
            boolean evidenceMatch = !targetEvidenceHash.isEmpty()
                    && targetEvidenceHash.equals(manifest.optString("sourceEvidenceHash", "").trim());
            boolean sourceMatch = manifestMatchesSourceFileName(manifest, targetSourceName);
            if ((caseMatch && evidenceMatch) || (caseMatch && sourceMatch) || (evidenceMatch && sourceMatch)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean manifestMatchesSourceFileName(JSONObject manifest, String sourceFileName) {
        if (manifest == null || sourceFileName == null || sourceFileName.trim().isEmpty()) {
            return false;
        }
        String normalizedTarget = normalizeRecoverySourceName(sourceFileName);
        JSONArray evidenceFiles = manifest.optJSONArray("evidenceFiles");
        if (evidenceFiles == null) {
            return false;
        }
        for (int i = 0; i < evidenceFiles.length(); i++) {
            JSONObject item = evidenceFiles.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String evidenceName = item.optString("name", "").trim();
            if (normalizedTarget.equals(normalizeRecoverySourceName(evidenceName))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeRecoverySourceName(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        int dot = normalized.lastIndexOf('.');
        if (dot > 0) {
            normalized = normalized.substring(0, dot);
        }
        return normalized.replaceAll("[^a-zA-Z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "")
                .toLowerCase(Locale.US);
    }

    private JSONObject loadJsonObject(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = fis.readAllBytes();
            return new JSONObject(new String(data, StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w("MainActivity", "Failed to load JSON object from " + file.getAbsolutePath(), e);
            return null;
        }
    }

    private void prepareScanFolderForRecovery(ScanFolder folder, boolean clearEvidenceDir) {
        if (folder == null || folder.getFolderPath() == null || folder.getFolderPath().trim().isEmpty()) {
            return;
        }
        File root = new File(folder.getFolderPath());
        File[] children = root.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child == null || !child.exists()) {
                continue;
            }
            if (child.isDirectory()) {
                String name = child.getName();
                if ("artifacts".equalsIgnoreCase(name) || (clearEvidenceDir && "evidence".equalsIgnoreCase(name))) {
                    clearDirectoryContents(child);
                }
                continue;
            }
            if ("scan_folder.json".equalsIgnoreCase(child.getName())) {
                continue;
            }
            if (!child.delete()) {
                Log.w("MainActivity", "Could not delete stale recovery helper file: " + child.getAbsolutePath());
            }
        }
    }

    private void clearDirectoryContents(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            deleteRecursivelyQuietly(child);
        }
    }

    private void deleteRecursivelyQuietly(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursivelyQuietly(child);
                }
            }
        }
        if (!file.delete()) {
            Log.w("MainActivity", "Could not delete stale recovery path: " + file.getAbsolutePath());
        }
    }

    private String buildRecoveredScanName(String sourceFileName, String caseId) {
        String base = sourceFileName == null ? "" : sourceFileName.trim();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.replaceAll("[^a-zA-Z0-9._-]+", "_").replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = safeValue(caseId);
        }
        if (base.isEmpty()) {
            base = "recovered_scan";
        }
        return base + "_restored";
    }

    private File writeRecoveredSealedEvidenceToVault(JSONObject pending, File sourceEvidence) throws Exception {
        if (pending == null || sourceEvidence == null || !sourceEvidence.exists()) {
            return null;
        }
        File outFile = VaultManager.createVaultFile(
                this,
                "sealed-evidence",
                ".pdf",
                pending.optString("sourceFileName", sourceEvidence.getName())
        );
        PDFSealer.SealRequest req = new PDFSealer.SealRequest();
        req.title = "Verum Omnis Sealed Evidence";
        req.summary = "Recovered sealed evidence certificate rebuilt from the pending run after app restart.";
        req.includeQr = true;
        req.includeHash = true;
        req.mode = PDFSealer.DocumentMode.SEAL_ONLY;
        req.evidenceHash = pending.optString("evidenceHash", "");
        req.caseId = pending.optString("caseId", "");
        req.sourceFileName = pending.optString("sourceFileName", sourceEvidence.getName());
        req.jurisdiction = safeValue(pending.optString("jurisdictionName", ""))
                + " (" + safeValue(pending.optString("jurisdictionCode", "")) + ")";
        JSONObject findingsRoot = parsePendingFindingsRoot(pending);
        JSONObject nativeEvidence = findingsRoot != null ? findingsRoot.optJSONObject("nativeEvidence") : null;
        JSONArray sourceFiles = nativeEvidence != null ? nativeEvidence.optJSONArray("sourceFiles") : null;
        List<File> pendingEvidenceFiles = getPendingSelectedEvidenceFiles(pending);
        boolean mergedEvidenceSet = (nativeEvidence != null
                && "merged".equalsIgnoreCase(nativeEvidence.optString("pipelineStatus", "")))
                || (sourceFiles != null && sourceFiles.length() > 1)
                || pendingEvidenceFiles.size() > 1;
        if (mergedEvidenceSet) {
            if (sourceFiles == null || sourceFiles.length() == 0) {
                sourceFiles = buildSourceFilesArrayFromFiles(pendingEvidenceFiles);
            }
            req.summary = "Recovered merged evidence bag rebuilt from the pending run after app restart.";
            req.sourceFileName = !pendingEvidenceFiles.isEmpty()
                    ? buildEvidenceSelectionLabel(pendingEvidenceFiles)
                    : pending.optString("sourceFileName", sourceEvidence.getName());
            req.legalSummary = "Recovered during pending narrative restoration. The full multi-file scan session was rebuilt and resealed as one indexed evidence bag.";
            req.bodyText = buildMergedSealedEvidenceBody(
                    pending.optString("caseId", ""),
                    pending.optString("evidenceHash", ""),
                    nativeEvidence,
                    sourceFiles
            );
            PDFSealer.generateSealedPdf(this, req, outFile);
            VaultManager.writeSealManifest(
                    this,
                    outFile,
                    "sealed-evidence",
                    pending.optString("caseId", ""),
                    pending.optString("evidenceHash", "")
            );
            return outFile;
        }
        req.legalSummary = "Recovered during pending narrative restoration. The source artifact was preserved and resealed into the vault package.";
        if (canSealSourceDocument(sourceEvidence)) {
            PDFSealer.generateSealedSourceDocument(this, req, sourceEvidence, outFile);
        } else {
            PDFSealer.generateSealedPdf(this, req, outFile);
        }
        VaultManager.writeSealManifest(
                this,
                outFile,
                "sealed-evidence",
                pending.optString("caseId", ""),
                pending.optString("evidenceHash", "")
        );
        return outFile;
    }

    private JSONObject parsePendingFindingsRoot(JSONObject pending) {
        if (pending == null) {
            return null;
        }
        String findingsJson = pending.optString("findingsJson", "").trim();
        if (findingsJson.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(findingsJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<File> getPendingSelectedEvidenceFiles(JSONObject pending) {
        ArrayList<File> files = new ArrayList<>();
        if (pending == null) {
            return files;
        }
        JSONArray selectedPaths = pending.optJSONArray("selectedEvidencePaths");
        if (selectedPaths == null) {
            return files;
        }
        for (int i = 0; i < selectedPaths.length(); i++) {
            String path = selectedPaths.optString(i, "").trim();
            if (path.isEmpty()) {
                continue;
            }
            File file = new File(path);
            if (file.exists()) {
                files.add(file);
            }
        }
        return files;
    }

    private JSONArray buildSourceFilesArrayFromFiles(List<File> files) {
        JSONArray array = new JSONArray();
        if (files == null) {
            return array;
        }
        for (File file : files) {
            if (file == null || !file.exists()) {
                continue;
            }
            try {
                JSONObject item = new JSONObject();
                item.put("fileName", file.getName());
                item.put("path", file.getAbsolutePath());
                item.put("sha512", HashUtil.sha512File(file));
                array.put(item);
            } catch (Exception ignored) {
                try {
                    JSONObject item = new JSONObject();
                    item.put("fileName", file.getName());
                    item.put("path", file.getAbsolutePath());
                    item.put("sha512", "");
                    array.put(item);
                } catch (Exception ignoredAgain) {
                    // Skip malformed recovery metadata rather than failing the recovered package.
                }
            }
        }
        return array;
    }

    private File writeRecoveredAuditReportToVault(JSONObject pending, String auditReport) throws Exception {
        if (pending == null || auditReport == null || auditReport.trim().isEmpty()) {
            return null;
        }
        File outFile = VaultManager.createVaultFile(
                this,
                "forensic-audit-report",
                ".pdf",
                pending.optString("sourceFileName", "unknown")
        );
        return writeAuditReportToPath(
                outFile,
                pending.optString("sourceFileName", "unknown"),
                pending.optString("caseId", ""),
                pending.optString("evidenceHash", ""),
                pending.optString("jurisdictionName", ""),
                pending.optString("jurisdictionCode", ""),
                auditReport
        );
    }

    private File writeRecoveredReadableFindingsBriefToVault(JSONObject pending, String readableBrief) throws Exception {
        if (pending == null || readableBrief == null || readableBrief.trim().isEmpty()) {
            return null;
        }
        File outFile = VaultManager.createVaultFile(
                this,
                "readable-forensic-brief",
                ".pdf",
                pending.optString("sourceFileName", "unknown")
        );
        return writeReadableFindingsBriefToPath(
                outFile,
                pending.optString("sourceFileName", "unknown"),
                pending.optString("caseId", ""),
                pending.optString("evidenceHash", ""),
                pending.optString("jurisdictionName", ""),
                pending.optString("jurisdictionCode", ""),
                readableBrief
        );
    }

    private File writeRecoveredPoliceReadyReportToVault(JSONObject pending, String policeReadyReport) throws Exception {
        if (pending == null || policeReadyReport == null || policeReadyReport.trim().isEmpty()) {
            return null;
        }
        File outFile = VaultManager.createVaultFile(
                this,
                "police-ready-forensic-report",
                ".pdf",
                pending.optString("sourceFileName", "unknown")
        );
        return writePoliceReadyReportToPath(
                outFile,
                pending.optString("sourceFileName", "unknown"),
                pending.optString("caseId", ""),
                pending.optString("evidenceHash", ""),
                pending.optString("jurisdictionName", ""),
                pending.optString("jurisdictionCode", ""),
                policeReadyReport
        );
    }

    private File writeRecoveredContradictionEngineReportToVault(JSONObject pending, String contradictionReport) throws Exception {
        if (pending == null || contradictionReport == null || contradictionReport.trim().isEmpty()) {
            return null;
        }
        File outFile = VaultManager.createVaultFile(
                this,
                "contradiction-engine-report",
                ".pdf",
                pending.optString("sourceFileName", "unknown")
        );
        return writeContradictionEngineReportToPath(
                outFile,
                pending.optString("sourceFileName", "unknown"),
                pending.optString("caseId", ""),
                pending.optString("evidenceHash", ""),
                pending.optString("jurisdictionName", ""),
                pending.optString("jurisdictionCode", ""),
                contradictionReport
        );
    }

    private File writeRecoveredConstitutionalVaultReportToVault(JSONObject pending, String constitutionalReport) throws Exception {
        if (pending == null || constitutionalReport == null || constitutionalReport.trim().isEmpty()) {
            return null;
        }
        File outFile = VaultManager.createVaultFile(
                this,
                "constitutional-vault-report",
                ".pdf",
                pending.optString("sourceFileName", "unknown")
        );
        return writeConstitutionalVaultReportToPath(
                outFile,
                pending.optString("sourceFileName", "unknown"),
                pending.optString("caseId", ""),
                pending.optString("evidenceHash", ""),
                pending.optString("jurisdictionName", ""),
                pending.optString("jurisdictionCode", ""),
                constitutionalReport
        );
    }

    private File writeRecoveredFindingsSnapshotToVault(JSONObject pending, String findingsJson) throws Exception {
        if (pending == null || findingsJson == null || findingsJson.trim().isEmpty()) {
            return null;
        }
        File outFile = VaultManager.createVaultFile(
                this,
                "forensic-findings",
                ".json",
                pending.optString("sourceFileName", "unknown")
        );
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(findingsJson.getBytes(StandardCharsets.UTF_8));
        }
        VaultManager.writeSealManifest(
                this,
                outFile,
                "forensic-findings",
                pending.optString("caseId", ""),
                pending.optString("evidenceHash", "")
        );
        return outFile;
    }

    private File writeRecoveredVisualFindingsMemoToVault(JSONObject pending, String visualFindingsMemo) throws Exception {
        if (pending == null || visualFindingsMemo == null || visualFindingsMemo.trim().isEmpty()) {
            return null;
        }
        File outFile = VaultManager.createVaultFile(
                this,
                "visual-findings-memo",
                ".txt",
                pending.optString("sourceFileName", "unknown")
        );
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(visualFindingsMemo.getBytes(StandardCharsets.UTF_8));
        }
        VaultManager.writeSealManifest(
                this,
                outFile,
                "visual-findings-memo",
                pending.optString("caseId", ""),
                pending.optString("evidenceHash", "")
        );
        return outFile;
    }

    private String buildRecoveredReadableFindingsBrief(
            JSONObject pending,
            String humanReadableReport,
            String auditReport,
            String findingsJson
    ) {
        AnalysisEngine.ForensicReport reconstructed = reconstructReadableBriefReport(pending, findingsJson);
        return buildReadableFindingsBriefFallback(
                reconstructed,
                humanReadableReport,
                auditReport,
                null,
                null
        );
    }

    private String buildRecoveredPoliceReadyReport(
            JSONObject pending,
            String findingsJson
    ) {
        AnalysisEngine.ForensicReport reconstructed = reconstructReadableBriefReport(pending, findingsJson);
        return generatePoliceReadyReport(reconstructed);
    }

    private String buildRecoveredConstitutionalVaultReport(
            JSONObject pending,
            String humanReadableReport,
            String auditReport,
            String findingsJson
    ) {
        AnalysisEngine.ForensicReport reconstructed = reconstructReadableBriefReport(pending, findingsJson);
        String recoveredReadableBrief = buildReadableFindingsBriefFallback(
                reconstructed,
                humanReadableReport,
                auditReport,
                null,
                null
        );
        return generateConstitutionalVaultReport(
                reconstructed,
                new LinkedHashMap<>(),
                new HashMap<>(),
                auditReport,
                humanReadableReport,
                recoveredReadableBrief
        );
    }

    private String buildRecoveredContradictionEngineReport(
            JSONObject pending,
            String findingsJson
    ) {
        AnalysisEngine.ForensicReport reconstructed = reconstructReadableBriefReport(pending, findingsJson);
        return generateContradictionEngineReport(reconstructed);
    }

    private String generateConstitutionalVaultReport(
            AnalysisEngine.ForensicReport report,
            Map<String, String> integrityResults,
            HashMap<String, String> fileMeta,
            String auditReport,
            String humanReadableReport,
            String readableBrief
    ) {
        if (report == null) {
            return "";
        }
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        List<JSONObject> certifiedNarratives = certifiedFindingNarratives(report, 8);
        String patternLine = buildHumanRepeatPatternLine(certifiedNarratives);
        JSONObject nativeEvidence = report.nativeEvidence != null ? report.nativeEvidence : new JSONObject();
        JSONObject diagnostics = report.diagnostics != null ? report.diagnostics : new JSONObject();

        VaultReportBuilder.Input input = new VaultReportBuilder.Input();
        input.constitutionalVersion = "v5.2.7";
        input.reportType = "Sealed Evidence Analysis";
        input.inputArtifact = selectedFile != null ? selectedFile.getName() : "unknown";
        input.reportDateUtc = String.valueOf(java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        input.engineMode = "Offline-first | Contradiction-first | Deterministic extraction";
        input.sections.add(new VaultReportBuilder.Section(
                "EXECUTIVE SUMMARY",
                renderContradictionSection(sb -> appendCharterExecutiveSummary(sb, assembled, patternLine))
        ));
        input.sections.add(new VaultReportBuilder.Section(
                "1. WHAT THE RECORD CURRENTLY SHOWS",
                TruthInCodeEngine.renderContradictionTruthSection(report, assembled)
        ));
        input.sections.add(new VaultReportBuilder.Section(
                "2. EVIDENCE MANIFEST (partial - key artifacts)",
                renderContradictionSection(sb -> appendCharterEvidenceManifest(sb, report, assembled, nativeEvidence))
        ));
        input.sections.add(new VaultReportBuilder.Section(
                "3. CHAIN-OF-CUSTODY LOG",
                renderContradictionSection(sb -> appendCharterChainOfCustody(sb, report))
        ));
        input.sections.add(new VaultReportBuilder.Section(
                "4. CONTRADICTION LEDGER",
                renderContradictionSection(sb -> appendCharterContradictionLedger(sb, diagnostics))
        ));
        input.sections.add(new VaultReportBuilder.Section(
                "5. ANCHORED TIMELINE",
                renderContradictionSection(sb -> appendCharterAnchoredTimeline(sb, assembled))
        ));
        input.sections.add(new VaultReportBuilder.Section(
                "6. NINE-BRAIN OUTPUTS (Anchored, Ordinal Confidence Only)",
                renderContradictionSection(sb -> appendBrainConsensusSection(sb, report.brainAnalysis))
        ));
        input.sections.add(new VaultReportBuilder.Section(
                "7. TRIPLE VERIFICATION",
                renderContradictionSection(sb -> appendCharterTripleVerification(sb, report, assembled))
        ));
        input.sections.add(new VaultReportBuilder.Section(
                "8. OPTIONAL LEGAL HANDOFF (NON-CORE)",
                "This section is downstream legal/advisory analysis. It does not overwrite the anchored findings, contradiction ledger, or ordinal synthesis above.\n\n"
                        + renderContradictionSection(sb -> appendLegalAttorneyAnalysisSection(sb, report))
        ));
        input.sections.add(new VaultReportBuilder.Section(
                "9. OPTIONAL CONSENSUS REVIEW (NON-CORE)",
                "This review records downstream comparison outputs only. It does not enlarge the core constitutional findings by itself.\n\n"
                        + renderContradictionSection(sb -> appendConsensusVerificationSection(sb, report))
        ));
        input.sections.add(new VaultReportBuilder.Section(
                "10. SEAL BLOCK",
                renderContradictionSection(sb -> appendCharterSealBlock(sb, report))
        ));
        input.sections.add(new VaultReportBuilder.Section(
                "11. COURT-READY DECLARATION",
                renderContradictionSection(this::appendCharterCourtReadyDeclaration)
        ));
        input.sections.add(new VaultReportBuilder.Section(
                "12. DISCLOSURE OF LIMITATIONS",
                renderContradictionSection(sb -> appendCharterLimitations(sb, report, nativeEvidence, diagnostics))
        ));
        return VaultReportBuilder.render(input);
    }

    private String generateContradictionEngineReport(AnalysisEngine.ForensicReport report) {
        if (report == null) {
            return "";
        }
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        JSONObject nativeEvidence = report.nativeEvidence != null ? report.nativeEvidence : new JSONObject();
        JSONObject diagnostics = report.diagnostics != null ? report.diagnostics : new JSONObject();
        ContradictionReportModel.Input input = new ContradictionReportModel.Input();
        input.constitutionalVersion = "v5.2.7";
        input.reportType = "Direct contradiction-engine output";
        input.inputArtifact = selectedFile != null ? selectedFile.getName() : "unknown";
        input.reportDateUtc = String.valueOf(java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        input.verifiedContradictions = assembled.verifiedContradictionCount;
        input.candidateContradictions = assembled.candidateContradictionCount;
        input.processingStatus = constitutionalPublicationStatus(report, assembled);
        input.ordinalConfidence = directContradictionConfidence(report, assembled);
        input.executiveSummary = directContradictionSummary(report, assembled);
        input.contradictionPosture = assembled.contradictionPosture;
        input.truthSection = TruthInCodeEngine.renderContradictionTruthSection(report, assembled);
        input.evidenceManifest = renderContradictionSection(sb -> appendCharterEvidenceManifest(sb, report, assembled, nativeEvidence));
        input.chainOfCustody = renderContradictionSection(sb -> appendCharterChainOfCustody(sb, report));
        input.contradictionLedger = renderContradictionSection(sb -> appendCharterContradictionLedger(sb, diagnostics));
        input.anchoredTimeline = renderContradictionSection(sb -> appendCharterAnchoredTimeline(sb, assembled));
        input.nineBrainOutputs = renderContradictionSection(sb -> appendBrainConsensusSection(sb, report.brainAnalysis));
        input.tripleVerification = renderContradictionSection(sb -> appendCharterTripleVerification(sb, report, assembled));
        input.resolutionGuidance = renderContradictionSection(sb -> appendContradictionResolutionGuidance(sb, diagnostics));
        input.coverageGaps = renderContradictionSection(sb -> appendContradictionCoverageGaps(sb, report, nativeEvidence, diagnostics));
        input.sealBlock = renderContradictionSection(sb -> appendCharterSealBlock(sb, report));
        return ContradictionReportBuilder.render(ContradictionReportBuilder.build(input));
    }

    private interface ContradictionSectionAppender {
        void append(StringBuilder sb);
    }

    private String renderContradictionSection(ContradictionSectionAppender appender) {
        StringBuilder sb = new StringBuilder();
        if (appender != null) {
            appender.append(sb);
        }
        return sb.toString().trim();
    }

    private String constitutionalPublicationStatus(
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled
    ) {
        JSONObject diagnostics = report != null && report.diagnostics != null
                ? report.diagnostics
                : new JSONObject();
        JSONObject overall = report != null && report.tripleVerification != null
                ? report.tripleVerification.optJSONObject("overall")
                : null;
        boolean indeterminateDueToConcealment = diagnostics.optBoolean("indeterminateDueToConcealment", false);
        if (indeterminateDueToConcealment) {
            return "INDETERMINATE DUE TO CONCEALMENT";
        }
        StringBuilder corpus = new StringBuilder();
        if (diagnostics != null) {
            corpus.append(" ").append(diagnostics.optString("processingStatus", ""));
            corpus.append(" ").append(diagnostics.optString("processingReason", ""));
        }
        if (overall != null) {
            corpus.append(" ").append(overall.optString("status", ""));
            corpus.append(" ").append(overall.optString("reason", ""));
        }
        String lowered = corpus.toString().toLowerCase(Locale.US);
        boolean hasCoverageGapLanguage = lowered.contains("coverage gap")
                || lowered.contains("request_more_evidence")
                || lowered.contains("mature consensus")
                || lowered.contains("immature consensus")
                || lowered.contains("concealment");
        if ((assembled != null && assembled.verifiedContradictionCount <= 0 && assembled.candidateContradictionCount > 0)
                || hasCoverageGapLanguage) {
            return "DETERMINATE WITH MATERIAL COVERAGE GAPS";
        }
        String raw = diagnostics.optString("processingStatus", "").trim();
        if (!raw.isEmpty() && !"COMPLETED".equalsIgnoreCase(raw)) {
            return raw;
        }
        return "DETERMINATE";
    }

    private boolean shouldSuppressCoreRoleNarration(ForensicReportAssembler.Assembly assembled) {
        return assembled != null
                && (assembled.verifiedContradictionCount > 0 || assembled.candidateContradictionCount > 0);
    }

    private String directContradictionConfidence(
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled
    ) {
        JSONObject nativeEvidence = report != null && report.nativeEvidence != null
                ? report.nativeEvidence
                : new JSONObject();
        int sourcePages = nativeEvidence.optInt("sourcePageCount", nativeEvidence.optInt("pageCount", 0));
        if (assembled.verifiedContradictionCount >= 6 && sourcePages >= 100) {
            return "VERY_HIGH";
        }
        if (assembled.verifiedContradictionCount > 0) {
            return "HIGH";
        }
        if (assembled.candidateContradictionCount > 0) {
            return "MODERATE";
        }
        return sourcePages > 0 ? "LOW" : "INSUFFICIENT";
    }

    private String directContradictionSummary(
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled
    ) {
        JSONObject nativeEvidence = report != null && report.nativeEvidence != null
                ? report.nativeEvidence
                : new JSONObject();
        int sourcePages = nativeEvidence.optInt("sourcePageCount", nativeEvidence.optInt("pageCount", 0));
        int ocrFailedPages = nativeEvidence.optInt("ocrFailedCount", 0);
        return "Source pages: " + sourcePages
                + " | Verified contradictions: " + assembled.verifiedContradictionCount
                + " | Candidate contradictions: " + assembled.candidateContradictionCount
                + " | OCR failed pages: " + ocrFailedPages + ".";
    }

    private void appendContradictionResolutionGuidance(StringBuilder sb, JSONObject diagnostics) {
        JSONArray contradictions = diagnostics != null ? diagnostics.optJSONArray("contradictionRegister") : null;
        LinkedHashSet<String> guidance = new LinkedHashSet<>();
        if (contradictions != null) {
            for (int i = 0; i < contradictions.length() && guidance.size() < 8; i++) {
                JSONObject item = contradictions.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String needed = cleanNarrativeText(item.optString("neededEvidence", ""));
                if (!needed.isEmpty()) {
                    guidance.add(needed);
                    continue;
                }
                String resolution = cleanNarrativeText(item.optString("resolution_evidence", ""));
                if (!resolution.isEmpty()) {
                    guidance.add(resolution);
                }
            }
        }
        if (guidance.isEmpty()) {
            sb.append("No specific further-evidence instructions were generated in this pass.\n");
            return;
        }
        for (String item : guidance) {
            sb.append("- ").append(item).append("\n");
        }
    }

    private void appendContradictionCoverageGaps(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            JSONObject nativeEvidence,
            JSONObject diagnostics
    ) {
        boolean wrote = false;
        JSONArray coverageGaps = report != null
                && report.brainAnalysis != null
                && report.brainAnalysis.optJSONObject("consensus") != null
                ? report.brainAnalysis.optJSONObject("consensus").optJSONArray("coverageGaps")
                : null;
        if (coverageGaps != null) {
            for (int i = 0; i < coverageGaps.length(); i++) {
                String item = cleanNarrativeText(coverageGaps.optString(i, ""));
                if (!item.isEmpty()) {
                    sb.append("- ").append(item).append("\n");
                    wrote = true;
                }
            }
        }
        int ocrFailedPages = nativeEvidence != null ? nativeEvidence.optInt("ocrFailedCount", 0) : 0;
        if (ocrFailedPages > 0) {
            sb.append("- OCR failed on ").append(ocrFailedPages).append(" page(s); direct page review remains necessary on those pages.\n");
            wrote = true;
        }
        if (diagnostics != null && diagnostics.optBoolean("indeterminateDueToConcealment", false)) {
            sb.append("- Concealment handling is active in this run; unresolved items remain constitutionally indeterminate until more evidence is obtained.\n");
            wrote = true;
        }
        if (!wrote) {
            sb.append("No additional coverage-gap or concealment warnings were recorded beyond the contradiction ledger in this pass.\n");
        }
    }

    private void appendCharterExecutiveSummary(
            StringBuilder sb,
            ForensicReportAssembler.Assembly assembled,
            String patternLine
    ) {
        sb.append("Scope | Finding | Confidence\n");
        int emitted = 0;
        for (ForensicReportAssembler.IssueCard issue : assembled.issueGroups) {
            if (issue == null || emitted >= 6) {
                break;
            }
            sb.append(issue.title)
                    .append(" | ")
                    .append(issue.summary)
                    .append(" | ")
                    .append(firstNonEmpty(issue.confidence, "CERTIFIED"))
                    .append("\n");
            emitted++;
        }
        if (emitted == 0) {
            sb.append("Certified findings | No guardian-approved certified issue was available for publication in this pass. | NONE\n");
        }
        sb.append("Overall contradiction status: ")
                .append(assembled.contradictionPosture)
                .append("\n");
        if (shouldSuppressCoreRoleNarration(assembled)) {
            sb.append("Core publication note: role labels and legal narration stay withheld here unless separately anchored beyond the contradiction-led record.\n");
        } else if (!assembled.primaryHarmedParty.isEmpty()) {
            sb.append("Harmed party: ").append(assembled.primaryHarmedParty).append("\n");
            if (!assembled.offenceFindings.isEmpty()) {
                sb.append("Direct offence finding: ").append(assembled.offenceFindings.get(0)).append("\n");
            } else if (!assembled.actorConclusion.isEmpty()) {
                sb.append("Primary adverse actor: ").append(assembled.actorConclusion).append("\n");
            }
        }
        if (!patternLine.isEmpty()) {
            sb.append("Pattern summary: ").append(patternLine).append("\n");
        }
    }

    private void appendCharterEvidenceManifest(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled,
            JSONObject nativeEvidence
    ) {
        JSONArray sourceFiles = nativeEvidence != null ? nativeEvidence.optJSONArray("sourceFiles") : null;
        int artifactIndex = 1;
        if (sourceFiles != null && sourceFiles.length() > 0) {
            for (int i = 0; i < sourceFiles.length() && i < 8; i++) {
                JSONObject item = sourceFiles.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                sb.append("A").append(artifactIndex++)
                        .append(" ")
                        .append(safeValue(item.optString("fileName", "Source artifact")))
                        .append(" | SHA-512 ")
                        .append(shortHashDisplay(item.optString("sha512", "")))
                        .append("\n");
            }
        }
        for (ForensicReportAssembler.IssueCard issue : assembled.issueGroups) {
            if (artifactIndex > 10) {
                break;
            }
            if (issue == null || issue.evidencePages == null || issue.evidencePages.isEmpty()) {
                continue;
            }
            sb.append("A").append(artifactIndex++)
                    .append(" ")
                    .append(issue.title)
                    .append(" | pages ")
                    .append(joinIntegerPages(issue.evidencePages, 8))
                    .append(" | anchored in sealed bundle\n");
        }
        if (artifactIndex == 1) {
            sb.append("No evidence manifest entries were available in this pass.\n");
        } else {
            sb.append("Note: this is a partial manifest of the key items surfaced into the constitutional report.\n");
        }
    }

    private void appendCharterChainOfCustody(StringBuilder sb, AnalysisEngine.ForensicReport report) {
        sb.append("Step | Action | Timestamp | Actor | Integrity note\n");
        sb.append("1 | Source evidence selected for local analysis | ")
                .append(intakeSnapshot != null ? intakeSnapshot.localTime : "Not recorded")
                .append(" | User device | Evidence processed offline on-device\n");
        sb.append("2 | SHA-512 evidence hash fixed for the run | ")
                .append(intakeSnapshot != null ? intakeSnapshot.capturedAtUtc : "Not recorded")
                .append(" | Verum Omnis engine | Evidence hash ")
                .append(shortHashDisplay(report.evidenceHash))
                .append("\n");
        sb.append("3 | Findings package and sealed reports written to vault | ")
                .append(report.generatedAt != null ? report.generatedAt : "Not recorded")
                .append(" | Verum Omnis engine | Deterministic publication pipeline\n");
        sb.append("4 | This constitutional vault report generated | ")
                .append(report.generatedAt != null ? report.generatedAt : "Not recorded")
                .append(" | Verum Omnis engine | Subordinate to sealed evidence and findings package\n");
    }

    private void appendCharterContradictionLedger(StringBuilder sb, JSONObject diagnostics) {
        JSONArray contradictions = diagnostics != null ? diagnostics.optJSONArray("contradictionRegister") : null;
        int verifiedCount = diagnostics != null ? diagnostics.optInt("verifiedContradictionCount", 0) : 0;
        int candidateCount = diagnostics != null ? diagnostics.optInt("candidateContradictionCount", 0) : 0;
        int rejectedCount = diagnostics != null ? diagnostics.optInt("rejectedContradictionCount", 0) : 0;
        sb.append("Verified: ").append(verifiedCount)
                .append(" | Candidate: ").append(candidateCount)
                .append(" | Rejected: ").append(rejectedCount)
                .append("\n");
        if (contradictions == null || contradictions.length() == 0) {
            sb.append("No contradiction records were extracted in this pass.\n");
            return;
        }
        int emitted = 0;
        for (int i = 0; i < contradictions.length() && emitted < 6; i++) {
            JSONObject item = contradictions.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if ("REJECTED".equalsIgnoreCase(item.optString("status", ""))) {
                continue;
            }
            sb.append("C").append(emitted + 1)
                    .append(" | ")
                    .append(humanizeConflictType(item.optString("conflictType", "CONTRADICTION")))
                    .append(" | ")
                    .append(cleanNarrativeText(item.optString("whyItConflicts", "")))
                    .append(" | p. ")
                    .append(item.optInt("page", 0))
                    .append(" | ")
                    .append(item.optString("status", "CANDIDATE"))
                    .append("\n");
            String neededEvidence = item.optString("neededEvidence", "").trim();
            if (!neededEvidence.isEmpty()) {
                sb.append("  Resolution evidence needed: ").append(neededEvidence).append("\n");
            }
            emitted++;
        }
        if (emitted == 0) {
            sb.append("Only rejected contradiction entries were present in the raw register.\n");
        }
    }

    private void appendCharterAnchoredTimeline(StringBuilder sb, ForensicReportAssembler.Assembly assembled) {
        sb.append("Date | Event | Anchors | Status\n");
        if (assembled.chronology == null || assembled.chronology.isEmpty()) {
            sb.append("No anchored timeline could be reconstructed from the present record.\n");
            return;
        }
        int emitted = 0;
        for (ForensicReportAssembler.ChronologyEvent event : assembled.chronology) {
            if (event == null || emitted >= 12) {
                continue;
            }
            sb.append(event.dateLabel)
                    .append(" | ")
                    .append(event.summary)
                    .append(" | ")
                    .append(joinIntegerPages(event.evidencePages, 6))
                    .append(" | ")
                    .append(event.status)
                    .append("\n");
            emitted++;
        }
    }

    private void appendCharterTripleVerification(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            ForensicReportAssembler.Assembly assembled
    ) {
        appendTripleVerificationSection(sb, report);
        if (assembled.guardianApprovedCertifiedFindingCount > 0) {
            sb.append("Overall publication note: guardian-approved certified findings are present even where verified contradictions remain low or absent.\n");
        }
    }

    private void appendLegalAttorneyAnalysisSection(StringBuilder sb, AnalysisEngine.ForensicReport report) {
        JSONObject analysis = report != null && report.legalAttorneyAnalysis != null
                ? report.legalAttorneyAnalysis
                : new JSONObject();
        if (analysis.length() == 0) {
            sb.append("No local legal-attorney analysis was available in this pass.\n");
            return;
        }
        JSONObject jurisdiction = analysis.optJSONObject("jurisdiction");
        if (jurisdiction != null && jurisdiction.length() > 0) {
            sb.append("Jurisdiction: ")
                    .append(jurisdiction.optString("name", jurisdiction.optString("code", "unresolved")))
                    .append(" (").append(jurisdiction.optString("code", "unresolved")).append(")\n");
        }
        sb.append("Boundary: ")
                .append(analysis.optString("boundary",
                        "This layer must use sealed findings and bundled legal corpus excerpts only."))
                .append("\n");
        String mode = analysis.optString("mode", "").trim();
        if (!mode.isEmpty()) {
            sb.append("Mode: ").append(mode).append("\n");
        }
        String confidence = analysis.optString("confidence", "").trim();
        if (!confidence.isEmpty()) {
            sb.append("Confidence: ").append(confidence).append("\n");
        }
        String narrative = cleanNarrativeText(analysis.optString("analysis", ""));
        if (!narrative.isEmpty()) {
            sb.append("\n").append(narrative).append("\n");
        }
        JSONArray nextSteps = analysis.optJSONArray("nextSteps");
        if (nextSteps != null && nextSteps.length() > 0) {
            sb.append("\nNext procedural steps:\n");
            for (int i = 0; i < nextSteps.length() && i < 5; i++) {
                String item = cleanNarrativeText(nextSteps.optString(i, ""));
                if (!item.isEmpty()) {
                    sb.append("- ").append(item).append("\n");
                }
            }
        }
    }

    private void appendConsensusVerificationSection(StringBuilder sb, AnalysisEngine.ForensicReport report) {
        JSONObject consensus = report != null && report.consensusReview != null
                ? report.consensusReview
                : new JSONObject();
        if (consensus.length() == 0) {
            sb.append("No local consensus review block was available in this pass.\n");
            return;
        }
        sb.append("Method: ").append(consensus.optString("consensusMethod", "unavailable")).append("\n");
        sb.append("Posture: ").append(consensus.optString("posture", "unavailable")).append("\n");
        sb.append("Verified contradictions: ").append(consensus.optInt("verifiedContradictions", 0)).append("\n");
        sb.append("Candidate contradictions: ").append(consensus.optInt("candidateContradictions", 0)).append("\n");
        sb.append("Gemma legal layer present: ").append(consensus.optBoolean("gemmaReviewerAvailable", false) ? "Yes" : "No").append("\n");
        sb.append("Phi-3 reviewer installed: ").append(consensus.optBoolean("phi3ReviewerInstalled", false) ? "Yes" : "No").append("\n");
        JSONArray verifiedBy = consensus.optJSONArray("verifiedBy");
        if (verifiedBy != null && verifiedBy.length() > 0) {
            sb.append("Available reviewers: ").append(joinJsonStrings(verifiedBy)).append("\n");
        }
        String note = cleanNarrativeText(consensus.optString("note", ""));
        if (!note.isEmpty()) {
            sb.append(note).append("\n");
        }
    }

    private void appendCharterSealBlock(StringBuilder sb, AnalysisEngine.ForensicReport report) {
        sb.append("```\n");
        sb.append("VERUM_OMNIS_CONSTITUTION version=v5.2.7 status=FINAL_SEALED_IMMUTABLE\n");
        sb.append("encoding=UTF-8 newlines=LF ordinal_confidence_only=TRUE triple_verification=TRUE\n");
        sb.append("contradiction_first=TRUE nine_brain_rules=TRUE business_constitution=TRUE\n");
        sb.append("CANONICAL_TEXT_SHA512=<bundled constitution hash recorded in app integrity manifest>\n");
        sb.append("GUARDIAN_APPROVALS_SHA512=<OPTIONAL_OR_EMPTY>\n");
        sb.append("SEAL_PAYLOAD_SHA512=").append(safeValue(report.evidenceHash)).append("\n");
        sb.append("```\n");
        sb.append("Note: this report does not alter the underlying evidence seal.\n");
    }

    private void appendCharterCourtReadyDeclaration(StringBuilder sb) {
        sb.append("This output is generated under Verum Omnis Constitution v5.2.7 with contradiction-first review, deterministic extraction, anchored evidence, ordinal confidence only, and mandatory failure disclosure.\n");
        sb.append("No guilt, intent, punishment, or verdict is declared. Any missing evidence, integrity uncertainty, or unresolved contradiction is disclosed in the sections above.\n");
    }

    private void appendCharterLimitations(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            JSONObject nativeEvidence,
            JSONObject diagnostics
    ) {
        JSONArray coverageGaps = report.brainAnalysis != null
                && report.brainAnalysis.optJSONObject("consensus") != null
                ? report.brainAnalysis.optJSONObject("consensus").optJSONArray("coverageGaps")
                : null;
        if (coverageGaps != null && coverageGaps.length() > 0) {
            for (int i = 0; i < coverageGaps.length(); i++) {
                String item = coverageGaps.optString(i, "").trim();
                if (!item.isEmpty()) {
                    sb.append("- ").append(item).append("\n");
                }
            }
        }
        int ocrFailed = nativeEvidence != null ? nativeEvidence.optInt("ocrFailedCount", 0) : 0;
        if (ocrFailed > 0) {
            sb.append("- OCR failed on ").append(ocrFailed).append(" page(s); those pages should be checked against the sealed images directly.\n");
        }
        if (diagnostics != null && diagnostics.optBoolean("indeterminateDueToConcealment", false)) {
            sb.append("- Concealment handling was triggered in this run; unresolved items remain constitutionally indeterminate until more evidence is obtained.\n");
        }
        sb.append("- Visual-forensics cues are screening indicators unless confirmed against the sealed evidence pages.\n");
        sb.append("- This report does not constitute legal advice or a determination of criminal liability.\n");
    }

    private String joinIntegerPages(List<Integer> pages, int limit) {
        List<String> labels = new ArrayList<>();
        if (pages == null) {
            return "no page anchors";
        }
        for (Integer page : pages) {
            if (page == null || page <= 0) {
                continue;
            }
            labels.add("p. " + page);
            if (labels.size() >= limit) {
                break;
            }
        }
        return labels.isEmpty() ? "no page anchors" : joinHumanNames(labels);
    }

    private String joinJsonStrings(JSONArray array) {
        List<String> labels = new ArrayList<>();
        if (array == null) {
            return "";
        }
        for (int i = 0; i < array.length(); i++) {
            String value = cleanNarrativeText(array.optString(i, ""));
            if (!value.isEmpty() && !labels.contains(value)) {
                labels.add(value);
            }
        }
        return labels.isEmpty() ? "" : joinHumanNames(labels);
    }

    private String cleanNarrativeText(String value) {
        String cleaned = safeValue(value).replace('\n', ' ').replace('\r', ' ').trim();
        while (cleaned.contains("  ")) {
            cleaned = cleaned.replace("  ", " ");
        }
        cleaned = cleaned.replace("[truncated for on-device prompt]", "")
                .replace("...[truncated for on-device prompt]...", "")
                .replace("...[truncated for on-device prompt]", "")
                .trim();
        return clipText(cleaned, 220);
    }

    private void appendConstitutionalPublishedFindingLines(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            int limit
    ) {
        if (sb == null) {
            return;
        }
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        int emitted = 0;
        for (ForensicReportAssembler.IssueCard card : assembled.issueGroups) {
            if (emitted >= limit) {
                break;
            }
            String line = card.toPlainLine();
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            sb.append("- ").append(line.trim()).append("\n");
            if (!card.whyItMatters.isEmpty()) {
                sb.append("  Why this matters: ").append(card.whyItMatters).append("\n");
            }
            emitted++;
        }
        if (emitted == 0) {
            sb.append("- No guardian-approved certified findings were available for publication in this pass.\n");
        }
    }

    private AnalysisEngine.ForensicReport reconstructReadableBriefReport(
            JSONObject pending,
            String findingsJson
    ) {
        AnalysisEngine.ForensicReport report = new AnalysisEngine.ForensicReport();
        if (pending != null) {
            report.caseId = safeValue(pending.optString("caseId", ""));
            report.evidenceHash = safeValue(pending.optString("evidenceHash", ""));
            report.evidenceHashShort = !report.evidenceHash.isEmpty()
                    ? HashUtil.truncate(report.evidenceHash, 16)
                    : "";
            report.jurisdictionName = safeValue(pending.optString("jurisdictionName", ""));
            report.jurisdiction = safeValue(pending.optString("jurisdictionCode", ""));
            report.summary = safeValue(pending.optString("summary", ""));
        }
        if (findingsJson == null || findingsJson.trim().isEmpty()) {
            report.certifiedFindings = new JSONArray();
            report.investigatorSuppliedFacts = new JSONArray();
            return report;
        }
        try {
            JSONObject root = new JSONObject(findingsJson);
            report.caseId = firstNonEmpty(root.optString("caseId", null), report.caseId);
            report.evidenceHash = firstNonEmpty(root.optString("evidenceHash", null), report.evidenceHash);
            report.evidenceHashShort = !safeValue(report.evidenceHash).isEmpty()
                    ? HashUtil.truncate(report.evidenceHash, 16)
                    : safeValue(report.evidenceHashShort);
            report.jurisdictionName = firstNonEmpty(root.optString("jurisdictionName", null), report.jurisdictionName);
            report.jurisdiction = firstNonEmpty(root.optString("jurisdiction", null), report.jurisdiction);
            report.summary = firstNonEmpty(root.optString("summary", null), report.summary);
            report.guardianDecision = cloneJsonObject(root.optJSONObject("guardianDecision"));
            report.tripleVerification = cloneJsonObject(root.optJSONObject("tripleVerification"));
            report.forensicSynthesis = cloneJsonObject(root.optJSONObject("forensicSynthesis"));
            report.diagnostics = cloneJsonObject(root.optJSONObject("diagnostics"));
            report.nativeEvidence = cloneJsonObject(root.optJSONObject("nativeEvidence"));
            report.constitutionalExtraction = cloneJsonObject(root.optJSONObject("constitutionalExtraction"));
            report.jurisdictionResolution = cloneJsonObject(root.optJSONObject("jurisdictionResolution"));
            report.legalAttorneyAnalysis = cloneJsonObject(root.optJSONObject("legalAttorneyAnalysis"));
            report.consensusReview = cloneJsonObject(root.optJSONObject("consensusReview"));
            report.investigatorContext = cloneJsonObject(root.optJSONObject("investigatorContext"));
            report.certifiedFindings = root.optJSONArray("certifiedFindings");
            if (report.certifiedFindings == null) {
                report.certifiedFindings = new JSONArray();
            }
            report.normalizedCertifiedFindings = root.optJSONArray("normalizedCertifiedFindings");
            report.normalizedCertifiedFindingCount = root.optInt("normalizedCertifiedFindingCount", 0);
            JSONObject investigatorContext = root.optJSONObject("investigatorContext");
            JSONArray suppliedFacts = investigatorContext != null
                    ? investigatorContext.optJSONArray("suppliedFacts")
                    : root.optJSONArray("investigatorSuppliedFacts");
            report.investigatorSuppliedFacts = suppliedFacts != null ? suppliedFacts : new JSONArray();
            FindingPublicationNormalizer.applyToReport(report);
        } catch (JSONException error) {
            Log.w("MainActivity", "Could not reconstruct readable brief report from findings JSON during recovery.", error);
            if (report.certifiedFindings == null) {
                report.certifiedFindings = new JSONArray();
            }
            if (report.investigatorSuppliedFacts == null) {
                report.investigatorSuppliedFacts = new JSONArray();
            }
            FindingPublicationNormalizer.applyToReport(report);
        }
        return report;
    }

    private JSONObject cloneJsonObject(JSONObject source) {
        if (source == null) {
            return null;
        }
        try {
            return new JSONObject(source.toString());
        } catch (JSONException error) {
            Log.w("MainActivity", "Failed to clone JSON object for recovered readable brief state.", error);
            return source;
        }
    }

    private String buildRecoveredVisualFindingsMemo(JSONObject pending, String findingsJson) {
        try {
            JSONObject root = new JSONObject(findingsJson);
            JSONObject nativeEvidence = root.optJSONObject("nativeEvidence");
            JSONArray visualFindings = nativeEvidence != null ? nativeEvidence.optJSONArray("visualFindings") : null;
            JSONArray anchors = nativeEvidence != null ? nativeEvidence.optJSONArray("anchors") : null;
            StringBuilder sb = new StringBuilder();
            sb.append("VERUM OMNIS VISUAL FORENSICS MEMO\n")
                    .append("Case ID: ").append(pending.optString("caseId", "")).append("\n")
                    .append("Source File: ").append(pending.optString("sourceFileName", "unknown")).append("\n")
                    .append("Evidence SHA-512: ").append(pending.optString("evidenceHash", "")).append("\n\n");
            if (nativeEvidence != null) {
                sb.append("Recovered Coverage\n")
                        .append("Pages: ").append(nativeEvidence.optInt("pageCount", 0)).append("\n")
                        .append("Rendered Pages: ").append(nativeEvidence.optInt("renderedPageCount", 0)).append("\n")
                        .append("Document Text Pages: ").append(nativeEvidence.optInt("documentTextPageCount", 0)).append("\n")
                        .append("OCR Success Count: ").append(nativeEvidence.optInt("ocrSuccessCount", 0)).append("\n")
                        .append("OCR Failed Count: ").append(nativeEvidence.optInt("ocrFailedCount", 0)).append("\n\n");
            }
            sb.append("Recovered Visual Findings\n");
            if (visualFindings == null || visualFindings.length() == 0) {
                sb.append("No visual findings were recovered from the findings snapshot.\n");
            } else {
                int emitted = 0;
                for (int i = 0; i < visualFindings.length() && emitted < 10; i++) {
                    JSONObject finding = visualFindings.optJSONObject(i);
                    if (finding == null) {
                        continue;
                    }
                    sb.append("- Page ").append(finding.optInt("page", 0))
                            .append(" [").append(finding.optString("severity", "info")).append("] ")
                            .append(finding.optString("type", "UNKNOWN"))
                            .append(": ").append(finding.optString("description", ""))
                            .append("\n");
                    emitted++;
                }
            }
            if (anchors != null && anchors.length() > 0) {
                sb.append("\nRecovered Evidence Anchors\n");
                int emitted = 0;
                for (int i = 0; i < anchors.length() && emitted < 10; i++) {
                    JSONObject anchor = anchors.optJSONObject(i);
                    if (anchor == null) {
                        continue;
                    }
                    sb.append("- ").append(anchor.optString("evidenceId", "EV"))
                            .append(" / page ").append(anchor.optInt("page", 0))
                            .append(" / ").append(anchor.optString("type", ""))
                            .append("\n");
                    emitted++;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            Log.w("MainActivity", "Failed to rebuild visual findings memo from pending findings snapshot.", e);
            return "";
        }
    }

    private String buildRecoveredGemmaChatContext(JSONObject pending, String humanReadableReport) {
        StringBuilder sb = new StringBuilder();
        sb.append("Latest case context for Gemma.\n")
                .append("Case ID: ").append(pending.optString("caseId", "")).append("\n")
                .append("Source file: ").append(pending.optString("sourceFileName", "unknown")).append("\n")
                .append("Evidence SHA-512: ").append(pending.optString("evidenceHash", "")).append("\n")
                .append("Summary: ").append(pending.optString("summary", "")).append("\n")
                .append("\nGemma narrative report excerpt:\n")
                .append(clipText(humanReadableReport, 2500))
                .append("\n\nAudit report excerpt:\n")
                .append(clipText(pending.optString("auditReport", ""), 2200))
                .append("\n\nFindings package excerpt:\n")
                .append(clipText(pending.optString("findingsJson", ""), 2200));
        return sb.toString();
    }



    private String buildForensicDraftReport(

            AnalysisEngine.ForensicReport report,

            Map<String, String> integrityResults

    ) {

        StringBuilder draft = new StringBuilder();

        appendConstitutionalReport(

                draft,

                report,

                integrityResults,

                MediaForensics.inspectFile(selectedFile),

                AssistanceRestrictionManager.preview(report)

        );

        return draft.toString();

    }



    private String buildGemmaForensicPrompt(

            AnalysisEngine.ForensicReport report,

            Map<String, String> integrityResults,

            String findingsJson,

            String auditorReport

    ) {

        String sealedAuditRecord = buildGemmaAuditHandoff(report, auditorReport, findingsJson);
        if (sealedAuditRecord.trim().isEmpty()) {
            return "";
        }

        StringBuilder prompt = new StringBuilder();

        prompt.append("Write the final human-readable Verum Omnis forensic report under the Verum Omnis constitution.\n")

                .append("This is the final human report, not a dashboard summary.\n")

                .append("Use only the facts below.\n")

                .append("Do not invent facts, law, people, dates, or outcomes.\n")

                .append("State clearly what is observed, what is strongly indicated, and what remains unconfirmed.\n")
                .append("Consume contradiction, timeline, evidence, financial, and consistency outputs internally, but do not expose raw engine mechanics.\n")
                .append("Resolve conflicts into deterministic factual findings wherever the evidence supports it.\n")
                .append("If evidence is insufficient, say exactly: INSUFFICIENT EVIDENCE TO CONCLUDE.\n")
                .append("Treat the sealed auditor-facing forensic extraction report as the source of truth.\n")

                .append("Mention that the evidence was cryptographically sealed and stored in the vault before user disclosure.\n")

                .append("Do not expose raw contradictions, contradiction candidates, internal scoring, or analysis mechanics.\n")
                .append("Do not report bare counts without naming what they are.\n")
                .append("Do not restate alternate or legacy brain definitions; if consensus is mentioned, use only the current implementation-grounded output.\n")

                .append("Prioritize the serious, human-relevant facts: who did what, to whom, when, how, and why it matters.\n")
                .append("Keep the contradiction register human-readable and anchor-backed.\n")

                .append("Use the sealed audit record below as the source record.\n")

                .append("Use these headings exactly:\n")

                .append("Case Identification and Processing Status\n")
                .append("Factual-Fault Synthesis\n")
                .append("Guardian-Approved Certified Findings\n")
                .append("Primary Evidence Chains\n")
                .append("Contradiction Register\n")
                .append("Candidate Findings Needing Review\n")
                .append("Timeline Anchors\n")
                .append("Legal Exposure and Recommended Actions\n")
                .append("Audit and Seal Details\n\n")
                .append(sealedAuditRecord);

        return prompt.toString();

    }

    private String buildGemmaAuditHandoff(
            AnalysisEngine.ForensicReport report,
            String auditorReport,
            String findingsJson
    ) {
        return finalizePublishedNarrative(
                report,
                ConstitutionalNarrativePacketBuilder.buildForensicReportPacket(
                        report,
                        selectedFile != null ? selectedFile.getName() : "unknown",
                        auditorReport,
                        findingsJson
                )
        );
    }

    private String buildGemmaCaseBrief(

            AnalysisEngine.ForensicReport report,

            Map<String, String> integrityResults

    ) {

        if (report == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        JSONObject diagnostics = report.diagnostics != null ? report.diagnostics : new JSONObject();
        JSONObject extraction = report.constitutionalExtraction != null ? report.constitutionalExtraction : new JSONObject();
        JSONObject nativeEvidence = report.nativeEvidence != null ? report.nativeEvidence : new JSONObject();
        JSONArray namedParties = extraction.optJSONArray("namedParties");
        JSONArray legalSubjects = extraction.optJSONArray("criticalLegalSubjects");
        JSONArray incidents = extraction.optJSONArray("incidentRegister");
        JSONArray timelineAnchors = preferredEvidenceArray(extraction.optJSONArray("timelineAnchorRegister"), incidents);
        JSONArray actorConduct = extraction.optJSONArray("actorConductRegister");
        JSONArray financialExposure = extraction.optJSONArray("financialExposureRegister");
        JSONArray narrativeThemes = extraction.optJSONArray("narrativeThemeRegister");
        JSONArray documentIntegrityFindings = extraction.optJSONArray("documentIntegrityFindings");
        JSONArray contradictions = diagnostics.optJSONArray("contradictionRegister");
        JSONArray visualFindings = nativeEvidence.optJSONArray("visualFindings");
        JSONObject forensicSynthesis = preferredForensicSynthesis(report);
        JSONArray patternMatches = report.patternAnalysis != null ? report.patternAnalysis.optJSONArray("matches") : null;
        JSONArray vulnerabilityMatches = report.vulnerabilityAnalysis != null ? report.vulnerabilityAnalysis.optJSONArray("matches") : null;
        List<JSONObject> primaryTimeline = collectPrimaryHumanEvidence(timelineAnchors, namedParties, 10, false);
        List<JSONObject> primaryActorConduct = collectPrimaryHumanEvidence(actorConduct, namedParties, 8, false);
        List<JSONObject> primaryFinancialFindings = collectPrimaryHumanEvidence(financialExposure, namedParties, 8, false);
        List<JSONObject> primaryIntegrityFindings = collectPrimaryHumanEvidence(documentIntegrityFindings, namedParties, 6, false);
        List<JSONObject> primarySubjectFindings = combineHumanEvidenceLists(
                primaryActorConduct,
                primaryFinancialFindings,
                primaryIntegrityFindings,
                collectPrimaryHumanEvidence(legalSubjects, namedParties, 6, false)
        );
        List<JSONObject> primaryContradictions = collectPrimaryHumanEvidence(contradictions, namedParties, 6, false);
        List<JSONObject> primaryNarrativeThemes = collectPrimaryHumanEvidence(narrativeThemes, namedParties, 6, false);
        List<JSONObject> primaryVisualFindings = collectPrimaryHumanEvidence(visualFindings, namedParties, 6, true);
        List<String> actorNames = collectActorNamesFromEvidence(primaryTimeline, primaryActorConduct, 8);

        sb.append("Case Brief\n");
        sb.append("Source file: ").append(selectedFile != null ? selectedFile.getName() : "unknown").append("\n");
        sb.append("Case ID: ").append(safeValue(report.caseId)).append("\n");
        sb.append("Jurisdiction: ").append(safeValue(report.jurisdictionName));
        if (report.jurisdiction != null && !report.jurisdiction.trim().isEmpty()) {
            sb.append(" (").append(report.jurisdiction).append(")");
        }
        sb.append("\n");
        sb.append("Processing status: ")
                .append(diagnostics.optString("processingStatus",
                        diagnostics.optBoolean("indeterminateDueToConcealment", false)
                                ? "INDETERMINATE DUE TO CONCEALMENT" : "DETERMINATE"))
                .append("\n");
        sb.append("Evidence SHA-512: ").append(safeValue(report.evidenceHashShort)).append("\n");
        sb.append("Blockchain anchor: ").append(safeValue(report.blockchainAnchor)).append("\n");
        sb.append("Source pages: ").append(nativeEvidence.optInt("sourcePageCount", nativeEvidence.optInt("pageCount", 0))).append("\n");
        sb.append("Rendered pages: ").append(nativeEvidence.optInt("renderedPageCount", 0)).append("\n");
        sb.append("OCR success pages: ").append(nativeEvidence.optInt("ocrSuccessCount", 0)).append("\n");
        sb.append("OCR failed pages: ").append(nativeEvidence.optInt("ocrFailedCount", 0)).append("\n");

        if (!actorNames.isEmpty()) {
            sb.append("\nNamed actors:\n");
            for (String actor : actorNames) {
                sb.append("- ").append(actor).append("\n");
            }
        }

        if (!primaryTimeline.isEmpty()) {
            sb.append("\nWhat happened:\n");
            appendGemmaEvidenceLines(sb, primaryTimeline, 8);
        }

        if (!primarySubjectFindings.isEmpty()) {
            sb.append("\nSerious findings:\n");
            appendGemmaEvidenceLines(sb, primarySubjectFindings, 6);
        }

        if (report.topLiabilities != null && report.topLiabilities.length > 0) {
            sb.append("\nProbable offences or critical exposures:\n");
            int emitted = 0;
            for (String liability : report.topLiabilities) {
                if (liability == null || liability.trim().isEmpty()) {
                    continue;
                }
                if (emitted >= 6) {
                    break;
                }
                String trimmed = liability.trim();
                if ("INDETERMINATE DUE TO CONCEALMENT".equalsIgnoreCase(trimmed)) {
                    sb.append("- ").append(trimmed).append("\n");
                } else {
                    sb.append("- ").append(trimmed)
                            .append(": ")
                            .append(legalExposureForLabel(trimmed))
                            .append("\n");
                }
                emitted++;
            }
        }

        if (report.legalReferences != null && report.legalReferences.length > 0) {
            sb.append("\nRelevant legal references:\n");
            appendStringArrayLines(sb, report.legalReferences, 6);
        }

        if (!primaryContradictions.isEmpty()
                || !primaryNarrativeThemes.isEmpty()
                || (patternMatches != null && patternMatches.length() > 0)
                || (vulnerabilityMatches != null && vulnerabilityMatches.length() > 0)
                || report.truthContinuityAnalysis != null) {
            sb.append("\nContradictions and behaviour:\n");
            appendGemmaEvidenceLines(sb, primaryContradictions, 4);
            appendGemmaEvidenceLines(sb, primaryNarrativeThemes, 3);
            if (patternMatches != null && patternMatches.length() > 0) {
                sb.append("- Behavioural patterns: ").append(joinPatternArray(patternMatches, 5)).append(".\n");
            }
            if (vulnerabilityMatches != null && vulnerabilityMatches.length() > 0) {
                sb.append("- Vulnerability patterns: ").append(joinPatternArray(vulnerabilityMatches, 4)).append(".\n");
            }
            if (report.truthContinuityAnalysis != null) {
                String overallAssessment = report.truthContinuityAnalysis.optString("overallAssessment", "").trim();
                if (!overallAssessment.isEmpty()) {
                    sb.append("- Truth continuity: ").append(overallAssessment).append(".\n");
                }
            }
        }

        if (!primaryIntegrityFindings.isEmpty() || !primaryVisualFindings.isEmpty()) {
            sb.append("\nVisual and integrity findings:\n");
            appendGemmaEvidenceLines(sb, primaryIntegrityFindings, 4);
            appendGemmaEvidenceLines(sb, primaryVisualFindings, 5);
        }

        sb.append("\nVault and integrity record:\n");
        sb.append("- The original evidence, human report, audit report, and findings JSON were sealed in the vault before disclosure.\n");
        if (integrityResults != null && !integrityResults.isEmpty()) {
            int emitted = 0;
            for (Map.Entry<String, String> entry : integrityResults.entrySet()) {
                if (emitted >= 5) {
                    break;
                }
                sb.append("- ").append(entry.getKey()).append(": ").append(safeValue(entry.getValue())).append("\n");
                emitted++;
            }
        }

        return clipText(sb.toString(), 5200);

    }

    private void appendStringArrayLines(StringBuilder sb, String[] values, int maxItems) {
        if (values == null || values.length == 0) {
            return;
        }
        int emitted = 0;
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            sb.append("- ").append(value.trim()).append("\n");
            emitted++;
            if (emitted >= maxItems) {
                break;
            }
        }
    }

    private void appendJsonArrayLines(StringBuilder sb, String label, JSONArray array, int maxItems) {
        if (array == null || array.length() == 0) {
            return;
        }
        sb.append("\n").append(label).append(":\n");
        int emitted = 0;
        for (int i = 0; i < array.length() && emitted < maxItems; i++) {
            Object item = array.opt(i);
            String line = summarizeGemmaBriefItem(item);
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            sb.append("- ").append(line).append("\n");
            emitted++;
        }
    }

    private String summarizeGemmaBriefItem(Object item) {
        return summarizeBriefItem(item, 320, false);
    }

    private String summarizeHumanReportItem(Object item) {
        return summarizeBriefItem(item, 680, true);
    }

    private String summarizeBriefItem(Object item, int maxChars, boolean humanReportMode) {
        if (item == null) {
            return null;
        }
        if (!(item instanceof JSONObject)) {
            return safeValue(String.valueOf(item));
        }

        JSONObject object = (JSONObject) item;
        StringBuilder line = new StringBuilder();
        appendGemmaBriefPart(line, firstNonEmpty(
                object.optString("name", null),
                object.optString("title", null),
                object.optString("subject", null),
                object.optString("eventType", null),
                object.optString("conductType", null),
                object.optString("theme", null),
                object.optString("amountCategory", null),
                object.optString("category", null),
                object.optString("type", null)
        ));
        appendGemmaBriefPart(line, object.optString("summary", null));
        appendGemmaBriefPart(line, object.optString("narrative", null));
        appendGemmaBriefPart(line, object.optString("excerpt", null));
        appendGemmaBriefPart(line, object.optString("description", null));
        appendGemmaBriefPart(line, object.optString("whyItMatters", null));
        appendGemmaBriefPart(line, object.optString("whyItConflicts", null));
        appendGemmaBriefPart(line, object.optString("amount", null));

        if (object.has("page")) {
            appendGemmaBriefPart(line, "page " + object.optInt("page"));
        } else if (object.has("pageAnchor")) {
            appendGemmaBriefPart(line, safeValue(object.optString("pageAnchor")));
        }

        if (object.has("pages")) {
            JSONArray pages = object.optJSONArray("pages");
            if (pages != null && pages.length() > 0) {
                StringBuilder pagesText = new StringBuilder("pages ");
                for (int i = 0; i < pages.length() && i < 4; i++) {
                    if (i > 0) {
                        pagesText.append(", ");
                    }
                    pagesText.append(pages.opt(i));
                }
                appendGemmaBriefPart(line, pagesText.toString());
            }
        }

        return humanReportMode
                ? clipReportText(line.toString(), maxChars)
                : clipText(line.toString(), maxChars);
    }

    private void appendGemmaBriefPart(StringBuilder line, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.isEmpty()) {
            return;
        }
        if (line.length() > 0) {
            line.append(" | ");
        }
        line.append(normalized);
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String lowerUs(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private String upperUs(String value) {
        return value == null ? "" : value.toUpperCase(Locale.US);
    }

    private JSONArray preferredEvidenceArray(JSONArray primary, JSONArray fallback) {
        return primary != null && primary.length() > 0 ? primary : fallback;
    }

    @SafeVarargs
    private final List<JSONObject> combineHumanEvidenceLists(List<JSONObject>... lists) {
        List<JSONObject> combined = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (lists == null) {
            return combined;
        }
        for (List<JSONObject> list : lists) {
            if (list == null) {
                continue;
            }
            for (JSONObject item : list) {
                if (item == null) {
                    continue;
                }
                String key = item.optString("summary", "")
                        + "|"
                        + item.optString("label", "")
                        + "|"
                        + item.optString("actor", "");
                if (!seen.add(key)) {
                    continue;
                }
                combined.add(item);
            }
        }
        return sortByNarrativePriority(combined);
    }

    private String safeValue(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value.trim();
    }



    private String buildFallbackHumanReport(

            AnalysisEngine.ForensicReport report,

            Map<String, String> integrityResults

    ) {

        String fileName = selectedFile != null ? selectedFile.getName() : "the uploaded evidence";
        JSONObject extraction = report.constitutionalExtraction != null ? report.constitutionalExtraction : new JSONObject();
        JSONObject diagnostics = report.diagnostics != null ? report.diagnostics : new JSONObject();
        JSONObject nativeEvidence = report.nativeEvidence != null ? report.nativeEvidence : new JSONObject();

        JSONArray namedParties = extraction.optJSONArray("namedParties");
        JSONArray legalSubjects = extraction.optJSONArray("criticalLegalSubjects");
        JSONArray incidents = extraction.optJSONArray("incidentRegister");
        JSONArray timelineAnchors = preferredEvidenceArray(extraction.optJSONArray("timelineAnchorRegister"), incidents);
        JSONArray actorConduct = extraction.optJSONArray("actorConductRegister");
        JSONArray financialExposure = extraction.optJSONArray("financialExposureRegister");
        JSONArray narrativeThemes = extraction.optJSONArray("narrativeThemeRegister");
        JSONArray anchoredFindings = extraction.optJSONArray("anchoredFindings");
        JSONArray documentIntegrityFindings = extraction.optJSONArray("documentIntegrityFindings");
        JSONArray contradictions = diagnostics.optJSONArray("contradictionRegister");
        JSONArray visualFindings = nativeEvidence.optJSONArray("visualFindings");
        JSONArray stableNarratives = report.truthContinuityAnalysis != null
                ? report.truthContinuityAnalysis.optJSONArray("stableNarratives") : null;
        JSONArray narrativeShifts = report.truthContinuityAnalysis != null
                ? report.truthContinuityAnalysis.optJSONArray("narrativeShifts") : null;
        JSONArray brokenLinks = report.truthContinuityAnalysis != null
                ? report.truthContinuityAnalysis.optJSONArray("brokenLinks") : null;
        JSONArray patternMatches = report.patternAnalysis != null
                ? report.patternAnalysis.optJSONArray("matches") : null;
        JSONArray vulnerabilityIndicators = report.vulnerabilityAnalysis != null
                ? report.vulnerabilityAnalysis.optJSONArray("indicators") : null;
        JSONArray vulnerabilityMatches = report.vulnerabilityAnalysis != null
                ? report.vulnerabilityAnalysis.optJSONArray("matches") : null;

        boolean indeterminateDueToConcealment = diagnostics.optBoolean("indeterminateDueToConcealment", false);
        String processingStatus = diagnostics.optString(
                "processingStatus",
                indeterminateDueToConcealment ? "INDETERMINATE DUE TO CONCEALMENT" : "DETERMINATE"
        );
        boolean guardianApproved = isGuardianApproved(report);
        boolean hasCertifiedFindings = normalizedCertifiedFindingCount(report) > 0;

        int sourcePages = nativeEvidence.optInt("sourcePageCount", nativeEvidence.optInt("pageCount", 0));
        int renderedPages = nativeEvidence.optInt("renderedPageCount", 0);
        int ocrSuccessPages = nativeEvidence.optInt("ocrSuccessCount", 0);
        int ocrFailedPages = nativeEvidence.optInt("ocrFailedCount", 0);

        StringBuilder sb = new StringBuilder();
        sb.append("Forensic Analysis of ").append(fileName).append("\n\n");

        sb.append("1. Forensic Integrity of the Case File\n");
        sb.append("This file was processed locally under the Verum Omnis protocol and sealed before release.\n");
        sb.append("Findings:\n");
        sb.append("- Processing status: ").append(processingStatus).append("\n");
        sb.append("- Case ID: ").append(report.caseId).append("\n");
        sb.append("- Jurisdiction: ").append(report.jurisdictionName).append(" (").append(report.jurisdiction).append(")\n");
        sb.append("- Evidence SHA-512: ").append(report.evidenceHash).append("\n");
        sb.append("- Blockchain anchor: ").append(report.blockchainAnchor).append("\n");
        sb.append("- Integrity checks: ").append(formatIntegritySummary(integrityResults)).append("\n");
        sb.append("- Source pages: ").append(sourcePages)
                .append(" | Rendered pages: ").append(renderedPages)
                .append(" | OCR success pages: ").append(ocrSuccessPages)
                .append(" | OCR failed pages: ").append(ocrFailedPages)
                .append("\n");
        if (intakeSnapshot != null) {
            sb.append("- Intake record: ")
                    .append(intakeSnapshot.capturedAtUtc)
                    .append(" UTC | ")
                    .append(intakeSnapshot.localTime)
                    .append(" | ")
                    .append(intakeSnapshot.coordinatesLabel())
                    .append("\n");
        }
        sb.append("Conclusion:\n");
        if (indeterminateDueToConcealment) {
            sb.append("The record is cryptographically sealed, but extraction remains materially incomplete or obstructed. ")
                    .append("No mature offence conclusion should be treated as final until fuller extraction is available.\n");
        } else {
            sb.append("The record is cryptographically sealed and the evidentiary chain is intact within the current extraction pass.\n");
        }
        if (!guardianApproved) {
            sb.append("Guardian review denied certification for this run. The material below is candidate or narrative only and should not be treated as court-ready without further review.\n");
            String guardianReason = guardianDecisionReason(report);
            if (!guardianReason.isEmpty()) {
                sb.append("Guardian reason: ").append(guardianReason).append("\n");
            }
        }
        sb.append("\nNine-brain consensus:\n");
        appendBrainConsensusSection(sb, report.brainAnalysis);
        sb.append("\nTriple verification:\n");
        appendTripleVerificationSection(sb, report);

        sb.append("\n2. Ingestion Summary\n");
        sb.append("- Source page count: ").append(sourcePages).append("\n");
        sb.append("- Rendered page count: ").append(renderedPages).append("\n");
        sb.append("- OCR success count: ").append(ocrSuccessPages).append("\n");
        sb.append("- OCR failed count: ").append(ocrFailedPages).append("\n");
        sb.append("- Native text page count: ").append(nativeEvidence.optInt("documentTextPageCount", 0)).append("\n");
        sb.append("- Analysis source: ").append(diagnostics.optString("analysisSource", "local")).append("\n");
        sb.append("- Continuity warning: ")
                .append(indeterminateDueToConcealment ? "material continuity or extraction concerns remain" : "usable with disclosed continuity limits")
                .append("\n");

        sb.append("\n3. Guardian-Approved Certified Findings\n");
        if (hasCertifiedFindings) {
            appendCertifiedFindingRegisterSection(sb, report, 12);
        } else {
            sb.append("No guardian-approved certified findings survived the publication layer.\n");
        }

        sb.append("\n4. Candidate Findings Needing Review\n");
        appendNamedPartiesSection(sb, extraction);
        appendAnchoredFindingsSection(sb, extraction);
        appendActorConductRegisterSection(sb, actorConduct);
        appendFinancialExposureRegisterSection(sb, financialExposure);
        appendCriticalLegalSubjectsSection(sb, extraction);
        appendNarrativeThemeRegisterSection(sb, narrativeThemes);

        sb.append("\n4A. Excluded Synthetic or Secondary Narrative Material\n");
        appendExcludedSecondaryNarrativeSection(sb, extraction, nativeEvidence);

        sb.append("\n5. Rejected Findings Appendix\n");
        sb.append("Rejected contradiction items remain in the contradiction register appendix and are not to be treated as proven conflict findings.\n");
        appendContradictionRegisterSection(sb, diagnostics);

        sb.append("\n6. Timeline Anchors\n");
        appendTimelineAnchorRegisterSection(sb, timelineAnchors);

        sb.append("\n7. Document Integrity Findings\n");
        appendDocumentIntegrityFindingsSection(sb, documentIntegrityFindings);
        appendNativeEvidenceSection(sb, nativeEvidence);

        sb.append("\n8. Narrative Pattern Summary\n");
        if (report.truthContinuityAnalysis != null) {
            sb.append("Truth continuity assessment: ")
                    .append(report.truthContinuityAnalysis.optString("overallAssessment", "not assessed"))
                    .append("\n");
            if (stableNarratives != null && stableNarratives.length() > 0) {
                sb.append("Stable narratives: ").append(joinJsonArray(stableNarratives, 4)).append("\n");
            }
            if (narrativeShifts != null && narrativeShifts.length() > 0) {
                sb.append("Narrative shifts: ").append(joinJsonArray(narrativeShifts, 4)).append("\n");
            }
            if (brokenLinks != null && brokenLinks.length() > 0) {
                sb.append("Broken links: ").append(joinJsonArray(brokenLinks, 4)).append("\n");
            }
        } else {
            sb.append("Truth continuity analysis was not available in this pass.\n");
        }
        if (narrativeThemes != null && narrativeThemes.length() > 0) {
            sb.append("Distilled narrative themes:\n");
            appendNarrativeThemeRegisterSection(sb, narrativeThemes);
        }
        if (patternMatches != null && patternMatches.length() > 0) {
            sb.append("Pattern analysis: ").append(joinPatternArray(patternMatches, 5)).append("\n");
        }
        if (report.vulnerabilityAnalysis != null) {
            sb.append("Vulnerability assessment: ")
                    .append(report.vulnerabilityAnalysis.optString("assessment", "not assessed"))
                    .append("\n");
            if (vulnerabilityIndicators != null && vulnerabilityIndicators.length() > 0) {
                sb.append("Indicators: ").append(joinJsonArray(vulnerabilityIndicators, 4)).append("\n");
            }
            if (vulnerabilityMatches != null && vulnerabilityMatches.length() > 0) {
                sb.append("Matched vulnerability patterns: ").append(joinPatternArray(vulnerabilityMatches, 4)).append("\n");
            }
        }

        sb.append("\n9. Escalation, Exposure, and Legal Position\n");
        sb.append("- Jurisdiction anchor: ").append(report.jurisdictionAnchor).append("\n");
        sb.append("- Legal references: ").append(joinList(report.legalReferences, 6)).append("\n");
        sb.append("- Leading exposures or offences: ").append(joinList(report.topLiabilities, 6)).append("\n");
        sb.append("- Contradiction hygiene: ")
                .append(diagnostics.optInt("verifiedContradictionCount", 0)).append(" verified, ")
                .append(diagnostics.optInt("candidateContradictionCount", 0)).append(" candidate, ")
                .append(diagnostics.optInt("rejectedContradictionCount", 0)).append(" rejected.\n");
        if (!guardianApproved) {
            sb.append("Legal position: no finding was certified in this run; any asserted exposure remains candidate only pending further review.\n");
        } else if (indeterminateDueToConcealment) {
            sb.append("Legal position: this record must presently be treated as indeterminate due to concealment or insufficient extraction.\n");
        } else {
            sb.append("Legal position: this record is sufficiently extracted to support a human-readable forensic opinion, subject to audit against the sealed technical report.\n");
        }

        sb.append("\n10. Summary of Forensic Findings\n");
        sb.append("| Element | Status |\n");
        sb.append("|--------|--------|\n");
        sb.append("| Evidentiary integrity | ")
                .append(indeterminateDueToConcealment ? "Sealed but extraction incomplete" : "Sealed and reviewable")
                .append(" |\n");
        sb.append("| Named parties | ").append(namedParties != null ? namedParties.length() : 0).append(" extracted |\n");
        sb.append("| Legal subjects | ").append(legalSubjects != null ? legalSubjects.length() : 0).append(" extracted |\n");
        sb.append("| Timeline anchors | ").append(timelineAnchors != null ? timelineAnchors.length() : 0).append(" extracted |\n");
        sb.append("| Actor conduct | ").append(actorConduct != null ? actorConduct.length() : 0).append(" extracted |\n");
        sb.append("| Financial exposure | ").append(financialExposure != null ? financialExposure.length() : 0).append(" extracted |\n");
        sb.append("| Anchored findings | ").append(anchoredFindings != null ? anchoredFindings.length() : 0).append(" extracted |\n");
        sb.append("| Document integrity | ").append(documentIntegrityFindings != null ? documentIntegrityFindings.length() : 0).append(" extracted |\n");
        sb.append("| Narrative themes | ").append(narrativeThemes != null ? narrativeThemes.length() : 0).append(" extracted |\n");
        sb.append("| Contradictions | ")
                .append(diagnostics.optInt("verifiedContradictionCount", 0)).append(" verified / ")
                .append(diagnostics.optInt("candidateContradictionCount", 0)).append(" candidate / ")
                .append(diagnostics.optInt("rejectedContradictionCount", 0)).append(" rejected |\n");
        sb.append("| Visual findings | ").append(visualFindings != null ? visualFindings.length() : 0).append(" extracted |\n");
        sb.append("| Legal theory | ").append(joinList(report.legalReferences, 4)).append(" |\n");

        sb.append("\n11. Conclusion\n");
        if (indeterminateDueToConcealment) {
            sb.append("Final forensic opinion: the record is sealed and auditable, but the present extraction remains constitutionally indeterminate. ")
                    .append("The user should rely on the sealed audit report and findings JSON until the missing actor-event-page chain is fully extracted.\n");
        } else {
            sb.append("Final forensic opinion: the record is sealed, internally coherent in the current extraction pass, and suitable for human review alongside the sealed auditor report. ")
                    .append("The findings above should be read as the human-facing explanation of the same sealed technical record kept in the vault.\n");
        }

        sb.append("\nIntegrity and Vault Record\n");
        sb.append("The evidence, the human-readable report, the audit report, and the findings JSON were cryptographically sealed and written to the vault before disclosure.\n");

        sb.append("\nImmediate Next Actions\n");
        sb.append("1. Read this human report first for the narrative overview.\n");
        sb.append("2. Use the forensic audit PDF and findings JSON in the vault to verify page anchors, extracts, and legal references.\n");
        sb.append("3. Use Gemma chat only as a discussion layer on top of the sealed case context, not as the primary evidentiary record.");

        return sb.toString();

    }

    private String buildHumanForensicReport(

            AnalysisEngine.ForensicReport report,

            Map<String, String> integrityResults

    ) {

        String fileName = selectedFile != null ? selectedFile.getName() : "the uploaded evidence";
        JSONObject extraction = report.constitutionalExtraction != null ? report.constitutionalExtraction : new JSONObject();
        JSONObject diagnostics = report.diagnostics != null ? report.diagnostics : new JSONObject();
        JSONObject nativeEvidence = report.nativeEvidence != null ? report.nativeEvidence : new JSONObject();
        JSONArray namedParties = extraction.optJSONArray("namedParties");
        JSONArray legalSubjects = extraction.optJSONArray("criticalLegalSubjects");
        JSONArray incidents = extraction.optJSONArray("incidentRegister");
        JSONArray timelineAnchors = preferredEvidenceArray(extraction.optJSONArray("timelineAnchorRegister"), incidents);
        JSONArray actorConduct = extraction.optJSONArray("actorConductRegister");
        JSONArray financialExposure = extraction.optJSONArray("financialExposureRegister");
        JSONArray narrativeThemes = extraction.optJSONArray("narrativeThemeRegister");
        JSONArray documentIntegrityFindings = extraction.optJSONArray("documentIntegrityFindings");
        JSONArray contradictions = diagnostics.optJSONArray("contradictionRegister");
        JSONArray visualFindings = nativeEvidence.optJSONArray("visualFindings");
        JSONObject forensicSynthesis = preferredForensicSynthesis(report);
        JSONArray patternMatches = report.patternAnalysis != null ? report.patternAnalysis.optJSONArray("matches") : null;
        JSONArray vulnerabilityMatches = report.vulnerabilityAnalysis != null ? report.vulnerabilityAnalysis.optJSONArray("matches") : null;
        boolean guardianApproved = isGuardianApproved(report);
        List<JSONObject> primaryTimeline = filterMeaningfulHumanTimeline(collectPrimaryHumanEvidence(timelineAnchors, namedParties, 18, false));
        List<JSONObject> primaryActorConduct = collectPrimaryHumanEvidence(actorConduct, namedParties, 12, false);
        List<JSONObject> primaryFinancialFindings = collectPrimaryHumanEvidence(financialExposure, namedParties, 10, false);
        List<JSONObject> primaryIntegrityFindings = collectPrimaryHumanEvidence(documentIntegrityFindings, namedParties, 10, false);
        List<JSONObject> primaryThemeFindings = collectPrimaryHumanEvidence(narrativeThemes, namedParties, 8, false);
        List<JSONObject> primarySubjectFindings = collectPrimaryHumanEvidence(legalSubjects, namedParties, 12, false);
        List<JSONObject> executiveSubjectFindings = filterExecutiveHumanEvidence(primarySubjectFindings);
        List<JSONObject> primaryLeadFindings = combineHumanEvidenceLists(
                primaryFinancialFindings,
                primaryIntegrityFindings,
                primaryTimeline,
                primaryActorConduct
        );
        List<JSONObject> primaryContradictions = collectPrimaryHumanEvidence(contradictions, namedParties, 10, false);
        List<JSONObject> candidateContradictions = collectPrimaryHumanEvidenceByStatus(contradictions, namedParties, 8, false, "CANDIDATE");
        List<JSONObject> verifiedContradictions = collectPrimaryHumanEvidenceByStatus(contradictions, namedParties, 6, false, "VERIFIED");
        List<JSONObject> verifiedFinancialFindings = collectPrimaryHumanEvidenceByStatus(financialExposure, namedParties, 6, false, "VERIFIED");
        List<JSONObject> primaryVisualFindings = collectPrimaryHumanEvidence(visualFindings, namedParties, 10, true);
        List<JSONObject> certifiedNarratives = certifiedFindingNarratives(report, 6);
        List<JSONObject> openIssues = combineHumanEvidenceLists(
                candidateContradictions,
                primaryThemeFindings,
                primaryContradictions
        );

        boolean indeterminateDueToConcealment = diagnostics.optBoolean("indeterminateDueToConcealment", false);
        String processingStatus = diagnostics.optString(
                "processingStatus",
                indeterminateDueToConcealment ? "INDETERMINATE DUE TO CONCEALMENT" : "DETERMINATE"
        );
        boolean hasCertifiedFindings = normalizedCertifiedFindingCount(report) > 0;
        if (hasCertifiedFindings && "INDETERMINATE DUE TO CONCEALMENT".equalsIgnoreCase(processingStatus)) {
            processingStatus = "COMPLETED";
        }
        final String resolvedProcessingStatus = processingStatus;

        List<JSONObject> chronologyEvidence = buildHumanChronologyEvidence(
                primaryTimeline,
                primaryLeadFindings,
                primarySubjectFindings,
                verifiedContradictions
        );

        HumanFindingsReportBuilder.Input input = new HumanFindingsReportBuilder.Input();
        input.sourceFileName = fileName;
        input.sections.add(new HumanFindingsReportBuilder.Section(
                "1. WHAT THIS REPORT IS ABOUT",
                renderContradictionSection(sb -> appendHumanCaseOverviewSection(
                        sb,
                        report,
                        forensicSynthesis,
                        certifiedNarratives,
                        combineHumanEvidenceLists(primaryLeadFindings, executiveSubjectFindings, primaryContradictions),
                        guardianApproved,
                        resolvedProcessingStatus
                ))
        ));

        if (hasInvestigatorSuppliedFacts(report)) {
            input.sections.add(new HumanFindingsReportBuilder.Section(
                    "1A. INVESTIGATOR-SUPPLIED CONTEXT",
                    renderContradictionSection(sb -> appendHumanInvestigatorContextSection(sb, report))
            ));
        }

        input.sections.add(new HumanFindingsReportBuilder.Section(
                "1B. FORENSIC CONCLUSION",
                renderContradictionSection(sb -> appendHumanForensicConclusionSection(sb, report, assembledReadFirstPages(report)))
        ));

        input.sections.add(new HumanFindingsReportBuilder.Section(
                "2. WHAT THE SEALED RECORD CURRENTLY SHOWS",
                renderContradictionSection(sb -> appendHumanNarrativeExecutiveSummarySection(
                        sb,
                        fileName,
                        report,
                        forensicSynthesis,
                        certifiedNarratives,
                        combineHumanEvidenceLists(primaryLeadFindings, executiveSubjectFindings, primaryContradictions),
                        primaryTimeline,
                        guardianApproved,
                        indeterminateDueToConcealment
                ))
        ));

        input.sections.add(new HumanFindingsReportBuilder.Section(
                "2A. CHRONOLOGY OF KEY EVENTS",
                renderContradictionSection(sb -> appendHumanChronologySection(
                        sb,
                        report,
                        chronologyEvidence,
                        "Partial chronology could not yet be reconstructed from anchored dated material in this pass."
                ))
        ));

        input.sections.add(new HumanFindingsReportBuilder.Section(
                "3. MAIN FINDINGS",
                renderContradictionSection(sb -> {
                    sb.append("3A. Certified findings\n");
                    appendHumanVerifiedFindingsSection(sb, report);
                    sb.append("\n3B. Core facts\n");
                    appendHumanCoreFindingsSection(
                            sb,
                            combineHumanEvidenceLists(
                                    certifiedNarratives,
                                    primaryLeadFindings,
                                    executiveSubjectFindings,
                                    verifiedFinancialFindings
                            ),
                            "No core fact statement survived the current primary-evidence filter."
                    );
                })
        ));

        input.sections.add(new HumanFindingsReportBuilder.Section(
                "4. WHO IS INVOLVED",
                renderContradictionSection(sb -> appendHumanActorEvidenceSection(
                        sb,
                        forensicSynthesis,
                        namedParties,
                        combineHumanEvidenceLists(primaryActorConduct, primaryLeadFindings, primaryContradictions),
                        combineHumanEvidenceLists(primarySubjectFindings, primaryFinancialFindings, primaryIntegrityFindings),
                        6
                ))
        ));

        input.sections.add(new HumanFindingsReportBuilder.Section(
                "5. WHAT STILL NEEDS REVIEW",
                renderContradictionSection(sb -> appendHumanOpenIssuesSection(
                        sb,
                        openIssues,
                        guardianApproved,
                        indeterminateDueToConcealment
                ))
        ));

        input.sections.add(new HumanFindingsReportBuilder.Section(
                "6. WHAT NEEDS TO HAPPEN NEXT",
                renderContradictionSection(sb -> appendHumanRecommendedActionsSection(
                        sb,
                        report,
                        guardianApproved,
                        indeterminateDueToConcealment,
                        hasCertifiedFindings,
                        contradictions,
                        financialExposure
                ))
        ));

        input.sections.add(new HumanFindingsReportBuilder.Section(
                "7. EVIDENCE INDEX",
                renderContradictionSection(sb -> appendHumanEvidenceIndexSection(
                        sb,
                        combineHumanEvidenceLists(
                                certifiedNarratives,
                                primaryLeadFindings,
                                executiveSubjectFindings,
                                verifiedContradictions,
                                verifiedFinancialFindings
                        ),
                        12
                ))
        ));

        input.sections.add(new HumanFindingsReportBuilder.Section(
                "8. APPENDIX: LEGAL SUBJECTS",
                renderContradictionSection(sb -> appendHumanCriticalLegalSubjectsSection(sb, legalSubjects, report))
        ));

        input.sections.add(new HumanFindingsReportBuilder.Section(
                "9. APPENDIX: CONTRADICTIONS AND PATTERN FLAGS",
                renderContradictionSection(sb -> {
                    appendHumanSynthesisSection(
                            sb,
                            fileName,
                            report,
                            forensicSynthesis,
                            primaryLeadFindings,
                            combineHumanEvidenceLists(
                                    primaryActorConduct,
                                    executiveSubjectFindings,
                                    primaryIntegrityFindings,
                                    primaryFinancialFindings
                            ),
                            indeterminateDueToConcealment,
                            guardianApproved
                    );
                    appendHumanContradictionAndForgerySection(
                            sb,
                            report,
                            combineHumanEvidenceLists(verifiedContradictions, primaryContradictions),
                            primaryVisualFindings,
                            patternMatches,
                            vulnerabilityMatches,
                            report.truthContinuityAnalysis
                    );
                })
        ));

        input.sections.add(new HumanFindingsReportBuilder.Section(
                "10. APPENDIX: VISUAL AND DOCUMENT FORENSICS",
                renderContradictionSection(sb -> appendHumanVisualEvidenceSummarySection(sb, primaryVisualFindings, nativeEvidence))
        ));

        input.sections.add(new HumanFindingsReportBuilder.Section(
                "11. APPENDIX: TECHNICAL VERIFICATION AND SEAL",
                renderContradictionSection(sb -> {
                    sb.append("11A. Legal subject mapping\n");
                    appendHumanLegalSubjectMappingSection(
                            sb,
                            legalSubjects,
                            combineHumanEvidenceLists(primarySubjectFindings, primaryFinancialFindings, primaryIntegrityFindings),
                            primaryTimeline,
                            report
                    );
                    sb.append("\n11B. Financial exposure department\n");
                    appendHumanCoreFindingsSection(
                            sb,
                            combineHumanEvidenceLists(primaryFinancialFindings, verifiedFinancialFindings),
                            "No structured financial-exposure finding survived the current primary-evidence filter."
                    );
                    sb.append("\n11C. Document integrity department\n");
                    appendHumanCoreFindingsSection(
                            sb,
                            primaryIntegrityFindings,
                            "No first-class document-integrity finding survived the current primary-evidence filter."
                    );
                    sb.append("\n11D. Native media and OCR department\n");
                    appendHumanNativeMediaDepartmentSection(sb, nativeEvidence);
                    sb.append("\n11E. Actor conduct department\n");
                    appendHumanActorEvidenceSection(
                            sb,
                            forensicSynthesis,
                            namedParties,
                            primaryActorConduct,
                            combineHumanEvidenceLists(primaryFinancialFindings, primaryIntegrityFindings),
                            12
                    );
                    sb.append("\n11F. Findings snapshot\n");
                    appendHumanKeyFindingsSnapshotSection(
                            sb,
                            certifiedNarratives,
                            combineHumanEvidenceLists(primaryLeadFindings, verifiedFinancialFindings, verifiedContradictions),
                            openIssues
                    );
                    sb.append("\n11G. Candidate-only or secondary material\n");
                    appendHumanCoreFindingsSection(
                            sb,
                            combineHumanEvidenceLists(primaryThemeFindings, primarySubjectFindings, primaryContradictions),
                            "No candidate-only narrative or support finding needed to be carried forward in this pass."
                    );
                    appendExcludedSecondaryNarrativeSection(sb, extraction, nativeEvidence);
                    sb.append("\n11H. Certified finding technical certification\n");
                    appendCertifiedFindingRegisterSection(sb, report, 8);
                    sb.append("\n11I. Post-analysis declaration and seal\n");
                    sb.append("This report was issued under the Verum Omnis protocol and sealed alongside the original evidence and audit record.\n");
                    sb.append("- Evidence SHA-512: ").append(report.evidenceHash).append("\n");
                    sb.append("- Blockchain anchor: ").append(report.blockchainAnchor).append("\n");
                    sb.append("- Vault record: original evidence, human report, audit report, and findings JSON sealed together.\n");
                    sb.append("- Technical note: the detailed component integrity table remains in the sealed forensic audit report, not in this human-facing summary.\n");
                    if (intakeSnapshot != null) {
                        sb.append("- Date of report: ").append(intakeSnapshot.localTime).append("\n");
                    }
                    sb.append("- Case ID: ").append(report.caseId).append("\n");
                    sb.append("- Jurisdiction: ").append(report.jurisdictionName).append(" (").append(report.jurisdiction).append(")\n");
                    sb.append("- Jurisdiction anchor: ").append(report.jurisdictionAnchor).append("\n");
                    sb.append("- Processing status: ").append(resolvedProcessingStatus).append("\n");
                    sb.append("- Guardian approval: ").append(guardianApproved ? "Approved" : "Denied").append("\n");
                    sb.append("- Guardian-approved certified findings available: ").append(normalizedCertifiedFindingCount(report)).append("\n");
                    sb.append("- Native pages processed: ")
                            .append(nativeEvidence.optInt("sourcePageCount", nativeEvidence.optInt("pageCount", 0)))
                            .append("\n");
                    if (!guardianApproved) {
                        String guardianReason = guardianDecisionReason(report);
                        if (!guardianReason.isEmpty()) {
                            sb.append("- Guardian reason: ").append(guardianReason).append("\n");
                        }
                    }
                    if (report.evidenceBundleHash != null && !report.evidenceBundleHash.trim().isEmpty()) {
                        sb.append("- Evidence bundle hash: ").append(report.evidenceBundleHash).append("\n");
                    }
                    if (report.deterministicRunId != null && !report.deterministicRunId.trim().isEmpty()) {
                        sb.append("- Deterministic run ID: ").append(report.deterministicRunId).append("\n");
                    }
                    sb.append("\n11J. Nine-brain consensus\n");
                    appendBrainConsensusSection(sb, report.brainAnalysis);
                    sb.append("\n11K. Triple verification\n");
                    appendTripleVerificationSection(sb, report);
                    sb.append("- Certification note: any review should be cross-checked against the sealed forensic audit report and findings JSON in the vault.\n");
                })
        ));

        return HumanFindingsReportBuilder.render(input);

    }

    private void appendHumanForensicConclusionSection(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            List<String> fallbackPages
    ) {
        JSONObject forensicConclusion = preferredForensicConclusion(report);
        String whatHappened = clipReportText(extractConclusionWhatHappened(forensicConclusion), 240);
        String primaryActor = extractPrimaryImplicatedActor(forensicConclusion);
        List<String> why = extractConclusionWhy(forensicConclusion, 4);
        List<String> otherLinkedActors = extractOtherLinkedActors(forensicConclusion, primaryActor);
        List<String> proven = extractConclusionProven(forensicConclusion, 5);
        List<String> certifiedConduct = extractConclusionList(forensicConclusion, "certifiedForensicConduct", 4);
        List<String> allegedExposure = extractConclusionList(forensicConclusion, "stronglyAllegedExposure", 6);
        List<String> frameworkMapping = extractConclusionList(forensicConclusion, "frameworkMapping", 6);
        List<String> pages = extractConclusionPages(forensicConclusion, fallbackPages, 6);
        String boundary = clipReportText(forensicConclusion.optString("publicationBoundary", ""), 320);

        if (!whatHappened.isEmpty()) {
            sb.append("* What happened: ").append(whatHappened).append("\n");
        } else {
            sb.append("* What happened: This pass does not support a shorter line without losing important detail or overclaiming.\n");
        }
        if (!primaryActor.isEmpty()) {
            sb.append("* Primary implicated actor: ").append(primaryActor).append("\n");
        } else {
            sb.append("* Primary implicated actor: not cleanly resolved in this pass.\n");
        }
        if (!why.isEmpty()) {
            sb.append("* Why this conclusion is leading:\n");
            for (String item : why) {
                sb.append("  - ").append(item).append("\n");
            }
        }
        if (!otherLinkedActors.isEmpty()) {
            sb.append("* Other linked actors: ").append(joinReadableList(otherLinkedActors)).append("\n");
        }
        if (!proven.isEmpty()) {
            sb.append("* What the sealed record already proves:\n");
            for (String item : proven) {
                sb.append("  - ").append(item).append("\n");
            }
        }
        if (!certifiedConduct.isEmpty()) {
            sb.append("* Certified forensic conduct:\n");
            for (String item : certifiedConduct) {
                sb.append("  - ").append(item).append("\n");
            }
        }
        if (!allegedExposure.isEmpty()) {
            sb.append("* Strongly alleged legal exposure:\n");
            for (String item : allegedExposure) {
                sb.append("  - ").append(item).append("\n");
            }
        }
        if (!frameworkMapping.isEmpty()) {
            sb.append("* Framework-level mapping:\n");
            for (String item : frameworkMapping) {
                sb.append("  - ").append(item).append("\n");
            }
        }
        if (!boundary.isEmpty()) {
            sb.append("* Boundary for this pass: ").append(boundary).append("\n");
        }
        if (!pages.isEmpty()) {
            sb.append("* Pages to read first: ").append(joinReadableList(pages)).append("\n");
        }
    }

    private String buildHumanForensicConclusionSectionText(AnalysisEngine.ForensicReport report) {
        StringBuilder sb = new StringBuilder();
        appendHumanForensicConclusionSection(sb, report, assembledReadFirstPages(report));
        return sb.toString().trim();
    }

    private String ensureHumanReportForensicConclusion(AnalysisEngine.ForensicReport report, String rendered) {
        String safeRendered = rendered == null ? "" : rendered.trim();
        if (safeRendered.isEmpty()) {
            return safeRendered;
        }
        if (safeRendered.contains("\n1B. FORENSIC CONCLUSION\n")
                || safeRendered.startsWith("1B. FORENSIC CONCLUSION\n")
                || safeRendered.contains("\nFORENSIC CONCLUSION\n")) {
            return safeRendered;
        }
        String section = buildHumanForensicConclusionSectionText(report);
        if (section.isEmpty()) {
            return safeRendered;
        }
        int insertionPoint = safeRendered.indexOf("\n2. ");
        if (insertionPoint >= 0) {
            return safeRendered.substring(0, insertionPoint)
                    + "\n1B. FORENSIC CONCLUSION\n"
                    + section
                    + "\n"
                    + safeRendered.substring(insertionPoint);
        }
        return safeRendered + "\n\nFORENSIC CONCLUSION\n" + section;
    }

    private String ensureReadableBriefForensicConclusion(AnalysisEngine.ForensicReport report, String rendered) {
        String safeRendered = rendered == null ? "" : rendered.trim();
        if (safeRendered.isEmpty() || safeRendered.contains("\nFORENSIC CONCLUSION\n")) {
            return safeRendered;
        }
        JSONObject forensicConclusion = preferredForensicConclusion(report);
        String whatHappened = forensicConclusion.optJSONArray("whatHappened") != null
                ? clipReportText(forensicConclusion.optJSONArray("whatHappened").optString(0, ""), 220)
                : "";
        String primaryActor = extractPrimaryImplicatedActor(forensicConclusion);
        String boundary = clipReportText(forensicConclusion.optString("publicationBoundary", ""), 240);
        List<String> pages = extractConclusionPages(forensicConclusion, assembledReadFirstPages(report), 6);
        if (whatHappened.isEmpty() && primaryActor.isEmpty() && boundary.isEmpty()) {
            return safeRendered;
        }
        StringBuilder section = new StringBuilder();
        section.append("FORENSIC CONCLUSION\n");
        if (!whatHappened.isEmpty()) {
            section.append("* What happened: ").append(whatHappened).append("\n");
        }
        if (!primaryActor.isEmpty()) {
            section.append("* Primary implicated actor: ").append(primaryActor).append("\n");
        }
        if (!boundary.isEmpty()) {
            section.append("* What is not yet published as final guilt: ").append(boundary).append("\n");
        }
        if (!pages.isEmpty()) {
            section.append("* Pages to read first: ").append(joinReadableList(pages)).append("\n");
        }
        String marker = "\nWhat Happened\n";
        int insertionPoint = safeRendered.indexOf(marker);
        if (insertionPoint >= 0) {
            return safeRendered.substring(0, insertionPoint)
                    + "\n"
                    + section
                    + safeRendered.substring(insertionPoint);
        }
        return safeRendered + "\n\n" + section.toString().trim();
    }

    private void appendHumanNarrativeExecutiveSummarySection(
            StringBuilder sb,
            String fileName,
            AnalysisEngine.ForensicReport report,
            JSONObject forensicSynthesis,
            List<JSONObject> certifiedNarratives,
            List<JSONObject> leadFindings,
            List<JSONObject> primaryTimeline,
            boolean guardianApproved,
            boolean indeterminateDueToConcealment
    ) {
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        sb.append("This brief explains, in plain language, what the sealed record for ")
                .append(fileName)
                .append(" currently shows, who it links most strongly, and why the evidence matters.\n\n");
        sb.append("Jurisdiction: ")
                .append(safeValue(report.jurisdictionName))
                .append(" (")
                .append(safeValue(report.jurisdiction))
                .append("). ");
        sb.append("Case ID: ").append(safeValue(report.caseId)).append(".\n");
        String crossBorderScope = buildCrossBorderExposureLine(report);
        if (!crossBorderScope.isEmpty()) {
            sb.append(crossBorderScope).append("\n");
        }
        JSONObject forensicConclusion = preferredForensicConclusion(report);
        JSONObject truthFrame = TruthInCodeEngine.buildTruthFrame(report, assembled);
        AnchorBoundNarrativeBuilder.Narrative anchorNarrative =
                AnchorBoundNarrativeBuilder.build(forensicConclusion, truthFrame);
        List<JSONObject> narrativeEvidence = combineHumanEvidenceLists(certifiedNarratives, leadFindings, primaryTimeline);
        String patternLine = firstNonEmpty(anchorNarrative.summary, buildHumanRepeatPatternLine(narrativeEvidence));
        if (!patternLine.isEmpty()) {
            sb.append("Main pattern in the record: ").append(patternLine).append("\n");
        }
        if (!anchorNarrative.keyFindings.isEmpty()) {
            sb.append("The clearest anchored points in this pass are:\n");
            for (String line : anchorNarrative.keyFindings) {
                sb.append("- ").append(line).append("\n");
            }
        }
        String sealingLine = buildHumanSealingImportanceLine(narrativeEvidence);
        if (!sealingLine.isEmpty()) {
            sb.append("Why the sealed record matters here: ").append(sealingLine).append("\n");
        }
        if (!anchorNarrative.timelineHighlights.isEmpty()) {
            sb.append("How the record unfolds in time:\n");
            for (String line : anchorNarrative.timelineHighlights) {
                sb.append("- ").append(line).append("\n");
            }
        } else {
            String patternOriginLine = buildHumanPatternOriginLine(narrativeEvidence);
            if (!patternOriginLine.isEmpty()) {
                sb.append("How that pattern appears in the record: ").append(patternOriginLine).append("\n");
            }
        }
        String completedHarmLine = firstNonEmpty(
                anchorNarrative.implicationSummary,
                buildHumanCompletedHarmLine(forensicSynthesis, narrativeEvidence)
        );
        if (!completedHarmLine.isEmpty()) {
            sb.append("Completed harm and current risk already visible in the record: ").append(completedHarmLine).append("\n");
        }
        sb.append("\n");

        LinkedHashSet<String> victimAliases = collectHumanVictimAliases(forensicSynthesis);
        JSONObject wrongfulActorProfile = forensicSynthesis != null
                ? forensicSynthesis.optJSONObject("wrongfulActorProfile")
                : null;
        List<JSONObject> principalActors = collectHumanPrincipalActorProfiles(forensicSynthesis, 3);
        String likelyActor = wrongfulActorProfile != null ? wrongfulActorProfile.optString("actor", "").trim() : "";
        String likelyAssessment = wrongfulActorProfile != null ? wrongfulActorProfile.optString("factualFaultAssessment", "").trim() : "";
        String conclusionLine = clipReportText(forensicConclusion.optString("strongestConclusion", ""), 220);
        if (!conclusionLine.isEmpty()) {
            sb.append("Conclusion: ").append(conclusionLine).append("\n");
        } else if (!principalActors.isEmpty()) {
            sb.append("Conclusion: ").append(buildHumanPrincipalActorConclusion(principalActors)).append("\n");
            for (int i = 0; i < principalActors.size() && i < 2; i++) {
                String actorLine = buildHumanPrincipalActorLine(principalActors.get(i), i == 0);
                if (!actorLine.isEmpty()) {
                    sb.append(actorLine).append("\n");
                }
            }
        } else if (!likelyActor.isEmpty()
                && !isDiscardedHumanActorName(likelyActor)
                && !isVictimScopedHumanActor(likelyActor, victimAliases)) {
            int verifiedContradictionCount = wrongfulActorProfile != null
                    ? wrongfulActorProfile.optInt("verifiedContradictionCount", 0)
                    : 0;
            if (verifiedContradictionCount > 0) {
                sb.append("Conclusion: the strongest current actor-linked case centers on ")
                        .append(likelyActor)
                        .append(".\n");
            } else {
                sb.append("Conclusion: the current candidate contradiction profile centers on ")
                        .append(likelyActor)
                        .append(", but that lead remains candidate-level until a verified contradiction matures.\n");
            }
            if (!likelyAssessment.isEmpty()) {
                sb.append("Why that actor is leading: ")
                        .append(clipReportText(likelyAssessment, 180))
                        .append(".\n");
            }
        } else {
            sb.append("Conclusion: the sealed record contains actor-linked tension, but this pass does not support one clean single-actor summary without overclaiming.\n");
        }
        sb.append("\n");

        if (guardianApproved) {
            sb.append("Current confidence: guardian review approved publishable findings in this pass.\n");
        } else {
            sb.append("Current confidence: guardian review did not certify a promoted finding in this pass, so the summary below stays anchored but candidate-led.\n");
        }
        if (indeterminateDueToConcealment) {
            sb.append("Caution: concealment, OCR gaps, or incomplete disclosure still affect parts of the record.\n");
        }
        sb.append("Contradiction posture: ").append(assembled.contradictionPosture).append("\n");
        sb.append("\nWhat is currently guardian-approved certified or strongest in the record:\n");

        int emitted = 0;
        if (certifiedNarratives != null) {
            for (JSONObject item : certifiedNarratives) {
                if (item == null || emitted >= 4) {
                    continue;
                }
                String line = buildCertifiedFindingNarrative(item);
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                sb.append("- ").append(line).append("\n");
                emitted++;
            }
        }
        if (emitted == 0) {
            sb.append("- No additional guardian-approved certified finding summary was available beyond the published certified set in this pass.\n");
        }
        if (emitted == 0) {
            sb.append("- Candidate contradiction leads remain candidate-level until a verified contradiction matures, even where certified findings already exist.\n");
        }
    }

    private void appendHumanCaseOverviewSection(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            JSONObject forensicSynthesis,
            List<JSONObject> certifiedNarratives,
            List<JSONObject> leadFindings,
            boolean guardianApproved,
            String processingStatus
    ) {
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        JSONObject forensicConclusion = preferredForensicConclusion(report);
        List<JSONObject> principalActors = collectHumanPrincipalActorProfiles(forensicSynthesis, 3);
        List<JSONObject> overviewEvidence = combineHumanEvidenceLists(certifiedNarratives, leadFindings);
        String patternLine = buildHumanRepeatPatternLine(overviewEvidence);
        String completedHarmLine = buildHumanCompletedHarmLine(forensicSynthesis, overviewEvidence);
        sb.append("Read this first: this is the short human-facing findings brief. The sealed audit report remains the controlling validation ledger.\n");
        sb.append("This brief is grounded in cryptographically sealed source documents. The seal and the fixed record are the proof; the software only organizes that record for review.\n");
        sb.append("Case conclusion: ");
        String strongestConclusion = clipReportText(forensicConclusion.optString("strongestConclusion", ""), 220);
        if (!strongestConclusion.isEmpty()) {
            sb.append(strongestConclusion);
        } else {
            sb.append("The record currently supports a structured case picture, but this pass does not support one fully dominant actor summary without overclaiming.");
        }
        sb.append("\n");
        if (shouldSuppressCoreRoleNarration(assembled)) {
            sb.append("Primary harmed party: not published in this section because the current pass remains contradiction-led and does not separately anchor that role strongly enough.\n");
        } else if (!assembled.primaryHarmedParty.isEmpty()) {
            sb.append("Primary harmed party: ").append(assembled.primaryHarmedParty).append("\n");
        }
        if (!completedHarmLine.isEmpty()) {
            sb.append("Completed harm and current risk already visible in the record: ").append(completedHarmLine).append("\n");
        }
        if (!assembled.otherAffectedParties.isEmpty()) {
            sb.append("Other affected parties already visible in the sealed record: ")
                    .append(joinHumanNames(assembled.otherAffectedParties))
                    .append("\n");
        }
        if (!patternLine.isEmpty()) {
            sb.append("Main pattern in the record: ").append(patternLine).append("\n");
        }
        String patternOriginLine = buildHumanPatternOriginLine(overviewEvidence);
        if (!patternOriginLine.isEmpty()) {
            sb.append("How that pattern was found: ").append(patternOriginLine).append("\n");
        }
        if (!principalActors.isEmpty()) {
            List<String> actorNames = new ArrayList<>();
            for (JSONObject item : principalActors) {
                String actor = item.optString("actor", "").trim();
                if (!actor.isEmpty() && !actorNames.contains(actor)) {
                    actorNames.add(actor);
                }
            }
            if (!actorNames.isEmpty()) {
                sb.append("Other materially implicated parties: ").append(joinHumanNames(actorNames)).append("\n");
            }
        }
        sb.append("Current record status: ")
                .append(processingStatus == null || processingStatus.trim().isEmpty() ? "COMPLETED" : processingStatus.trim())
                .append("\n");
        sb.append("Current finding status: ")
                .append(guardianApproved ? "at least one finding was confirmed on the present review pass." : "no promoted finding was confirmed on the present review pass.")
                .append("\n");
        sb.append("Guardian-approved certified findings: ")
                .append(assembled.guardianApprovedCertifiedFindingCount)
                .append("\n");
        if (!assembled.readFirstPages.isEmpty()) {
            sb.append("Top evidence pages: ").append(joinHumanNames(assembled.readFirstPages)).append("\n");
        }
    }

    private void appendHumanKeyFindingsSnapshotSection(
            StringBuilder sb,
            List<JSONObject> certifiedNarratives,
            List<JSONObject> verifiedOrCoreFindings,
            List<JSONObject> openIssues
    ) {
        sb.append("Status | Main actor | Why it matters | Evidence\n");
        sb.append("------------------------------------------------\n");
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        int emitted = 0;
        emitted += appendHumanSnapshotRows(sb, certifiedNarratives, "CERTIFIED", 3, seen);
        emitted += appendHumanSnapshotRows(sb, verifiedOrCoreFindings, "VERIFIED", Math.max(0, 6 - emitted), seen);
        emitted += appendHumanSnapshotRows(sb, openIssues, "NEEDS REVIEW", Math.max(0, 8 - emitted), seen);
        if (emitted == 0) {
            sb.append("No clean findings snapshot could be stated from the current primary-evidence pass.\n");
        }
    }

    private List<JSONObject> collectHumanPrincipalActorProfiles(JSONObject forensicSynthesis, int limit) {
        List<JSONObject> out = new ArrayList<>();
        if (forensicSynthesis == null || limit <= 0) {
            return out;
        }
        JSONArray actorScores = forensicSynthesis.optJSONArray("actorDishonestyScores");
        if (actorScores == null || actorScores.length() == 0) {
            return out;
        }
        LinkedHashSet<String> victimAliases = collectHumanVictimAliases(forensicSynthesis);
        boolean hasNonInstitutional = false;
        for (int i = 0; i < actorScores.length(); i++) {
            JSONObject item = actorScores.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String actor = item.optString("actor", "").trim();
            if (!actor.isEmpty()
                    && !isDiscardedHumanActorName(actor)
                    && !isInstitutionalHumanActor(actor)
                    && !isVictimScopedHumanActor(actor, victimAliases)) {
                hasNonInstitutional = true;
                break;
            }
        }
        double leadScore = -1.0d;
        for (int i = 0; i < actorScores.length() && out.size() < limit; i++) {
            JSONObject item = actorScores.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String actor = item.optString("actor", "").trim();
            if (actor.isEmpty() || isDiscardedHumanActorName(actor)) {
                continue;
            }
            if (hasNonInstitutional && isVictimScopedHumanActor(actor, victimAliases)) {
                continue;
            }
            if (hasNonInstitutional && isInstitutionalHumanActor(actor)) {
                continue;
            }
            double score = item.optDouble("dishonestyScore", 0.0d);
            if (score <= 0.0d) {
                continue;
            }
            int contradictionPriority = item.optInt("contradictionPriority", 0);
            int verifiedContradictions = item.optInt("verifiedContradictionCount", 0);
            int candidateContradictions = item.optInt("candidateContradictionCount", 0);
            int criticalContradictions = item.optInt("criticalContradictionCount", 0);
            int highConfidenceContradictions = item.optInt("highConfidenceContradictionCount", 0);
            if (contradictionPriority <= 0
                    && verifiedContradictions <= 0
                    && candidateContradictions <= 0
                    && criticalContradictions <= 0
                    && highConfidenceContradictions <= 0) {
                continue;
            }
            if (leadScore < 0.0d) {
                leadScore = score;
            } else if (score < 15.0d && (leadScore - score) > 20.0d) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    private String buildHumanPrincipalActorConclusion(List<JSONObject> principalActors) {
        if (principalActors == null || principalActors.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        boolean candidateOnly = false;
        for (JSONObject item : principalActors) {
            if (item == null) {
                continue;
            }
            String actor = item.optString("actor", "").trim();
            if (!actor.isEmpty() && !names.contains(actor)) {
                names.add(actor);
            }
            if (item.optInt("verifiedContradictionCount", 0) <= 0) {
                candidateOnly = true;
            }
        }
        if (names.isEmpty()) {
            return "";
        }
        if (names.size() == 1) {
            if (candidateOnly) {
                return "The record currently points mainly to "
                        + names.get(0)
                        + ", but that remains a working conclusion until contradiction review matures further.";
            }
            return "The record currently points most strongly to " + names.get(0) + " as the main adverse actor.";
        }
        String lead = names.get(0);
        List<String> others = names.subList(1, names.size());
        if (candidateOnly) {
            return "The record currently points mainly to "
                    + lead
                    + ", with " + joinHumanNames(others)
                    + " also appearing in the same provisional actor picture while contradiction review is still maturing.";
        }
        return "The record currently points most strongly to " + lead + ", with "
                + joinHumanNames(others) + " also materially implicated in the same case.";
    }

    private String buildHumanPrincipalActorLine(JSONObject actorProfile, boolean lead) {
        if (actorProfile == null) {
            return "";
        }
        String actor = actorProfile.optString("actor", "").trim();
        if (actor.isEmpty() || isDiscardedHumanActorName(actor)) {
            return "";
        }
        actor = canonicalizeHumanVictimDisplay(actor);
        StringBuilder line = new StringBuilder();
        boolean candidateOnly = actorProfile.optInt("verifiedContradictionCount", 0) <= 0;
        line.append(actor)
                .append(lead
                        ? (candidateOnly
                        ? " currently leads the candidate contradiction profile in the sealed record"
                        : " is the main adverse actor currently carried by the sealed record")
                        : (candidateOnly
                        ? " is also part of the same candidate contradiction profile"
                        : " is also materially implicated by the same record"));
        String flagPhrase = humanizeActorFlags(actorProfile.optJSONArray("flags"));
        if (!flagPhrase.isEmpty()) {
            line.append(", particularly around ").append(flagPhrase);
        }
        line.append(".");
        String note = firstJSONArrayText(actorProfile.optJSONArray("evidenceNotes"));
        if (!note.isEmpty()) {
            line.append(" Key anchored point: ")
                    .append(clipReportText(cleanHumanEvidenceNote(note), 180))
                    .append(".");
        }
        String pageRef = humanPageReference(actorProfile.optJSONArray("anchorPages"), 4);
        if (!pageRef.isEmpty()) {
            line.append(" ").append(pageRef);
        }
        return line.toString();
    }

    private String buildCrossBorderExposureLine(AnalysisEngine.ForensicReport report) {
        if (report == null) {
            return "";
        }
        boolean southAfrica = "MULTI".equalsIgnoreCase(report.jurisdiction)
                || reportHasJurisdictionSignal(report, "zaf", "south africa", "south african", "saps", "hawks", "precca");
        boolean uae = "MULTI".equalsIgnoreCase(report.jurisdiction)
                || reportHasJurisdictionSignal(report, "uae", "united arab emirates", "rakez", "emirates", "dubai", "abu dhabi");
        if (southAfrica && uae) {
            return "the same sealed record carries conduct and legal exposure in both South Africa and the UAE, so it should not be read as a single-country dispute.";
        }
        if (southAfrica) {
            return "the sealed record carries South African conduct and legal exposure.";
        }
        if (uae) {
            return "the sealed record carries UAE conduct and legal exposure.";
        }
        return "";
    }

    private boolean reportHasJurisdictionSignal(AnalysisEngine.ForensicReport report, String... needles) {
        if (report == null) {
            return false;
        }
        if (containsAny(firstNonEmpty(report.jurisdictionName, report.jurisdiction, report.summary, report.jurisdictionAnchor), needles)) {
            return true;
        }
        if (report.topLiabilities != null) {
            for (String item : report.topLiabilities) {
                if (containsAny(item, needles)) {
                    return true;
                }
            }
        }
        if (report.legalReferences != null) {
            for (String item : report.legalReferences) {
                if (containsAny(item, needles)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String resolvePrimaryHarmedParty(JSONObject forensicSynthesis) {
        if (forensicSynthesis == null) {
            return "";
        }
        JSONArray victims = forensicSynthesis.optJSONArray("victimActors");
        if (victims == null || victims.length() == 0) {
            return "";
        }
        return victims.optString(0, "").trim();
    }

    private String resolveHumanPrimaryHarmedParty(
            JSONObject forensicSynthesis,
            List<JSONObject> leadFindings
    ) {
        LinkedHashSet<String> victimAliases = collectHumanVictimAliases(forensicSynthesis);
        String corpus = lowerUs(buildHumanNarrativeCorpus(leadFindings));
        if ((victimAliases.contains("desmond smith") || victimAliases.contains("des"))
                && containsAny(corpus,
                "desmond smith",
                "des smith",
                "vulnerable client",
                "site lost",
                "forced off the site",
                "forced off site",
                "goodwill withheld",
                "goodwill stolen",
                "goodwill was stolen",
                "mentally broken")) {
            return "Desmond Smith";
        }
        String directVictim = resolvePrimaryHarmedParty(forensicSynthesis);
        if (!directVictim.isEmpty() && !looksLikeAbstractHarmedParty(directVictim)) {
            return canonicalizeHumanVictimDisplay(directVictim);
        }
        if (victimAliases.contains("desmond smith") || victimAliases.contains("des")) {
            return "Desmond Smith";
        }
        if (victimAliases.contains("gary highcock") || victimAliases.contains("your dad")) {
            return "Gary Highcock";
        }
        if (victimAliases.contains("wayne nel") || victimAliases.contains("wayne")) {
            return "Wayne Nel";
        }
        if (victimAliases.contains("marius nortje") || victimAliases.contains("marius")) {
            return "Marius Nortje";
        }
        if (victimAliases.contains("liam highcock") || victimAliases.contains("liam")) {
            return "Liam Highcock";
        }
        if (containsAny(corpus,
                "desmond smith",
                "des smith",
                "des's",
                "des is linked",
                "site lost",
                "forced off the site",
                "goodwill withheld",
                "goodwill stolen")) {
            return "Desmond Smith";
        }
        if (containsAny(corpus,
                "goodwill was stolen",
                "goodwill stolen",
                "forced off site",
                "vacate",
                "evict",
                "termination notice",
                "brand fee",
                "no-countersignature",
                "not countersigned",
                "never countersigned",
                "unsigned mou")) {
            return "operator(s) facing loss of site, lease, or goodwill";
        }
        return "";
    }

    private boolean looksLikeAbstractHarmedParty(String value) {
        String lower = lowerUs(trimToEmpty(value));
        return lower.isEmpty()
                || isDiscardedHumanActorName(value)
                || "all fuels".equals(lower)
                || "franchisor retail program".equals(lower)
                || "retail licence".equals(lower)
                || "operator".equals(lower)
                || "franchisee".equals(lower)
                || "franchisor".equals(lower)
                || "individuals".equals(lower)
                || "matters".equals(lower)
                || "common purpose".equals(lower)
                || "account".equals(lower)
                || "screenshot".equals(lower);
    }

    private LinkedHashSet<String> collectHumanVictimAliases(JSONObject forensicSynthesis) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (forensicSynthesis == null) {
            return out;
        }
        JSONArray victims = forensicSynthesis.optJSONArray("victimActors");
        if (victims == null) {
            return out;
        }
        for (int i = 0; i < victims.length(); i++) {
            String value = victims.optString(i, "").trim();
            if (value.isEmpty()) {
                continue;
            }
            String lower = lowerUs(value);
            if (containsAny(lower,
                    "section",
                    "that",
                    "which",
                    "forced",
                    "his",
                    "clause",
                    "civil disputes",
                    "differences matter",
                    "yours",
                    "negative evidence",
                    "asset forfeiture unit",
                    "natal south",
                    "south africa",
                    "liam highcock founder",
                    "nealy lombaard",
                    "all fuels",
                    "franchisor retail program",
                    "retail licence",
                    "individuals",
                    "matters",
                    "common purpose",
                    "account",
                    "screenshot")
                    || lower.endsWith(" yes")
                    || lower.endsWith(" no")) {
                continue;
            }
            addHumanVictimAlias(out, lower);
        }
        return out;
    }

    private LinkedHashSet<String> collectHumanVictimAliases(JSONArray namedParties) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (namedParties == null) {
            return out;
        }
        for (int i = 0; i < namedParties.length(); i++) {
            JSONObject party = namedParties.optJSONObject(i);
            if (party == null) {
                continue;
            }
            String role = trimToEmpty(firstNonEmpty(party.optString("role", null), party.optString("actorClass", null)));
            if (!"VICTIM".equalsIgnoreCase(role)) {
                continue;
            }
            String name = party.optString("name", "").trim();
            if (name.isEmpty()) {
                continue;
            }
            String lower = lowerUs(name);
            if (containsAny(lower,
                    "section",
                    "that",
                    "which",
                    "forced",
                    "his",
                    "clause",
                    "civil disputes",
                    "differences matter",
                    "yours",
                    "negative evidence",
                    "asset forfeiture unit",
                    "natal south",
                    "south africa",
                    "liam highcock founder",
                    "nealy lombaard",
                    "all fuels",
                    "franchisor retail program",
                    "retail licence")
                    || lower.endsWith(" yes")
                    || lower.endsWith(" no")) {
                continue;
            }
            addHumanVictimAlias(out, lower);
        }
        return out;
    }

    private List<String> collectHumanVictimDisplayNames(JSONObject forensicSynthesis, int limit) {
        List<String> out = new ArrayList<>();
        if (limit <= 0) {
            return out;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        JSONArray victims = forensicSynthesis != null ? forensicSynthesis.optJSONArray("victimActors") : null;
        if (victims != null) {
            for (int i = 0; i < victims.length() && seen.size() < limit; i++) {
                String value = canonicalizeHumanVictimDisplay(victims.optString(i, "").trim());
                if (!isUsableHumanVictimDisplayCandidate(value)) {
                    continue;
                }
                seen.add(value);
            }
        }
        LinkedHashSet<String> aliases = collectHumanVictimAliases(forensicSynthesis);
        if (seen.size() < limit && (aliases.contains("desmond smith") || aliases.contains("des"))) {
            seen.add("Desmond Smith");
        }
        if (seen.size() < limit && (aliases.contains("wayne nel") || aliases.contains("wayne"))) {
            seen.add("Wayne Nel");
        }
        if (seen.size() < limit && (aliases.contains("gary highcock") || aliases.contains("your dad"))) {
            seen.add("Gary Highcock");
        }
        if (seen.size() < limit && (aliases.contains("marius nortje") || aliases.contains("marius"))) {
            seen.add("Marius Nortje");
        }
        if (seen.size() < limit && (aliases.contains("liam highcock") || aliases.contains("liam"))) {
            seen.add("Liam Highcock");
        }
        for (String value : seen) {
            if (out.size() >= limit) {
                break;
            }
            out.add(value);
        }
        return out;
    }

    private boolean isUsableHumanVictimDisplayCandidate(String value) {
        if (value == null) {
            return false;
        }
        String lower = lowerUs(trimToEmpty(value));
        if (lower.isEmpty()) {
            return false;
        }
        return !isDiscardedHumanActorName(value)
                && !looksLikeAbstractHarmedParty(value)
                && !"his".equals(lower)
                && !"forced".equals(lower)
                && !"clause".equals(lower)
                && !"differences matter".equals(lower)
                && !"civil disputes".equals(lower)
                && !lower.endsWith(" yes")
                && !lower.endsWith(" no");
    }

    private void addHumanVictimAlias(Set<String> aliases, String alias) {
        if (aliases == null || alias == null) {
            return;
        }
        String lower = lowerUs(trimToEmpty(alias));
        if (lower.isEmpty()) {
            return;
        }
        aliases.add(lower);
        if ("des".equals(lower)
                || "desmond smith".equals(lower)
                || "des smith".equals(lower)
                || lower.startsWith("desmond owen smith")) {
            aliases.add("des");
            aliases.add("desmond smith");
        }
        if ("wayne".equals(lower) || "wayne nel".equals(lower) || "wayne nell".equals(lower)) {
            aliases.add("wayne");
            aliases.add("wayne nel");
        }
        if ("your dad".equals(lower) || "gary highcock".equals(lower)) {
            aliases.add("your dad");
            aliases.add("gary highcock");
        }
        if ("marius".equals(lower) || "marius nor".equals(lower) || "marius nortje".equals(lower)) {
            aliases.add("marius");
            aliases.add("marius nortje");
        }
        if ("liam".equals(lower) || "liam highcock".equals(lower)) {
            aliases.add("liam");
            aliases.add("liam highcock");
        }
    }

    private boolean isVictimScopedHumanActor(String actor, Set<String> victimAliases) {
        if (actor == null || victimAliases == null || victimAliases.isEmpty()) {
            return false;
        }
        String lower = lowerUs(trimToEmpty(actor));
        if (lower.isEmpty()) {
            return false;
        }
        if (victimAliases.contains(lower)) {
            return true;
        }
        return ("des".equals(lower) && victimAliases.contains("desmond smith"))
                || ("desmond smith".equals(lower) && victimAliases.contains("des"))
                || ("wayne".equals(lower) && victimAliases.contains("wayne nel"))
                || ("wayne nel".equals(lower) && victimAliases.contains("wayne"))
                || ("your dad".equals(lower) && victimAliases.contains("gary highcock"))
                || ("gary highcock".equals(lower) && victimAliases.contains("your dad"))
                || ("marius".equals(lower) && victimAliases.contains("marius nortje"))
                || ("marius nortje".equals(lower) && victimAliases.contains("marius"))
                || ("liam".equals(lower) && victimAliases.contains("liam highcock"))
                || ("liam highcock".equals(lower) && victimAliases.contains("liam"));
    }

    private String canonicalizeHumanVictimDisplay(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if ("des".equals(lower)
                || "desmond smith".equals(lower)
                || "des smith".equals(lower)
                || lower.startsWith("desmond owen smith")) {
            return "Desmond Smith";
        }
        if ("wayne".equals(lower) || "wayne nel".equals(lower) || "wayne nell".equals(lower)) {
            return "Wayne Nel";
        }
        if ("your dad".equals(lower) || "gary highcock".equals(lower)) {
            return "Gary Highcock";
        }
        if ("marius".equals(lower) || "marius nor".equals(lower) || "marius nortje".equals(lower)) {
            return "Marius Nortje";
        }
        if ("liam".equals(lower) || "liam highcock".equals(lower)) {
            return "Liam Highcock";
        }
        return value == null ? "" : value.trim();
    }

    private String buildHumanRepeatPatternLine(List<JSONObject> evidence) {
        String corpus = lowerUs(buildHumanNarrativeCorpus(evidence));
        boolean noCounterSignature = containsAny(corpus,
                "no-countersignature",
                "not countersigned",
                "never countersigned",
                "unsigned",
                "unsigned mou",
                "unsigned execution");
        boolean extraction = containsAny(corpus,
                "goodwill",
                "brand fee",
                "rent paid",
                "upgrade",
                "payment of fees",
                "extension fee");
        boolean removal = containsAny(corpus,
                "forced off site",
                "vacate",
                "evict",
                "termination notice",
                "threatened with removal",
                "notice to vacate");
        if (noCounterSignature && extraction && removal) {
            return "Across the sealed record, the same pattern keeps appearing: papers are signed, no countersigned copy comes back, money continues to move, and pressure to vacate follows.";
        }
        if (noCounterSignature && removal) {
            return "The record points to a repeated unsigned-document pattern followed by pressure to vacate or lose the site.";
        }
        if (extraction && removal) {
            return "The record points to value being taken from operators and then used as leverage to push them off site.";
        }
        return "";
    }

    private boolean hasInvestigatorSuppliedFacts(AnalysisEngine.ForensicReport report) {
        return report != null
                && report.investigatorSuppliedFacts != null
                && report.investigatorSuppliedFacts.length() > 0;
    }

    private void appendHumanInvestigatorContextSection(StringBuilder sb, AnalysisEngine.ForensicReport report) {
        if (!hasInvestigatorSuppliedFacts(report)) {
            sb.append("No investigator-supplied context was added during intake.\n");
            return;
        }
        JSONObject context = report.investigatorContext != null ? report.investigatorContext : new JSONObject();
        String summary = context.optString("summary", "").trim();
        if (!summary.isEmpty()) {
            sb.append(summary).append("\n");
        }
        String disclosure = context.optString("disclosure", "").trim();
        if (!disclosure.isEmpty()) {
            sb.append("Disclosure: ").append(disclosure).append("\n");
        }
        sb.append("The following statements were supplied manually and are shown as context, not as certified source-proof:\n");
        JSONArray facts = report.investigatorSuppliedFacts;
        for (int i = 0; i < facts.length(); i++) {
            JSONObject item = facts.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String statement = item.optString("statement", "").trim();
            if (statement.isEmpty()) {
                continue;
            }
            sb.append("- ").append(statement);
            String anchorStatus = item.optString("anchorStatus", "").trim();
            if (!anchorStatus.isEmpty()) {
                sb.append(" [").append(anchorStatus.replace('_', ' ')).append("]");
            }
            sb.append("\n");
        }
    }

    private String buildHumanCompletedHarmLine(JSONObject forensicSynthesis, List<JSONObject> evidence) {
        LinkedHashSet<String> victimAliases = collectHumanVictimAliases(forensicSynthesis);
        String corpus = lowerUs(buildHumanNarrativeCorpus(evidence));
        boolean desCompleted = (victimAliases.contains("des") || victimAliases.contains("desmond smith"))
                && containsAny(corpus,
                "site lost",
                "forced off the site",
                "forced off site",
                "goodwill withheld",
                "goodwill stolen",
                "goodwill was stolen",
                "vulnerable client",
                "mentally broken");
        boolean garyRisk = (victimAliases.contains("gary highcock") || victimAliases.contains("your dad"))
                && containsAny(corpus,
                "gary highcock",
                "your dad",
                "current risk",
                "notice to vacate",
                "vacate",
                "port edward",
                "unsigned mou");
        if (desCompleted && garyRisk) {
            return "The record already describes completed harm to Desmond Smith, whose site and goodwill are said to have been taken. The same method is then described as a live risk for Gary Highcock.";
        }
        if (desCompleted) {
            return "The record already describes completed harm to Desmond Smith, whose site and goodwill are said to have been taken.";
        }
        return "";
    }

    private String buildHumanSealingImportanceLine(List<JSONObject> evidence) {
        String corpus = lowerUs(buildHumanNarrativeCorpus(evidence));
        if (containsAny(corpus,
                "no-countersignature",
                "not countersigned",
                "never countersigned",
                "unsigned",
                "unsigned mou")) {
            return "The cryptographic seal fixes what was actually on the page at the time of capture, so any later-produced countersigned copy can be tested against the sealed record instead of being accepted at face value.";
        }
        return "";
    }

    private String buildHumanPatternOriginLine(List<JSONObject> evidence) {
        String corpus = lowerUs(buildHumanNarrativeCorpus(evidence));
        boolean explicitPatternOrigin = containsAny(corpus,
                "liam harcock",
                "became suspicious",
                "became suspicious that",
                "brought the records together",
                "sealed comparison",
                "tested that suspicion",
                "identified the pattern",
                "how the pattern was found");
        if (explicitPatternOrigin) {
            return "The repeat-method concern was first raised by Liam Harcock after he became suspicious that operators were losing sites or signing away goodwill under similar unsigned MOU or renewal circumstances. He brought the records together for sealed comparison, and the sealed comparison process then tested that suspicion against the documents; it did not invent the underlying pattern on its own.";
        }
        boolean softPatternOrigin = containsAny(corpus,
                "you saw the pattern before the ai did",
                "saw the pattern before the ai did");
        if (softPatternOrigin) {
            if (containsAny(corpus, "liam harcock", "liam highcock")) {
                return "The sealed record says Liam Harcock saw the pattern before the AI did and brought the related material together for comparison. The engine then tested that pattern claim against the sealed documents instead of inventing it.";
            }
            return "The sealed record says the pattern was noticed before the AI did and the related material was brought together for comparison. The engine then tested that pattern claim against the sealed documents instead of inventing it.";
        }
        return "";
    }

    private String buildHumanNarrativeCorpus(List<JSONObject> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JSONObject item : items) {
            if (item == null) {
                continue;
            }
            maybeAppendCorpus(sb, item.optString("summary", ""));
            maybeAppendCorpus(sb, item.optString("text", ""));
            maybeAppendCorpus(sb, item.optString("narrative", ""));
            maybeAppendCorpus(sb, item.optString("excerpt", ""));
            maybeAppendCorpus(sb, item.optString("label", ""));
        }
        return sb.toString();
    }

    private void maybeAppendCorpus(StringBuilder sb, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(trimmed);
    }

    private List<String> collectHumanOverviewPages(List<JSONObject> primary, List<JSONObject> secondary, int limit) {
        LinkedHashSet<String> pages = new LinkedHashSet<>();
        collectHumanOverviewPagesFromItems(pages, primary, limit);
        collectHumanOverviewPagesFromItems(pages, secondary, limit);
        return new ArrayList<>(pages);
    }

    private void collectHumanOverviewPagesFromItems(Set<String> pages, List<JSONObject> items, int limit) {
        if (items == null) {
            return;
        }
        for (JSONObject item : sortByNarrativePriority(items)) {
            if (item == null || pages.size() >= limit) {
                continue;
            }
            JSONArray anchors = item.optJSONArray("anchors");
            if (anchors != null) {
                for (int i = 0; i < anchors.length() && pages.size() < limit; i++) {
                    JSONObject anchor = anchors.optJSONObject(i);
                    if (anchor == null) {
                        continue;
                    }
                    int page = anchor.optInt("page", 0);
                    if (page > 0) {
                        pages.add("p. " + page);
                    }
                }
            }
            int page = item.optInt("page", 0);
            if (page > 0 && pages.size() < limit) {
                pages.add("p. " + page);
            }
        }
    }

    private int appendHumanSnapshotRows(
            StringBuilder sb,
            List<JSONObject> items,
            String fallbackStatus,
            int limit,
            Set<String> seen
    ) {
        if (items == null || items.isEmpty() || limit <= 0) {
            return 0;
        }
        int emitted = 0;
        for (JSONObject item : sortByNarrativePriority(items)) {
            if (item == null || emitted >= limit) {
                continue;
            }
            String summary = humanSummaryLine(item, true);
            if (summary == null || summary.trim().isEmpty()) {
                continue;
            }
            if (!seen.add(summary.trim())) {
                continue;
            }
            String status = item.optString("status", "").trim();
            if (status.isEmpty()) {
                status = fallbackStatus;
            }
            String actor = item.optString("actor", "").trim();
            if (actor.isEmpty() || isDiscardedHumanActorName(actor)) {
                actor = inferHumanActorFromSummary(summary);
            }
            if (actor.isEmpty()) {
                actor = "Record";
            }
            sb.append(status)
                    .append(" | ")
                    .append(actor)
                    .append(" | ")
                    .append(clipReportText(summary, 110))
                    .append(" | ")
                    .append(firstNonEmpty(compactEvidencePages(item, 4), "n/a"))
                    .append("\n");
            emitted++;
        }
        return emitted;
    }

    private String inferHumanActorFromSummary(String summary) {
        if (summary == null || summary.trim().isEmpty()) {
            return "";
        }
        int pipe = summary.indexOf('|');
        if (pipe <= 0) {
            return "";
        }
        String actor = summary.substring(0, pipe).trim();
        return isDiscardedHumanActorName(actor) ? "" : actor;
    }

    private String compactEvidencePages(JSONObject item, int limit) {
        if (item == null || limit <= 0) {
            return "";
        }
        LinkedHashSet<String> pages = new LinkedHashSet<>();
        JSONArray anchors = item.optJSONArray("anchors");
        if (anchors != null) {
            for (int i = 0; i < anchors.length() && pages.size() < limit; i++) {
                JSONObject anchor = anchors.optJSONObject(i);
                if (anchor == null) {
                    continue;
                }
                int page = anchor.optInt("page", 0);
                if (page > 0) {
                    pages.add("p. " + page);
                }
            }
        }
        int page = item.optInt("page", 0);
        if (page > 0 && pages.size() < limit) {
            pages.add("p. " + page);
        }
        return joinHumanNames(new ArrayList<>(pages));
    }

    private String humanizeActorFlags(JSONArray flags) {
        if (flags == null || flags.length() == 0) {
            return "";
        }
        List<String> phrases = new ArrayList<>();
        for (int i = 0; i < flags.length(); i++) {
            String flag = flags.optString(i, "").trim().toLowerCase(Locale.US);
            String phrase = "";
            if ("financial".equals(flag)) {
                phrase = "financial extraction or unpaid proceeds";
            } else if ("contradictions".equals(flag) || "contradiction-led".equals(flag)) {
                phrase = "contradictory statements or inconsistent explanations";
            } else if ("omissions".equals(flag) || "execution".equals(flag)) {
                phrase = "missing performance or withheld execution";
            } else if ("evasion".equals(flag) || "pressure".equals(flag)) {
                phrase = "pressure, evasion, or retaliatory conduct";
            } else if ("concealment".equals(flag) || "cyber".equals(flag)) {
                phrase = "concealment, intrusion, or tampering behaviour";
            }
            if (!phrase.isEmpty() && !phrases.contains(phrase)) {
                phrases.add(phrase);
            }
            if (phrases.size() >= 3) {
                break;
            }
        }
        return joinHumanNames(phrases);
    }

    private String firstJSONArrayText(JSONArray values) {
        if (values == null) {
            return "";
        }
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String cleanHumanEvidenceNote(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u2022', ' ')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String humanPageReference(JSONArray pages, int limit) {
        if (pages == null || pages.length() == 0 || limit <= 0) {
            return "";
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (int i = 0; i < pages.length() && unique.size() < limit; i++) {
            String value = pages.optString(i, "").trim();
            if (!value.isEmpty() && !"0".equals(value)) {
                unique.add(value);
            }
        }
        if (unique.isEmpty()) {
            return "";
        }
        return "Key pages: " + joinHumanNames(new ArrayList<>(unique)) + ".";
    }

    private String joinHumanNames(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        if (values.size() == 2) {
            return values.get(0) + " and " + values.get(1);
        }
        return String.join(", ", values.subList(0, values.size() - 1)) + ", and " + values.get(values.size() - 1);
    }

    private boolean isInstitutionalHumanActor(String actor) {
        String lower = actor == null ? "" : actor.trim().toLowerCase(Locale.US);
        return containsAny(lower,
                "police",
                "saps",
                "hawks",
                "department",
                "authority",
                "ministry",
                "government",
                "office",
                "council",
                "rakez",
                "ohchr",
                "fcdo",
                "samsa",
                "dffe");
    }

    private void appendHumanChronologySection(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            List<JSONObject> primaryTimeline,
            String emptyMessage
    ) {
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        if ((primaryTimeline == null || primaryTimeline.isEmpty())
                && (assembled.chronology == null || assembled.chronology.isEmpty())) {
            sb.append(emptyMessage).append("\n");
            return;
        }
        sb.append("Chronology of key events reconstructed from anchored dated material where available.\n");
        sb.append("Date | Event | Actor(s) | Evidence\n");
        sb.append("-----------------------------------\n");
        int emitted = 0;
        if (assembled.chronology != null && !assembled.chronology.isEmpty()) {
            for (ForensicReportAssembler.ChronologyEvent event : assembled.chronology) {
                if (event == null || emitted >= 12) {
                    continue;
                }
                StringBuilder line = new StringBuilder();
                line.append(event.dateLabel)
                        .append(" | ")
                        .append(event.summary);
                if (event.actors != null && !event.actors.isEmpty()) {
                    line.append(" | actor: ").append(joinHumanNames(event.actors));
                }
                if (event.evidencePages != null && !event.evidencePages.isEmpty()) {
                    List<String> pages = new ArrayList<>();
                    for (Integer page : event.evidencePages) {
                        if (page != null && page > 0) {
                            pages.add("p. " + page);
                        }
                    }
                    if (!pages.isEmpty()) {
                        line.append(" | ").append(joinHumanNames(pages));
                    }
                }
                line.append(" | ").append(event.status);
                sb.append("- ").append(line).append("\n");
                emitted++;
            }
        } else {
            for (JSONObject item : sortByNarrativePriority(primaryTimeline)) {
                if (item == null || emitted >= 12) {
                    continue;
                }
                String line = buildHumanChronologyLine(item);
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                sb.append("- ").append(line).append("\n");
                emitted++;
            }
        }
        if (emitted == 0) {
            sb.append(emptyMessage).append("\n");
        }
    }

    private String buildHumanChronologyLine(JSONObject item) {
        if (item == null) {
            return "";
        }
        String actor = canonicalizeHumanVictimDisplay(item.optString("actor", "").trim());
        String eventType = item.optString("eventType", "").trim();
        String date = item.optString("date", "").trim();
        String pageRef = compactEvidencePages(item, 4);
        String eventSummary;
        switch (eventType.toUpperCase(Locale.US)) {
            case "EXECUTION_STATUS":
                eventSummary = "the document state is described as unsigned, not countersigned, or not validly executed";
                break;
            case "EVICTION_PRESSURE":
                eventSummary = "the record describes non-renewal, pressure to vacate, or threatened removal";
                break;
            case "LAW_ENFORCEMENT_NOTICE":
                eventSummary = "the record states a criminal or law-enforcement escalation";
                break;
            case "COMPLAINT_ESCALATION":
                eventSummary = "the record states a complaint or escalation step";
                break;
            case "MANDATE_OR_AUTHORITY":
                eventSummary = "the record states an authority or mandate position";
                break;
            default:
                String line = humanSummaryLine(item, false);
                if (line == null || line.trim().isEmpty()) {
                    return "";
                }
                return line + (pageRef.isEmpty() ? "" : " | " + pageRef);
        }
        StringBuilder line = new StringBuilder();
        line.append(date.isEmpty() ? "Date not fixed in this extract" : date)
                .append(" | ")
                .append(eventSummary);
        if (!actor.isEmpty() && !isDiscardedHumanActorName(actor)) {
            line.append(" | actor: ").append(actor);
        }
        if (!pageRef.isEmpty()) {
            line.append(" | ").append(pageRef);
        }
        return line.toString();
    }

    @SafeVarargs
    private final List<JSONObject> buildHumanChronologyEvidence(
            List<JSONObject> primaryTimeline,
            List<JSONObject>... fallbackGroups
    ) {
        List<JSONObject> filteredTimeline = filterMeaningfulHumanTimeline(primaryTimeline);
        if (filteredTimeline != null && !filteredTimeline.isEmpty()) {
            return filteredTimeline;
        }
        LinkedHashMap<String, JSONObject> fallback = new LinkedHashMap<>();
        if (fallbackGroups != null) {
            for (List<JSONObject> group : fallbackGroups) {
                if (group == null) {
                    continue;
                }
                for (JSONObject item : sortByNarrativePriority(group)) {
                    if (!looksChronologyUsable(item)) {
                        continue;
                    }
                    String key = chronologyEvidenceKey(item);
                    if (!fallback.containsKey(key)) {
                        fallback.put(key, item);
                    }
                    if (fallback.size() >= 12) {
                        break;
                    }
                }
                if (fallback.size() >= 12) {
                    break;
                }
            }
        }
        return new ArrayList<>(fallback.values());
    }

    private boolean looksChronologyUsable(JSONObject item) {
        if (item == null) {
            return false;
        }
        String label = lowerUs(firstNonEmpty(
                item.optString("label", null),
                item.optString("eventType", null),
                item.optString("category", null)
        ));
        String corpus = lowerUs(buildHumanNarrativeCorpus(item));
        if (corpus.isEmpty()) {
            return false;
        }
        if (isSecondaryNarrativeEvidence(corpus)) {
            return false;
        }
        if (isSupportOnlyHumanNarrative(corpus) && !containsHardForensicHumanSignal(corpus)) {
            return false;
        }
        if (containsAny(label,
                "document execution state",
                "document integrity",
                "cybercrime",
                "emotional exploitation",
                "shareholder oppression",
                "financial irregularities",
                "breach of fiduciary duty",
                "concealment / deletion")) {
            return false;
        }
        if (containsAny(corpus,
                "survived the primary extraction filter",
                "forensic correction & addendum",
                "please ensure this judgment",
                "fraud docket",
                "recused protection-order case file",
                "official record",
                "cryptographically sealed evidence",
                "national priority indicators",
                "this isn't just documents")) {
            return false;
        }
        if (corpus.contains("chronological narrative")
                || corpus.contains("timeline")
                || corpus.contains("dated")
                || corpus.contains("date:")
                || corpus.matches(".*\\b20\\d{2}\\b.*")
                || corpus.matches(".*\\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\b.*")) {
            return true;
        }
        return corpus.matches(".*\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b.*");
    }

    private String chronologyEvidenceKey(JSONObject item) {
        if (item == null) {
            return "";
        }
        return firstNonEmpty(
                item.optString("summary", null),
                item.optString("excerpt", null),
                item.optString("text", null),
                item.optString("label", null),
                item.optString("findingType", null),
                item.toString()
        ).trim();
    }

    private void appendHumanLegalSubjectMappingSection(
            StringBuilder sb,
            JSONArray legalSubjects,
            List<JSONObject> primarySubjectFindings,
            List<JSONObject> primaryTimeline,
            AnalysisEngine.ForensicReport report
    ) {
        LinkedHashSet<String> rendered = new LinkedHashSet<>();
        if (legalSubjects != null) {
            for (int i = 0; i < legalSubjects.length() && rendered.size() < 8; i++) {
                JSONObject item = legalSubjects.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String subject = firstNonEmpty(
                        item.optString("subject", null),
                        item.optString("label", null),
                        item.optString("type", null)
                );
                if (subject == null || subject.trim().isEmpty()) {
                    continue;
                }
                StringBuilder line = new StringBuilder();
                line.append(subject.trim()).append(". Definition: ").append(buildHumanLegalSubjectDefinition(subject));
                String evidence = firstNonEmpty(
                        item.optString("excerpt", null),
                        findRelatedHumanEvidence(primarySubjectFindings, subject),
                        findRelatedHumanEvidence(primaryTimeline, subject)
                );
                if (!evidence.isEmpty()) {
                    line.append(" Evidence in this pass: ").append(clipReportText(evidence, 220)).append(".");
                }
                int page = item.optInt("page", 0);
                if (page > 0) {
                    line.append(" | page ").append(page);
                }
                rendered.add(line.toString());
            }
        }

        if (rendered.isEmpty() && report != null && report.topLiabilities != null) {
            for (int i = 0; i < report.topLiabilities.length && rendered.size() < 6; i++) {
                String liability = report.topLiabilities[i];
                if (liability == null || liability.trim().isEmpty()) {
                    continue;
                }
                rendered.add(liability.trim() + ". Definition: " + buildHumanLegalSubjectDefinition(liability));
            }
        }

        if (rendered.isEmpty()) {
            sb.append("No mature legal subject mapping survived this pass.\n");
            return;
        }
        for (String line : rendered) {
            sb.append("- ").append(line).append("\n");
        }
        if (report != null && report.legalReferences != null && report.legalReferences.length > 0) {
            List<String> legalRefs = new ArrayList<>();
            for (String item : report.legalReferences) {
                if (item != null && !item.trim().isEmpty() && !legalRefs.contains(item.trim())) {
                    legalRefs.add(item.trim());
                }
                if (legalRefs.size() >= 6) {
                    break;
                }
            }
            if (!legalRefs.isEmpty()) {
                sb.append("- Legal references retained in this pass: ").append(joinStringList(legalRefs)).append(".\n");
            }
        }
    }

    private String buildHumanLegalSubjectDefinition(String subject) {
        String lower = lowerUs(subject);
        if (lower.contains("shareholder oppression")) {
            return "This refers to the exclusion or prejudicial treatment of a shareholder through control, secrecy, or diversion of company opportunities.";
        }
        if (lower.contains("fiduciary")) {
            return "This refers to a duty of loyalty and good faith owed to the company or another protected party, especially where money or control is involved.";
        }
        if (lower.contains("fraud")) {
            return "This refers to deliberate deception, concealment, or false representation to obtain money, control, advantage, or to defeat accountability.";
        }
        if (lower.contains("forg")) {
            return "This refers to fabricated, altered, or manipulated documents, signatures, or communications used as if they were genuine.";
        }
        if (lower.contains("cyber") || lower.contains("unauthorized account access") || lower.contains("digital interference")) {
            return "This refers to unlawful access, interference, monitoring, deletion, or manipulation of a digital account, device, or communication record.";
        }
        if (lower.contains("emotional exploitation") || lower.contains("gaslighting") || lower.contains("coerc")) {
            return "This refers to manipulative conduct designed to destabilise, pressure, silence, or discredit a target rather than resolve the underlying issue honestly.";
        }
        if (lower.contains("human rights") || lower.contains("denial of remedy")) {
            return "This refers to a failure by institutions or counterparties to provide lawful process, protection, or an effective remedy after notice of harm.";
        }
        if (lower.contains("financial")) {
            return "This refers to a dispute or irregularity involving money flow, profit share, unpaid invoices, diversion, or unexplained exposure.";
        }
        return "This subject is treated by the engine as a legally material category that must be anchored to named evidence before it can be safely promoted.";
    }

    private String findRelatedHumanEvidence(List<JSONObject> items, String subject) {
        if (items == null || items.isEmpty() || subject == null || subject.trim().isEmpty()) {
            return "";
        }
        String lower = lowerUs(subject);
        for (JSONObject item : sortByNarrativePriority(items)) {
            if (item == null) {
                continue;
            }
            String corpus = lowerUs(buildHumanNarrativeCorpus(item));
            if (!corpus.isEmpty() && corpus.contains(lower)) {
                return humanSummaryLine(item, true);
            }
        }
        return "";
    }

    private void appendHumanVisualEvidenceSummarySection(
            StringBuilder sb,
            List<JSONObject> primaryVisualFindings,
            JSONObject nativeEvidence
    ) {
        int sourcePages = nativeEvidence != null
                ? nativeEvidence.optInt("sourcePageCount", nativeEvidence.optInt("pageCount", 0))
                : 0;
        int ocrSuccess = nativeEvidence != null ? nativeEvidence.optInt("ocrSuccessCount", 0) : 0;
        int ocrFailed = nativeEvidence != null ? nativeEvidence.optInt("ocrFailedCount", 0) : 0;
        sb.append("- Visual/OCR scope: ")
                .append(sourcePages)
                .append(" source pages, OCR success ")
                .append(ocrSuccess)
                .append(", OCR failed ")
                .append(ocrFailed)
                .append(".\n");
        appendHumanSignatureOcrCaution(sb, primaryVisualFindings, ocrSuccess, ocrFailed);
        appendHumanVisualIntegritySection(sb, primaryVisualFindings);
    }

    private void appendHumanCriticalLegalSubjectsSection(
            StringBuilder sb,
            JSONArray legalSubjects,
            AnalysisEngine.ForensicReport report
    ) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        if (legalSubjects != null) {
            for (int i = 0; i < legalSubjects.length() && lines.size() < 8; i++) {
                String line = summarizeHumanReportItem(legalSubjects.opt(i));
                if (line != null && !line.trim().isEmpty()) {
                    lines.add(line.trim());
                }
            }
        }
        if (lines.isEmpty() && report != null && report.topLiabilities != null) {
            for (int i = 0; i < report.topLiabilities.length && lines.size() < 6; i++) {
                String liability = report.topLiabilities[i];
                if (liability != null && !liability.trim().isEmpty()) {
                    lines.add(liability.trim());
                }
            }
        }
        if (lines.isEmpty()) {
            sb.append("No mature legal subject classification survived this pass.\n");
            return;
        }
        for (String line : lines) {
            sb.append("- ").append(line).append("\n");
        }
    }

    private void appendHumanDishonestyMatrixSection(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            JSONObject forensicSynthesis,
            JSONArray contradictions,
            JSONArray financialExposure,
            JSONArray patternMatches,
            JSONArray vulnerabilityMatches
    ) {
        JSONArray crossBrainContradictions = forensicSynthesis != null
                ? forensicSynthesis.optJSONArray("crossBrainContradictions")
                : null;
        sb.append("- Truth score: ").append(report.truthScore).append("/100\n");
        sb.append("- Dishonesty status: ").append(safeValue(report.dishonestyStatus))
                .append(" | threshold ").append(report.dishonestyThreshold).append("\n");
        sb.append("- Cross-brain contradictions: verified ")
                .append(countEvidenceItemsByStatus(crossBrainContradictions, "VERIFIED"))
                .append(" | total ")
                .append(crossBrainContradictions != null ? crossBrainContradictions.length() : 0)
                .append("\n");
        sb.append("- Typed contradictions: verified ")
                .append(countEvidenceItemsByStatus(contradictions, "VERIFIED"))
                .append(" | candidate ")
                .append(countEvidenceItemsByStatus(contradictions, "CANDIDATE"))
                .append(" | rejected ")
                .append(countEvidenceItemsByStatus(contradictions, "REJECTED"))
                .append("\n");
        sb.append("- Conflict classes: negation ")
                .append(countEvidenceItemsByField(contradictions, "conflictType", "NEGATION"))
                .append(" | numeric ")
                .append(countEvidenceItemsByField(contradictions, "conflictType", "NUMERIC"))
                .append(" | timeline ")
                .append(countEvidenceItemsByField(contradictions, "conflictType", "TIMELINE"))
                .append(" | location ")
                .append(countEvidenceItemsByField(contradictions, "conflictType", "LOCATION"))
                .append("\n");
        sb.append("- Financial exposure register entries: ")
                .append(filterStructuredFinancialExposure(financialExposure).length())
                .append("\n");
        if (patternMatches != null && patternMatches.length() > 0) {
            sb.append("- Behavioural pattern matches retained in sealed audit record: ")
                    .append(patternMatches.length()).append("\n");
        }
        if (vulnerabilityMatches != null && vulnerabilityMatches.length() > 0) {
            sb.append("- Vulnerability-linked flags retained in sealed audit record: ")
                    .append(vulnerabilityMatches.length()).append("\n");
        }
        String synthesisSummary = forensicSynthesis != null ? forensicSynthesis.optString("summary", "").trim() : "";
        if (!synthesisSummary.isEmpty()) {
            sb.append("- Synthesis summary: ").append(clipReportText(synthesisSummary, 240)).append("\n");
        }
        appendHumanActorScoreSummary(sb, forensicSynthesis != null ? forensicSynthesis.optJSONArray("actorDishonestyScores") : null, 3);
        if (report.dishonestyFindings != null && report.dishonestyFindings.length > 0) {
            for (int i = 0; i < Math.min(report.dishonestyFindings.length, 4); i++) {
                String finding = report.dishonestyFindings[i];
                if (finding != null && !finding.trim().isEmpty()) {
                    sb.append("- Matrix flag: ").append(finding.trim()).append("\n");
                }
            }
        }
    }

    private int countEvidenceItemsByStatus(JSONArray items, String status) {
        if (items == null || status == null || status.trim().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && status.equalsIgnoreCase(item.optString("status", ""))) {
                count++;
            }
        }
        return count;
    }

    private int countEvidenceItemsByField(JSONArray items, String field, String value) {
        if (items == null || field == null || value == null || field.trim().isEmpty() || value.trim().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && value.equalsIgnoreCase(item.optString(field, ""))) {
                count++;
            }
        }
        return count;
    }

    private void appendHumanNativeMediaDepartmentSection(StringBuilder sb, JSONObject nativeEvidence) {
        if (nativeEvidence == null || nativeEvidence.length() == 0) {
            sb.append("- Native evidence pipeline output was not available in this pass.\n");
            return;
        }
        int sourcePages = nativeEvidence.optInt("sourcePageCount", nativeEvidence.optInt("pageCount", 0));
        int renderedPages = nativeEvidence.optInt("renderedPageCount", 0);
        int ocrSuccess = nativeEvidence.optInt("ocrSuccessCount", 0);
        int ocrFailed = nativeEvidence.optInt("ocrFailedCount", 0);
        sb.append("- Source pages: ").append(sourcePages)
                .append(" | rendered pages: ").append(renderedPages)
                .append(" | OCR success: ").append(ocrSuccess)
                .append(" | OCR failed: ").append(ocrFailed)
                .append("\n");
        JSONArray anchors = nativeEvidence.optJSONArray("anchors");
        if (anchors != null && anchors.length() > 0) {
            sb.append("- Native evidence anchors preserved: ").append(anchors.length()).append("\n");
        }
        JSONArray visualFindings = nativeEvidence.optJSONArray("visualFindings");
        if (visualFindings != null && visualFindings.length() > 0) {
            sb.append("- Visual/media findings preserved in the native pipeline: ").append(visualFindings.length()).append("\n");
        }
    }

    private void appendHumanNamedActors(StringBuilder sb, JSONArray namedParties, int limit) {
        if (namedParties == null || namedParties.length() == 0) {
            sb.append("No named actors were extracted with confidence.\n");
            return;
        }

        StringBuilder line = new StringBuilder();
        int emitted = 0;
        for (int i = 0; i < namedParties.length() && emitted < limit; i++) {
            JSONObject party = namedParties.optJSONObject(i);
            if (party == null) {
                continue;
            }
            String name = party.optString("name", "").trim();
            if (!isAllowedHumanActor(name, namedParties)) {
                continue;
            }
            if (line.length() > 0) {
                line.append("; ");
            }
            line.append(name);
            if (party.has("firstPage")) {
                line.append(" (page ").append(party.optInt("firstPage", 0)).append(")");
            }
            emitted++;
        }

        if (line.length() == 0) {
            sb.append("No named actors were extracted with confidence.\n");
        } else {
            sb.append(line).append("\n");
        }
    }

    private void appendBrainConsensusSection(StringBuilder sb, JSONObject brainAnalysis) {
        if (brainAnalysis == null || brainAnalysis.length() == 0) {
            sb.append("Nine-brain analysis was not available in this pass.\n");
            return;
        }
        JSONObject consensus = brainAnalysis.optJSONObject("consensus");
        if (consensus != null) {
            sb.append("- Outcome: ").append(consensus.optString("processingStatus", "UNKNOWN")).append("\n");
            sb.append("- Summary: ").append(consensus.optString("summary", "No consensus summary available.")).append("\n");
            int verifiedFindingCount = consensus.optInt("verifiedFindingCount", 0);
            if (verifiedFindingCount > 0) {
                sb.append("- Guardian-approved certified findings: ").append(verifiedFindingCount).append("\n");
            }
            if (consensus.has("guardianApproved")) {
                boolean guardianApproved = consensus.optBoolean("guardianApproved", false);
                sb.append("- Guardian approval: ").append(guardianApproved ? "Approved" : "Denied").append("\n");
                String guardianNote = consensus.optString("guardianNote", "").trim();
                if (!guardianNote.isEmpty()) {
                    sb.append("- Guardian note: ").append(guardianNote).append("\n");
                }
                if (!guardianApproved) {
                    sb.append("- Outcome note: No findings were certified in this run.\n");
                }
            }
            if (consensus.has("quorumSatisfied")) {
                boolean quorumSatisfied = consensus.optBoolean("quorumSatisfied", false);
                sb.append("- Constitutional quorum: ")
                        .append(quorumSatisfied ? "Satisfied" : "Not satisfied")
                        .append(" (")
                        .append(consensus.optInt("contributingVotingBrainCount", 0))
                        .append("/")
                        .append(consensus.optInt("quorumMin", 0))
                        .append(" contributing voting brains)\n");
                if (!quorumSatisfied) {
                    String tieBreaker = consensus.optString("tieBreaker", "").trim();
                    if (!tieBreaker.isEmpty()) {
                        sb.append("- Tie-breaker: ").append(tieBreaker).append("\n");
                    }
                }
                String constitutionalOutcome = consensus.optString("constitutionalOutcome", "").trim();
                if (!constitutionalOutcome.isEmpty()
                        && !constitutionalOutcome.equalsIgnoreCase(consensus.optString("processingStatus", ""))) {
                    sb.append("- Constitutional outcome: ").append(constitutionalOutcome).append("\n");
                }
            }
            sb.append("- Engaged brains: ")
                    .append(consensus.optInt("engagedBrainCount", 0))
                    .append("/9 total, ")
                    .append(consensus.optInt("engagedVotingBrainCount", 0))
                    .append(" voting\n");
            JSONArray concordantThemes = consensus.optJSONArray("concordantThemes");
            if (concordantThemes != null && concordantThemes.length() > 0) {
                sb.append("- Concordant themes: ").append(joinJsonArray(concordantThemes, 5)).append("\n");
            }
            JSONArray coverageGaps = consensus.optJSONArray("coverageGaps");
            if (coverageGaps != null && coverageGaps.length() > 0) {
                sb.append("- Coverage gaps: ").append(joinJsonArray(coverageGaps, 3)).append("\n");
            }
        }
        JSONArray brains = brainAnalysis.optJSONArray("brains");
        if (brains == null || brains.length() == 0) {
            return;
        }
        sb.append("- Brain states:\n");
        for (int i = 0; i < brains.length(); i++) {
            JSONObject brain = brains.optJSONObject(i);
            if (brain == null) {
                continue;
            }
            sb.append("  - ")
                    .append(brain.optString("id", "B?"))
                    .append(" ")
                    .append(brain.optString("name", "Unnamed"))
                    .append(": ")
                    .append(brain.optString("status", "UNKNOWN"))
                    .append(" / ")
                    .append(brain.optString("summary", "No summary available."))
                    .append("\n");
        }
    }

    private void appendTripleVerificationSection(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report
    ) {
        if (report == null || report.tripleVerification == null || report.tripleVerification.length() == 0) {
            sb.append("- Triple verification was not available in this pass.\n");
            return;
        }
        JSONObject triple = report.tripleVerification;
        appendVerificationStage(sb, "Thesis", triple.optJSONObject("thesis"));
        appendVerificationStage(sb, "Antithesis", triple.optJSONObject("antithesis"));
        appendVerificationStage(sb, "Synthesis", triple.optJSONObject("synthesis"));
        appendVerificationStage(sb, "Overall", triple.optJSONObject("overall"));
        String tieBreaker = triple.optString("tieBreaker", "").trim();
        if (!tieBreaker.isEmpty()) {
            sb.append("- Tie-breaker: ").append(tieBreaker).append("\n");
        }
        int quorumMin = triple.optInt("quorumMin", 0);
        if (quorumMin > 0) {
            sb.append("- Quorum minimum: ").append(quorumMin).append(" contributing voting brains.\n");
        }
        String concealmentOutput = triple.optString("concealmentOutput", "").trim();
        if (!concealmentOutput.isEmpty()) {
            sb.append("- Concealment output: ").append(concealmentOutput).append("\n");
        }
    }

    private void appendVerificationStage(StringBuilder sb, String label, JSONObject stage) {
        if (stage == null) {
            sb.append("- ").append(label).append(": not available.\n");
            return;
        }
        String status = stage.optString("status", "UNKNOWN").trim();
        String reason = stage.optString("reason", "").trim();
        sb.append("- ").append(label).append(": ").append(status);
        if (!reason.isEmpty()) {
            sb.append(" - ").append(reason);
        }
        sb.append("\n");
    }

    private void appendHumanLiabilitySummary(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            JSONArray legalSubjects,
            int limit
    ) {
        if (report.topLiabilities != null && report.topLiabilities.length > 0) {
            for (int i = 0; i < Math.min(report.topLiabilities.length, limit); i++) {
                String liability = report.topLiabilities[i];
                if (liability == null || liability.trim().isEmpty()) {
                    continue;
                }
                sb.append("- ").append(liability.trim()).append("\n");
            }
        }

        if (legalSubjects != null && legalSubjects.length() > 0) {
            int emitted = 0;
            for (int i = 0; i < legalSubjects.length() && emitted < limit; i++) {
                String line = summarizeHumanReportItem(legalSubjects.opt(i));
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                sb.append("- ").append(line).append("\n");
                emitted++;
            }
        }

        if ((report.topLiabilities == null || report.topLiabilities.length == 0)
                && (legalSubjects == null || legalSubjects.length() == 0)) {
            sb.append("No mature offence or exposure classification was extracted in this pass.\n");
        }
    }

    private void appendHumanSummarySection(
            StringBuilder sb,
            JSONArray array,
            int limit,
            String emptyMessage
    ) {
        if (array == null || array.length() == 0) {
            sb.append(emptyMessage).append("\n");
            return;
        }

        int emitted = 0;
        for (int i = 0; i < array.length() && emitted < limit; i++) {
            String line = summarizeHumanReportItem(array.opt(i));
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            sb.append("- ").append(line).append("\n");
            emitted++;
        }

        if (emitted == 0) {
            sb.append(emptyMessage).append("\n");
        }
    }

    private JSONObject preferredForensicSynthesis(AnalysisEngine.ForensicReport report) {
        if (report == null) {
            return null;
        }
        if (report.forensicSynthesis != null && report.forensicSynthesis.length() > 0) {
            return report.forensicSynthesis;
        }
        if (report.brainAnalysis == null) {
            return null;
        }
        JSONObject b1Synthesis = report.brainAnalysis.optJSONObject("b1Synthesis");
        return b1Synthesis != null && b1Synthesis.length() > 0 ? b1Synthesis : null;
    }

    private void appendHumanSynthesisSection(
            StringBuilder sb,
            String fileName,
            AnalysisEngine.ForensicReport report,
            JSONObject forensicSynthesis,
            List<JSONObject> primaryTimeline,
            List<JSONObject> primarySubjectFindings,
            boolean indeterminateDueToConcealment,
            boolean guardianApproved
    ) {
        if (forensicSynthesis == null || forensicSynthesis.length() == 0) {
            appendHumanExecutiveSummary(
                    sb,
                    fileName,
                    report,
                    primaryTimeline,
                    primarySubjectFindings,
                    indeterminateDueToConcealment,
                    guardianApproved
            );
            return;
        }

        String synthesisSummary = forensicSynthesis.optString("summary", "").trim();
        if (!synthesisSummary.isEmpty()) {
            sb.append(synthesisSummary).append("\n");
        } else {
            sb.append("B1 synthesis was available, but no compact summary text was emitted in this pass.\n");
        }

        JSONObject wrongfulActorProfile = forensicSynthesis.optJSONObject("wrongfulActorProfile");
        if (wrongfulActorProfile != null) {
            String actor = wrongfulActorProfile.optString("actor", "").trim();
            String assessment = wrongfulActorProfile.optString("factualFaultAssessment", "").trim();
            if (!actor.isEmpty() && !isDiscardedHumanActorName(actor)) {
                sb.append("- Likely wrongful actor on the current evidence: ").append(actor);
                if (!assessment.isEmpty()) {
                    sb.append(" - ").append(assessment);
                }
                sb.append("\n");
            }
            JSONArray supportingBrains = wrongfulActorProfile.optJSONArray("supportingBrains");
            if (supportingBrains != null && supportingBrains.length() > 0) {
                sb.append("- Supporting brains: ").append(joinJsonArray(supportingBrains, 6)).append("\n");
            }
            JSONArray anchorPages = wrongfulActorProfile.optJSONArray("anchorPages");
            if (anchorPages != null && anchorPages.length() > 0) {
                sb.append("- Anchor pages: ").append(joinJsonArray(anchorPages, 6)).append("\n");
            }
        }

        appendHumanActorScoreSummary(sb, forensicSynthesis.optJSONArray("actorDishonestyScores"), 3);
        appendHumanCrossBrainSummary(sb, forensicSynthesis.optJSONArray("crossBrainContradictions"), 4);

        List<String> facts = collectHumanKeyFacts(primaryTimeline, primarySubjectFindings, 4);
        if (!facts.isEmpty()) {
            sb.append("- Primary evidence threads carried into this synthesis:\n");
            for (String fact : facts) {
                sb.append("  - ").append(fact).append("\n");
            }
        }

        if (!guardianApproved) {
            sb.append("- Guardian review denied certification in this pass, so the synthesis remains advisory and candidate-led until the guardian gate is satisfied.\n");
        }
        if (indeterminateDueToConcealment) {
            sb.append("- Concealment or extraction gaps still affect this record and should be resolved before treating the synthesis as mature.\n");
        }
    }

    private void appendHumanActorScoreSummary(
            StringBuilder sb,
            JSONArray actorScores,
            int limit
    ) {
        if (actorScores == null || actorScores.length() == 0) {
            return;
        }
        int emitted = 0;
        for (int i = 0; i < actorScores.length() && emitted < limit; i++) {
            JSONObject actorScore = actorScores.optJSONObject(i);
            if (actorScore == null) {
                continue;
            }
            if (!actorScore.optBoolean("likelyWrongfulParty", false)) {
                continue;
            }
            String actor = actorScore.optString("actor", "").trim();
            if (actor.isEmpty() || isDiscardedHumanActorName(actor)) {
                continue;
            }
            sb.append("- ").append(actor)
                    .append(" | dishonesty score ")
                    .append(actorScore.optInt("dishonestyScore", 0))
                    .append(" | ")
                    .append(actorScore.optString("severity", "LOW"));
            JSONArray flags = actorScore.optJSONArray("flags");
            if (flags != null && flags.length() > 0) {
                sb.append(" | flags: ").append(joinJsonArray(flags, 4));
            }
            sb.append("\n");
            emitted++;
        }
    }

    private void appendHumanCrossBrainSummary(
            StringBuilder sb,
            JSONArray contradictions,
            int limit
    ) {
        if (contradictions == null || contradictions.length() == 0) {
            return;
        }
        int emitted = 0;
        for (int i = 0; i < contradictions.length() && emitted < limit; i++) {
            JSONObject contradiction = contradictions.optJSONObject(i);
            if (contradiction == null) {
                continue;
            }
            String actor = contradiction.optString("actor", "").trim();
            if (actor.isEmpty() || isJunkContradictionActor(actor)) {
                continue;
            }
            sb.append("- ")
                    .append(actor)
                    .append(" | ")
                    .append(humanizeConflictType(contradiction.optString("contradictionType", "CONTRADICTION")))
                    .append(" | ")
                    .append(contradiction.optString("status", "CANDIDATE"));
            String reason = contradiction.optString("reason", "").trim();
            if (!reason.isEmpty()) {
                sb.append(" | ").append(clipReportText(reason, 180));
            }
            JSONArray supportingBrains = contradiction.optJSONArray("supportingBrains");
            if (supportingBrains != null && supportingBrains.length() > 0) {
                sb.append(" | brains ").append(joinJsonArray(supportingBrains, 4));
            }
            JSONArray anchorPages = contradiction.optJSONArray("anchorPages");
            if (anchorPages != null && anchorPages.length() > 0) {
                sb.append(" | pages ").append(joinJsonArray(anchorPages, 4));
            }
            sb.append("\n");
            emitted++;
        }
    }

    private void appendHumanExecutiveSummary(
            StringBuilder sb,
            String fileName,
            AnalysisEngine.ForensicReport report,
            List<JSONObject> primaryTimeline,
            List<JSONObject> primarySubjectFindings,
            boolean indeterminateDueToConcealment,
            boolean guardianApproved
    ) {
        sb.append("This report summarises the strongest actor-linked findings extracted from the sealed evidence for ")
                .append(fileName)
                .append(". ");

        if (!guardianApproved) {
            sb.append("Guardian review denied certification in this pass, so the summary below remains candidate-only and should be cross-checked against the technical report. ");
        }

        if (indeterminateDueToConcealment) {
            sb.append("The record is sealed, but some actor-event links still require caution and audit against the technical report. ");
        }

        List<String> actorNames = collectActorNamesFromEvidence(primaryTimeline, primarySubjectFindings, 5);
        if (!actorNames.isEmpty()) {
            sb.append("The main actors materially present in this pass are ")
                    .append(joinStringList(actorNames))
                    .append(".\n");
        } else {
            sb.append("\n");
        }

        List<String> facts = collectHumanKeyFacts(primaryTimeline, primarySubjectFindings, 5);
        if (facts.isEmpty()) {
            sb.append("- No clean fact statement survived the current primary-evidence filter.\n");
        } else {
            for (String fact : facts) {
                sb.append("- ").append(fact).append("\n");
            }
        }

        if (report.truthContinuityAnalysis != null) {
            String overallAssessment = report.truthContinuityAnalysis.optString("overallAssessment", "").trim();
            if (!overallAssessment.isEmpty()) {
                sb.append("- Truth continuity assessment: ").append(overallAssessment).append(".\n");
            }
        }
    }

    private void appendHumanActorEvidenceSection(
            StringBuilder sb,
            JSONObject forensicSynthesis,
            JSONArray namedParties,
            List<JSONObject> primaryTimeline,
            List<JSONObject> primarySubjectFindings,
            int limit
    ) {
        LinkedHashMap<String, List<JSONObject>> byActor = new LinkedHashMap<>();
        LinkedHashSet<String> victimAliases = collectHumanVictimAliases(forensicSynthesis);
        if (victimAliases.isEmpty()) {
            victimAliases = collectHumanVictimAliases(namedParties);
        }
        registerActorEvidence(byActor, primaryTimeline);
        registerActorEvidence(byActor, primarySubjectFindings);

        if (byActor.isEmpty()) {
            appendHumanNamedActors(sb, namedParties, limit);
            return;
        }

        List<Map.Entry<String, List<JSONObject>>> entries = new ArrayList<>(byActor.entrySet());
        Collections.sort(entries, (left, right) -> {
            int sizeCompare = Integer.compare(right.getValue().size(), left.getValue().size());
            if (sizeCompare != 0) {
                return sizeCompare;
            }
            return Integer.compare(firstPageOf(left.getValue()), firstPageOf(right.getValue()));
        });

        int emitted = 0;
        for (Map.Entry<String, List<JSONObject>> entry : entries) {
            if (emitted >= limit) {
                break;
            }
            String actor = entry.getKey();
            if (actor == null || actor.trim().isEmpty() || "unresolved actor".equalsIgnoreCase(actor) || isDiscardedHumanActorName(actor)) {
                continue;
            }
            String displayActor = canonicalizeHumanVictimDisplay(actor);
            boolean victimScoped = isVictimScopedHumanActor(displayActor, victimAliases);
            List<JSONObject> actorScopedItems = filterActorSpecificHumanEvidence(displayActor, entry.getValue());
            if (actorScopedItems.isEmpty()) {
                continue;
            }
            if (!victimScoped
                    && !displayActor.equalsIgnoreCase("Kevin Lappeman")
                    && containsOnlyVictimSideNarrative(actorScopedItems)) {
                continue;
            }
            List<JSONObject> rankedItems = sortByNarrativePriority(actorScopedItems);
            sb.append(displayActor).append("\n");
            sb.append("- Current position in the evidence: ");
            if (victimScoped) {
                sb.append("this person is carried in the sealed record as a harmed or at-risk party. ");
            }
            sb.append(joinHumanSummaries(rankedItems, 1)).append(".\n");
            sb.append("- Verified or primary points: ")
                    .append(joinHumanSummaries(rankedItems, 2))
                    .append(".\n");
            sb.append("- Key anchor pages: ")
                    .append(joinPages(rankedItems))
                    .append(".\n");
            emitted++;
        }

        if (emitted == 0) {
            appendHumanNamedActors(sb, namedParties, limit);
        }
    }

    private void appendHumanWhoDidWhatSection(
            StringBuilder sb,
            List<JSONObject> primaryTimeline,
            String emptyMessage
    ) {
        if (primaryTimeline.isEmpty()) {
            sb.append(emptyMessage).append("\n");
            return;
        }
        int emitted = 0;
        for (JSONObject item : sortByNarrativePriority(primaryTimeline)) {
            if (emitted >= 14) {
                break;
            }
            String line = humanSummaryLine(item, true);
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            sb.append("- ").append(line).append("\n");
            emitted++;
        }
        if (emitted == 0) {
            sb.append(emptyMessage).append("\n");
        }
    }

    private void appendHumanCoreFindingsSection(
            StringBuilder sb,
            List<JSONObject> findings,
            String emptyMessage
    ) {
        if (findings == null || findings.isEmpty()) {
            if (emptyMessage != null && !emptyMessage.trim().isEmpty()) {
                sb.append(emptyMessage).append("\n");
            }
            return;
        }
        int emitted = 0;
        for (JSONObject item : sortByNarrativePriority(findings)) {
            if (emitted >= 10) {
                break;
            }
            String line = humanSummaryLine(item, true);
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            sb.append("- ").append(line).append("\n");
            emitted++;
        }
        if (emitted == 0 && emptyMessage != null && !emptyMessage.trim().isEmpty()) {
            sb.append(emptyMessage).append("\n");
        }
    }

    private void appendHumanEvidenceIndexSection(
            StringBuilder sb,
            List<JSONObject> items,
            int limit
    ) {
        if (items == null || items.isEmpty()) {
            sb.append("No concise evidence index could be stated from the current primary-evidence pass.\n");
            return;
        }
        LinkedHashSet<String> rendered = new LinkedHashSet<>();
        for (JSONObject item : sortByNarrativePriority(items)) {
            if (item == null || rendered.size() >= limit) {
                continue;
            }
            String line = humanSummaryLine(item, false);
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            int page = item.optInt("page", 0);
            String renderedLine = page > 0 ? "Page " + page + " - " + line : line;
            rendered.add(renderedLine);
        }
        if (rendered.isEmpty()) {
            sb.append("No concise evidence index could be stated from the current primary-evidence pass.\n");
            return;
        }
        for (String line : rendered) {
            sb.append("- ").append(line).append("\n");
        }
    }

    private void appendHumanOffenceSection(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            List<JSONObject> primarySubjectFindings,
            List<JSONObject> primaryTimeline
    ) {
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        if (assembled != null && assembled.offenceFindings != null && !assembled.offenceFindings.isEmpty()) {
            int emittedDirect = 0;
            for (String finding : assembled.offenceFindings) {
                if (finding == null || finding.trim().isEmpty()) {
                    continue;
                }
                sb.append("- ").append(finding.trim()).append("\n");
                emittedDirect++;
                if (emittedDirect >= 8) {
                    break;
                }
            }
            List<String> legalRefs = new ArrayList<>();
            if (report.legalReferences != null) {
                for (String item : report.legalReferences) {
                    if (item != null && !item.trim().isEmpty() && !legalRefs.contains(item.trim())) {
                        legalRefs.add(item.trim());
                    }
                    if (legalRefs.size() >= 6) {
                        break;
                    }
                }
            }
            if (!legalRefs.isEmpty()) {
                sb.append("- Legal anchors referenced in this pass: ").append(joinStringList(legalRefs)).append(".\n");
            }
            return;
        }
        if (primarySubjectFindings.isEmpty() && primaryTimeline.isEmpty()
                && (report.topLiabilities == null || report.topLiabilities.length == 0)) {
            sb.append("No mature liability section could be stated from the current primary-evidence pass.\n");
            return;
        }

        LinkedHashSet<String> rendered = new LinkedHashSet<>();
        int emitted = 0;
        for (JSONObject subject : primarySubjectFindings) {
            if (emitted >= 10) {
                break;
            }
            String label = subject.optString("label", "").trim();
            if (label.isEmpty()) {
                continue;
            }
            String offenceLine = buildHumanOffenceLine(label, subject, primaryTimeline);
            if (offenceLine == null || offenceLine.trim().isEmpty() || !rendered.add(offenceLine)) {
                continue;
            }
            sb.append("- ").append(offenceLine).append("\n");
            emitted++;
        }

        if (emitted == 0 && report.topLiabilities != null) {
            for (int i = 0; i < Math.min(report.topLiabilities.length, 6); i++) {
                String liability = report.topLiabilities[i];
                if (liability == null || liability.trim().isEmpty()) {
                    continue;
                }
                if (rendered.add(liability.trim())) {
                    sb.append("- ").append(liability.trim()).append(".\n");
                }
            }
        }

        List<String> legalRefs = new ArrayList<>();
        if (report.legalReferences != null) {
            for (String item : report.legalReferences) {
                if (item != null && !item.trim().isEmpty() && !legalRefs.contains(item.trim())) {
                    legalRefs.add(item.trim());
                }
                if (legalRefs.size() >= 6) {
                    break;
                }
            }
        }
        if (!legalRefs.isEmpty()) {
            sb.append("- Legal anchors referenced in this pass: ").append(joinStringList(legalRefs)).append(".\n");
        }
    }

    private void appendHumanRecommendedActionsSection(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            boolean guardianApproved,
            boolean indeterminateDueToConcealment,
            boolean hasCertifiedFindings,
            JSONArray contradictions,
            JSONArray financialExposure
    ) {
        sb.append("- Start with sections 1 to 3 of this report. Use the sealed audit report only when you need the technical ledger behind the findings.\n");

        if (!guardianApproved) {
            sb.append("- Do not treat this run as final. Resolve the denied or under-supported findings and re-run before relying on candidate-only material.\n");
        } else if (hasCertifiedFindings) {
            sb.append("- Use the certified findings in section 3 as the main factual basis for any statement, affidavit, complaint, or prosecution brief.\n");
        }

        if (indeterminateDueToConcealment) {
            sb.append("- Obtain any missing source pages, original messages, or missing document versions before drawing a mature conclusion on disputed points.\n");
        }

        if (contradictions != null && contradictions.length() > 0) {
            sb.append("- Compare every contradiction against the exact sealed page anchors and preserve both sides of each conflict together.\n");
        }

        if (financialExposure != null && financialExposure.length() > 0) {
            sb.append("- Secure the rent, fee, upgrade, payment, and account records so the money trail can be checked against the sealed evidence bundle.\n");
        }

        sb.append("- Take statements from the harmed or at-risk parties named in this report and secure the original lease, MOU, renewal, and correspondence chain.\n");
        sb.append("- If any later-produced signed copy appears, compare it against the sealed record instead of accepting it at face value.\n");

        if ("UAE".equalsIgnoreCase(report.jurisdiction)) {
            sb.append("- Use the UAE route identified in the sealed record for company-law, regulatory, or prosecutorial follow-up.\n");
        } else if ("ZAF".equalsIgnoreCase(report.jurisdiction)) {
            sb.append("- Use the South African police, Hawks, NPA, or court route identified in the sealed record for criminal or civil escalation.\n");
        } else if ("MULTI".equalsIgnoreCase(report.jurisdiction)) {
            sb.append("- Treat this as a cross-border matter and coordinate both jurisdictions against the same sealed record.\n");
        } else {
            sb.append("- Confirm the correct escalation route from the legal subject map and sealed audit report before taking the matter further.\n");
        }
    }

    private void appendHumanContradictionAndForgerySection(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            List<JSONObject> primaryContradictions,
            List<JSONObject> primaryVisualFindings,
            JSONArray patternMatches,
            JSONArray vulnerabilityMatches,
            JSONObject truthContinuityAnalysis
    ) {
        JSONObject forensicSynthesis = preferredForensicSynthesis(report);
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        JSONArray crossBrainContradictions = forensicSynthesis != null
                ? forensicSynthesis.optJSONArray("crossBrainContradictions")
                : null;
        if (crossBrainContradictions != null) {
            for (int i = 0; i < crossBrainContradictions.length() && resolved.size() < 8; i++) {
                JSONObject item = crossBrainContradictions.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String actor = item.optString("actor", "").trim();
                if (actor.isEmpty() || isJunkContradictionActor(actor)) {
                    continue;
                }
                StringBuilder line = new StringBuilder();
                line.append(actor)
                        .append(" is linked to a ")
                        .append(item.optString("status", "CANDIDATE").toLowerCase(Locale.US))
                        .append(" ")
                        .append(humanizeConflictType(item.optString("contradictionType", "CONTRADICTION")));
                String reason = item.optString("reason", "").trim();
                if (!reason.isEmpty()) {
                    line.append(": ").append(clipReportText(reason, 160));
                }
                JSONArray anchorPages = item.optJSONArray("anchorPages");
                if (anchorPages != null && anchorPages.length() > 0) {
                    line.append(" | pages ").append(joinJsonArray(anchorPages, 4));
                }
                resolved.add(line.toString());
            }
        }
        if (resolved.isEmpty()) {
            for (JSONObject item : primaryContradictions) {
                String line = resolveContradictionToNarrative(item);
                if (line != null && !line.trim().isEmpty()) {
                    resolved.add(line);
                }
                if (resolved.size() >= 8) {
                    break;
                }
            }
        }
        if (resolved.isEmpty()) {
            sb.append("- No verified contradiction survived this pass.\n");
        } else {
            for (String line : resolved) {
                sb.append("- ").append(line).append("\n");
            }
        }

        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        if (assembled != null) {
            for (String line : assembled.behaviouralFindings) {
                if (line != null && !line.trim().isEmpty()) {
                    sb.append("- ").append(line.trim()).append("\n");
                }
            }
            for (String line : assembled.visualFindings) {
                if (line != null && !line.trim().isEmpty()) {
                    sb.append("- ").append(line.trim()).append("\n");
                }
            }
        } else {
            if (patternMatches != null && patternMatches.length() > 0) {
                sb.append("- Behavioural pattern register retained in the sealed audit report only.\n");
            }
            if (vulnerabilityMatches != null && vulnerabilityMatches.length() > 0) {
                sb.append("- Vulnerability-linked pattern register retained in the sealed audit report only.\n");
            }
        }
        if (truthContinuityAnalysis != null) {
            String overallAssessment = truthContinuityAnalysis.optString("overallAssessment", "").trim();
            if (!overallAssessment.isEmpty()) {
                sb.append("- Record continuity: ").append(overallAssessment).append(".\n");
            }
        }
    }

    private void appendHumanVerifiedFindingsSection(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report
    ) {
        if (appendCertifiedFindingEntries(sb, report, 10, false)) {
            sb.append("Technical certification detail remains in the appendix and the sealed audit report.\n");
            return;
        }
        if (normalizedCertifiedFindingCount(report) > 0) {
            sb.append("Guardian-approved certified findings were present in this run, but they did not resolve into a clean human-readable certified block. Cross-check the sealed audit report while the rendering path is repaired.\n");
            return;
        }
        if (report == null || !isGuardianApproved(report)) {
            sb.append("No guardian-approved certified findings survived the guardian gate.\n");
            return;
        }
        sb.append("No guardian-approved certified finding block was available for rendering in this pass.\n");
    }

    private void appendCertifiedFindingRegisterSection(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            int limit
    ) {
        if (report == null || normalizedCertifiedFindingCount(report) == 0) {
            return;
        }
        sb.append("\n3A. Certified Finding Blocks\n");
        if (!appendCertifiedFindingEntries(sb, report, limit, true)) {
            sb.append("No guardian-approved certified findings were available for rendering in this pass.\n");
        }
    }

    private boolean appendCertifiedFindingEntries(
            StringBuilder sb,
            AnalysisEngine.ForensicReport report,
            int limit,
            boolean includeCertification
    ) {
        ForensicReportAssembler.Assembly assembled = ForensicReportAssembler.assemble(report);
        if (assembled.issueGroups.isEmpty()) {
            return false;
        }
        int emitted = 0;
        for (ForensicReportAssembler.IssueCard issue : assembled.issueGroups) {
            if (emitted >= limit) {
                break;
            }
            String line = issue.toTechnicalLine();
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            sb.append("- CERTIFIED | ").append(line).append("\n");
            if (!issue.whyItMatters.isEmpty()) {
                sb.append("  Why this matters: ").append(issue.whyItMatters).append("\n");
            }
            if (includeCertification) {
                sb.append("  Publication status: guardian-approved certified finding");
                if (issue.confidence != null && !issue.confidence.trim().isEmpty()) {
                    sb.append(" | contradiction posture: ").append(issue.confidence.trim());
                }
                sb.append("\n");
            }
            emitted++;
        }
        return emitted > 0;
    }

    private int countRenderableCertifiedFindings(AnalysisEngine.ForensicReport report) {
        return normalizedCertifiedFindingCount(report);
    }

    private void appendHumanOpenIssuesSection(
            StringBuilder sb,
            List<JSONObject> openIssues,
            boolean guardianApproved,
            boolean indeterminateDueToConcealment
    ) {
        sb.append("- Not yet certified findings remain separate from the verified or certified case theory.\n");
        if (!guardianApproved) {
            sb.append("- Guardian review has not yet promoted a full final finding set in this pass.\n");
        }
        if (indeterminateDueToConcealment) {
            sb.append("- Concealment, OCR gaps, or missing surrounding material still affect parts of the record.\n");
        }
        if (openIssues == null || openIssues.isEmpty()) {
            sb.append("- No additional candidate issue needed to be carried into this section in this pass.\n");
            return;
        }
        LinkedHashSet<String> rendered = new LinkedHashSet<>();
        for (JSONObject item : sortByNarrativePriority(openIssues)) {
            if (item == null || rendered.size() >= 6) {
                continue;
            }
            String line = humanSummaryLine(item, true);
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            if (rendered.add(line.trim())) {
                sb.append("- Needs review: ")
                        .append(line.trim())
                        .append("\n");
            }
        }
        if (rendered.isEmpty()) {
            sb.append("- Candidate material exists in the audit record but did not compress into a clean human-facing issue line.\n");
        }
    }

    private List<JSONObject> certifiedFindingNarratives(AnalysisEngine.ForensicReport report, int limit) {
        List<JSONObject> findings = new ArrayList<>();
        JSONArray normalized = renderableCertifiedFindings(report);
        if (normalized == null) {
            return findings;
        }
        for (int i = 0; i < normalized.length() && findings.size() < limit; i++) {
            JSONObject certified = normalized.optJSONObject(i);
            if (!isRenderableCertifiedEntry(certified)) {
                continue;
            }
            JSONObject finding = certified.optJSONObject("finding");
            if (finding == null) {
                finding = new JSONObject();
            } else {
                try {
                    finding = new JSONObject(finding.toString());
                } catch (JSONException ignored) {
                }
            }
            if (finding.length() == 0) {
                continue;
            }
            if (safeValue(finding.optString("summary", "")).isEmpty()) {
                try {
                    finding.put("summary", certified.optString("primarySummary", ""));
                } catch (JSONException ignored) {
                }
            }
            if (finding.optInt("page", 0) <= 0 && certified.optInt("primaryPage", 0) > 0) {
                try {
                    finding.put("page", certified.optInt("primaryPage", 0));
                } catch (JSONException ignored) {
                }
            }
            try {
                finding.put("status", "CERTIFIED");
            } catch (JSONException ignored) {
            }
            findings.add(finding);
        }
        return findings;
    }

    private String buildCertifiedFindingNarrative(JSONObject finding) {
        if (finding == null) {
            return "";
        }
        String corpus = buildHumanNarrativeCorpus(finding);
        if (isSupportOnlyHumanNarrative(corpus) && !containsHardForensicHumanSignal(corpus)) {
            return "";
        }
        String conflictType = finding.optString("conflictType", "");
        if ("PROPOSITION_CONFLICT".equalsIgnoreCase(conflictType)
                || "INTER_ACTOR_CONFLICT".equalsIgnoreCase(conflictType)
                || "NEGATION".equalsIgnoreCase(conflictType)
                || "NUMERIC".equalsIgnoreCase(conflictType)
                || "TIMELINE".equalsIgnoreCase(conflictType)
                || "LOCATION".equalsIgnoreCase(conflictType)) {
            String line = resolveContradictionToNarrative(finding);
            if (line != null && !line.trim().isEmpty()) {
                return line;
            }
        }
        String line = humanSummaryLine(finding, true);
        if (line != null && !line.trim().isEmpty()
                && isSupportOnlyHumanNarrative(line)
                && !containsHardForensicHumanSignal(line)) {
            return "";
        }
        if (line != null && !line.trim().isEmpty()) {
            if (!isUsableHumanFindingLine(finding, line)) {
                return "";
            }
            return line;
        }
        String label = finding.optString("findingType", finding.optString("timelineType", finding.optString("oversightType", "CERTIFIED_FINDING"))).trim();
        String excerpt = finding.optString("summary", finding.optString("excerpt", "")).trim();
        if (excerpt.isEmpty()) {
            return label;
        }
        String fallback = label + " | " + excerpt;
        return isUsableHumanFindingLine(finding, fallback) ? fallback : "";
    }

    private boolean isUsableHumanFindingLine(JSONObject finding, String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        String trimmed = line.trim();
        String lower = lowerUs(trimmed);
        if (containsAny(lower,
                "unpaid amount of r 01",
                "invoice non-payment pattern involving an unpaid amount of r 01",
                "his is linked",
                "forced is linked",
                "clause is linked",
                "section is linked",
                "survived the primary extraction filter",
                "<liamhigh78")) {
            return false;
        }
        String actor = "";
        if (finding != null) {
            actor = canonicalizeHumanVictimDisplay(finding.optString("actor", "").trim());
        }
        if (actor.isEmpty()) {
            int marker = lower.indexOf(" is linked");
            if (marker > 0) {
                actor = trimmed.substring(0, marker).trim();
            }
        }
        if (!actor.isEmpty() && (isDiscardedHumanActorName(actor) || looksLikeAbstractHarmedParty(actor))) {
            return false;
        }
        return true;
    }

    private boolean isGuardianApproved(AnalysisEngine.ForensicReport report) {
        if (report == null || report.guardianDecision == null) {
            return false;
        }
        JSONObject guardianDecision = report.guardianDecision;
        if (guardianDecision.has("approved")) {
            return guardianDecision.optBoolean("approved", false);
        }
        String decision = guardianDecision.optString("decision", "").trim();
        return "CERTIFY".equalsIgnoreCase(decision)
                || "APPROVE".equalsIgnoreCase(decision)
                || "APPROVED".equalsIgnoreCase(decision);
    }

    private String guardianDecisionReason(AnalysisEngine.ForensicReport report) {
        if (report == null || report.guardianDecision == null) {
            return "";
        }
        return report.guardianDecision.optString(
                "reason",
                report.guardianDecision.optString("error", "")
        ).trim();
    }

    private void appendExcludedSecondaryNarrativeSection(
            StringBuilder sb,
            JSONObject extraction,
            JSONObject nativeEvidence
    ) {
        LinkedHashSet<String> excluded = new LinkedHashSet<>();
        collectExcludedSecondaryNarratives(excluded, extraction != null ? extraction.optJSONArray("documentIntegrityFindings") : null, 4);
        collectExcludedSecondaryNarratives(excluded, extraction != null ? extraction.optJSONArray("narrativeThemeRegister") : null, 4);
        collectExcludedSecondaryNarratives(excluded, nativeEvidence != null ? nativeEvidence.optJSONArray("documentTextBlocks") : null, 4);
        if (excluded.isEmpty()) {
            sb.append("No synthetic or secondary narrative material was quarantined in this pass.\n");
            return;
        }
        for (String line : excluded) {
            sb.append("- ").append(line).append("\n");
        }
    }

    private void collectExcludedSecondaryNarratives(
            LinkedHashSet<String> excluded,
            JSONArray entries,
            int limit
    ) {
        if (entries == null) {
            return;
        }
        for (int i = 0; i < entries.length() && excluded.size() < limit; i++) {
            JSONObject item = entries.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String combined = firstNonEmpty(
                    item.optString("summary", null),
                    item.optString("excerpt", null),
                    item.optString("text", null),
                    item.optString("label", null),
                    item.optString("type", null)
            );
            if (looksLikeSealedSourceWrapper(combined)) {
                continue;
            }
            if (!isSecondaryNarrativeEvidence(combined)) {
                continue;
            }
            String excerpt = item.optString(
                    "summary",
                    item.optString("excerpt", item.optString("text", ""))
            ).trim();
            excerpt = clipText(excerpt, 180);
            int page = item.optInt("page", 0);
            if (page > 0) {
                excluded.add("Page " + page + ": " + (excerpt.isEmpty()
                        ? "Secondary narrative block quarantined from the primary evidence sections."
                        : excerpt));
            } else {
                excluded.add(excerpt.isEmpty()
                        ? "Secondary narrative block quarantined from the primary evidence sections."
                        : excerpt);
            }
        }
    }

    private boolean looksLikeSealedSourceWrapper(String text) {
        if (text == null) {
            return false;
        }
        String lower = lowerUs(text);
        return lower.contains("verum omnis sealed evidence source:")
                || lower.contains("page 1 of")
                || lower.contains("page 2 of")
                || lower.contains("page 3 of");
    }

    private String buildCombinedHumanEvidenceText(JSONObject item) {
        if (item == null) {
            return "";
        }
        return (
                item.optString("summary", "")
                        + " "
                        + item.optString("excerpt", "")
                        + " "
                        + item.optString("text", "")
                        + " "
                        + item.optString("label", "")
                        + " "
                        + item.optString("type", "")
                        + " "
                        + item.optString("subject", "")
        ).replaceAll("\\s+", " ").trim();
    }

    private void appendCertificationBlock(StringBuilder sb, JSONObject certification) {
        if (certification == null || certification.length() == 0) {
            return;
        }
        sb.append("  Certification\n");
        sb.append("  - Constitution: ")
                .append(shortHash(certification.optString("constitutionHash", "")))
                .append(" | Rules: ")
                .append(certification.optString("rulePackVersion", "unknown"))
                .append(" | Engine: ")
                .append(certification.optString("engineVersion", "unknown"))
                .append("\n");
        sb.append("  - Run ID: ")
                .append(certification.optString("deterministicRunId", "unknown"))
                .append(" | Bundle: ")
                .append(shortHash(certification.optString("evidenceBundleHash", "")))
                .append("\n");
        sb.append("  - Finding hash: ")
                .append(shortHash(certification.optString("findingHash", "")))
                .append(" | Promotion hash: ")
                .append(shortHash(certification.optString("promotionHash", "")))
                .append("\n");
        sb.append("  - Guardian: ")
                .append(certification.optBoolean("guardianApproval", false) ? "Approved" : "Denied");
        String guardianReason = certification.optString("guardianReason", "").trim();
        if (!guardianReason.isEmpty()) {
            sb.append(" | ").append(guardianReason);
        }
        sb.append("\n");
        String reproducibility = certification.optString("reproducibilityStatement", "").trim();
        if (!reproducibility.isEmpty()) {
            sb.append("  - Reproducibility: ").append(reproducibility).append("\n");
        }
    }

    private String shortHash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.length() <= 12 ? trimmed : trimmed.substring(0, 12) + "...";
    }

    private void appendHumanVisualIntegritySection(
            StringBuilder sb,
            List<JSONObject> primaryVisualFindings
    ) {
        if (primaryVisualFindings.isEmpty()) {
            sb.append("- No visual forgery or tamper indicator survived the primary-evidence filter in this pass.\n");
            return;
        }
        LinkedHashSet<String> seenTypes = new LinkedHashSet<>();
        int emitted = 0;
        for (JSONObject item : sortByNarrativePriority(primaryVisualFindings)) {
            if (emitted >= 6) {
                break;
            }
            String type = item.optString("label", item.optString("type", "")).trim();
            if (type.isEmpty()) {
                type = item.optString("summary", "").trim();
            }
            String dedupeKey = type.toUpperCase(Locale.US);
            if (!dedupeKey.isEmpty() && !seenTypes.add(dedupeKey)) {
                continue;
            }
            String line = humanSummaryLine(item, true);
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            sb.append("- ").append(line).append("\n");
            emitted++;
        }
        if (emitted == 0) {
            sb.append("- No visual forgery or tamper indicator survived the primary-evidence filter in this pass.\n");
        }
    }

    private void appendHumanSignatureOcrCaution(
            StringBuilder sb,
            List<JSONObject> primaryVisualFindings,
            int ocrSuccess,
            int ocrFailed
    ) {
        if (sb == null || primaryVisualFindings == null || primaryVisualFindings.isEmpty()) {
            return;
        }
        LinkedHashSet<String> pages = new LinkedHashSet<>();
        boolean signatureWeak = false;
        for (JSONObject item : primaryVisualFindings) {
            if (item == null) {
                continue;
            }
            String corpus = lowerUs(buildHumanNarrativeCorpus(item));
            if (containsAny(corpus,
                    "signature_region_empty",
                    "signature region empty",
                    "signature_marks_not_found",
                    "signature marks not found")) {
                signatureWeak = true;
                int page = item.optInt("page", 0);
                if (page > 0) {
                    pages.add("p. " + page);
                }
            }
        }
        if (!signatureWeak) {
            return;
        }
        sb.append("- Direct signature-page caution: at least one scanned signature page was visually flagged as empty or lacking strong signature marks");
        if (!pages.isEmpty()) {
            sb.append(" (").append(joinHumanNames(new ArrayList<>(pages))).append(")");
        }
        sb.append(". Treat the unsigned or not-countersigned conclusion as strongest where the visual signature finding and the text record agree; where the page is scan-limited, confirm against the sealed page image itself.\n");
        if (ocrFailed > 0 || ocrSuccess > 0) {
            sb.append("- OCR note: this run included rendered/OCR processing. If a direct signature page is visually important, the sealed page image should be checked alongside the extracted text before external use.\n");
        }
    }

    private List<JSONObject> collectPrimaryHumanEvidence(
            JSONArray source,
            JSONArray namedParties,
            int limit,
            boolean allowVisualSignals
    ) {
        List<JSONObject> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (source == null) {
            return result;
        }

        for (int i = 0; i < source.length(); i++) {
            JSONObject normalized = normalizeHumanEvidenceItem(source.optJSONObject(i), namedParties, allowVisualSignals);
            if (normalized == null) {
                continue;
            }
            if (!allowVisualSignals && (isGenericDatedCommunicationItem(normalized) || isDemandOnlyHumanItem(normalized))) {
                continue;
            }
            String key = normalized.optString("summary", "")
                    + "|"
                    + normalized.optString("label", "");
            if (!seen.add(key)) {
                continue;
            }
            result.add(normalized);
        }

        Collections.sort(result, (left, right) -> {
            int priorityCompare = Integer.compare(right.optInt("priority", 0), left.optInt("priority", 0));
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Integer.compare(left.optInt("page", Integer.MAX_VALUE), right.optInt("page", Integer.MAX_VALUE));
        });
        if (result.size() > limit) {
            result = new ArrayList<>(result.subList(0, limit));
        }
        Collections.sort(result, Comparator.comparingInt(item -> item.optInt("page", Integer.MAX_VALUE)));
        return result;
    }

    private JSONObject normalizeHumanEvidenceItem(
            JSONObject source,
            JSONArray namedParties,
            boolean allowVisualSignals
    ) {
        if (source == null) {
            return null;
        }
        boolean sourcePrimaryEvidence = source.optBoolean("primaryEvidence", !source.optBoolean("supportOnly", false));
        if (!allowVisualSignals && source.optBoolean("supportOnly", false)) {
            return null;
        }

        String label = firstNonEmpty(
                source.optString("subject", null),
                source.optString("eventType", null),
                source.optString("conductType", null),
                source.optString("theme", null),
                source.optString("amountCategory", null),
                source.optString("incidentType", null),
                source.optString("category", null),
                source.optString("type", null),
                source.optString("name", null)
        );
        String text = firstNonEmpty(
                source.optString("summary", null),
                source.optString("narrative", null),
                source.optString("whyItMatters", null),
                source.optString("whyItConflicts", null),
                source.optString("excerpt", null),
                source.optString("description", null),
                source.optString("matchedPhrase", null),
                source.optString("amount", null)
        );
        String normalizedLabel = label == null ? "" : label.trim();
        String normalizedText = cleanEvidenceTextForHumanReport(text);
        String combined = (normalizedLabel + " " + normalizedText).trim();
        if (combined.trim().isEmpty()) {
            return null;
        }

        if (isSecondaryNarrativeEvidence(combined)) {
            return null;
        }
        if (!allowVisualSignals && isPureVisualNoise(label, text)) {
            return null;
        }
        if (!allowVisualSignals && isVisualDiagnosticText(normalizedText)) {
            return null;
        }
        if (!allowVisualSignals && !isReadableHumanEvidence(normalizedText)) {
            return null;
        }
        if (!allowVisualSignals
                && !sourcePrimaryEvidence
                && ("COMPLAINT_ESCALATION".equalsIgnoreCase(normalizedLabel)
                || "LAW_ENFORCEMENT_NOTICE".equalsIgnoreCase(normalizedLabel)
                || "MANDATE_OR_AUTHORITY".equalsIgnoreCase(normalizedLabel)
                || "NOTICE_AND_ESCALATION".equalsIgnoreCase(normalizedLabel)
                || "AUTHORITY_AND_STANDING".equalsIgnoreCase(normalizedLabel)
                || "DATED_COMMUNICATION".equalsIgnoreCase(normalizedLabel))) {
            return null;
        }

        JSONObject item = new JSONObject();
        try {
            String actor = resolveHumanEvidenceActor(source, namedParties, combined);
            if (allowVisualSignals) {
                actor = "unresolved actor";
            }
        if (!allowVisualSignals
                && "FINANCIAL_POSITION".equalsIgnoreCase(normalizedLabel)
                && !source.has("amountCategory")) {
            return null;
        }
        if (!allowVisualSignals
                && source.has("amountCategory")
                && !isStructuredFinancialEvidenceItem(source)) {
            return null;
        }
        if (!allowVisualSignals
                && "unresolved actor".equalsIgnoreCase(actor)
                && (normalizedLabel.toUpperCase(Locale.US).contains("FINANCIAL")
                || "DATED_COMMUNICATION".equalsIgnoreCase(normalizedLabel))
                && startsWithDiscardedEvidenceLead(normalizedText)) {
                return null;
            }
            int priority = humanEvidencePriority(normalizedLabel, normalizedText, actor);
            if ("VERIFIED".equalsIgnoreCase(source.optString("status", ""))) {
                priority += 8;
            }
            if (sourcePrimaryEvidence) {
                priority += 10;
            }
            if (!allowVisualSignals && priority < -2) {
                return null;
            }
            item.put("label", normalizedLabel);
            item.put("text", normalizedText);
            item.put("actor", isAllowedHumanActor(actor, namedParties) ? actor : "unresolved actor");
            int page = source.optInt("page", firstPageFromArray(source.optJSONArray("pages")));
            if (page > 0) {
                item.put("page", page);
            }
            item.put("status", source.optString("status", ""));
            item.put("priority", priority);
            item.put("primaryEvidence", sourcePrimaryEvidence);
            item.put("eventType", source.optString("eventType", ""));
            item.put("amountCategory", source.optString("amountCategory", ""));
            item.put("category", source.optString("category", ""));
            item.put("summary", summarizeHumanEvidence(normalizedLabel, normalizedText, actor, namedParties));
        } catch (JSONException e) {
            return null;
        }
        return item;
    }

    private List<JSONObject> collectPrimaryHumanEvidenceByStatus(
            JSONArray source,
            JSONArray namedParties,
            int limit,
            boolean allowVisualSignals,
            String requiredStatus
    ) {
        List<JSONObject> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (source == null || requiredStatus == null) {
            return result;
        }

        for (int i = 0; i < source.length(); i++) {
            JSONObject raw = source.optJSONObject(i);
            if (raw == null || !requiredStatus.equalsIgnoreCase(raw.optString("status", ""))) {
                continue;
            }
            JSONObject normalized = normalizeHumanEvidenceItem(raw, namedParties, allowVisualSignals);
            if (normalized == null) {
                continue;
            }
            if (!allowVisualSignals && (isGenericDatedCommunicationItem(normalized) || isDemandOnlyHumanItem(normalized))) {
                continue;
            }
            String key = normalized.optString("summary", "")
                    + "|"
                    + normalized.optString("label", "")
                    + "|"
                    + normalized.optString("status", "");
            if (!seen.add(key)) {
                continue;
            }
            result.add(normalized);
        }

        Collections.sort(result, (left, right) -> {
            int priorityCompare = Integer.compare(right.optInt("priority", 0), left.optInt("priority", 0));
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Integer.compare(left.optInt("page", Integer.MAX_VALUE), right.optInt("page", Integer.MAX_VALUE));
        });
        if (result.size() > limit) {
            result = new ArrayList<>(result.subList(0, limit));
        }
        Collections.sort(result, Comparator.comparingInt(item -> item.optInt("page", Integer.MAX_VALUE)));
        return result;
    }

    private boolean isSecondaryNarrativeEvidence(String text) {
        if (text == null) {
            return false;
        }
        String lower = lowerUs(text);
        return lower.contains("verum omnis")
                || lower.contains("constitutional forensic report")
                || lower.contains("executive case status report")
                || lower.contains("forensic seal")
                || lower.contains("structural repair declaration")
                || lower.contains("generated:")
                || lower.contains("report id")
                || lower.contains("master forensic archive")
                || lower.contains("founders archive")
                || lower.contains("checksum summary")
                || lower.contains("certification & forensic seal")
                || (lower.startsWith("section ") && lower.contains("this section"))
                || lower.contains("this section contains")
                || lower.contains("this section includes")
                || lower.contains("prepared for rakez")
                || lower.contains("relevant legal violations")
                || lower.contains("received this message in error")
                || lower.contains("mimecast")
                || lower.contains("company secretarial administrator")
                || lower.contains("additional evidence –")
                || lower.contains("executive summary – chronology")
                || lower.contains("final settlement offer")
                || lower.contains("section 14 –")
                || lower.contains("section 13 –")
                || lower.contains("section 12 –")
                || lower.contains("section 11 -")
                || lower.contains("section 11 –")
                || lower.contains("please ensure this judgment")
                || lower.contains("the public deserves to know")
                || lower.contains("i am making this correspondence public")
                || lower.contains("silence in the face of proof is complicity")
                || lower.contains("fraud docket")
                || lower.contains("fraudulent evidence")
                || lower.contains("emotional exploitation");
    }

    private boolean isPureVisualNoise(String label, String text) {
        String safeLabel = label == null ? "" : label.toUpperCase(Locale.US);
        String safeText = text == null ? "" : text.toLowerCase(Locale.US);
        if (safeLabel.contains("SIGNATURE_REGION_EMPTY")
                || safeLabel.contains("SIGNATURE REGION EMPTY")) {
            return false;
        }
        return (safeLabel.contains("POSSIBLE_OVERLAY_REGION")
                || safeLabel.contains("VISUAL_SIGNATURE_REVIEW")
                || safeLabel.contains("SIGNATURE_MARKS_PRESENT")
                || safeLabel.contains("SIGNATURE MARKS PRESENT")
                || safeLabel.contains("SIGNATURE_MARKS_NOT_FOUND")
                || safeLabel.contains("SIGNATURE MARKS NOT FOUND")
                || safeLabel.contains("FLATTENED OR BLURRED CONTENT")
                || safeLabel.contains("SIGNATURE ZONE OVERLAY SUSPECTED"))
                && !(safeText.contains("forg") || safeText.contains("tamper") || safeText.contains("cropped"));
    }

    private String resolveHumanEvidenceActor(JSONObject source, JSONArray namedParties, String combinedText) {
        String extractedActor = extractActorFromSenderLine(combinedText, namedParties);
        if (!extractedActor.isEmpty()) {
            return extractedActor;
        }
        String actor = source.optString("actor", "").trim();
        if (!actor.isEmpty()
                && !"unresolved actor".equalsIgnoreCase(actor)
                && isAllowedHumanActor(actor, namedParties)) {
            return actor;
        }
        extractedActor = extractActorFromEvidenceText(combinedText, namedParties);
        if (!extractedActor.isEmpty()) {
            return extractedActor;
        }
        return actor.isEmpty() ? "unresolved actor" : actor;
    }

    private String extractActorFromSenderLine(String text, JSONArray namedParties) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        String lower = lowerUs(text);
        int fromIndex = lower.indexOf("from:");
        if (fromIndex >= 0) {
            int endIndex = Math.min(text.length(), fromIndex + 180);
            String slice = text.substring(fromIndex, endIndex);
            String actor = extractActorFromEvidenceText(slice, namedParties);
            if (!actor.isEmpty()) {
                return actor;
            }
        }
        return "";
    }

    private String extractActorFromEvidenceText(String text, JSONArray namedParties) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        Matcher quotedAuthor = HUMAN_QUOTED_AUTHOR_PATTERN.matcher(text);
        while (quotedAuthor.find()) {
            String candidate = quotedAuthor.group(1).trim();
            if (isAllowedHumanActor(candidate, namedParties)) {
                return candidate;
            }
        }
        Matcher namedEmail = HUMAN_EMAIL_NAME_PATTERN.matcher(text);
        while (namedEmail.find()) {
            String candidate = namedEmail.group(1).trim();
            if (isAllowedHumanActor(candidate, namedParties)) {
                return candidate;
            }
        }

        String lower = lowerUs(text);
        if (namedParties != null) {
            for (int i = 0; i < namedParties.length(); i++) {
                JSONObject party = namedParties.optJSONObject(i);
                if (party == null) {
                    continue;
                }
                String name = party.optString("name", "").trim();
                if (name.isEmpty() || !isAllowedHumanActor(name, namedParties)) {
                    continue;
                }
                String nameLower = lowerUs(name);
                if (lower.contains(nameLower)) {
                    return name;
                }
                String[] parts = nameLower.split("\\s+");
                String first = parts.length > 0 ? parts[0] : "";
                String last = parts.length > 1 ? parts[parts.length - 1] : "";
                if ((!first.isEmpty() && first.length() >= 4 && (lower.contains(first + "@") || lower.contains(first + " ")))
                        || (!last.isEmpty() && last.length() >= 4 && (lower.contains(last + "@") || lower.contains(last + " ")))) {
                    return name;
                }
            }
        }

        Matcher emailMatcher = HUMAN_EMAIL_PATTERN.matcher(text);
        while (emailMatcher.find()) {
            String local = lowerUs(emailMatcher.group(1));
            if (namedParties != null) {
                for (int i = 0; i < namedParties.length(); i++) {
                    JSONObject party = namedParties.optJSONObject(i);
                    if (party == null) {
                        continue;
                    }
                    String name = party.optString("name", "").trim();
                    if (name.isEmpty() || !isAllowedHumanActor(name, namedParties)) {
                        continue;
                    }
                    String[] parts = lowerUs(name).split("\\s+");
                    String first = parts.length > 0 ? parts[0] : "";
                    String last = parts.length > 1 ? parts[parts.length - 1] : "";
                    if ((!first.isEmpty() && first.length() >= 4 && local.contains(first))
                            || (!last.isEmpty() && last.length() >= 4 && local.contains(last))) {
                        return name;
                    }
                }
            }
            if (local.contains("kevin") && isAllowedHumanActor("Kevin Lappeman", namedParties)) {
                return "Kevin Lappeman";
            }
            if (local.contains("marius") && isAllowedHumanActor("Marius Nortje", namedParties)) {
                return "Marius Nortje";
            }
            if (local.contains("liam")) {
                if (isAllowedHumanActor("Liam Harcock", namedParties)) {
                    return "Liam Harcock";
                }
                if (isAllowedHumanActor("Liam Highcock", namedParties)) {
                    return "Liam Highcock";
                }
            }
        }
        return "";
    }

    private String cleanEvidenceTextForHumanReport(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text
                .replace("Confidential – RAKEZ Case #1295911", " ")
                .replace("Confidential - RAKEZ Case #1295911", " ")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        cleaned = cleaned.replaceAll("(?i)^old woman angelfish\\s+", "");
        cleaned = cleaned.replaceAll("(?i)^harlequin anthias\\s+", "");
        cleaned = cleaned.replaceAll("(?i)^hong kong\\s+", "");
        cleaned = cleaned.replaceAll("(?i)^pty ltd\\s+", "");
        cleaned = cleaned.replaceAll("(?i)^additional evidence\\s*[–-]\\s*", "");
        cleaned = cleaned.replaceAll("(?i)^section\\s+\\d+\\s*[–-]\\s*", "");
        cleaned = cleaned.replaceAll("(?i)^from:\\s*", "");
        cleaned = cleaned.replaceAll("(?i)\\bfrom:\\s*[^\\s]+@[^\\s]+", "");
        cleaned = cleaned.replaceAll("(?i)\\bto:\\s*[^\\s]+@[^\\s]+", "");
        cleaned = cleaned.replaceAll("(?i)\\bsent:\\s+[A-Za-z]+,?\\s+\\d{1,2}\\s+[A-Za-z]+.*?$", "");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private boolean startsWithDiscardedEvidenceLead(String text) {
        if (text == null) {
            return false;
        }
        String lower = lowerUs(trimToEmpty(text));
        return lower.startsWith("old woman angelfish")
                || lower.startsWith("harlequin anthias")
                || lower.startsWith("hong kong")
                || lower.startsWith("pty ltd")
                || lower.startsWith("general manager")
                || lower.startsWith("support team")
                || lower.startsWith("company secretarial administrator");
    }

    private boolean isReadableHumanEvidence(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        if (text.contains("(cid:127)")) {
            return false;
        }
        String[] rawTokens = text.split("\\s+");
        int candidates = 0;
        int readable = 0;
        for (String raw : rawTokens) {
            String token = raw.replaceAll("[^A-Za-z]", "");
            if (token.length() < 4) {
                continue;
            }
            candidates++;
            String lower = token.toLowerCase(Locale.US);
            boolean hasVowel = lower.matches(".*[aeiouy].*");
            boolean longConsonantRun = lower.matches(".*[^aeiouy]{5,}.*");
            if (hasVowel && !longConsonantRun) {
                readable++;
            }
            if (candidates >= 24) {
                break;
            }
        }
        if (candidates < 4) {
            return true;
        }
        return ((double) readable / (double) candidates) >= 0.45d;
    }

    private boolean isVisualDiagnosticText(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.US);
        return lower.contains("low edge density")
                || lower.contains("flattened image content")
                || lower.contains("expected signature zone")
                || lower.contains("signature zone")
                || lower.contains("stroke density")
                || lower.contains("visual review completed")
                || lower.contains("possible overlays")
                || lower.contains("whiteout");
    }

    private int humanEvidencePriority(String label, String text, String actor) {
        String safeLabel = label == null ? "" : label.toLowerCase(Locale.US);
        String safeText = text == null ? "" : text.toLowerCase(Locale.US);
        int score = 0;
        if (actor != null && !actor.trim().isEmpty() && !"unresolved actor".equalsIgnoreCase(actor) && !isDiscardedHumanActorName(actor)) {
            score += 4;
        }
        if (HUMAN_DATE_PATTERN.matcher(text == null ? "" : text).find()) {
            score += 4;
        }
        if (safeText.contains("@") || safeText.contains(" from: ") || safeText.contains(" sent: ") || safeText.contains(" wrote: ")) {
            score += 3;
        }
        if (containsAny(safeText,
                "invoice", "deal", "shareholder", "private meeting", "meeting", "screenshot", "whatsapp",
                "forg", "manipulat", "google", "archive", "scaquaculture", "sealife", "profit", "payment",
                "proceeded with the deal", "export", "countersigned", "lease", "goodwill", "rent", "upgrade",
                "vacate", "hawks", "saps", "lpc", "complaint", "precca", "termination notice", "completed order",
                "selective screenshots", "must i do the marketing", "client confirmation", "hong kong",
                "kevin's export proceeded")) {
            score += 5;
        }
        if (safeLabel.contains("communication event") || safeLabel.contains("timeline")
                || safeLabel.contains("conduct") || safeLabel.contains("financial")
                || safeLabel.contains("execution") || safeLabel.contains("integrity")
                || safeLabel.contains("reconciliation")) {
            score += 2;
        }
        if (safeLabel.contains("fraud") || safeLabel.contains("cyber") || safeLabel.contains("shareholder")
                || safeLabel.contains("fiduciary") || safeLabel.contains("financial")
                || safeLabel.contains("goodwill") || safeLabel.contains("rent")
                || safeLabel.contains("reconciliation")
                || safeLabel.contains("countersignature")) {
            score += 3;
        }
        if (containsAny(safeText, "proceeded with the deal", "thanks for the invoice", "thank you for the invoice",
                "resolution of order issues", "private meeting", "sealife", "whatsapp", "screenshot",
                "no countersigned", "never countersigned", "never signed back", "vacate the premises",
                "profit-share reconciliation gap", "unpaid share", "issued a termination notice",
                "completed order", "selective screenshots", "attack call", "must i do the marketing",
                "client confirmation", "hong kong", "kevin's export proceeded",
                "goodwill", "unlawful rent")) {
            score += 12;
        }
        if (safeText.startsWith("section ") || safeText.contains("this section contains") || safeText.contains("this section includes")) {
            score -= 6;
        }
        if (safeText.contains("received this message in error") || safeText.contains("mimecast")) {
            score -= 6;
        }
        if (containsAny(safeText, "civil compensation claim", "payment must reflect", "standard bank account",
                "deadline", "final reminder", "settlement", "dear kevin")) {
            score -= 10;
        }
        if (containsAny(safeText,
                "signature region empty",
                "signature marks not found",
                "missing signature block")) {
            score += 12;
        }
        if (isVisualDiagnosticText(text)) {
            score -= 10;
        }
        if (!isReadableHumanEvidence(text)) {
            score -= 8;
        }
        return score;
    }

    private List<String> collectHumanKeyFacts(
            List<JSONObject> primaryTimeline,
            List<JSONObject> primarySubjectFindings,
            int limit
    ) {
        LinkedHashSet<String> facts = new LinkedHashSet<>();
        addHumanKeyFacts(facts, sortByNarrativePriority(primaryTimeline), limit);
        addHumanKeyFacts(facts, sortByNarrativePriority(primarySubjectFindings), limit);
        return new ArrayList<>(facts);
    }

    private void addHumanKeyFacts(Set<String> facts, List<JSONObject> items, int limit) {
        if (items == null) {
            return;
        }
        for (JSONObject item : items) {
            if (shouldSuppressExecutiveEvidence(item)) {
                continue;
            }
            if (facts.size() >= limit) {
                return;
            }
            String line = humanSummaryLine(item, true);
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            facts.add(line);
        }
    }

    private String buildHumanOffenceLine(
            String label,
            JSONObject subject,
            List<JSONObject> primaryTimeline
    ) {
        if (label == null || label.trim().isEmpty()) {
            return "";
        }
        String exposure = legalExposureForLabel(label);
        JSONObject supporting = findSupportingTimelineEvidence(label, primaryTimeline);
        String supportLine = supporting != null ? humanSummaryLine(supporting, true) : humanSummaryLine(subject, true);
        if (supportLine == null || supportLine.trim().isEmpty()) {
            supportLine = humanizeEvidenceLine(subject, false);
        }
        if (supportLine == null || supportLine.trim().isEmpty()) {
            return label + ": " + exposure + ".";
        }
        return label + ": " + exposure + ". Anchor-backed support: " + supportLine;
    }

    private JSONObject findSupportingTimelineEvidence(String label, List<JSONObject> primaryTimeline) {
        if (primaryTimeline == null || primaryTimeline.isEmpty()) {
            return null;
        }
        String safeLabel = lowerUs(label);
        for (JSONObject item : primaryTimeline) {
            String corpus = (item.optString("summary", "") + " " + item.optString("text", "") + " " + item.optString("label", ""))
                    .toLowerCase(Locale.US);
        if (safeLabel.contains("fraud") && containsAny(corpus, "whatsapp", "screenshot", "forg", "manipulat", "cropped")) {
                return item;
            }
            if (safeLabel.contains("cyber") && containsAny(corpus, "google", "archive", "scaquaculture", "unauthorized", "cyber")) {
                return item;
            }
            if (safeLabel.contains("fiduciary") && containsAny(corpus, "proceeded with the deal", "profit", "shares", "shareholder")) {
                return item;
            }
            if (safeLabel.contains("shareholder") && containsAny(corpus, "private meeting", "shareholder", "excluded", "oppression")) {
                return item;
            }
            if (safeLabel.contains("financial") && containsAny(corpus, "invoice", "payment", "deal", "profit", "loss")) {
                return item;
            }
            if (safeLabel.contains("concealment") && containsAny(corpus, "delete", "missing", "archive", "google", "spoliation")) {
                return item;
            }
        }
        if (safeLabel.contains("fiduciary") || safeLabel.contains("shareholder") || safeLabel.contains("financial")) {
            for (JSONObject item : primaryTimeline) {
                String corpus = (item.optString("summary", "") + " " + item.optString("text", "")).toLowerCase(Locale.US);
                if (containsAny(corpus, "proceeded with the deal", "thanks for the invoice", "resolution of order issues", "private meeting")) {
                    return item;
                }
            }
        }
        return null;
    }

    private String summarizeHumanEvidence(String label, String text, String actor, JSONArray namedParties) {
        String safeText = cleanEvidenceTextForHumanReport(text);
        String resolvedActor = actor;
        if (resolvedActor == null || resolvedActor.trim().isEmpty() || "unresolved actor".equalsIgnoreCase(resolvedActor) || isDiscardedHumanActorName(resolvedActor)) {
            resolvedActor = extractActorFromEvidenceText(safeText, namedParties);
        }
        String safeLabel = label == null ? "" : label.trim();
        String displayLabel = humanDisplayLabel(safeLabel);
        String neutralText = clipReportText(extractNeutralHumanExcerpt(safeText, resolvedActor), 190);
        if (!neutralText.isEmpty()) {
            if (!resolvedActor.isEmpty() && !"unresolved actor".equalsIgnoreCase(resolvedActor)) {
                return resolvedActor + " | " + displayLabel + " | " + neutralText;
            }
            if (!displayLabel.isEmpty()) {
                return displayLabel + " | " + neutralText;
            }
            return neutralText;
        }
        return displayLabel;
    }

    private String humanDisplayLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            return "";
        }
        switch (label.trim()) {
            case "FINANCIAL_POSITION":
            case "FINANCIAL_REFERENCE":
                return "Financial reference";
            case "RENT_EXTRACTION":
                return "Rent reference";
            case "GOODWILL":
                return "Goodwill reference";
            case "PAYMENT_DEMAND":
                return "Payment demand";
            case "UPGRADE_COSTS":
                return "Upgrade-cost reference";
            case "EXTENSION_FEE":
                return "Extension-fee reference";
            case "METADATA_ANOMALY":
            case "CHAIN_OF_CUSTODY_GAP":
            case "SIGNATURE_MISMATCH":
            case "BACKDATING_RISK":
            case "MISSING_COUNTERSIGNATURE":
            case "MISSING_EXECUTION_EVIDENCE":
                return "Document integrity";
            case "LAW_ENFORCEMENT_NOTICE":
            case "NOTICE_AND_ESCALATION":
                return "Notice and escalation";
            case "DATED_COMMUNICATION":
                return "Dated communication";
            default:
                return label.replace('_', ' ').trim();
        }
    }

    private String extractNeutralHumanExcerpt(String text, String actor) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replaceAll("(?i)\\[quoted text hidden\\]", " ")
                .replaceAll("(?i)\\bsubject:\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        String lower = lowerUs(cleaned);
        int fromIndex = lower.indexOf(" from: ");
        if (fromIndex > 20) {
            cleaned = cleaned.substring(0, fromIndex).trim();
        }
        int emailIndex = cleaned.indexOf("@");
        if (emailIndex > 20) {
            cleaned = cleaned.substring(0, emailIndex).trim();
        }
        if (actor != null && !actor.trim().isEmpty() && !"unresolved actor".equalsIgnoreCase(actor)) {
            String actorLower = lowerUs(actor);
            if (lowerUs(cleaned).startsWith(actorLower + " ")) {
                cleaned = cleaned.substring(actor.length()).trim();
            }
        }
        return cleaned.replaceAll("^[-:;,]+", "").trim();
    }

    private String extractHumanEvidenceDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        Matcher matcher = HUMAN_DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group().replaceAll("\\s+", " ").trim();
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && text.contains(lowerUs(needle))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsOnlyVictimSideNarrative(List<JSONObject> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        for (JSONObject item : items) {
            if (item == null) {
                continue;
            }
            String corpus = lowerUs(buildHumanNarrativeCorpus(item));
            if (corpus.isEmpty()) {
                continue;
            }
            if (containsAny(corpus,
                    "signed by",
                    "attempted archive request",
                    "issued a termination notice",
                    "proceeded with the deal",
                    "kevin's export proceeded",
                    "not countersigned",
                    "never countersigned",
                    "withheld",
                    "profit-share",
                    "reconciliation gap")) {
                return false;
            }
        }
        return true;
    }

    private List<JSONObject> sortByNarrativePriority(List<JSONObject> items) {
        List<JSONObject> copy = new ArrayList<>();
        if (items == null) {
            return copy;
        }
        copy.addAll(items);
        Collections.sort(copy, (left, right) -> {
            int compare = Integer.compare(narrativePriority(right), narrativePriority(left));
            if (compare != 0) {
                return compare;
            }
            return Integer.compare(left.optInt("page", Integer.MAX_VALUE), right.optInt("page", Integer.MAX_VALUE));
        });
        return copy;
    }

    private int narrativePriority(JSONObject item) {
        if (item == null) {
            return Integer.MIN_VALUE;
        }
        int score = item.optInt("priority", 0);
        String corpus = lowerUs(buildHumanNarrativeCorpus(item));
        if (containsAny(corpus,
                "24 february 2025", "8 march 2025", "25 march 2025", "6 april 2025",
                "proceeded with the deal", "thanks for the invoice", "thank you for the invoice",
                "resolution of order issues", "private meeting", "sealife", "screenshot", "whatsapp")) {
            score += 18;
        }
        if (containsAny(corpus,
                "fraudulent registration",
                "unlawful charter",
                "stolen vessel",
                "theft of vessel",
                "vessel was stolen",
                "goodwill was stolen",
                "forced off site",
                "permit holder",
                "samsa",
                "dffe",
                "desmond smith",
                "des smith",
                "port edward",
                "umtentweni",
                "r25,706.01",
                "r45,000",
                "r20,000",
                "million")) {
            score += 22;
        }
        if (containsHardForensicHumanSignal(corpus)) {
            score += 14;
        }
        if (isArchiveStructuralNarrative(corpus)) {
            score -= 40;
        }
        if (containsAny(corpus,
                "civil compensation claim", "payment must reflect", "standard bank account",
                "deadline", "final reminder", "settlement", "dear kevin")) {
            score -= 10;
        }
        if (containsAny(corpus,
                "protection order",
                "harassment order",
                "harassment application",
                "interdict"))
        {
            score -= containsHardForensicHumanSignal(corpus) ? 4 : 18;
        }
        if (isSupportOnlyHumanNarrative(corpus)) {
            score -= 22;
        }
        return score;
    }

    private String humanSummaryLine(JSONObject item, boolean includePage) {
        if (item == null) {
            return "";
        }
        String summary = trimToEmpty(item.optString("summary", ""));
        if (isUnsafeHumanFinancialAttribution(item)) {
            return "";
        }
        if (isSuppressedHumanFinancialItem(item)) {
            return "";
        }
        if (isGenericDatedCommunicationItem(item)) {
            return "";
        }
        if (summary.isEmpty()) {
            summary = humanizeEvidenceLine(item, false);
        }
        summary = canonicalizeHumanSummaryLead(summary);
        if (summary == null) {
            summary = "";
        }
        if (startsWithDiscardedNarrativeLead(summary)) {
            return "";
        }
        String lowerSummary = lowerUs(summary);
        if (isArchiveStructuralNarrative(lowerSummary)) {
            return "";
        }
        if (containsAny(lowerSummary,
                "survived the primary extraction filter",
                "document integrity | <",
                "<liamhigh78")) {
            return "";
        }
        if (containsAny(lowerSummary, "protection order", "harassment order", "harassment application")
                && !containsHardForensicHumanSignal(lowerSummary)) {
            return "";
        }
        if (isSupportOnlyHumanNarrative(summary) && !containsHardForensicHumanSignal(summary)) {
            return "";
        }
        int page = item.optInt("page", 0);
        if (includePage && page > 0 && !summary.contains("page " + page)) {
            summary = summary + " | page " + page;
        }
        return summary.trim();
    }

    private List<JSONObject> filterMeaningfulHumanTimeline(List<JSONObject> items) {
        List<JSONObject> filtered = new ArrayList<>();
        if (items == null) {
            return filtered;
        }
        for (JSONObject item : items) {
            if (item == null || isGenericDatedCommunicationItem(item) || !item.optBoolean("primaryEvidence", true)) {
                continue;
            }
            if (isArchiveStructuralNarrative(buildHumanNarrativeCorpus(item))) {
                continue;
            }
            if (isSupportOnlyHumanNarrative(buildHumanNarrativeCorpus(item))
                    && !containsHardForensicHumanSignal(buildHumanNarrativeCorpus(item))) {
                continue;
            }
            filtered.add(item);
        }
        return filtered;
    }

    private boolean isGenericDatedCommunicationItem(JSONObject item) {
        if (item == null) {
            return false;
        }
        String label = item.optString("label", "").trim();
        return "DATED_COMMUNICATION".equalsIgnoreCase(label) || "Dated communication".equalsIgnoreCase(label);
    }

    private boolean isDemandOnlyHumanItem(JSONObject item) {
        if (item == null) {
            return false;
        }
        String label = upperUs(item.optString("label", ""));
        String amountCategory = upperUs(item.optString("amountCategory", item.optString("category", "")));
        if ("PAYMENT_DEMAND".equals(amountCategory) || "FINANCIAL_REFERENCE".equals(amountCategory)) {
            return true;
        }
        String summary = lowerUs(item.optString("summary", item.optString("text", "")));
        if (summary.isEmpty()) {
            return false;
        }
        boolean demandLanguage = containsAny(summary,
                "payment-demand reference",
                "payment demand",
                "demand letter",
                "final demand",
                "settlement demand",
                "civil compensation claim",
                "final reminder regarding the civil compensation claim",
                "claiming r150,000",
                "claiming r100,000",
                "seeks r150,000",
                "seeks r100,000");
        boolean amountLanguage = containsAny(summary, "r150,000", "r100,000", " zar");
        return demandLanguage || ((label.contains("NOTICE") || label.contains("ESCALATION") || label.contains("FINANCIAL")) && amountLanguage);
    }

    private boolean isSuppressedHumanFinancialItem(JSONObject item) {
        if (item == null) {
            return false;
        }
        String label = upperUs(item.optString("label", ""));
        String status = item.optString("status", "").trim();
        String summary = lowerUs(item.optString("summary", item.optString("text", "")));
        if (!(label.contains("FINANCIAL") || label.contains("PAYMENT") || label.contains("DEMAND"))) {
            return false;
        }
        if ("VERIFIED".equalsIgnoreCase(status)) {
            return false;
        }
        return isDemandOnlyHumanItem(item) || containsAny(summary,
                "payment-demand reference",
                "payment demand",
                "demand letter",
                "final demand",
                "settlement demand",
                "r150,000",
                "r100,000");
    }

    private String resolveContradictionToNarrative(JSONObject item) {
        if (item == null) {
            return "";
        }
        String status = item.optString("status", "CANDIDATE").trim();
        String confidence = item.optString("confidence", "LOW").trim();
        String actor = item.optString("actor", "unresolved actor").trim();
        String conflictType = item.optString("conflictType", "CONTEXT_REVIEW").trim();
        int page = item.optInt("page", 0);

        if (!"VERIFIED".equalsIgnoreCase(status)) {
            return "";
        }
        if (isJunkContradictionActor(actor)) {
            return "";
        }

        JSONObject propositionA = item.optJSONObject("propositionA");
        JSONObject propositionB = item.optJSONObject("propositionB");
        String propositionAText = propositionA != null ? propositionA.optString("text", "").trim() : "";
        String propositionBText = propositionB != null ? propositionB.optString("text", "").trim() : "";
        int propositionBPage = 0;
        if (propositionB != null) {
            JSONObject anchor = propositionB.optJSONObject("anchor");
            if (anchor != null) {
                propositionBPage = anchor.optInt("page", 0);
            }
        }

        if (!propositionAText.isEmpty() && !propositionBText.isEmpty()) {
            return "The sealed record ties " + actor + " to a verified " + humanizeConflictType(conflictType)
                    + ": \"" + clipText(propositionAText, 120) + "\" cannot stand with \""
                    + clipText(propositionBText, 120) + "\""
                    + formatPagePair(page, propositionBPage)
                    + " (" + confidence + ").";
        }
        String line = humanSummaryLine(item, true);
        if (!line.isEmpty()) {
            return line + " (" + confidence + ").";
        }
        return "";
    }

    private boolean isJunkContradictionActor(String actor) {
        if (actor == null) {
            return true;
        }
        String trimmed = actor.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        if ("unresolved actor".equalsIgnoreCase(trimmed)) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.US);
        return lower.equals("the")
                || lower.equals("you")
                || lower.equals("april")
                || lower.equals("all")
                || lower.equals("date")
                || lower.equals("fraud")
                || lower.equals("this")
                || lower.equals("march")
                || lower.equals("page")
                || lower.equals("it")
                || lower.equals("that")
                || lower.equals("there")
                || lower.equals("here")
                || lower.equals("now")
                || lower.equals("then")
                || isDiscardedHumanActorName(trimmed);
    }

    private String humanizeConflictType(String conflictType) {
        if (conflictType == null || conflictType.trim().isEmpty()) {
            return "conflict";
        }
        return conflictType.toLowerCase(Locale.US).replace('_', ' ');
    }

    private String formatPagePair(int pageA, int pageB) {
        if (pageA > 0 && pageB > 0 && pageA != pageB) {
            return " (pages " + pageA + " and " + pageB + ")";
        }
        if (pageA > 0) {
            return " (page " + pageA + ")";
        }
        return "";
    }

    private String joinHumanSummaries(List<JSONObject> items, int limit) {
        List<String> snippets = new ArrayList<>();
        for (JSONObject item : items) {
            String line = humanSummaryLine(item, true);
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            if (!snippets.contains(line)) {
                snippets.add(line);
            }
            if (snippets.size() >= limit) {
                break;
            }
        }
        return snippets.isEmpty() ? "no clear actor-linked act stated" : joinStringList(snippets);
    }

    private List<JSONObject> filterActorSpecificHumanEvidence(String actor, List<JSONObject> items) {
        List<JSONObject> filtered = new ArrayList<>();
        if (items == null) {
            return filtered;
        }
        for (JSONObject item : items) {
            if (item == null) {
                continue;
            }
            if (isUnsafeHumanFinancialAttributionForActor(actor, item)) {
                continue;
            }
            String line = humanSummaryLine(item, true);
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            filtered.add(item);
        }
        return filtered;
    }

    private String canonicalizeHumanSummaryLead(String summary) {
        if (summary == null) {
            return "";
        }
        String trimmed = summary.trim();
        if (trimmed.startsWith("Des |")) {
            return "Desmond Smith |" + trimmed.substring("Des |".length());
        }
        if (trimmed.startsWith("Wayne |")) {
            return "Wayne Nel |" + trimmed.substring("Wayne |".length());
        }
        if (trimmed.startsWith("Your Dad |")) {
            return "Gary Highcock |" + trimmed.substring("Your Dad |".length());
        }
        return trimmed;
    }

    private boolean startsWithDiscardedNarrativeLead(String summary) {
        if (summary == null) {
            return false;
        }
        String trimmed = summary.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        int cut = trimmed.length();
        int pipeIndex = trimmed.indexOf('|');
        int isIndex = trimmed.indexOf(" is ");
        int colonIndex = trimmed.indexOf(':');
        if (pipeIndex > 0) {
            cut = Math.min(cut, pipeIndex);
        }
        if (isIndex > 0) {
            cut = Math.min(cut, isIndex);
        }
        if (colonIndex > 0) {
            cut = Math.min(cut, colonIndex);
        }
        String lead = trimmed.substring(0, cut).trim();
        return !lead.isEmpty() && isDiscardedHumanActorName(lead);
    }

    private int firstPageFromArray(JSONArray pages) {
        if (pages == null || pages.length() == 0) {
            return 0;
        }
        int first = Integer.MAX_VALUE;
        for (int i = 0; i < pages.length(); i++) {
            int page = pages.optInt(i, 0);
            if (page > 0) {
                first = Math.min(first, page);
            }
        }
        return first == Integer.MAX_VALUE ? 0 : first;
    }

    private List<String> collectActorNamesFromEvidence(
            List<JSONObject> primaryTimeline,
            List<JSONObject> primarySubjectFindings,
            int limit
    ) {
        List<String> names = new ArrayList<>();
        for (JSONObject item : primaryTimeline) {
            if (shouldSuppressExecutiveEvidence(item)) {
                continue;
            }
            addActorName(names, item.optString("actor", ""));
            if (names.size() >= limit) {
                return names;
            }
        }
        for (JSONObject item : primarySubjectFindings) {
            if (shouldSuppressExecutiveEvidence(item)) {
                continue;
            }
            addActorName(names, item.optString("actor", ""));
            if (names.size() >= limit) {
                return names;
            }
        }
        return names;
    }

    private void addActorName(List<String> names, String actor) {
        if (actor == null || actor.trim().isEmpty()
                || "unresolved actor".equalsIgnoreCase(actor)
                || !isAllowedPrincipalActorName(actor)) {
            return;
        }
        if (!names.contains(actor.trim())) {
            names.add(actor.trim());
        }
    }

    private void registerActorEvidence(LinkedHashMap<String, List<JSONObject>> byActor, List<JSONObject> items) {
        for (JSONObject item : items) {
            String actor = item.optString("actor", "").trim();
            if (actor.isEmpty() || !isAllowedPrincipalActorName(actor)) {
                continue;
            }
            byActor.computeIfAbsent(actor, ignored -> new ArrayList<>()).add(item);
        }
    }

    private boolean isDiscardedHumanActorName(String actor) {
        if (actor == null) {
            return true;
        }
        String lower = actor.trim().toLowerCase(Locale.US);
        if (lower.isEmpty()) {
            return true;
        }
        return lower.contains("offline deterministic")
                || lower.contains("forensic engine")
                || lower.contains("verum omnis")
                || lower.contains("constitutional forensic")
                || lower.contains("canonical spec")
                || lower.contains("master forensic archive")
                || lower.contains("deployment validator")
                || lower.contains("guardianship treaty")
                || lower.contains("triple verification doctrine")
                || lower.equals("the")
                || lower.equals("and")
                || lower.equals("his")
                || lower.equals("forced")
                || lower.equals("clause")
                || lower.equals("you")
                || lower.equals("april")
                || lower.equals("all")
                || lower.equals("this")
                || lower.equals("that")
                || lower.equals("which")
                || lower.equals("page")
                || lower.equals("date")
                || lower.equals("thanks")
                || lower.equals("march")
                || lower.equals("section")
                || lower.equals("negative evidence")
                || lower.equals("asset forfeiture unit")
                || lower.equals("natal south")
                || lower.equals("south africa")
                || lower.equals("termination")
                || lower.equals("yours")
                || lower.equals("old woman angelfish")
                || lower.equals("goodwill theft")
                || lower.equals("cryptographic sealing")
                || lower.equals("port edward garage")
                || lower.equals("registered address")
                || lower.equals("final window")
                || lower.equals("goodwill payment")
                || lower.equals("settlement deadline")
                || lower.equals("final reminder")
                || lower.equals("deadline for payment")
                || lower.equals("settlement")
                || lower.equals("wayne's world")
                || lower.equals("astron marks")
                || lower.equals("trade secrets")
                || lower.equals("palmbili property investments")
                || lower.equals("bright idea projects")
                || lower.equals("ronnie moir travel")
                || lower.equals("operator")
                || lower.equals("franchisee")
                || lower.equals("franchisor")
                || lower.equals("complainant")
                || lower.equals("first respondent")
                || lower.equals("second respondent")
                || lower.equals("respondent")
                || lower.equals("applicant")
                || lower.equals("claimant")
                || lower.equals("ancillary profit centre")
                || lower.equals("austrian law")
                || lower.equals("austrian trade agents")
                || lower.equals("australian law")
                || lower.equals("australian competition")
                || lower.equals("emphasis added")
                || lower.equals("base schedule")
                || lower.equals("advertising fund contribution")
                || lower.equals("commission sales")
                || lower.equals("domicile attn")
                || lower.equals("legal manager")
                || lower.equals("environmental health")
                || lower.equals("safety book")
                || lower.equals("data theft")
                || lower.equals("total sales")
                || lower.equals("gross retailer margin")
                || lower.equals("lease annexure")
                || lower.equals("kerosene")
                || lower.equals("outlets")
                || lower.equals("tue")
                || lower.equals("individuals")
                || lower.equals("matters")
                || lower.equals("general manager")
                || lower.equals("legal department")
                || lower.equals("common purpose")
                || lower.equals("account")
                || lower.equals("screenshot")
                || lower.equals("trade license")
                || lower.equals("dual liability")
                || lower.equals("commit fraud")
                || lower.equals("cyber forgery")
                || lower.equals("investigating officer")
                || lower.equals("south african")
                || lower.equals("south african law")
                || lower.equals("google drive")
                || lower.equals("doc ref")
                || lower.equals("fabricate liability")
                || lower.equals("franchisor retail program")
                || lower.equals("retail licence")
                || lower.equals("shareholder oppression")
                || lower.equals("financial irregularities")
                || lower.equals("financial irregularity signals")
                || lower.equals("unauthorized transfers")
                || lower.equals("final settlement offer")
                || lower.equals("unlawful conduct")
                || lower.equals("civil remedies")
                || lower.equals("criminal remedies")
                || lower.equals("fraudulent misrepresentation")
                || lower.equals("owen ellis drive")
                || lower.equals("gulf standard time")
                || lower.equals("reeftribe memorandum of")
                || lower.equals("memorandum of")
                || lower.equals("mobile")
                || lower.equals("please marius")
                || lower.equals("greensky ornamentals")
                || lower.equals("greensky solutions")
                || lower.equals("from")
                || lower.equals("pm")
                || lower.equals("am")
                || lower.equals("evidence source")
                || lower.equals("source file")
                || lower.equals("case references")
                || lower.equals("case reference")
                || lower.equals("human report")
                || lower.equals("forensic report")
                || lower.equals("mode")
                || lower.equals("case id")
                || lower.equals("jurisdiction")
                || lower.equals("immediate engagement required")
                || lower.contains("custody locked")
                || lower.contains("chain of custody")
                || lower.contains("structural repair")
                || lower.contains("generated narrative")
                || lower.contains("forensic integrity division")
                || lower.contains("master forensic archive")
                || lower.contains("deployment validator")
                || lower.contains("guardianship treaty")
                || lower.contains("triple verification doctrine")
                || lower.contains("franchise agreement")
                || lower.contains("commencement date")
                || lower.contains("the franchisee")
                || lower.contains("the franchisor")
                || lower.contains("the lessee")
                || lower.contains("memorandum of")
                || lower.contains("final window")
                || lower.contains("goodwill payment")
                || lower.contains("settlement deadline")
                || lower.contains("final reminder")
                || lower.contains("deadline for payment")
                || lower.contains("settlement offer")
                || lower.contains("settlement")
                || lower.contains("unauthorized transfers")
                || lower.contains("shareholder oppression")
                || lower.contains("financial irregularities")
                || lower.contains("unlawful conduct")
                || lower.contains("civil remedies")
                || lower.contains("criminal remedies")
                || lower.contains("fraudulent misrepresentation")
                || lower.contains("owen ellis drive")
                || lower.contains("gulf standard time")
                || lower.contains("cryptographic sealing")
                || lower.contains("goodwill theft")
                || lower.contains("registered address")
                || lower.contains("wayne's world")
                || lower.contains("trade secrets")
                || lower.contains("garage")
                || lower.contains("service station")
                || lower.contains("property investments")
                || lower.contains("petroleum products act")
                || lower.contains("particular terms")
                || lower.contains("astron manual")
                || lower.contains("franchised business")
                || lower.contains("franchise interest")
                || lower.contains("retail outlet standards")
                || lower.contains("astron products")
                || lower.contains("first respondent")
                || lower.contains("second respondent")
                || lower.contains("ancillary profit centre")
                || lower.contains("austrian law")
                || lower.contains("austrian trade agents")
                || lower.contains("australian law")
                || lower.contains("australian competition")
                || lower.contains("emphasis added")
                || lower.contains("base schedule")
                || lower.contains("advertising fund contribution")
                || lower.contains("commission sales")
                || lower.contains("domicile attn")
                || lower.contains("legal manager")
                || lower.contains("environmental health")
                || lower.contains("safety book")
                || lower.contains("total sales")
                || lower.contains("gross retailer margin")
                || lower.contains("lease annexure")
                || lower.contains("branded marketer")
                || lower.contains("mail delivery subsystem")
                || lower.contains("unified call center")
                || lower.contains("rakpp info")
                || lower.contains("urgent cross")
                || lower.contains("institutional silence despite")
                || lower.contains("immediate instruction opportunity")
                || lower.contains("sealed files")
                || lower.contains("greensky fraud cases")
                || lower.contains("southbridge misconduct")
                || lower.contains("integrity lock")
                || lower.contains("selective omissions")
                || lower.contains("fiduciary duty key")
                || lower.contains("fraudulent evidence key")
                || lower.contains("document execution state")
                || lower.contains("gaslighting refusal")
                || lower.contains("south african")
                || lower.contains("villiers road")
                || lower.contains("port elizabeth")
                || lower.equals("general manager")
                || lower.equals("support team")
                || lower.equals("new company registration")
                || lower.equals("national crime intelligence")
                || lower.equals("complaints form submission")
                || lower.equals("access")
                || lower.equals("action")
                || lower.equals("legal")
                || lower.equals("mail delivery subsystem")
                || lower.equals("unified call center")
                || lower.equals("rakpp info")
                || lower.equals("urgent cross")
                || lower.equals("institutional silence despite")
                || lower.equals("immediate instruction opportunity")
                || lower.equals("sealed files")
                || lower.equals("greensky fraud cases")
                || lower.equals("southbridge misconduct")
                || lower.equals("each")
                || lower.equals("evidence")
                || lower.equals("gold standard")
                || lower.equals("integrity lock")
                || lower.equals("selective omissions")
                || lower.equals("fiduciary duty key")
                || lower.equals("fraudulent evidence key")
                || lower.equals("document execution state")
                || lower.equals("gaslighting refusal")
                || lower.equals("dear sir")
                || lower.equals("dear sirs")
                || lower.equals("dear celeste")
                || lower.equals("franchise letter dear")
                || lower.equals("glenmore beach from")
                || lower.equals("gary highcock yes")
                || lower.equals("com subject")
                || lower.equals("sent")
                || lower.endsWith(" sent")
                || lower.endsWith(" yes")
                || lower.endsWith(" no")
                || lower.endsWith(" from")
                || lower.endsWith(" to")
                || lower.endsWith(" subject")
                || lower.endsWith(" date")
                || lower.contains("message-id")
                || lower.contains("in-reply-to")
                || lower.endsWith(" mobile")
                || lower.contains("company secretarial administrator");
    }

    private boolean isAllowedPrincipalActorName(String actor) {
        if (actor == null) {
            return false;
        }
        String trimmed = actor.trim();
        if (trimmed.isEmpty() || isDiscardedHumanActorName(trimmed)) {
            return false;
        }
        if (looksLikeHumanPersonName(trimmed)) {
            return true;
        }
        if (looksLikeHumanOrganisationActor(trimmed)) {
            return true;
        }
        String lower = trimmed.toLowerCase(Locale.US);
        return lower.contains("legal")
                || lower.contains("attorneys")
                || lower.contains("hawks")
                || lower.contains("saps")
                || lower.contains("police")
                || lower.contains("lpc");
    }

    private boolean isUnsafeHumanFinancialAttribution(JSONObject item) {
        String actor = canonicalizeHumanVictimDisplay(item == null ? "" : item.optString("actor", "").trim());
        return isUnsafeHumanFinancialAttributionForActor(actor, item);
    }

    private boolean isUnsafeHumanFinancialAttributionForActor(String actor, JSONObject item) {
        if (item == null || actor == null) {
            return false;
        }
        String canonicalActor = canonicalizeHumanVictimDisplay(actor);
        if (canonicalActor.isEmpty() || "unresolved actor".equalsIgnoreCase(canonicalActor)) {
            return false;
        }
        if (!looksLikeHumanPersonName(canonicalActor)) {
            return false;
        }
        if (!isStrictHumanFinancialItem(item)) {
            return false;
        }
        String contentCorpus = buildHumanEvidenceContentCorpus(item);
        if (contentCorpus.trim().isEmpty()) {
            return false;
        }
        return !mentionsHumanActorAlias(contentCorpus, canonicalActor);
    }

    private boolean isStrictHumanFinancialItem(JSONObject item) {
        if (item == null) {
            return false;
        }
        String label = firstNonEmpty(
                item.optString("label", null),
                item.optString("amountCategory", null),
                item.optString("category", null)
        );
        String normalizedLabel = upperUs(label);
        String corpus = lowerUs(buildHumanEvidenceContentCorpus(item));
        if (normalizedLabel.contains("FINANCIAL")
                || normalizedLabel.contains("PAYMENT")
                || normalizedLabel.contains("GOODWILL")
                || normalizedLabel.contains("RENT")
                || normalizedLabel.contains("UPGRADE")
                || normalizedLabel.contains("EXTENSION")) {
            return true;
        }
        return corpus.contains("rent")
                || corpus.contains("rental")
                || corpus.contains("invoice")
                || corpus.contains("goodwill")
                || corpus.contains("brand fee")
                || corpus.contains("extension fee")
                || corpus.contains("upgrade")
                || Pattern.compile("\\br\\s?\\d", Pattern.CASE_INSENSITIVE).matcher(corpus).find();
    }

    private String buildHumanEvidenceContentCorpus(JSONObject item) {
        if (item == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        maybeAppendCorpus(sb, stripHumanSummaryLead(item.optString("summary", "")));
        maybeAppendCorpus(sb, item.optString("text", ""));
        maybeAppendCorpus(sb, item.optString("excerpt", ""));
        maybeAppendCorpus(sb, item.optString("narrative", ""));
        return sb.toString();
    }

    private String stripHumanSummaryLead(String summary) {
        if (summary == null) {
            return "";
        }
        String trimmed = summary.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int lastPipe = trimmed.lastIndexOf('|');
        if (lastPipe >= 0 && lastPipe + 1 < trimmed.length()) {
            return trimmed.substring(lastPipe + 1).trim();
        }
        return trimmed;
    }

    private boolean mentionsHumanActorAlias(String corpus, String actor) {
        if (corpus == null || actor == null) {
            return false;
        }
        String normalizedCorpus = normalizeHumanAliasText(corpus);
        if (normalizedCorpus.trim().isEmpty()) {
            return false;
        }
        for (String alias : buildHumanActorAliases(actor)) {
            String normalizedAlias = normalizeHumanAliasText(alias).trim();
            if (normalizedAlias.isEmpty()) {
                continue;
            }
            if (normalizedCorpus.contains(" " + normalizedAlias + " ")) {
                return true;
            }
        }
        return false;
    }

    private Set<String> buildHumanActorAliases(String actor) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        if (actor == null) {
            return aliases;
        }
        String canonical = canonicalizeHumanVictimDisplay(actor).trim();
        if (canonical.isEmpty()) {
            return aliases;
        }
        String lower = canonical.toLowerCase(Locale.US);
        aliases.add(canonical);
        aliases.add(lower);
        if ("desmond smith".equals(lower) || "des smith".equals(lower) || "des".equals(lower)) {
            aliases.add("Desmond Smith");
            aliases.add("desmond smith");
            aliases.add("Des Smith");
            aliases.add("des smith");
            aliases.add("Des");
            aliases.add("des");
        } else if ("wayne nel".equals(lower) || "wayne".equals(lower)) {
            aliases.add("Wayne Nel");
            aliases.add("wayne nel");
            aliases.add("Wayne");
            aliases.add("wayne");
        } else if ("gary highcock".equals(lower) || "your dad".equals(lower)) {
            aliases.add("Gary Highcock");
            aliases.add("gary highcock");
            aliases.add("Gary");
            aliases.add("gary");
            aliases.add("Your Dad");
            aliases.add("your dad");
        } else if ("all fuels".equals(lower) || "bright idea projects 66".equals(lower)) {
            aliases.add("All Fuels");
            aliases.add("all fuels");
            aliases.add("AllFuels");
            aliases.add("allfuels");
            aliases.add("Bright Idea Projects 66");
            aliases.add("bright idea projects 66");
        }
        return aliases;
    }

    private String normalizeHumanAliasText(String text) {
        if (text == null) {
            return " ";
        }
        String normalized = text.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
        if (normalized.isEmpty()) {
            return " ";
        }
        return " " + normalized + " ";
    }

    private List<JSONObject> filterExecutiveHumanEvidence(List<JSONObject> items) {
        List<JSONObject> filtered = new ArrayList<>();
        if (items == null) {
            return filtered;
        }
        for (JSONObject item : items) {
            if (item == null || shouldSuppressExecutiveEvidence(item)) {
                continue;
            }
            filtered.add(item);
        }
        return filtered;
    }

    private boolean shouldSuppressExecutiveEvidence(JSONObject item) {
        if (item == null) {
            return true;
        }
        String label = firstNonEmpty(
                item.optString("label", null),
                item.optString("type", null),
                item.optString("subject", null)
        ).trim();
        String actor = item.optString("actor", "").trim();
        String corpus = buildHumanNarrativeCorpus(item);
        if (isAbstractExecutiveLabel(label)) {
            return true;
        }
        if (isArchiveStructuralNarrative(corpus)) {
            return true;
        }
        if (isSupportOnlyHumanNarrative(corpus) && !containsHardForensicHumanSignal(corpus)) {
            return true;
        }
        return !actor.isEmpty() && isDiscardedHumanActorName(actor);
    }

    private String buildHumanNarrativeCorpus(JSONObject item) {
        if (item == null) {
            return "";
        }
        return firstNonEmpty(item.optString("summary", null), "") + " "
                + firstNonEmpty(item.optString("text", null), "") + " "
                + firstNonEmpty(item.optString("label", null), "") + " "
                + firstNonEmpty(item.optString("excerpt", null), "") + " "
                + firstNonEmpty(item.optString("narrative", null), "");
    }

    private boolean containsHardForensicHumanSignal(String text) {
        return containsAny(text,
                "proceeded with the deal",
                "thanks for the invoice",
                "thank you for the invoice",
                "invoice",
                "shareholder agreement",
                "agreement states",
                "30%",
                "profit share",
                "termination",
                "sealife",
                "forged",
                "whatsapp",
                "screenshot",
                "archive request",
                "scaquaculture",
                "unauthorised",
                "unauthorized",
                "issued a termination notice",
                "termination notice",
                "completed order",
                "client confirmation",
                "selective screenshots",
                "attack call",
                "must i do the marketing",
                "hong kong",
                "kevin's export proceeded",
                "countersigned",
                "not countersigned",
                "never countersigned",
                "metadata",
                "tamper",
                "deal completed",
                "fraudulent registration",
                "registration fraud",
                "unlawful charter",
                "charter operations",
                "vessel",
                "stolen vessel",
                "theft of vessel",
                "theft",
                "stolen",
                "permit holder",
                "samsa",
                "dffe",
                "goodwill was stolen",
                "goodwill stolen",
                "forced off site",
                "port edward",
                "umtentweni",
                "desmond smith",
                "des smith",
                "r25,706.01",
                "r45,000",
                "r20,000",
                "million",
                "cease-and-desist",
                "cease and desist",
                "misconduct",
                "denial of remedy",
                "institutional silence",
                "bad-faith",
                "bad faith",
                "gaslighting",
                "coercive",
                "vulnerability",
                "withheld financials",
                "profit diversion");
    }

    private boolean isSupportOnlyHumanNarrative(String text) {
        return containsAny(text,
                "formal complaint",
                "complaint file",
                "legal practice council",
                "follow-up",
                "follow up",
                "meeting request",
                "private meeting",
                "goodwill payment",
                "settlement",
                "formal notice",
                "law-enforcement notice",
                "escalation",
                "anchored correspondence",
                "dear ",
                "subject:",
                "from:",
                "to:",
                "cc:",
                "bcc:");
    }

    private boolean isArchiveStructuralNarrative(String text) {
        return containsAny(text,
                "constitutional deployment validator",
                "#!/bin/bash",
                "test_requirement",
                "table of contents",
                "founders archive",
                "guardianship treaty",
                "triple verification doctrine",
                "machine-readable blueprint",
                "canonical spec",
                "official submission",
                "master forensic archive",
                "charter",
                "readme",
                "spec",
                "prompts",
                "generated:",
                "source of truth",
                "system summary");
    }

    private boolean isAbstractExecutiveLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            return false;
        }
        String lower = label.trim().toLowerCase(Locale.US);
        return lower.equals("shareholder oppression")
                || lower.equals("financial irregularities")
                || lower.equals("financial irregularity signals")
                || lower.equals("legal subject flags present")
                || lower.equals("civil")
                || lower.equals("criminal")
                || lower.equals("cybercrime")
                || lower.equals("breach of fiduciary duty")
                || lower.equals("unauthorized transfers")
                || lower.equals("final settlement offer");
    }

    private boolean isAllowedHumanActor(String actor, JSONArray namedParties) {
        if (actor == null) {
            return false;
        }
        String trimmed = actor.trim();
        if (trimmed.isEmpty() || isDiscardedHumanActorName(trimmed)) {
            return false;
        }
        if (!looksLikeHumanPersonName(trimmed) && !looksLikeHumanOrganisationActor(trimmed)) {
            return false;
        }
        if (namedParties == null || namedParties.length() == 0) {
            return true;
        }
        for (int i = 0; i < namedParties.length(); i++) {
            JSONObject party = namedParties.optJSONObject(i);
            if (party == null) {
                continue;
            }
            String name = party.optString("name", "").trim();
            if (name.equalsIgnoreCase(trimmed)
                    && (looksLikeHumanPersonName(name) || looksLikeHumanOrganisationActor(name))) {
                int occurrenceCount = party.optInt("occurrences", 0);
                int headerEvidenceCount = party.optInt("headerEvidenceCount", 0);
                JSONArray pages = party.optJSONArray("pages");
                int pageCount = pages != null ? pages.length() : 0;
                return headerEvidenceCount > 0 || occurrenceCount >= 2 || pageCount >= 2;
            }
        }
        return false;
    }

    private boolean looksLikeHumanPersonName(String actor) {
        if (actor == null) {
            return false;
        }
        String trimmed = actor.trim();
        if (trimmed.length() < 5 || trimmed.length() > 40) {
            return false;
        }
        if (trimmed.contains("@")) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.contains("pty")
                || lower.contains("ltd")
                || lower.contains("llc")
                || lower.contains(" sent")
                || lower.contains(" from")
                || lower.contains(" to")
                || lower.contains(" subject")
                || lower.contains(" date")
                || lower.contains(" message")
                || lower.contains("dear")
                || lower.contains("sir")
                || lower.contains("sirs")
                || lower.contains("complaint")
                || lower.contains("submission")
                || lower.contains("form")
                || lower.contains("crime")
                || lower.contains("intelligence")
                || lower.contains("franchise")
                || lower.contains("beach")
                || lower.contains("solutions")
                || lower.contains("ornamentals")
                || lower.contains("hong kong")
                || lower.contains("anthias")
                || lower.contains("angelfish")
                || lower.contains("manager")
                || lower.contains("team")
                || lower.contains("support")
                || lower.contains("general")
                || lower.contains("south africa")
                || lower.contains("durban")
                || lower.contains("marine")
                || lower.contains("parade")) {
            return false;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2 || parts.length > 3) {
            return false;
        }
        for (String part : parts) {
            if (part.length() < 2 || !Character.isUpperCase(part.charAt(0))) {
                return false;
            }
        }
        return true;
    }

    private boolean looksLikeHumanOrganisationActor(String actor) {
        if (actor == null) {
            return false;
        }
        String trimmed = actor.trim();
        if (trimmed.length() < 4 || trimmed.length() > 80 || isDiscardedHumanActorName(trimmed)) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.US);
        return containsAny(lower,
                "all fuels",
                "bright idea",
                "astron",
                "southbridge",
                "amos hardware",
                "energy",
                "legal",
                "attorneys",
                "projects",
                "investments",
                "holdings",
                "properties",
                "hardware",
                "bank",
                "pty",
                "ltd",
                "llc",
                "inc");
    }

    private void appendGemmaEvidenceLines(StringBuilder sb, List<JSONObject> items, int limit) {
        if (items == null || items.isEmpty()) {
            return;
        }
        int emitted = 0;
        for (JSONObject item : items) {
            if (emitted >= limit) {
                break;
            }
            String line = humanSummaryLine(item, true);
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            sb.append("- ").append(line).append("\n");
            emitted++;
        }
    }

    private int firstPageOf(List<JSONObject> items) {
        int first = Integer.MAX_VALUE;
        for (JSONObject item : items) {
            int page = item.optInt("page", Integer.MAX_VALUE);
            first = Math.min(first, page);
        }
        return first == Integer.MAX_VALUE ? 0 : first;
    }

    private String joinPages(List<JSONObject> items) {
        Set<Integer> pages = new LinkedHashSet<>();
        for (JSONObject item : items) {
            int page = item.optInt("page", 0);
            if (page > 0) {
                pages.add(page);
            }
            if (pages.size() >= 6) {
                break;
            }
        }
        if (pages.isEmpty()) {
            return "not stated";
        }
        StringBuilder sb = new StringBuilder();
        for (Integer page : pages) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(page);
        }
        return sb.toString();
    }

    private String joinEvidenceSnippets(List<JSONObject> items, int limit) {
        List<String> snippets = new ArrayList<>();
        for (JSONObject item : items) {
            String line = humanSummaryLine(item, true);
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            if (!snippets.contains(line)) {
                snippets.add(line);
            }
            if (snippets.size() >= limit) {
                break;
            }
        }
        return snippets.isEmpty() ? "no clear actor-linked act stated" : joinStringList(snippets);
    }

    private String humanizeEvidenceLine(JSONObject item, boolean omitActorPrefix) {
        if (item == null) {
            return "";
        }
        String actor = item.optString("actor", "").trim();
        String label = item.optString("label", "").trim();
        String text = clipReportText(item.optString("text", ""), 240);
        int page = item.optInt("page", 0);

        StringBuilder sb = new StringBuilder();
        if (!omitActorPrefix && !actor.isEmpty() && !"unresolved actor".equalsIgnoreCase(actor)) {
            sb.append(actor).append(": ");
        }

        if (!label.isEmpty()) {
            String friendlyLabel = humanDisplayLabel(label);
            sb.append(friendlyLabel);
            if (!text.isEmpty()) {
                sb.append(" - ").append(text);
            }
        } else if (!text.isEmpty()) {
            sb.append(text);
        }

        if (page > 0) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append("page ").append(page);
        }
        return sb.toString().trim();
    }

    private String legalExposureForLabel(String label) {
        if (label == null) {
            return "Abstract legal subject flag requiring audit against the sealed record";
        }
        switch (label.trim()) {
            case "Fraudulent Evidence":
                return "Abstract fraud / evidence-integrity subject flag";
            case "Cybercrime":
                return "Abstract cyber / unauthorized-access subject flag";
            case "Financial Irregularities":
                return "Abstract financial-irregularity subject flag";
            case "Concealment / Deletion":
                return "Abstract concealment / spoliation subject flag";
            case "Breach of Fiduciary Duty":
                return "Abstract fiduciary-duty subject flag";
            case "Shareholder Oppression":
                return "Abstract shareholder-oppression subject flag";
            case "Emotional Exploitation":
                return "Abstract coercion / vulnerability-exploitation subject flag";
            case "GOODWILL":
            case "RENT_EXTRACTION":
            case "UPGRADE_COSTS":
            case "EXTENSION_FEE":
            case "PAYMENT_DEMAND":
                return "Abstract financial / unjust-enrichment issue requiring reconciliation";
            case "MISSING_COUNTERSIGNATURE":
            case "MISSING_EXECUTION_EVIDENCE":
                return "Abstract contract-execution defect flag";
            case "BACKDATING_RISK":
            case "METADATA_ANOMALY":
            case "SIGNATURE_MISMATCH":
            case "CHAIN_OF_CUSTODY_GAP":
                return "Abstract document-integrity issue requiring technical verification";
            default:
                return "Abstract legal subject flag requiring audit against the sealed record";
        }
    }

    private String joinStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(value.trim());
        }
        return sb.toString();
    }



    private String buildGemmaEvidenceDigest(AnalysisEngine.ForensicReport report) {

        StringBuilder sb = new StringBuilder();

        if (report.constitutionalExtraction != null) {

            appendNamedPartiesSection(sb, report.constitutionalExtraction);

            appendCriticalLegalSubjectsSection(sb, report.constitutionalExtraction);

            appendIncidentRegisterSection(sb, report.constitutionalExtraction);

            appendAnchoredFindingsSection(sb, report.constitutionalExtraction);

        }

        if (report.diagnostics != null) {

            appendContradictionRegisterSection(sb, report.diagnostics);

        }

        if (report.nativeEvidence != null) {

            appendNativeEvidenceSection(sb, report.nativeEvidence);

        }

        if (report.patternAnalysis != null) {

            sb.append("\n=== Pattern Analysis ===\n").append(report.patternAnalysis.toString());

        }

        if (report.vulnerabilityAnalysis != null) {

            sb.append("\n=== Vulnerability Analysis ===\n").append(report.vulnerabilityAnalysis.toString());

        }

        if (report.truthContinuityAnalysis != null) {

            sb.append("\n=== Truth Continuity Analysis ===\n").append(report.truthContinuityAnalysis.toString());

        }

        return sb.toString();

    }



    private String joinJsonArray(JSONArray values, int limit) {

        if (values == null || values.length() == 0) {

            return "none recorded";

        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < Math.min(values.length(), limit); i++) {

            String value = values.optString(i, "").trim();

            if (value.isEmpty()) {

                continue;

            }

            if (sb.length() > 0) {

                sb.append("; ");

            }

            sb.append(value);

        }

        return sb.length() == 0 ? "none recorded" : sb.toString();

    }



    private String joinPatternArray(JSONArray values, int limit) {

        if (values == null || values.length() == 0) {

            return "none recorded";

        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < Math.min(values.length(), limit); i++) {

            JSONObject item = values.optJSONObject(i);

            if (item == null) {

                continue;

            }

            String name = item.optString("patternName", "").trim();

            String note = item.optString("evidenceNote", "").trim();

            if (name.isEmpty()) {

                continue;

            }

            if (sb.length() > 0) {

                sb.append("; ");

            }

            sb.append(name.replace('_', ' '));

            if (!note.isEmpty()) {

                sb.append(" - ").append(note);

            }

        }

        return sb.length() == 0 ? "none recorded" : sb.toString();

    }



    private String joinNamedParties(JSONArray values, int limit) {

        if (values == null || values.length() == 0) {

            return "none recorded";

        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < Math.min(values.length(), limit); i++) {

            JSONObject item = values.optJSONObject(i);

            if (item == null) {

                continue;

            }

            String name = item.optString("name", "").trim();

            if (name.isEmpty()) {

                continue;

            }

            if (sb.length() > 0) {

                sb.append("; ");

            }

            sb.append(name);

        }

        return sb.length() == 0 ? "none recorded" : sb.toString();

    }



    private String joinIncidentNarratives(JSONArray values, int limit) {

        if (values == null || values.length() == 0) {

            return "No anchored incidents extracted.\n";

        }

        StringBuilder sb = new StringBuilder();

        int emitted = 0;

        for (int i = 0; i < values.length() && emitted < limit; i++) {

            JSONObject item = values.optJSONObject(i);

            if (item == null) {

                continue;

            }

            String narrative = item.optString("narrative", "").trim();

            String actor = item.optString("actor", "").trim();

            String description = item.optString("description", "").trim();

            int page = item.optInt("page", 0);

            if (narrative.isEmpty()) {

                if (description.isEmpty()) {

                    continue;

                }

                StringBuilder fallback = new StringBuilder();

                fallback.append(actor.isEmpty() ? "Unresolved actor" : actor)

                        .append(": ")

                        .append(description);

                if (page > 0) {

                    fallback.append(" (page ").append(page).append(")");

                }

                narrative = fallback.toString();

            }

            sb.append("• ").append(narrative).append("\n");

            emitted++;

        }

        return sb.length() == 0 ? "No anchored incidents extracted.\n" : sb.toString();

    }



    private String joinContradictionArray(JSONArray values, int limit) {

        if (values == null || values.length() == 0) {

            return "No contradiction records extracted.\n";

        }

        StringBuilder sb = new StringBuilder();

        int emitted = 0;

        for (int i = 0; i < values.length() && emitted < limit; i++) {

            JSONObject item = values.optJSONObject(i);

            if (item == null) {

                continue;

            }

            sb.append("• Page ")

                    .append(item.optInt("page", 0))

                    .append(" / ")

                    .append(item.optString("actor", "unresolved actor"))

                    .append(": ")

                    .append(item.optString("excerpt", ""))

                    .append("\n");

            emitted++;

        }

        return sb.length() == 0 ? "No contradiction records extracted.\n" : sb.toString();

    }



    private String joinList(String[] values, int limit) {

        if (values == null || values.length == 0) {

            return "none recorded";

        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < Math.min(values.length, limit); i++) {

            if (values[i] == null || values[i].trim().isEmpty()) {

                continue;

            }

            if (sb.length() > 0) {

                sb.append("; ");

            }

            sb.append(values[i].trim());

        }

        return sb.length() == 0 ? "none recorded" : sb.toString();

    }



    private String joinListSkipping(String[] values, int limit, String skipValue) {

        if (values == null || values.length == 0) {

            return "none recorded";

        }

        StringBuilder sb = new StringBuilder();

        int emitted = 0;

        for (String value : values) {

            if (value == null) {

                continue;

            }

            String trimmed = value.trim();

            if (trimmed.isEmpty()) {

                continue;

            }

            if (skipValue != null && skipValue.equalsIgnoreCase(trimmed)) {

                continue;

            }

            if (sb.length() > 0) {

                sb.append("; ");

            }

            sb.append(trimmed);

            emitted++;

            if (emitted >= limit) {

                break;

            }

        }

        return sb.length() == 0 ? "none recorded" : sb.toString();

    }



    private String formatIntegritySummary(Map<String, String> integrityResults) {

        if (integrityResults == null || integrityResults.isEmpty()) {

            return "not available";

        }

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, String> entry : integrityResults.entrySet()) {

            if (sb.length() > 0) {

                sb.append("; ");

            }

            sb.append(entry.getKey()).append("=").append(entry.getValue());

        }

        return sb.toString();

    }



    private String clipText(String value, int maxChars) {

        if (value == null || value.length() <= maxChars) {

            return value == null ? "" : value;

        }

        return value.substring(0, maxChars) + "\n...[truncated for on-device prompt]...";

    }

    private String clipReportText(String value, int maxChars) {

        if (value == null || value.length() <= maxChars) {

            return value == null ? "" : value;

        }

        return value.substring(0, maxChars).trim() + " ...[continued in sealed audit report]...";

    }



    private String sanitizeGemmaReport(String response) {

        if (response == null) {

            return "";

        }

        String cleaned = response.trim();

        if (cleaned.startsWith("[START OF DOCUMENT]")) {

            cleaned = cleaned.substring("[START OF DOCUMENT]".length()).trim();

        }

        if (cleaned.endsWith("[END OF DOCUMENT]")) {

            cleaned = cleaned.substring(0, cleaned.length() - "[END OF DOCUMENT]".length()).trim();

        }

        if (cleaned.startsWith("```")) {

            cleaned = cleaned.replaceFirst("^```[a-zA-Z0-9_-]*\\s*", "").replaceFirst("\\s*```$", "").trim();

        }

        return cleaned;

    }



    private boolean looksLikeUsableGemmaReport(String reportText) {

        if (reportText == null || reportText.trim().length() < 800) {

            return false;

        }

        String lower = reportText.toLowerCase(Locale.ROOT);

        return lower.contains("executive summary")

                && lower.contains("what happened")

                && lower.contains("integrity and vault record")

                && lower.contains("immediate next actions");

    }



    private String buildVaultResultNotice(

            AnalysisEngine.ForensicReport report,

            File sealedEvidenceFile,

            File readableBriefFile,

            File humanNarrativePdf,

            File auditorPdf,

            File findingsFile,

            File legalAdvisoryFile,

            File visualFindingsFile,

            File modelAuditLedgerFile,

            File meshFile,

            AssistanceRestrictionManager.Snapshot restriction

    ) {

        StringBuilder sb = new StringBuilder();

        sb.append("Constitutional processing complete.")

                .append("\nCase ID: ").append(report.caseId)

                .append("\nProcessing status: ")

                .append(report.diagnostics != null

                        ? report.diagnostics.optString("processingStatus", "DETERMINATE")

                        : "DETERMINATE")

                .append("\nJurisdiction: ").append(report.jurisdictionName).append(" (").append(report.jurisdiction).append(")")

                .append("\nEvidence SHA-512: ").append(HashUtil.truncate(report.evidenceHash, 24))

                .append("...")

                .append("\n\nVault: ").append(VaultManager.getVaultDir(this).getAbsolutePath());

        if (sealedEvidenceFile != null) {

            sb.append("\nSealed Evidence: ").append(sealedEvidenceFile.getAbsolutePath());

        }

        if (readableBriefFile != null) {
            sb.append("\nReadable Findings Brief PDF: ").append(readableBriefFile.getAbsolutePath());
        }

        if (humanNarrativePdf != null) {

            sb.append("\nHuman Forensic Report PDF: ").append(humanNarrativePdf.getAbsolutePath());
        }

        if (auditorPdf != null) {

            sb.append("\nForensic Audit PDF: ").append(auditorPdf.getAbsolutePath());

        }

        if (findingsFile != null) {

            sb.append("\nFindings Package JSON: ").append(findingsFile.getAbsolutePath());

        }

        if (legalAdvisoryFile != null) {

            sb.append("\nLegal Advisory JSON: ").append(legalAdvisoryFile.getAbsolutePath());

        }

        if (visualFindingsFile != null) {

            sb.append("\nVisual Findings Memo: ").append(visualFindingsFile.getAbsolutePath());

        }

        if (modelAuditLedgerFile != null) {

            sb.append("\nBounded Model Audit Ledger: ").append(modelAuditLedgerFile.getAbsolutePath());

        }

        if (meshFile != null) {

            sb.append("\nMesh Packet: ").append(meshFile.getAbsolutePath());

        }

        if (restriction != null && restriction.restricted) {

            sb.append("\n\nAssistance Restriction: active after report issuance.")

                    .append("\nCase: ").append(restriction.caseId)

                    .append("\nEvidence Hash: ").append(restriction.evidenceHashShort)

                    .append("\nReason: ").append(restriction.reason);

        }

        return sb.toString();

    }



    private File writeVisualFindingsMemoToVault(AnalysisEngine.ForensicReport report) throws Exception {
        return writeVisualFindingsMemoToVault(report, buildVisualFindingsMemo(report));
    }

    private File writeVisualFindingsMemoToVault(
            AnalysisEngine.ForensicReport report,
            String visualFindingsMemo
    ) throws Exception {

        File outFile = VaultManager.createVaultFile(
                this,
                "visual-findings-memo",
                ".txt",
                selectedFile != null ? selectedFile.getName() : null
        );

        try (FileOutputStream fos = new FileOutputStream(outFile)) {

            fos.write((visualFindingsMemo == null ? "" : visualFindingsMemo).getBytes(StandardCharsets.UTF_8));

        }

        VaultManager.writeSealManifest(
                this,
                outFile,
                "visual-findings-memo",
                report.caseId,
                report.evidenceHash
        );

        return outFile;

    }

    private File writeReadableFindingsBriefToVault(
            AnalysisEngine.ForensicReport report,
            String readableBrief
    ) throws Exception {
        if (report == null || readableBrief == null || readableBrief.trim().isEmpty()) {
            return null;
        }

        File outFile = VaultManager.createVaultFile(
                this,
                "readable-forensic-brief",
                ".pdf",
                selectedFile != null ? selectedFile.getName() : null
        );
        return writeReadableFindingsBriefToPath(
                outFile,
                selectedFile != null ? selectedFile.getName() : "unknown",
                report.caseId,
                report.evidenceHash,
                report.jurisdictionName,
                report.jurisdiction,
                readableBrief
        );
    }

    private File writePoliceReadyReportToVault(
            AnalysisEngine.ForensicReport report,
            String policeReadyReport
    ) throws Exception {
        if (report == null || policeReadyReport == null || policeReadyReport.trim().isEmpty()) {
            return null;
        }

        File outFile = VaultManager.createVaultFile(
                this,
                "police-ready-forensic-report",
                ".pdf",
                selectedFile != null ? selectedFile.getName() : null
        );
        return writePoliceReadyReportToPath(
                outFile,
                selectedFile != null ? selectedFile.getName() : "unknown",
                report.caseId,
                report.evidenceHash,
                report.jurisdictionName,
                report.jurisdiction,
                policeReadyReport
        );
    }

    private File writeConstitutionalVaultReportToVault(
            AnalysisEngine.ForensicReport report,
            String constitutionalReport
    ) throws Exception {
        if (report == null || constitutionalReport == null || constitutionalReport.trim().isEmpty()) {
            return null;
        }

        File outFile = VaultManager.createVaultFile(
                this,
                "constitutional-vault-report",
                ".pdf",
                selectedFile != null ? selectedFile.getName() : null
        );
        return writeConstitutionalVaultReportToPath(
                outFile,
                selectedFile != null ? selectedFile.getName() : "unknown",
                report.caseId,
                report.evidenceHash,
                report.jurisdictionName,
                report.jurisdiction,
                constitutionalReport
        );
    }

    private File writeContradictionEngineReportToVault(
            AnalysisEngine.ForensicReport report,
            String contradictionReport
    ) throws Exception {
        if (report == null || contradictionReport == null || contradictionReport.trim().isEmpty()) {
            return null;
        }

        File outFile = VaultManager.createVaultFile(
                this,
                "contradiction-engine-report",
                ".pdf",
                selectedFile != null ? selectedFile.getName() : null
        );
        return writeContradictionEngineReportToPath(
                outFile,
                selectedFile != null ? selectedFile.getName() : "unknown",
                report.caseId,
                report.evidenceHash,
                report.jurisdictionName,
                report.jurisdiction,
                contradictionReport
        );
    }

    private File writeReadableFindingsBriefToPath(
            File outFile,
            String sourceFileName,
            String caseId,
            String evidenceHash,
            String jurisdictionName,
            String jurisdictionCode,
            String readableBrief
    ) throws Exception {
        PDFSealer.SealRequest req = new PDFSealer.SealRequest();
        req.title = "VERUM OMNIS - READABLE FINDINGS BRIEF";
        req.includeQr = true;
        req.includeHash = true;
        req.mode = PDFSealer.DocumentMode.FORENSIC_REPORT;
        req.evidenceHash = evidenceHash;
        req.caseId = caseId;
        req.jurisdiction = safeValue(jurisdictionName) + " (" + safeValue(jurisdictionCode) + ")";
        req.summary = "Plain-language constitutional findings brief generated from the sealed engine outputs.";
        req.legalSummary = "Readable vault brief generated from the engine outputs only. The audit report remains the technical ledger.";
        req.bodyText = readableBrief;
        req.sourceFileName = sourceFileName;
        if (intakeSnapshot != null) {
            req.intakeMetadata = "UTC " + intakeSnapshot.capturedAtUtc
                    + " | Local " + intakeSnapshot.localTime
                    + " | TZ " + intakeSnapshot.timezoneId
                    + " | GPS " + intakeSnapshot.coordinatesLabel();
        }
        PDFSealer.generateSealedPdf(this, req, outFile);
        VaultManager.writeSealManifest(
                this,
                outFile,
                "readable-findings-brief",
                caseId,
                evidenceHash
        );
        return outFile;
    }

    private File writePoliceReadyReportToPath(
            File outFile,
            String sourceFileName,
            String caseId,
            String evidenceHash,
            String jurisdictionName,
            String jurisdictionCode,
            String policeReadyReport
    ) throws Exception {
        PDFSealer.SealRequest req = new PDFSealer.SealRequest();
        req.title = "VERUM OMNIS - POLICE READY CONSTITUTIONAL FORENSIC REPORT";
        req.includeQr = true;
        req.includeHash = true;
        req.mode = PDFSealer.DocumentMode.FORENSIC_REPORT;
        req.evidenceHash = evidenceHash;
        req.caseId = caseId;
        req.jurisdiction = safeValue(jurisdictionName) + " (" + safeValue(jurisdictionCode) + ")";
        req.summary = "Police-ready constitutional forensic report generated from the sealed findings, chronology, and contradiction outputs.";
        req.legalSummary = "This report is built from anchored engine outputs only. It is designed for docketing and formal investigation, not as a substitute for a judicial verdict.";
        req.bodyText = policeReadyReport;
        req.sourceFileName = sourceFileName;
        if (intakeSnapshot != null) {
            req.intakeMetadata = "UTC " + intakeSnapshot.capturedAtUtc
                    + " | Local " + intakeSnapshot.localTime
                    + " | TZ " + intakeSnapshot.timezoneId
                    + " | GPS " + intakeSnapshot.coordinatesLabel();
        }
        PDFSealer.generateSealedPdf(this, req, outFile);
        VaultManager.writeSealManifest(
                this,
                outFile,
                "police-ready-forensic-report",
                caseId,
                evidenceHash
        );
        return outFile;
    }

    private File writeConstitutionalVaultReportToPath(
            File outFile,
            String sourceFileName,
            String caseId,
            String evidenceHash,
            String jurisdictionName,
            String jurisdictionCode,
            String constitutionalReport
    ) throws Exception {
        PDFSealer.SealRequest req = new PDFSealer.SealRequest();
        req.title = "VERUM OMNIS - CONSTITUTIONAL VAULT REPORT";
        req.includeQr = true;
        req.includeHash = true;
        req.mode = PDFSealer.DocumentMode.FORENSIC_REPORT;
        req.evidenceHash = evidenceHash;
        req.caseId = caseId;
        req.jurisdiction = safeValue(jurisdictionName) + " (" + safeValue(jurisdictionCode) + ")";
        req.summary = "Long-form constitutional report generated from sealed findings only and stored as a separate vault artifact.";
        req.legalSummary = "This report is generated from sealed findings, audit, and vault artifacts only. It stays subordinate to the sealed evidence and the findings package.";
        req.bodyText = constitutionalReport;
        req.sourceFileName = sourceFileName;
        if (intakeSnapshot != null) {
            req.intakeMetadata = "UTC " + intakeSnapshot.capturedAtUtc
                    + " | Local " + intakeSnapshot.localTime
                    + " | TZ " + intakeSnapshot.timezoneId
                    + " | GPS " + intakeSnapshot.coordinatesLabel();
        }
        PDFSealer.generateSealedPdf(this, req, outFile);
        VaultManager.writeSealManifest(
                this,
                outFile,
                "constitutional-vault-report",
                caseId,
                evidenceHash
        );
        return outFile;
    }

    private File writeContradictionEngineReportToPath(
            File outFile,
            String sourceFileName,
            String caseId,
            String evidenceHash,
            String jurisdictionName,
            String jurisdictionCode,
            String contradictionReport
    ) throws Exception {
        PDFSealer.SealRequest req = new PDFSealer.SealRequest();
        req.title = "VERUM OMNIS - CONTRADICTION ENGINE REPORT";
        req.includeQr = true;
        req.includeHash = true;
        req.mode = PDFSealer.DocumentMode.FORENSIC_REPORT;
        req.evidenceHash = evidenceHash;
        req.caseId = caseId;
        req.jurisdiction = safeValue(jurisdictionName) + " (" + safeValue(jurisdictionCode) + ")";
        req.summary = "Direct contradiction-engine ledger generated from the sealed record without publication-layer role rewriting.";
        req.legalSummary = "This artifact publishes the contradiction engine directly. It does not infer harmed-party labels unless those labels are separately anchored in the sealed record.";
        req.bodyText = contradictionReport;
        req.sourceFileName = sourceFileName;
        if (intakeSnapshot != null) {
            req.intakeMetadata = "UTC " + intakeSnapshot.capturedAtUtc
                    + " | Local " + intakeSnapshot.localTime
                    + " | TZ " + intakeSnapshot.timezoneId
                    + " | GPS " + intakeSnapshot.coordinatesLabel();
        }
        PDFSealer.generateSealedPdf(this, req, outFile);
        VaultManager.writeSealManifest(
                this,
                outFile,
                "contradiction-engine-report",
                caseId,
                evidenceHash
        );
        return outFile;
    }

    private File writeLegalAdvisoryToVault(
            AnalysisEngine.ForensicReport report,
            String legalAdvisory
    ) throws Exception {
        if (legalAdvisory == null || legalAdvisory.trim().isEmpty()) {
            return null;
        }

        File outFile = VaultManager.createVaultFile(
                this,
                "legal-advisory",
                ".legal.json",
                selectedFile != null ? selectedFile.getName() : null
        );

        JSONObject root = new JSONObject();
        root.put("caseId", safeValue(report.caseId));
        root.put("sourceFileName", selectedFile != null ? selectedFile.getName() : "unknown");
        root.put("jurisdiction", safeValue(report.jurisdiction));
        root.put("jurisdictionName", safeValue(report.jurisdictionName));
        root.put("evidenceHash", safeValue(report.evidenceHash));
        root.put("generatedAt", java.time.Instant.now().toString());
        root.put("advisoryText", legalAdvisory.trim());
        root.put("grounding", buildLegalAdvisoryGrounding(report));

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }

        VaultManager.writeSealManifest(
                this,
                outFile,
                "legal-advisory",
                report.caseId,
                report.evidenceHash
        );

        return outFile;
    }

    private File writeModelAuditLedgerToVault(
            AnalysisEngine.ForensicReport report,
            String modelAuditLedger
    ) throws Exception {
        if (modelAuditLedger == null || modelAuditLedger.trim().isEmpty()) {
            return null;
        }

        File outFile = VaultManager.createVaultFile(
                this,
                "bounded-model-audit-ledger",
                ".json",
                selectedFile != null ? selectedFile.getName() : null
        );

        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(modelAuditLedger.trim().getBytes(StandardCharsets.UTF_8));
        }

        VaultManager.writeSealManifest(
                this,
                outFile,
                "bounded-model-audit-ledger",
                report.caseId,
                report.evidenceHash
        );

        return outFile;
    }

    private JSONObject buildLegalAdvisoryGrounding(AnalysisEngine.ForensicReport report) {
        JSONObject root = new JSONObject();
        try {
            LegalGrounding grounding = new LegalGrounding(this);
            LegalGrounding.PromptPackage promptPackage = grounding.buildPromptPackage(report);

            root.put("caseId", safeValue(promptPackage.data.caseId));
            root.put("evidenceHash", safeValue(promptPackage.data.evidenceHash));
            root.put("jurisdictionCode", safeValue(promptPackage.pack.code));
            root.put("jurisdictionName", safeValue(promptPackage.pack.name));
            root.put("guardianApproved", promptPackage.data.guardianApproved);
            root.put("guardianReason", safeValue(promptPackage.data.guardianReason));
            root.put("summary", safeValue(promptPackage.data.summary));
            root.put("topLiabilities", new JSONArray(promptPackage.data.topLiabilities));
            root.put("legalReferences", new JSONArray(promptPackage.data.legalReferences));
            root.put("diagnostics", new JSONArray(promptPackage.data.diagnostics));

            JSONArray certified = new JSONArray();
            for (LegalGrounding.Finding finding : promptPackage.data.certifiedFindings) {
                JSONObject item = new JSONObject();
                item.put("summary", safeValue(finding.summary));
                item.put("excerpt", safeValue(finding.excerpt));
                item.put("anchor", safeValue(finding.anchor));
                certified.put(item);
            }
            root.put("certifiedFindings", certified);
            root.put("retrievalQueries", new JSONArray(promptPackage.queries));

            JSONArray docs = new JSONArray();
            for (LegalGrounding.RetrievedDocument doc : promptPackage.docs) {
                JSONObject item = new JSONObject();
                item.put("id", safeValue(doc.id));
                item.put("title", safeValue(doc.title));
                item.put("category", safeValue(doc.category));
                item.put("source", safeValue(doc.source));
                item.put("rank", doc.rank);
                item.put("score", doc.score);
                item.put("text", safeValue(doc.text));
                docs.put(item);
            }
            root.put("retrievedDocuments", docs);
        } catch (Exception e) {
            try {
                root.put("groundingError", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } catch (Exception ignored) {
            }
        }
        return root;
    }



    private String buildVisualFindingsMemo(AnalysisEngine.ForensicReport report) {

        StringBuilder sb = new StringBuilder();

        sb.append("VERUM OMNIS VISUAL FORENSICS MEMO\n")

                .append("Case ID: ").append(report.caseId).append("\n")

                .append("Source File: ").append(selectedFile != null ? selectedFile.getName() : "unknown").append("\n")

                .append("Evidence SHA-512: ").append(report.evidenceHash).append("\n")

                .append("Jurisdiction: ").append(report.jurisdictionName).append(" (").append(report.jurisdiction).append(")\n");

        if (report.nativeEvidence == null) {

            sb.append("\nNo native evidence payload was available.\n");

            return sb.toString();

        }

        sb.append("\nPipeline Coverage\n")

                .append("Pages: ").append(report.nativeEvidence.optInt("pageCount", 0)).append("\n")

                .append("Rendered Pages: ").append(report.nativeEvidence.optInt("renderedPageCount", 0)).append("\n")

                .append("Document Text Pages: ").append(report.nativeEvidence.optInt("documentTextPageCount", 0)).append("\n")

                .append("OCR Success Count: ").append(report.nativeEvidence.optInt("ocrSuccessCount", 0)).append("\n")

                .append("OCR Failed Count: ").append(report.nativeEvidence.optInt("ocrFailedCount", 0)).append("\n");

        JSONArray visualFindings = report.nativeEvidence.optJSONArray("visualFindings");
        sb.append("\nVisual Findings\n");
        if (visualFindings == null || visualFindings.length() == 0) {

            sb.append("No visual tamper findings were recorded.\n");

        } else {
            List<JSONObject> filteredVisualFindings = new ArrayList<>();
            LinkedHashSet<String> seenKeys = new LinkedHashSet<>();
            for (int i = 0; i < visualFindings.length(); i++) {
                JSONObject finding = visualFindings.optJSONObject(i);
                if (finding == null) continue;
                String severity = finding.optString("severity", "info").toLowerCase(Locale.US);
                if ("info".equals(severity) || "low".equals(severity)) {
                    continue;
                }
                String type = finding.optString("type", "UNKNOWN").trim();
                int page = finding.optInt("page", 0);
                String key = page + "|" + type;
                if (!seenKeys.add(key)) {
                    continue;
                }
                filteredVisualFindings.add(finding);
            }

            Collections.sort(filteredVisualFindings, Comparator
                    .comparingInt((JSONObject item) -> visualSeverityRank(item.optString("severity", "info")))
                    .reversed()
                    .thenComparingInt(item -> item.optInt("page", Integer.MAX_VALUE)));

            if (filteredVisualFindings.isEmpty()) {
                sb.append("No medium-or-higher visual tamper indicators survived memo filtering.\n");
            } else {
                LinkedHashMap<String, List<JSONObject>> grouped = new LinkedHashMap<>();
                for (JSONObject finding : filteredVisualFindings) {
                    String type = finding.optString("type", "UNKNOWN");
                    if ("SIGNATURE_MARKS_PRESENT".equals(type) || "POSSIBLE_OVERLAY_REGION".equals(type)) {
                        continue;
                    }
                    String key = finding.optString("type", "UNKNOWN") + "|" + finding.optString("severity", "info");
                    grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(finding);
                }
                int emittedGroups = 0;
                for (Map.Entry<String, List<JSONObject>> entry : grouped.entrySet()) {
                    if (emittedGroups >= 10) {
                        break;
                    }
                    List<JSONObject> groupItems = entry.getValue();
                    if (groupItems.isEmpty()) {
                        continue;
                    }
                    JSONObject first = groupItems.get(0);
                    String type = first.optString("type", "UNKNOWN");
                    String description = first.optString("description", "");
                    if ("SIGNATURE_ZONE_OVERLAY_SUSPECTED".equals(type)) {
                        description = "Forgery finding: the signature zone carries a high-severity overlay anomaly consistent with pasted, masked, or whiteout content.";
                    } else if ("SIGNATURE_REGION_EMPTY".equals(type) || "SIGNATURE_MARKS_NOT_FOUND".equals(type)) {
                        description = "Execution finding: the expected signing region does not show a normal signature block or stroke pattern.";
                    }
                    sb.append("\n- Pages ").append(joinVisualPages(groupItems))
                            .append(" [").append(first.optString("severity", "info")).append("] ")
                            .append(type)
                            .append("\n  ").append(description);
                    emittedGroups++;
                }
            }

            sb.append("\n");

        }

        JSONArray anchors = report.nativeEvidence.optJSONArray("anchors");
        sb.append("\nEvidence Anchors\n");
        if (anchors == null || anchors.length() == 0) {

            sb.append("No anchors were recorded.\n");

        } else {

            for (int i = 0; i < anchors.length(); i++) {

                JSONObject anchor = anchors.optJSONObject(i);

                if (anchor == null) continue;

                String type = anchor.optString("type", "");

                if (!type.startsWith("VF") && !"PDF_TEXT".equals(type) && !"OCR_BLOCK".equals(type)) {

                    continue;

                }

                sb.append("\n- ").append(anchor.optString("evidenceId", "EV"))

                        .append(" / page ").append(anchor.optInt("page", 0))

                        .append(" / ").append(type)

                        .append("\n  ").append(anchor.optString("excerpt", ""));

            }

            sb.append("\n");

        }

        return sb.toString();

    }

    private int visualSeverityRank(String severity) {
        if (severity == null) {
            return 0;
        }
        switch (severity.trim().toLowerCase(Locale.US)) {
            case "high":
                return 3;
            case "medium":
                return 2;
            case "low":
                return 1;
            default:
                return 0;
        }
    }

    private String joinVisualPages(List<JSONObject> items) {
        List<Integer> pages = new ArrayList<>();
        for (JSONObject item : items) {
            int page = item.optInt("page", 0);
            if (page > 0 && !pages.contains(page)) {
                pages.add(page);
            }
        }
        Collections.sort(pages);
        if (pages.isEmpty()) {
            return "not stated";
        }
        List<String> ranges = new ArrayList<>();
        int start = pages.get(0);
        int prev = start;
        for (int i = 1; i < pages.size(); i++) {
            int current = pages.get(i);
            if (current == prev + 1) {
                prev = current;
                continue;
            }
            ranges.add(start == prev ? String.valueOf(start) : start + "-" + prev);
            start = current;
            prev = current;
        }
        ranges.add(start == prev ? String.valueOf(start) : start + "-" + prev);
        return joinStringList(ranges);
    }



    private File writeSealedEvidenceToVault(AnalysisEngine.ForensicReport report) throws Exception {

        File outFile = VaultManager.createVaultFile(
                this,
                "sealed-evidence",
                ".pdf",
                selectedFile != null ? selectedFile.getName() : null
        );

        PDFSealer.SealRequest req = new PDFSealer.SealRequest();

        req.title = "Verum Omnis Sealed Evidence";

        req.summary = "Uploaded evidence sealed with constitutional watermark, QR verification, and SHA-512 integrity lock.";

        req.includeQr = true;

        req.includeHash = true;

        req.mode = PDFSealer.DocumentMode.SEAL_ONLY;

        req.evidenceHash = report.evidenceHash;

        req.caseId = report.caseId;

        req.sourceFileName = selectedFile != null ? selectedFile.getName() : "unknown";

        req.jurisdiction = report.jurisdictionName + " (" + report.jurisdiction + ")";

        if (intakeSnapshot != null) {

            req.intakeMetadata = "UTC " + intakeSnapshot.capturedAtUtc

                    + " | Local " + intakeSnapshot.localTime

                    + " | TZ " + intakeSnapshot.timezoneId

                    + " | GPS " + intakeSnapshot.coordinatesLabel();

        }

        JSONObject nativeEvidence = report != null && report.nativeEvidence != null
                ? report.nativeEvidence
                : new JSONObject();
        JSONArray sourceFiles = nativeEvidence.optJSONArray("sourceFiles");
        boolean mergedEvidenceSet = "merged".equalsIgnoreCase(nativeEvidence.optString("pipelineStatus", ""))
                || (sourceFiles != null && sourceFiles.length() > 1);
        if (mergedEvidenceSet) {
            req.summary = "Merged evidence bag sealed as one indexed constitutional evidence record for the full scan batch.";
            req.sourceFileName = buildEvidenceSelectionLabel(getSelectedEvidenceFilesSnapshot());
            req.legalSummary = "This sealed evidence bundle was generated from the full multi-file scan session. The merged evidence page numbers below are the authoritative references for the attached reports.";
            req.bodyText = buildMergedSealedEvidenceBody(report, nativeEvidence, sourceFiles);
            PDFSealer.generateSealedPdf(this, req, outFile);
            VaultManager.writeSealManifest(
                    this,
                    outFile,
                    "sealed-evidence",
                    report.caseId,
                    report.evidenceHash
            );
            return outFile;
        }



        PDFSealer.SealInspection sealInspection = PDFSealer.inspectExistingSeal(selectedFile);
        if (sealInspection.alreadySealed) {
            req.title = "Verum Omnis Prior Seal Validation";
            req.summary = "A prior Verum seal was detected on the uploaded evidence. The source artifact was preserved without resealing, and this certificate records the validation result.";
            req.legalSummary = sealInspection.validationNote;
            req.bodyText = "Prior seal detected: yes\n"
                    + "Reseal skipped: yes\n"
                    + "Current artifact SHA-512: " + safeValue(sealInspection.currentArtifactHash) + "\n"
                    + "Prior sealed evidence SHA-512: " + safeValue(sealInspection.priorEvidenceHash) + "\n"
                    + "Prior case ID: " + safeValue(sealInspection.priorCaseId) + "\n"
                    + "Prior source file: " + safeValue(sealInspection.priorSourceFileName) + "\n"
                    + "Validation note: " + safeValue(sealInspection.validationNote) + "\n";
            PDFSealer.generateSealedPdf(this, req, outFile);
            VaultManager.writeSealManifest(
                    this,
                    outFile,
                    "sealed-evidence",
                    report.caseId,
                    report.evidenceHash
            );
            return outFile;
        }

        boolean renderedSeal = canSealSourceDocument(selectedFile);

        if (renderedSeal) {

            PDFSealer.generateSealedSourceDocument(this, req, selectedFile, outFile);

        } else {

            req.legalSummary = "Source type cannot be visually re-rendered on-device, so this output falls back to a seal certificate.";

            PDFSealer.generateSealedPdf(this, req, outFile);

        }

        VaultManager.writeSealManifest(
                this,
                outFile,
                "sealed-evidence",
                report.caseId,
                report.evidenceHash
        );

        return outFile;

    }

    private String buildMergedSealedEvidenceBody(
            AnalysisEngine.ForensicReport report,
            JSONObject nativeEvidence,
            JSONArray sourceFiles
    ) {
        return buildMergedSealedEvidenceBody(
                report != null ? safeValue(report.caseId) : "",
                report != null ? safeValue(report.evidenceHash) : "",
                nativeEvidence,
                sourceFiles
        );
    }

    private String buildMergedSealedEvidenceBody(
            String caseId,
            String evidenceHash,
            JSONObject nativeEvidence,
            JSONArray sourceFiles
    ) {
        StringBuilder sb = new StringBuilder();
        int sourceCount = sourceFiles != null ? sourceFiles.length() : 0;
        sb.append("MERGED SEALED EVIDENCE BAG\n");
        sb.append("This scan session contains ").append(sourceCount).append(" source file(s) sealed into one indexed evidence record.\n");
        sb.append("Case ID: ").append(safeValue(caseId)).append("\n");
        sb.append("Evidence SHA-512: ").append(safeValue(evidenceHash)).append("\n");
        sb.append("Merged evidence pages: ").append(nativeEvidence != null ? nativeEvidence.optInt("pageCount", 0) : 0).append("\n\n");

        sb.append("SOURCE FILE INDEX\n");
        if (sourceFiles != null) {
            for (int i = 0; i < sourceFiles.length(); i++) {
                JSONObject item = sourceFiles.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                sb.append(i + 1)
                        .append(". ")
                        .append(safeValue(item.optString("fileName", "unknown")))
                        .append(" | SHA-512 ")
                        .append(shortHashDisplay(item.optString("sha512", "")))
                        .append("\n");
            }
        }

        sb.append("\nMERGED EVIDENCE PAGES\n");
        JSONArray blocks = nativeEvidence != null ? nativeEvidence.optJSONArray("documentTextBlocks") : null;
        if (blocks == null || blocks.length() == 0) {
            sb.append("No merged document text blocks were available in this run.\n");
            return sb.toString();
        }
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject block = blocks.optJSONObject(i);
            if (block == null) {
                continue;
            }
            int page = block.optInt("page", i + 1);
            String text = clipReportText(block.optString("text", ""), 6000);
            if (text.trim().isEmpty()) {
                continue;
            }
            sb.append("\n--- MERGED EVIDENCE PAGE ").append(page).append(" ---\n");
            sb.append(text.trim()).append("\n");
        }
        return sb.toString();
    }

    private String shortHashDisplay(String hash) {
        String trimmed = safeValue(hash);
        if (trimmed.length() <= 16) {
            return trimmed;
        }
        return trimmed.substring(0, 16) + "...";
    }



    private File writeForensicReportToVault(AnalysisEngine.ForensicReport report, String humanReadableReport) throws Exception {
        File outFile = VaultManager.createVaultFile(
                this,
                "human-forensic-report",
                ".pdf",
                selectedFile != null ? selectedFile.getName() : null
        );
        return writeForensicReportToPath(
                outFile,
                selectedFile != null ? selectedFile.getName() : "unknown",
                report.caseId,
                report.evidenceHash,
                report.jurisdictionName,
                report.jurisdiction,
                humanReadableReport
        );

    }

    private File writeForensicReportToPath(
            File outFile,
            String sourceFileName,
            String caseId,
            String evidenceHash,
            String jurisdictionName,
            String jurisdictionCode,
            String humanReadableReport
    ) throws Exception {
        PDFSealer.SealRequest req = new PDFSealer.SealRequest();
        req.title = "VERUM OMNIS - CONSTITUTIONAL FORENSIC REPORT";
        req.includeQr = true;
        req.includeHash = true;
        req.mode = PDFSealer.DocumentMode.FORENSIC_REPORT;
        req.evidenceHash = evidenceHash;
        req.caseId = caseId;
        req.jurisdiction = safeValue(jurisdictionName) + " (" + safeValue(jurisdictionCode) + ")";
        req.summary = "Contradiction-led constitutional forensic report with separate forensic-department sections.";
        req.legalSummary = "Human-facing contradiction and forensic-department report generated from sealed findings and stored alongside the audit record.";
        req.bodyText = humanReadableReport;
        req.sourceFileName = sourceFileName;
        if (intakeSnapshot != null) {
            req.intakeMetadata = "UTC " + intakeSnapshot.capturedAtUtc
                    + " | Local " + intakeSnapshot.localTime
                    + " | TZ " + intakeSnapshot.timezoneId
                    + " | GPS " + intakeSnapshot.coordinatesLabel();
        }
        PDFSealer.generateSealedPdf(this, req, outFile);
        VaultManager.writeSealManifest(
                this,
                outFile,
                "human-forensic-report",
                caseId,
                evidenceHash
        );
        return outFile;
    }



    private File writeAuditReportToVault(AnalysisEngine.ForensicReport report, String auditReport) throws Exception {

        File outFile = VaultManager.createVaultFile(
                this,
                "forensic-audit-report",
                ".pdf",
                selectedFile != null ? selectedFile.getName() : null
        );

        return writeAuditReportToPath(
                outFile,
                selectedFile != null ? selectedFile.getName() : "unknown",
                report.caseId,
                report.evidenceHash,
                report.jurisdictionName,
                report.jurisdiction,
                auditReport
        );
    }

    private File writeAuditReportToPath(
            File outFile,
            String sourceFileName,
            String caseId,
            String evidenceHash,
            String jurisdictionName,
            String jurisdictionCode,
            String auditReport
    ) throws Exception {
        PDFSealer.SealRequest req = new PDFSealer.SealRequest();

        req.title = "Verum Omnis Forensic Audit Report";

        req.includeQr = true;

        req.includeHash = true;

        req.mode = PDFSealer.DocumentMode.FORENSIC_REPORT;

        req.evidenceHash = evidenceHash;

        req.caseId = caseId;

        req.jurisdiction = safeValue(jurisdictionName) + " (" + safeValue(jurisdictionCode) + ")";

        req.summary = "Detailed auditor report with page-anchored findings, contradictions, visual findings, and evidentiary registers.";

        req.legalSummary = "Auditor-facing extraction register generated from the forensic engine before user disclosure.";

        req.bodyText = auditReport;

        req.sourceFileName = sourceFileName;

        if (intakeSnapshot != null) {

            req.intakeMetadata = "UTC " + intakeSnapshot.capturedAtUtc

                    + " | Local " + intakeSnapshot.localTime

                    + " | TZ " + intakeSnapshot.timezoneId

                    + " | GPS " + intakeSnapshot.coordinatesLabel();

        }

        PDFSealer.generateSealedPdf(this, req, outFile);

        VaultManager.writeSealManifest(
                this,
                outFile,
                "forensic-audit-report",
                caseId,
                evidenceHash
        );

        return outFile;
    }



    private String buildGemmaChatContext(

            AnalysisEngine.ForensicReport report,

            String humanReadableReport,

            String auditReport,

            String findingsJson

    ) {

        String caseBrief = buildGemmaCaseBrief(report, null);
        StringBuilder sb = new StringBuilder();

        sb.append("Latest case context for Gemma.\n")

                .append("Case ID: ").append(report.caseId).append("\n")

                .append("Source file: ").append(selectedFile != null ? selectedFile.getName() : "unknown").append("\n")

                .append("Jurisdiction: ").append(report.jurisdictionName).append(" (").append(report.jurisdiction).append(")\n")

                .append("Processing status: ")

                .append(report.diagnostics != null ? report.diagnostics.optString("processingStatus", "DETERMINATE") : "DETERMINATE")

                .append("\n\nCompact case brief:\n")

                .append(clipText(caseBrief, 5200))

                .append("\n\nGemma narrative report excerpt:\n")

                .append(clipText(humanReadableReport, 2400))

                .append("\n\nAuditor forensic report excerpt:\n")

                .append(clipText(auditReport, 2600))

                .append("\n\nFindings package excerpt:\n")

                .append(clipText(findingsJson, 1800));

        return sb.toString();

    }



    private String buildForensicPdfSummary(AnalysisEngine.ForensicReport report) {

        return report.summary != null ? report.summary : "";

    }



    private void appendNativeEvidenceSection(StringBuilder sb, JSONObject nativeEvidence) {

        sb.append("\n=== Native Evidence Pipeline ===\n");

        sb.append("Pages: ").append(nativeEvidence.optInt("pageCount", 0));

        sb.append("\nRendered Pages: ").append(nativeEvidence.optInt("renderedPageCount", 0));

        sb.append("\nOCR Success Count: ").append(nativeEvidence.optInt("ocrSuccessCount", 0));

        sb.append("\nOCR Failed Count: ").append(nativeEvidence.optInt("ocrFailedCount", 0));



        JSONArray visualFindings = nativeEvidence.optJSONArray("visualFindings");

        if (visualFindings != null && visualFindings.length() > 0) {

            sb.append("\nVisual Findings:");

            for (int i = 0; i < visualFindings.length(); i++) {

                JSONObject finding = visualFindings.optJSONObject(i);

                if (finding == null) continue;

                sb.append("\n• Page ").append(finding.optInt("page", 0))

                        .append(" [").append(finding.optString("severity", "info")).append("] ")

                        .append(finding.optString("type", "UNKNOWN"))

                        .append(" @ ").append(finding.optString("region", "full-page"))

                        .append(": ").append(finding.optString("description", ""));

            }

        }



        JSONArray anchors = nativeEvidence.optJSONArray("anchors");

        if (anchors != null && anchors.length() > 0) {

            sb.append("\nEvidence Anchors:");

            for (int i = 0; i < Math.min(anchors.length(), 8); i++) {

                JSONObject anchor = anchors.optJSONObject(i);

                if (anchor == null) continue;

                sb.append("\n• ").append(anchor.optString("evidenceId", "EV"))

                        .append(" / page ").append(anchor.optInt("page", 0))

                        .append(" / ").append(anchor.optString("type", "ANCHOR"))

                        .append(": ").append(anchor.optString("excerpt", ""));

            }

        }

    }



    private HashMap<String, String> buildMeta() {

        HashMap<String, String> meta = new HashMap<>();

        meta.put("timestamp", intakeSnapshot != null ? intakeSnapshot.capturedAtUtc : "local-session");

        meta.put("device_model", Build.MANUFACTURER + " " + Build.MODEL);

        meta.put("android_version", Build.VERSION.RELEASE);

        if (intakeSnapshot != null) {

            meta.put("local_time", intakeSnapshot.localTime);

            meta.put("timezone", intakeSnapshot.timezoneId);

            meta.put("coordinates", intakeSnapshot.coordinatesLabel());

        }

        return meta;

    }



    private RulesEngine.Result buildRulesResultFromReport(AnalysisEngine.ForensicReport report) {

        RulesEngine.Result result = new RulesEngine.Result();

        result.riskScore = report.riskScore;

        boolean indeterminateDueToConcealment = report.diagnostics != null

                && report.diagnostics.optBoolean("indeterminateDueToConcealment", false);

        result.topLiabilities = report.topLiabilities != null

                ? report.topLiabilities

                : new String[]{indeterminateDueToConcealment

                ? "INDETERMINATE DUE TO CONCEALMENT"

                : "General risk"};

        result.diagnostics = report.diagnostics != null ? report.diagnostics : new JSONObject();

        result.constitutionalExtraction = report.constitutionalExtraction != null

                ? report.constitutionalExtraction : new JSONObject();

        return result;

    }



    private void publishBusyProgress(String message) {

        runOnUiThread(() -> setBusy(true, message));

    }



    private void setBusy(boolean busy, String message) {

        composeUiBusy = busy;
        AssistanceRestrictionManager.Snapshot restriction = AssistanceRestrictionManager.load(this);

        if (gemmaBtn != null) gemmaBtn.setEnabled(!busy && !restriction.restricted);
        if (legalAdvisoryToggle != null) legalAdvisoryToggle.setEnabled(!busy && !restriction.restricted);

        if (selectBtn != null) selectBtn.setEnabled(!busy && !restriction.restricted);
        if (newScanBtn != null) newScanBtn.setEnabled(!busy && !restriction.restricted);
        if (mergeScansBtn != null) mergeScansBtn.setEnabled(!busy && !restriction.restricted);

        if (sealBtn != null) sealBtn.setEnabled(!busy && !restriction.restricted);

        if (verifyQrBtn != null) verifyQrBtn.setEnabled(!busy);

        if (vaultBtn != null) vaultBtn.setEnabled(!busy);



        if (busy && message != null) {

            selectedFileView.setText(message);
            if (mainScreenBridge != null) {
                mainScreenBridge.setSelectedEvidenceText(message);
            }
            syncComposeState();

        } else if (!busy) {

            applyRestrictionState();

        }

    }



    @Override

    protected void onResume() {

        super.onResume();

        scrollLandingPageToTop();
        refreshCaseManagementSection();
        recoverPendingGemmaNarrativeIfNeeded();
        showLastVaultResultIfNeeded();

    }

    @Override

    protected void onPause() {

        if (introVideoView != null && introVideoView.isPlaying()) {

            introVideoView.pause();

            if (introVideoButton != null) {

                introVideoButton.setText(R.string.action_resume_intro_video);

            }

        }

        super.onPause();

    }



    @Override

    protected void onDestroy() {

        if (introVideoView != null) {

            introVideoView.stopPlayback();

        }

        super.onDestroy();


    }

}









