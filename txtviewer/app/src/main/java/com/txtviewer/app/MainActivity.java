package com.txtviewer.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineHeightSpan;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.provider.DocumentsContract;
import android.database.Cursor;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_OPEN = 1, REQ_TREE = 2;
    private static final int MAX_BYTES = 30 * 1024 * 1024;

    // 0·1번은 추천 2종(회사 낮 / 취침 전), 나머지는 기타 테마. 순백/순흑을 피하고 명암비 약 10~12:1 유지
    private static final String[] T_NAME = {"회사 (밝은 곳)", "취침 전 (불 끄고)", "종이 회백", "크림",
            "세피아", "소프트 다크", "네이비 다크", "저조도 다크"};
    private static final int[] T_BG = {0xFFDCDCD7, 0xFF14110F, 0xFFF2F2EE, 0xFFF5EEDC,
            0xFFEBDDBE, 0xFF1C1C1E, 0xFF172033, 0xFF0E0E0E};
    private static final int[] T_FG = {0xFF1E1E1E, 0xFFC4B8A4, 0xFF222222, 0xFF2E2A24,
            0xFF3A2F22, 0xFFD0D0D0, 0xFFC5D0E0, 0xFFB8B8B8};
    private static final String[] FONT_NAME = {"기본", "명조(세리프)", "고정폭"};

    private interface IntCb { void on(int v); }

    private static final class Fe { String id, name, mime; long size; boolean dir, sel; }
    private static final class Bm { int off; String note; }
    private static final class LItem { String text; int off; Bm ref; }
    private static final String BACKUP_NAME = "나의소설_백업.json";
    private static final Pattern CH = Pattern.compile("^\\s*(?:제\\s*\\d+\\s*[장화회편부권절막]|\\d+\\s*[장화회편](?![가-힣])|第\\s*\\d+\\s*[章話话回]|(?i:chapter|episode)\\s*\\d+|(?i:ch|ep)\\.?\\s*\\d+(?![0-9A-Za-z가-힣])|프롤로그|에필로그|외전|번외|서장|종장)");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences sp;

    // 설정값
    private int theme;          // 0..7, -1 = 직접 설정
    private int customBg, customFg;
    private float sizeSp;
    private int spacing10;      // 줄 간격 x10
    private int marginH, marginV; // dp
    private int font;
    private boolean showNum, volKeys, animOn;
    private boolean autoTheme, keepOn = true;
    private int dayTheme = 0, nightTheme = 1, nightStart = 20, nightEnd = 6, dimPct = 0, indentCh = 0, gap10 = 0;
    private int lastEff = -2;
    private View dimView;
    private FrameLayout sleepCover;
    private long sleepEndAt = 0;
    private Runnable sleepRun;
    private String bk = "";
    private final ArrayList<Bm> bms = new ArrayList<>();
    private ArrayList<Integer> tocOff = new ArrayList<>();
    private ArrayList<String> tocTitle = new ArrayList<>();
    private LinearLayout listPanel;
    private TextView listTitle;
    private ListView listView;
    private BaseAdapter listAdapter;
    private ArrayList<LItem> litems = new ArrayList<>();
    private ArrayList<Fe> fmAll = new ArrayList<>();
    private String fmFilter = "";
    private Button fmSearchB;

    // 본문/페이지
    private String text = "";
    private int[] starts = new int[1];
    private int pageCount = 0;
    private int curPage = 0;
    private int keepOffset = 0;
    private int gen = 0;
    private boolean paginating = false;
    private Uri curUri;
    private String curName = "", curCharset = "";

    // 뷰
    private FrameLayout root;
    private PageView pv;
    private LinearLayout topBar, bottomBar;
    private TextView titleTv, infoTv;
    private SeekBar seek;
    private boolean seeking;
    private LinearLayout settingsPanel, settingsBody;
    private ScrollView settingsScroll;
    private LinearLayout fmPanel, fmBottom;
    private TextView fmPath, fmInfo;
    private ListView fmList;
    private BaseAdapter fmAdapter;
    private Uri fmTree;
    private String fmDirId = "";
    private final ArrayList<String> fmStackIds = new ArrayList<>(), fmStackNames = new ArrayList<>();
    private ArrayList<Fe> fmItems = new ArrayList<>();
    private boolean fmMoving;
    private ArrayList<Fe> fmMoveSrc;
    private String fmMoveSrcDir = "";
    private LinearLayout searchBar, quickCard;
    private FrameLayout quickPanel;
    private EditText searchEt;
    private TextView searchInfo;
    private Button nextBtn;
    private String searchQ = "";
    private int lastHit = -1;
    private String lower;
    private int lastPW = 0, lastPH = 0;
    private int[] snapI;
    private float snapSize;
    private FrameLayout pickerPanel;
    private boolean menuVisible;
    private Runnable pendingTap;

    private final Runnable repRun = () -> {
        if (text.isEmpty() || pv.getWidth() == 0 || pv.getHeight() == 0) return;
        if (searchBar.getVisibility() == View.VISIBLE || pickerPanel.getVisibility() == View.VISIBLE) return;
        if (pv.getWidth() == lastPW && pv.getHeight() == lastPH) return;
        repaginate();
    };

    // ───────────────────────── 생명주기 ─────────────────────────
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("txt", MODE_PRIVATE);
        installCrashLog();
        loadPrefs();
        build();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        goImmersive();
        showLastCrash();
        if (!handleIntent(getIntent())) {
            String last = sp.getString("uri", null);
            if (last != null) open(Uri.parse(last)); else showHelp();
        }
        checkUpdate(false);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleIntent(i);
    }

    private boolean handleIntent(Intent i) {
        if (i != null && Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) {
            open(i.getData());
            return true;
        }
        return false;
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) goImmersive();
    }

    @Override
    protected void onPause() { savePos(); super.onPause(); }

    @Override
    public void onBackPressed() {
        if (pickerPanel.getVisibility() == View.VISIBLE) { pickerPanel.setVisibility(View.GONE); return; }
        if (pickerPanel.getVisibility() != View.VISIBLE && fmPanel.getVisibility() == View.VISIBLE) { fmBack(); return; }
        if (settingsPanel.getVisibility() == View.VISIBLE) { closeSettings(true); return; }
        if (listPanel.getVisibility() == View.VISIBLE) { listPanel.setVisibility(View.GONE); return; }
        if (quickPanel.getVisibility() == View.VISIBLE) { quickPanel.setVisibility(View.GONE); return; }
        if (searchBar.getVisibility() == View.VISIBLE) { closeSearch(); return; }
        if (menuVisible) { setMenu(false); return; }
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        if (volKeys && !text.isEmpty() && settingsPanel.getVisibility() != View.VISIBLE && searchBar.getVisibility() != View.VISIBLE) {
            if (k == KeyEvent.KEYCODE_VOLUME_DOWN) { turn(1); return true; }
            if (k == KeyEvent.KEYCODE_VOLUME_UP) { turn(-1); return true; }
        }
        return super.onKeyDown(k, e);
    }

    @Override
    public boolean onKeyUp(int k, KeyEvent e) {
        if (volKeys && !text.isEmpty() && (k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_UP)) return true;
        return super.onKeyUp(k, e);
    }

    /** 전체화면(상태바·내비게이션바 숨김). 화면 밝기는 건드리지 않아 휴대폰 밝기를 그대로 따른다 */
    private void goImmersive() {
        try { goImmersive0(); } catch (Throwable t) { /* 전체화면 실패해도 앱은 계속 */ }
    }

    private void goImmersive0() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    /** 오류로 종료되면 내용을 저장해 두었다가 다음 실행 때 보여준다 */
    private void installCrashLog() {
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                String st = sw.toString();
                sp.edit().putString("crash", st.length() > 3000 ? st.substring(0, 3000) : st).commit();
            } catch (Throwable ignored) {}
            if (old != null) old.uncaughtException(t, e);
        });
    }

    private void showLastCrash() {
        final String c = sp.getString("crash", null);
        if (c == null) return;
        sp.edit().remove("crash").apply();
        new android.app.AlertDialog.Builder(this)
                .setTitle("이전 실행 중 오류가 있었어요")
                .setMessage(c)
                .setPositiveButton("확인", null)
                .show();
    }

    // ───────────────────────── 설정 저장 ─────────────────────────
    private void loadPrefs() {
        theme = sp.getInt("theme", 0);
        if (sp.getInt("tver", 0) < 2) {   // 테마 목록이 바뀌어 번호가 달라졌으므로 한 번 초기화
            if (theme >= 0) theme = 0;
            sp.edit().putInt("tver", 2).putInt("theme", theme).apply();
        }
        customBg = sp.getInt("cbg", T_BG[0]);
        customFg = sp.getInt("cfg", T_FG[0]);
        sizeSp = sp.getFloat("size", 18f);
        spacing10 = sp.getInt("spacing", 16);
        marginH = sp.getInt("mh", 20);
        marginV = sp.getInt("mv", 28);
        font = sp.getInt("font", 0);
        showNum = sp.getBoolean("num", true);
        volKeys = sp.getBoolean("vol", true);
        animOn = sp.getBoolean("anim", true);
        autoTheme = sp.getBoolean("auto", false);
        dayTheme = sp.getInt("dayT", 0);
        nightTheme = sp.getInt("nightT", 1);
        nightStart = sp.getInt("ns", 20);
        nightEnd = sp.getInt("ne", 6);
        dimPct = sp.getInt("dim", 0);
        keepOn = sp.getBoolean("keep", true);
        indentCh = sp.getInt("indent", 0);
        gap10 = sp.getInt("gap", 0);
    }

    private void savePrefs() {
        sp.edit().putInt("theme", theme).putInt("cbg", customBg).putInt("cfg", customFg)
                .putFloat("size", sizeSp).putInt("spacing", spacing10).putInt("mh", marginH)
                .putInt("mv", marginV).putInt("font", font).putBoolean("num", showNum)
                .putBoolean("vol", volKeys).putBoolean("anim", animOn)
                .putBoolean("auto", autoTheme).putInt("dayT", dayTheme).putInt("nightT", nightTheme)
                .putInt("ns", nightStart).putInt("ne", nightEnd).putInt("dim", dimPct).putBoolean("keep", keepOn)
                .putInt("indent", indentCh).putInt("gap", gap10).apply();
    }

    private boolean isNight() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return nightStart > nightEnd ? (h >= nightStart || h < nightEnd) : (h >= nightStart && h < nightEnd);
    }

    /** 자동 테마를 켰으면 낮/밤에 따라 정해 둔 테마, 아니면 직접 고른 테마 */
    private int effTheme() { return !autoTheme ? theme : (isNight() ? nightTheme : dayTheme); }

    private void setTheme(int idx) {
        theme = idx;
        if (autoTheme) { if (isNight()) nightTheme = idx; else dayTheme = idx; }
    }

    private void checkAutoTheme() {
        int e = effTheme();
        if (e == lastEff) return;
        lastEff = e;
        if (pageCount > 0 && !paginating) showPage(false);
        if (pv != null) pv.invalidate();
    }

    @Override
    protected void onResume() { super.onResume(); checkAutoTheme(); }

    private int bgColor() { int t = effTheme(); return t >= 0 ? T_BG[t] : customBg; }
    private int fgColor() { int t = effTheme(); return t >= 0 ? T_FG[t] : customFg; }

    private void applyDim() {
        if (dimView != null) dimView.setBackgroundColor(Color.argb(Math.max(0, Math.min(70, dimPct)) * 255 / 100, 0, 0, 0));
    }

    private void applyKeepOn() {
        if (keepOn) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    // ───────────────────────── 화면 구성 ─────────────────────────
    private void build() {
        root = new FrameLayout(this);
        pv = new PageView();
        root.addView(pv, new FrameLayout.LayoutParams(-1, -1));

        nextBtn = new Button(this);
        nextBtn.setText("다음권 열기 ▶");
        nextBtn.setAllCaps(false);
        nextBtn.setVisibility(View.GONE);
        nextBtn.setOnClickListener(v -> openNextVolume());
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2);
        nl.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        nl.bottomMargin = dp(40);
        root.addView(nextBtn, nl);

        // 상단 바 (두 번 탭하면 표시)
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(0xEE202020);
        topBar.setPadding(dp(16), dp(30), dp(10), dp(10));
        topBar.setClickable(true);
        titleTv = new TextView(this);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(15);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        topBar.addView(titleTv, new LinearLayout.LayoutParams(0, -2, 1f));
        Button sb = new Button(this);
        sb.setText("설정");
        sb.setOnClickListener(v -> openSettings());
        topBar.addView(sb, new LinearLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-1, -2);
        tl.gravity = Gravity.TOP;
        root.addView(topBar, tl);

        // 하단 바 (페이지 이동)
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setBackgroundColor(0xEE202020);
        bottomBar.setPadding(dp(14), dp(10), dp(14), dp(22));
        bottomBar.setClickable(true);
        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && pageCount > 1 && !paginating) {
                    curPage = (int) (p * (long) (pageCount - 1) / 1000);
                    showPage(false);
                }
            }
            public void onStartTrackingTouch(SeekBar s) { seeking = true; }
            public void onStopTrackingTouch(SeekBar s) { seeking = false; savePos(); }
        });
        bottomBar.addView(seek, new LinearLayout.LayoutParams(-1, -2));
        infoTv = new TextView(this);
        infoTv.setTextColor(0xFFDDDDDD);
        infoTv.setTextSize(13);
        infoTv.setGravity(Gravity.CENTER);
        bottomBar.addView(infoTv, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, -2);
        bl.gravity = Gravity.BOTTOM;
        root.addView(bottomBar, bl);

        topBar.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);

        // 검색 바 (길게 누르기 → 텍스트 검색)
        searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.VERTICAL);
        searchBar.setBackgroundColor(0xF0202020);
        searchBar.setPadding(dp(10), dp(30), dp(10), dp(8));
        searchBar.setClickable(true);
        LinearLayout sr = new LinearLayout(this);
        sr.setOrientation(LinearLayout.HORIZONTAL);
        sr.setGravity(Gravity.CENTER_VERTICAL);
        searchEt = new EditText(this);
        searchEt.setHint("검색어");
        searchEt.setHintTextColor(0xFF888888);
        searchEt.setTextColor(Color.WHITE);
        searchEt.setSingleLine(true);
        searchEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchEt.setOnEditorActionListener((v, a, ev) -> { doSearch(true); return true; });
        sr.addView(searchEt, new LinearLayout.LayoutParams(0, -2, 1f));
        Button sp1 = new Button(this); sp1.setText("◀"); sp1.setMinWidth(0); sp1.setMinimumWidth(dp(48));
        sp1.setOnClickListener(v -> doSearch(false));
        Button sn1 = new Button(this); sn1.setText("▶"); sn1.setMinWidth(0); sn1.setMinimumWidth(dp(48));
        sn1.setOnClickListener(v -> doSearch(true));
        Button sx = new Button(this); sx.setText("닫기"); sx.setMinWidth(0); sx.setMinimumWidth(dp(56));
        sx.setOnClickListener(v -> closeSearch());
        sr.addView(sp1); sr.addView(sn1); sr.addView(sx);
        searchBar.addView(sr, new LinearLayout.LayoutParams(-1, -2));
        searchInfo = new TextView(this);
        searchInfo.setTextColor(0xFFBBBBBB);
        searchInfo.setTextSize(12);
        searchInfo.setPadding(dp(6), 0, 0, 0);
        searchBar.addView(searchInfo, new LinearLayout.LayoutParams(-1, -2));
        searchBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, -2);
        sl.gravity = Gravity.TOP;
        root.addView(searchBar, sl);

        // 길게 누르기 간이 메뉴
        quickPanel = new FrameLayout(this);
        quickPanel.setBackgroundColor(0x66000000);
        quickPanel.setClickable(true);
        quickPanel.setOnClickListener(v -> quickPanel.setVisibility(View.GONE));
        quickCard = new LinearLayout(this);
        quickCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable qg = new GradientDrawable();
        qg.setColor(0xF0262626);
        qg.setCornerRadius(dp(14));
        quickCard.setBackground(qg);
        quickCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        quickCard.setClickable(true);
        quickBtn("책갈피 추가 / 해제", () -> toggleBookmark());
        quickBtn("책갈피 목록", () -> openBookmarks());
        quickBtn("목차", () -> openToc());
        quickBtn("텍스트 검색", () -> openSearch());
        quickBtn("취침 타이머", () -> sleepDialog());
        quickBtn("파일 열기 · 관리", () -> openFm());
        quickBtn("설정", () -> openSettings());
        FrameLayout.LayoutParams ql = new FrameLayout.LayoutParams(dp(260), -2);
        ql.gravity = Gravity.CENTER;
        quickPanel.addView(quickCard, ql);
        quickPanel.setVisibility(View.GONE);
        root.addView(quickPanel, new FrameLayout.LayoutParams(-1, -1));

        // 목차 / 책갈피 목록 화면
        listPanel = new LinearLayout(this);
        listPanel.setOrientation(LinearLayout.VERTICAL);
        listPanel.setBackgroundColor(0xFF202020);
        listPanel.setClickable(true);
        listPanel.setVisibility(View.GONE);
        LinearLayout lh = new LinearLayout(this);
        lh.setOrientation(LinearLayout.HORIZONTAL);
        lh.setGravity(Gravity.CENTER_VERTICAL);
        lh.setBackgroundColor(0xFF2E2E2E);
        lh.setPadding(dp(18), dp(30), dp(10), dp(8));
        listTitle = new TextView(this);
        listTitle.setTextColor(Color.WHITE);
        listTitle.setTextSize(20);
        lh.addView(listTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        Button lcl = new Button(this); lcl.setText("닫기");
        lcl.setOnClickListener(v -> listPanel.setVisibility(View.GONE));
        lh.addView(lcl);
        listPanel.addView(lh, new LinearLayout.LayoutParams(-1, -2));
        listView = new ListView(this);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(0xFF333333));
        listView.setDividerHeight(1);
        listAdapter = new BaseAdapter() {
            public int getCount() { return litems.size(); }
            public Object getItem(int i) { return litems.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup g) {
                final LItem it = litems.get(i);
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(2), dp(8), dp(2));
                TextView tv = new TextView(MainActivity.this);
                tv.setText(it.text);
                tv.setTextColor(it.text.startsWith("▶") ? 0xFFFFE082 : Color.WHITE);
                tv.setTextSize(15);
                tv.setPadding(0, dp(14), dp(8), dp(14));
                row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
                if (it.ref != null) {
                    Button x = new Button(MainActivity.this);
                    x.setText("✕");
                    x.setMinWidth(0); x.setMinimumWidth(dp(48));
                    x.setOnClickListener(v -> {
                        bms.remove(it.ref);
                        saveBms();
                        if (pageCount > 0) showPage(false);
                        openBookmarks();
                    });
                    row.addView(x);
                }
                row.setOnClickListener(v -> jumpTo(it.off));
                return row;
            }
        };
        listView.setAdapter(listAdapter);
        listPanel.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(listPanel, new FrameLayout.LayoutParams(-1, -1));

        // 설정 화면: 최상단에 저장/닫기 버튼
        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setBackgroundColor(0xFF202020);
        settingsPanel.setClickable(true);
        settingsPanel.setVisibility(View.GONE);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(0xFF2E2E2E);
        head.setPadding(dp(18), dp(30), dp(10), dp(8));
        TextView ht = new TextView(this);
        ht.setText("설정");
        ht.setTextColor(Color.WHITE);
        ht.setTextSize(20);
        head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1f));
        Button hs = new Button(this); hs.setText("저장");
        hs.setOnClickListener(v -> closeSettings(true));
        Button hc = new Button(this); hc.setText("닫기");
        hc.setOnClickListener(v -> closeSettings(false));
        head.addView(hs); head.addView(hc);
        settingsPanel.addView(head, new LinearLayout.LayoutParams(-1, -2));
        settingsScroll = new ScrollView(this);
        settingsBody = new LinearLayout(this);
        settingsBody.setOrientation(LinearLayout.VERTICAL);
        settingsBody.setPadding(dp(18), dp(6), dp(18), dp(30));
        settingsScroll.addView(settingsBody, new ViewGroup.LayoutParams(-1, -2));
        settingsPanel.addView(settingsScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(settingsPanel, new FrameLayout.LayoutParams(-1, -1));

        // 파일 관리 화면 (이동·삭제·이름 변경)
        fmPanel = new LinearLayout(this);
        fmPanel.setOrientation(LinearLayout.VERTICAL);
        fmPanel.setBackgroundColor(0xFF202020);
        fmPanel.setClickable(true);
        fmPanel.setVisibility(View.GONE);
        LinearLayout fh = new LinearLayout(this);
        fh.setOrientation(LinearLayout.HORIZONTAL);
        fh.setGravity(Gravity.CENTER_VERTICAL);
        fh.setBackgroundColor(0xFF2E2E2E);
        fh.setPadding(dp(18), dp(30), dp(10), dp(8));
        LinearLayout ft = new LinearLayout(this);
        ft.setOrientation(LinearLayout.VERTICAL);
        TextView ftt = new TextView(this);
        ftt.setText("파일 열기 · 관리");
        ftt.setTextColor(Color.WHITE);
        ftt.setTextSize(20);
        ft.addView(ftt);
        fmPath = new TextView(this);
        fmPath.setTextColor(0xFFAAAAAA);
        fmPath.setTextSize(12);
        fmPath.setSingleLine(true);
        fmPath.setEllipsize(TextUtils.TruncateAt.START);
        ft.addView(fmPath);
        fh.addView(ft, new LinearLayout.LayoutParams(0, -2, 1f));
        Button fup = new Button(this); fup.setText("상위");
        fup.setOnClickListener(v -> fmUp());
        Button fnf = new Button(this); fnf.setText("새 폴더");
        fnf.setOnClickListener(v -> fmNewFolder());
        Button fcl = new Button(this); fcl.setText("닫기");
        fcl.setOnClickListener(v -> fmClose());
        fh.addView(fup); fh.addView(fnf); fh.addView(fcl);
        fmPanel.addView(fh, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout ftb = new LinearLayout(this);
        ftb.setOrientation(LinearLayout.HORIZONTAL);
        ftb.setBackgroundColor(0xFF262626);
        ftb.setPadding(dp(8), dp(2), dp(8), dp(2));
        Button fsr = new Button(this); fsr.setText("읽은 책 선택"); fsr.setAllCaps(false);
        fsr.setOnClickListener(v -> {
            int c = 0;
            for (Fe e : fmItems) { e.sel = !e.dir && sp.getBoolean("d2_" + Integer.toString(e.name.hashCode()), false); if (e.sel) c++; }
            fmAdapter.notifyDataSetChanged(); updateFmBottom();
            if (c == 0) Toast.makeText(this, "끝까지 읽은 책이 이 목록에 없어요", Toast.LENGTH_SHORT).show();
        });
        Button fsa = new Button(this); fsa.setText("전체 선택"); fsa.setAllCaps(false);
        fsa.setOnClickListener(v -> {
            boolean all = true;
            for (Fe e : fmItems) if (!e.dir && !e.sel) all = false;
            for (Fe e : fmItems) e.sel = !e.dir && !all;
            fmAdapter.notifyDataSetChanged(); updateFmBottom();
        });
        fmSearchB = new Button(this); fmSearchB.setText("파일명 검색"); fmSearchB.setAllCaps(false);
        fmSearchB.setOnClickListener(v -> fmSearchDialog());
        ftb.addView(fsr, new LinearLayout.LayoutParams(0, -2, 1f));
        ftb.addView(fsa, new LinearLayout.LayoutParams(0, -2, 1f));
        ftb.addView(fmSearchB, new LinearLayout.LayoutParams(0, -2, 1f));
        fmPanel.addView(ftb, new LinearLayout.LayoutParams(-1, -2));
        fmList = new ListView(this);
        fmList.setDivider(new android.graphics.drawable.ColorDrawable(0xFF333333));
        fmList.setDividerHeight(1);
        fmAdapter = new BaseAdapter() {
            public int getCount() { return fmItems.size(); }
            public Object getItem(int i) { return fmItems.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View cv, ViewGroup g) {
                final Fe e = fmItems.get(i);
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(4), dp(14), dp(4));
                CheckBox cb = new CheckBox(MainActivity.this);
                cb.setChecked(e.sel);
                cb.setOnClickListener(v -> { e.sel = cb.isChecked(); updateFmBottom(); });
                row.addView(cb);
                TextView nm = new TextView(MainActivity.this);
                nm.setText((e.dir ? "📁 " : "📄 ") + e.name);
                nm.setTextColor(e.dir ? 0xFFFFE082 : Color.WHITE);
                nm.setTextSize(15);
                nm.setPadding(dp(4), dp(10), dp(8), dp(10));
                row.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
                if (!e.dir) {
                    String k = Integer.toString(e.name.hashCode());
                    int pr = sp.getInt("p2_" + k, 0);
                    boolean dn = sp.getBoolean("d2_" + k, false);
                    TextView sz = new TextView(MainActivity.this);
                    sz.setText((dn ? "✓ 읽음\n" : (pr > 0 ? pr + "%\n" : "")) + fmtSize(e.size));
                    sz.setGravity(Gravity.END);
                    sz.setTextColor(dn ? 0xFF8BC98B : 0xFF999999);
                    sz.setTextSize(12);
                    row.addView(sz);
                }
                row.setOnClickListener(v -> fmClick(e));
                return row;
            }
        };
        fmList.setAdapter(fmAdapter);
        fmPanel.addView(fmList, new LinearLayout.LayoutParams(-1, 0, 1f));
        fmInfo = new TextView(this);
        fmInfo.setTextColor(0xFFBBBBBB);
        fmInfo.setTextSize(12);
        fmInfo.setPadding(dp(14), dp(6), dp(14), dp(2));
        fmPanel.addView(fmInfo, new LinearLayout.LayoutParams(-1, -2));
        fmBottom = new LinearLayout(this);
        fmBottom.setOrientation(LinearLayout.HORIZONTAL);
        fmBottom.setPadding(dp(8), 0, dp(8), dp(20));
        fmPanel.addView(fmBottom, new LinearLayout.LayoutParams(-1, -2));
        root.addView(fmPanel, new FrameLayout.LayoutParams(-1, -1));

        pickerPanel = new FrameLayout(this);
        pickerPanel.setBackgroundColor(0xFF202020);
        pickerPanel.setClickable(true);
        pickerPanel.setVisibility(View.GONE);
        root.addView(pickerPanel, new FrameLayout.LayoutParams(-1, -1));

        // 추가 어둡게: 터치는 아래 화면으로 통과
        dimView = new View(this);
        dimView.setClickable(false);
        dimView.setFocusable(false);
        root.addView(dimView, new FrameLayout.LayoutParams(-1, -1));

        // 취침 타이머 종료 화면
        sleepCover = new FrameLayout(this);
        sleepCover.setBackgroundColor(0xFF000000);
        sleepCover.setClickable(true);
        TextView sct = new TextView(this);
        sct.setText("취침 타이머가 끝났어요.\n화면을 터치하면 계속 읽을 수 있어요.");
        sct.setTextColor(0xFF3A3A3A);
        sct.setTextSize(14);
        sct.setGravity(Gravity.CENTER);
        sleepCover.addView(sct, new FrameLayout.LayoutParams(-1, -1));
        sleepCover.setOnClickListener(v -> sleepWake());
        sleepCover.setVisibility(View.GONE);
        root.addView(sleepCover, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
        applyDim();
        applyKeepOn();
    }

    private void quickBtn(String t, Runnable r) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(v -> { quickPanel.setVisibility(View.GONE); r.run(); });
        quickCard.addView(b, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showQuick() {
        if (settingsPanel.getVisibility() == View.VISIBLE) return;
        setMenu(false);
        quickPanel.setVisibility(View.VISIBLE);
    }

    private void setMenu(boolean on) {
        menuVisible = on;
        topBar.setVisibility(on ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) updateInfo();
    }

    // ───────────────────────── 페이지 넘김 ─────────────────────────
    private void turn(int d) {
        if (text.isEmpty() || paginating || pageCount == 0) return;
        checkAutoTheme();
        int n = curPage + d;
        if (n < 0) { Toast.makeText(this, "첫 페이지예요", Toast.LENGTH_SHORT).show(); return; }
        if (n >= pageCount) { Toast.makeText(this, "마지막 페이지예요", Toast.LENGTH_SHORT).show(); return; }
        if (animOn && pv.canAnimate()) {
            try {
                pv.finishAnim();
                Bitmap from = pv.snapshot();
                curPage = n;
                showPage(true);
                Bitmap to = pv.snapshot();
                pv.startFlip(from, to, d);
                return;
            } catch (Throwable t) { pv.finishAnim(); }
        }
        curPage = n;
        showPage(true);
    }

    /** 한 번 탭: 오른쪽=다음, 왼쪽=이전 / 빠르게 두 번 탭: 메뉴 */
    private void onTap(float x) {
        if (menuVisible) { setMenu(false); return; }
        if (pendingTap != null) {
            ui.removeCallbacks(pendingTap);
            pendingTap = null;
            setMenu(true);
            return;
        }
        final boolean right = x > pv.getWidth() / 2f;
        pendingTap = () -> { pendingTap = null; turn(right ? 1 : -1); };
        ui.postDelayed(pendingTap, 210);
    }

    private void showPage(boolean save) {
        if (pageCount == 0) return;
        curPage = Math.max(0, Math.min(pageCount - 1, curPage));
        int s = starts[curPage];
        int e = curPage + 1 < pageCount ? starts[curPage + 1] : text.length();
        String sub = text.substring(s, Math.max(s, e));
        TextPaint pp = pv.pagePaint();
        SpannableString ss = decorate(sub, s == 0 || text.charAt(s - 1) == '\n', Math.round(indentCh * pp.getTextSize()), Math.round(gap10 / 10f * pp.getTextSize()));
        if (!searchQ.isEmpty()) highlight(ss, sub);
        pv.layout = buildLayout(ss, pp, pv.textW(), spacing10 / 10f);
        pv.marked = isMarked(s, e);
        keepOffset = s;
        nextBtn.setVisibility(!paginating && curPage == pageCount - 1 ? View.VISIBLE : View.GONE);
        pv.invalidate();
        if (save) savePos();
        updateInfo();
    }

    private void updateInfo() {
        if (pageCount == 0 || paginating) { infoTv.setText(""); return; }
        infoTv.setText((curPage + 1) + " / " + pageCount + " 쪽  ·  " + (int) ((curPage + 1) * 100L / pageCount) + "%"
                + (sleepEndAt > 0 ? "  ·  취침 " + sleepRemain() + "분 남음" : ""));
        if (!seeking && pageCount > 1) seek.setProgress((int) (curPage * 1000L / (pageCount - 1)));
    }

    private void savePos() {
        if (curUri != null && pageCount > 0 && !paginating && !bk.isEmpty()) {
            int pg = Math.min(curPage, pageCount - 1);
            SharedPreferences.Editor ed = sp.edit()
                    .putInt("o2_" + bk, starts[pg])
                    .putInt("p2_" + bk, (int) ((pg + 1) * 100L / pageCount))
                    .putString("n2_" + bk, curName);
            if (pg == pageCount - 1) ed.putBoolean("d2_" + bk, true);   // 끝까지 읽음
            ed.apply();
        }
    }

    // ───────────────────────── 문단 서식 ─────────────────────────
    /** 문단 사이에 줄 높이를 더해 간격을 만든다 (문단 마지막 줄에만 적용) */
    private static final class GapSpan implements LineHeightSpan {
        private final int gap;
        GapSpan(int g) { gap = g; }
        @Override
        public void chooseHeight(CharSequence t, int start, int end, int spanstartv, int lineHeight, Paint.FontMetricsInt fm) {
            if (t instanceof Spanned && ((Spanned) t).getSpanEnd(this) == end) { fm.descent += gap; fm.bottom += gap; }
        }
    }

    private static SpannableString decorate(String sub, boolean startsParagraph, int indentPx, int gapPx) {
        SpannableString ss = new SpannableString(sub);
        if (indentPx <= 0 && gapPx <= 0) return ss;
        int n = sub.length(), pos = 0;
        while (pos < n) {
            int nl = sub.indexOf('\n', pos);
            int end = nl < 0 ? n : nl + 1;
            if (end - pos > 1) {   // 개행만 있는 빈 줄은 제외
                if (indentPx > 0 && (pos > 0 || startsParagraph))
                    ss.setSpan(new LeadingMarginSpan.Standard(indentPx, 0), pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (gapPx > 0 && nl >= 0)
                    ss.setSpan(new GapSpan(gapPx), pos, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            pos = end;
        }
        return ss;
    }

    // ───────────────────────── 목차 · 책갈피 ─────────────────────────
    private static void findChapters(String t, ArrayList<Integer> offs, ArrayList<String> titles) {
        int pos = 0, len = t.length();
        Matcher m = CH.matcher("");
        while (pos < len && offs.size() < 5000) {
            int nl = t.indexOf('\n', pos);
            if (nl < 0) nl = len;
            int l = nl - pos;
            if (l > 0 && l <= 60) {
                String line = t.substring(pos, nl);
                m.reset(line);
                if (m.lookingAt()) { offs.add(pos); titles.add(line.trim()); }
            }
            pos = nl + 1;
        }
    }

    private void loadBms() {
        bms.clear();
        String j = sp.getString("b2_" + bk, null);
        if (j == null) return;
        try {
            JSONArray a = new JSONArray(j);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Bm b = new Bm();
                b.off = o.getInt("o");
                b.note = o.optString("t", "");
                bms.add(b);
            }
        } catch (Exception ignored) {}
        Collections.sort(bms, (x, y) -> Integer.compare(x.off, y.off));
    }

    private void saveBms() {
        JSONArray a = new JSONArray();
        for (Bm b : bms) {
            try { JSONObject o = new JSONObject(); o.put("o", b.off); o.put("t", b.note); a.put(o); } catch (Exception ignored) {}
        }
        sp.edit().putString("b2_" + bk, a.toString()).putString("n2_" + bk, curName).apply();
    }

    private boolean isMarked(int s, int e) {
        for (Bm b : bms) if (b.off >= s && b.off < e) return true;
        return false;
    }

    private void toggleBookmark() {
        if (pageCount == 0 || paginating || bk.isEmpty()) { Toast.makeText(this, "먼저 책을 열어 주세요", Toast.LENGTH_SHORT).show(); return; }
        final int s = starts[curPage];
        final int e = curPage + 1 < pageCount ? starts[curPage + 1] : text.length();
        boolean removed = bms.removeIf(b -> b.off >= s && b.off < e);
        if (removed) {
            Toast.makeText(this, "책갈피를 해제했어요", Toast.LENGTH_SHORT).show();
        } else {
            Bm b = new Bm();
            b.off = s;
            String snip = text.substring(s, Math.min(e, s + 60)).replace('\n', ' ').trim();
            b.note = snip.length() > 26 ? snip.substring(0, 26) + "…" : snip;
            bms.add(b);
            Collections.sort(bms, (x, y) -> Integer.compare(x.off, y.off));
            Toast.makeText(this, "책갈피를 추가했어요", Toast.LENGTH_SHORT).show();
        }
        saveBms();
        pv.marked = isMarked(s, e);
        pv.invalidate();
    }

    private void showList(String title, ArrayList<LItem> items, int select) {
        litems = items;
        listTitle.setText(title);
        listAdapter.notifyDataSetChanged();
        listPanel.setVisibility(View.VISIBLE);
        listView.setSelection(Math.max(0, select - 2));
    }

    private void openToc() {
        if (pageCount == 0 || paginating) { Toast.makeText(this, "먼저 책을 열어 주세요", Toast.LENGTH_SHORT).show(); return; }
        if (tocOff.isEmpty()) { Toast.makeText(this, "목차로 쓸 제목(제1장, 1화, Chapter 등)을 찾지 못했어요", Toast.LENGTH_LONG).show(); return; }
        int cur = 0;
        for (int i = 0; i < tocOff.size(); i++) if (tocOff.get(i) <= keepOffset) cur = i;
        ArrayList<LItem> items = new ArrayList<>();
        for (int i = 0; i < tocOff.size(); i++) {
            LItem it = new LItem();
            it.text = (i == cur ? "▶ " : "") + tocTitle.get(i);
            it.off = tocOff.get(i);
            items.add(it);
        }
        showList("목차 (" + items.size() + ")", items, cur);
    }

    private void openBookmarks() {
        if (pageCount == 0 || paginating) { Toast.makeText(this, "먼저 책을 열어 주세요", Toast.LENGTH_SHORT).show(); return; }
        if (bms.isEmpty()) { listPanel.setVisibility(View.GONE); Toast.makeText(this, "책갈피가 없어요. 길게 눌러 '책갈피 추가/해제'로 추가하세요", Toast.LENGTH_LONG).show(); return; }
        ArrayList<LItem> items = new ArrayList<>();
        for (Bm b : bms) {
            LItem it = new LItem();
            it.text = (pageOf(b.off) + 1) + "쪽   " + b.note;
            it.off = b.off;
            it.ref = b;
            items.add(it);
        }
        showList("책갈피 (" + items.size() + ")", items, 0);
    }

    private void jumpTo(int off) {
        listPanel.setVisibility(View.GONE);
        if (pageCount == 0) return;
        curPage = pageOf(off);
        showPage(true);
    }

    // ───────────────────────── 페이지 계산 ─────────────────────────
    private static StaticLayout buildLayout(CharSequence s, TextPaint tp, int w, float mult) {
        return StaticLayout.Builder.obtain(s, 0, s.length(), tp, Math.max(50, w))
                .setLineSpacing(0f, mult).setIncludePad(false)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build();
    }

    private static int pageEnd(String t, int start, int w, int h, TextPaint tp, float mult, int indentPx, int gapPx) {
        int len = t.length();
        float px = tp.getTextSize();
        int cpl = (int) (w / (px * 0.45f)) + 1;
        int lpp = (int) (h / (px * 1.1f * mult)) + 2;
        int chunk = Math.max(600, (int) (cpl * lpp * 1.6f) + 200);
        while (true) {
            int e = Math.min(len, start + chunk);
            StaticLayout sl = buildLayout(decorate(t.substring(start, e), start == 0 || t.charAt(start - 1) == '\n', indentPx, gapPx), tp, w, mult);
            int lc = sl.getLineCount();
            int last = -1;
            for (int i = 0; i < lc; i++) { if (sl.getLineBottom(i) <= h) last = i; else break; }
            if (last < lc - 1) {
                if (last < 0) last = 0;
                int end = start + sl.getLineEnd(last);
                return end > start ? end : start + 1;
            }
            if (e >= len) return len;
            chunk *= 2;
        }
    }

    private void requestRepaginate() {
        ui.removeCallbacks(repRun);
        ui.postDelayed(repRun, 150);
    }

    private void repaginate() {
        final int g = ++gen;
        final String t = text;
        final int w = pv.textW(), h = pv.textH();
        final float mult = spacing10 / 10f;
        final TextPaint tp = pv.newPaint();
        final int ind = Math.round(indentCh * tp.getTextSize()), gp = Math.round(gap10 / 10f * tp.getTextSize());
        final int keep = keepOffset;
        paginating = true;
        lastPW = pv.getWidth();
        lastPH = pv.getHeight();
        nextBtn.setVisibility(View.GONE);
        pv.msg = "페이지 계산 중…";
        pv.invalidate();
        updateInfo();
        new Thread(() -> {
            int[] arr = new int[1024];
            int n = 0, pos = 0, len = t.length();
            long lastUi = 0;
            while (pos < len && g == gen) {
                if (n > 0) while (pos < len && t.charAt(pos) == '\n') pos++;
                if (pos >= len) break;
                if (n == arr.length) arr = Arrays.copyOf(arr, n * 2);
                arr[n++] = pos;
                pos = pageEnd(t, pos, w, h, tp, mult, ind, gp);
                long now = System.currentTimeMillis();
                if (now - lastUi > 400) {
                    lastUi = now;
                    final int pct = (int) (pos * 100L / Math.max(1, len));
                    ui.post(() -> { if (g == gen) { pv.msg = "페이지 계산 중… " + pct + "%"; pv.invalidate(); } });
                }
            }
            if (g != gen) return;
            if (n == 0) { arr[0] = 0; n = 1; }
            final int[] fa = arr;
            final int fn = n;
            ui.post(() -> {
                if (g != gen) return;
                starts = fa;
                pageCount = fn;
                paginating = false;
                int lo = 0, hi = fn - 1;
                while (lo < hi) {
                    int mid = (lo + hi + 1) >>> 1;
                    if (fa[mid] <= keep) lo = mid; else hi = mid - 1;
                }
                curPage = lo;
                showPage(true);
            });
        }).start();
    }

    // ───────────────────────── 페이지 뷰 ─────────────────────────
    private final class PageView extends View {
        StaticLayout layout;
        String msg = "";
        private float dx0, dy0;
        private boolean longFired;
        private final Runnable lpRun = () -> {
            longFired = true;
            if (pendingTap != null) { ui.removeCallbacks(pendingTap); pendingTap = null; }
            showQuick();
        };
        private final TextPaint foot = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);

        PageView() { super(MainActivity.this); }

        int topPad() { return dp(marginV); }
        int botPad() { return dp(marginV) + (showNum ? dp(18) : 0); }
        int textW() { return getWidth() - 2 * dp(marginH); }
        int textH() { return getHeight() - topPad() - botPad(); }

        TextPaint newPaint() {
            TextPaint p = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            p.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, getResources().getDisplayMetrics()));
            p.setColor(fgColor());
            p.setTypeface(font == 1 ? Typeface.SERIF : font == 2 ? Typeface.MONOSPACE : Typeface.DEFAULT);
            return p;
        }

        TextPaint pagePaint() { return newPaint(); }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (w > 0 && h > 0 && !text.isEmpty()) requestRepaginate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (animating && bFrom != null && bTo != null) { drawFlip(c); return; }
            c.drawColor(bgColor());
            if (text.isEmpty() || paginating || layout == null) {
                TextPaint p = newPaint();
                if (getWidth() > 0 && msg != null && !msg.isEmpty()) {
                    StaticLayout m = buildLayout(msg, p, getWidth() - dp(48), 1.5f);
                    c.save();
                    c.translate(dp(24), Math.max(dp(40), (getHeight() - m.getHeight()) / 2f));
                    m.draw(c);
                    c.restore();
                }
                return;
            }
            drawContent(c, layout, curPage);
        }

        void drawContent(Canvas c, StaticLayout lay, int page) {
            c.drawColor(bgColor());
            c.save();
            c.translate(dp(marginH), topPad());
            c.clipRect(0, 0, textW(), textH());
            lay.draw(c);
            c.restore();
            if (showNum && pageCount > 0) {
                foot.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));
                foot.setColor((fgColor() & 0x00FFFFFF) | 0x88000000);
                foot.setTextAlign(android.graphics.Paint.Align.CENTER);
                c.drawText((page + 1) + " / " + pageCount, getWidth() / 2f, getHeight() - dp(marginV) + dp(6), foot);
            }
            if (marked) {   // 책갈피가 있는 페이지는 오른쪽 위에 빨간 리본
                int w = getWidth(), r = dp(26);
                rib.reset();
                rib.moveTo(w - dp(38), 0); rib.lineTo(w - dp(12), 0); rib.lineTo(w - dp(12), r + dp(10));
                rib.lineTo(w - dp(25), r); rib.lineTo(w - dp(38), r + dp(10)); rib.close();
                ribP.setColor(0xFFC4574F);
                c.drawPath(rib, ribP);
            }
        }

        // ── 책 넘김 효과: 책등(왼쪽)을 축으로 페이지가 접히며 넘어간다 ──
        boolean animating;
        boolean marked;
        private final android.graphics.Path rib = new android.graphics.Path();
        private final Paint ribP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float animT;
        private int animDir;
        private Bitmap bFrom, bTo;
        private ValueAnimator va;
        private final Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint shp = new Paint();
        private final Paint dk = new Paint();

        boolean canAnimate() { return getWidth() > 0 && getHeight() > 0 && layout != null && !text.isEmpty() && !paginating; }

        Bitmap snapshot() {
            Bitmap b = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            drawContent(new Canvas(b), layout, curPage);
            return b;
        }

        void startFlip(Bitmap from, Bitmap to, int dir) {
            bFrom = from; bTo = to; animDir = dir; animT = 0f; animating = true;
            va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(200);
            va.setInterpolator(new AccelerateDecelerateInterpolator());
            va.addUpdateListener(a -> { animT = (float) a.getAnimatedValue(); invalidate(); });
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator an) { endAnim(); }
                @Override public void onAnimationCancel(Animator an) { endAnim(); }
            });
            va.start();
        }

        void finishAnim() {
            if (va != null && va.isRunning()) va.end();
            else endAnim();
        }

        private void endAnim() {
            animating = false;
            va = null;
            if (bFrom != null) { bFrom.recycle(); bFrom = null; }
            if (bTo != null) { bTo.recycle(); bTo = null; }
            invalidate();
        }

        private void shadow(Canvas c, float edge, int alpha) {
            if (alpha <= 0) return;
            float w = dp(34);
            shp.setShader(new LinearGradient(edge, 0, edge + w, 0, Color.argb(alpha, 0, 0, 0), 0, Shader.TileMode.CLAMP));
            c.drawRect(edge, 0, Math.min(getWidth(), edge + w), getHeight(), shp);
        }

        private void drawFlip(Canvas c) {
            float t = animT;
            int W = getWidth(), H = getHeight();
            if (animDir > 0) {
                // 다음: 새 페이지가 아래에 있고, 현재 페이지가 왼쪽으로 접혀 들어감
                c.drawBitmap(bTo, 0, 0, bp);
                shadow(c, W * (1 - t), (int) (110 * (1 - t)));
                c.save();
                c.scale(1 - t, 1f, 0f, 0f);
                c.drawBitmap(bFrom, 0, 0, bp);
                dk.setColor(Color.argb((int) (70 * t), 0, 0, 0));
                c.drawRect(0, 0, W, H, dk);
                c.restore();
            } else {
                // 이전: 현재 페이지가 아래에 있고, 이전 페이지가 책등에서 펼쳐짐
                c.drawBitmap(bFrom, 0, 0, bp);
                shadow(c, W * t, (int) (110 * t));
                c.save();
                c.scale(t, 1f, 0f, 0f);
                c.drawBitmap(bTo, 0, 0, bp);
                dk.setColor(Color.argb((int) (70 * (1 - t)), 0, 0, 0));
                c.drawRect(0, 0, W, H, dk);
                c.restore();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dx0 = e.getX(); dy0 = e.getY();
                    longFired = false;
                    ui.postDelayed(lpRun, 550);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.hypot(e.getX() - dx0, e.getY() - dy0) > dp(16)) ui.removeCallbacks(lpRun);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    ui.removeCallbacks(lpRun);
                    return true;
                case MotionEvent.ACTION_UP: {
                    ui.removeCallbacks(lpRun);
                    if (longFired) return true;
                    float dx = e.getX() - dx0, dy = e.getY() - dy0;
                    if (Math.abs(dx) > dp(70) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        if (pendingTap != null) { ui.removeCallbacks(pendingTap); pendingTap = null; }
                        if (!menuVisible) turn(dx < 0 ? 1 : -1);
                    } else if (Math.hypot(dx, dy) < dp(16)) {
                        onTap(e.getX());
                    }
                    return true;
                }
                default:
                    return true;
            }
        }
    }

    // ───────────────────────── 파일 열기 ─────────────────────────
    private void showHelp() {
        pv.msg = "화면을 길게 누르면 간이 메뉴가 나와요.\n\n'파일 열기 · 관리'에서 폴더를 한 번 고르고, TXT를 눌러 읽으세요.\n\n· 빠르게 두 번 터치: 메뉴\n· 오른쪽 터치: 다음 페이지\n· 왼쪽 터치: 이전 페이지";
        pv.invalidate();
    }

    private void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/*", "application/octet-stream"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_OPEN);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (req == REQ_TREE) {
            if (res == RESULT_OK && d != null && d.getData() != null) {
                try { getContentResolver().takePersistableUriPermission(d.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); }
                catch (Exception e1) {
                    try { getContentResolver().takePersistableUriPermission(d.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
                }
                sp.edit().putString("tree", d.getData().toString()).apply();
                if (treeFor == 1) openFm(); else if (treeFor == 2) doBackup(); else if (treeFor == 3) confirmRestore(); else openNextVolume();
            }
            return;
        }
        if (req == REQ_OPEN && res == RESULT_OK && d != null && d.getData() != null) {
            Uri u = d.getData();
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            settingsPanel.setVisibility(View.GONE);
            closeSearch();
            setMenu(false);
            open(u);
        }
    }

    private void open(Uri u) {
        savePos();
        gen++;
        paginating = true;
        lastPW = 0; lastPH = 0;
        lower = null; lastHit = -1;
        nextBtn.setVisibility(View.GONE);
        text = "";
        pageCount = 0;
        pv.layout = null;
        pv.msg = "불러오는 중…";
        pv.invalidate();
        new Thread(() -> {
            String err = null, str = null, cs = "";
            String name = u.getLastPathSegment() == null ? "" : u.getLastPathSegment();
            try {
                try (android.database.Cursor c = getContentResolver().query(u, null, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (ix >= 0) name = c.getString(ix);
                    }
                } catch (Exception ignored) {}
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(u)) {
                    if (in == null) throw new Exception("파일을 열 수 없음");
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) > 0) {
                        bo.write(buf, 0, n);
                        if (bo.size() > MAX_BYTES) throw new Exception("파일이 너무 큼 (30MB 초과)");
                    }
                }
                Object[] r = decode(bo.toByteArray());
                cs = (String) r[1];
                str = ((String) r[0]).replace("\r\n", "\n").replace('\r', '\n');
            } catch (Exception e) { err = e.getMessage(); }
            final ArrayList<Integer> to = new ArrayList<>();
            final ArrayList<String> tt = new ArrayList<>();
            if (str != null) { try { findChapters(str, to, tt); } catch (Exception ignored) {} }
            final String fe = err, fs = str, fn = name, fc = cs;
            ui.post(() -> {
                if (fe != null || fs == null) {
                    paginating = false;
                    Toast.makeText(this, "열지 못했어요: " + fe, Toast.LENGTH_LONG).show();
                    if (text.isEmpty()) showHelp();
                    return;
                }
                text = fs; curUri = u; curName = fn; curCharset = fc;
                bk = Integer.toString(fn.hashCode());
                keepOffset = sp.getInt("o2_" + bk, sp.getInt("off_" + u.toString().hashCode(), 0));
                tocOff = to; tocTitle = tt;
                loadBms();
                sp.edit().putString("uri", u.toString()).apply();
                titleTv.setText(fn + "  (" + fc + ")");
                if (text.isEmpty()) { paginating = false; pv.msg = "빈 파일이에요"; pv.invalidate(); return; }
                requestRepaginate();
            });
        }).start();
    }

    /** BOM → 엄격한 UTF-8 → 한글(CP949) 순으로 인식 */
    private static Object[] decode(byte[] d) throws Exception {
        if (d.length >= 3 && (d[0] & 0xff) == 0xEF && (d[1] & 0xff) == 0xBB && (d[2] & 0xff) == 0xBF)
            return new Object[]{new String(d, 3, d.length - 3, "UTF-8"), "UTF-8"};
        if (d.length >= 2 && (d[0] & 0xff) == 0xFF && (d[1] & 0xff) == 0xFE)
            return new Object[]{new String(d, 2, d.length - 2, "UTF-16LE"), "UTF-16LE"};
        if (d.length >= 2 && (d[0] & 0xff) == 0xFE && (d[1] & 0xff) == 0xFF)
            return new Object[]{new String(d, 2, d.length - 2, "UTF-16BE"), "UTF-16BE"};
        try {
            String s = Charset.forName("UTF-8").newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(d)).toString();
            return new Object[]{s, "UTF-8"};
        } catch (CharacterCodingException e) {
            Charset k;
            try { k = Charset.forName("x-windows-949"); } catch (Exception x) { k = Charset.forName("EUC-KR"); }
            return new Object[]{new String(d, k), "CP949"};
        }
    }

    // ───────────────────────── 설정 화면 ─────────────────────────
    private boolean layoutDirty;

    private void openSettings() {
        setMenu(false);
        layoutDirty = false;
        snapI = new int[]{theme, customBg, customFg, spacing10, marginH, marginV, font, showNum ? 1 : 0, volKeys ? 1 : 0, animOn ? 1 : 0,
                autoTheme ? 1 : 0, dayTheme, nightTheme, nightStart, nightEnd, dimPct, keepOn ? 1 : 0, indentCh, gap10};
        snapSize = sizeSp;
        fillSettings();
        settingsScroll.scrollTo(0, 0);
        settingsPanel.setVisibility(View.VISIBLE);
    }

    /** save=true: 저장하고 닫기 / false: 이번에 바꾼 내용을 취소하고 닫기 */
    private void closeSettings(boolean save) {
        if (!save && snapI != null) {
            theme = snapI[0]; customBg = snapI[1]; customFg = snapI[2]; spacing10 = snapI[3];
            marginH = snapI[4]; marginV = snapI[5]; font = snapI[6]; showNum = snapI[7] == 1; volKeys = snapI[8] == 1; animOn = snapI[9] == 1;
            autoTheme = snapI[10] == 1; dayTheme = snapI[11]; nightTheme = snapI[12]; nightStart = snapI[13]; nightEnd = snapI[14];
            dimPct = snapI[15]; keepOn = snapI[16] == 1; indentCh = snapI[17]; gap10 = snapI[18];
            sizeSp = snapSize;
            layoutDirty = false;
        }
        settingsPanel.setVisibility(View.GONE);
        savePrefs();
        applyDim();
        applyKeepOn();
        lastEff = -2;
        checkAutoTheme();
        if (layoutDirty && !text.isEmpty()) { keepOffset = pageCount > 0 ? starts[Math.min(curPage, pageCount - 1)] : keepOffset; repaginate(); }
        else if (pageCount > 0) showPage(false);
        pv.invalidate();
    }

    private TextView label(String s, int sizeSp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setPadding(0, dp(14), 0, dp(6));
        return t;
    }

    private void section(String s) {
        TextView t = label(s, 13, 0xFF8FB8FF);
        t.setPadding(0, dp(22), 0, dp(4));
        settingsBody.addView(t);
    }

    private void seekRow(String name, int min, int max, int cur, String unit, int div, IntCb cb) {
        final TextView t = label("", 15, Color.WHITE);
        t.setPadding(0, dp(10), 0, 0);
        settingsBody.addView(t);
        final Runnable setTxt = () -> {};
        SeekBar s = new SeekBar(this);
        s.setMax(max - min);
        s.setProgress(cur - min);
        t.setText(name + "  " + fmt(cur, div) + unit);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean user) {
                int v = p + min;
                t.setText(name + "  " + fmt(v, div) + unit);
                if (user) { cb.on(v); layoutDirty = true; }
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) { savePrefs(); }
        });
        settingsBody.addView(s, new LinearLayout.LayoutParams(-1, -2));
    }

    private static String fmt(int v, int div) { return div == 1 ? String.valueOf(v) : String.valueOf(v / (float) div); }

    private Button themeButton(final int idx, String label, int hDp, boolean addToBody) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(T_FG[idx]);
        b.setTextSize(addToBody ? 15 : 13);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(T_BG[idx]);
        gd.setCornerRadius(dp(8));
        boolean selT = effTheme() == idx;
        gd.setStroke(selT ? dp(3) : dp(1), selT ? 0xFF4C9AFF : 0xFF666666);
        b.setBackground(gd);
        b.setOnClickListener(v -> { setTheme(idx); savePrefs(); fillSettings(); });
        LinearLayout.LayoutParams lp = addToBody ? new LinearLayout.LayoutParams(-1, dp(hDp)) : new LinearLayout.LayoutParams(0, dp(hDp), 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        if (addToBody) settingsBody.addView(b, lp); else b.setLayoutParams(lp);
        return b;
    }

    /** 색 견본(현재 색) + 항상 읽히는 일반 버튼 */
    private void colorEntry(LinearLayout row, String name, int color, boolean isBg) {
        View sw = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(6));
        g.setStroke(dp(2), 0xFFBBBBBB);
        sw.setBackground(g);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(40), dp(40));
        sl.leftMargin = dp(4);
        row.addView(sw, sl);
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(name + "\n" + String.format("#%06X", color & 0xFFFFFF));
        b.setTextSize(13);
        b.setOnClickListener(v -> pickColor(isBg));
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
    }

    private void fillSettings() {
        int scroll = settingsScroll.getScrollY();
        settingsBody.removeAllViews();

        section("파일");
        Button fmb = new Button(this);
        fmb.setText("파일 열기 · 관리 (이동 · 삭제 · 이름 변경)");
        fmb.setOnClickListener(v -> openFm());
        settingsBody.addView(fmb, new LinearLayout.LayoutParams(-1, -2));
        if (!curName.isEmpty()) settingsBody.addView(label("현재: " + curName + " (" + curCharset + ")", 12, 0xFFAAAAAA));

        section("백업 / 복원");
        LinearLayout brow = new LinearLayout(this);
        brow.setOrientation(LinearLayout.HORIZONTAL);
        Button bb = new Button(this); bb.setText("백업하기"); bb.setAllCaps(false);
        bb.setOnClickListener(v -> doBackup());
        Button br = new Button(this); br.setText("복원하기"); br.setAllCaps(false);
        br.setOnClickListener(v -> confirmRestore());
        brow.addView(bb, new LinearLayout.LayoutParams(0, -2, 1f));
        brow.addView(br, new LinearLayout.LayoutParams(0, -2, 1f));
        settingsBody.addView(brow, new LinearLayout.LayoutParams(-1, -2));
        settingsBody.addView(label("설정·읽던 위치·책갈피를 책 폴더 안의 '" + BACKUP_NAME + "' 파일로 저장해요. 폴더가 구글 드라이브 등에 동기화되면 폰을 바꿔도 복원할 수 있어요.", 12, 0xFFAAAAAA));

        section("추천 테마");
        themeButton(0, "회사 · 밝은 곳\n가나다 ABC", 84, true);
        themeButton(1, "취침 전 · 불 끄고 누워서\n가나다 ABC", 84, true);

        section("다른 테마");
        for (int i = 2; i < T_NAME.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(i + 2, T_NAME.length); j++) {
                row.addView(themeButton(j, T_NAME[j] + "\n가나다 ABC", 72, false));
            }
            settingsBody.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        section("직접 설정" + (effTheme() < 0 ? "  (사용 중)" : ""));
        LinearLayout cr = new LinearLayout(this);
        cr.setOrientation(LinearLayout.HORIZONTAL);
        cr.setGravity(Gravity.CENTER_VERTICAL);
        colorEntry(cr, "배경색", bgColor(), true);
        colorEntry(cr, "글자색", fgColor(), false);
        settingsBody.addView(cr, new LinearLayout.LayoutParams(-1, -2));

        section("자동 테마 전환 (낮 / 밤)");
        CheckBox cat = new CheckBox(this);
        cat.setText("밤에는 자동으로 어두운 테마로 바꾸기");
        cat.setTextColor(Color.WHITE);
        cat.setChecked(autoTheme);
        cat.setOnCheckedChangeListener((b, c) -> {
            autoTheme = c;
            if (c) { if (isNight()) nightTheme = theme; else dayTheme = theme; }
            savePrefs();
            fillSettings();
        });
        settingsBody.addView(cat);
        if (autoTheme) {
            seekRow("밤 시작 시각", 0, 23, nightStart, "시", 1, v -> nightStart = v);
            seekRow("밤 종료 시각", 0, 23, nightEnd, "시", 1, v -> nightEnd = v);
            settingsBody.addView(label("지금은 " + (isNight() ? "밤" : "낮") + " 시간대예요. 위에서 테마를 고르면 지금 시간대의 테마로 저장돼요.", 12, 0xFFAAAAAA));
        }

        section("글자");
        seekRow("글자 크기", 10, 40, Math.round(sizeSp), "sp", 1, v -> sizeSp = v);
        seekRow("줄 간격", 10, 25, spacing10, "배", 10, v -> spacing10 = v);
        seekRow("좌우 여백", 0, 60, marginH, "dp", 1, v -> marginH = v);
        seekRow("상하 여백", 0, 80, marginV, "dp", 1, v -> marginV = v);
        seekRow("문단 첫 줄 들여쓰기", 0, 4, indentCh, "글자", 1, v -> indentCh = v);
        seekRow("문단 사이 간격", 0, 10, gap10, "줄", 10, v -> gap10 = v);
        settingsBody.addView(label("글꼴", 15, Color.WHITE));
        LinearLayout fr = new LinearLayout(this);
        fr.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < FONT_NAME.length; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText((font == i ? "● " : "") + FONT_NAME[i]);
            b.setTextSize(13);
            b.setOnClickListener(v -> { font = idx; layoutDirty = true; savePrefs(); fillSettings(); });
            fr.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        settingsBody.addView(fr, new LinearLayout.LayoutParams(-1, -2));

        section("읽기");
        CheckBox cn = new CheckBox(this);
        cn.setText("페이지 번호 표시");
        cn.setTextColor(Color.WHITE);
        cn.setChecked(showNum);
        cn.setOnCheckedChangeListener((b, c) -> { showNum = c; layoutDirty = true; savePrefs(); });
        settingsBody.addView(cn);
        CheckBox cv = new CheckBox(this);
        cv.setText("볼륨 키로 페이지 넘기기 (아래=다음, 위=이전)");
        cv.setTextColor(Color.WHITE);
        cv.setChecked(volKeys);
        cv.setOnCheckedChangeListener((b, c) -> { volKeys = c; savePrefs(); });
        settingsBody.addView(cv);
        CheckBox ca = new CheckBox(this);
        ca.setText("책 넘기는 효과");
        ca.setTextColor(Color.WHITE);
        ca.setChecked(animOn);
        ca.setOnCheckedChangeListener((b, c) -> { animOn = c; savePrefs(); });
        settingsBody.addView(ca);
        CheckBox ck = new CheckBox(this);
        ck.setText("읽는 동안 화면 켜짐 유지");
        ck.setTextColor(Color.WHITE);
        ck.setChecked(keepOn);
        ck.setOnCheckedChangeListener((b, c) -> { keepOn = c; savePrefs(); applyKeepOn(); });
        settingsBody.addView(ck);
        seekRow("추가 어둡게 (휴대폰 최저 밝기보다 더 어둡게)", 0, 70, dimPct, "%", 1, v -> { dimPct = v; applyDim(); });
        settingsBody.addView(label("화면 밝기 자체는 휴대폰 밝기를 그대로 따라갑니다.", 12, 0xFFAAAAAA));

        section("앱");
        Button up = new Button(this);
        up.setText("업데이트 확인  (현재 v" + Updater.currentName(this) + ")");
        up.setOnClickListener(v -> checkUpdate(true));
        settingsBody.addView(up, new LinearLayout.LayoutParams(-1, -2));

        settingsScroll.post(() -> settingsScroll.scrollTo(0, scroll));
    }

    // ───────────────────────── 색 선택 ─────────────────────────
    private void pickColor(final boolean isBg) {
        if (effTheme() >= 0) { customBg = bgColor(); customFg = fgColor(); }
        final int[] cur = {isBg ? customBg : customFg};
        pickerPanel.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(40), dp(18), dp(20));
        box.addView(label(isBg ? "배경색 직접 설정" : "글자색 직접 설정", 20, Color.WHITE));

        final TextView prev = new TextView(this);
        prev.setText("가나다라마바사 ABC abc 123\n이 색으로 표시됩니다.");
        prev.setTextSize(18);
        prev.setPadding(dp(16), dp(24), dp(16), dp(24));
        box.addView(prev, new LinearLayout.LayoutParams(-1, -2));

        final SeekBar[] sbs = new SeekBar[3];
        final EditText hex = new EditText(this);
        final boolean[] guard = {false};
        box.addView(label("색 코드 (예: #F4ECD8)", 13, 0xFFCCCCCC));
        box.addView(hex, new LinearLayout.LayoutParams(-1, -2));
        final Runnable refresh = () -> {
            int c = cur[0];
            prev.setBackgroundColor(isBg ? c : customBg);
            prev.setTextColor(isBg ? customFg : c);
            guard[0] = true;
            hex.setText(String.format("#%06X", c & 0xFFFFFF));
            guard[0] = false;
        };
        String[] names = {"R (빨강)", "G (초록)", "B (파랑)"};
        int[] vals = {Color.red(cur[0]), Color.green(cur[0]), Color.blue(cur[0])};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            box.addView(label(names[i], 13, 0xFFCCCCCC));
            SeekBar s = new SeekBar(this);
            s.setMax(255);
            s.setProgress(vals[i]);
            sbs[i] = s;
            s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar sb, int p, boolean user) {
                    if (!user) return;
                    cur[0] = Color.rgb(sbs[0].getProgress(), sbs[1].getProgress(), sbs[2].getProgress()) | 0xFF000000;
                    refresh.run();
                }
                public void onStartTrackingTouch(SeekBar sb) {}
                public void onStopTrackingTouch(SeekBar sb) {}
            });
            box.addView(s, new LinearLayout.LayoutParams(-1, -2));
        }

        hex.setSingleLine(true);
        hex.setTextColor(Color.WHITE);
        hex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) {
                if (guard[0]) return;
                String t = e.toString().trim();
                if (t.startsWith("#")) t = t.substring(1);
                if (t.length() != 6) return;
                try {
                    int c = Integer.parseInt(t, 16) | 0xFF000000;
                    cur[0] = c;
                    sbs[0].setProgress(Color.red(c));
                    sbs[1].setProgress(Color.green(c));
                    sbs[2].setProgress(Color.blue(c));
                    prev.setBackgroundColor(isBg ? c : customBg);
                    prev.setTextColor(isBg ? customFg : c);
                } catch (Exception ignored) {}
            }
        });
        refresh.run();

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = new Button(this);
        cancel.setText("취소");
        cancel.setOnClickListener(v -> pickerPanel.setVisibility(View.GONE));
        Button ok = new Button(this);
        ok.setText("적용");
        ok.setOnClickListener(v -> {
            if (isBg) customBg = cur[0]; else customFg = cur[0];
            setTheme(-1);
            savePrefs();
            pickerPanel.setVisibility(View.GONE);
            fillSettings();
        });
        btns.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1f));
        btns.addView(ok, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = dp(20);
        box.addView(btns, bl);

        ScrollView sv = new ScrollView(this);
        sv.addView(box, new ViewGroup.LayoutParams(-1, -2));
        pickerPanel.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        pickerPanel.setVisibility(View.VISIBLE);
    }

    // ───────────────────────── 취침 타이머 ─────────────────────────
    private void sleepDialog() {
        String[] it = {"15분 후", "30분 후", "60분 후", "타이머 끄기"};
        String ttl = "취침 타이머" + (sleepEndAt > 0 ? " (약 " + sleepRemain() + "분 남음)" : "");
        new android.app.AlertDialog.Builder(this).setTitle(ttl).setItems(it, (d, w) -> {
            if (w == 3) setSleep(0); else setSleep(new int[]{15, 30, 60}[w]);
        }).show();
    }

    private int sleepRemain() {
        return (int) Math.max(1, (sleepEndAt - android.os.SystemClock.elapsedRealtime() + 59999) / 60000);
    }

    private void setSleep(int min) {
        if (sleepRun != null) ui.removeCallbacks(sleepRun);
        sleepRun = null;
        if (min <= 0) {
            sleepEndAt = 0;
            Toast.makeText(this, "취침 타이머를 껐어요", Toast.LENGTH_SHORT).show();
        } else {
            sleepEndAt = android.os.SystemClock.elapsedRealtime() + min * 60000L;
            sleepRun = this::sleepFire;
            ui.postDelayed(sleepRun, min * 60000L);
            Toast.makeText(this, min + "분 뒤에 화면을 어둡게 하고 멈춰요", Toast.LENGTH_SHORT).show();
        }
        updateInfo();
    }

    private void sleepFire() {
        sleepEndAt = 0;
        sleepRun = null;
        savePos();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.01f;
        getWindow().setAttributes(lp);
        sleepCover.setVisibility(View.VISIBLE);
    }

    private void sleepWake() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
        sleepCover.setVisibility(View.GONE);
        applyKeepOn();
    }

    // ───────────────────────── 텍스트 검색 ─────────────────────────
    private int pageOf(int off) {
        int lo = 0, hi = pageCount - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (starts[mid] <= off) lo = mid; else hi = mid - 1;
        }
        return Math.max(0, lo);
    }

    private void openSearch() {
        if (text.isEmpty()) { Toast.makeText(this, "먼저 TXT 파일을 열어 주세요", Toast.LENGTH_SHORT).show(); return; }
        setMenu(false);
        searchBar.setVisibility(View.VISIBLE);
        searchEt.requestFocus();
        searchEt.post(() -> {
            InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(searchEt, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void closeSearch() {
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(searchEt.getWindowToken(), 0);
        searchBar.setVisibility(View.GONE);
        searchQ = "";
        lastHit = -1;
        if (pageCount > 0 && !paginating) showPage(false);
    }

    private void highlight(SpannableString ss, String sub) {
        String hay = sub.toLowerCase(Locale.ROOT);
        String q = searchQ.toLowerCase(Locale.ROOT);
        if (hay.length() != sub.length()) { hay = sub; q = searchQ; }
        int i = 0;
        while (!q.isEmpty() && (i = hay.indexOf(q, i)) >= 0) {
            ss.setSpan(new BackgroundColorSpan(0xFFFFD54F), i, i + q.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new ForegroundColorSpan(0xFF000000), i, i + q.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            i += q.length();
        }
    }

    private void doSearch(boolean fwd) {
        String q = searchEt.getText().toString();
        if (q.isEmpty() || text.isEmpty() || pageCount == 0 || paginating) return;
        if (!q.equals(searchQ)) { searchQ = q; lastHit = -1; }
        if (lower == null) lower = text.toLowerCase(Locale.ROOT);
        boolean lowOk = lower.length() == text.length();
        String hay = lowOk ? lower : text;
        String needle = lowOk ? q.toLowerCase(Locale.ROOT) : q;
        int ps = starts[curPage];
        int pe = curPage + 1 < pageCount ? starts[curPage + 1] : text.length();
        boolean inPage = lastHit >= ps && lastHit < pe;
        int h;
        if (fwd) {
            int from = inPage ? lastHit + 1 : ps;
            h = hay.indexOf(needle, from);
            if (h < 0 && from > 0) {
                h = hay.indexOf(needle, 0);
                if (h >= 0) Toast.makeText(this, "끝까지 검색해서 처음부터 다시 찾았어요", Toast.LENGTH_SHORT).show();
            }
        } else {
            int from = inPage ? lastHit - 1 : ps - 1;
            h = from >= 0 ? hay.lastIndexOf(needle, from) : -1;
            if (h < 0) {
                h = hay.lastIndexOf(needle);
                if (h >= 0) Toast.makeText(this, "처음까지 검색해서 끝에서부터 다시 찾았어요", Toast.LENGTH_SHORT).show();
            }
        }
        if (h < 0) { searchInfo.setText("찾을 수 없어요"); showPage(false); return; }
        lastHit = h;
        curPage = pageOf(h);
        showPage(true);
        searchInfo.setText((curPage + 1) + " / " + pageCount + " 쪽에서 찾음");
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(searchEt.getWindowToken(), 0);
    }

    // ───────────────────────── 다음권 ─────────────────────────
    private static int natCmp(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i), y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int i2 = i, j2 = j;
                while (i2 < a.length() && Character.isDigit(a.charAt(i2))) i2++;
                while (j2 < b.length() && Character.isDigit(b.charAt(j2))) j2++;
                String na = a.substring(i, i2).replaceFirst("^0+(?=.)", ""), nb = b.substring(j, j2).replaceFirst("^0+(?=.)", "");
                if (na.length() != nb.length()) return na.length() - nb.length();
                int c = na.compareTo(nb);
                if (c != 0) return c;
                i = i2; j = j2;
            } else {
                int c = Character.compare(Character.toLowerCase(x), Character.toLowerCase(y));
                if (c != 0) return c;
                i++; j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    private int treeFor = 0; // 0=다음권, 1=파일 관리

    private void askTree(int forWhat) {
        treeFor = forWhat;
        Toast.makeText(this, forWhat == 1 ? "관리할 폴더를 선택해 주세요 (이동·삭제 허용 필요)" : forWhat >= 2 ? "백업 파일을 저장·읽을 폴더를 선택해 주세요 (쓰기 허용 필요)" : "책 파일들이 들어 있는 폴더를 한 번만 선택해 주세요", Toast.LENGTH_LONG).show();
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_TREE);
    }

    /** 선택해 둔 폴더에서 현재 파일 다음 순서(숫자 순)의 TXT를 연다 */
    private void openNextVolume() {
        final String t = sp.getString("tree", null);
        if (t == null) { askTree(0); return; }
        final String cur = curName;
        nextBtn.setEnabled(false);
        new Thread(() -> {
            String err = null;
            Uri next = null;
            boolean needTree = false;
            try {
                Uri tree = Uri.parse(t);
                Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
                ArrayList<String[]> files = new ArrayList<>();
                try (Cursor c = getContentResolver().query(kids, new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                    while (c != null && c.moveToNext()) {
                        String nm = c.getString(1);
                        if (nm != null && !DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2)) && nm.toLowerCase(Locale.ROOT).endsWith(".txt"))
                            files.add(new String[]{c.getString(0), nm});
                    }
                }
                Collections.sort(files, (x, y) -> natCmp(x[1], y[1]));
                int idx = -1;
                for (int i = 0; i < files.size(); i++) if (files.get(i)[1].equals(cur)) { idx = i; break; }
                if (idx < 0) needTree = true;
                else if (idx + 1 >= files.size()) err = "마지막 권이에요";
                else next = DocumentsContract.buildDocumentUriUsingTree(tree, files.get(idx + 1)[0]);
            } catch (Exception e) { needTree = true; }
            final Uri fn = next; final String fe = err; final boolean nt = needTree;
            ui.post(() -> {
                nextBtn.setEnabled(true);
                if (fn != null) open(fn);
                else if (nt) { Toast.makeText(this, "현재 파일이 선택한 폴더에 없어요. 폴더를 다시 선택해 주세요", Toast.LENGTH_LONG).show(); askTree(0); }
                else if (fe != null) Toast.makeText(this, fe, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    // ───────────────────────── 백업 / 복원 ─────────────────────────
    private static boolean isBackupKey(String k) {
        return !k.equals("tree") && !k.equals("tver");
    }

    private String buildBackup() throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "나의소설");
        root.put("v", 1);
        JSONObject ps = new JSONObject();
        for (java.util.Map.Entry<String, ?> en : sp.getAll().entrySet()) {
            String k = en.getKey(); Object v = en.getValue();
            if (!isBackupKey(k) || k.startsWith("off_")) continue;
            JSONObject o = new JSONObject();
            if (v instanceof Boolean) { o.put("t", "b"); o.put("v", (Boolean) v); }
            else if (v instanceof Integer) { o.put("t", "i"); o.put("v", (Integer) v); }
            else if (v instanceof Float) { o.put("t", "f"); o.put("v", ((Float) v).doubleValue()); }
            else if (v instanceof String) { o.put("t", "s"); o.put("v", (String) v); }
            else continue;
            ps.put(k, o);
        }
        root.put("prefs", ps);
        return root.toString();
    }

    private void doBackup() {
        final String t = sp.getString("tree", null);
        if (t == null || !hasWrite(Uri.parse(t))) { askTree(2); return; }
        new Thread(() -> {
            String msg;
            try {
                final String data = buildBackup();
                Uri tree = Uri.parse(t);
                String rootId = DocumentsContract.getTreeDocumentId(tree);
                Uri existing = null;
                Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId);
                try (Cursor c = getContentResolver().query(kids, new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                    while (c != null && c.moveToNext())
                        if (BACKUP_NAME.equals(c.getString(1))) { existing = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)); break; }
                }
                if (existing == null)
                    existing = DocumentsContract.createDocument(getContentResolver(), DocumentsContract.buildDocumentUriUsingTree(tree, rootId), "application/json", BACKUP_NAME);
                if (existing == null) throw new IllegalStateException("파일을 만들지 못했어요");
                try (OutputStream os = getContentResolver().openOutputStream(existing, "wt")) {
                    os.write(data.getBytes("UTF-8"));
                }
                msg = "백업 완료: 선택한 폴더의 '" + BACKUP_NAME + "'";
            } catch (Exception e) {
                msg = "백업 실패: " + e.getMessage() + "\n폴더를 다시 선택해 보세요";
            }
            final String m = msg;
            ui.post(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
        }).start();
    }

    private void confirmRestore() {
        final String t = sp.getString("tree", null);
        if (t == null || !hasWrite(Uri.parse(t))) { askTree(3); return; }
        new android.app.AlertDialog.Builder(this).setTitle("복원")
                .setMessage("선택한 폴더의 '" + BACKUP_NAME + "'로 설정·읽던 위치·책갈피를 덮어씁니다. 계속할까요?")
                .setPositiveButton("복원", (d, w) -> doRestore(t))
                .setNegativeButton("취소", null).show();
    }

    private void doRestore(final String t) {
        new Thread(() -> {
            String msg;
            boolean ok = false;
            try {
                Uri tree = Uri.parse(t);
                String rootId = DocumentsContract.getTreeDocumentId(tree);
                Uri src = null;
                Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId);
                try (Cursor c = getContentResolver().query(kids, new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                    while (c != null && c.moveToNext())
                        if (BACKUP_NAME.equals(c.getString(1))) { src = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)); break; }
                }
                if (src == null) throw new IllegalStateException("선택한 폴더에 '" + BACKUP_NAME + "'가 없어요");
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream in = getContentResolver().openInputStream(src)) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                }
                JSONObject ps = new JSONObject(bo.toString("UTF-8")).getJSONObject("prefs");
                SharedPreferences.Editor ed = sp.edit();
                java.util.Iterator<String> it = ps.keys();
                int cnt = 0;
                while (it.hasNext()) {
                    String k = it.next();
                    if (!isBackupKey(k)) continue;
                    JSONObject o = ps.getJSONObject(k);
                    String ty = o.getString("t");
                    if (ty.equals("b")) ed.putBoolean(k, o.getBoolean("v"));
                    else if (ty.equals("i")) ed.putInt(k, o.getInt("v"));
                    else if (ty.equals("f")) ed.putFloat(k, (float) o.getDouble("v"));
                    else if (ty.equals("s")) ed.putString(k, o.getString("v"));
                    else continue;
                    cnt++;
                }
                ed.apply();
                ok = true;
                msg = "복원 완료 (" + cnt + "개 항목)";
            } catch (Exception e) {
                msg = "복원 실패: " + e.getMessage();
            }
            final String m = msg; final boolean fok = ok;
            ui.post(() -> {
                Toast.makeText(this, m, Toast.LENGTH_LONG).show();
                if (!fok) return;
                loadPrefs();
                applyDim(); applyKeepOn();
                lastEff = -2; layoutDirty = true;
                if (!bk.isEmpty()) {
                    keepOffset = sp.getInt("o2_" + bk, keepOffset);
                    loadBms();
                }
                pv.invalidate();
                if (pageCount > 0) requestRepaginate();
                fillSettings();
            });
        }).start();
    }

    // ───────────────────────── 파일 관리 ─────────────────────────
    private static String fmtSize(long b) {
        if (b < 1024) return b + "B";
        if (b < 1024 * 1024) return (b / 1024) + "KB";
        return String.format(Locale.US, "%.1fMB", b / 1048576.0);
    }

    private Uri docUri(String id) { return DocumentsContract.buildDocumentUriUsingTree(fmTree, id); }

    private boolean hasWrite(Uri tree) {
        try {
            for (android.content.UriPermission up : getContentResolver().getPersistedUriPermissions())
                if (up.getUri().equals(tree) && up.isWritePermission()) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private void openFm() {
        setMenu(false);
        String t = sp.getString("tree", null);
        if (t == null || !hasWrite(Uri.parse(t))) { askTree(1); return; }
        fmTree = Uri.parse(t);
        fmStackIds.clear(); fmStackNames.clear();
        fmMoving = false; fmMoveSrc = null;
        try { fmDirId = DocumentsContract.getTreeDocumentId(fmTree); } catch (Exception e) { askTree(1); return; }
        fmPanel.setVisibility(View.VISIBLE);
        fmLoad();
    }

    private void fmClose() {
        fmMoving = false; fmMoveSrc = null;
        fmPanel.setVisibility(View.GONE);
    }

    private void fmBack() {
        if (!fmStackIds.isEmpty()) fmUp(); else fmClose();
    }

    private void fmUp() {
        if (fmStackIds.isEmpty()) { Toast.makeText(this, "선택한 폴더의 맨 위예요", Toast.LENGTH_SHORT).show(); return; }
        fmDirId = fmStackIds.remove(fmStackIds.size() - 1);
        fmStackNames.remove(fmStackNames.size() - 1);
        fmLoad();
    }

    private void fmLoad() {
        final String dirId = fmDirId;
        final Uri tree = fmTree;
        StringBuilder sb = new StringBuilder("/");
        for (String n : fmStackNames) sb.append(n).append("/");
        fmPath.setText(sb.toString());
        new Thread(() -> {
            ArrayList<Fe> list = new ArrayList<>();
            String err = null;
            try {
                Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirId);
                try (Cursor c = getContentResolver().query(kids, new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE}, null, null, null)) {
                    while (c != null && c.moveToNext()) {
                        Fe e = new Fe();
                        e.id = c.getString(0); e.name = c.getString(1) == null ? "?" : c.getString(1);
                        e.mime = c.getString(2); e.size = c.isNull(3) ? 0 : c.getLong(3);
                        e.dir = DocumentsContract.Document.MIME_TYPE_DIR.equals(e.mime);
                        list.add(e);
                    }
                }
                Collections.sort(list, (x, y) -> x.dir != y.dir ? (x.dir ? -1 : 1) : natCmp(x.name, y.name));
            } catch (Exception e) { err = e.getMessage(); }
            final String fe = err;
            ui.post(() -> {
                if (!dirId.equals(fmDirId)) return;
                if (fe != null) Toast.makeText(this, "목록을 읽지 못했어요: " + fe, Toast.LENGTH_LONG).show();
                fmAll = list;
                fmFilter = "";
                applyFmFilter();
            });
        }).start();
    }

    private void applyFmFilter() {
        if (fmFilter.isEmpty()) fmItems = fmAll;
        else {
            ArrayList<Fe> r = new ArrayList<>();
            String q = fmFilter.toLowerCase(Locale.ROOT);
            for (Fe e : fmAll) if (e.name.toLowerCase(Locale.ROOT).contains(q)) r.add(e);
            fmItems = r;
        }
        fmSearchB.setText(fmFilter.isEmpty() ? "파일명 검색" : "검색 해제");
        fmAdapter.notifyDataSetChanged();
        updateFmBottom();
    }

    private void fmSearchDialog() {
        if (!fmFilter.isEmpty()) { fmFilter = ""; applyFmFilter(); return; }
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setHint("파일명에 포함된 글자");
        new android.app.AlertDialog.Builder(this).setTitle("이 폴더에서 파일명 검색").setView(et)
                .setPositiveButton("검색", (d, w) -> { fmFilter = et.getText().toString().trim(); applyFmFilter(); })
                .setNegativeButton("취소", null).show();
    }

    private void fmClick(Fe e) {
        if (e.dir) {
            fmStackIds.add(fmDirId);
            fmStackNames.add(e.name);
            fmDirId = e.id;
            fmLoad();
        } else if (e.name.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            fmClose();
            settingsPanel.setVisibility(View.GONE);
            closeSearch();
            setMenu(false);
            open(docUri(e.id));
        } else {
            Toast.makeText(this, "TXT 파일만 열 수 있어요", Toast.LENGTH_SHORT).show();
        }
    }

    private ArrayList<Fe> fmSelected() {
        ArrayList<Fe> r = new ArrayList<>();
        for (Fe e : fmItems) if (e.sel) r.add(e);
        return r;
    }

    private Button fmBtn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        fmBottom.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        return b;
    }

    private void updateFmBottom() {
        fmBottom.removeAllViews();
        int n = fmSelected().size();
        if (fmMoving) {
            fmInfo.setText(fmMoveSrc.size() + "개를 이동 중이에요. 옮길 폴더로 들어간 뒤 '여기로 이동'을 누르세요.");
            fmBtn("여기로 이동", v -> fmMoveHere());
            fmBtn("이동 취소", v -> { fmMoving = false; fmMoveSrc = null; updateFmBottom(); });
        } else if (n > 0) {
            fmInfo.setText(n + "개 선택됨");
            fmBtn("이동", v -> { fmMoveSrc = fmSelected(); fmMoveSrcDir = fmDirId; fmMoving = true; for (Fe e : fmItems) e.sel = false; fmAdapter.notifyDataSetChanged(); updateFmBottom(); });
            fmBtn("삭제", v -> fmDelete());
            if (n == 1) fmBtn("이름 변경", v -> fmRename());
            fmBtn("선택 해제", v -> { for (Fe e : fmItems) e.sel = false; fmAdapter.notifyDataSetChanged(); updateFmBottom(); });
        } else {
            fmInfo.setText(fmItems.isEmpty() ? "비어 있는 폴더예요" : "왼쪽 체크박스로 선택하면 이동·삭제할 수 있어요");
        }
    }

    private void fmMoveHere() {
        final ArrayList<Fe> src = fmMoveSrc;
        final String srcDir = fmMoveSrcDir, dst = fmDirId;
        if (src == null) return;
        if (srcDir.equals(dst)) { Toast.makeText(this, "같은 폴더예요. 다른 폴더로 들어가 주세요", Toast.LENGTH_SHORT).show(); return; }
        new Thread(() -> {
            int ok = 0, fail = 0;
            Uri sp0 = docUri(srcDir), dp0 = docUri(dst);
            for (Fe e : src) {
                boolean done = false;
                try {
                    Uri r = DocumentsContract.moveDocument(getContentResolver(), docUri(e.id), sp0, dp0);
                    done = r != null;
                } catch (Exception ex) { done = false; }
                if (!done && !e.dir) {
                    try {   // 이동을 지원하지 않는 저장소: 복사 후 원본 삭제
                        Uri nu = DocumentsContract.createDocument(getContentResolver(), dp0, e.mime == null ? "text/plain" : e.mime, e.name);
                        if (nu != null) {
                            try (InputStream in = getContentResolver().openInputStream(docUri(e.id));
                                 java.io.OutputStream out = getContentResolver().openOutputStream(nu)) {
                                byte[] buf = new byte[65536]; int n;
                                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                            }
                            done = DocumentsContract.deleteDocument(getContentResolver(), docUri(e.id));
                        }
                    } catch (Exception ex) { done = false; }
                }
                if (done) ok++; else fail++;
            }
            final int fo = ok, ff = fail;
            ui.post(() -> {
                fmMoving = false; fmMoveSrc = null;
                Toast.makeText(this, fo + "개 이동" + (ff > 0 ? ", " + ff + "개 실패" : ""), Toast.LENGTH_LONG).show();
                fmLoad();
            });
        }).start();
    }

    private void fmDelete() {
        final ArrayList<Fe> sel = fmSelected();
        if (sel.isEmpty()) return;
        new android.app.AlertDialog.Builder(this)
                .setTitle("삭제할까요?")
                .setMessage("선택한 " + sel.size() + "개를 삭제합니다.\n휴지통 없이 바로 영구 삭제되어 되돌릴 수 없어요."
                        + (sel.size() == 1 ? "\n\n" + sel.get(0).name : ""))
                .setPositiveButton("삭제", (d, w) -> new Thread(() -> {
                    int ok = 0, fail = 0;
                    for (Fe e : sel) {
                        try { if (DocumentsContract.deleteDocument(getContentResolver(), docUri(e.id))) ok++; else fail++; }
                        catch (Exception ex) { fail++; }
                    }
                    final int fo = ok, ff = fail;
                    ui.post(() -> {
                        Toast.makeText(this, fo + "개 삭제" + (ff > 0 ? ", " + ff + "개 실패" : ""), Toast.LENGTH_LONG).show();
                        fmLoad();
                    });
                }).start())
                .setNegativeButton("취소", null)
                .show();
    }

    private void fmAskName(String title, String init, java.util.function.Consumer<String> cb) {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(init);
        et.setSelection(init.length());
        new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(et)
                .setPositiveButton("확인", (d, w) -> { String v = et.getText().toString().trim(); if (!v.isEmpty()) cb.accept(v); })
                .setNegativeButton("취소", null)
                .show();
    }

    private void fmRename() {
        final ArrayList<Fe> sel = fmSelected();
        if (sel.size() != 1) return;
        final Fe e = sel.get(0);
        fmAskName("이름 변경", e.name, nm -> new Thread(() -> {
            boolean ok;
            try { ok = DocumentsContract.renameDocument(getContentResolver(), docUri(e.id), nm) != null; } catch (Exception ex) { ok = false; }
            final boolean fo = ok;
            ui.post(() -> { if (!fo) Toast.makeText(this, "이름을 바꾸지 못했어요", Toast.LENGTH_LONG).show(); fmLoad(); });
        }).start());
    }

    private void fmNewFolder() {
        fmAskName("새 폴더", "", nm -> new Thread(() -> {
            boolean ok;
            try { ok = DocumentsContract.createDocument(getContentResolver(), docUri(fmDirId), DocumentsContract.Document.MIME_TYPE_DIR, nm) != null; } catch (Exception ex) { ok = false; }
            final boolean fo = ok;
            ui.post(() -> { if (!fo) Toast.makeText(this, "폴더를 만들지 못했어요", Toast.LENGTH_LONG).show(); fmLoad(); });
        }).start());
    }

    // ───────────────────────── 업데이트 ─────────────────────────
    private void checkUpdate(boolean manual) {
        Updater.check(this, (newer, ver, url, err) -> {
            if (isFinishing()) return;
            if (err != null) { if (manual) Toast.makeText(this, err, Toast.LENGTH_LONG).show(); return; }
            if (newer) Updater.promptAndInstall(this, ver, url);
            else if (manual) Toast.makeText(this, "최신 버전이에요 (v" + Updater.currentName(this) + ")", Toast.LENGTH_SHORT).show();
        });
    }
}
