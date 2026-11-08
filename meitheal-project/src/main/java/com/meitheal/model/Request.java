package com.meitheal.model;

import java.time.LocalDate;
import java.util.Objects;

public class Request {
    private final String id;
    private final Member requester;
    private final String skillCategory;
    private final LocalDate earliestDate;
    private final LocalDate latestDate;
    private boolean active;

    public Request(String id, Member requester, String skillCategory, LocalDate earliestDate, LocalDate latestDate) {
        this.id = Objects.requireNonNull(id);
        this.requester = Objects.requireNonNull(requester);
        this.skillCategory = Objects.requireNonNull(skillCategory);
        this.earliestDate = Objects.requireNonNull(earliestDate);
        this.latestDate = Objects.requireNonNull(latestDate);
        if (latestDate.isBefore(earliestDate)) {
            throw new IllegalArgumentException("Latest date cannot be before earliest date in fulfillment window.");
        }
        this.active = true;
    }

    public String getId() {
        return id;
    }

    public Member getRequester() {
        return requester;
    }

    public String getSkillCategory() {
        return skillCategory;
    }

    public LocalDate getEarliestDate() {
        return earliestDate;
    }

    public LocalDate getLatestDate() {
        return latestDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDateWithinWindow(LocalDate date) {
        return !date.isBefore(earliestDate) && !date.isAfter(latestDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Request request = (Request) o;
        return id.equals(request.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
