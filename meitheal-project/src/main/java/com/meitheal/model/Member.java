package com.meitheal.model;

import java.util.Objects;

public class Member {
    private final String id;
    private final String name;
    private final String email;
    private int balance;
    private MemberStatus status;
    private int lateCancellationCount;

    public Member(String id, String name, String email, int initialBalance) {
        this.id = Objects.requireNonNull(id, "Member ID cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.email = Objects.requireNonNull(email, "Email cannot be null");
        this.balance = initialBalance;
        this.status = MemberStatus.ACTIVE;
        this.lateCancellationCount = 0;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public int getBalance() {
        return balance;
    }

    public void updateBalance(int delta) {
        int newBalance = this.balance + delta;
        if (newBalance < -180) {
            throw new IllegalArgumentException("Operation violates overdraft floor of -180 credits. New balance would be: " + newBalance);
        }
        this.balance = newBalance;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public void setStatus(MemberStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public int getLateCancellationCount() {
        return lateCancellationCount;
    }

    public void incrementLateCancellationCount() {
        this.lateCancellationCount++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Member member = (Member) o;
        return id.equals(member.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Member{id='" + id + "', name='" + name + "', email='" + email + "', balance=" + balance + ", status=" + status + "}";
    }
}
