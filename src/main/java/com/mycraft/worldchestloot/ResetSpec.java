package com.mycraft.worldchestloot;

import org.bukkit.configuration.ConfigurationSection;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ResetSpec {
    private static final Pattern DURATION_PART = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)([dhms])",
            Pattern.CASE_INSENSITIVE);

    private enum Kind { DURATION, DAILY, WEEKLY, MONTHLY, COMPOSITE }

    private final Kind kind;
    private final long durationSeconds;
    private final LocalTime time;
    private final int calendarValue;
    private final List<ResetSpec> schedules;

    private ResetSpec(Kind kind, long durationSeconds, LocalTime time, int calendarValue,
                      List<ResetSpec> schedules) {
        this.kind = kind;
        this.durationSeconds = durationSeconds;
        this.time = time;
        this.calendarValue = calendarValue;
        this.schedules = schedules;
    }

    static ResetSpec duration(long seconds) {
        return new ResetSpec(Kind.DURATION, seconds, null, 0, new ArrayList<ResetSpec>());
    }

    static ResetSpec parse(Object value) {
        if (value instanceof ConfigurationSection) return parseSection((ConfigurationSection) value);
        if (value instanceof Map) return parseMap((Map<?, ?>) value);
        if (value instanceof List) return parseList((List<?>) value);
        if (value instanceof Number) return duration(((Number) value).longValue());
        if (value == null) return null;
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return null;
        if (text.equals("-1s")) return duration(-1);
        if (text.contains(";")) return parseFixed(text);
        return parseDuration(text);
    }

    private static ResetSpec parseList(List<?> values) {
        Map<String, ResetSpec> unique = new LinkedHashMap<>();
        for (Object value : values) {
            ResetSpec parsed = parse(value);
            if (parsed == null || parsed.kind == Kind.COMPOSITE) return null;
            unique.put(String.valueOf(parsed.serialize()), parsed);
        }
        if (unique.isEmpty()) return null;
        return new ResetSpec(Kind.COMPOSITE, 0, null, 0, new ArrayList<>(unique.values()));
    }

    private static ResetSpec parseSection(ConfigurationSection section) {
        return fromParts(section.getDouble("Days"), section.getDouble("Hours"),
                section.getDouble("Minutes"), section.getDouble("Seconds"));
    }

    private static ResetSpec parseMap(Map<?, ?> map) {
        return fromParts(number(map.get("Days")), number(map.get("Hours")),
                number(map.get("Minutes")), number(map.get("Seconds")));
    }

    private static ResetSpec fromParts(double days, double hours, double minutes, double seconds) {
        if (days < 0 || hours < 0 || minutes < 0 || seconds < 0) return duration(-1);
        return duration(Math.round(days * 86400D + hours * 3600D + minutes * 60D + seconds));
    }

    private static double number(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        try { return value == null ? 0 : Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static ResetSpec parseDuration(String text) {
        double seconds = 0;
        String[] parts = text.split("\\+");
        for (String part : parts) {
            Matcher matcher = DURATION_PART.matcher(part.trim());
            if (!matcher.matches()) return null;
            double amount;
            try { amount = Double.parseDouble(matcher.group(1)); }
            catch (NumberFormatException ex) { return null; }
            char unit = Character.toLowerCase(matcher.group(2).charAt(0));
            if (unit == 'd') seconds += amount * 86400D;
            else if (unit == 'h') seconds += amount * 3600D;
            else if (unit == 'm') seconds += amount * 60D;
            else seconds += amount;
        }
        if (seconds > Long.MAX_VALUE) return null;
        return duration(Math.round(seconds));
    }

    private static ResetSpec parseFixed(String text) {
        String[] halves = text.split(";", -1);
        if (halves.length != 2) return null;
        LocalTime time = parseTime(halves[1]);
        if (time == null) return null;
        String[] type = halves[0].split(",", -1);
        if (type.length == 1 && type[0].equals("daily")) {
            return fixed(Kind.DAILY, time, 0);
        }
        if (type.length != 2) return null;
        int value;
        try { value = Integer.parseInt(type[1].trim()); }
        catch (NumberFormatException ex) { return null; }
        if (type[0].equals("weekly") && value >= 1 && value <= 7) {
            return fixed(Kind.WEEKLY, time, value);
        }
        if (type[0].equals("monthly") && value >= 1 && value <= 31) {
            return fixed(Kind.MONTHLY, time, value);
        }
        return null;
    }

    private static ResetSpec fixed(Kind kind, LocalTime time, int calendarValue) {
        return new ResetSpec(kind, 0, time, calendarValue, new ArrayList<ResetSpec>());
    }

    private static LocalTime parseTime(String text) {
        String[] parts = text.trim().split(":", -1);
        if (parts.length != 3) return null;
        try {
            return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    boolean isDisabled() {
        if (kind == Kind.COMPOSITE) {
            for (ResetSpec schedule : schedules) if (!schedule.isDisabled()) return false;
            return true;
        }
        return kind == Kind.DURATION && durationSeconds == 0;
    }

    boolean isFixed() {
        return kind != Kind.DURATION;
    }

    int editorSeconds() {
        if (kind != Kind.DURATION || durationSeconds < 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, durationSeconds);
    }

    long nextResetAt(long now, boolean roundDownTime) {
        if (kind == Kind.COMPOSITE) {
            long next = Long.MAX_VALUE;
            for (ResetSpec schedule : schedules) {
                next = Math.min(next, schedule.nextResetAt(now, roundDownTime));
            }
            return next;
        }
        if (kind == Kind.DURATION) {
            if (durationSeconds < 0) return Long.MAX_VALUE;
            long start = roundDownTime ? roundDownStart(now, durationSeconds) : now;
            long millis;
            try { millis = Math.multiplyExact(durationSeconds, 1000L); }
            catch (ArithmeticException ex) { return Long.MAX_VALUE; }
            return Long.MAX_VALUE - start < millis ? Long.MAX_VALUE : start + millis;
        }

        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime current = Instant.ofEpochMilli(now).atZone(zone);
        if (kind == Kind.DAILY) {
            ZonedDateTime candidate = current.toLocalDate().atTime(time).atZone(zone);
            if (!candidate.isAfter(current)) candidate = candidate.plusDays(1);
            return candidate.toInstant().toEpochMilli();
        }
        if (kind == Kind.WEEKLY) {
            LocalDate today = current.toLocalDate();
            for (int offset = 0; offset <= 7; offset++) {
                LocalDate date = today.plusDays(offset);
                if (date.getDayOfWeek().getValue() != calendarValue) continue;
                ZonedDateTime candidate = date.atTime(time).atZone(zone);
                if (candidate.isAfter(current)) return candidate.toInstant().toEpochMilli();
            }
        }
        if (kind == Kind.MONTHLY) {
            YearMonth month = YearMonth.from(current);
            for (int offset = 0; offset < 1200; offset++) {
                YearMonth candidateMonth = month.plusMonths(offset);
                if (calendarValue > candidateMonth.lengthOfMonth()) continue;
                ZonedDateTime candidate = candidateMonth.atDay(calendarValue).atTime(time).atZone(zone);
                if (candidate.isAfter(current)) return candidate.toInstant().toEpochMilli();
            }
        }
        return Long.MAX_VALUE;
    }

    private static long roundDownStart(long now, long seconds) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        if (seconds > 0 && seconds % 60 == 0) {
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            if (seconds % 3600 == 0) {
                calendar.set(Calendar.MINUTE, 0);
                if (seconds % 86400 == 0) calendar.set(Calendar.HOUR_OF_DAY, 0);
            }
        }
        return calendar.getTimeInMillis();
    }

    Object serialize() {
        if (kind == Kind.COMPOSITE) {
            List<String> values = new ArrayList<>();
            for (ResetSpec schedule : schedules) values.add(String.valueOf(schedule.serialize()));
            return values;
        }
        if (kind == Kind.DAILY) return "daily;" + formatTime();
        if (kind == Kind.WEEKLY) return "weekly," + calendarValue + ";" + formatTime();
        if (kind == Kind.MONTHLY) return "monthly," + calendarValue + ";" + formatTime();
        if (durationSeconds < 0) return "-1s";
        if (durationSeconds == 0) return "0s";
        long remaining = durationSeconds;
        StringBuilder value = new StringBuilder();
        long days = remaining / 86400;
        remaining %= 86400;
        long hours = remaining / 3600;
        remaining %= 3600;
        long minutes = remaining / 60;
        long seconds = remaining % 60;
        if (days > 0) value.append(days).append('d');
        if (hours > 0) append(value, hours + "h");
        if (minutes > 0) append(value, minutes + "m");
        if (seconds > 0) append(value, seconds + "s");
        return value.toString();
    }

    private String formatTime() {
        return time.getHour() + ":" + time.getMinute() + ":" + time.getSecond();
    }

    private static void append(StringBuilder value, String part) {
        if (value.length() > 0) value.append('+');
        value.append(part);
    }
}
