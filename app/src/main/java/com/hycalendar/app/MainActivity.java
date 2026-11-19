package com.hycalendar.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;

import java.time.LocalDate;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(new MonthView(this));
    }

    static class MonthView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final LocalDate today = LocalDate.now();

        MonthView(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            cv.drawColor(Color.WHITE);
            LocalDate first = today.withDayOfMonth(1);
            int offset = first.getDayOfWeek().getValue() % 7; // 일요일 시작
            int days = first.lengthOfMonth();
            int rows = (offset + days + 6) / 7;
            float top = h * 0.08f;
            float cw = w / 7f, ch = (h - top) / rows;
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Color.BLACK);
            p.setTextSize(cw * 0.35f);
            cv.drawText(today.getYear() + "." + today.getMonthValue(), w / 2f, top * 0.7f, p);
            for (int d = 1; d <= days; d++) {
                int idx = offset + d - 1;
                float x = (idx % 7) * cw, y = top + (idx / 7) * ch;
                LocalDate date = first.withDayOfMonth(d);
                if (date.equals(today)) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(5);
                    p.setColor(Color.BLUE);
                    cv.drawRect(x + 3, y + 3, x + cw - 3, y + ch - 3, p);
                    p.setStyle(Paint.Style.FILL);
                }
                p.setColor(idx % 7 == 0 ? Color.RED : Color.BLACK);
                p.setTextSize(cw * 0.25f);
                cv.drawText(String.valueOf(d), x + cw / 2, y + ch * 0.28f, p);
                p.setTextSize(cw * 0.22f);
                p.setColor(Color.rgb(0, 100, 200));
                cv.drawText("나 " + label(ShiftCore.typeFor(1, date)), x + cw / 2, y + ch * 0.58f, p);
                p.setColor(Color.rgb(200, 80, 0));
                cv.drawText("배 " + label(ShiftCore.typeFor(4, date)), x + cw / 2, y + ch * 0.85f, p);
            }
        }

        private String label(char t) { return t == 'O' ? "휴" : String.valueOf(t); }
    }
}
