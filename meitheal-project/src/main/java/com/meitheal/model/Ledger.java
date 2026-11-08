package com.meitheal.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Ledger {
    private final List<LedgerEntry> entries = new ArrayList<>();

    public void addEntry(LedgerEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Ledger entry cannot be null");
        }
        entries.add(entry); // Append-only
    }

    public List<LedgerEntry> getEntries() {
        // R12: Return unmodifiable view to prevent internal collection modification
        return Collections.unmodifiableList(entries);
    }
}
