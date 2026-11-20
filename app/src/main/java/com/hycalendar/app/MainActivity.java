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
        try { Alarms.schedule(this); } catch (Throwable ignored) { }
        WidgetBase.updateAll(this);
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
        String[] names = {"달력", "근무표", "근태", "알람", "설정"};
        int[] icons = {0, 1, 5, 3, 4};
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
        content.removeAllViews();
        cal = null;
        for (int k = 0; k < 5; k++) {
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
                int sub = Store.getI("grp_sub", 0);
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                View seg = Ui.segmented(this, th, new String[]{"부부", "4개조"}, sub, ix -> { Store.putI("grp_sub", ix); showTab(1); });
                LinearLayout.LayoutParams sp = (LinearLayout.LayoutParams) seg.getLayoutParams();
                sp.setMargins(dp(14), dp(2), dp(14), dp(6));
                col.addView(seg);
                cal = new CalView(sub == 0 ? Renderer.COUPLE : Renderer.FOUR);
                col.addView(cal, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
                content.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            refreshTitle();
        } else {
            header.setVisibility(View.GONE);
            content.setBackgroundColor(th.page);
            content.addView(i == 2 ? buildLeave() : i == 3 ? buildAlarm() : buildSettings());
        }
    }

    private void refreshTitle() { title.setText(ym.getYear() + ". " + ym.getMonthValue()); }

    private void moveMonth(int d) {
        ym = ym.plusMonths(d);
        refreshTitle();
        if (cal != null) cal.invalidate();
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
    private void showDay(LocalDate d) {
        Opts o = Opts.load(this, "", Renderer.COUPLE);
        final android.app.Dialog dlg = new android.app.Dialog(this);
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(18), dp(20), dp(16));
        box.setBackground(Ui.round(th.card, 20));
        sv.addView(box);
        dlg.setContentView(sv);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dlg.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        Lunar.Res lr = Lunar.of(d);
        int dowIdx = d.getDayOfWeek().getValue() % 7;
        TextView head = Ui.text(this, d.getMonthValue() + "월 " + d.getDayOfMonth() + "일 " + DOWS[dowIdx] + "요일", 20, dowIdx == 0 ? th.sun : dowIdx == 6 ? th.sat : th.fg, true);
        box.addView(head);
        String hol = Holidays.name(d);
        box.addView(Ui.text(this, "음력 " + (lr.leap ? "윤" : "") + lr.m + "." + lr.d + (hol.isEmpty() ? "" : "  ·  " + hol), 13, hol.isEmpty() ? th.sub : th.sun, false));

        char my = ShiftCore.typeFor(o.me, d), mt = ShiftCore.typeFor(o.mate, d);
        LinearLayout sr = new LinearLayout(this);
        sr.setPadding(0, dp(10), 0, dp(6));
        sr.addView(shiftPill("나", my));
        View gap = new View(this);
        sr.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));
        sr.addView(shiftPill("배우자", mt));
        box.addView(sr);

        EditText et = new EditText(this);
        et.setHint("메모 (줄바꿈으로 여러 개)");
        et.setHintTextColor(th.sub);
        et.setTextColor(th.fg);
        et.setTextSize(15);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(3);
        et.setGravity(Gravity.TOP);
        et.setBackground(Ui.round(th.soft, 12));
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        et.setText(Store.memo(d));
        box.addView(et, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int who = 0; who < 2; who++) {
            final int wf = who;
            String wn = who == 0 ? "나" : "배우자";
            char sh = who == 0 ? my : mt;
            List<Store.Use> uses = Store.usesOn(d, who);
            if (!uses.isEmpty()) {
                LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ml.setMargins(0, dp(12), 0, dp(2));
                box.addView(Ui.text(this, wn + " 근태 기록", 12, th.sub, true), ml);
                for (Store.Use u : uses) {
                    LeaveCore.Type t = LeaveCore.type(u.key);
                    LinearLayout row = new LinearLayout(this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.addView(Ui.text(this, t == null ? u.key : t.label, 15, th.orange, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    row.addView(Ui.btn(this, th, "삭제", false, v -> {
                        Store.setMemo(d, et.getText().toString());
                        Store.removeUse(u.id);
                        toast("취소했어요. 잔여가 복원됐어요");
                        dlg.dismiss();
                        dataChanged();
                        showDay(d);
                    }));
                    box.addView(row);
                }
            }
            if (sh != 'O') {
                LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ml.setMargins(0, dp(12), 0, dp(4));
                box.addView(Ui.text(this, wn + " 근태 추가 (" + lbl(sh) + " 근무)", 12, th.sub, true), ml);
                List<LeaveCore.Type> ok = new ArrayList<>();
                for (LeaveCore.Type t : LeaveCore.TYPES) if (LeaveCore.allowed(t, sh)) ok.add(t);
                for (int i = 0; i < ok.size(); i += 4) {
                    LinearLayout row = new LinearLayout(this);
                    for (int j = i; j < i + 4; j++) {
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                        if (j >= ok.size()) { row.addView(new View(this), lp); continue; }
                        final LeaveCore.Type t = ok.get(j);
                        TextView bt = Ui.btn(this, th, t.label, false, v -> {
                            for (String k : Store.useKeysOn(d, wf)) if (k.equals(t.key)) { toast("이미 등록돼 있어요"); return; }
                            Store.setMemo(d, et.getText().toString());
                            double nb = Store.addUse(d, t, wf);
                            if (Double.isNaN(nb)) {
                                if (t.pool != null) toast(LeaveCore.START + "년 이전 기간은 차감 없이 기록만 해요");
                            } else if (nb < 0) toast("잔여가 부족해요 (" + nb + ")");
                            dlg.dismiss();
                            dataChanged();
                            showDay(d);
                        });
                        bt.setTextSize(12);
                        bt.setPadding(dp(2), dp(9), dp(2), dp(9));
                        row.addView(bt, lp);
                    }
                    box.addView(row);
                }
            }
        }

        LinearLayout act = new LinearLayout(this);
        act.setPadding(0, dp(16), 0, 0);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        l1.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        l2.setMargins(dp(6), 0, 0, 0);
        act.addView(Ui.btn(this, th, "닫기", false, v -> dlg.dismiss()), l1);
        act.addView(Ui.btn(this, th, "저장", true, v -> { Store.setMemo(d, et.getText().toString()); dlg.dismiss(); dataChanged(); }), l2);
        box.addView(act);
        dlg.show();
    }

    private View shiftPill(String who, char t) {
        TextView v = Ui.text(this, who + "  " + lbl(t), 14, t == 'O' ? th.offText : Theme.onColor(th.shiftColor(t)), true);
        v.setPadding(dp(14), dp(6), dp(14), dp(6));
        v.setBackground(Ui.round(t == 'O' ? th.soft : th.shiftColor(t), 16));
        return v;
    }

    private String lbl(char t) { return t == 'O' ? "휴" : String.valueOf(t); }

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
        top.addView(Ui.segmented(this, th, new String[]{"나", "배우자"}, leaveWho, i -> { leaveWho = i; showTab(2); }));
        box.addView(top);

        final int who = leaveWho;
        LinearLayout pc = Ui.card(this, th);
        pc.setPadding(dp(14), dp(4), dp(14), dp(4));
        for (String pool : LeaveCore.POOLS) {
            double bal = Store.bal(pool, leaveYear, who), used = Store.used(pool, leaveYear, who);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(7), 0, dp(7));
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            LinearLayout nm = new LinearLayout(this);
            nm.setGravity(Gravity.CENTER_VERTICAL);
            nm.addView(Ui.text(this, LeaveCore.poolName(pool), 15, th.fg, true));
            TextView bl = Ui.text(this, "  잔여 " + fmt(bal), 14, bal < 0 ? th.sun : th.todayStroke, true);
            nm.addView(bl);
            left.addView(nm);
            left.addView(Ui.text(this, LeaveCore.periodText(pool, leaveYear).replace("20", "").replace(" ~ ", "~") + " · 사용 " + fmt(used), 11, th.sub, false));
            row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            EditText et = new EditText(this);
            et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            et.setText(fmt(bal));
            et.setTextSize(14);
            et.setTextColor(th.fg);
            et.setBackground(Ui.round(th.soft, 8));
            et.setPadding(dp(8), dp(6), dp(8), dp(6));
            et.setGravity(Gravity.END);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dp(70), ViewGroup.LayoutParams.WRAP_CONTENT);
            ep.setMargins(0, 0, dp(6), 0);
            row.addView(et, ep);
            TextView sb = Ui.btn(this, th, "저장", true, v -> {
                try {
                    Store.setBal(pool, leaveYear, who, Double.parseDouble(et.getText().toString().trim()));
                    toast(leaveYear + "년 잔여를 저장했어요");
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
        TextView note = Ui.text(this, "연차 기간은 전년 12.18~금년 12.17, 그 외 1.1~12.31 기준 · 1개=8시간 (0.5 오전/오후, 0.25 2시간, 0.125 1시간)", 11, th.sub, false);
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
    private View buildAlarm() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = vbox();
        sv.addView(box);
        box.addView(Ui.sectionTitle(this, th, "출근 알람"));
        LinearLayout ac = Ui.card(this, th);
        for (char t : Alarms.TYPES) ac.addView(alarmRow(t));
        TextView nt = Ui.text(this, "내 근무일에만 울립니다. 휴무, 연차·연중·케어 종일, 검진·교육·장기근속인 날은 울리지 않습니다.", 11, th.sub, false);
        nt.setPadding(0, dp(6), 0, 0);
        ac.addView(nt);
        box.addView(ac);

        box.addView(Ui.sectionTitle(this, th, "다음 알람"));
        LinearLayout uc = Ui.card(this, th);
        List<String> up = Alarms.upcoming(this, 6);
        if (up.isEmpty()) uc.addView(Ui.text(this, "예정된 알람이 없어요. 위에서 알람을 켜 주세요.", 13, th.sub, false));
        for (String s2 : up) {
            TextView t = Ui.text(this, s2, 14, th.fg, false);
            t.setPadding(0, dp(4), 0, dp(4));
            uc.addView(t);
        }
        box.addView(uc);
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

        box.addView(Ui.sectionTitle(this, th, "근무조"));
        LinearLayout g = Ui.card(this, th);
        g.addView(Ui.text(this, "나", 13, th.sub, false));
        g.addView(Ui.segmented(this, th, new String[]{"1조", "2조", "3조", "4조"}, Store.getI("grp_me", 1) - 1, i -> { Store.putI("grp_me", i + 1); dataChanged(); }));
        g.addView(Ui.text(this, "배우자", 13, th.sub, false));
        g.addView(Ui.segmented(this, th, new String[]{"1조", "2조", "3조", "4조"}, Store.getI("grp_mate", 4) - 1, i -> { Store.putI("grp_mate", i + 1); dataChanged(); }));
        g.addView(Ui.text(this, "나 줄 배경색", 13, th.sub, false));
        g.addView(Ui.swatches(this, th, Store.getI("c_me", 0), i -> { Store.putI("c_me", i); dataChanged(); }));
        g.addView(Ui.text(this, "배우자 줄 배경색", 13, th.sub, false));
        g.addView(Ui.swatches(this, th, Store.getI("c_mate", 1), i -> { Store.putI("c_mate", i); dataChanged(); }));
        box.addView(g);

        box.addView(Ui.sectionTitle(this, th, "내 정보"));
        LinearLayout nc = Ui.card(this, th);
        LinearLayout nr = new LinearLayout(this);
        nr.setGravity(Gravity.CENTER_VERTICAL);
        EditText nick = new EditText(this);
        nick.setHint("닉네임 (한글 2~5자)");
        nick.setHintTextColor(th.sub);
        nick.setTextColor(th.fg);
        nick.setBackground(Ui.round(th.soft, 10));
        nick.setPadding(dp(12), dp(8), dp(12), dp(8));
        nick.setText(Store.getS("nick", ""));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        np.setMargins(0, 0, dp(8), 0);
        nr.addView(nick, np);
        nr.addView(Ui.btn(this, th, "변경", false, v -> {
            String s = nick.getText().toString().trim();
            if (!s.matches("^[가-힣]{2,5}$")) { toast("한글 2~5글자만 가능해요"); return; }
            Store.putS("nick", s);
            toast("저장했어요 (중복 검사는 서버 연동 후 적용돼요)");
        }));
        nc.addView(nr);
        box.addView(nc);

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
        br.addView(Ui.btn(this, th, "파일로 내보내기", false, v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "hycalendar_backup_" + LocalDate.now() + ".json");
            startActivityForResult(i, REQ_EXPORT);
        }), b1);
        br.addView(Ui.btn(this, th, "파일에서 불러오기", false, v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            startActivityForResult(i, REQ_IMPORT);
        }), b2);
        bc.addView(br);
        TextView note = Ui.text(this, "앱을 업데이트해도 메모·일정·설정은 그대로 유지되고, 버전이 바뀔 때 자동으로 내부 백업을 남깁니다.", 12, th.sub, false);
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

    private View alarmRow(char t) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.toggle(this, th, t + " 근무일 알람", Alarms.enabled(t), on -> { Store.putB("al_" + t + "_on", on); Alarms.schedule(this); }),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int m = Alarms.minutes(t);
        TextView[] tb = new TextView[1];
        tb[0] = Ui.btn(this, th, String.format("%02d:%02d", m / 60, m % 60), false, v -> {
            int cur = Alarms.minutes(t);
            new TimePickerDialog(this, (view, h, mi) -> {
                Store.putI("al_" + t + "_min", h * 60 + mi);
                tb[0].setText(String.format("%02d:%02d", h, mi));
                Alarms.schedule(this);
            }, cur / 60, cur % 60, true).show();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(12), 0, 0, 0);
        row.addView(tb[0], lp);
        return row;
    }

    private View slider(String s, String key, float min, float max, float def) {
        return Ui.slider(this, th, s, min, max, Store.getF(key, def), v -> { Store.putF(key, v); });
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
        l.setPadding(dp(14), dp(10), dp(14), dp(10));
        return l;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
