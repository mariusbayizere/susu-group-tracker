package com.susu;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves one {@link SusuGroup} to a plain text file and loads it back.
 *
 * <p>The file has one record per line, with fields separated by tabs. Text fields (names)
 * are URL-encoded, so a name can never contain a tab or a line break that would break the
 * format. The last line is {@code end}; a file without it was cut off and is rejected.
 *
 * <pre>
 * SUSU-GROUP-TRACKER	1
 * name	Family+Susu
 * weekly	5000
 * week	2
 * member	Ama
 * member	Kofi
 * member	Esi
 * payout	1	Ama	15000
 * paid	Kofi	5000
 * end
 * </pre>
 *
 * <p>Loading rebuilds the group by replaying it through the normal {@link SusuGroup} rules,
 * so a file describing an impossible group (an overpayment, a wrong payout order) is
 * reported as corrupt instead of being trusted.
 */
public final class Storage {

    /** Default data file. It is listed in .gitignore because it holds a user's data, not code. */
    public static final String DEFAULT_FILE = "susu-group.txt";

    private static final String HEADER = "SUSU-GROUP-TRACKER";
    private static final String FORMAT_VERSION = "1";
    private static final String SEPARATOR = "\t";

    private Storage() {
    }

    /**
     * Writes the group to {@code file} atomically: a temporary file is written first and then
     * moved over the real file in one step, so a crash never leaves a half-written file.
     */
    public static void save(SusuGroup group, Path file) {
        Path target = file.toAbsolutePath();
        Path temp = null;
        try {
            temp = Files.createTempFile(target.getParent(), "." + target.getFileName() + ".", ".tmp");
            Files.write(temp, toLines(group), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new SusuException("Could not save the group to " + file + ": " + describe(e));
        } finally {
            deleteQuietly(temp);
        }
    }

    /** Reads a group saved by {@link #save}. Every problem becomes a friendly {@link SusuException}. */
    public static SusuGroup load(Path file) {
        if (!Files.exists(file)) {
            throw new SusuException("No group found at " + file + ". Create one first with: create <weekly-amount>");
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            throw corrupt(file, "it is not a text file");
        } catch (IOException e) {
            throw new SusuException("Could not read " + file + ": " + describe(e));
        }
        try {
            return fromLines(lines);
        } catch (SusuException e) {
            throw corrupt(file, e.getMessage());
        }
    }

    private static List<String> toLines(SusuGroup group) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER + SEPARATOR + FORMAT_VERSION);
        lines.add(join("name", encode(group.getName())));
        lines.add(join("weekly", Long.toString(group.getWeeklyContribution())));
        lines.add(join("week", Integer.toString(group.getCurrentWeek())));
        for (String member : group.getMembers()) {
            lines.add(join("member", encode(member)));
        }
        for (Payout payout : group.getPayouts()) {
            lines.add(join("payout", Integer.toString(payout.week()), encode(payout.member()),
                    Long.toString(payout.amount())));
        }
        for (String member : group.getMembers()) {
            long paid = group.isCycleComplete() ? 0 : group.amountPaid(member);
            if (paid > 0) {
                lines.add(join("paid", encode(member), Long.toString(paid)));
            }
        }
        lines.add("end");
        return lines;
    }

    private static SusuGroup fromLines(List<String> lines) {
        if (lines.isEmpty() || !lines.get(0).equals(HEADER + SEPARATOR + FORMAT_VERSION)) {
            throw new SusuException("it is not a Susu Group Tracker data file");
        }
        String name = null;
        Long weekly = null;
        Integer week = null;
        List<String> members = new ArrayList<>();
        List<Payout> payouts = new ArrayList<>();
        Map<String, Long> paid = new LinkedHashMap<>();
        boolean ended = false;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (ended) {
                if (line.isBlank()) {
                    continue;
                }
                throw new SusuException("unexpected data after 'end' on line " + (i + 1));
            }
            String[] f = line.split(SEPARATOR, -1);
            String where = " on line " + (i + 1);
            switch (f[0]) {
                case "name" -> name = decode(field(f, 2, where)[1], where);
                case "weekly" -> weekly = number(field(f, 2, where)[1], where);
                case "week" -> week = weekNumber(field(f, 2, where)[1], where);
                case "member" -> members.add(decode(field(f, 2, where)[1], where));
                case "payout" -> {
                    field(f, 4, where);
                    payouts.add(new Payout(weekNumber(f[1], where), decode(f[2], where), number(f[3], where)));
                }
                case "paid" -> {
                    field(f, 3, where);
                    paid.put(decode(f[1], where), number(f[2], where));
                }
                case "end" -> ended = true;
                default -> throw new SusuException("unknown record '" + f[0] + "'" + where);
            }
        }
        if (!ended || name == null || weekly == null || week == null) {
            throw new SusuException("the file is incomplete (it may have been cut off while saving)");
        }
        return rebuild(name, weekly, week, members, payouts, paid);
    }

    /** Replays the saved state through the normal rules, so impossible data is rejected. */
    private static SusuGroup rebuild(String name, long weekly, int week, List<String> members,
                                     List<Payout> payouts, Map<String, Long> paid) {
        SusuGroup group = new SusuGroup(name, weekly);
        for (String member : members) {
            group.addMember(member);
        }
        for (Payout expected : payouts) {
            for (String member : group.getMembers()) {
                group.recordContribution(member, weekly, group.getCurrentWeek());
            }
            Payout actual = group.closeWeek().payout();
            if (!expected.equals(actual)) {
                throw new SusuException("payout history does not match the member order (week "
                        + expected.week() + ")");
            }
        }
        if (group.getCurrentWeek() != week) {
            throw new SusuException("current week " + week + " does not match the payout history");
        }
        for (Map.Entry<String, Long> entry : paid.entrySet()) {
            group.recordContribution(entry.getKey(), entry.getValue(), week);
        }
        return group;
    }

    private static String[] field(String[] fields, int expected, String where) {
        if (fields.length != expected) {
            throw new SusuException("record '" + fields[0] + "' has the wrong number of fields" + where);
        }
        return fields;
    }

    /** Parses a whole number of zero or more, such as pesewas. */
    private static long number(String text, String where) {
        try {
            long value = Long.parseLong(text);
            if (value >= 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new SusuException("'" + text + "' is not a valid number" + where);
    }

    /** Parses a week number. */
    private static int weekNumber(String text, String where) {
        long value = number(text, where);
        if (value > Integer.MAX_VALUE) {
            throw new SusuException("'" + text + "' is not a valid week" + where);
        }
        return (int) value;
    }

    private static String join(String... fields) {
        return String.join(SEPARATOR, fields);
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static String decode(String text, String where) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new SusuException("badly encoded name" + where);
        }
    }

    private static SusuException corrupt(Path file, String reason) {
        return new SusuException("The data file " + file + " is damaged: " + reason
                + ". Restore a backup or create a new group with: create <weekly-amount> --force");
    }

    private static String describe(IOException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static void deleteQuietly(Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
            // The temp file is only left behind if the disk fails; it never replaces real data.
        }
    }
}
