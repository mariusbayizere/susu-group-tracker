package com.susu;

/**
 * Thrown when a request breaks one of the susu rules (unknown member, bad amount, ...).
 *
 * <p>The message is written for the person using the program, so the command line can
 * print it as it is instead of showing a stack trace.
 */
public class SusuException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SusuException(String message) {
        super(message);
    }
}
