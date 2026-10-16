package com.susu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class SusuGroupTest {

    private static final long FIFTY = Money.parse("50.00");

    private SusuGroup group;

    @Before
    public void setUp() {
        group = new SusuGroup(FIFTY);
        group.addMember("Ama");
        group.addMember("Kofi");
        group.addMember("Esi");
    }

    private void payEveryoneInFull() {
        for (String member : group.getMembers()) {
            group.recordContribution(member, FIFTY, group.getCurrentWeek());
        }
    }

    private static void assertMessageContains(SusuException e, String text) {
        assertTrue("message was: " + e.getMessage(), e.getMessage().contains(text));
    }

    @Test
    public void challengeExampleAmaKofiEsi() {
        group.recordContribution("Ama", Money.parse("50"), 1);
        group.recordContribution("Kofi", Money.parse("50"), 1);
        group.recordContribution("Esi", Money.parse("30"), 1);

        CloseWeekResult first = group.closeWeek();
        assertFalse(first.closed());
        assertEquals(List.of(new Debt("Esi", Money.parse("20.00"))), first.debts());
        assertEquals("20.00", Money.format(first.debts().get(0).owed()));
        assertEquals(1, group.getCurrentWeek());
        assertTrue(group.getPayouts().isEmpty());

        group.recordContribution("Esi", Money.parse("20"), 1);
        CloseWeekResult second = group.closeWeek();
        assertTrue(second.closed());
        assertEquals(new Payout(1, "Ama", Money.parse("150.00")), second.payout());
        assertEquals(2, group.getCurrentWeek());
        assertEquals("Kofi", group.nextRecipient());
    }

    @Test
    public void partialPaymentsAddUpAcrossSeveralPayments() {
        assertEquals(Money.parse("40"), group.recordContribution("Esi", Money.parse("10"), 1));
        assertEquals(Money.parse("15.50"), group.recordContribution("Esi", Money.parse("24.50"), 1));
        assertEquals(0L, group.recordContribution("Esi", Money.parse("15.50"), 1));
        assertEquals(FIFTY, group.amountPaid("Esi"));
    }

    @Test
    public void weekCannotCloseWhileSomeoneOwesAndNothingIsPaidOut() {
        group.recordContribution("Ama", FIFTY, 1);
        group.recordContribution("Kofi", Money.parse("49.99"), 1);

        CloseWeekResult result = group.closeWeek();

        assertFalse(result.closed());
        assertNull(result.payout());
        assertEquals(1, result.week());
        assertEquals(List.of(new Debt("Kofi", 1L), new Debt("Esi", FIFTY)), result.debts());
        assertEquals(1, group.getCurrentWeek());
        assertTrue(group.getPayouts().isEmpty());
        assertEquals(Money.parse("49.99"), group.amountPaid("Kofi"));
    }

    @Test
    public void weekCannotCloseWithNoPayments() {
        CloseWeekResult result = group.closeWeek();
        assertFalse(result.closed());
        assertEquals(3, result.debts().size());
        assertEquals(1, group.getCurrentWeek());
    }

    @Test
    public void overpaymentIsRejected() {
        SusuException tooMuch = assertThrows(SusuException.class,
                () -> group.recordContribution("Ama", Money.parse("50.01"), 1));
        assertMessageContains(tooMuch, "only owes 50.00");

        group.recordContribution("Ama", Money.parse("30"), 1);
        SusuException rest = assertThrows(SusuException.class,
                () -> group.recordContribution("Ama", Money.parse("25"), 1));
        assertMessageContains(rest, "only owes 20.00");
        assertEquals(Money.parse("30"), group.amountPaid("Ama"));
    }

    @Test
    public void memberWhoPaidInFullCannotPayAgain() {
        group.recordContribution("Ama", FIFTY, 1);
        SusuException e = assertThrows(SusuException.class,
                () -> group.recordContribution("Ama", 1L, 1));
        assertMessageContains(e, "already paid in full");
    }

    @Test
    public void unknownMemberIsRejected() {
        SusuException e = assertThrows(SusuException.class,
                () -> group.recordContribution("Yaw", FIFTY, 1));
        assertMessageContains(e, "Unknown member 'Yaw'");
    }

    @Test
    public void duplicateMemberIsRejectedIgnoringCaseAndSpaces() {
        assertThrows(SusuException.class, () -> group.addMember("Ama"));
        assertThrows(SusuException.class, () -> group.addMember("  aMA  "));
        group.addMember("Kwame  Mensah");
        assertThrows(SusuException.class, () -> group.addMember("kwame mensah"));
        assertEquals(List.of("Ama", "Kofi", "Esi", "Kwame Mensah"), group.getMembers());
    }

    @Test
    public void memberNamesAreMatchedIgnoringCaseAndSpacesWhenPaying() {
        group.recordContribution("  esi ", Money.parse("5"), 1);
        assertEquals(Money.parse("5"), group.amountPaid("ESI"));
    }

    @Test
    public void emptyMemberNameIsRejected() {
        assertThrows(SusuException.class, () -> group.addMember("   "));
        assertThrows(SusuException.class, () -> group.addMember(null));
    }

    @Test
    public void zeroAndNegativeAmountsAreRejected() {
        SusuException zero = assertThrows(SusuException.class,
                () -> group.recordContribution("Ama", 0L, 1));
        assertMessageContains(zero, "greater than zero");
        assertThrows(SusuException.class,
                () -> group.recordContribution("Ama", Money.parse("-5"), 1));
        assertEquals(0L, group.amountPaid("Ama"));
    }

    @Test
    public void weeklyContributionMustBeGreaterThanZero() {
        assertThrows(SusuException.class, () -> new SusuGroup(0));
        assertThrows(SusuException.class, () -> new SusuGroup(Money.parse("-50")));
    }

    @Test
    public void lateMemberIsRejectedAfterFirstPayment() {
        group.recordContribution("Ama", Money.parse("10"), 1);
        SusuException e = assertThrows(SusuException.class, () -> group.addMember("Yaw"));
        assertMessageContains(e, "already started");
    }

    @Test
    public void lateMemberIsRejectedAfterFirstClosedWeek() {
        payEveryoneInFull();
        group.closeWeek();
        assertThrows(SusuException.class, () -> group.addMember("Yaw"));
    }

    @Test
    public void groupNeedsAtLeastTwoMembers() {
        SusuGroup small = new SusuGroup(FIFTY);
        assertThrows(SusuException.class, small::closeWeek);
        small.addMember("Ama");
        SusuException pay = assertThrows(SusuException.class,
                () -> small.recordContribution("Ama", FIFTY, 1));
        assertMessageContains(pay, "at least 2 members");
        assertThrows(SusuException.class, small::closeWeek);
    }

    @Test
    public void onlyTheCurrentWeekAcceptsPayments() {
        SusuException future = assertThrows(SusuException.class,
                () -> group.recordContribution("Ama", FIFTY, 2));
        assertMessageContains(future, "has not started");
        assertThrows(SusuException.class, () -> group.recordContribution("Ama", FIFTY, 0));

        payEveryoneInFull();
        group.closeWeek();
        SusuException past = assertThrows(SusuException.class,
                () -> group.recordContribution("Ama", FIFTY, 1));
        assertMessageContains(past, "already closed");
    }

    @Test
    public void whoOwesListsCurrentDebtsAndClosedWeeksOweNothing() {
        group.recordContribution("Kofi", Money.parse("20"), 1);
        assertEquals(List.of(new Debt("Ama", FIFTY), new Debt("Kofi", Money.parse("30")), new Debt("Esi", FIFTY)),
                group.whoOwes(1));
        assertThrows(SusuException.class, () -> group.whoOwes(2));

        group.recordContribution("Kofi", Money.parse("30"), 1);
        group.recordContribution("Ama", FIFTY, 1);
        group.recordContribution("Esi", FIFTY, 1);
        group.closeWeek();
        assertTrue(group.whoOwes(1).isEmpty());
        assertEquals(3, group.whoOwes(2).size());
    }

    @Test
    public void fullRotationPaysEachMemberOnceInJoiningOrderThenCompletes() {
        for (int week = 1; week <= 3; week++) {
            payEveryoneInFull();
            assertTrue(group.closeWeek().closed());
        }

        assertEquals(List.of(
                new Payout(1, "Ama", Money.parse("150")),
                new Payout(2, "Kofi", Money.parse("150")),
                new Payout(3, "Esi", Money.parse("150"))), group.getPayouts());
        assertTrue(group.isCycleComplete());
        assertNull(group.nextRecipient());

        SusuException pay = assertThrows(SusuException.class,
                () -> group.recordContribution("Ama", FIFTY, 4));
        assertMessageContains(pay, "cycle is complete");
        assertThrows(SusuException.class, group::closeWeek);
        assertThrows(SusuException.class, () -> group.addMember("Yaw"));
    }

    @Test
    public void reportShowsWeekPaidInFullDebtsAndHistory() {
        payEveryoneInFull();
        group.closeWeek();
        group.recordContribution("Kofi", FIFTY, 2);
        group.recordContribution("Esi", Money.parse("12.50"), 2);

        Report report = group.report();

        assertEquals(2, report.currentWeek());
        assertFalse(report.cycleComplete());
        assertEquals(List.of("Kofi"), report.paidInFull());
        assertEquals(List.of(new Debt("Ama", FIFTY), new Debt("Esi", Money.parse("37.50"))), report.owing());
        assertEquals("Kofi", report.nextRecipient());
        assertEquals(Money.parse("150"), report.pot());
        assertEquals(List.of(new Payout(1, "Ama", Money.parse("150"))), report.payouts());
    }
}
