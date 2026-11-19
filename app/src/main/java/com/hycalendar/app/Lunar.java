package com.hycalendar.app;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 한국 음력 (천문 계산, KST). 안드로이드 의존성 없음. */
public final class Lunar {
    private Lunar() {}

    public static final class Res { public int m, d; public boolean leap; }
    private static final class Month { long s, e; int L = -1, n; boolean leap, guess; }

    private static final double D2R = Math.PI / 180;
    private static double sinD(double x) { return Math.sin(x * D2R); }

    private static double newMoonJDE(double k) {
        double T = k / 1236.85, T2 = T * T, T3 = T2 * T, T4 = T3 * T;
        double J = 2451550.09766 + 29.530588861 * k + 0.00015437 * T2 - 0.00000015 * T3 + 0.00000000073 * T4;
        double E = 1 - 0.002516 * T - 0.0000074 * T2;
        double M = 2.5534 + 29.1053567 * k - 0.0000014 * T2 - 0.00000011 * T3;
        double Mp = 201.5643 + 385.81693528 * k + 0.0107582 * T2 + 0.00001238 * T3 - 0.000000058 * T4;
        double F = 160.7108 + 390.67050284 * k - 0.0016118 * T2 - 0.00000227 * T3 + 0.000000011 * T4;
        double O = 124.7746 - 1.56375588 * k + 0.0020672 * T2 + 0.00000215 * T3;
        double c = -0.4072 * sinD(Mp) + 0.17241 * E * sinD(M) + 0.01608 * sinD(2 * Mp) + 0.01039 * sinD(2 * F) + 0.00739 * E * sinD(Mp - M)
                - 0.00514 * E * sinD(Mp + M) + 0.00208 * E * E * sinD(2 * M) - 0.00111 * sinD(Mp - 2 * F) - 0.00057 * sinD(Mp + 2 * F) + 0.00056 * E * sinD(2 * Mp + M)
                - 0.00042 * sinD(3 * Mp) + 0.00042 * E * sinD(M + 2 * F) + 0.00038 * E * sinD(M - 2 * F) - 0.00024 * E * sinD(2 * Mp - M) - 0.00017 * sinD(O)
                - 0.00007 * sinD(Mp + 2 * M) + 0.00004 * sinD(2 * Mp - 2 * F) + 0.00004 * sinD(3 * M) + 0.00003 * sinD(Mp + M - 2 * F) + 0.00003 * sinD(2 * Mp + 2 * F)
                - 0.00003 * sinD(Mp + M + 2 * F) + 0.00003 * sinD(Mp - M + 2 * F) - 0.00002 * sinD(Mp - M - 2 * F) - 0.00002 * sinD(3 * Mp + M) + 0.00002 * sinD(4 * Mp);
        double[] A = {299.77 + 0.107408 * k - 0.009173 * T2, 251.88 + 0.016321 * k, 251.83 + 26.651886 * k, 349.42 + 36.412478 * k, 84.66 + 18.206239 * k,
                141.74 + 53.303771 * k, 207.14 + 2.453732 * k, 154.84 + 7.30686 * k, 34.52 + 27.261239 * k, 207.19 + 0.121824 * k, 291.34 + 1.844379 * k,
                161.72 + 24.198154 * k, 239.56 + 25.513099 * k, 331.55 + 3.592518 * k};
        double[] w = {0.000325, 0.000165, 0.000164, 0.000126, 0.00011, 0.000062, 0.00006, 0.000056, 0.000047, 0.000042, 0.00004, 0.000037, 0.000035, 0.000023};
        double p = 0;
        for (int i = 0; i < 14; i++) p += w[i] * sinD(A[i]);
        return J + c + p;
    }

    private static double deltaT(int year) { double t = year - 2000; return 62.92 + 0.32217 * t + 0.005589 * t * t; }

    private static long jdeToJdnKST(double jde, int year) {
        double ut = jde - deltaT(year) / 86400.0;
        return (long) Math.floor(ut + 0.5 + 9.0 / 24);
    }

    private static double sunLongJDE(double jde) {
        double T = (jde - 2451545) / 36525;
        double L0 = 280.46646 + 36000.76983 * T + 0.0003032 * T * T;
        double M = 357.52911 + 35999.05029 * T - 0.0001537 * T * T;
        double C = (1.914602 - 0.004817 * T - 0.000014 * T * T) * sinD(M) + (0.019993 - 0.000101 * T) * sinD(2 * M) + 0.000289 * sinD(3 * M);
        double l = L0 + C - 0.00569 - 0.00478 * sinD(125.04 - 1934.136 * T);
        return ((l % 360) + 360) % 360;
    }

    private static double termJDE(double target, double approx) {
        double j = approx;
        for (int i = 0; i < 8; i++) {
            double d = target - sunLongJDE(j);
            d = ((d + 180) % 360 + 360) % 360 - 180;
            j += d / 0.9856;
        }
        return j;
    }

    static long jdn(int y, int m, int d) {
        int a = (14 - m) / 12;
        long yy = y + 4800 - a, mm = m + 12 * a - 3;
        return d + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045;
    }

    private static final Map<Integer, List<Month>> CACHE = new HashMap<>();

    private static synchronized List<Month> monthsAround(int y) {
        List<Month> cached = CACHE.get(y);
        if (cached != null) return cached;
        long k0 = (long) Math.floor((y - 1 + (11.0 / 12) - 2000) * 12.3685) - 1;
        List<Long> nm = new ArrayList<>();
        for (long k = k0; k < k0 + 19; k++) nm.add(jdeToJdnKST(newMoonJDE(k), y));
        List<int[]> terms = new ArrayList<>(); // {L, jdn}
        for (int yy = y - 1; yy <= y + 1; yy++) {
            for (int L = 0; L < 360; L += 30) {
                double approx = jdn(yy, 3, 20) - 0.5 + L / 360.0 * 365.2422;
                double jde = termJDE(L, approx);
                terms.add(new int[]{L, (int) jdeToJdnKST(jde, yy)});
            }
        }
        List<Month> months = new ArrayList<>();
        for (int i = 0; i < nm.size() - 1; i++) {
            Month m = new Month();
            m.s = nm.get(i);
            m.e = nm.get(i + 1) - 1;
            for (int[] t : terms) {
                if (t[1] >= m.s && t[1] <= m.e) { m.L = t[0]; break; }
            }
            months.add(m);
        }
        int idx = -1;
        for (int i = 0; i < months.size(); i++) if (months.get(i).L >= 0) { idx = i; break; }
        months.get(idx).n = num(months.get(idx).L);
        for (int i = idx + 1; i < months.size(); i++) {
            Month m = months.get(i);
            if (m.L >= 0) { m.n = num(m.L); m.leap = false; }
            else { m.n = months.get(i - 1).n; m.leap = true; }
        }
        for (int i = idx - 1; i >= 0; i--) {
            Month m = months.get(i);
            if (m.L >= 0) { m.n = num(m.L); m.leap = false; }
            else { m.n = months.get(i + 1).n == 1 ? 12 : months.get(i + 1).n - 1; m.leap = false; m.guess = true; }
        }
        CACHE.put(y, months);
        return months;
    }

    private static int num(int L) { int n = L / 30 + 2; if (n > 12) n -= 12; return n; }

    private static Month find(List<Month> ms, long jdn, boolean skipGuess) {
        for (Month m : ms) if (jdn >= m.s && jdn <= m.e && !(skipGuess && m.guess)) return m;
        return null;
    }

    public static Res of(LocalDate date) {
        long j = jdn(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        Month mo = find(monthsAround(date.getYear()), j, false);
        if (mo == null || mo.guess) {
            for (int yy : new int[]{date.getYear() - 1, date.getYear() + 1}) {
                Month mm = find(monthsAround(yy), j, true);
                if (mm != null) { mo = mm; break; }
            }
        }
        Res r = new Res();
        if (mo == null) return r;
        r.m = mo.n; r.d = (int) (j - mo.s + 1); r.leap = mo.leap;
        return r;
    }
}
