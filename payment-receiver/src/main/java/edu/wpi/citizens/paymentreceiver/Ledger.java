package edu.wpi.citizens.paymentreceiver;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/** Fake in-memory ledger. Entries are lost on restart. */
@Component
public class Ledger {

    private final List<LedgerEntry> entries = new CopyOnWriteArrayList<>();

    public void post(LedgerEntry entry) {
        entries.add(entry);
    }

    public List<LedgerEntry> entries() {
        return List.copyOf(entries);
    }
}
