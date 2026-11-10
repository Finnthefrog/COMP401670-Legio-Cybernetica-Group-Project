package com.meitheal.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Abstract login identity for the Meitheal CLI.
 *
 * <p>A login answers "who is acting right now?" and declares the capabilities
 * that vary between identities, so callers ask the identity object instead of
 * testing its concrete type.</p>
 */
public abstract class User {
    private final String loginId;
    private final String displayName;
    private final Member member;

    /**
     * Base constructor for login identities. Subclasses decide whether a linked
     * member is required: a community login supplies one, an administrator does not.
     */
    protected User(String loginId, String displayName, Member member) {
        this.loginId = Objects.requireNonNull(loginId, "Login ID cannot be null");
        this.displayName = Objects.requireNonNull(displayName, "Display name cannot be null");
        this.member = member;
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
    public abstract boolean canSuspendMembers();

    /**
     * Whether this identity may inspect the given member's record.
     */
    public abstract boolean canInspect(Member other);

    /**
     * Whether this identity is a time-bank account that may perform member actions.
     */
    public abstract boolean canActAsMember();

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
