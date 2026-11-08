package com.meitheal.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class LedgerEntry {
    private final String entryId;
    private final LocalDateTime timestamp;
    private final String description;
    private final String senderId;     // null if system/fund
    private final String recipientId;  // null if system/fund
    private final int amount;
    private final int levyAmount;

    public LedgerEntry(String entryId, LocalDateTime timestamp, String description, String senderId, String recipientId, int amount, int levyAmount) {
        this.entryId = Objects.requireNonNull(entryId);
        this.timestamp = Objects.requireNonNull(timestamp);
        this.description = Objects.requireNonNull(description);
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.amount = amount;
        this.levyAmount = levyAmount;
    }

    public String getEntryId() {
        return entryId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getDescription() {
        return description;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public int getAmount() {
        return amount;
    }

    public int getLevyAmount() {
        return levyAmount;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | From: %s -> To: %s | Amount: %s | Levy: %s | Desc: %s",
                timestamp, entryId, senderId != null ? senderId : "SYSTEM", recipientId != null ? recipientId : "SYSTEM", amount, levyAmount, description);
    }
}
