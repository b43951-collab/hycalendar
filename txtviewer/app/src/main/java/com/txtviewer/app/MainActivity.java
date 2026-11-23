package com.txtviewer.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
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
import android.widget.Button;
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

    // 순백/순흑을 피하고 명암비 약 10~14:1(WCAG AAA 7:1 이상)을 유지한 테마들
    private static final String[] T_NAME = {"종이 회백", "크림", "연회색", "세피아",
            "소프트 다크", "따뜻한 다크", "네이비 다크", "저조도 다크"};
    private static final int[] T_BG = {0xFFF2F2EE, 0xFFF5EEDC, 0xFFE3E3E0, 0xFFEBDDBE,
            0xFF1C1C1E, 0xFF211D1A, 0xFF172033, 0xFF0E0E0E};
    private static final int[] T_FG = {0xFF222222, 0xFF2E2A24, 0xFF1F1F1F, 0xFF3A2F22,
            0xFFD0D0D0, 0xFFD6CBB8, 0xFFC5D0E0, 0xFFB8B8B8};
    private static final String[] FONT_NAME = {"기본", "명조(세리프)", "고정폭"};

    private interface IntCb { void on(int v); }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences sp;

    // 설정값
    private int theme;          // 0..7, -1 = 직접 설정
    private int customBg, customFg;
    private float sizeSp;
    private int spacing10;      // 줄 간격 x10
    private int marginH, marginV; // dp
    private int font;
    private boolean showNum, volKeys;

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
        if (settingsPanel.getVisibility() == View.VISIBLE) { closeSettings(true); return; }
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
        customBg = sp.getInt("cbg", T_BG[0]);
        customFg = sp.getInt("cfg", T_FG[0]);
        sizeSp = sp.getFloat("size", 18f);
        spacing10 = sp.getInt("spacing", 16);
        marginH = sp.getInt("mh", 20);
        marginV = sp.getInt("mv", 28);
        font = sp.getInt("font", 0);
        showNum = sp.getBoolean("num", true);
        volKeys = sp.getBoolean("vol", true);
    }

    private void savePrefs() {
        sp.edit().putInt("theme", theme).putInt("cbg", customBg).putInt("cfg", customFg)
                .putFloat("size", sizeSp).putInt("spacing", spacing10).putInt("mh", marginH)
                .putInt("mv", marginV).putInt("font", font).putBoolean("num", showNum)
                .putBoolean("vol", volKeys).apply();
    }

    private int bgColor() { return theme >= 0 ? T_BG[theme] : customBg; }
    private int fgColor() { return theme >= 0 ? T_FG[theme] : customFg; }

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
        quickBtn("텍스트 검색", () -> openSearch());
        quickBtn("설정", () -> openSettings());
        quickBtn("TXT 파일 열기", () -> pick());
        FrameLayout.LayoutParams ql = new FrameLayout.LayoutParams(dp(260), -2);
        ql.gravity = Gravity.CENTER;
        quickPanel.addView(quickCard, ql);
        quickPanel.setVisibility(View.GONE);
        root.addView(quickPanel, new FrameLayout.LayoutParams(-1, -1));

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

        pickerPanel = new FrameLayout(this);
        pickerPanel.setBackgroundColor(0xFF202020);
        pickerPanel.setClickable(true);
        pickerPanel.setVisibility(View.GONE);
        root.addView(pickerPanel, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
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
        int n = curPage + d;
        if (n < 0) { Toast.makeText(this, "첫 페이지예요", Toast.LENGTH_SHORT).show(); return; }
        if (n >= pageCount) { Toast.makeText(this, "마지막 페이지예요", Toast.LENGTH_SHORT).show(); return; }
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
        ui.postDelayed(pendingTap, 260);
    }

    private void showPage(boolean save) {
        if (pageCount == 0) return;
        curPage = Math.max(0, Math.min(pageCount - 1, curPage));
        int s = starts[curPage];
        int e = curPage + 1 < pageCount ? starts[curPage + 1] : text.length();
        String sub = text.substring(s, Math.max(s, e));
        CharSequence cs = searchQ.isEmpty() ? sub : highlight(sub);
        pv.layout = buildLayout(cs, pv.pagePaint(), pv.textW(), spacing10 / 10f);
        keepOffset = s;
        nextBtn.setVisibility(!paginating && curPage == pageCount - 1 ? View.VISIBLE : View.GONE);
        pv.invalidate();
        if (save) savePos();
        updateInfo();
    }

    private void updateInfo() {
        if (pageCount == 0 || paginating) { infoTv.setText(""); return; }
        infoTv.setText((curPage + 1) + " / " + pageCount + " 쪽  ·  " + (int) ((curPage + 1) * 100L / pageCount) + "%");
        if (!seeking && pageCount > 1) seek.setProgress((int) (curPage * 1000L / (pageCount - 1)));
    }

    private void savePos() {
        if (curUri != null && pageCount > 0 && !paginating)
            sp.edit().putInt("off_" + curUri.toString().hashCode(), starts[Math.min(curPage, pageCount - 1)]).apply();
    }

    // ───────────────────────── 페이지 계산 ─────────────────────────
    private static StaticLayout buildLayout(CharSequence s, TextPaint tp, int w, float mult) {
        return StaticLayout.Builder.obtain(s, 0, s.length(), tp, Math.max(50, w))
                .setLineSpacing(0f, mult).setIncludePad(false)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build();
    }

    private static int pageEnd(String t, int start, int w, int h, TextPaint tp, float mult) {
        int len = t.length();
        float px = tp.getTextSize();
        int cpl = (int) (w / (px * 0.45f)) + 1;
        int lpp = (int) (h / (px * 1.1f * mult)) + 2;
        int chunk = Math.max(600, (int) (cpl * lpp * 1.6f) + 200);
        while (true) {
            int e = Math.min(len, start + chunk);
            StaticLayout sl = buildLayout(t.substring(start, e), tp, w, mult);
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
                pos = pageEnd(t, pos, w, h, tp, mult);
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
            c.save();
            c.translate(dp(marginH), topPad());
            c.clipRect(0, 0, textW(), textH());
            layout.draw(c);
            c.restore();
            if (showNum && pageCount > 0) {
                foot.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));
                foot.setColor((fgColor() & 0x00FFFFFF) | 0x88000000);
                foot.setTextAlign(android.graphics.Paint.Align.CENTER);
                c.drawText((curPage + 1) + " / " + pageCount, getWidth() / 2f, getHeight() - dp(marginV) + dp(6), foot);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dx0 = e.getX(); dy0 = e.getY();
                    longFired = false;
                    if (!text.isEmpty()) ui.postDelayed(lpRun, 550);
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
        pv.msg = "화면을 빠르게 두 번 터치하면 메뉴가 나와요.\n\n메뉴 → 설정 → 파일 열기로 TXT를 선택하세요.\n\n· 오른쪽 터치: 다음 페이지\n· 왼쪽 터치: 이전 페이지";
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
                try { getContentResolver().takePersistableUriPermission(d.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
                sp.edit().putString("tree", d.getData().toString()).apply();
                openNextVolume();
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
        final int saved = sp.getInt("off_" + u.toString().hashCode(), 0);
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
            final String fe = err, fs = str, fn = name, fc = cs;
            ui.post(() -> {
                if (fe != null || fs == null) {
                    paginating = false;
                    Toast.makeText(this, "열지 못했어요: " + fe, Toast.LENGTH_LONG).show();
                    if (text.isEmpty()) showHelp();
                    return;
                }
                text = fs; curUri = u; curName = fn; curCharset = fc;
                keepOffset = saved;
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
        snapI = new int[]{theme, customBg, customFg, spacing10, marginH, marginV, font, showNum ? 1 : 0, volKeys ? 1 : 0};
        snapSize = sizeSp;
        fillSettings();
        settingsScroll.scrollTo(0, 0);
        settingsPanel.setVisibility(View.VISIBLE);
    }

    /** save=true: 저장하고 닫기 / false: 이번에 바꾼 내용을 취소하고 닫기 */
    private void closeSettings(boolean save) {
        if (!save && snapI != null) {
            theme = snapI[0]; customBg = snapI[1]; customFg = snapI[2]; spacing10 = snapI[3];
            marginH = snapI[4]; marginV = snapI[5]; font = snapI[6]; showNum = snapI[7] == 1; volKeys = snapI[8] == 1;
            sizeSp = snapSize;
            layoutDirty = false;
        }
        settingsPanel.setVisibility(View.GONE);
        savePrefs();
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
        Button open = new Button(this);
        open.setText("TXT 파일 열기");
        open.setOnClickListener(v -> pick());
        settingsBody.addView(open, new LinearLayout.LayoutParams(-1, -2));
        if (!curName.isEmpty()) settingsBody.addView(label("현재: " + curName + " (" + curCharset + ")", 12, 0xFFAAAAAA));

        section("테마 (눈의 피로를 줄인 색 조합)");
        for (int i = 0; i < T_NAME.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(i + 2, T_NAME.length); j++) {
                final int idx = j;
                Button b = new Button(this);
                b.setAllCaps(false);
                b.setText(T_NAME[j] + "\n가나다 ABC");
                b.setTextColor(T_FG[j]);
                b.setTextSize(13);
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(T_BG[j]);
                gd.setCornerRadius(dp(8));
                gd.setStroke(theme == j ? dp(3) : dp(1), theme == j ? 0xFF4C9AFF : 0xFF666666);
                b.setBackground(gd);
                b.setOnClickListener(v -> { theme = idx; savePrefs(); fillSettings(); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(72), 1f);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                row.addView(b, lp);
            }
            settingsBody.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        section("직접 설정" + (theme < 0 ? "  (사용 중)" : ""));
        LinearLayout cr = new LinearLayout(this);
        cr.setOrientation(LinearLayout.HORIZONTAL);
        cr.setGravity(Gravity.CENTER_VERTICAL);
        colorEntry(cr, "배경색", bgColor(), true);
        colorEntry(cr, "글자색", fgColor(), false);
        settingsBody.addView(cr, new LinearLayout.LayoutParams(-1, -2));

        section("글자");
        seekRow("글자 크기", 10, 40, Math.round(sizeSp), "sp", 1, v -> sizeSp = v);
        seekRow("줄 간격", 10, 25, spacing10, "배", 10, v -> spacing10 = v);
        seekRow("좌우 여백", 0, 60, marginH, "dp", 1, v -> marginH = v);
        seekRow("상하 여백", 0, 80, marginV, "dp", 1, v -> marginV = v);
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
        settingsBody.addView(label("화면 밝기는 휴대폰 밝기를 그대로 따라갑니다.", 12, 0xFFAAAAAA));

        section("앱");
        Button up = new Button(this);
        up.setText("업데이트 확인  (현재 v" + Updater.currentName(this) + ")");
        up.setOnClickListener(v -> checkUpdate(true));
        settingsBody.addView(up, new LinearLayout.LayoutParams(-1, -2));

        settingsScroll.post(() -> settingsScroll.scrollTo(0, scroll));
    }

    // ───────────────────────── 색 선택 ─────────────────────────
    private void pickColor(final boolean isBg) {
        if (theme >= 0) { customBg = bgColor(); customFg = fgColor(); }
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
            theme = -1;
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

    private CharSequence highlight(String sub) {
        SpannableString ss = new SpannableString(sub);
        String hay = sub.toLowerCase(Locale.ROOT);
        String q = searchQ.toLowerCase(Locale.ROOT);
        if (hay.length() != sub.length()) { hay = sub; q = searchQ; }
        int i = 0;
        while (!q.isEmpty() && (i = hay.indexOf(q, i)) >= 0) {
            ss.setSpan(new BackgroundColorSpan(0xFFFFD54F), i, i + q.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ss.setSpan(new ForegroundColorSpan(0xFF000000), i, i + q.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            i += q.length();
        }
        return ss;
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

    private void askTree() {
        Toast.makeText(this, "책 파일들이 들어 있는 폴더를 한 번만 선택해 주세요", Toast.LENGTH_LONG).show();
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_TREE);
    }

    /** 선택해 둔 폴더에서 현재 파일 다음 순서(숫자 순)의 TXT를 연다 */
    private void openNextVolume() {
        final String t = sp.getString("tree", null);
        if (t == null) { askTree(); return; }
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
                else if (nt) { Toast.makeText(this, "현재 파일이 선택한 폴더에 없어요. 폴더를 다시 선택해 주세요", Toast.LENGTH_LONG).show(); askTree(); }
                else if (fe != null) Toast.makeText(this, fe, Toast.LENGTH_LONG).show();
            });
        }).start();
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
