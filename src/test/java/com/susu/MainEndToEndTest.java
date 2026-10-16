package com.susu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Runs the real program ({@code java com.susu.Main ...}) as a separate process, the way a user
 * would, to prove that Main passes the arguments to Cli and turns its result into the real
 * process exit code. Everything happens inside a temporary folder.
 */
public class MainEndToEndTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path dataFile;
    private String out;
    private String err;

    @Before
    public void setUp() {
        dataFile = folder.getRoot().toPath().resolve("example.txt");
    }

    /** Starts {@code java -cp <compiled classes> com.susu.Main args... --file <temp file>}. */
    private int runMain(String... args) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        List<String> command = new ArrayList<>(List.of(java, "-cp", classes, "com.susu.Main"));
        command.addAll(List.of(args));
        command.add("--file");
        command.add(dataFile.toString());

        File stdout = folder.newFile();
        File stderr = folder.newFile();
        Process process = new ProcessBuilder(command)
                .directory(folder.getRoot())
                .redirectOutput(stdout)
                .redirectError(stderr)
                .start();
        assertTrue("program did not finish in time", process.waitFor(30, TimeUnit.SECONDS));
        out = Files.readString(stdout.toPath(), StandardCharsets.UTF_8);
        err = Files.readString(stderr.toPath(), StandardCharsets.UTF_8);
        return process.exitValue();
    }

    private void assertOut(String text) {
        assertTrue("output was:\n" + out, out.contains(text));
    }

    @Test
    public void realProgramRunsTheChallengeExampleWithCorrectExitCodes() throws Exception {
        assertEquals(0, runMain("create", "50.00"));
        assertEquals(0, runMain("add-member", "Ama"));
        assertEquals(0, runMain("add-member", "Kofi"));
        assertEquals(0, runMain("add-member", "Esi"));
        assertEquals(0, runMain("pay", "Ama", "50"));
        assertEquals(0, runMain("pay", "Kofi", "50"));
        assertEquals(0, runMain("pay", "Esi", "30"));

        assertEquals(1, runMain("close-week"));
        assertOut("Esi owes 20.00");

        assertEquals(0, runMain("pay", "Esi", "20"));
        assertEquals(0, runMain("close-week"));
        assertOut("Ama receives GHS 150.00.");
        assertOut("Kofi is next.");

        assertEquals(0, runMain("report"));
        assertOut("Current week: 2 (pot goes to Kofi)");
        assertOut("Week 1: Ama received GHS 150.00");
        assertEquals("", err);
    }

    @Test
    public void realProgramExitsWithTwoAndNoStackTraceOnBadInput() throws Exception {
        assertEquals(2, runMain("report"));
        assertTrue(err.startsWith("Error: No group found"));
        assertFalse(err.contains("Exception"));
        assertFalse(Files.exists(folder.getRoot().toPath().resolve(Storage.DEFAULT_FILE)));
    }
}
