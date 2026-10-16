package com.susu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One rotating savings group (susu).
 *
 * <p>Every member pays the same weekly contribution. When a week closes with everyone paid
 * in full, the whole pot goes to the next member in joining order. After each member has
 * received the pot once, the cycle is complete.
 *
 * <p>All amounts are {@code long} pesewas. Any request that breaks a rule throws a
 * {@link SusuException} with a message meant for the user.
 */
public class SusuGroup {

    /** The smallest group that makes sense: one member pays, another receives. */
    public static final int MIN_MEMBERS = 2;

    /** Name used when the group is created without one. */
    public static final String DEFAULT_NAME = "Susu Group";

    private final String name;

    private final long weeklyContribution;

    /** Lookup key (lower case, single spaces) to the name as the member typed it, in joining order. */
    private final Map<String, String> members = new LinkedHashMap<>();

    /** Pesewas paid so far in the current week, by lookup key. Members who paid nothing are absent. */
    private final Map<String, Long> paidThisWeek = new LinkedHashMap<>();

    private final List<Payout> payouts = new ArrayList<>();

    private int currentWeek = 1;

    /**
     * Creates a group called {@value #DEFAULT_NAME} in week 1 with no members.
     *
     * @param weeklyContribution what each member pays per week, in pesewas; must be above zero
     */
    public SusuGroup(long weeklyContribution) {
        this(DEFAULT_NAME, weeklyContribution);
    }

    /**
     * Creates a named group in week 1 with no members.
     *
     * @param name               a label for the group; blank means {@value #DEFAULT_NAME}
     * @param weeklyContribution what each member pays per week, in pesewas; must be above zero
     */
    public SusuGroup(String name, long weeklyContribution) {
        if (weeklyContribution <= 0) {
            throw new SusuException("The weekly contribution must be greater than zero.");
        }
        String cleaned = cleanName(name);
        this.name = cleaned.isEmpty() ? DEFAULT_NAME : cleaned;
        this.weeklyContribution = weeklyContribution;
    }

    /**
     * Adds a member at the end of the payout order and returns their name as stored.
     *
     * <p>Extra spaces are removed. Names are compared ignoring case, so "ama" and " AMA "
     * count as the same member. Members can only join before the group has started.
     */
    public String addMember(String name) {
        String displayName = cleanName(name);
        if (displayName.isEmpty()) {
            throw new SusuException("Member name cannot be empty.");
        }
        if (hasStarted()) {
            throw new SusuException("Cannot add '" + displayName + "': the group has already started. "
                    + "Members can only join before the first payment, so the payout order and pot stay fair.");
        }
        String key = keyOf(displayName);
        if (members.containsKey(key)) {
            throw new SusuException("Member '" + members.get(key) + "' already exists.");
        }
        try {
            Math.multiplyExact(weeklyContribution, members.size() + 1L);
        } catch (ArithmeticException e) {
            throw new SusuException("Cannot add '" + displayName + "': the weekly pot would be too large.");
        }
        members.put(key, displayName);
        return displayName;
    }

    /**
     * Records a payment by a member for a week. Partial payments add up.
     *
     * @param member name of an existing member (case and extra spaces are ignored)
     * @param amount pesewas paid; must be above zero and not more than the member still owes
     * @param week   must be the current week
     * @return pesewas the member still owes for the week after this payment
     */
    public long recordContribution(String member, long amount, int week) {
        requireCycleOpen();
        requireEnoughMembers();
        String key = requireMember(member);
        String name = members.get(key);
        if (amount <= 0) {
            throw new SusuException("Amount must be greater than zero.");
        }
        requireCurrentWeek(week);

        long owed = owedBy(key);
        if (owed == 0) {
            throw new SusuException(name + " has already paid in full for week " + week + ".");
        }
        if (amount > owed) {
            throw new SusuException(name + " only owes " + Money.format(owed) + " for week " + week
                    + ", so " + Money.format(amount) + " is too much.");
        }
        paidThisWeek.merge(key, amount, Long::sum);
        return owed - amount;
    }

    /**
     * Lists members who have not paid in full for a week, in joining order.
     *
     * <p>Closed weeks always return an empty list, because a week only closes when everyone
     * has paid. Weeks that have not started yet are rejected.
     */
    public List<Debt> whoOwes(int week) {
        int lastWeek = isCycleComplete() ? currentWeek - 1 : currentWeek;
        if (week < 1) {
            throw new SusuException("Week must be 1 or more.");
        }
        if (week > lastWeek) {
            throw new SusuException("Week " + week + " has not started. The current week is " + currentWeek + ".");
        }
        if (week < currentWeek) {
            return List.of();
        }
        List<Debt> debts = new ArrayList<>();
        for (Map.Entry<String, String> entry : members.entrySet()) {
            long owed = owedBy(entry.getKey());
            if (owed > 0) {
                debts.add(new Debt(entry.getValue(), owed));
            }
        }
        return debts;
    }

    /**
     * Tries to close the current week.
     *
     * <p>If everyone has paid in full, the pot goes to the next member in joining order and
     * the next week starts. Otherwise nothing is paid, the week stays open, and the result
     * lists who still owes. Only rule violations (cycle complete, too few members) throw.
     */
    public CloseWeekResult closeWeek() {
        requireCycleOpen();
        requireEnoughMembers();
        List<Debt> debts = whoOwes(currentWeek);
        if (!debts.isEmpty()) {
            return CloseWeekResult.blocked(currentWeek, debts);
        }
        Payout payout = new Payout(currentWeek, nextRecipient(), pot());
        payouts.add(payout);
        paidThisWeek.clear();
        currentWeek++;
        return CloseWeekResult.paidOut(payout);
    }

    /** A snapshot of the current state for printing. */
    public Report report() {
        List<String> paidInFull = new ArrayList<>();
        List<Debt> owing = new ArrayList<>();
        if (!isCycleComplete()) {
            for (Map.Entry<String, String> entry : members.entrySet()) {
                long owed = owedBy(entry.getKey());
                if (owed == 0) {
                    paidInFull.add(entry.getValue());
                } else {
                    owing.add(new Debt(entry.getValue(), owed));
                }
            }
        }
        return new Report(name, weeklyContribution, currentWeek, isCycleComplete(), getMembers(),
                paidInFull, owing, nextRecipient(), pot(), payouts);
    }

    /** True once the group has taken a payment or closed a week; members can no longer join. */
    public boolean hasStarted() {
        return currentWeek > 1 || !paidThisWeek.isEmpty();
    }

    /** True once every member has received the pot exactly once. */
    public boolean isCycleComplete() {
        return members.size() >= MIN_MEMBERS && payouts.size() == members.size();
    }

    /** Pesewas the member has paid so far in the current week. */
    public long amountPaid(String member) {
        return paidThisWeek.getOrDefault(requireMember(member), 0L);
    }

    public String getName() {
        return name;
    }

    public long getWeeklyContribution() {
        return weeklyContribution;
    }

    public int getCurrentWeek() {
        return currentWeek;
    }

    /** Member names in joining order, which is also the payout order. */
    public List<String> getMembers() {
        return List.copyOf(members.values());
    }

    /** Payout history, oldest first. */
    public List<Payout> getPayouts() {
        return Collections.unmodifiableList(payouts);
    }

    /** The full pot for one week: weekly contribution times the number of members. */
    public long pot() {
        return weeklyContribution * members.size();
    }

    /** Who receives the next pot, or {@code null} if there are no members or the cycle is complete. */
    public String nextRecipient() {
        if (payouts.size() >= members.size()) {
            return null;
        }
        return new ArrayList<>(members.values()).get(payouts.size());
    }

    private long owedBy(String key) {
        return weeklyContribution - paidThisWeek.getOrDefault(key, 0L);
    }

    private String requireMember(String name) {
        String key = keyOf(cleanName(name));
        if (!members.containsKey(key)) {
            throw new SusuException("Unknown member '" + cleanName(name) + "'. Add them first with add-member.");
        }
        return key;
    }

    private void requireEnoughMembers() {
        if (members.size() < MIN_MEMBERS) {
            throw new SusuException("The group needs at least " + MIN_MEMBERS + " members before it can take "
                    + "payments or close a week. It has " + members.size() + ".");
        }
    }

    private void requireCycleOpen() {
        if (isCycleComplete()) {
            throw new SusuException("The cycle is complete: every member has received the pot once.");
        }
    }

    private void requireCurrentWeek(int week) {
        if (week < 1) {
            throw new SusuException("Week must be 1 or more.");
        }
        if (week < currentWeek) {
            throw new SusuException("Week " + week + " is already closed. The current week is " + currentWeek + ".");
        }
        if (week > currentWeek) {
            throw new SusuException("Week " + week + " has not started. The current week is " + currentWeek + ".");
        }
    }

    /** Trims the name and turns any run of spaces, tabs or new lines into one space. */
    private static String cleanName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    private static String keyOf(String cleanName) {
        return cleanName.toLowerCase(Locale.ROOT);
    }
}
