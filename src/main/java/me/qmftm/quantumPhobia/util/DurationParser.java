package me.qmftm.quantumPhobia.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {

    private static final Pattern PATTERN = Pattern.compile("(\\d+)([smhd]?)", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static Optional<Long> parseMillis(String input) {
        if (input == null || input.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(input.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0) {
            return Optional.empty();
        }
        long unitMillis = switch (matcher.group(2).toLowerCase()) {
            case "", "s" -> 1_000L;
            case "m" -> 60_000L;
            case "h" -> 3_600_000L;
            case "d" -> 86_400_000L;
            default -> -1L;
        };
        if (unitMillis < 0) {
            return Optional.empty();
        }
        return Optional.of(amount * unitMillis);
    }
}
