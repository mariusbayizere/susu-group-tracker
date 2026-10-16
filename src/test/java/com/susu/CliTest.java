package com.susu;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CliTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private String file;
    private String lastOut;
    private String lastErr;

    @Before
    public void setUp() {
        file = folder.getRoot().toPath().resolve("group.txt").toString();
    }

    /** Runs one command against the temp file and keeps what it printed. */
    private int run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String[] withFile = new String[args.length + 2];
        System.arraycopy(args, 0, withFile, 0, args.length);
        withFile[args.length] = "--file";
        withFile[args.length + 1] = file;
        int code = new Cli(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)).run(withFile);
        lastOut = out.toString(StandardCharsets.UTF_8);
        lastErr = err.toString(StandardCharsets.UTF_8);
        return code;
    }

    private void createAmaKofiEsiGroup() {
        assertEquals(0, run("create", "50.00"));
        assertEquals(0, run("add-member", "Ama"));
        assertEquals(0, run("add-member", "Kofi"));
        assertEquals(0, run("add-member", "Esi"));
    }

    private Path dataFile() {
        return Path.of(file);
    }

    /**
     * Sets the data file's time stamp to the year 2000. Every save replaces the file and gives
     * it a new time stamp, so if the time is still 2000 afterwards, nothing was written.
     */
    private FileTime markFileAsOld() throws Exception {
        FileTime old = FileTime.fromMillis(946_684_800_000L);
        Files.setLastModifiedTime(dataFile(), old);
        return old;
    }

    private void assertOut(String text) {
        assertTrue("output was:\n" + lastOut, lastOut.contains(text));
    }

    private void assertErr(String text) {
        assertTrue("errors were:\n" + lastErr, lastErr.contains(text));
    }

    @Test
    public void challengeExampleThroughTheCommandLine() {
        assertEquals(0, run("create", "50.00", "--name", "Family Susu"));
        assertEquals(0, run("add-member", "Ama"));
        assertEquals(0, run("add-member", "Kofi"));
        assertEquals(0, run("add-member", "Esi"));
        assertEquals(0, run("pay", "Ama", "50"));
        assertEquals(0, run("pay", "Kofi", "50"));
        assertEquals(0, run("pay", "Esi", "30"));
        assertOut("Esi still owes 20.00");

        assertEquals(1, run("close-week"));
        assertOut("Week 1 cannot close. Nothing was paid out.");
        assertOut("Esi owes 20.00");
        assertFalse(lastOut.contains("Ama owes"));

        assertEquals(0, run("pay", "esi", "20", "--week", "1"));
        assertOut("Esi has paid in full");

        assertEquals(0, run("close-week"));
        assertOut("Week 1 closed. Ama receives GHS 150.00.");
        assertOut("Week 2 begins. Kofi is next.");

        assertEquals(0, run("report"));
        assertOut("Group: Family Susu");
        assertOut("Current week: 2 (pot goes to Kofi)");
        assertOut("Paid in full: (nobody yet)");
        assertOut("Ama owes 50.00");
        assertOut("Week 1: Ama received GHS 150.00");
        assertEquals("", lastErr);
    }

    @Test
    public void ruleViolationsExitWithTwoAndAFriendlyMessage() {
        run("create", "50");
        run("add-member", "Ama");
        run("add-member", "Kofi");

        assertEquals(2, run("add-member", "ama"));
        assertErr("Error: Member 'Ama' already exists.");
        assertEquals(2, run("pay", "Yaw", "10"));
        assertErr("Unknown member 'Yaw'");
        assertEquals(2, run("pay", "Ama", "0"));
        assertErr("greater than zero");
        assertEquals(2, run("pay", "Ama", "-5"));
        assertErr("greater than zero");
        assertEquals(2, run("pay", "Ama", "60"));
        assertErr("only owes 50.00");
        assertEquals(2, run("pay", "Ama", "1e3"));
        assertErr("not a valid amount");
        assertEquals(2, run("pay", "Ama", "10", "--week", "2"));
        assertErr("has not started");

        run("pay", "Ama", "10");
        assertEquals(2, run("add-member", "Yaw"));
        assertErr("already started");
        assertFalse(lastErr.contains("Exception"));
        assertFalse(lastErr.contains("\tat "));
    }

    @Test
    public void invalidUsageExitsWithTwoAndAFriendlyMessage() {
        assertEquals(2, run("report"));
        assertErr("No group found");
        assertEquals(2, run("dance"));
        assertErr("Unknown command 'dance'");
        assertEquals(2, run("create"));
        assertErr("Usage: create");
        assertEquals(2, run("create", "50", "--colour", "red"));
        assertErr("Unknown option '--colour'");
        assertEquals(2, run("create", "abc"));
        assertErr("not a valid amount");
        assertEquals(2, new Cli(new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream())).run());
    }

    @Test
    public void createRefusesToOverwriteWithoutForce() throws Exception {
        assertEquals(0, run("create", "50"));
        run("add-member", "Ama");

        assertEquals(2, run("create", "20"));
        assertErr("already exists");
        assertTrue(Files.readString(Path.of(file)).contains("member\tAma"));

        assertEquals(0, run("create", "20", "--force"));
        assertFalse(Files.readString(Path.of(file)).contains("member\tAma"));
    }

    @Test
    public void fullThreeWeekCycleThroughTheCommandLineEndsWithCycleComplete() {
        createAmaKofiEsiGroup();
        String[] expectedRecipients = {"Ama", "Kofi", "Esi"};
        for (int week = 1; week <= 3; week++) {
            for (String member : expectedRecipients) {
                assertEquals(0, run("pay", member, "50.00"));
            }
            assertEquals(0, run("close-week"));
            assertOut("Week " + week + " closed. " + expectedRecipients[week - 1] + " receives GHS 150.00.");
        }
        assertOut("Cycle complete: every member has received the pot once.");
        assertFalse(lastOut.contains("begins"));

        assertEquals(2, run("pay", "Ama", "10"));
        assertErr("cycle is complete");
        assertEquals(2, run("close-week"));
        assertErr("cycle is complete");

        assertEquals(0, run("report"));
        assertOut("Cycle complete");
        assertOut("Week 1: Ama received GHS 150.00");
        assertOut("Week 2: Kofi received GHS 150.00");
        assertOut("Week 3: Esi received GHS 150.00");
    }

    @Test
    public void partialPaymentsAddUpToExactlyFiftyAndThePotIsExactly150() {
        createAmaKofiEsiGroup();
        assertEquals(0, run("pay", "Esi", "10.00"));
        assertOut("Esi still owes 40.00");
        assertEquals(0, run("pay", "Esi", "24.50"));
        assertOut("Esi still owes 15.50");
        assertEquals(0, run("pay", "Esi", "15.50"));
        assertOut("Esi has paid in full");

        assertEquals(0, run("report"));
        assertOut("Paid in full: Esi");
        assertOut("Ama owes 50.00");
        assertOut("Kofi owes 50.00");

        run("pay", "Ama", "50");
        run("pay", "Kofi", "50");
        assertEquals(0, run("close-week"));
        assertOut("Ama receives GHS 150.00.");
    }

    @Test
    public void failedPaymentsDoNotChangeTheSavedFile() throws Exception {
        createAmaKofiEsiGroup();
        run("pay", "Ama", "20");
        FileTime before = markFileAsOld();
        byte[] contents = Files.readAllBytes(dataFile());

        assertEquals(2, run("pay", "Ama", "40"));
        assertEquals(2, run("pay", "Yaw", "10"));
        assertEquals(2, run("pay", "Ama", "0"));
        assertEquals(2, run("pay", "Ama", "abc"));
        assertEquals(2, run("pay", "Ama", "10", "--week", "2"));
        assertEquals(2, run("add-member", "Yaw"));

        assertEquals(before, Files.getLastModifiedTime(dataFile()));
        assertArrayEquals(contents, Files.readAllBytes(dataFile()));
    }

    @Test
    public void blockedCloseWeekDoesNotWriteToDisk() throws Exception {
        createAmaKofiEsiGroup();
        run("pay", "Ama", "50");
        FileTime before = markFileAsOld();
        byte[] contents = Files.readAllBytes(dataFile());

        assertEquals(1, run("close-week"));

        assertEquals(before, Files.getLastModifiedTime(dataFile()));
        assertArrayEquals(contents, Files.readAllBytes(dataFile()));
    }

    @Test
    public void helpPrintsUsageAndExitsWithZero() {
        assertEquals(0, run("help"));
        assertOut("Usage:");
        assertOut("close-week");
    }
}
