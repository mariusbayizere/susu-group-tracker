package com.susu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StorageTest {

    private static final long FIFTY = Money.parse("50.00");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path file;

    @Before
    public void setUp() {
        file = folder.getRoot().toPath().resolve(Storage.DEFAULT_FILE);
    }

    private SusuGroup exampleGroup() {
        SusuGroup group = new SusuGroup("Family Susu", FIFTY);
        group.addMember("Ama");
        group.addMember("Kofi");
        group.addMember("Esi");
        return group;
    }

    private void write(String... lines) throws IOException {
        Files.write(file, List.of(lines), StandardCharsets.UTF_8);
    }

    private static void assertMessageContains(SusuException e, String text) {
        assertTrue("message was: " + e.getMessage(), e.getMessage().contains(text));
    }

    @Test
    public void saveAndLoadRoundTripKeepsMidWeekPartialPayments() {
        SusuGroup group = exampleGroup();
        for (String member : group.getMembers()) {
            group.recordContribution(member, FIFTY, 1);
        }
        group.closeWeek();
        group.recordContribution("Kofi", FIFTY, 2);
        group.recordContribution("Esi", Money.parse("12.50"), 2);

        Storage.save(group, file);
        SusuGroup loaded = Storage.load(file);

        assertEquals("Family Susu", loaded.getName());
        assertEquals(FIFTY, loaded.getWeeklyContribution());
        assertEquals(List.of("Ama", "Kofi", "Esi"), loaded.getMembers());
        assertEquals(2, loaded.getCurrentWeek());
        assertEquals(group.getPayouts(), loaded.getPayouts());
        assertEquals(group.whoOwes(2), loaded.whoOwes(2));
        assertEquals(List.of(new Debt("Ama", FIFTY), new Debt("Esi", Money.parse("37.50"))), loaded.whoOwes(2));
        assertEquals(group.report(), loaded.report());

        // The loaded group keeps working: Esi can pay exactly what is left, not more.
        assertThrows(SusuException.class, () -> loaded.recordContribution("Esi", FIFTY, 2));
        assertEquals(0L, loaded.recordContribution("Esi", Money.parse("37.50"), 2));
    }

    @Test
    public void saveAndLoadKeepsACompletedCycleComplete() {
        SusuGroup group = exampleGroup();
        for (int week = 1; week <= 3; week++) {
            for (String member : group.getMembers()) {
                group.recordContribution(member, FIFTY, week);
            }
            group.closeWeek();
        }
        Storage.save(group, file);
        SusuGroup loaded = Storage.load(file);
        assertTrue(loaded.isCycleComplete());
        assertEquals(group.getPayouts(), loaded.getPayouts());
    }

    @Test
    public void namesWithSpecialCharactersCannotBreakTheFile() {
        SusuGroup group = new SusuGroup("Tab\tand\nnewline % + = group", FIFTY);
        group.addMember("Ama\tMensah");
        group.addMember("Kofi%20+Boateng=1");
        group.addMember("Ɛsi Ɔwusu");
        Storage.save(group, file);

        SusuGroup loaded = Storage.load(file);
        assertEquals(group.getName(), loaded.getName());
        assertEquals(List.of("Ama Mensah", "Kofi%20+Boateng=1", "Ɛsi Ɔwusu"), loaded.getMembers());
    }

    @Test
    public void secondSaveReplacesTheFileAndLeavesNoTempFiles() throws IOException {
        SusuGroup group = exampleGroup();
        Storage.save(group, file);
        group.recordContribution("Ama", FIFTY, 1);
        Storage.save(group, file);

        assertEquals(FIFTY, Storage.load(file).amountPaid("Ama"));
        try (Stream<Path> files = Files.list(folder.getRoot().toPath())) {
            assertEquals(List.of(file), files.toList());
        }
    }

    @Test
    public void missingFileGivesFriendlyError() {
        SusuException e = assertThrows(SusuException.class, () -> Storage.load(file));
        assertMessageContains(e, "No group found");
        assertMessageContains(e, "create");
    }

    @Test
    public void corruptFileGivesFriendlyError() throws IOException {
        write("this is not a susu file");
        assertMessageContains(assertThrows(SusuException.class, () -> Storage.load(file)), "damaged");

        write("SUSU-GROUP-TRACKER\t1", "name\tX", "weekly\tfifty", "week\t1", "end");
        assertMessageContains(assertThrows(SusuException.class, () -> Storage.load(file)), "'fifty' is not a valid number");

        write("SUSU-GROUP-TRACKER\t1", "name\tX", "weekly\t5000", "week\t1",
                "member\tAma", "member\tKofi", "paid\tAma\t9000", "end");
        assertMessageContains(assertThrows(SusuException.class, () -> Storage.load(file)), "only owes");

        write("SUSU-GROUP-TRACKER\t1", "name\tX", "weekly\t5000", "week\t2",
                "member\tAma", "member\tKofi", "payout\t1\tKofi\t10000", "end");
        assertMessageContains(assertThrows(SusuException.class, () -> Storage.load(file)), "payout history");
    }

    @Test
    public void incompleteFileGivesFriendlyError() throws IOException {
        Storage.save(exampleGroup(), file);
        List<String> lines = Files.readAllLines(file);
        write(lines.subList(0, lines.size() - 1).toArray(new String[0]));

        SusuException e = assertThrows(SusuException.class, () -> Storage.load(file));
        assertMessageContains(e, "incomplete");

        write("");
        assertMessageContains(assertThrows(SusuException.class, () -> Storage.load(file)), "damaged");
    }
}
