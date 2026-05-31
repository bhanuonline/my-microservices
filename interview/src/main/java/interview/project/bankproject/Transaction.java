package interview.project.bankproject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public class Transaction {

    // ── auto ID generator (thread safe) ──────────────
    private static final AtomicInteger counter = new AtomicInteger(1000);

    // ── variables ─────────────────────────────────────
    private final String txnId;
    private final String type;
    private final double amount;
    private final double balanceAfter;
    private final String timestamp;
    private String status;

    // ── types ─────────────────────────────────────────
    public static final String DEPOSIT       = "DEPOSIT";
    public static final String WITHDRAW      = "WITHDRAW";
    public static final String TRANSFER_IN   = "TRANSFER_IN";
    public static final String TRANSFER_OUT  = "TRANSFER_OUT";
    public static final String REVERSAL      = "REVERSAL";

    // ── status ────────────────────────────────────────
    public static final String SUCCESS  = "SUCCESS";
    public static final String FAILED   = "FAILED";
    public static final String REVERSED = "REVERSED";

    // ── constructor ───────────────────────────────────
    public Transaction(String type, double amount, double balanceAfter) {
        this.txnId        = "TXN" + counter.incrementAndGet(); // TXN1001, TXN1002...
        this.type         = type;
        this.amount       = amount;
        this.balanceAfter = balanceAfter;
        this.timestamp    = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
        this.status       = SUCCESS;
    }

    // ── getters ───────────────────────────────────────
    public String getTxnId()        { return txnId; }
    public String getType()         { return type; }
    public double getAmount()       { return amount; }
    public double getBalanceAfter() { return balanceAfter; }
    public String getTimestamp()    { return timestamp; }
    public String getStatus()       { return status; }

    // ── only setter — status can change ───────────────
    public void setStatus(String status) { this.status = status; }

    // ── display ───────────────────────────────────────
    @Override
    public String toString() {
        return String.format("[%s] %-12s | %-15s | Amt: %-10.2f | Bal: %-10.2f | %s",
                timestamp, txnId, type, amount, balanceAfter, status);
    }
}