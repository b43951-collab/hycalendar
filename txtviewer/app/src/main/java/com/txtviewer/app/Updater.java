package com.txtviewer.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GitHub Releases "latest"를 확인해서 새 버전을 내려받아 설치한다 */
final class Updater {
    private Updater() {}

    static int dp(Activity a, int v) { return (int) (v * a.getResources().getDisplayMetrics().density); }

    private static final String API = "https://api.github.com/repos/b43951-collab/hycalendar/releases/tags/txtviewer-latest";
    private static final Handler UI = new Handler(Looper.getMainLooper());

    interface Result { void run(boolean newer, String ver, String url, String err); }

    static int currentCode(Activity a) {
        try {
            PackageInfo p = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            return (int) p.getLongVersionCode();
        } catch (Exception e) { return 0; }
    }

    static String currentName(Activity a) {
        try { return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    static void check(Activity a, Result cb) {
        final int cur = currentCode(a);
        new Thread(() -> {
            String err = null, url = null, ver = "";
            boolean newer = false;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                if (c.getResponseCode() != 200) throw new Exception("서버 응답 " + c.getResponseCode());
                StringBuilder sb = new StringBuilder();
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[4096]; int n;
                    while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
                }
                JSONObject o = new JSONObject(sb.toString());
                String body = o.optString("body");
                Matcher m = Pattern.compile("code (\\d+)").matcher(body);
                int code = m.find() ? Integer.parseInt(m.group(1)) : 0;
                Matcher vm = Pattern.compile("v(\\d+(?:\\.\\d+)*)").matcher(body);
                if (vm.find()) ver = vm.group(1);
                JSONArray as = o.optJSONArray("assets");
                if (as != null) for (int i = 0; i < as.length(); i++) {
                    JSONObject x = as.getJSONObject(i);
                    if (x.optString("name").endsWith(".apk")) url = x.optString("browser_download_url");
                }
                newer = code > cur && url != null;
            } catch (Exception e) {
                err = "확인하지 못했어요 (" + e.getMessage() + ")";
            }
            final boolean fn = newer; final String fv = ver, fu = url, fe = err;
            UI.post(() -> cb.run(fn, fv, fu, fe));
        }).start();
    }

    /** 새 버전 안내 → 확인하면 다운로드 후 설치 */
    static void promptAndInstall(Activity a, String ver, String url) {
        new AlertDialog.Builder(a)
                .setTitle("새 버전이 있어요")
                .setMessage((ver.isEmpty() ? "" : "v" + ver + " 버전이 ") + "나왔어요. 지금 업데이트할까요?\n내 데이터는 그대로 유지돼요.")
                .setPositiveButton("업데이트", (d, w) -> download(a, url))
                .setNegativeButton("나중에", null)
                .show();
    }

    private static void download(Activity a, String url) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(a, "'이 앱에서 설치 허용'을 켠 뒤 다시 눌러 주세요", Toast.LENGTH_LONG).show();
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            return;
        }
        final TextView tv = new TextView(a);
        tv.setPadding(dp(a, 24), dp(a, 20), dp(a, 24), dp(a, 8));
        tv.setTextSize(16);
        tv.setText("내려받는 중… 0%");
        final AlertDialog dlg = new AlertDialog.Builder(a).setTitle("업데이트").setView(tv).setCancelable(false).show();
        new Thread(() -> {
            String err = null;
            File out = null;
            try {
                File dir = new File(a.getExternalCacheDir() != null ? a.getExternalCacheDir() : a.getCacheDir(), "update");
                dir.mkdirs();
                out = new File(dir, "txtviewer.apk");
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                int total = c.getContentLength();
                try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(out)) {
                    byte[] buf = new byte[16384]; int n; long got = 0; int last = -1;
                    while ((n = in.read(buf)) > 0) {
                        fo.write(buf, 0, n);
                        got += n;
                        if (total > 0) {
                            final int pct = (int) (got * 100 / total);
                            if (pct != last) { last = pct; UI.post(() -> tv.setText("내려받는 중… " + pct + "%")); }
                        }
                    }
                }
            } catch (Exception e) { err = e.getMessage(); }
            final String fe = err; final File fo = out;
            UI.post(() -> {
                dlg.dismiss();
                if (fe != null || fo == null) { Toast.makeText(a, "내려받지 못했어요 (" + fe + ")", Toast.LENGTH_LONG).show(); return; }
                try {
                    Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fp", fo);
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(uri, "application/vnd.android.package-archive");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    a.startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(a, "설치 화면을 열지 못했어요 (" + e.getMessage() + ")", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}
