package com.gbalocalizer.vn.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gbalocalizer.vn.R;
import com.gbalocalizer.vn.core.GbaCodec;
import com.gbalocalizer.vn.core.GbaPatcher;
import com.gbalocalizer.vn.core.GbaScanner;
import com.gbalocalizer.vn.core.ScriptItem;
import com.gbalocalizer.vn.core.VnTranslator;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StudioActivity extends AppCompatActivity {

    private static final int RC_OPEN_ROM = 201;
    private static final int RC_OPEN_TBL = 202;
    private static final int RC_SAVE_ROM = 203;
    private static final int RC_SAVE_JSON = 204;
    private static final int RC_LOAD_JSON = 205;

    private View panelRom, panelTranslate, panelEditor;
    private TextView txtHeaderSub, badgeOffline, txtPackDetail, txtRomSummary, txtRomStatus;
    private TextView txtTransStats, txtTransSpeed, txtLiveFeed, txtEditorCount;
    private Button btnFetchPack, btnSelectRom, btnSelectTbl, btnScanRom, btnBuildRom;
    private Button btnStartTrans, btnStopTrans, btnSaveJson, btnLoadJson;
    private Spinner spnEncoding, spnFilterMode;
    private SwitchMaterial swNoAccents, swSingleWord;
    private EditText edtCols, edtBatchLimit, edtFilterQuery;
    private RadioButton rbOffline;
    private ProgressBar pbTranslate;
    private ScriptAdapter scriptAdapter;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final VnTranslator translator = new VnTranslator();
    private final StringBuilder feedBuf = new StringBuilder();

    private byte[] loadedRom;
    private String loadedRomName = "game.gba";
    private GbaCodec.TableMap customTbl;
    private GbaCodec.EncodingMode activeEncoding = GbaCodec.EncodingMode.GEN3;
    private final List<ScriptItem> scriptItems = new ArrayList<>();

    private volatile boolean busy = false;
    private volatile boolean stopSignal = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_studio);

        bindViews();
        setupSpinnersAndNav();
        setupListeners();
        refreshOfflinePackBadge();
        logFeed("Khoi dong GBA VN Studio thanh cong. Hay nap file ROM .gba.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSignal = true;
        translator.shutdown();
        worker.shutdownNow();
    }

    private void bindViews() {
        panelRom = findViewById(R.id.panelRom);
        panelTranslate = findViewById(R.id.panelTranslate);
        panelEditor = findViewById(R.id.panelEditor);

        txtHeaderSub = findViewById(R.id.txtHeaderSub);
        badgeOffline = findViewById(R.id.badgeOffline);
        txtPackDetail = findViewById(R.id.txtPackDetail);
        txtRomSummary = findViewById(R.id.txtRomSummary);
        txtRomStatus = findViewById(R.id.txtRomStatus);

        txtTransStats = findViewById(R.id.txtTransStats);
        txtTransSpeed = findViewById(R.id.txtTransSpeed);
        txtLiveFeed = findViewById(R.id.txtLiveFeed);
        txtLiveFeed.setMovementMethod(new ScrollingMovementMethod());
        txtEditorCount = findViewById(R.id.txtEditorCount);

        btnFetchPack = findViewById(R.id.btnFetchPack);
        btnSelectRom = findViewById(R.id.btnSelectRom);
        btnSelectTbl = findViewById(R.id.btnSelectTbl);
        btnScanRom = findViewById(R.id.btnScanRom);
        btnBuildRom = findViewById(R.id.btnBuildRom);
        btnStartTrans = findViewById(R.id.btnStartTrans);
        btnStopTrans = findViewById(R.id.btnStopTrans);
        btnSaveJson = findViewById(R.id.btnSaveJson);
        btnLoadJson = findViewById(R.id.btnLoadJson);

        spnEncoding = findViewById(R.id.spnEncoding);
        spnFilterMode = findViewById(R.id.spnFilterMode);
        swNoAccents = findViewById(R.id.swNoAccents);
        swSingleWord = findViewById(R.id.swSingleWord);
        edtCols = findViewById(R.id.edtCols);
        edtBatchLimit = findViewById(R.id.edtBatchLimit);
        edtFilterQuery = findViewById(R.id.edtFilterQuery);
        rbOffline = findViewById(R.id.rbOffline);
        pbTranslate = findViewById(R.id.pbTranslate);

        RecyclerView rv = findViewById(R.id.rvScripts);
        rv.setLayoutManager(new LinearLayoutManager(this));
        scriptAdapter = new ScriptAdapter(this::openEditDialog);
        rv.setAdapter(scriptAdapter);
    }

    private void setupSpinnersAndNav() {
        String[] encNames = new String[GbaCodec.EncodingMode.values().length];
        for (int i = 0; i < encNames.length; i++) {
            encNames[i] = GbaCodec.EncodingMode.values()[i].displayName;
        }
        ArrayAdapter<String> encAdp = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, encNames);
        encAdp.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnEncoding.setAdapter(encAdp);
        spnEncoding.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                btnSelectTbl.setVisibility(pos == GbaCodec.EncodingMode.CUSTOM_TBL.ordinal() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });

        String[] filters = {"Tat ca", "Da dich", "Chua dich", "Co bien {FD}"};
        ArrayAdapter<String> fltAdp = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, filters);
        fltAdp.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnFilterMode.setAdapter(fltAdp);
        spnFilterMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                refreshEditorList();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            panelRom.setVisibility(id == R.id.nav_rom ? View.VISIBLE : View.GONE);
            panelTranslate.setVisibility(id == R.id.nav_translate ? View.VISIBLE : View.GONE);
            panelEditor.setVisibility(id == R.id.nav_editor ? View.VISIBLE : View.GONE);
            if (id == R.id.nav_translate) updateTranslateDashboard();
            if (id == R.id.nav_editor) refreshEditorList();
            return true;
        });
    }

    private void setupListeners() {
        btnFetchPack.setOnClickListener(v -> downloadOfflinePack());
        btnSelectRom.setOnClickListener(v -> launchOpenDoc(RC_OPEN_ROM));
        btnSelectTbl.setOnClickListener(v -> launchOpenDoc(RC_OPEN_TBL));
        btnScanRom.setOnClickListener(v -> scanLoadedRom());
        btnBuildRom.setOnClickListener(v -> {
            if (loadedRom == null || scriptItems.isEmpty()) {
                toast("Hay nap ROM va quet text truoc.");
                return;
            }
            String outName = loadedRomName.replace(".gba", "").replace(".GBA", "") + "_vn.gba";
            launchSaveDoc(RC_SAVE_ROM, outName, "application/octet-stream");
        });

        btnStartTrans.setOnClickListener(v -> startTranslation());
        btnStopTrans.setOnClickListener(v -> {
            stopSignal = true;
            txtTransSpeed.setText("Dang dung an toan...");
        });

        btnSaveJson.setOnClickListener(v -> {
            if (scriptItems.isEmpty()) {
                toast("Chua co du lieu de xuat JSON.");
                return;
            }
            String outName = loadedRomName.replace(".gba", "").replace(".GBA", "") + "_vn.json";
            launchSaveDoc(RC_SAVE_JSON, outName, "application/json");
        });

        btnLoadJson.setOnClickListener(v -> {
            if (scriptItems.isEmpty()) {
                toast("Hay nap ROM va quet text truoc khi nhap JSON.");
                return;
            }
            launchOpenDoc(RC_LOAD_JSON);
        });

        edtFilterQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshEditorList(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void refreshOfflinePackBadge() {
        worker.execute(() -> {
            try {
                boolean ready = translator.isOfflinePackReady();
                ui.post(() -> applyPackUi(ready));
            } catch (Exception ignored) {}
        });
    }

    private void applyPackUi(boolean ready) {
        if (ready) {
            badgeOffline.setText("VN Offline: SAN SANG");
            badgeOffline.setTextColor(ContextCompat.getColor(this, R.color.accent_emerald));
            txtPackDetail.setText("Goi ngon ngu Anh-Viet (~30MB) da co tren may. Dich khong can mang.");
            btnFetchPack.setText("Da tai goi Tieng Viet (San sang)");
        } else {
            badgeOffline.setText("VN Offline: CHUA TAI");
            badgeOffline.setTextColor(ContextCompat.getColor(this, R.color.accent_amber));
            txtPackDetail.setText("Can tai 1 lan (~30MB) de dich Offline sieu toc.");
            btnFetchPack.setText("Tai goi Tieng Viet Offline (~30MB)");
        }
    }

    private void downloadOfflinePack() {
        setBusyState(true);
        txtRomStatus.setText("Dang tai goi ngon ngu Tieng Viet (~30MB)...");
        logFeed("[OFFLINE] Dang tai goi ngon ngu Anh -> Viet...");
        worker.execute(() -> {
            try {
                translator.downloadOfflinePack();
                ui.post(() -> {
                    applyPackUi(true);
                    txtRomStatus.setText("Tai xong goi Tieng Viet Offline!");
                    logFeed("[OFFLINE] San sang dich sieu nhanh khong can mang.");
                    setBusyState(false);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    txtRomStatus.setText("Loi tai goi: " + e.getMessage());
                    logFeed("[LOI] " + e.getMessage());
                    setBusyState(false);
                });
            }
        });
    }

    private void launchOpenDoc(int rc) {
        if (busy) {
            toast("Dang xu ly, vui long cho.");
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, rc);
    }

    private void launchSaveDoc(int rc, String name, String mime) {
        if (busy) {
            toast("Dang xu ly, vui long cho.");
            return;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, rc);
    }

    @Override
    protected void onActivityResult(int rc, int resultCode, Intent data) {
        super.onActivityResult(rc, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (rc == RC_OPEN_ROM) loadRom(uri);
        else if (rc == RC_OPEN_TBL) loadTbl(uri);
        else if (rc == RC_SAVE_ROM) savePatchedRom(uri);
        else if (rc == RC_SAVE_JSON) exportJson(uri);
        else if (rc == RC_LOAD_JSON) importJson(uri);
    }

    private byte[] readUriBytes(Uri uri) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) throw new Exception("Khong mo duoc tep.");
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private String resolveFileName(Uri uri) {
        String name = "rom.gba";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {}
        return name;
    }

    private void loadRom(Uri uri) {
        setBusyState(true);
        txtRomStatus.setText("Dang doc ROM...");
        worker.execute(() -> {
            try {
                String name = resolveFileName(uri);
                byte[] bytes = readUriBytes(uri);
                GbaScanner.RomMetadata meta = GbaScanner.analyzeRom(bytes);
                loadedRom = bytes;
                loadedRomName = name;
                scriptItems.clear();

                String info = String.format(
                        Locale.US,
                        "Tep: %s\nHeader: %s [%s] | %.2f MB\nVung trong 0xFF: %.2f MB | 0x00: %.2f MB",
                        name, meta.title, meta.gameCode,
                        meta.totalBytes / 1048576.0, meta.freeFF / 1048576.0, meta.free00 / 1048576.0
                );
                ui.post(() -> {
                    txtHeaderSub.setText(meta.title + " (" + meta.gameCode + ")");
                    txtRomSummary.setText(info);
                    txtRomStatus.setText("Da nap ROM. Hay bam Quet & Trich xuat.");
                    logFeed("[ROM] Da nap " + name + " (" + meta.title + ")");
                    updateTranslateDashboard();
                    setBusyState(false);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    txtRomStatus.setText("Loi doc ROM: " + e.getMessage());
                    setBusyState(false);
                });
            }
        });
    }

    private void loadTbl(Uri uri) {
        setBusyState(true);
        worker.execute(() -> {
            try {
                String raw = new String(readUriBytes(uri), StandardCharsets.UTF_8);
                customTbl = GbaCodec.parseTbl(raw);
                String name = resolveFileName(uri);
                ui.post(() -> {
                    txtRomStatus.setText("Da nap bang ma " + name + " (" + customTbl.byteToChar.size() + " ky tu).");
                    setBusyState(false);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    txtRomStatus.setText("Loi .tbl: " + e.getMessage());
                    setBusyState(false);
                });
            }
        });
    }

    private void scanLoadedRom() {
        if (loadedRom == null) {
            toast("Hay chon file ROM .gba truoc.");
            return;
        }
        GbaCodec.EncodingMode req = GbaCodec.EncodingMode.values()[spnEncoding.getSelectedItemPosition()];
        if (req == GbaCodec.EncodingMode.CUSTOM_TBL && customTbl == null) {
            toast("Hay nap file .tbl truoc.");
            return;
        }
        boolean includeSingle = swSingleWord.isChecked();
        setBusyState(true);
        txtRomStatus.setText("Dang quet con tro & loc van ban...");

        worker.execute(() -> {
            try {
                long t0 = System.currentTimeMillis();
                GbaScanner.ScanOutput out = GbaScanner.scanStrings(loadedRom, req, includeSingle, customTbl);
                double sec = (System.currentTimeMillis() - t0) / 1000.0;
                scriptItems.clear();
                scriptItems.addAll(out.items);
                activeEncoding = out.detectedMode;

                ui.post(() -> {
                    txtRomStatus.setText(String.format(
                            Locale.US,
                            "Quet xong: %,d chuoi (%s) trong %.2fs. Chuyen sang tab Dich Offline!",
                            scriptItems.size(), activeEncoding.displayName, sec
                    ));
                    logFeed(String.format(Locale.US, "[SCAN] Tim thay %,d chuoi (%s).", scriptItems.size(), activeEncoding.displayName));
                    updateTranslateDashboard();
                    refreshEditorList();
                    setBusyState(false);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    txtRomStatus.setText("Loi quet: " + e.getMessage());
                    setBusyState(false);
                });
            }
        });
    }

    private void startTranslation() {
        if (scriptItems.isEmpty()) {
            toast("Hay quet ROM o tab 1 truoc.");
            return;
        }
        boolean useOffline = rbOffline.isChecked();
        boolean noAccents = swNoAccents.isChecked();
        int cols = parseIntOr(edtCols.getText().toString(), 34, 10);
        int limit = parseIntOr(edtBatchLimit.getText().toString(), 0, 0);

        List<ScriptItem> pending = new ArrayList<>();
        for (ScriptItem item : scriptItems) {
            if (!item.isTranslated()) pending.add(item);
        }
        if (pending.isEmpty()) {
            toast("Tat ca chuoi da duoc dich!");
            return;
        }
        int targetCount = (limit > 0 && limit < pending.size()) ? limit : pending.size();

        stopSignal = false;
        setBusyState(true);
        btnStopTrans.setEnabled(true);
        logFeed("[DICH] Bat dau dich " + targetCount + " chuoi (" + (useOffline ? "Offline ML Kit" : "Google Online") + ")...");

        worker.execute(() -> {
            int done = 0;
            try {
                if (useOffline && !translator.isOfflinePackReady()) {
                    ui.post(() -> txtTransSpeed.setText("Dang tai goi Tieng Viet Offline (~30MB)..."));
                    translator.downloadOfflinePack();
                    ui.post(() -> applyPackUi(true));
                }

                long t0 = System.currentTimeMillis();
                for (int i = 0; i < targetCount && !stopSignal; i++) {
                    ScriptItem item = pending.get(i);
                    String rawVi = useOffline
                            ? translator.translateOffline(item.sourceText)
                            : translator.translateOnlineGoogle(item.sourceText);
                    item.translatedText = VnTranslator.formatForGba(rawVi, noAccents, item.isMultiline, cols, activeEncoding);
                    done++;

                    if (!useOffline && !stopSignal) {
                        Thread.sleep(220);
                    }

                    if (done <= 15 || (done % 40) == 0 || done == targetCount) {
                        final int cur = done;
                        final double elapsed = Math.max(0.2, (System.currentTimeMillis() - t0) / 1000.0);
                        final double speed = cur / elapsed;
                        final double etaSec = (targetCount - cur) / Math.max(0.5, speed);
                        final String preview = item.translatedText;
                        ui.post(() -> {
                            updateTranslateDashboard();
                            txtTransSpeed.setText(String.format(
                                    Locale.US,
                                    "Toc do: %.1f cau/s | Con ~%.0fs (%d/%d)",
                                    speed, etaSec, cur, targetCount
                            ));
                            if (cur <= 5 || (cur % 200) == 0 || cur == targetCount) {
                                String shortP = preview.length() > 60 ? preview.substring(0, 60) + "..." : preview;
                                logFeed("  [" + cur + "/" + targetCount + "] " + shortP);
                            }
                        });
                    }
                }

                final int finalDone = done;
                final boolean cancelled = stopSignal;
                ui.post(() -> {
                    updateTranslateDashboard();
                    refreshEditorList();
                    txtTransSpeed.setText(cancelled
                            ? "Da tam dung tai " + finalDone + "/" + targetCount + " cau."
                            : "Hoan tat dich " + finalDone + " cau! Quay lai tab 1 de xuat ROM .gba.");
                    logFeed(cancelled ? "[TAM DUNG] Da luu " + finalDone + " cau." : "[XONG] Hoan tat " + finalDone + " cau.");
                    btnStopTrans.setEnabled(false);
                    setBusyState(false);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    txtTransSpeed.setText("Loi dich: " + e.getMessage());
                    logFeed("[LOI DICH] " + e.getMessage());
                    btnStopTrans.setEnabled(false);
                    setBusyState(false);
                });
            }
        });
    }

    private void savePatchedRom(Uri uri) {
        setBusyState(true);
        txtRomStatus.setText("Dang dong goi & repoint ROM...");
        worker.execute(() -> {
            try {
                GbaPatcher.BuildReport rep = new GbaPatcher.BuildReport();
                byte[] out = GbaPatcher.buildPatchedRom(loadedRom, scriptItems, activeEncoding, customTbl, rep);
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new Exception("Khong ghi duoc tep.");
                    os.write(out);
                    os.flush();
                }
                String msg = String.format(
                        Locale.US,
                        "Xuat ROM thanh cong! Tai cho: %,d | Repoint: %,d | Bo qua: %,d | Thieu cho: %,d",
                        rep.writtenInPlace, rep.writtenRepointed, rep.skippedUntranslated, rep.failedNoSpace
                );
                ui.post(() -> {
                    txtRomStatus.setText(msg);
                    logFeed("[PATCH] " + msg);
                    toast("Da luu file ROM .gba Viet hoa!");
                    setBusyState(false);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    txtRomStatus.setText("Loi dong goi: " + e.getMessage());
                    setBusyState(false);
                });
            }
        });
    }

    private void exportJson(Uri uri) {
        setBusyState(true);
        worker.execute(() -> {
            try {
                String json = GbaPatcher.serializeProjectJson(loadedRomName, activeEncoding, scriptItems);
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new Exception("Khong ghi duoc JSON.");
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
                ui.post(() -> {
                    logFeed("[JSON] Da xuat " + scriptItems.size() + " chuoi.");
                    toast("Da luu file JSON!");
                    setBusyState(false);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    logFeed("[LOI JSON] " + e.getMessage());
                    setBusyState(false);
                });
            }
        });
    }

    private void importJson(Uri uri) {
        setBusyState(true);
        worker.execute(() -> {
            try {
                String json = new String(readUriBytes(uri), StandardCharsets.UTF_8);
                int count = GbaPatcher.mergeProjectJson(json, scriptItems);
                ui.post(() -> {
                    updateTranslateDashboard();
                    refreshEditorList();
                    logFeed("[JSON] Da nhap " + count + " cau dich.");
                    toast("Da nhap " + count + " ban dich!");
                    setBusyState(false);
                });
            } catch (Exception e) {
                ui.post(() -> {
                    logFeed("[LOI NHAP JSON] " + e.getMessage());
                    setBusyState(false);
                });
            }
        });
    }

    private void updateTranslateDashboard() {
        int total = scriptItems.size();
        int done = 0;
        for (ScriptItem item : scriptItems) {
            if (item.isTranslated()) done++;
        }
        int pct = total == 0 ? 0 : (int) ((done * 100L) / total);
        txtTransStats.setText(String.format(Locale.US, "%,d / %,d chuoi (%d%%)", done, total, pct));
        pbTranslate.setProgress(pct);
    }

    private void refreshEditorList() {
        String q = edtFilterQuery.getText().toString().trim().toLowerCase(Locale.US);
        int mode = spnFilterMode.getSelectedItemPosition();
        List<ScriptItem> out = new ArrayList<>();
        for (ScriptItem item : scriptItems) {
            if (mode == 1 && !item.isTranslated()) continue;
            if (mode == 2 && item.isTranslated()) continue;
            if (mode == 3 && !item.hasControlVars()) continue;
            if (!q.isEmpty()) {
                boolean match = item.sourceText.toLowerCase(Locale.US).contains(q)
                        || item.translatedText.toLowerCase(Locale.US).contains(q)
                        || ("#" + item.index).contains(q);
                if (!match) continue;
            }
            out.add(item);
        }
        scriptAdapter.submitList(out);
        txtEditorCount.setText(String.format(Locale.US, "Hien thi %,d / %,d chuoi - Cham the de sua", out.size(), scriptItems.size()));
    }

    private void openEditDialog(ScriptItem item) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);

        TextView tvSrc = new TextView(this);
        tvSrc.setText(String.format(Locale.US, "Goc (0x%X | %dB):\n%s", item.romOffset, item.originalByteLength, item.sourceText));
        tvSrc.setTextSize(13f);
        box.addView(tvSrc);

        EditText edt = new EditText(this);
        edt.setText(item.translatedText);
        edt.setHint("Nhap ban dich tieng Viet...");
        edt.setMinLines(3);
        box.addView(edt);

        CheckBox chkFmt = new CheckBox(this);
        chkFmt.setText("Tu dong bo dau & ngat dong chuan GBA");
        chkFmt.setChecked(swNoAccents.isChecked());
        box.addView(chkFmt);

        new AlertDialog.Builder(this)
                .setTitle("Chinh sua chuoi #" + item.index)
                .setView(box)
                .setPositiveButton("Luu", (d, w) -> {
                    String val = edt.getText().toString().trim();
                    if (!val.isEmpty() && chkFmt.isChecked()) {
                        int cols = parseIntOr(edtCols.getText().toString(), 34, 10);
                        val = VnTranslator.formatForGba(val, swNoAccents.isChecked(), item.isMultiline, cols, activeEncoding);
                    }
                    item.translatedText = val;
                    updateTranslateDashboard();
                    refreshEditorList();
                })
                .setNegativeButton("Huy", null)
                .show();
    }

    private void setBusyState(boolean state) {
        busy = state;
        btnFetchPack.setEnabled(!state);
        btnSelectRom.setEnabled(!state);
        btnSelectTbl.setEnabled(!state);
        btnScanRom.setEnabled(!state);
        btnBuildRom.setEnabled(!state);
        btnStartTrans.setEnabled(!state);
        btnSaveJson.setEnabled(!state);
        btnLoadJson.setEnabled(!state);
    }

    private int parseIntOr(String s, int def, int min) {
        try {
            int v = Integer.parseInt(s.trim());
            return Math.max(v, min);
        } catch (Exception e) {
            return def;
        }
    }

    private void logFeed(String msg) {
        if (feedBuf.length() > 14000) feedBuf.delete(0, 7000);
        feedBuf.append(msg).append("\n");
        txtLiveFeed.setText(feedBuf.toString());
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
