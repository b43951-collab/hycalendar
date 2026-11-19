package com.hycalendar.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** 위젯 설정 화면 (추가할 때, 그리고 위젯 길게 눌러 설정할 때). */
public class WidgetConfigActivity extends Activity {
    private int id;
    private String pfx;
    private float dp;

    private int dp(int v) { return (int) (v * dp + 0.5f); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Store.init(this);
        dp = getResources().getDisplayMetrics().density;
        setResult(RESULT_CANCELED);
        id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
        if (id == 0) { finish(); return; }
        pfx = "w" + id + "_";

        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(30));
        sv.addView(box);
        setContentView(sv);

        box.addView(title("위젯 설정"));

        box.addView(label("표시 방식"));
        box.addView(choice(pfx + "mode", new String[]{"나", "부부", "4개조"}, Store.getI(pfx + "mode", 0)));

        box.addView(label("배경 색"));
        box.addView(choice(pfx + "bgstyle", new String[]{"테마 따라", "밝게", "어둡게"}, Store.getI(pfx + "bgstyle", 0)));

        box.addView(seek("배경 투명도 (오른쪽이 진함)", pfx + "alpha", 0, 100, Store.getI(pfx + "alpha", 100), true));
        box.addView(seekF("날짜 글자 크기", pfx + "s_date", 0.6f, 1.6f, Opts.f(pfx, "s_date", 1f)));
        box.addView(seekF("근무 표시 크기", pfx + "s_shift", 0.5f, 1.6f, Opts.f(pfx, "s_shift", 1f)));
        box.addView(seekF("메모 글자 크기", pfx + "s_memo", 0.6f, 1.6f, Opts.f(pfx, "s_memo", 1f)));

        box.addView(label("음력 표시"));
        box.addView(choice(pfx + "lunar", new String[]{"끔", "초하루·보름", "전체"}, Opts.i(pfx, "lunar", 1)));

        box.addView(check("메모 표시", pfx + "showmemo", Opts.b(pfx, "showmemo", true)));
        box.addView(check("날짜 구분선", pfx + "lines", Opts.b(pfx, "lines", true)));
        box.addView(check("달력 6주 고정", pfx + "fixed6", Opts.b(pfx, "fixed6", false)));

        Button ok = new Button(this);
        ok.setText("완료");
        ok.setOnClickListener(v -> {
            WidgetBase.update(this, AppWidgetManager.getInstance(this), id);
            Intent r = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            setResult(RESULT_OK, r);
            finish();
        });
        box.addView(ok);
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(20); t.setTextColor(Color.BLACK);
        t.setPadding(0, 0, 0, dp(10));
        return t;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(14); t.setTextColor(Color.DKGRAY);
        t.setPadding(0, dp(14), 0, dp(4));
        return t;
    }

    private View choice(String key, String[] names, int cur) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button[] bs = new Button[names.length];
        for (int i = 0; i < names.length; i++) {
            Button bt = new Button(this);
            bt.setText(names[i]);
            bt.setAllCaps(false);
            bt.setAlpha(i == cur ? 1f : 0.45f);
            final int ix = i;
            bs[i] = bt;
            bt.setOnClickListener(v -> {
                Store.putI(key, ix);
                for (int j = 0; j < bs.length; j++) bs[j].setAlpha(j == ix ? 1f : 0.45f);
            });
            row.addView(bt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        return row;
    }

    private View check(String s, String key, boolean cur) {
        CheckBox cb = new CheckBox(this);
        cb.setText(s); cb.setChecked(cur); cb.setTextColor(Color.BLACK);
        cb.setOnCheckedChangeListener((v, on) -> Store.putB(key, on));
        return cb;
    }

    private View seek(String s, String key, int min, int max, int cur, boolean isInt) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(label(s));
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(cur - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar x, int p, boolean u) { if (u) Store.putI(key, p + min); }
            public void onStartTrackingTouch(SeekBar x) { }
            public void onStopTrackingTouch(SeekBar x) { }
        });
        l.addView(sb);
        return l;
    }

    private View seekF(String s, String key, float min, float max, float cur) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(label(s));
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(Math.round((cur - min) / (max - min) * 100));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar x, int p, boolean u) { if (u) Store.putF(key, min + (max - min) * p / 100f); }
            public void onStartTrackingTouch(SeekBar x) { }
            public void onStopTrackingTouch(SeekBar x) { }
        });
        l.addView(sb);
        return l;
    }
}
