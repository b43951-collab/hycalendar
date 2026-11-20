package com.hycalendar.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 가족 게시판: 글 + 사진(작게 줄여서 글과 함께 저장) */
final class BoardUi {
    static final int REQ_PHOTO = 14;
    private static final int MAX_PHOTOS = 3;
    private static final int MAX_BYTES = 190_000;

    private final Activity act;
    private final Theme th;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable stopper;
    private LinearLayout list;

    // 글쓰기 창 상태
    private final List<String> picked = new ArrayList<>();
    private LinearLayout pickedRow;
    private TextView pickBtn;

    BoardUi(Activity a, Theme th) { this.act = a; this.th = th; }

    private int dp(float v) { return Ui.dp(v); }

    void stop() {
        if (stopper != null) { try { stopper.run(); } catch (Throwable ignored) { } stopper = null; }
    }

    View build() {
        stop();
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(act);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(14), dp(14), dp(8));
        bar.addView(Ui.text(act, "게시판", 24, th.fg, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        boolean ready = Sync.configured() && Sync.active();
        if (ready) bar.addView(Ui.btn(act, th, "글쓰기", true, v -> compose()));
        root.addView(bar);

        ScrollView sv = new ScrollView(act);
        list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), 0, dp(14), dp(20));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (!ready) {
            TextView t = Ui.text(act, !Sync.configured() ? "서버 설정 전이에요." : "설정에서 로그인하고 가족과 연결하면 게시판을 쓸 수 있어요.", 14, th.sub, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(60), 0, 0);
            list.addView(t);
            return root;
        }
        TextView loading = Ui.text(act, "불러오는 중…", 14, th.sub, false);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, dp(60), 0, 0);
        list.addView(loading);
        stopper = Sync.listenPosts(posts -> ui.post(() -> render(posts)));
        return root;
    }

    private void render(List<Sync.Post> posts) {
        if (list == null) return;
        list.removeAllViews();
        if (posts.isEmpty()) {
            TextView t = Ui.text(act, "아직 글이 없어요. 첫 글을 남겨 보세요", 14, th.sub, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(60), 0, 0);
            list.addView(t);
            return;
        }
        String me = Sync.uid();
        SimpleDateFormat f = new SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA);
        for (final Sync.Post p : posts) {
            LinearLayout c = Ui.card(act, th);
            LinearLayout head = new LinearLayout(act);
            head.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout who = new LinearLayout(act);
            who.setOrientation(LinearLayout.VERTICAL);
            who.addView(Ui.text(act, p.n.isEmpty() ? "가족" : p.n, 15, th.fg, true));
            who.addView(Ui.text(act, f.format(new Date(p.ts)), 11, th.sub, false));
            head.addView(who, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            if (p.o.equals(me)) {
                TextView ed = Ui.text(act, "수정", 13, th.sub, true);
                ed.setPadding(dp(10), dp(6), dp(10), dp(6));
                ed.setOnClickListener(v -> editText(p));
                head.addView(ed);
                TextView del = Ui.text(act, "삭제", 13, th.sun, true);
                del.setPadding(dp(10), dp(6), dp(6), dp(6));
                del.setOnClickListener(v -> new AlertDialog.Builder(act).setTitle("글 삭제").setMessage("이 글을 삭제할까요?")
                        .setPositiveButton("삭제", (x, w) -> Sync.deletePost(p.id, e -> { if (e != null) toast(e); }))
                        .setNegativeButton("취소", null).show());
                head.addView(del);
            }
            c.addView(head);
            if (!p.t.isEmpty()) {
                TextView t = Ui.text(act, p.t, 15, th.fg, false);
                t.setPadding(0, dp(8), 0, dp(4));
                t.setTextIsSelectable(true);
                c.addView(t);
            }
            for (String b64 : p.ph) {
                final Bitmap bm = decode(b64);
                if (bm == null) continue;
                ImageView iv = new ImageView(act);
                iv.setImageBitmap(bm);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setOnClickListener(v -> showImage(bm));
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ip.setMargins(0, dp(8), 0, 0);
                c.addView(iv, ip);
            }
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, dp(6), 0, dp(6));
            list.addView(c, cp);
        }
    }

    private static Bitmap decode(String b64) {
        try {
            byte[] b = Base64.decode(b64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(b, 0, b.length);
        } catch (Throwable t) { return null; }
    }

    private void showImage(Bitmap bm) {
        final Dialog d = new Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(act);
        iv.setImageBitmap(bm);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(0xFF000000);
        iv.setOnClickListener(v -> d.dismiss());
        d.setContentView(iv);
        d.show();
    }

    private void toast(String s) { Toast.makeText(act, s, Toast.LENGTH_SHORT).show(); }

    // ---------- 글 수정 (글만) ----------
    private void editText(final Sync.Post p) {
        final EditText et = new EditText(act);
        et.setText(p.t);
        et.setMinLines(3);
        et.setGravity(Gravity.TOP);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setPadding(dp(20), dp(14), dp(20), dp(14));
        new AlertDialog.Builder(act).setTitle("글 수정").setView(et)
                .setPositiveButton("저장", (x, w) -> Sync.updatePost(p.id, et.getText().toString().trim(), e -> { if (e != null) toast(e); }))
                .setNegativeButton("취소", null).show();
    }

    // ---------- 글쓰기 ----------
    private void compose() {
        picked.clear();
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.round(th.card, 20));
        box.setPadding(dp(20), dp(18), dp(20), dp(14));
        box.addView(Ui.text(act, "새 글", 20, th.fg, true));
        final EditText et = new EditText(act);
        et.setHint("내용을 입력하세요");
        et.setHintTextColor(th.sub);
        et.setTextColor(th.fg);
        et.setTextSize(16);
        et.setMinLines(4);
        et.setGravity(Gravity.TOP);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setBackground(Ui.round(th.soft, 12));
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.setMargins(0, dp(12), 0, dp(10));
        box.addView(et, ep);

        pickedRow = new LinearLayout(act);
        box.addView(pickedRow);
        pickBtn = Ui.btn(act, th, "사진 추가 (최대 " + MAX_PHOTOS + "장)", false, v -> pickPhotos());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, dp(6), 0, dp(8));
        box.addView(pickBtn, bp);
        final TextView err = Ui.text(act, " ", 12, th.sun, false);
        box.addView(err);

        final Dialog dlg = new Dialog(act, android.R.style.Theme_DeviceDefault_NoActionBar);
        dlg.setContentView(box);
        dlg.setCanceledOnTouchOutside(false);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            dlg.getWindow().setLayout((int) (act.getResources().getDisplayMetrics().widthPixels * 0.92f), ViewGroup.LayoutParams.WRAP_CONTENT);
            dlg.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        LinearLayout row = new LinearLayout(act);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        l1.setMargins(0, 0, dp(6), 0);
        row.addView(Ui.btn(act, th, "취소", false, v -> { dlg.dismiss(); pickedRow = null; pickBtn = null; }), l1);
        final TextView ok = Ui.btn(act, th, "올리기", true, null);
        ok.setOnClickListener(v -> {
            String t = et.getText().toString().trim();
            if (t.isEmpty() && picked.isEmpty()) { err.setText("글이나 사진을 넣어 주세요"); return; }
            ok.setEnabled(false);
            err.setText("올리는 중…");
            Sync.addPost(t, new ArrayList<>(picked), e -> {
                ok.setEnabled(true);
                if (e != null) { err.setText(e); return; }
                dlg.dismiss();
                pickedRow = null; pickBtn = null;
            });
        });
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        l2.setMargins(dp(6), 0, 0, 0);
        row.addView(ok, l2);
        box.addView(row);
        dlg.show();
    }

    private void pickPhotos() {
        if (picked.size() >= MAX_PHOTOS) { toast("사진은 " + MAX_PHOTOS + "장까지예요"); return; }
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        act.startActivityForResult(Intent.createChooser(i, "사진 선택"), REQ_PHOTO);
    }

    /** MainActivity.onActivityResult 에서 호출 */
    void onPhotos(Intent data) {
        if (data == null) return;
        final List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());
        if (uris.isEmpty()) return;
        toast("사진을 줄이는 중…");
        new Thread(() -> {
            final List<String> out = new ArrayList<>();
            for (Uri u : uris) {
                if (picked.size() + out.size() >= MAX_PHOTOS) break;
                String s = compress(u);
                if (s != null) out.add(s);
            }
            ui.post(() -> {
                picked.addAll(out);
                refreshPicked();
            });
        }).start();
    }

    private void refreshPicked() {
        if (pickedRow == null) return;
        pickedRow.removeAllViews();
        for (int i = 0; i < picked.size(); i++) {
            final int ix = i;
            Bitmap bm = decode(picked.get(i));
            if (bm == null) continue;
            ImageView iv = new ImageView(act);
            iv.setImageBitmap(bm);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setOnClickListener(v -> { picked.remove(ix); refreshPicked(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(72));
            lp.setMargins(0, 0, dp(8), 0);
            pickedRow.addView(iv, lp);
        }
        if (pickBtn != null) pickBtn.setText(picked.isEmpty() ? "사진 추가 (최대 " + MAX_PHOTOS + "장)" : "사진 " + picked.size() + "장 · 눌러서 빼기 / 더 추가");
    }

    /** 가로세로 최대 1000px, 190KB 이하 JPEG로 줄여 Base64 문자열로 돌려준다 */
    private String compress(Uri u) {
        try {
            BitmapFactory.Options bo = new BitmapFactory.Options();
            bo.inJustDecodeBounds = true;
            try (InputStream in = act.getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, bo); }
            int s = 1;
            while (Math.max(bo.outWidth, bo.outHeight) / s > 2000) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            Bitmap b;
            try (InputStream in = act.getContentResolver().openInputStream(u)) { b = BitmapFactory.decodeStream(in, null, o2); }
            if (b == null) return null;
            int rot = 0;
            try (InputStream in = act.getContentResolver().openInputStream(u)) {
                int ori = new android.media.ExifInterface(in).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
                rot = ori == 6 ? 90 : ori == 3 ? 180 : ori == 8 ? 270 : 0;
            } catch (Throwable ignored) { }
            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            }
            int maxSide = 1000;
            while (true) {
                float sc = Math.min(1f, (float) maxSide / Math.max(b.getWidth(), b.getHeight()));
                Bitmap sm = sc < 1f ? Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * sc), Math.round(b.getHeight() * sc), true) : b;
                for (int q = 80; q >= 40; q -= 10) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    sm.compress(Bitmap.CompressFormat.JPEG, q, bos);
                    if (bos.size() <= MAX_BYTES) return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                }
                if (maxSide <= 500) return null;
                maxSide -= 200;
            }
        } catch (Throwable t) { return null; }
    }
}
