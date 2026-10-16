# Susu Group Tracker

A small command line program that manages one rotating savings group (a *susu*).
Every member pays the same fixed amount in Ghana cedis (GHS) each week. When everyone
has paid in full, the whole pot goes to one member, in the order they joined, and the
next week begins. When every member has received the pot once, the cycle is complete.

Money is stored as whole pesewas (1 GHS = 100 pesewas), so there are no rounding errors.
The program uses only the Java standard library. JUnit 4 is used for the tests only.

## Requirements

- JDK 17 or newer (`java -version`)
- Maven 3.8 or newer (`mvn -version`)

## Build

```bash
mvn package
```

This compiles the code, runs all tests and creates the runnable jar
`target/susu-group-tracker.jar`.

## Run the tests

```bash
mvn test
```

Expected result (43 tests in 5 test classes):

```
[INFO] Tests run: 43, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Usage

```bash
java -jar target/susu-group-tracker.jar <command> [options]
```

| Command | What it does |
|---|---|
| `create <weekly-amount> [--name NAME] [--force]` | Start a new group with a weekly contribution in GHS, e.g. `50.00`. Refuses to replace an existing data file unless you add `--force`. |
| `add-member <name>` | Add a member at the end of the payout order. Only allowed before the first payment. |
| `pay <member> <amount> [--week N]` | Record a payment. Partial payments add up. The week defaults to the current week; only the current week accepts payments. |
| `close-week` | If everyone has paid in full, pay the pot to the next member and start the next week. Otherwise pay nothing and list who owes what. |
| `report` | Show the current week and who gets the pot, who has paid in full, who owes what, and the payout history. |
| `help` | Show the usage message. |

| Option | Used with | Meaning |
|---|---|---|
| `--file PATH` | every command | Data file to use. Default: `susu-group.txt` in the current folder. |
| `--name NAME` | `create` | Name of the group. Default: `Susu Group`. |
| `--force` | `create` | Replace an existing data file. |
| `--week N` | `pay` | The week the payment is for. Default: the current week. |

**Names with spaces must be quoted**, for example `add-member "Kwame Mensah"`.
Names are matched ignoring upper/lower case and extra spaces, so `pay "kwame mensah" 5`
pays into Kwame Mensah's account.

Amounts are plain numbers with at most 2 decimals: `50`, `50.5` and `50.00` are fine;
`0`, `-5`, `1e3`, `1,000` and `10.000` are rejected.

### Data file

The group is saved to `susu-group.txt` in the current folder after every successful
change (or to the file given with `--file`). This file is your data, not source code, so
it is listed in `.gitignore` and never committed. Any other `.txt` file in the project
folder (for example one chosen with `--file office.txt`) is ignored too.

## Example session (from the challenge brief)

Members Ama, Kofi and Esi, weekly contribution GHS 50.00. This is real output from the
commands below, run from the project folder after `mvn package`.

```
$ java -jar target/susu-group-tracker.jar create 50.00 --name "Family Susu"
Created group 'Family Susu' with a weekly contribution of GHS 50.00 (saved to susu-group.txt).
Next: add members with add-member <name>.

$ java -jar target/susu-group-tracker.jar add-member Ama
Added Ama (position 1 in the payout order).

$ java -jar target/susu-group-tracker.jar add-member Kofi
Added Kofi (position 2 in the payout order).

$ java -jar target/susu-group-tracker.jar add-member Esi
Added Esi (position 3 in the payout order).

$ java -jar target/susu-group-tracker.jar pay Ama 50.00
Recorded GHS 50.00 from Ama for week 1. Ama has paid in full.

$ java -jar target/susu-group-tracker.jar pay Kofi 50.00
Recorded GHS 50.00 from Kofi for week 1. Kofi has paid in full.

$ java -jar target/susu-group-tracker.jar pay Esi 30.00
Recorded GHS 30.00 from Esi for week 1. Esi still owes 20.00.

$ java -jar target/susu-group-tracker.jar report
Group: Family Susu
Weekly contribution: GHS 50.00 x 3 members = pot GHS 150.00
Members (payout order): Ama, Kofi, Esi

Current week: 1 (pot goes to Ama)
Paid in full: Ama, Kofi
Still owing:
  Esi owes 20.00

Payout history: (none yet)

$ java -jar target/susu-group-tracker.jar close-week
Week 1 cannot close. Nothing was paid out.
  Esi owes 20.00

$ java -jar target/susu-group-tracker.jar pay Esi 20.00 --week 1
Recorded GHS 20.00 from Esi for week 1. Esi has paid in full.

$ java -jar target/susu-group-tracker.jar close-week
Week 1 closed. Ama receives GHS 150.00.
Week 2 begins. Kofi is next.

$ java -jar target/susu-group-tracker.jar report
Group: Family Susu
Weekly contribution: GHS 50.00 x 3 members = pot GHS 150.00
Members (payout order): Ama, Kofi, Esi

Current week: 2 (pot goes to Kofi)
Paid in full: (nobody yet)
Still owing:
  Ama owes 50.00
  Kofi owes 50.00
  Esi owes 50.00

Payout history:
  Week 1: Ama received GHS 150.00
```

Rule violations print a friendly message and exit with code 2. For example, with a second
group kept in its own file:

```
$ java -jar target/susu-group-tracker.jar create 25.50 --name "Office Susu" --file office.txt
Created group 'Office Susu' with a weekly contribution of GHS 25.50 (saved to office.txt).
Next: add members with add-member <name>.

$ java -jar target/susu-group-tracker.jar add-member "Kwame Mensah" --file office.txt
Added Kwame Mensah (position 1 in the payout order).

$ java -jar target/susu-group-tracker.jar add-member Abena --file office.txt
Added Abena (position 2 in the payout order).

$ java -jar target/susu-group-tracker.jar pay Abena 30 --file office.txt
Error: Abena only owes 25.50 for week 1, so 30.00 is too much.

$ java -jar target/susu-group-tracker.jar pay Abena 10.000 --file office.txt
Error: Amount '10.000' has more than 2 decimal places.
```

To start again with a fresh group, run `create` with `--force`, or delete `susu-group.txt`.

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Success. |
| `1` | `close-week` ran, but the week could not close because someone still owes. Nothing was paid out; the debts are printed. |
| `2` | Invalid input or a broken rule (unknown member, overpayment, bad amount, missing file, ...). An `Error: ...` message is printed and nothing is changed. |

Check the code with `echo $?` straight after a command.

## Project layout

| Path | Purpose |
|---|---|
| `pom.xml` | Maven build: Java 17, JUnit 4 (tests only), runnable jar with `com.susu.Main`. |
| `src/main/java/com/susu/Main.java` | Entry point. Only calls `System.exit(new Cli(...).run(args))`. |
| `src/main/java/com/susu/Cli.java` | Reads the command and options, runs it, prints the result, returns the exit code. |
| `src/main/java/com/susu/SusuGroup.java` | The group and all its rules: members, payments, closing weeks, payouts. |
| `src/main/java/com/susu/Money.java` | Converts GHS text to whole pesewas and back, exactly. |
| `src/main/java/com/susu/Storage.java` | Saves the group to a text file (atomic save) and loads it back safely. |
| `src/main/java/com/susu/CloseWeekResult.java` | What `closeWeek` returns: the payout, or who still owes. |
| `src/main/java/com/susu/Payout.java` | One line of payout history (week, member, amount). |
| `src/main/java/com/susu/Debt.java` | A member and how much they still owe this week. |
| `src/main/java/com/susu/Report.java` | A read-only snapshot of the group for the report. |
| `src/main/java/com/susu/SusuException.java` | A broken rule, with a message meant for the user. |
| `src/test/java/com/susu/*Test.java` | JUnit 4 tests (see NOTES.md for what each class covers). |
| `NOTES.md` | Assumptions, design decisions and future improvements. |
