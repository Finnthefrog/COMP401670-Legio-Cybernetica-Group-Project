package com.meitheal.model;

import java.util.Objects;

/**
 * The login identity of an ordinary community member.
 *
 * <p>A community login always acts as a member (a time-bank account). It may
 * inspect only its own record and may not suspend or reinstate anybody.</p>
 */
public class CommunityUser extends User {

    /**
     * Creates an ordinary member login. Such a login always acts as somebody.
     */
    public CommunityUser(String loginId, String displayName, Member member) {
        super(loginId, displayName, Objects.requireNonNull(member, "A community user must act as a member"));
    }

    @Override
    public boolean canSuspendMembers() {
        return false;
    }

    /**
     * R12 / INV-12: a member may inspect only their own record, never another member's.
     */
    @Override
    public boolean canInspect(Member other) {
        return linkedMember().filter(member -> member.equals(other)).isPresent();
    }

    @Override
    public boolean canActAsMember() {
        return true;
    }

    /**
     * INV-6 (R6): surfaces the linked member's status so the CLI can refuse early.
     * Enforcement of the suspension rule stays in {@code TimeBankService}; this
     * login must not change its own status.
     */
    public MemberStatus getMemberStatus() {
        return linkedMember().orElseThrow().getStatus();
    }
}
