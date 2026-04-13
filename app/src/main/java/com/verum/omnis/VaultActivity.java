package com.verum.omnis;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.verum.omnis.casefile.CaseFile;
import com.verum.omnis.casefile.CaseFileManager;
import com.verum.omnis.casefile.ScanFolder;
import com.verum.omnis.casefile.ScanFolderManager;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class VaultActivity extends AppCompatActivity {

    private final LinkedHashSet<String> selectedScanFolderPaths = new LinkedHashSet<>();
    private final List<ScanFolder> currentScanFolders = new ArrayList<>();
    private volatile boolean mergeInProgress;

    private ScanFolderManager scanFolderManager;
    private CaseFileManager caseFileManager;

    private TextView vaultSummaryView;
    private TextView scanFoldersStatusView;
    private TextView caseFilesStatusView;
    private LinearLayout scanFoldersContainer;
    private LinearLayout caseFilesContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vault);

        scanFolderManager = new ScanFolderManager(this);
        caseFileManager = new CaseFileManager(this);

        vaultSummaryView = findViewById(R.id.vaultSummary);
        scanFoldersStatusView = findViewById(R.id.scanFoldersStatus);
        caseFilesStatusView = findViewById(R.id.caseFilesStatus);
        scanFoldersContainer = findViewById(R.id.scanFoldersContainer);
        caseFilesContainer = findViewById(R.id.caseFilesContainer);

        Button refreshBtn = findViewById(R.id.refreshVaultBtn);
        Button selectAllBtn = findViewById(R.id.selectAllScansBtn);
        Button mergeBtn = findViewById(R.id.mergeVaultScansBtn);
        Button backBtn = findViewById(R.id.backVaultBtn);

        refreshBtn.setOnClickListener(v -> refreshVault());
        selectAllBtn.setOnClickListener(v -> toggleSelectAllScans());
        mergeBtn.setOnClickListener(v -> promptMergeSelectedScans());
        backBtn.setOnClickListener(v -> finish());

        refreshVault();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshVault();
    }

    private void refreshVault() {
        currentScanFolders.clear();
        currentScanFolders.addAll(scanFolderManager.listScanFolders());
        List<CaseFile> caseFiles = caseFileManager.listCaseFiles();

        vaultSummaryView.setText(
                getString(
                        R.string.vault_summary_format,
                        currentScanFolders.size(),
                        caseFiles.size(),
                        scanFolderManager.getScanRootDir().getAbsolutePath(),
                        caseFileManager.getCaseRootDir().getAbsolutePath()
                )
        );
        scanFoldersStatusView.setText(getString(R.string.scan_folders_count_format, currentScanFolders.size()));
        caseFilesStatusView.setText(getString(R.string.case_files_count_format, caseFiles.size()));

        renderScanFolders(currentScanFolders);
        renderCaseFiles(caseFiles);
    }

    private void toggleSelectAllScans() {
        boolean selectAll = selectedScanFolderPaths.size() < currentScanFolders.size();
        selectedScanFolderPaths.clear();
        if (selectAll) {
            for (ScanFolder folder : currentScanFolders) {
                if (folder != null && folder.getFolderPath() != null) {
                    selectedScanFolderPaths.add(folder.getFolderPath());
                }
            }
        }
        renderScanFolders(currentScanFolders);
    }

    private void promptMergeSelectedScans() {
        if (getSelectedScanFolders().isEmpty()) {
            showMessage(getString(R.string.merge_scans_title), getString(R.string.merge_scans_none_selected));
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
                    List<ScanFolder> selected = getSelectedScanFolders();
                    if (selected.isEmpty()) {
                        showMessage(getString(R.string.merge_scans_title), getString(R.string.merge_scans_none_selected));
                        return;
                    }
                    String caseName = input.getText() != null ? input.getText().toString().trim() : "";
                    if (caseName.isEmpty()) {
                        caseName = "Merged Case";
                    }
                    mergeSelectedScans(selected, caseName);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void mergeSelectedScans(List<ScanFolder> selected, String caseName) {
        if (mergeInProgress) {
            showMessage(getString(R.string.merge_scans_title), getString(R.string.merging_scan_folders));
            return;
        }
        mergeInProgress = true;
        caseFilesStatusView.setText(getString(R.string.merging_scan_folders));
        new Thread(() -> {
            try {
                CaseFile caseFile = caseFileManager.mergeScanFolders(selected, caseName);
                runOnUiThread(() -> {
                    mergeInProgress = false;
                    selectedScanFolderPaths.clear();
                    refreshVault();
                    showMessage(
                            getString(R.string.case_file_created_title),
                            getString(R.string.case_file_created_message_format, caseFile.getName(), caseFile.getMergedFolderPath())
                    );
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    mergeInProgress = false;
                    refreshVault();
                    showMessage(getString(R.string.case_file_creation_failed), safeMessage(e));
                });
            }
        }, "vault-case-merge").start();
    }

    private List<ScanFolder> getSelectedScanFolders() {
        List<ScanFolder> selected = new ArrayList<>();
        for (ScanFolder folder : currentScanFolders) {
            if (folder != null
                    && folder.getFolderPath() != null
                    && selectedScanFolderPaths.contains(folder.getFolderPath())) {
                selected.add(folder);
            }
        }
        return selected;
    }

    private void renderScanFolders(List<ScanFolder> folders) {
        scanFoldersContainer.removeAllViews();
        if (folders == null || folders.isEmpty()) {
            scanFoldersContainer.addView(buildHintView(getString(R.string.scan_folders_empty)));
            return;
        }
        for (ScanFolder folder : folders) {
            LinearLayout wrapper = buildWrapper();

            CheckBox box = new CheckBox(this);
            box.setTextColor(ContextCompat.getColor(this, R.color.pure_white));
            box.setButtonTintList(ContextCompat.getColorStateList(this, R.color.verum_gold));
            int fileCount = visibleScanFiles(folder).size();
            box.setText(getString(
                    R.string.scan_folder_item_format,
                    safeValue(folder.getName()),
                    formatDate(folder.getScanDate()),
                    fileCount
            ));
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
            wrapper.addView(box);

            wrapper.addView(buildActionRow(
                    buildActionButton(getString(R.string.action_view_contents), v -> showScanContents(folder)),
                    buildActionButton(getString(R.string.action_export_scan), v -> exportScanFolder(folder)),
                    buildActionButton(getString(R.string.action_delete_scan), v -> confirmDeleteScanFolder(folder))
            ));
            scanFoldersContainer.addView(wrapper);
        }
    }

    private void renderCaseFiles(List<CaseFile> caseFiles) {
        caseFilesContainer.removeAllViews();
        if (caseFiles == null || caseFiles.isEmpty()) {
            caseFilesContainer.addView(buildHintView(getString(R.string.case_files_empty)));
            return;
        }
        for (CaseFile caseFile : caseFiles) {
            LinearLayout wrapper = buildWrapper();
            TextView summary = new TextView(this);
            summary.setTextColor(ContextCompat.getColor(this, R.color.pure_white));
            String bundleStatus = caseFile.getSealedBundlePath() == null || caseFile.getSealedBundlePath().trim().isEmpty()
                    ? getString(R.string.not_sealed_yet)
                    : "Ready";
            String narrativeStatus = caseFile.getSealedNarrativePath() == null || caseFile.getSealedNarrativePath().trim().isEmpty()
                    ? getString(R.string.not_sealed_yet)
                    : "Ready";
            summary.setText(
                    getString(
                            R.string.case_file_item_format,
                            safeValue(caseFile.getName()),
                            formatDate(caseFile.getCreationDate()),
                        caseFile.getScanFolders() != null ? caseFile.getScanFolders().size() : 0
                    ) + "\nBundle: " + bundleStatus
                            + "\nNarrative: " + narrativeStatus
            );
            wrapper.addView(summary);

            wrapper.addView(buildActionRow(
                    buildActionButton(getString(R.string.action_view_case_file), v -> openCaseFileArtifact(caseFile)),
                    buildActionButton(getString(R.string.action_export_case_file), v -> exportCaseFile(caseFile)),
                    buildActionButton(getString(R.string.action_delete_case_file), v -> confirmDeleteCaseFile(caseFile))
            ));
            caseFilesContainer.addView(wrapper);
        }
    }

    private void showScanContents(ScanFolder folder) {
        List<File> visibleFiles = visibleScanFiles(folder);
        if (visibleFiles.isEmpty()) {
            showMessage(getString(R.string.action_view_contents), getString(R.string.scan_folder_empty_contents));
            return;
        }
        List<String> labels = new ArrayList<>();
        String root = folder.getFolderPath() != null ? folder.getFolderPath() : "";
        for (File file : visibleFiles) {
            String label = file.getAbsolutePath().startsWith(root)
                    ? file.getAbsolutePath().substring(root.length()).replaceFirst("^[\\\\/]+", "")
                    : file.getName();
            labels.add(label);
        }
        new AlertDialog.Builder(this)
                .setTitle(folder.getName())
                .setItems(labels.toArray(new String[0]), (dialog, which) -> showStoredFileActions(visibleFiles.get(which)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private List<File> visibleScanFiles(ScanFolder folder) {
        List<File> files = new ArrayList<>();
        if (folder == null || folder.getFilePaths() == null) {
            return files;
        }
        for (String path : folder.getFilePaths()) {
            File file = new File(path);
            if (!file.exists() || file.isDirectory() || isInternalScanFile(file)) {
                continue;
            }
            files.add(file);
        }
        return files;
    }

    private boolean isInternalScanFile(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        return name.endsWith(".seal.json")
                || name.equals("scan_folder.json")
                || name.equals("scan_manifest.json")
                || name.endsWith("-seal-manifest.json")
                || name.equals("audit_report.txt")
                || name.equals("findings_snapshot.json")
                || name.equals("human_report_snapshot.txt")
                || name.equals("rnd-mesh.json");
    }

    private void showStoredFileActions(File file) {
        String[] actions = new String[]{
                getString(R.string.action_open),
                getString(R.string.action_export),
                getString(R.string.action_cancel)
        };
        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        openFile(file);
                    } else if (which == 1) {
                        shareFile(file);
                    } else {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void confirmDeleteCaseFile(CaseFile caseFile) {
        if (caseFile == null || caseFile.getMergedFolderPath() == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_delete_case_file)
                .setMessage(getString(R.string.delete_case_file_confirm_format, caseFile.getName()))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    File root = new File(caseFile.getMergedFolderPath());
                    if (!deleteRecursively(root)) {
                        showMessage(getString(R.string.delete_failed), root.getAbsolutePath());
                        return;
                    }
                    refreshVault();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmDeleteScanFolder(ScanFolder folder) {
        if (folder == null || folder.getFolderPath() == null || folder.getFolderPath().trim().isEmpty()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_delete_scan)
                .setMessage(getString(R.string.delete_scan_confirm_format, safeValue(folder.getName())))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    File root = new File(folder.getFolderPath());
                    if (!deleteRecursively(root)) {
                        showMessage(getString(R.string.delete_failed), root.getAbsolutePath());
                        return;
                    }
                    selectedScanFolderPaths.remove(folder.getFolderPath());
                    refreshVault();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void openCaseFileArtifact(CaseFile caseFile) {
        List<File> files = collectCaseFileArtifacts(caseFile);
        if (files.isEmpty() && caseFile != null && caseFile.getMergedForensicJsonPath() != null) {
            try {
                caseFileManager.sealCaseFile(caseFile);
                files = collectCaseFileArtifacts(caseFile);
            } catch (Exception ignored) {
            }
        }
        if (files.isEmpty()) {
            showMessage(getString(R.string.open_failed), getString(R.string.case_file_unavailable));
            return;
        }
        File primaryBundle = findPrimaryCaseBundle(caseFile, files);
        if (primaryBundle != null) {
            showStoredFileActions(primaryBundle);
            return;
        }
        final List<File> resolvedFiles = files;
        String[] labels = new String[files.size()];
        for (int i = 0; i < files.size(); i++) {
            File file = files.get(i);
            labels[i] = caseArtifactLabel(file) + "\n" + buildSubtitle(file);
        }
        new AlertDialog.Builder(this)
                .setTitle(caseFile.getName())
                .setItems(labels, (dialog, which) -> showStoredFileActions(resolvedFiles.get(which)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private List<File> collectCaseFileArtifacts(CaseFile caseFile) {
        List<File> files = new ArrayList<>();
        if (caseFile == null) {
            return files;
        }
        addIfExists(files, caseFile.getSealedBundlePath());
        addIfExists(files, caseFile.getSealedNarrativePath());
        if (caseFile.getMergedFolderPath() != null && !caseFile.getMergedFolderPath().trim().isEmpty()) {
            File root = new File(caseFile.getMergedFolderPath());
            collectPdfArtifacts(root, files);
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

    private void collectPdfArtifacts(File file, List<File> files) {
        if (file == null || files == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                collectPdfArtifacts(child, files);
            }
            return;
        }
        if (file.getName().toLowerCase(Locale.US).endsWith(".pdf")) {
            addIfExists(files, file.getAbsolutePath());
        }
    }

    private void addIfExists(List<File> files, String path) {
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

    private void exportScanFolder(ScanFolder folder) {
        List<File> visibleFiles = visibleScanFiles(folder);
        if (visibleFiles.isEmpty()) {
            showMessage(getString(R.string.action_export_scan), getString(R.string.scan_folder_empty_contents));
            return;
        }
        shareFiles(visibleFiles, getString(R.string.action_export_scan), safeValue(folder.getName()));
    }

    private void exportCaseFile(CaseFile caseFile) {
        List<File> files = collectPrimaryCaseExportArtifacts(caseFile);
        if (files.isEmpty() && caseFile != null && caseFile.getMergedForensicJsonPath() != null) {
            try {
                caseFileManager.sealCaseFile(caseFile);
                files = collectPrimaryCaseExportArtifacts(caseFile);
            } catch (Exception ignored) {
            }
        }
        if (files.isEmpty()) {
            showMessage(
                    getString(R.string.case_file_creation_failed),
                    "The merged case package is not ready yet. The export must include the sealed case bundle and sealed narrative, not just the underlying scan PDFs."
            );
            return;
        }
        shareFiles(files, getString(R.string.action_export_case_file), safeValue(caseFile.getName()));
    }

    private List<File> collectPrimaryCaseExportArtifacts(CaseFile caseFile) {
        List<File> files = new ArrayList<>();
        if (caseFile == null) {
            return files;
        }
        addIfExists(files, caseFile.getSealedBundlePath());
        addIfExists(files, caseFile.getSealedNarrativePath());
        if (files.isEmpty() && caseFile.getMergedFolderPath() != null && !caseFile.getMergedFolderPath().trim().isEmpty()) {
            File caseRoot = new File(caseFile.getMergedFolderPath());
            addIfExists(files, new File(caseRoot, "sealed_case_bundle.pdf").getAbsolutePath());
            addIfExists(files, new File(caseRoot, "sealed_narrative.pdf").getAbsolutePath());
        }
        return files;
    }

    private void shareFiles(List<File> files, String chooserTitle, String subject) {
        if (files == null || files.isEmpty()) {
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        for (File file : files) {
            uris.add(FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file));
        }
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("application/pdf");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, chooserTitle));
    }

    private LinearLayout buildWrapper() {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = 18;
        wrapper.setLayoutParams(params);
        return wrapper;
    }

    private LinearLayout buildActionRow(Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = 10;
        row.setLayoutParams(rowParams);
        for (int i = 0; i < buttons.length; i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            if (i < buttons.length - 1) {
                params.rightMargin = 8;
            }
            row.addView(buttons[i], params);
        }
        return row;
    }

    private Button buildActionButton(String text, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(20, 16, 20, 16);
        button.setOnClickListener(listener);
        return button;
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

    private TextView buildHintView(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(this, R.color.verum_text_soft));
        return view;
    }

    private String buildSubtitle(File file) {
        double sizeKb = Math.max(1d, file.length() / 1024d);
        DecimalFormat sizeFormat = new DecimalFormat("0.0");
        String modified = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date(file.lastModified()));
        return sizeFormat.format(sizeKb) + " KB  |  " + modified;
    }

    private String formatDate(LocalDateTime date) {
        if (date == null) {
            return "Unknown";
        }
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private void shareFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(getMimeType(file));
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_export)));
        } catch (Exception e) {
            showMessage(getString(R.string.export_failed), safeMessage(e));
        }
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
            startActivity(Intent.createChooser(intent, getString(R.string.action_open)));
        } catch (Exception e) {
            showMessage(getString(R.string.open_failed), file.getAbsolutePath() + "\n\n" + safeMessage(e));
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

    private boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return true;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    private String safeMessage(Exception e) {
        if (e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()) {
            return e == null ? "unknown error" : e.getClass().getSimpleName();
        }
        return e.getMessage().trim();
    }

    private String safeValue(String value) {
        return value == null || value.trim().isEmpty() ? "N/A" : value.trim();
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
