package interview.project.teahut;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tea Hut with a configurable worker-thread pool.
 *
 * How it works:
 *  - placeOrder() drops an order into a LinkedBlockingQueue — thread-safe, FCFS.
 *  - A ThreadPoolExecutor with NUM_WORKERS threads drains the queue.
 *  - Each worker calls queue.take(), which BLOCKS if queue is empty (no spin-waiting).
 *  - If all workers are busy, new orders wait in the queue automatically.
 *  - IngredientStore.consume() is synchronized — only one worker deducts at a time.
 */
public class TeaHut {

    private static final int NUM_WORKERS = 3; // configurable barista count

    private final String name;
    private final IngredientStore store;
    private final List<TeaOrder> orderHistory = new ArrayList<>();

    // LinkedBlockingQueue: thread-safe, FCFS, blocks workers when empty
    private final LinkedBlockingQueue<TeaOrder> queue = new LinkedBlockingQueue<>();
    private final AtomicInteger activeWorkers = new AtomicInteger(0);

    private volatile boolean open = true;

    private final ThreadPoolExecutor pool;

    public TeaHut(String name) {
        this.name  = name;
        this.store = new IngredientStore();

        // Create fixed thread pool — each thread is a "barista"
        pool = new ThreadPoolExecutor(
            NUM_WORKERS, NUM_WORKERS,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>()
        );

        // Start NUM_WORKERS barista threads — each loops on queue.take()
        for (int i = 1; i <= NUM_WORKERS; i++) {
            final int id = i;
            pool.submit(() -> baristaLoop(id));
        }

        System.out.printf("  [TeaHut] %s opened with %d baristas ready.%n%n", name, NUM_WORKERS);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Any customer (any thread) can call this concurrently.
     * Returns the system-generated token immediately — no waiting.
     */
    public synchronized String placeOrder(String customerName, TeaType type) {
        if (!open) {
            System.out.println("  Sorry, " + name + " is now closed.");
            return null;
        }
        TeaOrder order = new TeaOrder(customerName, type);
        orderHistory.add(order);
        queue.offer(order); // non-blocking — always succeeds (unbounded queue)

        int waiting = queue.size();
        int busy    = activeWorkers.get();
        System.out.printf("  [Queue]   %s → %s | token: %s | queue depth: %d | busy baristas: %d/%d%n",
            customerName, type.getDisplayName(), order.getToken(), waiting, busy, NUM_WORKERS);
        return order.getToken();
    }

    public void close() {
        open = false;
        // Inject poison pills to unblock each waiting barista thread
        for (int i = 0; i < NUM_WORKERS; i++) {
            queue.offer(TeaOrder.POISON_PILL);
        }
        pool.shutdown();
        System.out.println("\n  [TeaHut] Closing — finishing remaining orders...");
    }

    public synchronized void printQueue() {
        System.out.println("\n  ── Current queue ───────────────────────");
        if (queue.isEmpty()) {
            System.out.println("  (queue is empty)");
        } else {
            int pos = 1;
            for (TeaOrder o : queue) {
                if (o == TeaOrder.POISON_PILL) continue;
                System.out.printf("  %d.  [%s] %s — %s%n",
                    pos++, o.getToken(), o.getCustomerName(), o.getTeaType().getDisplayName());
            }
        }
        System.out.printf("  Active baristas: %d / %d%n", activeWorkers.get(), NUM_WORKERS);
        System.out.println("  ────────────────────────────────────────");
    }

    public synchronized void printHistory() {
        System.out.println("\n  ── Order history ───────────────────────");
        if (orderHistory.isEmpty()) {
            System.out.println("  (no orders yet)");
        } else {
            orderHistory.forEach(o -> System.out.println("  " + o));
        }
        System.out.println("  ────────────────────────────────────────");
    }

    public IngredientStore getStore() { return store; }

    // ── Barista loop ──────────────────────────────────────────────────────────

    /**
     * Each barista thread runs this loop forever.
     * queue.take() BLOCKS when empty — barista sleeps until an order arrives.
     * When all baristas are blocked, new orders wake one up automatically.
     */
    private void baristaLoop(int baristaId) {
        System.out.printf("  [Barista %d] Ready and waiting for orders.%n", baristaId);
        while (true) {
            try {
                TeaOrder order = queue.take(); // BLOCKS here if queue empty
                if (order == TeaOrder.POISON_PILL) break; // shutdown signal
                activeWorkers.incrementAndGet();
                process(baristaId, order);
                activeWorkers.decrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.printf("  [Barista %d] Signing off.%n", baristaId);
    }

    private void process(int baristaId, TeaOrder order) {
        order.markPreparing();
        System.out.printf("%n  [Barista %d] Brewing %s for %s (token: %s)%n",
            baristaId, order.getTeaType().getDisplayName(),
            order.getCustomerName(), order.getToken());

        try {
            store.consume(order.getTeaType()); // synchronized — safe across workers
            int ms = order.getTeaType() == TeaType.KADAK ? 1200 : 800;
            Thread.sleep(ms);
            order.markReady();
            System.out.printf("  [Barista %d] Done! Token %-8s — %s ready for %s ☕%n",
                baristaId, order.getToken(),
                order.getTeaType().getDisplayName(), order.getCustomerName());
        } catch (InsufficientIngredientsException e) {
            order.markFailed(e.getMessage());
            System.out.printf("  [Barista %d] FAILED token %-8s — %s%n",
                baristaId, order.getToken(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
