package com.meitheal.model;

import java.util.Objects;

public class Offering {
    private final String id;
    private final Member provider;
    private final String skillCategory;
    private boolean active;

    public Offering(String id, Member provider, String skillCategory) {
        this.id = Objects.requireNonNull(id);
        this.provider = Objects.requireNonNull(provider);
        this.skillCategory = Objects.requireNonNull(skillCategory);
        this.active = true;
    }

    public String getId() {
        return id;
    }

    public Member getProvider() {
        return provider;
    }

    public String getSkillCategory() {
        return skillCategory;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Offering offering = (Offering) o;
        return id.equals(offering.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
