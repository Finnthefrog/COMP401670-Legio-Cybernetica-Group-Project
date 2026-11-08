package com.meitheal;

import com.meitheal.cli.ConsoleApp;
import com.meitheal.model.Engagement;
import com.meitheal.model.EngagementStatus;
import com.meitheal.model.Member;
import com.meitheal.model.MemberStatus;
import com.meitheal.model.Offering;
import com.meitheal.model.Request;
import com.meitheal.service.TimeBankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ConsoleAppCharacterisationTest {

    private static final String BANNER =
            "==================================================\n"
                    + "   Welcome to Meitheal Community Time-Bank CLI    \n"
                    + "==================================================\n";

    // printMenu() ends with print (no newline) so the command output follows "Enter choice: " directly.
    private static final String MENU =
            "\n--- MEITHEAL MAIN MENU ---\n"
                    + "1. Register a member\n"
                    + "2. Suspend / Reinstate a member (Admin)\n"
                    + "3. Publish an offer\n"
                    + "4. Raise a request\n"
                    + "5. Propose an engagement\n"
                    + "6. Confirm a session\n"
                    + "7. Cancel an engagement\n"
                    + "8. Apply for a community fund grant\n"
                    + "9. Query balances and history\n"
                    + "10. System Report (Credits & Fund)\n"
                    + "0. Exit\n"
                    + "Enter choice: ";

    private static final String GOODBYE = "Exiting Meitheal Time-Bank. Goodbye!\n";
    private static final String INVALID = "Invalid option. Please choose between 0 and 10.\n";

    private TimeBankService service;

    @BeforeEach
    void setUp() {
        service = new TimeBankService();
    }

    /** One pass round the loop: menu, then whatever the command printed, then the blank println(). */
    private static String iteration(String commandOutput) {
        return MENU + commandOutput + "\n";
    }

    /**
     * Feeds the lines to System.in BEFORE constructing ConsoleApp (its constructor
     * builds new Scanner(System.in)), runs start(), and returns everything printed.
     */
    private String run(String... lines) {
        InputStream oldIn = System.in;
        PrintStream oldOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            System.setIn(new ByteArrayInputStream(
                    (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            new ConsoleApp(service).start();
        } finally {
            System.setIn(oldIn);
            System.setOut(oldOut);
        }
        // println() emits \r\n on Windows
        return out.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private String anySkill() {
        return service.getValidSkillCategories().iterator().next();
    }

    // ---------------------------------------------------------------- menu loop

    @Test
    void exitImmediately_printsBannerMenuAndGoodbye() {
        assertEquals(BANNER + iteration(GOODBYE), run("0"));
    }

    @Test
    void choiceIsTrimmed_soSpacesAroundZeroStillExit() {
        assertEquals(BANNER + iteration(GOODBYE), run(" 0 "));
    }

    @Test
    void unknownChoice_printsInvalidOptionAndKeepsLooping() {
        assertEquals(
                BANNER + iteration(INVALID) + iteration(GOODBYE),
                run("99", "0"));
    }

    @Test
    void blankAndNonNumericChoices_areBothInvalidOption() {
        assertEquals(
                BANNER + iteration(INVALID) + iteration(INVALID) + iteration(GOODBYE),
                run("", "abc", "0"));
    }

    @Test
    void inputEndingWithoutExit_throwsNoSuchElementException() {
        // ODDITY: scanner.nextLine() sits outside the try block, so running out of
        // input crashes start() instead of exiting cleanly.
        assertThrows(NoSuchElementException.class, () -> run("99"));
    }

    // ---------------------------------------------------------------- option 1: register

    @Test
    void registerMember_happyPath_printsIdAndWelcomeCredits() {
        String expected = BANNER
                + iteration("Enter name: Enter email: "
                + "Member registered successfully! Assigned ID: M1 with 120 welcome credits.\n")
                + iteration(GOODBYE);

        assertEquals(expected, run("1", "Alice", "alice@example.com", "0"));
        assertEquals(120, service.getMembers().get("M1").getBalance());
    }

    @Test
    void registerTwoMembers_idsSkipBecauseLedgerEntriesShareTheCounter() {
        // ODDITY: one idCounter is shared by members, ledger entries, offers, requests
        // and engagements, so the second member is M3 (L2 was used by the welcome-grant entry).
        String output = run("1", "Alice", "a@example.com", "1", "Bob", "b@example.com", "0");

        assertTrue(output.contains("Assigned ID: M1 with 120 welcome credits."));
        assertTrue(output.contains("Assigned ID: M3 with 120 welcome credits."));
    }

    // ---------------------------------------------------------------- option 2: suspend

    @Test
    void suspendMember_thenPublishOffer_isRejectedAsSuspended() {
        String output = run(
                "1", "Alice", "a@example.com",
                "2", "M1", "true",
                "3", "M1", "Gardening",
                "0");

        assertTrue(output.contains("Member M1 suspension status updated to: true"));
        assertTrue(output.contains("Error: Member is suspended and cannot perform this operation (R6)."));
        assertEquals(MemberStatus.SUSPENDED, service.getMembers().get("M1").getStatus());
    }

    @Test
    void suspendWithNonBooleanText_silentlyMeansFalse() {
        // ODDITY: Boolean.parseBoolean("yes") is false, so "yes" does NOT suspend and
        // the confirmation message reports "false" without any warning.
        String output = run("1", "Alice", "a@example.com", "2", "M1", "yes", "0");

        assertTrue(output.contains("Member M1 suspension status updated to: false"));
        assertEquals(MemberStatus.ACTIVE, service.getMembers().get("M1").getStatus());
    }

    // ---------------------------------------------------------------- options 3 and 4: error paths

    @Test
    void publishOffer_unknownMember_printsErrorAndContinues() {
        String output = run("3", "M99", "Gardening", "0");

        assertTrue(output.contains("Error: Member not found: M99"));
        assertTrue(output.contains(GOODBYE));
    }

    @Test
    void raiseRequest_unparseableDate_printsJdkParseMessage() {
        // The date is parsed in ConsoleApp, before the service is ever called.
        String output = run("4", "M1", "Gardening", "not-a-date", "0");

        assertTrue(output.contains("Error: Text 'not-a-date' could not be parsed at index 0"));
        assertTrue(output.contains(GOODBYE));
    }

    // ---------------------------------------------------------------- option 6: confirm (levy, R4/R10)

    @Test
    void confirmSession_bothSidesConfirm_transfersCostPlusLevy() {
        Member provider = service.registerMember("Alice", "a@example.com");
        Member requester = service.registerMember("Bob", "b@example.com");
        Offering offering = service.publishOffer(provider.getId(), anySkill());
        Request request = service.raiseRequest(
                requester.getId(), anySkill(), LocalDate.now(), LocalDate.now().plusDays(7));
        Engagement engagement = service.proposeEngagement(
                offering.getId(), request.getId(), LocalDateTime.now().plusDays(3), 60);

        String output = run(
                "6", engagement.getId(), provider.getId(), "true",
                "6", engagement.getId(), requester.getId(), "false",
                "0");

        assertTrue(output.contains("Confirmation recorded successfully."));
        // 60 minutes -> cost 60, levy floor(60 * 0.05) = 3 paid by requester on top
        assertEquals(180, provider.getBalance());
        assertEquals(57, requester.getBalance());
        assertEquals(3, service.getCommunityFund().getBalance());
        assertEquals(240, service.getTotalCreditsInCirculation());
    }

    // ---------------------------------------------------------------- option 7: cancel (R14)

    @Test
    void cancelEngagement_lessThan24HoursBefore_recordsLateCancellation() {
        Member provider = service.registerMember("Alice", "a@example.com");
        Member requester = service.registerMember("Bob", "b@example.com");
        Offering offering = service.publishOffer(provider.getId(), anySkill());
        Request request = service.raiseRequest(
                requester.getId(), anySkill(), LocalDate.now(), LocalDate.now().plusDays(7));
        Engagement engagement = service.proposeEngagement(
                offering.getId(), request.getId(), LocalDateTime.now().plusHours(1), 60);

        String output = run("7", engagement.getId(), provider.getId(), "0");

        assertTrue(output.contains("Engagement cancelled."));
        assertEquals(EngagementStatus.CANCELLED, engagement.getStatus());
        assertEquals(1, provider.getLateCancellationCount());
        assertEquals(120, provider.getBalance());
        assertEquals(120, requester.getBalance());
    }

    // ---------------------------------------------------------------- option 8: grant (R11)

    @Test
    void applyForGrant_memberWithPositiveBalance_isRejectedWithCombinedMessage() {
        Member member = service.registerMember("Alice", "a@example.com");

        String output = run("8", member.getId(), "50", "0");

        // ODDITY: one message covers both "already granted this month" and "balance > 0".
        assertTrue(output.contains(
                "Error: Member is not eligible for a community fund grant this month or balance is > 0 (R11)."));
    }

    @Test
    void applyForGrant_nonNumericAmount_printsNumberFormatMessage() {
        String output = run("8", "M1", "abc", "0");

        assertTrue(output.contains("Error: For input string: \"abc\""));
        assertTrue(output.contains(GOODBYE));
    }

    // ---------------------------------------------------------------- options 9 and 10: reports

    @Test
    void queryBalancesAndHistory_onEmptySystem_printsOnlyHeadings() {
        String expected = BANNER
                + iteration("\n--- MEMBERS & BALANCES ---\n\n--- LEDGER ENTRIES ---\n")
                + iteration(GOODBYE);

        assertEquals(expected, run("9", "0"));
    }

    @Test
    void systemReport_onEmptySystem_printsZeroTotals() {
        String expected = BANNER
                + iteration("\n--- SYSTEM REPORT ---\n"
                + "Total Credits in Circulation (Members + Fund): 0\n"
                + "Community Fund Balance: 0 credits\n"
                + "Total Members Registered: 0\n"
                + "Total Active Offerings: 0\n"
                + "Total Active Requests: 0\n")
                + iteration(GOODBYE);

        assertEquals(expected, run("10", "0"));
    }
}