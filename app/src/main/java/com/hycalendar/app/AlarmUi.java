package com.hycalendar.app;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.List;

/** 알람 탭 + 알람 설정 화면 */
final class AlarmUi {
    static final int REQ_SOUND = 13;
    private static final String[] DOWS = {"일", "월", "화", "수", "목", "금", "토"};

    private final MainActivity act;
    private final Theme th;
    private final Runnable refresh;
    private Alarms.Item ed;
    private TextView soundLbl;

    AlarmUi(MainActivity a, Theme t, Runnable r) { act = a; th = t; refresh = r; }

    private int dp(float v) { return Ui.dp(v); }

    static String clock(int min) {
        int h = min / 60, m = min % 60;
        if (!Store.getB("alarm12h", true)) return String.format("%02d:%02d", h, m);
        return (h < 12 ? "오전 " : "오후 ") + (h % 12 == 0 ? 12 : h % 12) + ":" + String.format("%02d", m);
    }

    private NumberPicker wheel(int lo, int hi, int cur, String[] labels, boolean pad) {
        NumberPicker np = new NumberPicker(act);
        np.setMinValue(lo);
        np.setMaxValue(hi);
        if (labels != null) np.setDisplayedValues(labels);
        else if (pad) np.setFormatter(v -> String.format("%02d", v));
        np.setValue(cur);
        np.setWrapSelectorWheel(labels == null);
        np.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= 29) { np.setTextColor(th.fg); np.setTextSize(Ui.dp(26)); }
        return np;
    }

    /** 알람 시간: 오전/오후·시·분을 위아래로 돌려 고르는 휠 + 12시간제 스위치 */
    private void fillTime(final LinearLayout tc, final Alarms.Item it) {
        tc.removeAllViews();
        final boolean h12 = Store.getB("alarm12h", true);
        final int h0 = it.min / 60, m0 = it.min % 60;
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final NumberPicker ap = h12 ? wheel(0, 1, h0 >= 12 ? 1 : 0, new String[]{"오전", "오후"}, false) : null;
        final NumberPicker hp = h12 ? wheel(1, 12, h0 % 12 == 0 ? 12 : h0 % 12, null, false) : wheel(0, 23, h0, null, true);
        final NumberPicker mp = wheel(0, 59, m0, null, true);
        final Runnable apply = () -> {
            int h = hp.getValue();
            if (h12) h = (h % 12) + (ap.getValue() == 1 ? 12 : 0);
            it.min = h * 60 + mp.getValue();
        };
        NumberPicker.OnValueChangeListener l = (pk, o, n) -> apply.run();
        if (h12) { ap.setOnValueChangedListener(l); row.addView(ap, new LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT)); }
        hp.setOnValueChangedListener(l);
        mp.setOnValueChangedListener(l);
        row.addView(hp, new LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(Ui.text(act, ":", 26, th.fg, true));
        row.addView(mp, new LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT));
        tc.addView(row);
        tc.addView(Ui.toggle(act, th, "12시간제", h12, on -> {
            Store.putB("alarm12h", on);
            tc.post(() -> fillTime(tc, it));
        }));
    }

    private TextView circle(char t, int sizeDp) {
        int fill; String s; int tc;
        if (t == 'O') { fill = th.soft; s = "휴"; tc = th.offText; }
        else if (t == 'L') { fill = th.orange; s = "휴가"; tc = Color.WHITE; }
        else if (t == 'N') { fill = th.soft; s = "통상"; tc = th.fg; }
        else { fill = th.shiftColor(t); s = String.valueOf(t); tc = Theme.onColor(fill); }
        TextView v = Ui.text(act, s, sizeDp * (s.length() > 1 ? 0.3f : 0.42f), tc, true);
        v.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(fill);
        v.setBackground(g);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    View build() {
        ScrollView sv = new ScrollView(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(10), dp(14), dp(24));
        sv.addView(box);

        // 다음 알람
        Alarms.Next nx = Alarms.next(act);
        LinearLayout top = new LinearLayout(act);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(6), dp(6), dp(12));
        LinearLayout left = new LinearLayout(act);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(Ui.text(act, "다음 알람", 13, th.sub, true));
        TextView big = Ui.text(act, nx == null ? "예정된 알람이 없어요" : untilText(nx.at), 22, th.fg, true);
        left.addView(big);
        top.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (nx != null) top.addView(circle(Memos.cat(nx.day), 52));
        box.addView(top);

        // 배터리 최적화 안내
        try {
            PowerManager pm = (PowerManager) act.getSystemService(android.content.Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(act.getPackageName())) {
                LinearLayout bn = new LinearLayout(act);
                bn.setGravity(Gravity.CENTER_VERTICAL);
                bn.setPadding(dp(14), dp(10), dp(14), dp(10));
                bn.setBackground(Ui.round(th.soft, 14));
                bn.addView(Ui.text(act, "배터리 최적화를 끄면 알람이 더 정확하게 울려요", 13, th.fg, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                bn.addView(Ui.btn(act, th, "설정", true, v -> {
                    try { act.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
                    catch (Exception e) { toast("설정을 열 수 없어요"); }
                }));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bp.setMargins(0, 0, 0, dp(12));
                box.addView(bn, bp);
            }
        } catch (Throwable ignored) { }

        List<Alarms.Item> list = Alarms.items();
        long now = System.currentTimeMillis();
        LocalDate today = LocalDate.now();
        if (list.isEmpty()) {
            TextView e = Ui.text(act, "알람이 없어요. 아래 + 버튼으로 추가하세요.", 14, th.sub, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(30), 0, dp(30));
            box.addView(e);
        }
        for (Alarms.Item it : list) {
            LinearLayout card = Ui.card(act, th);
            card.setAlpha(it.on ? 1f : 0.55f);
            LinearLayout r1 = new LinearLayout(act);
            r1.setGravity(Gravity.CENTER_VERTICAL);
            r1.addView(Ui.text(act, clock(it.min), 30, th.fg, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            r1.addView(Ui.switchView(act, th, it.on, on -> {
                it.on = on;
                Alarms.save(it);
                Alarms.schedule(act);
                refresh.run();
            }));
            card.addView(r1);
            card.addView(Ui.text(act, Alarms.title(it) + (it.dayOff == -1 ? "  ·  전날" : it.dayOff == 1 ? "  ·  다음날" : ""), 14, th.fg, false));
            LinearLayout r2 = new LinearLayout(act);
            r2.setGravity(Gravity.CENTER_VERTICAL);
            r2.setPadding(0, dp(8), 0, 0);
            for (char ch : it.shifts.toCharArray()) {
                View c = circle(ch, 26);
                LinearLayout.LayoutParams cp = (LinearLayout.LayoutParams) c.getLayoutParams();
                cp.setMargins(0, 0, dp(6), 0);
                r2.addView(c);
            }
            Alarms.Next n = it.on ? Alarms.nextOf(it, today, now) : null;
            TextView nt = Ui.text(act, n == null ? (it.on ? "예정 없음" : "꺼짐") : "다음 " + n.day.getMonthValue() + "/" + n.day.getDayOfMonth() + "(" + DOWS[n.day.getDayOfWeek().getValue() % 7] + ")", 12, th.sub, false);
            nt.setGravity(Gravity.END);
            r2.addView(nt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            card.addView(r2);
            card.setOnClickListener(v -> openEditor(it, false));
            box.addView(card);
        }

        LinearLayout act2 = new LinearLayout(act);
        act2.setPadding(0, dp(6), 0, 0);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        l1.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        l2.setMargins(dp(6), 0, 0, 0);
        act2.addView(Ui.btn(act, th, "전체 끄기", false, v -> { Alarms.setAll(false); Alarms.schedule(act); refresh.run(); }), l1);
        act2.addView(Ui.btn(act, th, "+ 알람 추가", true, v -> openEditor(Alarms.create(), true)), l2);
        box.addView(act2);
        TextView nt = Ui.text(act, "알람은 내 근무 기준입니다. 알람 소리 크기는 휴대폰의 알람 볼륨을 따라가요.", 11, th.sub, false);
        nt.setPadding(dp(6), dp(10), dp(6), 0);
        box.addView(nt);
        return sv;
    }

    private String untilText(long at) {
        long min = Math.max(0, (at - System.currentTimeMillis()) / 60000L);
        long d = min / 1440, h = (min % 1440) / 60, m = min % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("일 ");
        if (h > 0) sb.append(h).append("시간 ");
        if (m > 0 || sb.length() == 0) sb.append(m).append("분 ");
        return sb + "후에 울려요";
    }

    // ---------- 알람 설정 화면 ----------
    private void openEditor(Alarms.Item src, boolean isNew) {
        final Alarms.Item it = copy(src);
        ed = it;
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(th.page);

        final Dialog dlg = Ui.fullDialog(act, th, root);

        LinearLayout bar = new LinearLayout(act);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        TextView close = Ui.text(act, "✕", 22, th.fg, false);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dlg.dismiss());
        bar.addView(close, new LinearLayout.LayoutParams(dp(48), dp(44)));
        TextView tt = Ui.text(act, isNew ? "알람 추가" : "알람 설정", 18, th.fg, true);
        tt.setGravity(Gravity.CENTER);
        bar.addView(tt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(Ui.btn(act, th, "저장", true, v -> {
            if (it.shifts.isEmpty() && it.dow == 0) { toast("근무 또는 요일을 하나 이상 골라 주세요"); return; }
            it.on = true;
            Alarms.save(it);
            Alarms.schedule(act);
            dlg.dismiss();
            refresh.run();
        }));
        root.addView(bar);

        ScrollView sv = new ScrollView(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(4), dp(14), dp(24));
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // 시간
        LinearLayout tc = Ui.card(act, th);
        fillTime(tc, it);
        box.addView(tc);

        // 반복
        LinearLayout rc = Ui.card(act, th);
        rc.addView(Ui.text(act, "근무에 반복", 14, th.fg, true));
        char[] codes = {'A', 'B', 'C', 'N', 'O', 'L'};
        String[] names = {"A", "B", "C", "통상", "휴", "휴가"};
        boolean[] ss = new boolean[6];
        for (int i = 0; i < 6; i++) ss[i] = it.shifts.indexOf(codes[i]) >= 0;
        rc.addView(Ui.chips(act, th, names, ss, () -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) if (ss[i]) sb.append(codes[i]);
            it.shifts = sb.toString();
        }));
        TextView d2 = Ui.text(act, "요일에 반복", 14, th.fg, true);
        d2.setPadding(0, dp(10), 0, 0);
        rc.addView(d2);
        boolean[] ds = new boolean[7];
        for (int i = 0; i < 7; i++) ds[i] = (it.dow & (1 << i)) != 0;
        rc.addView(Ui.chips(act, th, DOWS, ds, () -> {
            int m = 0;
            for (int i = 0; i < 7; i++) if (ds[i]) m |= 1 << i;
            it.dow = m;
        }));
        TextView rn = Ui.text(act, "근무와 요일 중 하나라도 맞는 날 울려요. 휴가·검진·교육 등 종일 휴무인 날은 '휴가'로 분류돼요.", 11, th.sub, false);
        rn.setPadding(0, dp(4), 0, 0);
        rc.addView(rn);
        box.addView(rc);

        // 알람 조건
        LinearLayout cc = Ui.card(act, th);
        cc.addView(Ui.text(act, "알람 날짜", 14, th.fg, true));
        int[] dayOffs = {0, -1, 1};
        cc.addView(Ui.segmented(act, th, new String[]{"근무 당일", "전날", "다음날"}, it.dayOff == -1 ? 1 : it.dayOff == 1 ? 2 : 0, i -> it.dayOff = dayOffs[i]));
        cc.addView(Ui.text(act, "자동 끄기", 14, th.fg, true));
        int[] autos = {0, 1, 3, 5, 10};
        int ai = 3;
        for (int i = 0; i < autos.length; i++) if (autos[i] == it.autoOff) ai = i;
        cc.addView(Ui.segmented(act, th, new String[]{"안 함", "1분", "3분", "5분", "10분"}, ai, i -> it.autoOff = autos[i]));
        cc.addView(Ui.text(act, "다시 울림 간격", 14, th.fg, true));
        int[] sns = {0, 3, 5, 10, 15};
        int si = 0;
        for (int i = 0; i < sns.length; i++) if (sns[i] == it.snoozeMin) si = i;
        cc.addView(Ui.segmented(act, th, new String[]{"안 함", "3분", "5분", "10분", "15분"}, si, i -> it.snoozeMin = sns[i]));
        cc.addView(Ui.text(act, "다시 울림 횟수", 14, th.fg, true));
        int[] cnts = {1, 2, 3, 5};
        int ci = 0;
        for (int i = 0; i < cnts.length; i++) if (cnts[i] == it.snoozeCnt) ci = i;
        cc.addView(Ui.segmented(act, th, new String[]{"1회", "2회", "3회", "5회"}, ci, i -> it.snoozeCnt = cnts[i]));
        box.addView(cc);

        // 응원 메시지, 진동, 알람음
        LinearLayout mc = Ui.card(act, th);
        mc.addView(Ui.text(act, "응원 메시지", 14, th.fg, true));
        EditText ch = new EditText(act);
        ch.setHint("알람이 울릴 때 보여줄 문구");
        ch.setHintTextColor(th.sub);
        ch.setTextColor(th.fg);
        ch.setTextSize(15);
        ch.setInputType(InputType.TYPE_CLASS_TEXT);
        ch.setBackground(Ui.round(th.soft, 12));
        ch.setPadding(dp(12), dp(10), dp(12), dp(10));
        ch.setText(it.cheer);
        ch.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { it.cheer = s.toString(); }
            public void afterTextChanged(android.text.Editable s) { }
        });
        mc.addView(ch);
        mc.addView(Ui.toggle(act, th, "진동", it.vib, on -> it.vib = on));
        LinearLayout sr = new LinearLayout(act);
        sr.setGravity(Gravity.CENTER_VERTICAL);
        sr.setPadding(0, dp(8), 0, dp(4));
        sr.addView(Ui.text(act, "알람음", 16, th.fg, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        soundLbl = Ui.text(act, soundName(it.sound), 14, th.todayStroke, true);
        sr.addView(soundLbl);
        sr.setOnClickListener(v -> pickSound(it));
        mc.addView(sr);
        box.addView(mc);

        if (!isNew) {
            TextView del = Ui.btn(act, th, "이 알람 삭제", false, v -> {
                Alarms.delete(it.id);
                Alarms.schedule(act);
                dlg.dismiss();
                refresh.run();
            });
            del.setTextColor(th.sun);
            box.addView(del);
        }
        dlg.show();
    }

    private Alarms.Item copy(Alarms.Item s) {
        Alarms.Item n = new Alarms.Item();
        n.id = s.id; n.on = s.on; n.min = s.min; n.shifts = s.shifts; n.dow = s.dow; n.dayOff = s.dayOff;
        n.autoOff = s.autoOff; n.snoozeMin = s.snoozeMin; n.snoozeCnt = s.snoozeCnt; n.cheer = s.cheer; n.vib = s.vib; n.sound = s.sound;
        return n;
    }

    private String soundName(String s) {
        if (s.isEmpty()) return "기본 알람음";
        if ("none".equals(s)) return "무음";
        try {
            return RingtoneManager.getRingtone(act, Uri.parse(s)).getTitle(act);
        } catch (Throwable e) { return "선택한 소리"; }
    }

    private void pickSound(Alarms.Item it) {
        try {
            Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "알람음");
            Uri cur = it.sound.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : "none".equals(it.sound) ? null : Uri.parse(it.sound);
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, cur);
            act.startActivityForResult(i, REQ_SOUND);
        } catch (Exception e) { toast("알람음 선택 화면을 열 수 없어요"); }
    }

    /** MainActivity.onActivityResult에서 호출 */
    void onSound(Intent data) {
        if (ed == null) return;
        Uri u = data == null ? null : (Uri) data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        if (u == null) ed.sound = "none";
        else if (u.equals(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))) ed.sound = "";
        else ed.sound = u.toString();
        if (soundLbl != null) soundLbl.setText(soundName(ed.sound));
    }

    private void toast(String s) { Toast.makeText(act, s, Toast.LENGTH_SHORT).show(); }
}
