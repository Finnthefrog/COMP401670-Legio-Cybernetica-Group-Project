package com.meitheal;

import com.meitheal.model.AdminUser;
import com.meitheal.model.CommunityUser;
import com.meitheal.model.Engagement;
import com.meitheal.model.EngagementStatus;
import com.meitheal.model.Member;
import com.meitheal.model.MemberStatus;
import com.meitheal.model.Offering;
import com.meitheal.model.Request;
import com.meitheal.model.User;
import com.meitheal.service.TimeBankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CommunityUserTest {

    private TimeBankService service;

    @BeforeEach
    void setUp() {
        service = new TimeBankService();
    }

    @Test
    void suspensionCapabilityIsAnsweredThroughAUserReference() {
        Member member = service.registerMember("Alice Smith", "alice@example.com");
        User community = new CommunityUser("alice", "Alice Smith", member);
        User administrator = new AdminUser("admin", "System Administrator");

        assertFalse(community.canSuspendMembers(), "A community user cannot suspend members");
        assertTrue(administrator.canSuspendMembers(), "An administrator can suspend members");
    }

    @Test
    void memberActionCapabilityIsAnsweredThroughAUserReference() {
        Member member = service.registerMember("Bob", "bob@example.com");
        User community = new CommunityUser("bob", "Bob", member);
        User administrator = new AdminUser("admin", "System Administrator");

        assertTrue(community.canActAsMember(), "A community user is a time-bank account");
        assertFalse(administrator.canActAsMember(), "An administrator has no time-bank account");
    }

    @Test
    void inspectionIsScopedToOwnRecordUnlessAdmin() {
        Member own = service.registerMember("Own Member", "own@example.com");
        Member other = service.registerMember("Other Member", "other@example.com");
        User community = new CommunityUser("own", "Own Member", own);
        User administrator = new AdminUser("admin", "System Administrator");

        assertTrue(community.canInspect(own), "A member may inspect their own record");
        assertFalse(community.canInspect(other), "A member may not inspect another member's record");

        assertTrue(administrator.canInspect(own), "An administrator may inspect any member");
        assertTrue(administrator.canInspect(other), "An administrator may inspect any member");
    }

    @Test
    void onlyCommunityUsersHaveALinkedMember() {
        Member member = service.registerMember("Linked Member", "linked@example.com");
        User community = new CommunityUser("linked", "Linked Member", member);
        User administrator = new AdminUser("admin", "System Administrator");

        assertSame(member, community.linkedMember().orElseThrow(),
                "A community login acts as its member");
        assertTrue(administrator.linkedMember().isEmpty(),
                "An administrator has no time-bank account");
    }

    @Test
    void constructorRejectsNullMember() {
        assertThrows(NullPointerException.class,
                () -> new CommunityUser("null-member", "No Member", null));
    }

    @Test
    void memberStatusReflectsServiceSuspension() {
        Member member = service.registerMember("Status Member", "status@example.com");
        CommunityUser community = new CommunityUser("status", "Status Member", member);
        User administrator = new AdminUser("admin", "System Administrator");

        assertEquals(MemberStatus.ACTIVE, community.getMemberStatus());

        service.suspendMember(administrator, member.getId(), true);
        assertEquals(MemberStatus.SUSPENDED, community.getMemberStatus(),
                "The community login surfaces the member's current status");

        service.suspendMember(administrator, member.getId(), false);
        assertEquals(MemberStatus.ACTIVE, community.getMemberStatus());
    }

    @Test
    void communityAndAdminLoginsCompareEqualBySharedLoginId() {
        Member member = service.registerMember("Shared", "shared@example.com");
        User community = new CommunityUser("shared-login", "Community", member);
        User administrator = new AdminUser("shared-login", "Administrator");

        assertEquals(community, administrator,
                "equals is deliberately by loginId across User subclasses");
        assertEquals(community.hashCode(), administrator.hashCode());
    }

    @Test
    void communityLoginCannotReachAnotherMembersCreditsOrEngagements() {
        Member firstMember = service.registerMember("First", "first@example.com");
        Member secondMember = service.registerMember("Second", "second@example.com");
        Member thirdMember = service.registerMember("Third", "third@example.com");

        Offering secondOffering = service.publishOffer(secondMember.getId(), "Gardening");
        Request thirdRequest = service.raiseRequest(thirdMember.getId(), "Gardening",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(5));
        Engagement secondEngagement = service.proposeEngagement(
                secondOffering.getId(), thirdRequest.getId(), LocalDateTime.now().plusDays(1), 60);

        CommunityUser first = new CommunityUser("first", "First", firstMember);

        assertTrue(first.canInspect(firstMember), "The login can inspect its own member");
        assertFalse(first.canInspect(secondMember),
                "The second member's credits must not be inspectable through the first login");
        assertFalse(first.canInspect(secondEngagement.getOffering().getProvider()),
                "The second member's engagements must not be inspectable through the first login");
        assertFalse(first.canInspect(secondEngagement.getRequest().getRequester()));
        assertSame(firstMember, first.linkedMember().orElseThrow());

        assertThrows(SecurityException.class,
                () -> service.suspendMember(first, secondMember.getId(), true),
                "Community authority must not reach another member");
        assertEquals(MemberStatus.ACTIVE, secondMember.getStatus());
        assertEquals(120, secondMember.getBalance());
    }

    @Test
    void suspendedProviderCanStillConfirmPreSuspensionEngagement() {
        Member provider = service.registerMember("Provider", "provider@example.com");
        Member requester = service.registerMember("Requester", "requester@example.com");

        Offering offering = service.publishOffer(provider.getId(), "Gardening");
        Request request = service.raiseRequest(requester.getId(), "Gardening",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(5));
        Engagement engagement = service.proposeEngagement(
                offering.getId(), request.getId(), LocalDateTime.now().plusDays(1), 60);

        User administrator = new AdminUser("admin", "System Administrator");
        service.suspendMember(administrator, provider.getId(), true);

        CommunityUser providerLogin = new CommunityUser("provider", "Provider", provider);
        assertEquals(MemberStatus.SUSPENDED, providerLogin.getMemberStatus());
        assertTrue(providerLogin.canActAsMember(), "Suspension does not remove the time-bank account");

        // INV-6 (R6): the suspended provider may still confirm work proposed before suspension.
        service.confirmEngagementSession(engagement.getId(), provider.getId(), true);
        service.confirmEngagementSession(engagement.getId(), requester.getId(), false);

        assertEquals(EngagementStatus.CONFIRMED, engagement.getStatus());
        assertEquals(180, provider.getBalance(),
                "The suspended provider still receives credits for pre-suspension work");
        assertEquals(57, requester.getBalance());
    }
}
