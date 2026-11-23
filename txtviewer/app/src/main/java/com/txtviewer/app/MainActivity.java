package com.txtviewer.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

public class MainActivity extends Activity {
    private static final int REQ_OPEN = 1;
    private static final int MAX_BYTES = 30 * 1024 * 1024;

    private SharedPreferences sp;
    private String[] lines = new String[0];
    private Uri curUri;
    private String curName = "";
    private String curCharset = "";
    private float textSp;
    private boolean dark;

    private LinearLayout root, bar;
    private TextView title, info;
    private ListView list;
    private SeekBar seek;
    private BaseAdapter adapter;
    private boolean seeking;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("txt", MODE_PRIVATE);
        textSp = sp.getFloat("size", 18f);
        dark = sp.getBoolean("dark", false);
        build();
        applyTheme();
        if (!handleIntent(getIntent())) {
            String last = sp.getString("uri", null);
            if (last != null) open(Uri.parse(last), sp.getInt("pos_" + last.hashCode(), 0));
            else showHelp();
        }
        checkUpdate(false);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleIntent(i);
    }

    private boolean handleIntent(Intent i) {
        if (i != null && Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) {
            Uri u = i.getData();
            open(u, sp.getInt("pos_" + u.toString().hashCode(), 0));
            return true;
        }
        return false;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private Button btn(String t, View.OnClickListener l) {
        Button x = new Button(this);
        x.setText(t);
        x.setAllCaps(false);
        x.setTextSize(14);
        x.setMinWidth(0);
        x.setMinimumWidth(0);
        x.setPadding(dp(10), 0, dp(10), 0);
        x.setOnClickListener(l);
        return x;
    }

    private void build() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(24), dp(8), dp(4));
        bar.addView(btn("열기", v -> pick()));
        bar.addView(btn("A-", v -> setSize(textSp - 1)));
        bar.addView(btn("A+", v -> setSize(textSp + 1)));
        bar.addView(btn("밝기", v -> { dark = !dark; sp.edit().putBoolean("dark", dark).apply(); applyTheme(); }));
        bar.addView(btn("업데이트", v -> checkUpdate(true)));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        title = new TextView(this);
        title.setTextSize(13);
        title.setSingleLine(true);
        title.setPadding(dp(14), dp(4), dp(14), dp(4));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(true);
        list.setFastScrollEnabled(true);
        adapter = new BaseAdapter() {
            public int getCount() { return lines.length; }
            public Object getItem(int p) { return lines[p]; }
            public long getItemId(int p) { return p; }
            public View getView(int p, View c, ViewGroup g) {
                TextView t = c instanceof TextView ? (TextView) c : new TextView(MainActivity.this);
                t.setPadding(dp(14), dp(2), dp(14), dp(2));
                t.setTextSize(textSp);
                t.setLineSpacing(0, 1.35f);
                t.setTextColor(dark ? Color.rgb(0xd8, 0xd8, 0xd8) : Color.rgb(0x22, 0x22, 0x22));
                t.setText(lines[p].isEmpty() ? " " : lines[p]);
                return t;
            }
        };
        list.setAdapter(adapter);
        list.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            public void onScrollStateChanged(android.widget.AbsListView v, int s) { if (s == SCROLL_STATE_IDLE) savePos(); }
            public void onScroll(android.widget.AbsListView v, int f, int vc, int tc) {
                if (!seeking && tc > 1) { seek.setProgress((int) (f * 1000L / (tc - 1))); updateInfo(); }
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setPadding(dp(8), dp(2), dp(8), dp(10));
        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && lines.length > 1) { list.setSelection((int) (p * (long) (lines.length - 1) / 1000)); updateInfo(); }
            }
            public void onStartTrackingTouch(SeekBar s) { seeking = true; }
            public void onStopTrackingTouch(SeekBar s) { seeking = false; savePos(); }
        });
        foot.addView(seek, new LinearLayout.LayoutParams(-1, -2));
        info = new TextView(this);
        info.setTextSize(12);
        info.setGravity(Gravity.CENTER);
        foot.addView(info, new LinearLayout.LayoutParams(-1, -2));
        root.addView(foot, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private void applyTheme() {
        int bg = dark ? Color.rgb(0x12, 0x12, 0x12) : Color.rgb(0xfa, 0xf6, 0xea);
        int fg = dark ? Color.rgb(0xaa, 0xaa, 0xaa) : Color.rgb(0x66, 0x66, 0x66);
        root.setBackgroundColor(bg);
        title.setTextColor(fg);
        info.setTextColor(fg);
        adapter.notifyDataSetChanged();
    }

    private void setSize(float s) {
        textSp = Math.max(10f, Math.min(40f, s));
        sp.edit().putFloat("size", textSp).apply();
        int pos = list.getFirstVisiblePosition();
        adapter.notifyDataSetChanged();
        list.setSelection(pos);
    }

    private void showHelp() {
        lines = new String[]{"", "상단의 [열기]를 눌러 TXT 파일을 선택하세요.", "",
                "· 파일 앱에서 TXT를 열 때 이 앱을 선택해도 됩니다.",
                "· 읽던 위치는 파일별로 자동 저장됩니다.",
                "· UTF-8 / 한글(EUC-KR·CP949) 자동 인식"};
        title.setText("");
        adapter.notifyDataSetChanged();
        updateInfo();
    }

    private void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/*", "application/octet-stream"});
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_OPEN);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (req == REQ_OPEN && res == RESULT_OK && d != null && d.getData() != null) {
            Uri u = d.getData();
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
            open(u, sp.getInt("pos_" + u.toString().hashCode(), 0));
        }
    }

    private void open(Uri u, int pos) {
        title.setText("불러오는 중…");
        new Thread(() -> {
            String err = null;
            String[] ls = null;
            String cs = "";
            String name = u.getLastPathSegment() == null ? "" : u.getLastPathSegment();
            try {
                try (android.database.Cursor c = getContentResolver().query(u, null, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (ix >= 0) name = c.getString(ix);
                    }
                } catch (Exception ignored) {}
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(u)) {
                    if (in == null) throw new Exception("파일을 열 수 없음");
                    byte[] buf = new byte[65536]; int n;
                    while ((n = in.read(buf)) > 0) {
                        bo.write(buf, 0, n);
                        if (bo.size() > MAX_BYTES) throw new Exception("파일이 너무 큼 (30MB 초과)");
                    }
                }
                byte[] data = bo.toByteArray();
                Object[] r = decode(data);
                cs = (String) r[1];
                ls = ((String) r[0]).split("\r\n|\n|\r", -1);
            } catch (Exception e) { err = e.getMessage(); }
            final String fe = err, fn = name, fc = cs;
            final String[] fl = ls;
            runOnUiThread(() -> {
                if (fe != null) {
                    Toast.makeText(this, "열지 못했어요: " + fe, Toast.LENGTH_LONG).show();
                    title.setText("");
                    return;
                }
                lines = fl; curUri = u; curName = fn; curCharset = fc;
                sp.edit().putString("uri", u.toString()).apply();
                title.setText(fn + "  (" + fc + ")");
                adapter.notifyDataSetChanged();
                list.setSelection(Math.min(pos, Math.max(0, lines.length - 1)));
                updateInfo();
            });
        }).start();
    }

    /** BOM → 엄격한 UTF-8 → 한글(CP949) 순으로 인식 */
    private static Object[] decode(byte[] d) throws Exception {
        if (d.length >= 3 && (d[0] & 0xff) == 0xEF && (d[1] & 0xff) == 0xBB && (d[2] & 0xff) == 0xBF)
            return new Object[]{new String(d, 3, d.length - 3, "UTF-8"), "UTF-8"};
        if (d.length >= 2 && (d[0] & 0xff) == 0xFF && (d[1] & 0xff) == 0xFE)
            return new Object[]{new String(d, 2, d.length - 2, "UTF-16LE"), "UTF-16LE"};
        if (d.length >= 2 && (d[0] & 0xff) == 0xFE && (d[1] & 0xff) == 0xFF)
            return new Object[]{new String(d, 2, d.length - 2, "UTF-16BE"), "UTF-16BE"};
        try {
            String s = Charset.forName("UTF-8").newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(d)).toString();
            return new Object[]{s, "UTF-8"};
        } catch (CharacterCodingException e) {
            Charset k;
            try { k = Charset.forName("x-windows-949"); } catch (Exception x) { k = Charset.forName("EUC-KR"); }
            return new Object[]{new String(d, k), "CP949"};
        }
    }

    private void updateInfo() {
        if (lines.length == 0) { info.setText(""); return; }
        int p = Math.min(lines.length, list.getFirstVisiblePosition() + 1);
        info.setText(p + " / " + lines.length + "줄  ·  " + (int) (p * 100L / lines.length) + "%");
    }

    private void savePos() {
        if (curUri != null) sp.edit().putInt("pos_" + curUri.toString().hashCode(), list.getFirstVisiblePosition()).apply();
    }

    @Override
    protected void onPause() { savePos(); super.onPause(); }

    private void checkUpdate(boolean manual) {
        Updater.check(this, (newer, ver, url, err) -> {
            if (isFinishing()) return;
            if (err != null) { if (manual) Toast.makeText(this, err, Toast.LENGTH_LONG).show(); return; }
            if (newer) Updater.promptAndInstall(this, ver, url);
            else if (manual) Toast.makeText(this, "최신 버전이에요 (v" + Updater.currentName(this) + ")", Toast.LENGTH_SHORT).show();
        });
    }
}
