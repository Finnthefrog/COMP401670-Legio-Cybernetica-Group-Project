package com.meitheal.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Engagement {
    private final String id;
    private final Offering offering;
    private final Request request;
    private final LocalDateTime sessionDateTime;
    private final int durationMinutes; // R8: 0 < duration <= 480
    private EngagementStatus status;
    private boolean providerConfirmed;
    private boolean requesterConfirmed;

    public Engagement(String id, Offering offering, Request request, LocalDateTime sessionDateTime, int durationMinutes) {
        this.id = Objects.requireNonNull(id);
        this.offering = Objects.requireNonNull(offering);
        this.request = Objects.requireNonNull(request);
        this.sessionDateTime = Objects.requireNonNull(sessionDateTime);
        this.durationMinutes = durationMinutes;

        if (durationMinutes <= 0 || durationMinutes > 480) {
            throw new IllegalArgumentException("Session duration must be greater than 0 and no longer than 8 hours (480 minutes). Got: " + durationMinutes);
        }

        if (!offering.getSkillCategory().equals(request.getSkillCategory())) {
            throw new IllegalArgumentException("Offering and Request skill categories must match.");
        }

        if (offering.getProvider().equals(request.getRequester())) {
            throw new IllegalArgumentException("A member cannot create an engagement for their own request/offer (R5).");
        }

        if (!request.isDateWithinWindow(sessionDateTime.toLocalDate())) {
            throw new IllegalArgumentException("Session date must fall within the request fulfillment window (R9).");
        }

        this.status = EngagementStatus.PROPOSED;
        this.providerConfirmed = false;
        this.requesterConfirmed = false;
    }

    public String getId() {
        return id;
    }

    public Offering getOffering() {
        return offering;
    }

    public Request getRequest() {
        return request;
    }

    public LocalDateTime getSessionDateTime() {
        return sessionDateTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public EngagementStatus getStatus() {
        return status;
    }

    public void setStatus(EngagementStatus status) {
        this.status = status;
    }

    public boolean isProviderConfirmed() {
        return providerConfirmed;
    }

    public void setProviderConfirmed(boolean providerConfirmed) {
        this.providerConfirmed = providerConfirmed;
    }

    public boolean isRequesterConfirmed() {
        return requesterConfirmed;
    }

    public void setRequesterConfirmed(boolean requesterConfirmed) {
        this.requesterConfirmed = requesterConfirmed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Engagement that = (Engagement) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
