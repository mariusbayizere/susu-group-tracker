package com.susu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MoneyTest {

    @Test
    public void parsesWholeAndDecimalAmountsIntoPesewas() {
        assertEquals(5000L, Money.parse("50"));
        assertEquals(5000L, Money.parse("50.00"));
        assertEquals(5050L, Money.parse("50.5"));
        assertEquals(1L, Money.parse("0.01"));
        assertEquals(2000L, Money.parse("  20.00  "));
        assertEquals(-300L, Money.parse("-3"));
    }

    @Test
    public void formatsPesewasWithTwoDecimals() {
        assertEquals("50.00", Money.format(5000));
        assertEquals("0.05", Money.format(5));
        assertEquals("150.00", Money.format(15000));
        assertEquals("-20.00", Money.format(-2000));
    }

    @Test
    public void tenPesewasPlusTwentyPesewasIsExactlyThirtyPesewas() {
        // With double, 0.10 + 0.20 is 0.30000000000000004. With pesewas it is exact.
        long sum = Money.parse("0.10") + Money.parse("0.20");
        assertEquals(Money.parse("0.30"), sum);
        assertEquals("0.30", Money.format(sum));
    }

    @Test
    public void rejectsJunkAndSpecialValues() {
        String[] bad = {"", "   ", "abc", "NaN", "Infinity", "1e3", "1,000", "50.", ".5", "GHS 50", "5 0", "--5"};
        for (String text : bad) {
            assertThrows("should reject '" + text + "'", SusuException.class, () -> Money.parse(text));
        }
        assertThrows(SusuException.class, () -> Money.parse(null));
    }

    @Test
    public void rejectsMoreThanTwoDecimalPlaces() {
        SusuException e = assertThrows(SusuException.class, () -> Money.parse("10.005"));
        assertTrue(e.getMessage().contains("more than 2 decimal places"));
    }

    @Test
    public void rejectsAmountsTooLargeForALong() {
        assertThrows(SusuException.class, () -> Money.parse("999999999999999999999"));
    }
}
