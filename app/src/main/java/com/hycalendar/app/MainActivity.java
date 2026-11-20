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
    private DayUi dayUi;
    private AlarmUi alarmUi;
    private BoardUi boardUi;
    private ScrollView settingsScroll;
    private int pendingScroll = 0;

    private int dp(int v) { return (int) (v * dp + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Store.init(this);
        dp = getResources().getDisplayMetrics().density;
        leaveYear = Math.max(LeaveCore.START, LocalDate.now().getYear());
        if (b != null) {
            tab = b.getInt("tab", 0);
            pendingScroll = b.getInt("scroll", 0);
            ym = YearMonth.of(b.getInt("ymY", ym.getYear()), b.getInt("ymM", ym.getMonthValue()));
        }
        versionBackup();
        try {
            Sync.init(this, () -> runOnUiThread(() -> {
                try { Alarms.schedule(this); } catch (Throwable ignored) { }
                WidgetBase.updateAll(this);
                if (cal != null) cal.invalidate();
                if (tab == 1 || tab == 2 || tab == 3) showTab(tab);
            }));
        } catch (Throwable ignored) { }
        buildUi();
        try { Alarms.schedule(this); } catch (Throwable ignored) { }
        WidgetBase.updateAll(this);
        try { if (Sync.loggedIn() && Store.getS("email", "").isEmpty()) Sync.signOut(); } catch (Throwable ignored) { }
        try { Sync.start(); } catch (Throwable ignored) { }
        try {
            Updater.check(this, (newer, ver, url, err) -> { if (newer && !isFinishing()) Updater.promptAndInstall(this, ver, url); });
        } catch (Throwable ignored) { }
        final Runnable askLogin = () -> {
            if (Sync.configured() && !Sync.loggedIn() && !Store.getB("login_later", false)) SyncUi.loginDialog(this, th, true, this::refreshSettings);
        };
        // 처음 설치했거나 빠진 권한이 있으면 먼저 권한 안내를 보여 주고, 끝나면 로그인 창을 띄운다.
        if (Perms.needGuide(this)) Perms.show(this, th, askLogin);
        else askLogin.run();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        Perms.refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        o.putInt("tab", tab);
        o.putInt("scroll", settingsScroll != null && tab == 5 ? settingsScroll.getScrollY() : 0);
        o.putInt("ymY", ym.getYear());
        o.putInt("ymM", ym.getMonthValue());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (boardUi != null) boardUi.stop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        WidgetBase.updateAll(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cal != null) cal.invalidate();
        Perms.refresh();
        if (tab == 3) showTab(3);
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
        Ui.init(this);
        th = Theme.get(Store.getS("theme", "auto"), Opts.systemDark(this));
        dayUi = new DayUi(this, th, this::dataChanged);
        alarmUi = new AlarmUi(this, th, () -> showTab(3));
        boardUi = new BoardUi(this, th);
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
        header.setPadding(dp(16), dp(4), dp(8), dp(4));
        title = Ui.text(this, "", 24, th.fg, true);
        title.setOnClickListener(v -> { ym = YearMonth.now(); refreshTitle(); if (cal != null) cal.invalidate(); });
        header.addView(title);
        TextView today = Ui.text(this, "오늘", 13, th.fg, false);
        today.setPadding(dp(12), dp(4), dp(12), dp(4));
        today.setBackground(Ui.stroke(0, th.fg, 14, 1.2f));
        today.setOnClickListener(v -> { ym = YearMonth.now(); refreshTitle(); if (cal != null) cal.invalidate(); });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.setMargins(dp(10), 0, 0, 0);
        header.addView(today, tp);
        View sp = new View(this);
        header.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
        TextView prev = Ui.text(this, "‹", 26, th.fg, false);
        prev.setGravity(Gravity.CENTER);
        prev.setOnClickListener(v -> moveMonth(-1));
        TextView next = Ui.text(this, "›", 26, th.fg, false);
        next.setGravity(Gravity.CENTER);
        next.setOnClickListener(v -> moveMonth(1));
        header.addView(prev, new LinearLayout.LayoutParams(dp(44), dp(40)));
        header.addView(next, new LinearLayout.LayoutParams(dp(44), dp(40)));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        View div = new View(this);
        div.setBackgroundColor(th.line);
        root.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        tabbar = new LinearLayout(this);
        tabbar.setOrientation(LinearLayout.HORIZONTAL);
        tabbar.setBackgroundColor(th.bg);
        String[] names = {"달력", "근무표", "근태", "알람", "게시판", "설정"};
        int[] icons = {0, 1, 5, 3, 6, 4};
        for (int i = 0; i < names.length; i++) {
            final int ix = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setTag("tab" + i);
            View bar = new View(this);
            bar.setTag("bar");
            cell.addView(bar, new LinearLayout.LayoutParams(dp(34), dp(3)));
            Ui.TabIcon ic = new Ui.TabIcon(this, icons[i]);
            ic.setTag("ic");
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(26), dp(26));
            ip.setMargins(0, dp(6), 0, dp(1));
            cell.addView(ic, ip);
            TextView t = Ui.text(this, names[i], 10.5f, th.sub, false);
            t.setTag("lb");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(5));
            cell.addView(t, lp);
            cell.setOnClickListener(v -> showTab(ix));
            tabbar.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        root.addView(tabbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        showTab(tab);
    }

    private void showTab(int i) {
        tab = i;
        if (boardUi != null && i != 4) boardUi.stop();
        content.removeAllViews();
        cal = null;
        for (int k = 0; k < 6; k++) {
            View cell = tabbar.findViewWithTag("tab" + k);
            if (cell == null) continue;
            boolean on = k == i;
            ((Ui.TabIcon) cell.findViewWithTag("ic")).setColor(on ? th.todayStroke : th.sub);
            ((TextView) cell.findViewWithTag("lb")).setTextColor(on ? th.todayStroke : th.sub);
            cell.findViewWithTag("bar").setBackgroundColor(on ? th.todayStroke : 0);
        }
        if (i <= 1) {
            header.setVisibility(View.VISIBLE);
            content.setBackgroundColor(th.bg);
            if (i == 0) {
                cal = new CalView(Renderer.SINGLE);
                content.addView(cal, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            } else {
                final boolean mate = hasMate();
                int sub = mate ? Store.getI("grp_sub", 0) : 1;   // 배우자가 없으면 부부 탭 없이 4개조만
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                if (mate) {
                    View seg = Ui.segmented(this, th, new String[]{"부부", "4개조"}, sub, ix -> { Store.putI("grp_sub", ix); showTab(1); });
                    LinearLayout.LayoutParams sp = (LinearLayout.LayoutParams) seg.getLayoutParams();
                    sp.setMargins(dp(14), dp(2), dp(14), dp(6));
                    col.addView(seg);
                }
                cal = new CalView(sub == 0 ? Renderer.COUPLE : Renderer.FOUR);
                col.addView(cal, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
                content.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            refreshTitle();
        } else {
            header.setVisibility(View.GONE);
            content.setBackgroundColor(th.page);
            content.addView(i == 2 ? buildLeave() : i == 3 ? buildAlarm() : i == 4 ? boardUi.build() : buildSettings());
        }
    }

    /** 사내 부부(배우자)가 연결돼 있을 때만 배우자 관련 화면을 보여 준다. */
    private boolean hasMate() { return !Sync.partner().isEmpty(); }

    private void refreshTitle() { title.setText(ym.getYear() + ". " + ym.getMonthValue()); }

    private void moveMonth(int d) {
        ym = ym.plusMonths(d);
        refreshTitle();
        if (cal != null) cal.invalidate();
    }

    private void refreshSettings() {
        if (settingsScroll != null && tab == 5) pendingScroll = settingsScroll.getScrollY();
        showTab(tab);
    }

    private void dataChanged() {
        Alarms.schedule(this);
        WidgetBase.updateAll(this);
        if (cal != null) cal.invalidate();
        if (tab == 2) showTab(2);
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
                    LocalDate d = Renderer.cellAt(getWidth(), getHeight(), ym, o, e.getX(), e.getY());
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
    private void showDay(LocalDate d) { dayUi.show(d); }

    // ---------- 근태 탭 ----------
    private int leaveWho = 0;

    private View buildLeave() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = vbox();
        sv.addView(box);

        LinearLayout top = Ui.card(this, th);
        top.setPadding(dp(14), dp(8), dp(14), dp(6));
        LinearLayout yr = new LinearLayout(this);
        yr.setGravity(Gravity.CENTER_VERTICAL);
        TextView p = Ui.text(this, "‹", 24, th.fg, false); p.setGravity(Gravity.CENTER);
        p.setOnClickListener(v -> { leaveYear = Math.max(LeaveCore.START, leaveYear - 1); showTab(2); });
        TextView n = Ui.text(this, "›", 24, th.fg, false); n.setGravity(Gravity.CENTER);
        n.setOnClickListener(v -> { leaveYear++; showTab(2); });
        TextView yt = Ui.text(this, leaveYear + "년", 18, th.fg, true); yt.setGravity(Gravity.CENTER);
        yr.addView(p, new LinearLayout.LayoutParams(dp(48), dp(32)));
        yr.addView(yt, new LinearLayout.LayoutParams(0, dp(32), 1));
        yr.addView(n, new LinearLayout.LayoutParams(dp(48), dp(32)));
        top.addView(yr);
        if (hasMate()) top.addView(Ui.segmented(this, th, new String[]{"나", "배우자"}, leaveWho, i -> { leaveWho = i; showTab(2); }));
        else leaveWho = 0;
        box.addView(top);

        final int who = leaveWho;
        LinearLayout pc = Ui.card(this, th);
        pc.setPadding(dp(14), dp(4), dp(14), dp(4));
        for (String pool : LeaveCore.POOLS) {
            double grant = Store.bal(pool, leaveYear, who), used = Store.used(pool, leaveYear, who), rem = grant - used;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(7), 0, dp(7));
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            LinearLayout nm = new LinearLayout(this);
            nm.setGravity(Gravity.CENTER_VERTICAL);
            nm.addView(Ui.text(this, LeaveCore.poolName(pool), 15, th.fg, true));
            TextView bl = Ui.text(this, "  잔여 " + fmt(rem), 14, rem < 0 ? th.sun : th.todayStroke, true);
            nm.addView(bl);
            left.addView(nm);
            left.addView(Ui.text(this, "발생 " + fmt(grant) + " − 사용 " + fmt(used), 12, th.fg, false));
            left.addView(Ui.text(this, LeaveCore.periodText(pool, leaveYear).replace("20", "").replace(" ~ ", "~"), 11, th.sub, false));
            row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            EditText et = new EditText(this);
            et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            et.setText(fmt(grant));
            et.setTextSize(14);
            et.setTextColor(th.fg);
            et.setBackground(Ui.round(th.soft, 8));
            et.setPadding(dp(8), dp(6), dp(8), dp(6));
            et.setGravity(Gravity.END);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT);
            ep.setMargins(0, 0, dp(6), 0);
            row.addView(Ui.text(this, "발생", 12, th.sub, false), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(et, ep);
            TextView sb = Ui.btn(this, th, "저장", true, v -> {
                try {
                    Store.setBal(pool, leaveYear, who, Double.parseDouble(et.getText().toString().trim()));
                    toast(leaveYear + "년 발생 개수를 저장했어요");
                    showTab(2);
                } catch (Exception e) { toast("숫자를 입력하세요"); }
            });
            sb.setTextSize(13);
            sb.setPadding(dp(12), dp(7), dp(12), dp(7));
            row.addView(sb);
            pc.addView(row);
            View dv = new View(this);
            dv.setBackgroundColor(th.line);
            pc.addView(dv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        box.addView(pc);
        TextView note = Ui.text(this, "입력칸은 그해 '발생' 개수(고정)이고, 잔여 = 발생 − 사용으로 자동 계산돼요.\n연차 기간은 전년 12.18~금년 12.17, 그 외 1.1~12.31 기준 · 1개=8시간 (0.5 오전/오후, 0.25 2시간, 0.125 1시간)", 11, th.sub, false);
        note.setPadding(dp(6), 0, dp(6), dp(8));
        box.addView(note);

        box.addView(Ui.sectionTitle(this, th, leaveYear + "년 사용 내역"));
        LinearLayout hc = Ui.card(this, th);
        hc.setPadding(dp(14), dp(6), dp(14), dp(6));
        List<Store.Use> uses = Store.allUses();
        Collections.sort(uses, (a2, b2) -> a2.date.compareTo(b2.date));
        boolean any = false;
        for (Store.Use u : uses) {
            if (u.who != who) continue;
            LeaveCore.Type t = LeaveCore.type(u.key);
            if (t == null) continue;
            int py = t.pool != null ? LeaveCore.pyear(t.pool, u.date) : u.date.getYear();
            if (py != leaveYear) continue;
            any = true;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(Ui.text(this, u.date.getMonthValue() + "/" + u.date.getDayOfMonth() + "  " + t.label, 13, th.fg, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView del = Ui.text(this, "삭제", 12, th.sub, false);
            del.setPadding(dp(10), dp(6), dp(10), dp(6));
            del.setOnClickListener(v -> { Store.removeUse(u.id); dataChanged(); });
            row.addView(del);
            hc.addView(row);
        }
        if (!any) hc.addView(Ui.text(this, "사용 내역이 없어요. 달력에서 날짜를 눌러 등록하세요.", 12, th.sub, false));
        box.addView(hc);
        return sv;
    }

    // ---------- 알람 탭 ----------
    private View buildAlarm() { return alarmUi.build(); }

    private String fmt(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    // ---------- 설정 탭 ----------
    private View buildSettings() {
        ScrollView sv = new ScrollView(this);
        settingsScroll = sv;
        if (pendingScroll > 0) { final int ps = pendingScroll; pendingScroll = 0; sv.post(() -> sv.scrollTo(0, ps)); }
        LinearLayout box = vbox();
        sv.addView(box);

        box.addView(Ui.sectionTitle(this, th, "근무조"));
        LinearLayout g = Ui.card(this, th);
        g.addView(Ui.text(this, "나", 13, th.sub, false));
        g.addView(Ui.segmented(this, th, new String[]{"1조", "2조", "3조", "4조", "통상"}, Store.getI("grp_me", 1) - 1, i -> { Store.putI("grp_me", i + 1); Sync.pushProfile(); dataChanged(); }));
        if (hasMate()) {
            g.addView(Ui.text(this, "배우자", 13, th.sub, false));
            g.addView(Ui.segmented(this, th, new String[]{"1조", "2조", "3조", "4조", "통상"}, Store.getI("grp_mate", 4) - 1, i -> { Store.putI("grp_mate", i + 1); dataChanged(); }));
        }
        g.addView(Ui.text(this, "나 줄 배경색", 13, th.sub, false));
        g.addView(Ui.swatches(this, th, Store.getI("c_me", 0), i -> { Store.putI("c_me", i); dataChanged(); }));
        if (hasMate()) {
            g.addView(Ui.text(this, "배우자 줄 배경색", 13, th.sub, false));
            g.addView(Ui.swatches(this, th, Store.getI("c_mate", 1), i -> { Store.putI("c_mate", i); dataChanged(); }));
        }
        box.addView(g);

        box.addView(Ui.sectionTitle(this, th, "사내 부부 / 가족 연결"));
        box.addView(SyncUi.card(this, th, this::refreshSettings));

        box.addView(Ui.sectionTitle(this, th, "앱 업데이트"));
        LinearLayout uc = Ui.card(this, th);
        uc.addView(Ui.text(this, "현재 버전 v" + Updater.currentName(this), 15, th.fg, true));
        uc.addView(Ui.btn(this, th, "업데이트 확인", false, v -> {
            toast("확인 중…");
            Updater.check(this, (newer, ver, url, err) -> {
                if (err != null) toast(err);
                else if (newer) Updater.promptAndInstall(this, ver, url);
                else toast("최신 버전이에요");
            });
        }));
        box.addView(uc);

        box.addView(Ui.sectionTitle(this, th, "보기"));
        LinearLayout vc = Ui.card(this, th);
        vc.addView(Ui.toggle(this, th, "공휴일 표시", Store.getB("holiday", true), on -> { Store.putB("holiday", on); dataChanged(); }));
        vc.addView(Ui.toggle(this, th, "메모 표시", Store.getB("showmemo", true), on -> { Store.putB("showmemo", on); dataChanged(); }));
        vc.addView(Ui.toggle(this, th, "날짜 구분선", Store.getB("lines", true), on -> { Store.putB("lines", on); dataChanged(); }));
        vc.addView(Ui.toggle(this, th, "월요일부터 시작", Store.getB("monstart", false), on -> { Store.putB("monstart", on); dataChanged(); }));
        box.addView(vc);

        box.addView(Ui.sectionTitle(this, th, "테마"));
        LinearLayout tc = Ui.card(this, th);
        for (int i = 0; i < Theme.NAMES.length; i += 3) {
            int end = Math.min(i + 3, Theme.NAMES.length);
            final int base = i;
            String[] part = java.util.Arrays.copyOfRange(Theme.LABELS, i, end);
            int cur = java.util.Arrays.asList(Theme.NAMES).indexOf(Store.getS("theme", "auto")) - i;
            tc.addView(Ui.segmented(this, th, part, cur, ix -> { Store.putS("theme", Theme.NAMES[base + ix]); dataChanged(); recreate(); }));
        }
        box.addView(tc);

        box.addView(Ui.sectionTitle(this, th, "화면 설정"));
        LinearLayout sc = Ui.card(this, th);
        sc.addView(Ui.text(this, "근무 모양", 13, th.sub, false));
        sc.addView(Ui.segmented(this, th, new String[]{"원", "둥근 사각", "사각"}, Store.getI("shape", 0), i -> { Store.putI("shape", i); dataChanged(); }));
        sc.addView(Ui.text(this, "음력 표시", 13, th.sub, false));
        sc.addView(Ui.segmented(this, th, new String[]{"안 함", "초하루·보름", "매일"}, Store.getI("lunar", 1), i -> { Store.putI("lunar", i); dataChanged(); }));
        sc.addView(slider("날짜 글씨 크기", "s_date", 0.6f, 1.6f, 1f));
        sc.addView(slider("근무 표시 크기", "s_shift", 0.5f, 1.6f, 1f));
                sc.addView(slider("메모 글씨 크기", "s_memo", 0.6f, 1.6f, 1f));
        sc.addView(slider("날짜 ↔ 근무 간격", "s_gapds", 0f, 1f, 0.1f));
        sc.addView(slider("근무 ↔ 메모 간격", "s_gapsm", 0f, 1f, 0.1f));
        box.addView(sc);

        box.addView(Ui.sectionTitle(this, th, "백업·복원"));
        LinearLayout bc = Ui.card(this, th);
        LinearLayout br = new LinearLayout(this);
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        b1.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        b2.setMargins(dp(6), 0, 0, 0);
        br.addView(Ui.btn(this, th, "서버에 백업하기", false, v -> {
            if (!Sync.loggedIn()) { toast("먼저 로그인하세요"); return; }
            toast("서버에 올리는 중…");
            Sync.backupToServer(Store.exportJson(), e -> runOnUiThread(() -> {
                if (e != null) { toast(e); return; }
                Store.putS("srv_bk", new java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.KOREA).format(new java.util.Date()));
                toast("서버에 백업했어요");
                showTab(5);
            }));
        }), b1);
        br.addView(Ui.btn(this, th, "서버에서 복원하기", false, v -> {
            if (!Sync.loggedIn()) { toast("먼저 로그인하세요"); return; }
            toast("서버에서 가져오는 중…");
            Sync.restoreFromServer((json, ts, err) -> runOnUiThread(() -> {
                if (err != null) { toast(err); return; }
                String when = new java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.KOREA).format(new java.util.Date(ts));
                new AlertDialog.Builder(this).setTitle("서버에서 복원").setMessage(when + "에 올린 백업으로 바꿉니다. 현재 데이터는 내부 백업으로 먼저 저장해 둡니다.")
                        .setPositiveButton("복원", (d, w) -> {
                            try {
                                File f = new File(getFilesDir(), "backup_before_restore_" + System.currentTimeMillis() + ".json");
                                try (FileOutputStream o = new FileOutputStream(f)) { o.write(Store.exportJson().getBytes(StandardCharsets.UTF_8)); }
                            } catch (Exception ignored) { }
                            if (Store.importJson(json)) {
                                toast("복원했어요");
                                dataChanged();
                                recreate();
                            } else toast("올바른 백업이 아니에요");
                        }).setNegativeButton("취소", null).show();
            }));
        }), b2);
        bc.addView(br);
        TextView note = Ui.text(this, "마지막 서버 백업: " + Store.getS("srv_bk", "없음") + "\n백업은 로그인한 계정에만 저장되고 나만 볼 수 있어요. 폰을 바꾸면 같은 계정으로 로그인한 뒤 '서버에서 복원하기'를 누르세요.\n앱을 업데이트해도 메모·일정·설정은 그대로 유지되고, 버전이 바뀔 때 자동으로 내부 백업을 남깁니다.", 12, th.sub, false);
        note.setPadding(0, dp(8), 0, 0);
        bc.addView(note);
        box.addView(bc);

        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        TextView vt = Ui.text(this, "앱 버전 " + ver, 12, th.sub, false);
        vt.setPadding(dp(6), dp(4), 0, dp(30));
        box.addView(vt);
        return sv;
    }

    private View slider(String s, String key, float min, float max, float def) {
        return Ui.slider(this, th, s, min, max, Store.getF(key, def), v -> { Store.putF(key, v); });
    }

    // ---------- 백업 파일 ----------
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == BoardUi.REQ_PHOTO) { if (res == RESULT_OK && boardUi != null) boardUi.onPhotos(data); return; }
        if (req == AlarmUi.REQ_SOUND) { if (res == RESULT_OK && alarmUi != null) alarmUi.onSound(data); return; }
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
        l.setPadding(dp(14), dp(10), dp(14), dp(10));
        return l;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
