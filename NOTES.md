# Notes

These notes explain the assumptions I made, the design choices behind the code, what I
would improve with more time, and how the program was tested.

## 1. Assumptions and decisions

Each item says what the program does and why.

### Money

1. **Money is stored as whole pesewas in a `long`** (GHS 50.00 is `5000`).
   *Why:* `double` and `float` store numbers in base 2 and cannot hold 0.10 exactly, so
   0.10 + 0.20 becomes 0.30000000000000004. Whole numbers add up exactly. A `long` is
   used instead of an `int` so even very large amounts fit. `double` and `float` are not
   used anywhere in the program.
2. **`BigDecimal` is used only to read and print amounts.** `Money.parse("50.5")` gives
   `5050` and `Money.format(5050)` gives `"50.50"`. All calculations use `long`.
   *Why:* `BigDecimal` converts text safely, but whole numbers are simpler for everything else.
3. **Amounts are strict: optional sign, digits, and at most 2 decimals.** `50`, `50.5` and
   `50.00` are accepted. `10.000`, `1e3`, `1,000`, `NaN`, `Infinity`, `.5`, `50.` and
   `GHS 50` are rejected. `10.000` gets its own message, "more than 2 decimal places".
   *Why:* a pesewa is the smallest unit, so a third decimal is always a mistake. Guessing
   (for example rounding `10.005`) would quietly change someone's money. The text is
   checked against a pattern *before* `BigDecimal` sees it, because `BigDecimal` itself
   would accept `1e3` as 1000.
4. **`Money.parse` accepts negative numbers; the group rejects zero and negative amounts.**
   See the Design section for why.
5. **If the weekly pot (contribution × members) would be too large for a `long`, the member
   is refused.** *Why:* this can never happen with real amounts, but the program must never
   produce a wrong total silently.

### Members

6. **Names are cleaned and compared without case.** Spaces at the start and end are
   removed, any run of spaces, tabs or new lines becomes one space, and names are compared
   in lower case. So `"Ama"`, `" ama "` and `"AMA"` are the same member, and
   `"Kwame  Mensah"` is stored as `"Kwame Mensah"`. The name is shown as it was first typed.
   *Why:* the brief says names must be unique. Without this, one person could join twice
   with a different spelling and receive the pot twice.
7. **Empty names are rejected.**
8. **Members can only be added before the group has started.** The group has started after
   the first payment or the first closed week, whichever comes first.
   *Why:* the brief leaves this open. A late member would make the pot bigger than in
   earlier weeks, and there is no fair place to put them in the payout order. Members who
   paid in earlier weeks would be treated unfairly.
9. **The payout order is the joining order and cannot be changed.** *Why:* that is what
   the brief says.

### Weeks and payments

10. **A group needs at least 2 members before it accepts payments or closes a week.**
    *Why:* with one member, that person would only pay themselves.
11. **Only the current week accepts payments.** A week before the current one gets "already
    closed", a future week gets "has not started", and week 0 or less is invalid. `pay`
    uses the current week unless `--week N` is given.
    *Why:* the brief says "for a given week", so the week can be passed in. A closed week
    is always fully paid, so there is nothing left to pay. Paying ahead would mix up the
    money of different weeks.
12. **Partial payments add up, but nobody can pay more than they still owe that week.** A
    member who has paid in full cannot pay again, not even 0.01.
    *Why:* extra money would make the pot bigger than members × weekly amount.
13. **Payment checks run in a fixed order:** the cycle is still open, there are enough
    members, the member exists, the amount is above zero, it is the current week, and the
    payment is not more than what is owed.
    *Why:* basic problems are reported before money problems, so the message points at the
    real mistake.
14. **A week closes only when everyone has paid in full.** Then the pot (weekly amount ×
    number of members) goes to the next member in joining order and the next week starts.
    If anyone owes even one pesewa, nothing is paid, the week stays open, payments made so
    far are kept, and the result lists who owes how much.
15. **End of cycle:** once every member has received the pot once, the cycle is complete.
    Paying, closing a week and adding members are then rejected, and the report says
    "Cycle complete". The current week counter reads one past the last week (4 for 3
    members), but the report shows "Cycle complete" instead.
    *Why:* the brief describes one rotation. Starting a second cycle is listed under
    improvements.
16. **`whoOwes` on a closed week returns an empty list**, because a week can only close when
    everyone has paid. Asking about a future week is an error.

### Command line

17. **Exit codes:**

    | Code | Meaning |
    |---|---|
    | `0` | Success. |
    | `1` | `close-week` ran but the week could not close. The debts are printed to normal output, because this is a normal outcome, not an error. |
    | `2` | Invalid input or a broken rule. `Error: ...` is printed to the error stream and nothing is changed. |

    *Why:* scripts can tell "worked", "not everyone has paid" and "you made a mistake" apart.
18. **Failed commands change nothing.** Every check happens before the group changes, and
    the file is only saved after a command succeeds. This is tested by checking the file's
    bytes and its last-modified time.
19. **A blocked `close-week` writes nothing to disk**, because nothing changed. Tested the
    same way.
20. **`create` refuses to replace an existing data file unless `--force` is given.**
    *Why:* one mistyped command must not wipe out a group that is in the middle of a cycle.
21. **Errors never show a stack trace.** `Cli.run` catches `SusuException` and prints its
    message. As a safety net, any other unexpected error becomes one friendly line.
22. **Names with spaces must be quoted** (`add-member "Kwame Mensah"`). Extra words give a
    "wrong number of arguments" error instead of being guessed.
23. **Unknown options, missing option values and options on the wrong command** (for
    example `--week` on `report`) are rejected with exit code 2.

### Storage

24. **One group per data file.** The default file is `susu-group.txt` in the current folder;
    `--file PATH` chooses another file, so separate groups can live in separate files.
25. **`Storage.DEFAULT_FILE` must match the line in `.gitignore`.** The data file is a user's
    savings data, not source code, so it must never be committed. `.gitignore` also
    ignores every `.txt` file in the project root (`/*.txt`), so a file chosen with
    `--file`, like `office.txt` in the README, is not committed by accident either. I
    checked this by hand with `git check-ignore -v susu-group.txt office.txt`. I deliberately did not make this an
    automated test, because a test that reads the project's `.gitignore` depends on a file
    outside its temp folder.
26. **File format:** plain text, one record per line, fields separated by tabs. It has a
    version header (`SUSU-GROUP-TRACKER 1`) and ends with an `end` marker. Names are
    URL-encoded, so a name can never contain a tab or line break that would break the format.
    *Why:* it needs only the standard library, a person can read it, and no name can
    break it. The header lets a future version recognise old files. The `end` marker shows
    whether a file was cut off.
27. **Atomic save:** the program writes a temporary file in the same folder, then
    `Files.move(..., ATOMIC_MOVE)` replaces the real file in one step. If a file system
    cannot do an atomic move, it falls back to a normal replacing move. The temp file is
    deleted if anything fails.
    *Why:* if the program crashes while saving, the old file is still complete. The file
    is never half-written.
28. **Loading replays the saved data through the normal `SusuGroup` rules.** It adds the
    members, re-pays and re-closes each finished week (checking that the payouts match),
    then records the current week's payments.
    *Why:* a damaged or hand-edited file can never create an impossible group, such as an
    overpayment or a wrong payout order. These cases are reported as "damaged" with the
    reason. It also means `SusuGroup` needs no back door for setting its fields directly.
29. **A missing, damaged or incomplete file gives a friendly message** that says what to do
    next, never a stack trace.

### Naming

30. **Method names use Java's camelCase:** `addMember`, `recordContribution`, `whoOwes`,
    `closeWeek` and `report`, instead of the brief's `add_member` style.
    *Why:* that is the normal Java convention. The meaning is the same as in the brief.

## 2. Design

**Why `closeWeek` returns a result instead of throwing an exception.**
The brief describes "cannot close" as a normal thing that happens: "do NOT pay out, and
report who still owes". Exceptions are for mistakes, such as an unknown member or a
negative amount. A susu leader who tries to close the week while Esi is 20.00 short has
not made a mistake; they want to know who still owes. So `closeWeek` returns a
`CloseWeekResult`: either the `payout` that was made, or the list of `debts`. The calling
code has to look at the result, and the CLI turns it into exit code 1 instead of 2.
Exceptions are still thrown for real rule violations, such as closing a week in a
completed cycle or in a group with fewer than 2 members.

**Why `Money` only parses and the group enforces "greater than zero".**
`Money` answers one question: is this text a well-formed amount, and how many pesewas is
it? Whether an amount is *allowed* depends on what it is for. That is a business rule,
so it belongs in `SusuGroup`. This also means the rule is enforced however the group is
used (the CLI, a test, or a future API), not only when the amount came from text. And
the user gets a clear "must be greater than zero" message instead of a vague "invalid
amount".

**Why records are used.**
`Payout`, `Debt`, `CloseWeekResult` and `Report` are small data holders. A Java record
writes the constructor, getters, `equals`, `hashCode` and `toString` automatically, and
its fields cannot be changed. Because they cannot change, a `Report` cannot be used to
change the group by accident; the lists inside are also copied as read-only. Because
`equals` comes for free, tests can compare whole values in one line, for example
`assertEquals(new Payout(1, "Ama", 15000), result.payout())`.

**Other structure choices.**
- `SusuGroup` holds all the rules and knows nothing about files or the console.
  `Storage` knows only about files, and `Cli` only about arguments and printing. Each
  part can be tested on its own.
- `Cli` takes its output and error streams as arguments and *returns* the exit code
  instead of calling `System.exit`. `Main` is one line. This makes the CLI testable
  without starting a new process.
- Members are kept in a `LinkedHashMap`, which keeps insertion order, so the joining
  order is also the payout order. The map key is the cleaned, lower-case name.

## 3. What I would improve with more time

1. **More than one group per file**, or a folder of groups selected by name, instead of
   one file per group.
2. **A dated payment ledger with corrections.** Keep every payment with its date and
   allow a recorded mistake to be reversed with a correction entry, instead of keeping
   only each member's total for the current week.
3. **Late joiners with catch-up payments.** Let a member join after week 1 by paying what
   they missed, and decide fairly where they go in the payout order.
4. **A different payout order:** agreed by the group, random, or by bidding, instead of
   joining order only.
5. **JSON or a database for storage**, such as SQLite, for larger groups, history queries
   and safe use by several people at once. This would need an external library or a
   hand-written JSON parser.
6. **An HTTP API**, for example with the JDK's built-in `HttpServer`, so a phone app or
   a website could use the same `SusuGroup` rules.
7. **More than one cycle:** start a new rotation when the current one completes, and keep
   the history of earlier cycles.
8. **File locking**, so two commands running at the same moment cannot overwrite each
   other's changes.

## 4. How it was tested

There are 43 automated JUnit 4 tests. `mvn test` runs them and they all pass.

| Test class | Tests | What it covers |
|---|---|---|
| `MoneyTest` | 6 | Parsing and formatting; 0.10 + 0.20 is exactly 0.30; rejects junk, `NaN`, `1e3`, `1,000`, more than 2 decimals, and too-large amounts. |
| `SusuGroupTest` | 19 | Every group rule: the Ama/Kofi/Esi example, partial payments, a week that cannot close (with some and with no payments), overpayment, paying twice, unknown and duplicate members, empty names, zero and negative amounts, late members, fewer than 2 members, current week only, `whoOwes`, a full rotation to cycle complete, and the report. |
| `StorageTest` | 7 | Save and load round trip in the middle of a week (debts unchanged), a completed cycle, names with tabs, line breaks, `%`, `+` and Ghanaian letters, no temp files left behind, and friendly errors for missing, damaged and incomplete files. |
| `CliTest` | 9 | The example through the command line with exit codes 0, 1 and 2; rule violations; bad usage; `create` without and with `--force`; a full three-week cycle; 10.00 + 24.50 + 15.50 = exactly 50.00 with a 150.00 pot; failed payments and a blocked `close-week` leave the file unchanged; `help`. |
| `MainEndToEndTest` | 2 | Runs the real `com.susu.Main` in a separate Java process on the example and checks the real exit codes, and checks that bad input exits with 2 and shows no stack trace. |

How the tests are kept reliable:
- Each test builds its own group in `@Before` and uses JUnit's `TemporaryFolder`, so tests
  do not depend on each other or on files outside a temp folder.
- To check that the "nothing was written" test can actually fail, I temporarily made a
  blocked `close-week` save the file. The test failed as it should, and then I put the
  code back.
- I also ran every command in the README by hand against the built jar. The example
  output in the README is copied from those runs.

What is **not** covered by automated tests:
- A real crash in the middle of a save is not simulated. The atomic save is built from
  standard `Files.move` behaviour, and the tests check that no temp files are left
  behind.
- The fallback for file systems without atomic moves is not tested, because it cannot be
  triggered on a normal Linux disk.
- The code was compiled with `--release 17` and run on JDK 21 on Linux. I did not run it
  on a JDK 17 install or on Windows.
