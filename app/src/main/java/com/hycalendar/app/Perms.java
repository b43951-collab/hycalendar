package com.hycalendar.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 알람이 제대로 울리고 앱이 동작하는 데 필요한 권한을 점검하고, 설정 화면으로 안내한다. */
final class Perms {
    private Perms() {}

    static final int VER = 1;
    private static Runnable rebuild;

    static final class Row {
        String title, why;
        boolean ok;
        boolean essential = true;   // 알람이 제대로 울리는 데 꼭 필요한 권한이면 true
        Runnable fix;
    }

    private static void open(Activity a, Intent i, Intent fallback) {
        try { a.startActivity(i); }
        catch (Throwable e) {
            try { if (fallback != null) a.startActivity(fallback); } catch (Throwable ignored) { }
        }
    }

    private static Uri pkg(Activity a) { return Uri.parse("package:" + a.getPackageName()); }

    static List<Row> rows(final Activity a) {
        List<Row> l = new ArrayList<>();
        NotificationManager nm = (NotificationManager) a.getSystemService(Activity.NOTIFICATION_SERVICE);

        Row n = new Row();
        n.title = "알림";
        n.why = "알람과 메모 알림을 받으려면 꼭 필요해요";
        boolean notifOk = nm != null && nm.areNotificationsEnabled();
        if (Build.VERSION.SDK_INT >= 33 && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) notifOk = false;
        n.ok = notifOk;
        n.fix = () -> {
            if (Build.VERSION.SDK_INT >= 33 && !Store.getB("notif_asked", false)) {
                Store.putB("notif_asked", true);
                a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            } else {
                open(a, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()),
                        new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(a)));
            }
        };
        l.add(n);

        AlarmManager am = (AlarmManager) a.getSystemService(Activity.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= 31 && am != null && !am.canScheduleExactAlarms()) {
            Row e = new Row();
            e.title = "정확한 시간에 알람";
            e.why = "정해진 시각에 정확히 울리게 해 줘요";
            e.ok = false;
            e.fix = () -> open(a, new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg(a)), new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(a)));
            l.add(e);
        }

        Row f = new Row();
        f.title = "잠금 화면에서 알람 화면 표시";
        f.why = "화면이 꺼져 있어도 알람 화면이 켜지면서 울려요";
        f.ok = Build.VERSION.SDK_INT < 34 || (nm != null && nm.canUseFullScreenIntent());
        f.fix = () -> open(a, new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", pkg(a)), new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(a)));
        l.add(f);

        Row o = new Row();
        o.title = "다른 앱 위에 표시";
        o.why = "폰을 쓰는 중에도 알람 화면이 바로 떠요";
        o.ok = Settings.canDrawOverlays(a);
        o.fix = () -> open(a, new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg(a)), new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(a)));
        l.add(o);

        Row b = new Row();
        b.title = "배터리 사용 제한 없음";
        b.why = "절전 모드에서도 알람이 빠지지 않고 울려요";
        PowerManager pm = (PowerManager) a.getSystemService(Activity.POWER_SERVICE);
        b.ok = pm != null && pm.isIgnoringBatteryOptimizations(a.getPackageName());
        b.fix = () -> open(a, new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg(a)), new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        l.add(b);

        Row u = new Row();
        u.title = "앱 설치 허용";
        u.why = "앱 안에서 새 버전으로 업데이트하려면 필요해요";
        u.essential = false;
        u.ok = a.getPackageManager().canRequestPackageInstalls();
        u.fix = () -> open(a, new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg(a)), new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(a)));
        l.add(u);
        return l;
    }

    static boolean allOk(Activity a) {
        for (Row r : rows(a)) if (!r.ok) return false;
        return true;
    }

    static boolean essentialMissing(Activity a) {
        for (Row r : rows(a)) if (r.essential && !r.ok) return true;
        return false;
    }

    private static long nextAt() {
        try { return Long.parseLong(Store.getS("perm_next", "0")); } catch (Exception e) { return 0; }
    }

    /**
     * 앱을 열 때 권한 창을 띄울지: 처음이거나 안내가 새로 생긴 버전이면 한 번,
     * 그 뒤에는 필수 권한이 빠져 있는 동안 '나중에'를 누른 지 24시간이 지나면 다시.
     */
    static boolean needGuide(Activity a) {
        if (Store.getI("perm_guide_ver", 0) < VER && !allOk(a)) return true;
        return essentialMissing(a) && System.currentTimeMillis() >= nextAt();
    }

    /** 설정에서 돌아왔을 때 화면의 ✓/허용 표시를 다시 그린다. */
    static void refresh() {
        Runnable r = rebuild;
        if (r != null) r.run();
    }

    static void show(final Activity a, final Theme th, final Runnable after) {
        Ui.init(a);
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(20), Ui.dp(18), Ui.dp(20), Ui.dp(8));
        final ScrollView sv = new ScrollView(a);
        sv.addView(box);
        final AlertDialog dlg = new AlertDialog.Builder(a).setView(sv).create();
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            box.removeAllViews();
            box.addView(Ui.text(a, "권한 설정", 20, th.fg, true));
            TextView ex = Ui.text(a, "알람이 꺼지지 않고 제때 울리려면 아래 권한을 모두 허용해 주세요. '허용'을 누르면 설정 화면이 열리고, 허용한 뒤 돌아오면 자동으로 표시가 바뀌어요.", 13, th.sub, false);
            ex.setPadding(0, Ui.dp(6), 0, Ui.dp(12));
            box.addView(ex);
            boolean all = true;
            for (Row r : rows(a)) {
                if (!r.ok) all = false;
                LinearLayout row = new LinearLayout(a);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, Ui.dp(8), 0, Ui.dp(8));
                LinearLayout left = new LinearLayout(a);
                left.setOrientation(LinearLayout.VERTICAL);
                left.addView(Ui.text(a, r.title, 15, th.fg, true));
                left.addView(Ui.text(a, r.why, 12, th.sub, false));
                row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                if (r.ok) {
                    row.addView(Ui.text(a, "✓ 완료", 14, th.todayStroke, true));
                } else {
                    final Runnable fx = r.fix;
                    row.addView(Ui.btn(a, th, "허용", true, v -> { if (fx != null) fx.run(); }));
                }
                box.addView(row);
            }
            TextView done = Ui.btn(a, th, all ? "확인" : "나중에 할게요", all, v -> dlg.dismiss());
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dp.setMargins(0, Ui.dp(14), 0, Ui.dp(4));
            box.addView(done, dp);
        };
        fill[0].run();
        rebuild = () -> { if (dlg.isShowing()) fill[0].run(); };
        dlg.setOnDismissListener(d -> {
            rebuild = null;
            Store.putI("perm_guide_ver", VER);
            // 필수 권한이 아직 빠져 있으면 24시간 뒤 앱을 열 때 다시 안내한다
            Store.putS("perm_next", essentialMissing(a) ? String.valueOf(System.currentTimeMillis() + 24L * 3600_000L) : "0");
            if (after != null) after.run();
        });
        dlg.show();
    }
}
