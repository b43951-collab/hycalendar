package com.hycalendar.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private static final String[] DOWS = {"일", "월", "화", "수", "목", "금", "토"};
    private static final int REQ_EXPORT = 11, REQ_IMPORT = 12;

    private float dp;
    private Theme th;
    private int tab = 0;
    private YearMonth ym = YearMonth.now();
    private int leaveYear;
    private LinearLayout header, tabbar;
    private FrameLayout content;
    private TextView title;
    private CalView cal;

    private int dp(int v) { return (int) (v * dp + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Store.init(this);
        dp = getResources().getDisplayMetrics().density;
        leaveYear = Math.max(LeaveCore.START, LocalDate.now().getYear());
        versionBackup();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        buildUi();
        Alarms.schedule(this);
        WidgetBase.updateAll(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cal != null) cal.invalidate();
    }

    // ---------- 버전 변경 시 자동 백업 (데이터는 업데이트로 바뀌지 않는다) ----------
    private void versionBackup() {
        try {
            String ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            String old = Store.getS("app_ver", "");
            if (!old.isEmpty() && !old.equals(ver)) {
                File f = new File(getFilesDir(), "backup_" + old + "_" + System.currentTimeMillis() + ".json");
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(Store.exportJson().getBytes(StandardCharsets.UTF_8)); }
                File[] all = getFilesDir().listFiles((d, n) -> n.startsWith("backup_"));
                if (all != null && all.length > 5) {
                    java.util.Arrays.sort(all);
                    for (int i = 0; i < all.length - 5; i++) all[i].delete();
                }
            }
            Store.putS("app_ver", ver);
        } catch (Exception ignored) { }
    }

    // ---------- UI 뼈대 ----------
    private void buildUi() {
        th = Theme.get(Store.getS("theme", "auto"), Opts.systemDark(this));
        getWindow().setStatusBarColor(th.bg);
        getWindow().setNavigationBarColor(th.bg);
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        if (!th.dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(th.bg);
        root.setFitsSystemWindows(true);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = tv("‹", 22, th.fg);
        prev.setGravity(Gravity.CENTER);
        prev.setOnClickListener(v -> moveMonth(-1));
        TextView next = tv("›", 22, th.fg);
        next.setGravity(Gravity.CENTER);
        next.setOnClickListener(v -> moveMonth(1));
        title = tv("", 15, th.fg);
        title.setGravity(Gravity.CENTER);
        title.setOnClickListener(v -> { ym = YearMonth.now(); refreshTitle(); if (cal != null) cal.invalidate(); });
        header.addView(prev, new LinearLayout.LayoutParams(dp(52), dp(34)));
        header.addView(title, new LinearLayout.LayoutParams(0, dp(34), 1));
        header.addView(next, new LinearLayout.LayoutParams(dp(52), dp(34)));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        tabbar = new LinearLayout(this);
        tabbar.setOrientation(LinearLayout.HORIZONTAL);
        tabbar.setBackgroundColor(th.bg);
        String[] names = {"달력", "부부", "4개조", "근태", "설정"};
        for (int i = 0; i < names.length; i++) {
            final int ix = i;
            TextView t = tv(names[i], 13, th.fg);
            t.setGravity(Gravity.CENTER);
            t.setTag("tab" + i);
            t.setOnClickListener(v -> showTab(ix));
            tabbar.addView(t, new LinearLayout.LayoutParams(0, dp(46), 1));
        }
        root.addView(tabbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        showTab(tab);
    }

    private void showTab(int i) {
        tab = i;
        content.removeAllViews();
        cal = null;
        for (int k = 0; k < 5; k++) {
            TextView t = tabbar.findViewWithTag("tab" + k);
            if (t == null) continue;
            t.setTextColor(k == i ? th.todayStroke : th.sub);
            t.setTypeface(null, k == i ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        if (i <= 2) {
            header.setVisibility(View.VISIBLE);
            cal = new CalView(i);
            content.addView(cal, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            refreshTitle();
        } else {
            header.setVisibility(View.GONE);
            content.addView(i == 3 ? buildLeave() : buildSettings());
        }
    }

    private void refreshTitle() { title.setText(ym.getYear() + "." + ym.getMonthValue()); }

    private void moveMonth(int d) {
        ym = ym.plusMonths(d);
        refreshTitle();
        if (cal != null) cal.invalidate();
    }

    private void dataChanged() {
        Alarms.schedule(this);
        WidgetBase.updateAll(this);
        if (cal != null) cal.invalidate();
        if (tab == 3) showTab(3);
    }

    // ---------- 달력 뷰 ----------
    private class CalView extends View {
        private final int mode;
        private final GestureDetector gd;

        CalView(int mode) {
            super(MainActivity.this);
            this.mode = mode;
            gd = new GestureDetector(MainActivity.this, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }

                @Override public boolean onSingleTapUp(MotionEvent e) {
                    Opts o = Opts.load(MainActivity.this, "", CalView.this.mode);
                    Renderer.Grid g = Renderer.grid(getWidth(), getHeight(), ym, o);
                    LocalDate d = g.cellAt(e.getX(), e.getY());
                    if (d != null) showDay(d);
                    return true;
                }

                @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                    if (e1 == null || e2 == null) return false;
                    float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                    if (Math.abs(dx) > dp(70) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        moveMonth(dx < 0 ? 1 : -1);
                        return true;
                    }
                    return false;
                }
            });
        }

        @Override public boolean onTouchEvent(MotionEvent e) { return gd.onTouchEvent(e); }

        @Override protected void onDraw(Canvas c) {
            Opts o = Opts.load(MainActivity.this, "", mode);
            Renderer.draw(MainActivity.this, c, getWidth(), getHeight(), ym, o);
        }
    }

    // ---------- 하루 상세 ----------
    private void showDay(LocalDate d) {
        Opts o = Opts.load(this, "", Renderer.COUPLE);
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(18), dp(20), dp(10));
        box.setBackgroundColor(th.bg);
        sv.addView(box);

        Lunar.Res lr = Lunar.of(d);
        box.addView(tv(d.getMonthValue() + "월 " + d.getDayOfMonth() + "일 (" + DOWS[d.getDayOfWeek().getValue() % 7] + ")  음력 " + (lr.leap ? "윤" : "") + lr.m + "." + lr.d, 18, th.fg));
        String hol = Holidays.name(d);
        if (!hol.isEmpty()) box.addView(tv(hol, 14, th.sun));
        char my = ShiftCore.typeFor(o.me, d), mt = ShiftCore.typeFor(o.mate, d);
        box.addView(tv("나 " + lbl(my) + "      배우자 " + lbl(mt), 15, th.fg));

        EditText et = new EditText(this);
        et.setHint("메모 (줄바꿈으로 여러 개)");
        et.setHintTextColor(th.sub);
        et.setTextColor(th.fg);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(3);
        et.setGravity(Gravity.TOP);
        et.setText(Store.memo(d));
        box.addView(et, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final AlertDialog[] dlg = new AlertDialog[1];

        List<Store.Use> uses = Store.usesOn(d);
        if (!uses.isEmpty()) {
            box.addView(space(10));
            box.addView(tv("근태 기록", 13, th.sub));
            for (Store.Use u : uses) {
                LeaveCore.Type t = LeaveCore.type(u.key);
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(tv(t == null ? u.key : t.label, 15, th.leave), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                Button del = button("삭제", v -> {
                    Store.setMemo(d, et.getText().toString());
                    Store.removeUse(u.id);
                    toast("취소했어요. 잔여가 복원됐어요");
                    dlg[0].dismiss();
                    dataChanged();
                    showDay(d);
                });
                row.addView(del);
                box.addView(row);
            }
        }

        if (my != 'O') {
            box.addView(space(10));
            box.addView(tv("근태 사용 추가 (내 근무 " + lbl(my) + ")", 13, th.sub));
            List<LeaveCore.Type> ok = new ArrayList<>();
            for (LeaveCore.Type t : LeaveCore.TYPES) if (LeaveCore.allowed(t, my)) ok.add(t);
            for (int i = 0; i < ok.size(); i += 3) {
                LinearLayout row = new LinearLayout(this);
                for (int j = i; j < Math.min(i + 3, ok.size()); j++) {
                    final LeaveCore.Type t = ok.get(j);
                    Button bt = button(t.label, v -> {
                        for (String k : Store.useKeysOn(d)) if (k.equals(t.key)) { toast("이미 등록돼 있어요"); return; }
                        Store.setMemo(d, et.getText().toString());
                        double nb = Store.addUse(d, t);
                        if (Double.isNaN(nb)) {
                            if (t.pool != null) toast(LeaveCore.START + "년 이전 기간은 차감 없이 기록만 해요");
                        } else if (nb < 0) toast("잔여가 부족해요 (" + nb + ")");
                        dlg[0].dismiss();
                        dataChanged();
                        showDay(d);
                    });
                    row.addView(bt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                }
                box.addView(row);
            }
        }

        dlg[0] = new AlertDialog.Builder(this).setView(sv)
                .setPositiveButton("저장", (a, w) -> { Store.setMemo(d, et.getText().toString()); dataChanged(); })
                .setNegativeButton("닫기", null).create();
        dlg[0].show();
    }

    private String lbl(char t) { return t == 'O' ? "휴" : String.valueOf(t); }

    // ---------- 근태 탭 ----------
    private View buildLeave() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = vbox();
        sv.addView(box);

        LinearLayout yr = new LinearLayout(this);
        yr.setGravity(Gravity.CENTER_VERTICAL);
        TextView p = tv("‹", 24, th.fg); p.setGravity(Gravity.CENTER);
        p.setOnClickListener(v -> { leaveYear = Math.max(LeaveCore.START, leaveYear - 1); showTab(3); });
        TextView n = tv("›", 24, th.fg); n.setGravity(Gravity.CENTER);
        n.setOnClickListener(v -> { leaveYear++; showTab(3); });
        TextView yt = tv(leaveYear + "년", 18, th.fg); yt.setGravity(Gravity.CENTER);
        yr.addView(p, new LinearLayout.LayoutParams(dp(56), dp(40)));
        yr.addView(yt, new LinearLayout.LayoutParams(0, dp(40), 1));
        yr.addView(n, new LinearLayout.LayoutParams(dp(56), dp(40)));
        box.addView(yr);
        box.addView(tv("연도를 고르고 잔여를 입력해 저장하세요. 연차는 전년 12.18~금년 12.17, 그 외는 1.1~12.31 기준이며 사용한 날짜가 속한 기간에서 차감·복원됩니다. 1개=8시간(0.5=오전/오후, 0.25=2시간, 0.125=1시간)", 12, th.sub));

        for (String pool : LeaveCore.POOLS) {
            box.addView(space(14));
            box.addView(tv(LeaveCore.poolName(pool), 16, th.fg));
            box.addView(tv(LeaveCore.periodText(pool, leaveYear), 12, th.sub));
            double bal = Store.bal(pool, leaveYear), used = Store.used(pool, leaveYear);
            box.addView(tv("잔여 " + fmt(bal) + "   사용 " + fmt(used), 15, bal < 0 ? th.sun : th.fg));
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            EditText et = new EditText(this);
            et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            et.setText(fmt(bal));
            et.setTextColor(th.fg);
            row.addView(et, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(button(leaveYear + "년 잔여 저장", v -> {
                try {
                    Store.setBal(pool, leaveYear, Double.parseDouble(et.getText().toString().trim()));
                    toast("저장했어요");
                    showTab(3);
                } catch (Exception e) { toast("숫자를 입력하세요"); }
            }));
            box.addView(row);
        }

        box.addView(space(18));
        box.addView(tv(leaveYear + "년 사용 내역", 16, th.fg));
        List<Store.Use> uses = Store.allUses();
        Collections.sort(uses, (a, b) -> a.date.compareTo(b.date));
        boolean any = false;
        for (Store.Use u : uses) {
            LeaveCore.Type t = LeaveCore.type(u.key);
            if (t == null) continue;
            int py = t.pool != null ? LeaveCore.pyear(t.pool, u.date) : u.date.getYear();
            if (py != leaveYear) continue;
            any = true;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(tv(u.date + "  " + t.label, 14, th.fg), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(button("삭제", v -> { Store.removeUse(u.id); dataChanged(); }));
            box.addView(row);
        }
        if (!any) box.addView(tv("사용 내역이 없어요. 달력에서 날짜를 눌러 등록하세요.", 13, th.sub));
        return sv;
    }

    private String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    // ---------- 설정 탭 ----------
    private View buildSettings() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = vbox();
        sv.addView(box);

        box.addView(section("내 정보"));
        LinearLayout nr = new LinearLayout(this);
        nr.setGravity(Gravity.CENTER_VERTICAL);
        EditText nick = new EditText(this);
        nick.setHint("닉네임 (한글 2~5자)");
        nick.setHintTextColor(th.sub);
        nick.setTextColor(th.fg);
        nick.setText(Store.getS("nick", ""));
        nr.addView(nick, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        nr.addView(button("저장", v -> {
            String s = nick.getText().toString().trim();
            if (!s.matches("^[가-힣]{2,5}$")) { toast("한글 2~5글자만 가능해요"); return; }
            Store.putS("nick", s);
            toast("저장했어요 (중복 검사는 서버 연동 후 적용돼요)");
        }));
        box.addView(nr);

        box.addView(label("내 조"));
        box.addView(choiceRow(new String[]{"1조", "2조", "3조", "4조"}, Store.getI("grp_me", 1) - 1, i -> { Store.putI("grp_me", i + 1); dataChanged(); }));
        box.addView(label("배우자 조"));
        box.addView(choiceRow(new String[]{"1조", "2조", "3조", "4조"}, Store.getI("grp_mate", 4) - 1, i -> { Store.putI("grp_mate", i + 1); dataChanged(); }));

        box.addView(section("화면"));
        box.addView(label("테마"));
        for (int i = 0; i < Theme.NAMES.length; i += 3) {
            int end = Math.min(i + 3, Theme.NAMES.length);
            final int base = i;
            String[] part = java.util.Arrays.copyOfRange(Theme.LABELS, i, end);
            int cur = java.util.Arrays.asList(Theme.NAMES).indexOf(Store.getS("theme", "auto")) - i;
            box.addView(choiceRow(part, cur, ix -> { Store.putS("theme", Theme.NAMES[base + ix]); dataChanged(); recreate(); }));
        }
        box.addView(label("근무 표시 모양"));
        box.addView(choiceRow(new String[]{"원", "둥근 사각", "사각"}, Store.getI("shape", 0), i -> { Store.putI("shape", i); dataChanged(); }));
        box.addView(label("음력 표시"));
        box.addView(choiceRow(new String[]{"끔", "초하루·보름", "전체"}, Store.getI("lunar", 1), i -> { Store.putI("lunar", i); dataChanged(); }));
        box.addView(check("월요일부터 시작", "monstart", false));
        box.addView(check("공휴일 표시", "holiday", true));
        box.addView(check("메모 표시", "showmemo", true));
        box.addView(check("날짜 구분선", "lines", true));

        box.addView(section("크기·간격"));
        box.addView(seek("날짜 숫자 크기", "s_date", 0.6f, 1.6f, 1f));
        box.addView(seek("근무 표시 크기", "s_shift", 0.5f, 1.6f, 1f));
        box.addView(seek("배우자 근무 크기 (부부 탭)", "s_mate", 0.4f, 1.2f, 0.8f));
        box.addView(seek("메모 글자 크기", "s_memo", 0.6f, 1.6f, 1f));
        box.addView(seek("날짜와 근무 사이 간격", "s_gapds", 0f, 1f, 0.1f));
        box.addView(seek("근무와 메모 사이 간격", "s_gapsm", 0f, 1f, 0.1f));

        box.addView(section("출근 알람 (휴무·연차 종일·검진·교육은 울리지 않음)"));
        for (char t : Alarms.TYPES) box.addView(alarmRow(t));

        box.addView(section("백업"));
        LinearLayout br = new LinearLayout(this);
        br.addView(button("파일로 저장", v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "hycalendar_backup_" + LocalDate.now() + ".json");
            startActivityForResult(i, REQ_EXPORT);
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        br.addView(button("파일에서 불러오기", v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            startActivityForResult(i, REQ_IMPORT);
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(br);
        box.addView(tv("앱을 업데이트해도 메모·일정·설정은 그대로 유지되고, 버전이 바뀔 때 자동으로 내부 백업을 남깁니다.", 12, th.sub));
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        box.addView(tv("버전 " + ver, 12, th.sub));
        box.addView(space(30));
        return sv;
    }

    private View alarmRow(char t) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox cb = new CheckBox(this);
        cb.setText(t + " 근무일 알람");
        cb.setTextColor(th.fg);
        cb.setChecked(Alarms.enabled(t));
        cb.setOnCheckedChangeListener((v, on) -> { Store.putB("al_" + t + "_on", on); Alarms.schedule(this); });
        row.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int m = Alarms.minutes(t);
        Button tb = new Button(this);
        tb.setAllCaps(false);
        tb.setText(String.format("%02d:%02d", m / 60, m % 60));
        tb.setOnClickListener(v -> {
            int cur = Alarms.minutes(t);
            new TimePickerDialog(this, (view, h, mi) -> {
                Store.putI("al_" + t + "_min", h * 60 + mi);
                tb.setText(String.format("%02d:%02d", h, mi));
                Alarms.schedule(this);
            }, cur / 60, cur % 60, true).show();
        });
        row.addView(tb);
        return row;
    }

    interface IntCb { void run(int i); }

    private View choiceRow(String[] names, int cur, IntCb cb) {
        LinearLayout row = new LinearLayout(this);
        Button[] bs = new Button[names.length];
        for (int i = 0; i < names.length; i++) {
            Button bt = new Button(this);
            bt.setAllCaps(false);
            bt.setText(names[i]);
            bt.setAlpha(i == cur ? 1f : 0.45f);
            final int ix = i;
            bs[i] = bt;
            bt.setOnClickListener(v -> {
                for (int j = 0; j < bs.length; j++) bs[j].setAlpha(j == ix ? 1f : 0.45f);
                cb.run(ix);
            });
            row.addView(bt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        return row;
    }

    private View check(String s, String key, boolean def) {
        CheckBox cb = new CheckBox(this);
        cb.setText(s);
        cb.setTextColor(th.fg);
        cb.setChecked(Store.getB(key, def));
        cb.setOnCheckedChangeListener((v, on) -> { Store.putB(key, on); dataChanged(); });
        return cb;
    }

    private View seek(String s, String key, float min, float max, float def) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(label(s));
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(Math.round((Store.getF(key, def) - min) / (max - min) * 100));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar x, int p, boolean u) { if (u) Store.putF(key, min + (max - min) * p / 100f); }
            public void onStartTrackingTouch(SeekBar x) { }
            public void onStopTrackingTouch(SeekBar x) { dataChanged(); }
        });
        l.addView(sb);
        return l;
    }

    // ---------- 백업 파일 ----------
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            if (req == REQ_EXPORT) {
                try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(Store.exportJson().getBytes(StandardCharsets.UTF_8)); }
                toast("백업 파일을 저장했어요");
            } else if (req == REQ_IMPORT) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(u)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                }
                final String json = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                new AlertDialog.Builder(this).setTitle("불러오기").setMessage("현재 데이터를 이 백업으로 바꿉니다. 현재 데이터는 내부 백업으로 먼저 저장해 둡니다.")
                        .setPositiveButton("불러오기", (a, w) -> {
                            try {
                                File f = new File(getFilesDir(), "backup_before_restore_" + System.currentTimeMillis() + ".json");
                                try (FileOutputStream o = new FileOutputStream(f)) { o.write(Store.exportJson().getBytes(StandardCharsets.UTF_8)); }
                            } catch (Exception ignored) { }
                            if (Store.importJson(json)) {
                                toast("불러왔어요");
                                dataChanged();
                                recreate();
                            } else toast("올바른 백업 파일이 아니에요");
                        }).setNegativeButton("취소", null).show();
            }
        } catch (Exception e) {
            toast("파일 처리에 실패했어요");
        }
    }

    // ---------- 작은 도우미 ----------
    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(12), dp(18), dp(12));
        return l;
    }

    private TextView tv(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private TextView section(String s) {
        TextView t = tv(s, 15, th.todayStroke);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(0, dp(22), 0, dp(4));
        return t;
    }

    private TextView label(String s) {
        TextView t = tv(s, 13, th.sub);
        t.setPadding(0, dp(8), 0, dp(2));
        return t;
    }

    private View space(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(s);
        b.setOnClickListener(l);
        return b;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
