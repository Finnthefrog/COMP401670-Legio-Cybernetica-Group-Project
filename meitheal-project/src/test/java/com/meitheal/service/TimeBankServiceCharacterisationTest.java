package com.meitheal.service;

import com.meitheal.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TimeBankServiceCharacterisationTest {

    private TimeBankService service;
    private Member alice;   // provider in most tests
    private Member bob;     // requester in most tests
    private String skillA;
    private String skillB;

    @BeforeEach
    void setUp() {
        service = new TimeBankService();
        List<String> skills = service.getValidSkillCategories().stream()
                .filter(s -> !s.isEmpty())
                .sorted()
                .collect(Collectors.toList());
        skillA = skills.get(0);
        skillB = skills.get(1);
        alice = service.registerMember("Alice", "alice@example.com"); // M1 (welcome ledger entry takes L2)
        bob = service.registerMember("Bob", "bob@example.com");       // M3 (welcome ledger entry takes L4)
    }

    // ---------- helpers ----------

    private static String lastKey(Map<String, ?> map) {
        String last = null;
        for (String key : map.keySet()) {
            last = key;
        }
        return last;
    }

    /** Alice offers skillA, Bob requests skillA, engagement proposed. Returns the engagement id. */
    private String engage(int minutes, LocalDateTime when) {
        service.publishOffer(alice.getId(), skillA);
        service.raiseRequest(bob.getId(), skillA, LocalDate.now(), LocalDate.now().plusDays(7));
        service.proposeEngagement(
                lastKey(service.getOfferings()), lastKey(service.getRequests()), when, minutes);
        return lastKey(service.getEngagements());
    }

    private String engage(int minutes) {
        return engage(minutes, LocalDateTime.now().plusDays(2));
    }

    private void confirmBoth(String engagementId) {
        service.confirmEngagementSession(engagementId, alice.getId(), true);
        service.confirmEngagementSession(engagementId, bob.getId(), false);
    }

    private Engagement engagement(String id) {
        return service.getEngagements().get(id);
    }

    /** Single place to adapt if the getter on Member is named differently. */
    private int lateCancellations(Member m) {
        return m.getLateCancellationCount();
    }

    // ---------- registration and ID allocation ----------

    @Test
    void pinsWelcomeGrantOf120() {
        assertEquals(120, alice.getBalance());
        assertEquals(120, bob.getBalance());
    }

    @Test
    void pinsSharedIdCounterBetweenMembersAndLedgerEntries() {
        // Each registration burns two ids: one for the member, one for the welcome ledger entry.
        assertEquals("M1", alice.getId());
        assertEquals("M3", bob.getId());
    }

    @Test
    void pinsIdSequenceForOfferRequestEngagement() {
        // counter is 5 after two registrations
        service.publishOffer(alice.getId(), skillA);
        service.raiseRequest(bob.getId(), skillA, LocalDate.now(), LocalDate.now().plusDays(7));
        assertTrue(service.getOfferings().containsKey("OFF5"));
        assertTrue(service.getRequests().containsKey("REQ6"));
        service.proposeEngagement("OFF5", "REQ6", LocalDateTime.now().plusDays(2), 60);
        assertTrue(service.getEngagements().containsKey("ENG7"));
    }

    @Test
    void pinsSuccessfulTransferConsumingOneLedgerId() {
        String first = engage(60);          // OFF5, REQ6, ENG7
        confirmBoth(first);                 // ledger entry takes L8
        String second = engage(60);         // OFF9, REQ10, ENG11
        assertEquals("ENG7", first);
        assertEquals("ENG11", second);
    }

    @Test
    void pinsRejectedTransferNotConsumingAnId() {
        String first = engage(287);         // OFF5, REQ6, ENG7
        assertThrows(IllegalStateException.class, () -> confirmBoth(first));
        String second = engage(60);         // OFF8, REQ9, ENG10: no id was burned by the rejection
        assertEquals("ENG10", second);
    }

    // ---------- validation and member status ----------

    @Test
    void pinsInvalidSkillRejectionMentionsR13() {
        IllegalArgumentException offer = assertThrows(IllegalArgumentException.class,
                () -> service.publishOffer(alice.getId(), "Underwater Basket Weaving"));
        assertTrue(offer.getMessage().contains("R13"));
        IllegalArgumentException request = assertThrows(IllegalArgumentException.class,
                () -> service.raiseRequest(bob.getId(), "Underwater Basket Weaving",
                        LocalDate.now(), LocalDate.now().plusDays(1)));
        assertTrue(request.getMessage().contains("R13"));
    }

    @Test
    void pinsUnknownMemberMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.publishOffer("M999", skillA));
        assertEquals("Member not found: M999", e.getMessage());
    }

    @Test
    void pinsWartSuspensionExceptionTypesDiffer() {
        // WART: publishOffer/raiseRequest throw IllegalStateException for a suspended member,
        // but proposeEngagement throws IllegalArgumentException for the same condition.
        service.publishOffer(alice.getId(), skillA);
        service.raiseRequest(bob.getId(), skillA, LocalDate.now(), LocalDate.now().plusDays(7));
        String offeringId = lastKey(service.getOfferings());
        String requestId = lastKey(service.getRequests());

        service.suspendMember(alice.getId(), true);
        assertEquals(MemberStatus.SUSPENDED, alice.getStatus());
        assertThrows(IllegalStateException.class, () -> service.publishOffer(alice.getId(), skillA));
        assertThrows(IllegalStateException.class, () -> service.raiseRequest(
                alice.getId(), skillA, LocalDate.now(), LocalDate.now().plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.proposeEngagement(
                offeringId, requestId, LocalDateTime.now().plusDays(2), 60));
    }

    @Test
    void pinsUnknownOfferingOrRequestRejected() {
        service.publishOffer(alice.getId(), skillA);
        service.raiseRequest(bob.getId(), skillA, LocalDate.now(), LocalDate.now().plusDays(7));
        String offeringId = lastKey(service.getOfferings());
        String requestId = lastKey(service.getRequests());
        assertThrows(IllegalArgumentException.class, () -> service.proposeEngagement(
                "OFF999", requestId, LocalDateTime.now().plusDays(2), 60));
        assertThrows(IllegalArgumentException.class, () -> service.proposeEngagement(
                offeringId, "REQ999", LocalDateTime.now().plusDays(2), 60));
    }

    @Test
    void pinsWartNoSkillMatchCheckBetweenOfferingAndRequest() {
        // WART: an offering for skillA can be engaged against a request for skillB.
        service.publishOffer(alice.getId(), skillA);
        service.raiseRequest(bob.getId(), skillB, LocalDate.now(), LocalDate.now().plusDays(7));
        Engagement e = service.proposeEngagement(
                lastKey(service.getOfferings()), lastKey(service.getRequests()),
                LocalDateTime.now().plusDays(2), 60);
        assertEquals(EngagementStatus.PROPOSED, e.getStatus());
    }

    @Test
    void pinsWartSelfEngagementAllowedAndCostsOnlyTheLevy() {
        // WART: a member can offer to and request from themselves. Net effect is just the levy.
        service.publishOffer(alice.getId(), skillA);
        service.raiseRequest(alice.getId(), skillA, LocalDate.now(), LocalDate.now().plusDays(7));
        service.proposeEngagement(lastKey(service.getOfferings()), lastKey(service.getRequests()),
                LocalDateTime.now().plusDays(2), 60);
        String id = lastKey(service.getEngagements());
        service.confirmEngagementSession(id, alice.getId(), true);
        service.confirmEngagementSession(id, alice.getId(), false);
        assertEquals(117, alice.getBalance());           // 120 - 63 + 60
        assertEquals(3, service.getCommunityFund().getBalance());
    }

    // ---------- transfer, levy, overdraft floor ----------

    @Test
    void pinsStandardTransferOf60MinutesWithLevy() {
        String id = engage(60);
        confirmBoth(id);
        assertEquals(57, bob.getBalance());               // 120 - (60 + 3)
        assertEquals(180, alice.getBalance());            // 120 + 60
        assertEquals(3, service.getCommunityFund().getBalance());
        assertEquals(EngagementStatus.CONFIRMED, engagement(id).getStatus());
    }

    @Test
    void pinsLevyRoundsDownToZeroUnder20Minutes() {
        confirmBoth(engage(19));
        assertEquals(101, bob.getBalance());              // 120 - 19, levy 0
        assertEquals(139, alice.getBalance());
        assertEquals(0, service.getCommunityFund().getBalance());
    }

    @Test
    void pinsLevyOfOneAt20Minutes() {
        confirmBoth(engage(20));
        assertEquals(99, bob.getBalance());               // 120 - 21
        assertEquals(1, service.getCommunityFund().getBalance());
    }

    @Test
    void pinsOverdraftFloorBoundaryExactlyMinus180Allowed() {
        confirmBoth(engage(286));                         // cost 286 + levy 14 = 300
        assertEquals(-180, bob.getBalance());
        assertEquals(406, alice.getBalance());
        assertEquals(14, service.getCommunityFund().getBalance());
    }

    @Test
    void pinsOverdraftFloorBreachRejectedAt287Minutes() {
        String id = engage(287);                          // cost 287 + levy 14 = 301 -> -181
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> confirmBoth(id));
        assertTrue(e.getMessage().contains("overdraft floor"));
        assertEquals(120, bob.getBalance());
        assertEquals(120, alice.getBalance());
        assertEquals(0, service.getCommunityFund().getBalance());
    }

    @Test
    void pinsWartRejectedTransferLeavesConfirmationFlagsSetAndStatusProposed() {
        // WART: the flags are set before the transfer is attempted and never rolled back.
        String id = engage(287);
        assertThrows(IllegalStateException.class, () -> confirmBoth(id));
        assertTrue(engagement(id).isProviderConfirmed());
        assertTrue(engagement(id).isRequesterConfirmed());
        assertEquals(EngagementStatus.PROPOSED, engagement(id).getStatus());
    }

    @Test
    void pinsWartNegativeDurationAcceptedAndReversesTransfer() {
        // WART: no duration validation. Levy becomes floor(-3.0) = -3, deduction -63.
        // VERIFY BY RUNNING: the fund assertion depends on CommunityFund.addCredits(-3).
        String id = engage(-60);
        confirmBoth(id);
        assertEquals(183, bob.getBalance());              // 120 - (-63)
        assertEquals(60, alice.getBalance());             // 120 + (-60)
        // assertEquals(-3, service.getCommunityFund().getBalance());  // observe, then pin
    }

    // ---------- confirmation rules ----------

    @Test
    void pinsConfirmationByNonPartyRejected() {
        String id = engage(60);
        assertThrows(IllegalArgumentException.class,
                () -> service.confirmEngagementSession(id, bob.getId(), true));   // bob is not the provider
        assertThrows(IllegalArgumentException.class,
                () -> service.confirmEngagementSession(id, alice.getId(), false)); // alice is not the requester
        assertFalse(engagement(id).isProviderConfirmed());
        assertFalse(engagement(id).isRequesterConfirmed());
    }

    @Test
    void pinsNoTransferUntilBothConfirm() {
        String id = engage(60);
        service.confirmEngagementSession(id, alice.getId(), true);
        assertEquals(EngagementStatus.PROPOSED, engagement(id).getStatus());
        assertEquals(120, bob.getBalance());
        assertEquals(120, alice.getBalance());
    }

    @Test
    void pinsConfirmingAgainAfterConfirmedDoesNotTransferTwice() {
        String id = engage(60);
        confirmBoth(id);
        service.confirmEngagementSession(id, alice.getId(), true);
        service.confirmEngagementSession(id, bob.getId(), false);
        assertEquals(57, bob.getBalance());
        assertEquals(180, alice.getBalance());
        assertEquals(3, service.getCommunityFund().getBalance());
    }

    // ---------- cancellation ----------

    @Test
    void pinsLateCancellationUnder24HoursIsRecordedAgainstCancellingMember() {
        String id = engage(60, LocalDateTime.now().plusHours(1));
        service.cancelEngagement(id, bob.getId());
        assertEquals(EngagementStatus.CANCELLED, engagement(id).getStatus());
        assertEquals(1, lateCancellations(bob));
        assertEquals(0, lateCancellations(alice));
        assertEquals(120, bob.getBalance());              // no credits move
    }

    @Test
    void pinsEarlyCancellationNotRecordedAsLate() {
        String id = engage(60, LocalDateTime.now().plusHours(48));
        service.cancelEngagement(id, bob.getId());
        assertEquals(EngagementStatus.CANCELLED, engagement(id).getStatus());
        assertEquals(0, lateCancellations(bob));
    }

    @Test
    void pinsWartAnyExistingMemberCanCancelAnyEngagement() {
        // WART: no check that the cancelling member is a party to the engagement.
        Member carol = service.registerMember("Carol", "carol@example.com");
        String id = engage(60, LocalDateTime.now().plusHours(48));
        service.cancelEngagement(id, carol.getId());
        assertEquals(EngagementStatus.CANCELLED, engagement(id).getStatus());
    }

    @Test
    void pinsWartCancellingConfirmedEngagementRefundsNothing() {
        // WART: a completed transfer is not reversed by cancellation.
        String id = engage(60, LocalDateTime.now().plusHours(48));
        confirmBoth(id);
        service.cancelEngagement(id, bob.getId());
        assertEquals(EngagementStatus.CANCELLED, engagement(id).getStatus());
        assertEquals(57, bob.getBalance());
        assertEquals(180, alice.getBalance());
    }

    @Test
    void pinsCancelTwiceAndConfirmAfterCancelRejected() {
        String id = engage(60, LocalDateTime.now().plusHours(48));
        service.cancelEngagement(id, bob.getId());
        assertThrows(IllegalArgumentException.class, () -> service.cancelEngagement(id, bob.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> service.confirmEngagementSession(id, alice.getId(), true));
    }

    // ---------- invariants and accessors ----------

    @Test
    void pinsCreditsInCirculationEquals120PerMemberAfterTransfers() {
        confirmBoth(engage(60));
        confirmBoth(engage(100));
        assertEquals(240, service.getTotalCreditsInCirculation());
    }

    @Test
    void pinsReadOnlyAccessorsAreUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> service.getValidSkillCategories().add("Hacking"));
        assertThrows(UnsupportedOperationException.class, () -> service.getMembers().clear());
        assertThrows(UnsupportedOperationException.class, () -> service.getOfferings().clear());
        assertThrows(UnsupportedOperationException.class, () -> service.getEngagements().clear());
    }


}
