package interview.project.bankproject;

import java.util.ArrayList;
import java.util.List;

public class Customer implements Runnable {

    // ── variables ─────────────────────────────────────
    private final String       custId;
    private final String       name;
    private final String       phone;
    private final Account      myAccount;
    private       Account      targetAccount;
    private final List<String> transactionLog;

    // ── constructor ───────────────────────────────────
    public Customer(String custId,
                    String name,
                    String phone,
                    Account myAccount) {
        this.custId         = custId;
        this.name           = name;
        this.phone          = phone;
        this.myAccount      = myAccount;
        this.transactionLog = new ArrayList<>();
    }

    // ══════════════════════════════════════════════════
    // THREAD ENTRY POINT
    // ══════════════════════════════════════════════════

    @Override
    public void run() {
        System.out.println("\n👤 " + name +
                " [" + Thread.currentThread().getName() + "] started banking session...");

        try {

            // Step 1 — Deposit
            doDeposit(1000);
            Thread.sleep(200);

            // Step 2 — Check Balance
            checkBalance();
            Thread.sleep(200);

            // Step 3 — Withdraw
            doWithdraw(500);
            Thread.sleep(200);

            // Step 4 — Transfer (if target exists)
            if (targetAccount != null) {
                doTransfer(targetAccount, 300);
                Thread.sleep(200);
            }

            // Step 5 — Try overdraw
            doWithdraw(99999);
            Thread.sleep(200);

            // Step 6 — Mini statement
            printMiniStatement();

        } catch (InterruptedException e) {
            System.out.println("⚠️  " + name + "'s session interrupted!");
            Thread.currentThread().interrupt();
        }

        System.out.println("👤 " + name + " finished banking session.");
        printTransactionLog();
    }

    // ══════════════════════════════════════════════════
    // BANKING ACTIONS
    // ══════════════════════════════════════════════════

    // ── DEPOSIT ───────────────────────────────────────
    public void doDeposit(double amount) {
        log("Depositing " + amount);
        myAccount.deposit(amount);
    }

    // ── WITHDRAW ──────────────────────────────────────
    public void doWithdraw(double amount) {
        log("Withdrawing " + amount);
        boolean success = myAccount.withdraw(amount);
        if (!success) log("Withdraw FAILED for " + amount);
    }

    // ── TRANSFER ──────────────────────────────────────
    public void doTransfer(Account target, double amount) {
        log("Transferring " + amount + " to " + target.getOwner());
        boolean success = myAccount.transfer(target, amount);
        if (!success) log("Transfer FAILED for " + amount);
    }

    // ── ROLLBACK ──────────────────────────────────────
    public void doRollback(String txnId) {
        log("Rolling back " + txnId);
        boolean success = myAccount.rollback(txnId);
        if (success) log("Rollback SUCCESS for " + txnId);
        else         log("Rollback FAILED for " + txnId);
    }

    // ── CHECK BALANCE ─────────────────────────────────
    public void checkBalance() {
        System.out.printf("💰 [%s] %s — Current Balance: %.2f%n",
                myAccount.getAccountNumber(),
                name,
                myAccount.getBalance());
        log("Checked balance: " + myAccount.getBalance());
    }

    // ── PRINT STATEMENT ───────────────────────────────
    public void printStatement() {
        myAccount.printStatement();
    }

    // ── PRINT MINI STATEMENT ──────────────────────────
    public void printMiniStatement() {
        myAccount.printMiniStatement();
    }

    // ══════════════════════════════════════════════════
    // INTERNAL LOG
    // ══════════════════════════════════════════════════

    private void log(String action) {
        String entry = "[" + name + "] " + action;
        transactionLog.add(entry);
    }

    public void printTransactionLog() {
        System.out.println("\n📋 Transaction Log — " + name);
        transactionLog.forEach(l -> System.out.println("  → " + l));
        System.out.println();
    }

    // ══════════════════════════════════════════════════
    // SETTERS / GETTERS
    // ══════════════════════════════════════════════════

    public void    setTargetAccount(Account target) { this.targetAccount = target; }
    public String  getCustId()    { return custId; }
    public String  getName()      { return name; }
    public String  getPhone()     { return phone; }
    public Account getMyAccount() { return myAccount; }

    @Override
    public String toString() {
        return String.format("Customer[%s | %s | %s | Acc: %s]",
                custId, name, phone, myAccount.getAccountNumber());
    }
}