package com.hycalendar.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 하이닉스 4조3교대(6근 2휴) 근무 계산. 안드로이드 의존성 없음. */
public final class ShiftCore {
    private ShiftCore() {}

    /** A 6일, 휴 2일, B 6일, 휴 2일, C 6일, 휴 2일 = 24일 주기 */
    private static final char[] SEQ = "AAAAAAOOBBBBBBOOCCCCCCOO".toCharArray();
    /** 1조가 이 날짜에 A 첫째 날이다. */
    private static final LocalDate ANCHOR = LocalDate.of(2027, 1, 10);

    /** group: 1~4. 반환: 'A','B','C','O'(휴) */
    public static char typeFor(int group, LocalDate d) {
        if (group == 5) { // 통상근무: 월~금(공휴일 제외) 'N', 그 외 'X'
            int w = d.getDayOfWeek().getValue();
            return (w <= 5 && Holidays.name(d).isEmpty()) ? 'N' : 'X';
        }
        long diff = ChronoUnit.DAYS.between(ANCHOR, d) + (group - 1) * 6L;
        return SEQ[(int) Math.floorMod(diff, 24L)];
    }

    /** 그 날이 근무(또는 휴) 구간의 몇 번째 날인지: 근무 1~6, 휴 1~2 */
    public static int dayInBlock(int group, LocalDate d) {
        if (group == 5) return 1;
        long diff = ChronoUnit.DAYS.between(ANCHOR, d) + (group - 1) * 6L;
        return (int) (Math.floorMod(diff, 24L) % 8) + 1;
    }
}
