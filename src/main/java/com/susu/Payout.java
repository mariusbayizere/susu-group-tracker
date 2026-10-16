package com.susu;

/**
 * One line of payout history: in {@code week}, {@code member} received {@code amount} pesewas.
 */
public record Payout(int week, String member, long amount) {
}
