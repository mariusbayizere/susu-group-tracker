package com.susu;

/**
 * Entry point of the Susu Group Tracker. All the work happens in {@link Cli}; this class
 * only turns its result into the process exit code.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(new Cli(System.out, System.err).run(args));
    }
}
