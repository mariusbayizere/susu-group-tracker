package com.susu;

/**
 * A member who has not paid in full yet, and how many pesewas they still owe this week.
 */
public record Debt(String member, long owed) {
}
