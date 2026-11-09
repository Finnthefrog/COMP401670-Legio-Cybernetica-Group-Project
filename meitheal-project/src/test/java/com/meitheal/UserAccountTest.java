package com.meitheal;

import com.meitheal.model.AdminUser;
import com.meitheal.model.Member;
import com.meitheal.model.MemberStatus;
import com.meitheal.model.User;
import com.meitheal.service.TimeBankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserAccountTest {

    private TimeBankService service;

    @BeforeEach
    void setUp() {
        service = new TimeBankService();
    }

    @Test
    void suspensionCapabilityIsAnsweredThroughAUserReference() {
        Member member = service.registerMember("Alice Smith", "alice@example.com");
        User ordinary = new User("alice", "Alice Smith", member);
        User administrator = new AdminUser("admin", "System Administrator");

        assertFalse(ordinary.canSuspendMembers(), "An ordinary member cannot suspend members");
        assertTrue(administrator.canSuspendMembers(), "An administrator can suspend members");
    }

    @Test
    void ordinaryMemberCannotSuspendButAdminCan() {
        Member target = service.registerMember("Target Member", "target@example.com");
        Member actorMember = service.registerMember("Acting Member", "actor@example.com");
        User ordinary = new User("actor", "Acting Member", actorMember);
        User administrator = new AdminUser("admin", "System Administrator");

        assertThrows(SecurityException.class,
                () -> service.suspendMember(ordinary, target.getId(), true),
                "The acting-user overload must reject an ordinary member");
        assertEquals(MemberStatus.ACTIVE, target.getStatus(),
                "A rejected suspension must not change the target member");

        service.suspendMember(administrator, target.getId(), true);
        assertEquals(MemberStatus.SUSPENDED, target.getStatus(),
                "An administrator may suspend a member");

        service.suspendMember(administrator, target.getId(), false);
        assertEquals(MemberStatus.ACTIVE, target.getStatus(),
                "An administrator may reinstate a member");
    }

    @Test
    void inspectionIsScopedToOwnRecordUnlessAdmin() {
        Member own = service.registerMember("Own Member", "own@example.com");
        Member other = service.registerMember("Other Member", "other@example.com");
        User ordinary = new User("own", "Own Member", own);
        User administrator = new AdminUser("admin", "System Administrator");

        assertTrue(ordinary.canInspect(own), "A member may inspect their own record");
        assertFalse(ordinary.canInspect(other), "A member may not inspect another member's record");

        assertTrue(administrator.canInspect(own), "An administrator may inspect any member");
        assertTrue(administrator.canInspect(other), "An administrator may inspect any member");
    }

    @Test
    void onlyOrdinaryUsersHaveALinkedMember() {
        Member member = service.registerMember("Linked Member", "linked@example.com");
        User ordinary = new User("linked", "Linked Member", member);
        AdminUser administrator = new AdminUser("admin", "System Administrator");

        assertSame(member, ordinary.linkedMember().orElseThrow(),
                "An ordinary login acts as its member");
        assertTrue(administrator.linkedMember().isEmpty(),
                "An administrator has no time-bank account");
    }

    @Test
    void usersAreEqualByLoginId() {
        Member firstMember = service.registerMember("First", "first@example.com");
        Member secondMember = service.registerMember("Second", "second@example.com");

        User first = new User("shared-login", "First Name", firstMember);
        User second = new User("shared-login", "Different Name", secondMember);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new User("different-login", "First Name", firstMember));
    }
}
