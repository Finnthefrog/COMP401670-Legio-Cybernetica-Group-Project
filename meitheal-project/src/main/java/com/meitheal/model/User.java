package com.meitheal.model;

import java.util.Objects;
import java.util.Optional;

/**
 * The login identity of an ordinary community member.
 *
 * <p>A login answers "who is acting right now?" and exposes the capabilities that
 * vary between identities, so callers ask the identity object instead of testing
 * its concrete type.</p>
 */
public class User {
    private final String loginId;
    private final String displayName;
    private final Member member;

    /**
     * Creates an ordinary member login. Such a login always acts as a member.
     */
    public User(String loginId, String displayName, Member member) {
        this(loginId, displayName, member, true);
    }

    /**
     * Constructor for subclasses whose login does not act as a member (for example
     * {@link AdminUser}). {@code requireMember} keeps the ordinary public contract
     * strict while allowing an administrator login to have no linked member.
     */
    protected User(String loginId, String displayName, Member member, boolean requireMember) {
        this.loginId = Objects.requireNonNull(loginId, "Login ID cannot be null");
        this.displayName = Objects.requireNonNull(displayName, "Display name cannot be null");
        this.member = requireMember
                ? Objects.requireNonNull(member, "An ordinary user must act as a member")
                : member;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * The member this login acts as, if any.
     */
    public Optional<Member> linkedMember() {
        return Optional.ofNullable(member);
    }

    /**
     * Whether this identity is allowed to suspend or reinstate members.
     */
    public boolean canSuspendMembers() {
        return false;
    }

    /**
     * Whether this identity may inspect the given member's record.
     * An ordinary member may only inspect their own record (R12).
     */
    public boolean canInspect(Member other) {
        return member != null && member.equals(other);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return loginId.equals(user.loginId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loginId);
    }

    @Override
    public String toString() {
        return displayName + " (" + loginId + ")";
    }
}
