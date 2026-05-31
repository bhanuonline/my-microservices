package interview.project.bankproject;

import java.util.*;
import java.util.stream.Collectors;

public class Bank {

    // ── variables ─────────────────────────────────────
    private final String                  name;
    private final String                  ifscCode;
    private final Map<String, Account>    accounts;
    private final Map<String, Customer>   customers;
    private final List<String>            blockedAccounts;
    private       int                     accountCounter;
    private       int                     customerCounter;

    // ── constructor ───────────────────────────────────
    public Bank(String name, String ifscCode) {
        this.name            = name;
        this.ifscCode        = ifscCode;
        this.accounts        = new LinkedHashMap<>();
        this.customers       = new LinkedHashMap<>();
        this.blockedAccounts = new ArrayList<>();
        this.accountCounter  = 1000;
        this.customerCounter = 100;
        System.out.println("🏦 " + name + " [" + ifscCode + "] opened!");
    }

    // ══════════════════════════════════════════════════
    // ACCOUNT MANAGEMENT
    // ══════════════════════════════════════════════════

    // ── CREATE ACCOUNT ────────────────────────────────
    public synchronized Account createAccount(String owner,
                                               String type,
                                               double initialBalance) {
        String accNo   = "ACC" + (++accountCounter);
        Account account = new Account(accNo, owner, type, initialBalance);
        accounts.put(accNo, account);

        System.out.printf("✅ Account created → [%s] %s | Type: %s | Balance: %.2f%n",
                accNo, owner, type, initialBalance);
        return account;
    }

    // ── CLOSE ACCOUNT ─────────────────────────────────
    public synchronized boolean closeAccount(String accNo) {
        if (!accounts.containsKey(accNo)) {
            System.out.println("❌ Account not found: " + accNo);
            return false;
        }
        Account account = accounts.get(accNo);
        if (account.getBalance() > 0) {
            System.out.printf("❌ Cannot close! Account [%s] has balance: %.2f%n",
                    accNo, account.getBalance());
            return false;
        }
        accounts.remove(accNo);
        System.out.println("✅ Account closed: " + accNo);
        return true;
    }

    // ── GET ACCOUNT ───────────────────────────────────
    public Account getAccount(String accNo) {
        Account account = accounts.get(accNo);
        if (account == null) {
            System.out.println("❌ Account not found: " + accNo);
        }
        return account;
    }

    // ── BLOCK ACCOUNT ─────────────────────────────────
    public synchronized boolean blockAccount(String accNo) {
        Account account = accounts.get(accNo);
        if (account == null) {
            System.out.println("❌ Account not found: " + accNo);
            return false;
        }
        account.block();
        blockedAccounts.add(accNo);
        return true;
    }

    // ── UNBLOCK ACCOUNT ───────────────────────────────
    public synchronized boolean unblockAccount(String accNo) {
        Account account = accounts.get(accNo);
        if (account == null) {
            System.out.println("❌ Account not found: " + accNo);
            return false;
        }
        account.unblock();
        blockedAccounts.remove(accNo);
        return true;
    }

    // ══════════════════════════════════════════════════
    // CUSTOMER MANAGEMENT
    // ══════════════════════════════════════════════════

    // ── REGISTER CUSTOMER ─────────────────────────────
    public synchronized Customer registerCustomer(String name,
                                                   String phone,
                                                   Account account) {
        String custId    = "CUS" + (++customerCounter);
        Customer customer = new Customer(custId, name, phone, account);
        customers.put(custId, customer);

        System.out.printf("✅ Customer registered → [%s] %s | Phone: %s%n",
                custId, name, phone);
        return customer;
    }

    // ── GET CUSTOMER ──────────────────────────────────
    public Customer getCustomer(String custId) {
        Customer customer = customers.get(custId);
        if (customer == null) {
            System.out.println("❌ Customer not found: " + custId);
        }
        return customer;
    }

    // ══════════════════════════════════════════════════
    // REPORTS
    // ══════════════════════════════════════════════════

    // ── PRINT ALL ACCOUNTS ────────────────────────────
    public synchronized void printAllAccounts() {
        System.out.println("\n🏦 ══════ " + name + " — All Accounts ══════");
        if (accounts.isEmpty()) {
            System.out.println("  No accounts yet.");
        } else {
            accounts.values().forEach(acc ->
                System.out.printf("  %-8s | %-12s | %-8s | Balance: %10.2f | %s%n",
                        acc.getAccountNumber(),
                        acc.getOwner(),
                        acc.getType(),
                        acc.getBalance(),
                        acc.isBlocked() ? "BLOCKED" : "ACTIVE")
            );
        }
        System.out.println("═══════════════════════════════════════════\n");
    }

    // ── PRINT ALL CUSTOMERS ───────────────────────────
    public synchronized void printAllCustomers() {
        System.out.println("\n🏦 ══════ " + name + " — All Customers ══════");
        if (customers.isEmpty()) {
            System.out.println("  No customers yet.");
        } else {
            customers.values().forEach(cust ->
                System.out.printf("  %-8s | %-12s | Phone: %s%n",
                        cust.getCustId(),
                        cust.getName(),
                        cust.getPhone())
            );
        }
        System.out.println("════════════════════════════════════════════\n");
    }

    // ── GET TOTAL BALANCE ─────────────────────────────
    public synchronized double getTotalBalance() {
        double total = accounts.values()
                               .stream()
                               .mapToDouble(Account::getBalance)
                               .sum();
        System.out.printf("🏦 Total Bank Balance: %.2f%n", total);
        return total;
    }

    // ── GET TOP N ACCOUNTS ────────────────────────────
    public synchronized List<Account> getTopAccounts(int n) {
        List<Account> top = accounts.values()
                .stream()
                .sorted((a, b) -> Double.compare(b.getBalance(), a.getBalance()))
                .limit(n)
                .collect(Collectors.toList());

        System.out.println("\n🏆 Top " + n + " Accounts:");
        top.forEach(acc ->
            System.out.printf("  %-8s | %-12s | Balance: %.2f%n",
                    acc.getAccountNumber(), acc.getOwner(), acc.getBalance())
        );
        return top;
    }

    // ── GENERATE FULL REPORT ──────────────────────────
    public synchronized void generateReport() {
        System.out.println("\n📊 ══════════ BANK REPORT ══════════");
        System.out.println("  Bank       : " + name);
        System.out.println("  IFSC       : " + ifscCode);
        System.out.println("  Accounts   : " + accounts.size());
        System.out.println("  Customers  : " + customers.size());
        System.out.println("  Blocked    : " + blockedAccounts.size());
        System.out.printf( "  Total Bal  : %.2f%n", getTotalBalance());
        System.out.println("════════════════════════════════════\n");
    }

    // ── GETTERS ───────────────────────────────────────
    public String getName()    { return name; }
    public String getIfsc()    { return ifscCode; }
    public int    totalAccounts()  { return accounts.size(); }
    public int    totalCustomers() { return customers.size(); }
}