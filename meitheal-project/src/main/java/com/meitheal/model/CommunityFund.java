package com.meitheal.model;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

public class CommunityFund {
    private int balance;
    // Track member grants per calendar month: Map<MemberId, Set<YearMonth>> or Map<MemberId, YearMonth>
    private final Map<String, YearMonth> lastGrantMonthMap = new HashMap<>();

    public CommunityFund(int initialBalance) {
        this.balance = initialBalance;
    }

    public int getBalance() {
        return balance;
    }

    public void addCredits(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot add negative amount to community fund.");
        }
        this.balance += amount;
    }

    public void deductCredits(int amount) {
        if (amount < 0 || amount > balance) {
            throw new IllegalArgumentException("Community fund cannot grant more credits than it holds or negative amount.");
        }
        this.balance -= amount;
    }

    public boolean canReceiveGrant(Member member, YearMonth currentMonth) {
        if (member.getBalance() > 0) {
            return false; // R11: Member balance must be below or equal to zero
        }
        YearMonth lastGrant = lastGrantMonthMap.get(member.getId());
        if (lastGrant != null && lastGrant.equals(currentMonth)) {
            return false; // R11: At most one grant per calendar month
        }
        return true;
    }

    public void recordGrant(Member member, YearMonth currentMonth) {
        lastGrantMonthMap.put(member.getId(), currentMonth);
    }
}
