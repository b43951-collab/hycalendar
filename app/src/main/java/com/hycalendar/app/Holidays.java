package com.hycalendar.app;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 법정공휴일 + 대체공휴일. 임시공휴일은 포함하지 않음. */
public final class Holidays {
    private Holidays() {}

    private static final Map<Integer, Map<LocalDate, String>> CACHE = new HashMap<>();
    private static final Map<Integer, Map<String, List<LocalDate>>> LMAP = new HashMap<>();

    private static final class Group {
        String name; List<LocalDate> dates; boolean subst;
        Group(String n, boolean s, LocalDate... d) { name = n; subst = s; dates = new ArrayList<>(Arrays.asList(d)); }
    }

    private static synchronized List<LocalDate> lunarToSolar(int y, int m, int d) {
        Map<String, List<LocalDate>> mp = LMAP.get(y);
        if (mp == null) {
            mp = new HashMap<>();
            LocalDate s = LocalDate.of(y, 1, 1), e = LocalDate.of(y + 1, 1, 1);
            for (LocalDate x = s; x.isBefore(e); x = x.plusDays(1)) {
                Lunar.Res r = Lunar.of(x);
                if (!r.leap) {
                    String k = r.m + "-" + r.d;
                    List<LocalDate> l = mp.get(k);
                    if (l == null) { l = new ArrayList<>(); mp.put(k, l); }
                    l.add(x);
                }
            }
            LMAP.put(y, mp);
        }
        List<LocalDate> l = mp.get(m + "-" + d);
        return l == null ? new ArrayList<LocalDate>() : l;
    }

    private static boolean isSun(LocalDate d) { return d.getDayOfWeek() == DayOfWeek.SUNDAY; }
    private static boolean isWeekend(LocalDate d) { return d.getDayOfWeek() == DayOfWeek.SUNDAY || d.getDayOfWeek() == DayOfWeek.SATURDAY; }

    public static synchronized Map<LocalDate, String> of(int y) {
        Map<LocalDate, String> H = CACHE.get(y);
        if (H != null) return H;
        List<Group> groups = new ArrayList<>();
        groups.add(new Group("신정", false, LocalDate.of(y, 1, 1)));
        List<LocalDate> sl = lunarToSolar(y, 1, 1);
        if (!sl.isEmpty()) groups.add(new Group("설날", true, sl.get(0).minusDays(1), sl.get(0), sl.get(0).plusDays(1)));
        groups.add(new Group("삼일절", true, LocalDate.of(y, 3, 1)));
        groups.add(new Group("어린이날", true, LocalDate.of(y, 5, 5)));
        List<LocalDate> bd = lunarToSolar(y, 4, 8);
        if (!bd.isEmpty()) groups.add(new Group("석가탄신일", true, bd.get(0)));
        groups.add(new Group("현충일", false, LocalDate.of(y, 6, 6)));
        groups.add(new Group("광복절", true, LocalDate.of(y, 8, 15)));
        List<LocalDate> cs = lunarToSolar(y, 8, 15);
        if (!cs.isEmpty()) groups.add(new Group("추석", true, cs.get(0).minusDays(1), cs.get(0), cs.get(0).plusDays(1)));
        groups.add(new Group("개천절", true, LocalDate.of(y, 10, 3)));
        groups.add(new Group("한글날", true, LocalDate.of(y, 10, 9)));
        groups.add(new Group("성탄절", true, LocalDate.of(y, 12, 25)));
        if (y == 2026) groups.add(new Group("지방선거", false, LocalDate.of(2026, 6, 3)));

        H = new HashMap<>();
        for (Group g : groups) for (LocalDate d : g.dates) put(H, d, g.name);

        List<Group> sub = new ArrayList<>();
        for (Group g : groups) if (g.subst) sub.add(g);
        sub.sort((a, b) -> a.dates.get(0).compareTo(b.dates.get(0)));
        for (Group g : sub) {
            boolean multi = g.dates.size() > 1;
            boolean clash = false;
            for (LocalDate d : g.dates) for (Group o : groups) if (o != g && o.dates.contains(d)) clash = true;
            boolean hit = clash;
            for (LocalDate d : g.dates) if (multi ? isSun(d) : isWeekend(d)) hit = true;
            if (hit) {
                LocalDate d = g.dates.get(g.dates.size() - 1).plusDays(1);
                while (H.containsKey(d) || isWeekend(d)) d = d.plusDays(1);
                put(H, d, "대체공휴일");
            }
        }
        CACHE.put(y, H);
        return H;
    }

    private static void put(Map<LocalDate, String> H, LocalDate d, String n) {
        String o = H.get(d);
        H.put(d, o == null ? n : o + "·" + n);
    }

    public static String name(LocalDate d) {
        String s = of(d.getYear()).get(d);
        return s == null ? "" : s;
    }
}
