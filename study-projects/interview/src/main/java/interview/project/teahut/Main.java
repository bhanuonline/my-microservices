package interview.project.teahut;

import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Interactive CLI for Tea Hut — parallel edition.
 * Multiple customers can arrive simultaneously using option 2 (simulate).
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        printBanner();
        TeaHut hut = new TeaHut("Chai Corner");
        Scanner scanner = new Scanner(System.in);

        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("  Your choice: ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> placeOrder(hut, scanner);
                case "2" -> simulateParallelCustomers(hut);
                case "3" -> hut.printQueue();
                case "4" -> hut.printHistory();
                case "5" -> hut.getStore().printStatus();
                case "6" -> hut.getStore().refillAll();
                case "0" -> {
                    hut.close();
                    running = false;
                }
                default -> System.out.println("\n  Invalid choice, try again.");
            }
        }

        System.out.println("\n  Finishing any pending orders...");
        Thread.sleep(5000);
        hut.printHistory();
        System.out.println("\n  ☕  Thanks for visiting! Goodbye.\n");
    }

    // ── Single manual order ───────────────────────────────────────────────────

    private static void placeOrder(TeaHut hut, Scanner scanner) {
        System.out.println();
        System.out.print("  Enter your name: ");
        String name = scanner.nextLine().trim();
        if (name.isEmpty()) { System.out.println("  Name cannot be empty."); return; }

        System.out.println("\n  Select tea type:");
        System.out.println("    1) Simple Tea  (water + milk + tea leaves + sugar)");
        System.out.println("    2) Kadak Tea   (extra tea leaves + ginger)");
        System.out.print("  Your choice: ");
        String t = scanner.nextLine().trim();

        TeaType type = switch (t) {
            case "1" -> TeaType.SIMPLE;
            case "2" -> TeaType.KADAK;
            default  -> null;
        };
        if (type == null) { System.out.println("  Invalid tea type."); return; }

        String token = hut.placeOrder(name, type);
        if (token != null) {
            System.out.println("\n  ─────────────────────────────────────");
            System.out.println("  Order placed!");
            System.out.printf("  Your token : %s%n", token);
            System.out.printf("  Tea type   : %s%n", type.getDisplayName());
            System.out.println("  Please wait — a barista will brew it.");
            System.out.println("  ─────────────────────────────────────");
        }
    }

    // ── Parallel simulation ───────────────────────────────────────────────────

    /**
     * Simulates 7 customers arriving at almost the same time.
     * With 3 workers: first 3 get picked up immediately, next 4 wait in queue.
     */
    private static void simulateParallelCustomers(TeaHut hut) throws InterruptedException {
        System.out.println("\n  ── Simulating 7 customers arriving in parallel ──");
        System.out.println("  (3 workers → first 3 start immediately, rest wait)\n");

        String[][] customers = {
            {"Ravi",    "1"},
            {"Priya",   "2"},
            {"Amit",    "1"},
            {"Sunita",  "2"},
            {"Deepak",  "1"},
            {"Meena",   "2"},
            {"Arjun",   "1"},
        };

        // Spawn one thread per customer — they all hit placeOrder() concurrently
        ExecutorService arrivals = Executors.newFixedThreadPool(customers.length);
        for (String[] c : customers) {
            String name = c[0];
            TeaType type = c[1].equals("2") ? TeaType.KADAK : TeaType.SIMPLE;
            arrivals.submit(() -> {
                try { Thread.sleep((long)(Math.random() * 150)); } // slight spread
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                hut.placeOrder(name, type);
            });
        }

        arrivals.shutdown();
        arrivals.awaitTermination(3, TimeUnit.SECONDS);
        System.out.println("\n  All 7 orders queued. Baristas working in parallel...");
        System.out.println("  Use option 3 to see queue, or wait and check history.\n");
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private static void printBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════╗");
        System.out.println("  ║      ☕  Chai Corner — Parallel Hut       ║");
        System.out.println("  ║   3 baristas · FCFS · customers wait     ║");
        System.out.println("  ╚══════════════════════════════════════════╝");
        System.out.println();
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("  ┌────────────────────────────────────────────┐");
        System.out.println("  │  1  Place an order (you)                   │");
        System.out.println("  │  2  Simulate 7 parallel customers          │");
        System.out.println("  │  3  View current queue + barista status    │");
        System.out.println("  │  4  View order history                     │");
        System.out.println("  │  5  Check ingredient stock                 │");
        System.out.println("  │  6  Refill all ingredients                 │");
        System.out.println("  │  0  Exit                                   │");
        System.out.println("  └────────────────────────────────────────────┘");
    }

}
