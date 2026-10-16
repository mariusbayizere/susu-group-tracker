package com.susu;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The command line: reads the arguments, runs one command against the saved group, prints
 * the outcome and returns an exit code.
 *
 * <p>Output and error streams are passed in, so tests can capture what is printed.
 */
public class Cli {

    /** The command worked. */
    public static final int OK = 0;
    /** close-week ran, but the week could not close because someone still owes. */
    public static final int WEEK_NOT_CLOSED = 1;
    /** Invalid input or a broken rule; nothing was changed. */
    public static final int ERROR = 2;

    static final String USAGE = String.join(System.lineSeparator(),
            "Usage: java -jar target/susu-group-tracker.jar <command> [options]",
            "",
            "Commands:",
            "  create <weekly-amount> [--name NAME] [--force]   start a new group (amount in GHS, e.g. 50.00)",
            "  add-member <name>                               add a member (only before the first payment)",
            "  pay <member> <amount> [--week N]                record a payment (default: current week)",
            "  close-week                                      pay the pot if everyone has paid in full",
            "  report                                          show the week, payments, debts and payouts",
            "  help                                            show this message",
            "",
            "Options:",
            "  --file PATH   data file to use (default: " + Storage.DEFAULT_FILE + ")",
            "",
            "Names with spaces must be quoted, e.g. add-member \"Kwame Mensah\".",
            "Exit codes: 0 success, 1 week could not close, 2 invalid input or broken rule.");

    private final PrintStream out;
    private final PrintStream err;

    public Cli(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    /** Runs one command and returns its exit code. Never throws and never prints a stack trace. */
    public int run(String... args) {
        try {
            Arguments arguments = Arguments.parse(args);
            return dispatch(arguments);
        } catch (SusuException e) {
            err.println("Error: " + e.getMessage());
            return ERROR;
        } catch (RuntimeException e) {
            err.println("Error: something unexpected went wrong (" + e + "). Nothing was saved.");
            return ERROR;
        }
    }

    private int dispatch(Arguments a) {
        switch (a.command) {
            case "help", "--help", "-h" -> {
                out.println(USAGE);
                return OK;
            }
            case "create" -> {
                return create(a);
            }
            case "add-member" -> {
                return addMember(a);
            }
            case "pay" -> {
                return pay(a);
            }
            case "close-week" -> {
                return closeWeek(a);
            }
            case "report" -> {
                return report(a);
            }
            default -> throw new SusuException("Unknown command '" + a.command + "'. Run 'help' to see the commands.");
        }
    }

    private int create(Arguments a) {
        a.expectPositionals(1, "create <weekly-amount> [--name NAME] [--force]");
        a.allowOptions("--name", "--force");
        long weekly = Money.parse(a.positionals.get(0));
        SusuGroup group = new SusuGroup(a.name, weekly);
        if (Files.exists(a.file) && !a.force) {
            throw new SusuException(a.file + " already exists. Use --force to replace it, "
                    + "or --file to choose another file.");
        }
        Storage.save(group, a.file);
        out.println("Created group '" + group.getName() + "' with a weekly contribution of GHS "
                + Money.format(weekly) + " (saved to " + a.file + ").");
        out.println("Next: add members with add-member <name>.");
        return OK;
    }

    private int addMember(Arguments a) {
        a.expectPositionals(1, "add-member <name>   (put names with spaces in quotes)");
        a.allowOptions();
        SusuGroup group = Storage.load(a.file);
        String name = group.addMember(a.positionals.get(0));
        Storage.save(group, a.file);
        out.println("Added " + name + " (position " + group.getMembers().size() + " in the payout order).");
        return OK;
    }

    private int pay(Arguments a) {
        a.expectPositionals(2, "pay <member> <amount> [--week N]");
        a.allowOptions("--week");
        SusuGroup group = Storage.load(a.file);
        long amount = Money.parse(a.positionals.get(1));
        int week = a.week != null ? a.week : group.getCurrentWeek();
        long stillOwed = group.recordContribution(a.positionals.get(0), amount, week);
        Storage.save(group, a.file);
        String member = findMember(group, a.positionals.get(0));
        out.print("Recorded GHS " + Money.format(amount) + " from " + member + " for week " + week + ". ");
        out.println(stillOwed == 0
                ? member + " has paid in full."
                : member + " still owes " + Money.format(stillOwed) + ".");
        return OK;
    }

    private int closeWeek(Arguments a) {
        a.expectPositionals(0, "close-week");
        a.allowOptions();
        SusuGroup group = Storage.load(a.file);
        CloseWeekResult result = group.closeWeek();
        if (!result.closed()) {
            out.println("Week " + result.week() + " cannot close. Nothing was paid out.");
            for (Debt debt : result.debts()) {
                out.println("  " + debt.member() + " owes " + Money.format(debt.owed()));
            }
            return WEEK_NOT_CLOSED;
        }
        Storage.save(group, a.file);
        Payout payout = result.payout();
        out.println("Week " + payout.week() + " closed. " + payout.member() + " receives GHS "
                + Money.format(payout.amount()) + ".");
        if (group.isCycleComplete()) {
            out.println("Cycle complete: every member has received the pot once.");
        } else {
            out.println("Week " + group.getCurrentWeek() + " begins. " + group.nextRecipient() + " is next.");
        }
        return OK;
    }

    private int report(Arguments a) {
        a.expectPositionals(0, "report");
        a.allowOptions();
        Report r = Storage.load(a.file).report();
        out.println("Group: " + r.name());
        out.println("Weekly contribution: GHS " + Money.format(r.weeklyContribution())
                + " x " + r.members().size() + " members = pot GHS " + Money.format(r.pot()));
        out.println("Members (payout order): " + (r.members().isEmpty() ? "(none yet)" : String.join(", ", r.members())));
        out.println();
        if (r.cycleComplete()) {
            out.println("Cycle complete: every member has received the pot once.");
        } else {
            out.println("Current week: " + r.currentWeek()
                    + (r.nextRecipient() == null ? "" : " (pot goes to " + r.nextRecipient() + ")"));
            out.println("Paid in full: " + (r.paidInFull().isEmpty() ? "(nobody yet)" : String.join(", ", r.paidInFull())));
            if (r.owing().isEmpty()) {
                out.println("Still owing: (nobody)");
            } else {
                out.println("Still owing:");
                for (Debt debt : r.owing()) {
                    out.println("  " + debt.member() + " owes " + Money.format(debt.owed()));
                }
            }
        }
        out.println();
        if (r.payouts().isEmpty()) {
            out.println("Payout history: (none yet)");
        } else {
            out.println("Payout history:");
            for (Payout p : r.payouts()) {
                out.println("  Week " + p.week() + ": " + p.member() + " received GHS " + Money.format(p.amount()));
            }
        }
        return OK;
    }

    /** The member's name as stored, so output shows "Esi" even if the user typed "esi". */
    private static String findMember(SusuGroup group, String typed) {
        String wanted = typed.trim().replaceAll("\\s+", " ");
        return group.getMembers().stream()
                .filter(m -> m.equalsIgnoreCase(wanted))
                .findFirst()
                .orElse(wanted);
    }

    /** The parsed command line: the command, its positional arguments and the options. */
    private static final class Arguments {
        String command;
        final List<String> positionals = new ArrayList<>();
        final List<String> optionsSeen = new ArrayList<>();
        Path file = Paths.get(Storage.DEFAULT_FILE);
        String name;
        Integer week;
        boolean force;

        static Arguments parse(String[] args) {
            Arguments a = new Arguments();
            if (args == null || args.length == 0) {
                throw new SusuException("No command given." + System.lineSeparator() + USAGE);
            }
            a.command = args[0];
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--file" -> a.file = Paths.get(value(args, ++i, arg));
                    case "--name" -> a.name = value(args, ++i, arg);
                    case "--week" -> a.week = parseWeek(value(args, ++i, arg));
                    case "--force" -> a.force = true;
                    default -> {
                        if (arg.startsWith("--")) {
                            throw new SusuException("Unknown option '" + arg + "'. Run 'help' to see the options.");
                        }
                        a.positionals.add(arg);
                        continue;
                    }
                }
                a.optionsSeen.add(arg);
            }
            return a;
        }

        void expectPositionals(int count, String usage) {
            if (positionals.size() != count) {
                throw new SusuException("Wrong number of arguments. Usage: " + usage);
            }
        }

        /** Rejects options that do not belong to this command; --file is always allowed. */
        void allowOptions(String... allowed) {
            List<String> ok = new ArrayList<>(List.of(allowed));
            ok.add("--file");
            List<String> wrong = optionsSeen.stream().filter(o -> !ok.contains(o)).distinct()
                    .collect(Collectors.toList());
            if (!wrong.isEmpty()) {
                throw new SusuException("Option " + String.join(", ", wrong) + " cannot be used with " + command + ".");
            }
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new SusuException("Option " + option + " needs a value.");
            }
            return args[index];
        }

        private static int parseWeek(String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                throw new SusuException("Week must be a whole number, not '" + text + "'.");
            }
        }
    }
}
