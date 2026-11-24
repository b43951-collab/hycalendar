package com.hycalendar.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 알람이 울릴 때 뜨는 전체 화면. '알람 종료'를 눌러야만 꺼진다 (화면 터치·뒤로 가기로는 꺼지지 않는다). */
public class AlarmRingActivity extends Activity {
    private static final int BG = 0xFF252B3B, FG = 0xFFF2EEE9;
    private TextView ampm, hm, date, msg, circle;
    private LinearLayout btns;
    private final Handler h = new Handler(Looper.getMainLooper());
    private BroadcastReceiver closer;
    private int base = -1, kIdx = -1;
    private String key = "";
    private final Runnable tick = new Runnable() {
        @Override public void run() { updateClock(); h.postDelayed(this, 1000); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Store.init(this);
        Ui.init(this);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(build());
        bind(getIntent());
        closer = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { finish(); }
        };
        IntentFilter f = new IntentFilter(AlarmService.ACT_CLOSE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(closer, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(closer, f);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        bind(i);
    }

    @Override
    protected void onResume() { super.onResume(); h.post(tick); }

    @Override
    protected void onPause() { h.removeCallbacks(tick); super.onPause(); }

    @Override
    protected void onDestroy() {
        h.removeCallbacks(tick);
        try { if (closer != null) unregisterReceiver(closer); } catch (Throwable ignored) { }
        super.onDestroy();
    }

    /** 실수로 꺼지지 않도록 뒤로 가기는 막는다. */
    @Override
    public void onBackPressed() { }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setPadding(Ui.dp(24), Ui.dp(40), Ui.dp(24), Ui.dp(40));

        root.addView(spacer(1));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_mark);
        logo.setAdjustViewBounds(true);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Ui.dp(84), Ui.dp(84));
        llp.setMargins(0, 0, 0, Ui.dp(22));
        root.addView(logo, llp);
        ampm = Ui.text(this, "", 44, FG, true);
        ampm.setGravity(Gravity.CENTER);
        root.addView(ampm, wrap());
        hm = Ui.text(this, "", 88, FG, true);
        hm.setGravity(Gravity.CENTER);
        root.addView(hm, wrap());
        date = Ui.text(this, "", 20, FG, false);
        date.setGravity(Gravity.CENTER);
        date.setPadding(0, Ui.dp(14), 0, Ui.dp(30));
        root.addView(date, wrap());
        msg = Ui.text(this, "", 22, FG, false);
        msg.setGravity(Gravity.CENTER);
        root.addView(msg, wrap());
        root.addView(spacer(1));
        circle = Ui.text(this, "", 26, Color.WHITE, true);
        circle.setGravity(Gravity.CENTER);
        root.addView(circle, new LinearLayout.LayoutParams(Ui.dp(64), Ui.dp(64)));
        root.addView(spacer(1));
        btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.VERTICAL);
        root.addView(btns, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private View spacer(float w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, w));
        return v;
    }

    private void bind(Intent i) {
        base = i.getIntExtra("base", -1);
        key = i.getStringExtra("key") == null ? "" : i.getStringExtra("key");
        kIdx = i.getIntExtra("k", -1);
        String text = i.getStringExtra("text");
        msg.setText(text == null || text.isEmpty() ? "" : "\"" + text + "\"");
        String sh = i.getStringExtra("shift");
        char c = sh == null || sh.isEmpty() ? 'N' : sh.charAt(0);
        int fill = c == 'A' ? 0xFFFFD400 : c == 'B' ? 0xFF5285E8 : c == 'C' ? 0xFF404040 : c == 'L' ? 0xFFE65100 : 0xFF6B7280;
        String label = c == 'O' ? "휴" : c == 'L' ? "휴가" : c == 'N' ? "통상" : String.valueOf(c);
        circle.setText(label);
        circle.setTextColor(c == 'A' ? Color.BLACK : Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        circle.setBackground(g);

        btns.removeAllViews();
        int more = i.getIntExtra("more", 0), snz = i.getIntExtra("snz", 0);
        if (more > 0 && snz > 0) {
            TextView sn = pill("다시 울림 · " + snz + "분 뒤", 0xFFD1D5DB, Color.BLACK, 18);
            sn.setOnClickListener(v -> send(AlarmService.ACT_SNOOZE));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(56));
            lp.setMargins(0, 0, 0, Ui.dp(14));
            btns.addView(sn, lp);
        }
        TextView st = pill("알람 종료", Color.WHITE, Color.BLACK, 20);
        st.setOnClickListener(v -> send(AlarmService.ACT_STOP));
        btns.addView(st, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(64)));
        updateClock();
    }

    private TextView pill(String s, int fill, int tc, float sp) {
        TextView t = Ui.text(this, s, sp, tc, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(Ui.dp(40));
        t.setBackground(g);
        return t;
    }

    private void send(String action) {
        try { startService(new Intent(this, AlarmService.class).setAction(action).putExtra("base", base).putExtra("key", key).putExtra("k", kIdx)); } catch (Throwable ignored) { }
        finish();
    }

    private void updateClock() {
        LocalTime t = LocalTime.now();
        int hh = t.getHour();
        boolean h12 = Store.getB("alarm12h", true);
        ampm.setVisibility(h12 ? View.VISIBLE : View.GONE);
        ampm.setText(hh < 12 ? "오전" : "오후");
        hm.setText(String.format(Locale.US, "%02d  %02d", h12 ? (hh % 12 == 0 ? 12 : hh % 12) : hh, t.getMinute()));
        date.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd(E)", Locale.KOREAN)));
    }
}
