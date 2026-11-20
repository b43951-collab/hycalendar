package com.hycalendar.app;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 날짜 상세 시트(메모/근무 탭), 메모 입력 화면, 메모 전체보기 */
final class DayUi {
    private static final String[] DOWS = {"일", "월", "화", "수", "목", "금", "토"};

    private final MainActivity act;
    private final Theme th;
    private final Runnable changed;

    private LocalDate day;
    private int tab = 0;
    private Dialog sheet;
    private FrameLayout body;
    private TextView allBtn;
    private LinearLayout tabMemo, tabWork;

    DayUi(MainActivity a, Theme t, Runnable c) { act = a; th = t; changed = c; }

    private int dp(float v) { return Ui.dp(v); }
    private void toast(String s) { Toast.makeText(act, s, Toast.LENGTH_SHORT).show(); }

    // ================= 날짜 상세 시트 =================
    void show(LocalDate d) {
        day = d;
        tab = 0;
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.card);
        float r = dp(24);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        root.setBackground(bg);
        root.setPadding(dp(18), dp(14), dp(18), 0);

        // 날짜 한 줄 + 닫기
        LinearLayout top = new LinearLayout(act);
        top.setGravity(Gravity.CENTER_VERTICAL);
        int dow = d.getDayOfWeek().getValue() % 7;
        Lunar.Res lr = Lunar.of(d);
        String hol = Holidays.name(d);
        TextView ht = Ui.text(act, d.getMonthValue() + "월 " + d.getDayOfMonth() + "일 " + DOWS[dow] + "요일", 18, dow == 0 ? th.sun : dow == 6 ? th.sat : th.fg, true);
        top.addView(ht);
        TextView sub = Ui.text(act, "  음 " + (lr.leap ? "윤" : "") + lr.m + "." + lr.d + (hol.isEmpty() ? "" : " · " + hol), 12, hol.isEmpty() ? th.sub : th.sun, false);
        top.addView(sub);
        top.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1));
        TextView x = Ui.text(act, "✕", 20, th.sub, false);
        x.setGravity(Gravity.CENTER);
        x.setOnClickListener(v -> sheet.dismiss());
        top.addView(x, new LinearLayout.LayoutParams(dp(40), dp(36)));
        root.addView(top);

        // 탭
        LinearLayout tabs = new LinearLayout(act);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(0, dp(6), 0, dp(6));
        tabMemo = tabItem("메모", 0);
        tabWork = tabItem("근무", 1);
        tabs.addView(tabMemo);
        tabs.addView(tabWork);
        tabs.addView(new View(act), new LinearLayout.LayoutParams(0, 1, 1));
        allBtn = Ui.text(act, "전체보기", 13, th.todayStroke, true);
        allBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        allBtn.setBackground(Ui.round(th.soft, 18));
        allBtn.setOnClickListener(v -> showAll());
        tabs.addView(allBtn);
        root.addView(tabs);

        body = new FrameLayout(act);
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        sheet = Ui.sheetDialog(act, th, root, 0.82f);
        render();
        sheet.show();
    }

    private LinearLayout tabItem(String label, int idx) {
        LinearLayout l = new LinearLayout(act);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(dp(4), dp(4), dp(4), 0);
        TextView t = Ui.text(act, label, 20, th.sub, true);
        t.setTag("t");
        l.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View u = new View(act);
        u.setTag("u");
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(dp(26), dp(3));
        up.setMargins(0, dp(4), 0, 0);
        l.addView(u, up);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(18), 0);
        l.setLayoutParams(lp);
        l.setOnClickListener(v -> { tab = idx; render(); });
        return l;
    }

    private void paintTab(LinearLayout l, boolean on) {
        ((TextView) l.findViewWithTag("t")).setTextColor(on ? th.fg : th.sub);
        l.findViewWithTag("u").setBackgroundColor(on ? th.fg : 0);
    }

    private void render() {
        paintTab(tabMemo, tab == 0);
        paintTab(tabWork, tab == 1);
        allBtn.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        body.removeAllViews();
        ScrollView sv = new ScrollView(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(24));
        sv.addView(box);
        body.addView(sv);
        if (tab == 0) fillMemo(box); else fillWork(box);
    }

    // ---- 메모 탭 ----
    private void fillMemo(LinearLayout box) {
        LinearLayout add = new LinearLayout(act);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setPadding(dp(2), dp(10), dp(2), dp(10));
        TextView plus = Ui.text(act, "+", 18, Color.WHITE, true);
        plus.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(th.sub);
        plus.setBackground(g);
        add.addView(plus, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView lab = Ui.text(act, "  새 메모", 18, th.sub, false);
        add.addView(lab);
        add.setOnClickListener(v -> openEditor(null, day));
        box.addView(add);

        List<Memos.Memo> list = Memos.forDate(day);
        if (list.isEmpty()) {
            TextView e = Ui.text(act, "이 날의 메모가 없어요", 13, th.sub, false);
            e.setPadding(dp(4), dp(8), 0, 0);
            box.addView(e);
        }
        for (Memos.Memo m : list) box.addView(memoRow(m, true));
    }

    private View memoRow(Memos.Memo m, boolean showDate) { return memoRow(m, showDate, null); }

    private View memoRow(Memos.Memo m, boolean showDate, View.OnClickListener onDelete) {
        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(8), dp(10));
        row.setBackground(Ui.round(m.bg != 0 ? m.bg : th.soft, 14));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(rp);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(act, m.text.isEmpty() ? "(내용 없음)" : m.text, 16, m.color != 0 ? m.color : th.fg, false);
        col.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String meta = meta(m, showDate);
        if (!meta.isEmpty()) col.addView(Ui.text(act, meta, 11, th.sub, false), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (onDelete != null) {
            TextView del = Ui.text(act, "삭제", 13, th.sun, true);
            del.setPadding(dp(12), dp(8), dp(12), dp(8));
            del.setBackground(Ui.round(th.card, 12));
            del.setOnClickListener(onDelete);
            row.addView(del);
        }
        row.setOnClickListener(v -> openEditor(m, m.start));
        return row;
    }

    private String meta(Memos.Memo m, boolean showDate) {
        List<String> p = new ArrayList<>();
        if (!m.label.isEmpty()) p.add("#" + m.label);
        if (!m.allDay) p.add(Alarms.clockShort(m.sMin) + "~" + Alarms.clockShort(m.eMin));
        if (m.end != null && !m.end.equals(m.start) && !m.repeats()) p.add(md(m.start) + "~" + md(m.end));
        if (m.repeats()) p.add("반복" + (m.end != null ? " ~" + md(m.end) : ""));
        if (m.notify >= 0) p.add("알림 " + Alarms.clockShort(m.notify));
        if (!showDate) p.add(0, md(m.start));
        return String.join("  ·  ", p);
    }

    private String md(LocalDate d) { return d.getMonthValue() + "/" + d.getDayOfMonth(); }

    // ---- 근무 탭 ----
    private void fillWork(LinearLayout box) {
        Opts o = Opts.load(act, "", Renderer.COUPLE);
        char my = ShiftCore.typeFor(o.me, day), mt = ShiftCore.typeFor(o.mate, day);
        LinearLayout sr = new LinearLayout(act);
        sr.setPadding(0, dp(4), 0, dp(6));
        sr.addView(pill("나", my));
        sr.addView(new View(act), new LinearLayout.LayoutParams(dp(8), 1));
        sr.addView(pill("배우자", mt));
        box.addView(sr);

        for (int who = 0; who < 2; who++) {
            final int wf = who;
            String wn = who == 0 ? "나" : "배우자";
            char sh = who == 0 ? my : mt;
            List<Store.Use> uses = Store.usesOn(day, who);
            if (!uses.isEmpty()) {
                LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ml.setMargins(0, dp(12), 0, dp(2));
                box.addView(Ui.text(act, wn + " 근태 기록", 13, th.sub, true), ml);
                for (Store.Use u : uses) {
                    LeaveCore.Type t = LeaveCore.type(u.key);
                    LinearLayout row = new LinearLayout(act);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.addView(Ui.text(act, t == null ? u.key : t.label, 16, th.orange, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    row.addView(Ui.btn(act, th, "삭제", false, v -> {
                        Store.removeUse(u.id);
                        toast("취소했어요. 잔여가 복원됐어요");
                        changed.run();
                        render();
                    }));
                    box.addView(row);
                }
            }
            if (sh != 'O') {
                LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ml.setMargins(0, dp(14), 0, dp(4));
                box.addView(Ui.text(act, wn + " 근태 추가 (" + sh + " 근무)", 13, th.sub, true), ml);
                List<LeaveCore.Type> ok = new ArrayList<>();
                for (LeaveCore.Type t : LeaveCore.TYPES) if (LeaveCore.allowed(t, sh)) ok.add(t);
                for (int i = 0; i < ok.size(); i += 4) {
                    LinearLayout row = new LinearLayout(act);
                    for (int j = i; j < i + 4; j++) {
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
                        if (j >= ok.size()) { row.addView(new View(act), lp); continue; }
                        final LeaveCore.Type t = ok.get(j);
                        TextView bt = Ui.btn(act, th, t.label, false, v -> {
                            for (String k : Store.useKeysOn(day, wf)) if (k.equals(t.key)) { toast("이미 등록돼 있어요"); return; }
                            double nb = Store.addUse(day, t, wf);
                            if (Double.isNaN(nb)) {
                                if (t.pool != null) toast(LeaveCore.START + "년 이전 기간은 차감 없이 기록만 해요");
                            } else if (nb < 0) toast("잔여가 부족해요 (" + nb + ")");
                            changed.run();
                            render();
                        });
                        bt.setTextSize(12);
                        bt.setPadding(dp(2), dp(10), dp(2), dp(10));
                        row.addView(bt, lp);
                    }
                    box.addView(row);
                }
            }
        }
    }

    private View pill(String who, char t) {
        String s = who + "  " + (t == 'O' ? "휴" : String.valueOf(t));
        TextView v = Ui.text(act, s, 14, t == 'O' ? th.offText : Theme.onColor(th.shiftColor(t)), true);
        v.setPadding(dp(14), dp(7), dp(14), dp(7));
        v.setBackground(Ui.round(t == 'O' ? th.soft : th.shiftColor(t), 18));
        return v;
    }

    // ================= 전체보기 =================
    private void showAll() {
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
        TextView tt = Ui.text(act, "전체 메모", 18, th.fg, true);
        tt.setGravity(Gravity.CENTER);
        bar.addView(tt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(new View(act), new LinearLayout.LayoutParams(dp(48), 1));
        root.addView(bar);
        ScrollView sv = new ScrollView(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(4), dp(14), dp(24));
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        fillAll(dlg, box);
        dlg.show();
    }

    private void fillAll(Dialog dlg, LinearLayout box) {
        box.removeAllViews();
        List<Memos.Memo> all = Memos.sorted();
        if (all.isEmpty()) {
            TextView e = Ui.text(act, "메모가 없어요", 14, th.sub, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(40), 0, 0);
            box.addView(e);
        }
        for (Memos.Memo m : all) {
            View r = memoRow(m, false, v -> new android.app.AlertDialog.Builder(act)
                    .setTitle("메모 삭제")
                    .setMessage("이 메모를 삭제할까요?\n" + (m.text.length() > 30 ? m.text.substring(0, 30) + "…" : m.text))
                    .setPositiveButton("삭제", (x, w) -> {
                        Memos.delete(m.id);
                        Alarms.schedule(act);
                        changed.run();
                        fillAll(dlg, box);
                        if (sheet != null && sheet.isShowing()) render();
                    }).setNegativeButton("취소", null).show());
            r.setOnClickListener(v -> { dlg.dismiss(); openEditor(m, m.start); });
            box.addView(r);
        }
    }

    // ================= 메모 입력 =================
    private EditText field(String hint, String text, int minLines) {
        EditText e = new EditText(act);
        e.setHint(hint);
        e.setHintTextColor(th.sub);
        e.setTextColor(th.fg);
        e.setTextSize(15);
        e.setInputType(minLines > 1 ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_CLASS_TEXT);
        e.setMinLines(minLines);
        e.setGravity(minLines > 1 ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        e.setBackground(Ui.round(th.soft, 12));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setText(text);
        return e;
    }

    private View labelRow(String label, View right) {
        LinearLayout r = new LinearLayout(act);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(7), 0, dp(7));
        r.addView(Ui.text(act, label, 16, th.fg, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.addView(right);
        return r;
    }

    private TextView valueBtn(String s) {
        TextView t = Ui.text(act, s, 14, th.todayStroke, true);
        t.setPadding(dp(12), dp(7), dp(12), dp(7));
        t.setBackground(Ui.round(th.soft, 10));
        return t;
    }

    private void openEditor(Memos.Memo src, LocalDate defDate) {
        final boolean isNew = src == null;
        final Memos.Memo m = new Memos.Memo();
        if (isNew) {
            m.start = defDate;
        } else {
            m.id = src.id; m.text = src.text; m.url = src.url; m.label = src.label; m.color = src.color; m.bg = src.bg;
            m.todo = src.todo; m.done = src.done; m.allDay = src.allDay; m.start = src.start; m.end = src.end;
            m.sMin = src.sMin; m.eMin = src.eMin; m.notify = src.notify; m.repShift = src.repShift; m.repDow = src.repDow; m.repDate = src.repDate; m.repLunar = src.repLunar;
        }

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(th.page);
        final Dialog dlg = Ui.fullDialog(act, th, root);

        final EditText txt = field("메모 내용", m.text, 4);
        final EditText lab = field("라벨 (선택)", m.label, 1);

        LinearLayout bar = new LinearLayout(act);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        TextView close = Ui.text(act, "✕", 22, th.fg, false);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> dlg.dismiss());
        bar.addView(close, new LinearLayout.LayoutParams(dp(48), dp(44)));
        TextView tt = Ui.text(act, isNew ? "새 메모" : "메모 수정", 18, th.fg, true);
        tt.setGravity(Gravity.CENTER);
        bar.addView(tt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(Ui.btn(act, th, "저장", true, v -> {
            m.text = txt.getText().toString().trim();
            m.label = lab.getText().toString().trim().replace("#", "");
            if (m.text.isEmpty()) { toast("내용을 입력하세요"); return; }
            if (m.end != null && m.end.isBefore(m.start)) m.end = m.start;
            Memos.save(m);
            Alarms.schedule(act);
            dlg.dismiss();
            changed.run();
            if (sheet != null && sheet.isShowing()) render();
        }));
        root.addView(bar);

        ScrollView sv = new ScrollView(act);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(4), dp(14), dp(24));
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // 내용 + 색
        LinearLayout c1 = Ui.card(act, th);
        c1.addView(txt);
        // 색: 글자색 / 배경색 한 줄
        LinearLayout cr = new LinearLayout(act);
        cr.setGravity(Gravity.CENTER_VERTICAL);
        cr.setPadding(0, dp(12), 0, dp(4));
        cr.addView(Ui.text(act, "색", 16, th.fg, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView ta = Ui.text(act, "글자", 14, th.sub, true);
        ta.setPaintFlags(ta.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        cr.addView(ta);
        final ColorUi.Dot textDot = ColorUi.dot(act, th, m.color, true);
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(dp(34), dp(34));
        dp1.setMargins(dp(8), 0, dp(22), 0);
        textDot.setOnClickListener(v -> ColorUi.pick(act, th, m.color, true, c -> { m.color = c; textDot.set(c); }));
        cr.addView(textDot, dp1);
        cr.addView(Ui.text(act, "배경", 14, th.sub, true));
        final ColorUi.Dot bgDot = ColorUi.dot(act, th, m.bg, false);
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(dp(34), dp(34));
        dp2.setMargins(dp(8), 0, 0, 0);
        bgDot.setOnClickListener(v -> ColorUi.pick(act, th, m.bg, false, c -> { m.bg = c; bgDot.set(c); }));
        cr.addView(bgDot, dp2);
        c1.addView(cr);
        box.addView(c1);

        // 라벨
        LinearLayout c2 = Ui.card(act, th);
        c2.addView(lab);
        box.addView(c2);

        // 기간
        LinearLayout c3 = Ui.card(act, th);
        c3.addView(Ui.text(act, "기간", 14, th.fg, true));
        final LinearLayout timeBox = new LinearLayout(act);
        timeBox.setOrientation(LinearLayout.VERTICAL);
        timeBox.setVisibility(m.allDay ? View.GONE : View.VISIBLE);
        c3.addView(Ui.toggle(act, th, "하루 종일", m.allDay, on -> { m.allDay = on; timeBox.setVisibility(on ? View.GONE : View.VISIBLE); }));
        final TextView sTime = valueBtn(Alarms.clockShort(m.sMin));
        sTime.setOnClickListener(v -> new TimePickerDialog(act, (vw, h, mi) -> { m.sMin = h * 60 + mi; sTime.setText(Alarms.clockShort(m.sMin)); }, m.sMin / 60, m.sMin % 60, true).show());
        final TextView eTime = valueBtn(Alarms.clockShort(m.eMin));
        eTime.setOnClickListener(v -> new TimePickerDialog(act, (vw, h, mi) -> { m.eMin = h * 60 + mi; eTime.setText(Alarms.clockShort(m.eMin)); }, m.eMin / 60, m.eMin % 60, true).show());
        timeBox.addView(labelRow("시작 시간", sTime));
        timeBox.addView(labelRow("종료 시간", eTime));
        c3.addView(timeBox);

        final TextView sd = valueBtn(dateText(m.start));
        final TextView ed = valueBtn(m.end == null ? "없음" : dateText(m.end));
        sd.setOnClickListener(v -> new DatePickerDialog(act, (vw, y, mo, dd) -> { m.start = LocalDate.of(y, mo + 1, dd); sd.setText(dateText(m.start)); },
                m.start.getYear(), m.start.getMonthValue() - 1, m.start.getDayOfMonth()).show());
        ed.setOnClickListener(v -> {
            LocalDate b = m.end == null ? m.start : m.end;
            new DatePickerDialog(act, (vw, y, mo, dd) -> { m.end = LocalDate.of(y, mo + 1, dd); ed.setText(dateText(m.end)); },
                    b.getYear(), b.getMonthValue() - 1, b.getDayOfMonth()).show();
        });
        ed.setOnLongClickListener(v -> { m.end = null; ed.setText("없음"); return true; });
        c3.addView(labelRow("시작일", sd));
        c3.addView(labelRow("종료일", ed));
        TextView en = Ui.text(act, "종료일을 길게 누르면 지워져요 (반복 메모는 종료 없이 계속)", 11, th.sub, false);
        c3.addView(en);

        final TextView nt = valueBtn(Alarms.clockShort(m.notify < 0 ? 540 : m.notify));
        nt.setVisibility(m.notify >= 0 ? View.VISIBLE : View.GONE);
        nt.setOnClickListener(v -> {
            int cur = m.notify < 0 ? 540 : m.notify;
            new TimePickerDialog(act, (vw, h, mi) -> { m.notify = h * 60 + mi; nt.setText(Alarms.clockShort(m.notify)); }, cur / 60, cur % 60, true).show();
        });
        LinearLayout nr = new LinearLayout(act);
        nr.setGravity(Gravity.CENTER_VERTICAL);
        nr.setPadding(0, dp(9), 0, dp(2));
        nr.addView(Ui.text(act, "알림", 16, th.fg, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.setMargins(0, 0, dp(10), 0);
        nr.addView(nt, np);
        nr.addView(Ui.switchView(act, th, m.notify >= 0, on -> {
            m.notify = on ? 540 : -1;
            nt.setText(Alarms.clockShort(540));
            nt.setVisibility(on ? View.VISIBLE : View.GONE);
        }));
        c3.addView(nr);
        box.addView(c3);

        // 반복 (고른 조건을 모두 만족하는 날만)
        LinearLayout c4 = Ui.card(act, th);
        c4.addView(Ui.text(act, "근무에 반복", 14, th.fg, true));
        char[] codes = {'A', 'B', 'C', 'O'};
        boolean[] ss = new boolean[4];
        for (int i = 0; i < 4; i++) ss[i] = m.repShift.indexOf(codes[i]) >= 0;
        c4.addView(Ui.chips(act, th, new String[]{"A", "B", "C", "휴"}, ss, () -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) if (ss[i]) sb.append(codes[i]);
            m.repShift = sb.toString();
        }));
        TextView l4 = Ui.text(act, "요일에 반복", 14, th.fg, true);
        l4.setPadding(0, dp(8), 0, 0);
        c4.addView(l4);
        boolean[] ds = new boolean[7];
        for (int i = 0; i < 7; i++) ds[i] = (m.repDow & (1 << i)) != 0;
        c4.addView(Ui.chips(act, th, DOWS, ds, () -> {
            int mk = 0;
            for (int i = 0; i < 7; i++) if (ds[i]) mk |= 1 << i;
            m.repDow = mk;
        }));
        TextView l5 = Ui.text(act, "날짜에 반복", 14, th.fg, true);
        l5.setPadding(0, dp(8), 0, 0);
        c4.addView(l5);
        final View lunarRow = Ui.toggle(act, th, "음력 기준 (시작일의 음력 날짜로 반복)", m.repLunar, on -> m.repLunar = on);
        lunarRow.setVisibility(m.repDate != 0 ? View.VISIBLE : View.GONE);
        c4.addView(Ui.segmented(act, th, new String[]{"안 함", "매월", "매년"}, m.repDate, i -> {
            m.repDate = i;
            lunarRow.setVisibility(i != 0 ? View.VISIBLE : View.GONE);
        }));
        c4.addView(lunarRow);
        TextView rn = Ui.text(act, "근무·요일·날짜를 함께 고르면 모두 맞는 날에만 표시돼요. 예) A + 일요일 = A조 근무인 일요일. 시작일 이후부터 적용돼요.", 11, th.sub, false);
        c4.addView(rn);
        box.addView(c4);

        if (!isNew) {
            TextView del = Ui.btn(act, th, "이 메모 삭제", false, v -> {
                Memos.delete(m.id);
                Alarms.schedule(act);
                dlg.dismiss();
                changed.run();
                if (sheet != null && sheet.isShowing()) render();
            });
            del.setTextColor(th.sun);
            box.addView(del);
        }
        dlg.show();
    }

    private String dateText(LocalDate d) {
        return d.getYear() + "." + d.getMonthValue() + "." + d.getDayOfMonth() + " (" + DOWS[d.getDayOfWeek().getValue() % 7] + ")";
    }
}
