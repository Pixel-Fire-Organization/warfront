package org.pixelfire.nationwars.state;

/**
 * The optional {@code warWindowStart}/{@code warWindowEnd} time-of-day restriction on declarations.
 * Either config value empty disables the restriction entirely.
 */
public final class WarWindow
{
    private WarWindow()
    {
    }

    public static boolean isWithin(final String start, final String end, final int currentMinuteOfDay)
    {
        if (start.isBlank() || end.isBlank())
        {
            return true;
        }
        final int startMinute = parseMinuteOfDay(start);
        final int endMinute = parseMinuteOfDay(end);
        if (startMinute <= endMinute)
        {
            return currentMinuteOfDay >= startMinute && currentMinuteOfDay < endMinute;
        }
        // Wraps past midnight, e.g. 22:00-06:00.
        return currentMinuteOfDay >= startMinute || currentMinuteOfDay < endMinute;
    }

    private static int parseMinuteOfDay(final String hhmm)
    {
        final int colon = hhmm.indexOf(':');
        final int hour = Integer.parseInt(hhmm.substring(0, colon));
        final int minute = Integer.parseInt(hhmm.substring(colon + 1));
        return hour * 60 + minute;
    }
}
