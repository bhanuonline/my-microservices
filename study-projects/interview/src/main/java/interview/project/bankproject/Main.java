package interview.project.bankproject;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        // ══════════════════════════════════════════════
        // STEP 1 — Create Bank
        // ══════════════════════════════════════════════
        Bank bank = new Bank("National Java Bank", "NJB0001234");
        System.out.println();

        // ══════════════════════════════════════════════
        // STEP 2 — Create Accounts
        // ══════════════════════════════════════════════
        Account aliceAcc   = bank.createAccount("Alice",   Account.SAVINGS,  5000);
        Account bobAcc     = bank.createAccount("Bob",     Account.SAVINGS,  3000);
        Account charlieAcc = bank.createAccount("Charlie", Account.CURRENT,  7000);
        Account dianaAcc   = bank.createAccount("Diana",   Account.SALARY,   4000);
        System.out.println();

        // ══════════════════════════════════════════════
        // STEP 3 — Register Customers
        // ══════════════════════════════════════════════
        Customer alice   = bank.registerCustomer("Alice",   "9876543210", aliceAcc);
        Customer bob     = bank.registerCustomer("Bob",     "9876543211", bobAcc);
        Customer charlie = bank.registerCustomer("Charlie", "9876543212", charlieAcc);
        Customer diana   = bank.registerCustomer("Diana",   "9876543213", dianaAcc);
        System.out.println();

        // ══════════════════════════════════════════════
        // STEP 4 — Link target accounts for transfers
        // ══════════════════════════════════════════════
        alice.setTargetAccount(bobAcc);       // Alice transfers to Bob
        bob.setTargetAccount(charlieAcc);     // Bob transfers to Charlie
        charlie.setTargetAccount(dianaAcc);   // Charlie transfers to Diana
        diana.setTargetAccount(aliceAcc);     // Diana transfers to Alice

        // ══════════════════════════════════════════════
        // STEP 5 — Create Threads
        // ══════════════════════════════════════════════
        Thread t1 = new Thread(alice,   "Alice-Thread");
        Thread t2 = new Thread(bob,     "Bob-Thread");
        Thread t3 = new Thread(charlie, "Charlie-Thread");
        Thread t4 = new Thread(diana,   "Diana-Thread");

        // ══════════════════════════════════════════════
        // STEP 6 — Start all Threads (parallel banking)
        // ══════════════════════════════════════════════
        System.out.println("🚀 All customers starting banking simultaneously...\n");
        t1.start();
        t2.start();
        t3.start();
        t4.start();

        // ══════════════════════════════════════════════
        // STEP 7 — Wait for all Threads to finish
        // ══════════════════════════════════════════════
        t1.join();
        t2.join();
        t3.join();
        t4.join();

        // ══════════════════════════════════════════════
        // STEP 8 — Print Final Results
        // ══════════════════════════════════════════════
        System.out.println("\n✅ All customers finished banking!\n");

        // full account list
        bank.printAllAccounts();

        // top 3 accounts by balance
        bank.getTopAccounts(3);

        // full bank report
        bank.generateReport();

        // individual statements
        aliceAcc.printStatement();
        bobAcc.printStatement();
        charlieAcc.printStatement();
        dianaAcc.printStatement();
    }
}