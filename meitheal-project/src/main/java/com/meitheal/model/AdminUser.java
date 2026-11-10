package com.meitheal.model;

/**
 * The operator/admin login identity at the CLI.
 *
 * <p>An administrator may suspend members and inspect the whole ledger, but has
 * no time-bank account and therefore cannot move credits, propose engagements or
 * confirm sessions. Those operations remain member actions.</p>
 */
public class AdminUser extends User {

    public AdminUser(String loginId, String displayName) {
        super(loginId, displayName, null);
    }

    @Override
    public boolean canSuspendMembers() {
        return true;
    }

    @Override
    public boolean canInspect(Member other) {
        return true;
    }

    /**
     * An administrator has no time-bank account and may not act as a member.
     */
    @Override
    public boolean canActAsMember() {
        return false;
    }
}
