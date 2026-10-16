package com.susu;

import java.util.List;

/**
 * What happened when a week was closed.
 *
 * <p>Either the week closed and {@link #payout()} says who received the pot, or the week
 * could not close and {@link #debts()} lists who still owes. A week that cannot close is a
 * normal outcome, not an error, so it is reported here instead of being thrown.
 *
 * @param week   the week that was being closed
 * @param payout the payout made, or {@code null} if the week could not close
 * @param debts  members who still owe; empty when the week closed
 */
public record CloseWeekResult(int week, Payout payout, List<Debt> debts) {

    public CloseWeekResult {
        debts = List.copyOf(debts);
    }

    static CloseWeekResult paidOut(Payout payout) {
        return new CloseWeekResult(payout.week(), payout, List.of());
    }

    static CloseWeekResult blocked(int week, List<Debt> debts) {
        return new CloseWeekResult(week, null, debts);
    }

    /** True if everyone had paid in full and the pot was paid out. */
    public boolean closed() {
        return payout != null;
    }
}
