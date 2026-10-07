package interview.project.bankproject;

import java.util.ArrayList;
import java.util.List;

public class Account {

    // ── account types ─────────────────────────────────
    public static final String SAVINGS = "SAVINGS";
    public static final String CURRENT = "CURRENT";
    public static final String SALARY  = "SALARY";

    // ── variables ─────────────────────────────────────
    private final String            accountNumber;
    private final String            owner;
    private final String            type;
    private       double            balance;
    private       boolean           blocked;
    private final List<Transaction> transactions;
    final         Object            lock = new Object(); // package-level for transfer

    // ── constructor ───────────────────────────────────
    public Account(String accountNumber, String owner, String type, double balance) {
        this.accountNumber = accountNumber;
        this.owner         = owner;
        this.type          = type;
        this.balance       = balance;
        this.blocked       = false;
        this.transactions  = new ArrayList<>();
    }

    // ── DEPOSIT ───────────────────────────────────────
    public void deposit(double amount) {
        synchronized (lock) {
            if (blocked) {
                System.out.println("❌ Account blocked! Cannot deposit.");
                return;
            }
            if (amount <= 0) {
                System.out.println("❌ Invalid deposit amount!");
                return;
            }
            balance += amount;
            Transaction txn = new Transaction(Transaction.DEPOSIT, amount, balance);
            transactions.add(txn);
            System.out.printf("✅ [%s] %s deposited %.2f | Balance: %.2f%n",
                    accountNumber, owner, amount, balance);
        }
    }

    // ── WITHDRAW ──────────────────────────────────────
    public boolean withdraw(double amount) {
        synchronized (lock) {
            if (blocked) {
                System.out.println("❌ Account blocked! Cannot withdraw.");
                return false;
            }
            if (amount <= 0) {
                System.out.println("❌ Invalid withdraw amount!");
                return false;
            }
            if (balance < amount) {
                System.out.printf("❌ [%s] %s insufficient balance: %.2f%n",
                        accountNumber, owner, balance);
                Transaction txn = new Transaction(Transaction.WITHDRAW, amount, balance);
                txn.setStatus(Transaction.FAILED);
                transactions.add(txn);
                return false;
            }
            balance -= amount;
            Transaction txn = new Transaction(Transaction.WITHDRAW, amount, balance);
            transactions.add(txn);
            System.out.printf("✅ [%s] %s withdrew %.2f | Balance: %.2f%n",
                    accountNumber, owner, amount, balance);
            return true;
        }
    }

    // ── TRANSFER ──────────────────────────────────────
    public boolean transfer(Account target, double amount) {

        // fixed lock order → prevents deadlock!
        Account first  = this.accountNumber.compareTo(target.accountNumber) < 0
                         ? this : target;
        Account second = (first == this) ? target : this;

        synchronized (first.lock) {
            synchronized (second.lock) {

                if (blocked) {
                    System.out.println("❌ Sender account blocked!");
                    return false;
                }
                if (balance < amount) {
                    System.out.printf("❌ [%s] Transfer failed! Insufficient: %.2f%n",
                            accountNumber, balance);
                    return false;
                }

                balance        -= amount;
                target.balance += amount;

                transactions.add(
                    new Transaction(Transaction.TRANSFER_OUT, amount, balance));
                target.transactions.add(
                    new Transaction(Transaction.TRANSFER_IN, amount, target.balance));

                System.out.printf("✅ [%s] %s transferred %.2f to %s | " +
                        "My Bal: %.2f | Their Bal: %.2f%n",
                        accountNumber, owner, amount,
                        target.owner, balance, target.balance);
                return true;
            }
        }
    }

    // ── ROLLBACK ──────────────────────────────────────
    public boolean rollback(String txnId) {
        synchronized (lock) {
            for (Transaction txn : transactions) {
                if (txn.getTxnId().equals(txnId)
                        && txn.getStatus().equals(Transaction.SUCCESS)) {

                    // reverse the transaction
                    if (txn.getType().equals(Transaction.DEPOSIT)) {
                        balance -= txn.getAmount();
                    } else if (txn.getType().equals(Transaction.WITHDRAW)) {
                        balance += txn.getAmount();
                    }

                    txn.setStatus(Transaction.REVERSED);
                    transactions.add(
                        new Transaction(Transaction.REVERSAL, txn.getAmount(), balance));

                    System.out.printf("↩️  [%s] Rolled back %s | Balance: %.2f%n",
                            accountNumber, txnId, balance);
                    return true;
                }
            }
            System.out.println("❌ Transaction not found or already reversed: " + txnId);
            return false;
        }
    }

    // ── BLOCK / UNBLOCK ───────────────────────────────
    public void block() {
        synchronized (lock) {
            blocked = true;
            System.out.println("🔒 Account blocked: " + accountNumber);
        }
    }

    public void unblock() {
        synchronized (lock) {
            blocked = false;
            System.out.println("🔓 Account unblocked: " + accountNumber);
        }
    }

    // ── STATEMENTS ────────────────────────────────────
    public void printStatement() {
        synchronized (lock) {
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.printf("  Statement — %s [%s] [%s]%n", owner, accountNumber, type);
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            if (transactions.isEmpty()) {
                System.out.println("  No transactions yet.");
            } else {
                transactions.forEach(t -> System.out.println("  " + t));
            }
            System.out.printf("  Current Balance : %.2f%n", balance);
            System.out.printf("  Account Status  : %s%n", blocked ? "BLOCKED" : "ACTIVE");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        }
    }

    public void printMiniStatement() {
        synchronized (lock) {
            System.out.println("\n── Mini Statement [" + owner + "] ──");
            int start = Math.max(0, transactions.size() - 5);
            transactions.subList(start, transactions.size())
                        .forEach(t -> System.out.println("  " + t));
            System.out.printf("  Balance: %.2f%n%n", balance);
        }
    }

    // ── GETTERS ───────────────────────────────────────
    public String           getAccountNumber() { return accountNumber; }
    public String           getOwner()         { return owner; }
    public String           getType()          { return type; }
    public double           getBalance()       { return balance; }
    public boolean          isBlocked()        { return blocked; }
    public List<Transaction> getTransactions() { return new ArrayList<>(transactions); }
}