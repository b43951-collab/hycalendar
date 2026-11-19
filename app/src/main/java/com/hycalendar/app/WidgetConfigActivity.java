package com.hycalendar.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** 위젯 설정 화면 (추가할 때, 그리고 위젯 왼쪽 위 톱니를 눌렀을 때). */
public class WidgetConfigActivity extends Activity {
    private int id;
    private String pfx;
    private Theme th;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Store.init(this);
        Ui.init(this);
        setResult(RESULT_CANCELED);
        id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        if (id == 0) { finish(); return; }
        pfx = "w" + id + "_";
        th = Theme.get(Store.getS("theme", "auto"), Opts.systemDark(this));
        getWindow().setStatusBarColor(th.page);
        getWindow().setNavigationBarColor(th.page);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(th.page);
        sv.setFitsSystemWindows(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(24));
        sv.addView(box);
        setContentView(sv);

        box.addView(Ui.text(this, "위젯 설정", 22, th.fg, true));

        box.addView(Ui.sectionTitle(this, th, "표시"));
        LinearLayout c1 = Ui.card(this, th);
        c1.addView(Ui.text(this, "표시 방식", 13, th.sub, false));
        c1.addView(Ui.segmented(this, th, new String[]{"나", "부부", "4개조"}, Store.getI(pfx + "mode", 0), i -> Store.putI(pfx + "mode", i)));
        c1.addView(Ui.text(this, "음력", 13, th.sub, false));
        c1.addView(Ui.segmented(this, th, new String[]{"안 함", "초하루·보름", "매일"}, Opts.i(pfx, "lunar", 1), i -> Store.putI(pfx + "lunar", i)));
        c1.addView(Ui.toggle(this, th, "메모 표시", Opts.b(pfx, "showmemo", true), on -> Store.putB(pfx + "showmemo", on)));
        c1.addView(Ui.toggle(this, th, "달력 6주 고정", Opts.b(pfx, "fixed6", false), on -> Store.putB(pfx + "fixed6", on)));
        box.addView(c1);

        box.addView(Ui.sectionTitle(this, th, "배경"));
        LinearLayout c2 = Ui.card(this, th);
        c2.addView(Ui.text(this, "배경 색", 13, th.sub, false));
        c2.addView(Ui.segmented(this, th, new String[]{"테마 따라", "밝게", "어둡게"}, Store.getI(pfx + "bgstyle", 0), i -> Store.putI(pfx + "bgstyle", i)));
        c2.addView(Ui.slider(this, th, "배경 투명도 (오른쪽이 진함, %)", 0f, 100f, Store.getI(pfx + "alpha", 100), v -> Store.putI(pfx + "alpha", Math.round(v))));
        box.addView(c2);

        box.addView(Ui.sectionTitle(this, th, "크기"));
        LinearLayout c3 = Ui.card(this, th);
        c3.addView(Ui.slider(this, th, "날짜 글씨 크기", 0.6f, 1.6f, Opts.f(pfx, "s_date", 1f), v -> Store.putF(pfx + "s_date", v)));
        c3.addView(Ui.slider(this, th, "근무 표시 크기", 0.5f, 1.6f, Opts.f(pfx, "s_shift", 1f), v -> Store.putF(pfx + "s_shift", v)));
        c3.addView(Ui.slider(this, th, "메모 글씨 크기", 0.6f, 1.6f, Opts.f(pfx, "s_memo", 1f), v -> Store.putF(pfx + "s_memo", v)));
        box.addView(c3);

        box.addView(Ui.btn(this, th, "완료", true, v -> {
            WidgetBase.update(this, AppWidgetManager.getInstance(this), id);
            Intent r = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            setResult(RESULT_OK, r);
            finish();
        }), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
