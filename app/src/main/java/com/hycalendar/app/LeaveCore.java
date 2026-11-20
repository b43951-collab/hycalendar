package com.hycalendar.app;

import java.time.LocalDate;
import java.util.List;

/** 근태(연차·연중·케어 등) 규칙. 안드로이드 의존성 없음. */
public final class LeaveCore {
    private LeaveCore() {}

    /** 이 연도부터 잔여 차감. 이전 기간은 기록만 한다. */
    public static final int START = 2027;

    public static final class Type {
        public final String key, label, shortLabel, pool, only;
        public final double amt;
        public final boolean off;
        Type(String key, String label, String shortLabel, String pool, double amt, boolean off, String only) {
            this.key = key; this.label = label; this.shortLabel = shortLabel;
            this.pool = pool; this.amt = amt; this.off = off; this.only = only;
        }
    }

    public static final Type[] TYPES = {
            new Type("yc", "연차", "연", "yc", 1, true, null),
            new Type("yca", "연차 오전", "연", "yc", 0.5, false, null),
            new Type("ycp", "연차 오후", "연", "yc", 0.5, false, null),
            new Type("yc2", "연차 2시간", "연", "yc", 0.25, false, null),
            new Type("yc1", "연차 1시간", "연", "yc", 0.125, false, null),
            new Type("jj", "연중", "중", "jj", 1, true, null),
            new Type("jja", "연중 오전", "중", "jj", 0.5, false, null),
            new Type("jjp", "연중 오후", "중", "jj", 0.5, false, null),
            new Type("jj2", "연중 2시간", "중", "jj", 0.25, false, null),
            new Type("jj1", "연중 1시간", "중", "jj", 0.125, false, null),
            new Type("nc", "야간케어", "야", "nc", 1, true, "C"),
            new Type("sc", "교대케어", "교", "sc", 1, true, "AB"),
            new Type("chk", "검진", "검", null, 0, true, null),
            new Type("edu", "교육", "교", null, 0, true, null),
            new Type("lt", "장기근속", "장", null, 0, true, null),
            new Type("etc", "기타휴가", "기", null, 0, true, null),
    };

    public static final String[] POOLS = {"yc", "jj", "nc", "sc"};

    public static String poolName(String pool) {
        switch (pool) {
            case "yc": return "연차";
            case "jj": return "연중";
            case "nc": return "야간케어";
            default: return "교대케어";
        }
    }

    public static Type type(String key) {
        for (Type t : TYPES) if (t.key.equals(key)) return t;
        return null;
    }

    /** 사용일이 속한 기간의 연도. 연차: 전년 12.18 ~ 금년 12.17, 그 외: 1.1 ~ 12.31 */
    public static int pyear(String pool, LocalDate d) {
        if ("yc".equals(pool)) {
            if (d.getMonthValue() == 12 && d.getDayOfMonth() >= 18) return d.getYear() + 1;
        }
        return d.getYear();
    }

    public static String periodText(String pool, int y) {
        if ("yc".equals(pool)) return (y - 1) + ".12.18 ~ " + y + ".12.17";
        return y + ".1.1 ~ " + y + ".12.31";
    }

    /** 그 날 사용 기록 키들로 하루 전체 휴무인지 판단. */
    public static boolean isOff(List<String> keys) {
        double sum = 0;
        for (String k : keys) {
            Type t = type(k);
            if (t == null) continue;
            if (t.off) return true;
            if ("yc".equals(t.pool) || "jj".equals(t.pool)) sum += t.amt;
        }
        return sum >= 1.0;
    }

    /** 그 날 근무 종류(A/B/C)에서 선택 가능한 유형인가. */
    public static boolean allowed(Type t, char shift) {
        if (shift == 'O') return false;
        return t.only == null || t.only.indexOf(shift) >= 0;
    }

    /** 하루 휴무 알약에 표시할 문구: 해당 근태명 */
    public static String offLabel(List<String> keys) {
        for (String k : keys) {
            Type t = type(k);
            if (t != null && t.off) return t.label;
        }
        double yc = 0, jj = 0;
        for (String k : keys) {
            Type t = type(k);
            if (t == null) continue;
            if ("yc".equals(t.pool)) yc += t.amt;
            if ("jj".equals(t.pool)) jj += t.amt;
        }
        if (yc >= jj && yc > 0) return "연차";
        if (jj > 0) return "연중";
        return "휴가";
    }
}
