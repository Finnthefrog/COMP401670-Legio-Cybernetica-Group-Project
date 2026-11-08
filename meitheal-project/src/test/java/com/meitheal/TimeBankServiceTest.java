package com.meitheal;

import com.meitheal.model.*;
import com.meitheal.service.TimeBankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class TimeBankServiceTest {

    private TimeBankService service;

    @BeforeEach
    void setUp() {
        service = new TimeBankService();
    }

    @Test
    void testMemberRegistrationWelcomeGrant() {
        Member m = service.registerMember("Alice Smith", "alice@example.com");
        assertEquals(120, m.getBalance(), "New member should receive 120 welcome credits (R3)");
        assertEquals(MemberStatus.ACTIVE, m.getStatus());
    }

    @Test
    void testOverdraftFloorEnforcement() {
        Member m = service.registerMember("Bob", "bob@example.com");
        // Bob has 120 credits. Try to deduct more than 120 + 180 = 300 credits.
        assertThrows(IllegalArgumentException.class, () -> {
            m.updateBalance(-301);
        }, "Balance cannot go below -180 floor (R2)");
    }

    @Test
    void testDualConfirmationAndLevyTransfer() {
        Member provider = service.registerMember("Charlie", "charlie@example.com"); // 120 credits
        Member requester = service.registerMember("Diana", "diana@example.com"); // 120 credits

        Offering offering = service.publishOffer(provider.getId(), "Gardening");
        Request request = service.raiseRequest(requester.getId(), "Gardening", LocalDate.now().minusDays(1), LocalDate.now().plusDays(5));

        LocalDateTime sessionTime = LocalDateTime.now().plusDays(1);
        Engagement engagement = service.proposeEngagement(offering.getId(), request.getId(), sessionTime, 60);

        // First confirmation by provider
        service.confirmEngagementSession(engagement.getId(), provider.getId(), true);
        assertEquals(120, requester.getBalance(), "Transfer should not happen on single confirmation (R4)");

        // Second confirmation by requester
        service.confirmEngagementSession(engagement.getId(), requester.getId(), false);

        // 60 min session cost = 60 credits. 5% levy = 3 credits. Requester pays 63 credits total.
        // Requester balance: 120 - 63 = 57. Provider balance: 120 + 60 = 180. Community fund: 3.
        assertEquals(57, requester.getBalance());
        assertEquals(180, provider.getBalance());
        assertEquals(3, service.getCommunityFund().getBalance());
    }

    @Test
    void testSelfFulfillmentProhibited() {
        Member member = service.registerMember("Eve", "eve@example.com");
        Offering offering = service.publishOffer(member.getId(), "Tutor");
        Request request = service.raiseRequest(member.getId(), "Tutor", LocalDate.now(), LocalDate.now().plusDays(2));

        assertThrows(IllegalArgumentException.class, () -> {
            service.proposeEngagement(offering.getId(), request.getId(), LocalDateTime.now().plusDays(1), 60);
        }, "Member cannot fulfill their own request (R5)");
    }

    @Test
    void testSessionDurationBounds() {
        Member provider = service.registerMember("Frank", "frank@example.com");
        Member requester = service.registerMember("Grace", "grace@example.com");
        Offering offering = service.publishOffer(provider.getId(), "Cooking");
        Request request = service.raiseRequest(requester.getId(), "Cooking", LocalDate.now(), LocalDate.now().plusDays(2));

        // Duration 0 or > 480 (8 hours) should throw exception (R8)
        assertThrows(IllegalArgumentException.class, () -> {
            new Engagement("ENG99", offering, request, LocalDateTime.now().plusDays(1), 0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new Engagement("ENG100", offering, request, LocalDateTime.now().plusDays(1), 481);
        });
    }

    @Test
    void testCommunityFundGrantEligibility() {
        Member member = service.registerMember("Hank", "hank@example.com");
        // Hank has 120 credits, balance > 0 so cannot receive community fund grant (R11)
        assertFalse(service.getCommunityFund().canReceiveGrant(member, YearMonth.now()));

        // Push Hank's balance to -10
        member.updateBalance(-130); // 120 - 130 = -10
        assertTrue(service.getCommunityFund().canReceiveGrant(member, YearMonth.now()));
    }
}
