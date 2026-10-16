package com.susu;

import java.util.List;

/**
 * A read-only snapshot of the group, used to print the report.
 *
 * @param weeklyContribution what each member pays per week, in pesewas
 * @param currentWeek        the open week (when the cycle is complete, the week after the last one)
 * @param cycleComplete      true once every member has received the pot
 * @param members            members in joining order (this is also the payout order)
 * @param paidInFull         members who have paid in full for the current week
 * @param owing              members who still owe for the current week, with amounts
 * @param nextRecipient      who receives the next pot, or {@code null} if there is none
 * @param pot                the full pot for one week, in pesewas
 * @param payouts            payout history, oldest first
 */
public record Report(
        long weeklyContribution,
        int currentWeek,
        boolean cycleComplete,
        List<String> members,
        List<String> paidInFull,
        List<Debt> owing,
        String nextRecipient,
        long pot,
        List<Payout> payouts) {

    public Report {
        members = List.copyOf(members);
        paidInFull = List.copyOf(paidInFull);
        owing = List.copyOf(owing);
        payouts = List.copyOf(payouts);
    }
}
