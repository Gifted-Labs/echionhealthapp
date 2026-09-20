package com.giftedlabs.echoinhealthbackend.util;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Parses enum values supplied as request text into a readable error when they don't match.
 *
 * <p>Multipart endpoints receive enum-valued fields as plain strings and were calling
 * {@code Enum.valueOf} directly. A typo therefore surfaced to the user as
 * {@code No enum constant com.giftedlabs.echoinhealthbackend.entity.SharingLevel.EVERYBODY} — a
 * Java class name where an explanation should be.
 */
public final class EnumParser {

    private EnumParser() {
    }

    /**
     * @param type      enum class to parse into
     * @param rawValue  caller-supplied text, case-insensitive, surrounding whitespace ignored
     * @param fieldName name to use in the error message, as the caller spells it in the request
     * @return the matching constant
     * @throws IllegalArgumentException with the accepted values listed, mapped to HTTP 400
     */
    public static <E extends Enum<E>> E parse(Class<E> type, String rawValue, String fieldName) {
        String normalized = rawValue == null ? "" : rawValue.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " is required. Accepted values: " + accepted(type));
        }

        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "'%s' is not a valid %s. Accepted values: %s",
                        normalized, fieldName, accepted(type))));
    }

    /**
     * Same as {@link #parse}, but treats absent or blank input as "not supplied" rather than an
     * error. Used for optional fields that fall back to a default.
     */
    public static <E extends Enum<E>> E parseOptional(Class<E> type, String rawValue, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return parse(type, rawValue, fieldName);
    }

    private static <E extends Enum<E>> String accepted(Class<E> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}
