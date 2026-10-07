package interview.project.teahut;

import java.time.LocalDateTime;
import java.util.UUID;

public class TeaOrder {

    // Sentinel — signals barista threads to stop on shutdown
    public static final TeaOrder POISON_PILL = new TeaOrder();

    private final String        token;
    private final String        customerName;
    private final TeaType       teaType;
    private final LocalDateTime placedAt;

    private volatile OrderStatus status;   // written by barista, read by main thread
    private String        failureReason;
    private LocalDateTime completedAt;

    // Package-private — only TeaHut can create orders
    TeaOrder(String customerName, TeaType teaType) {
        this.token        = UUID.randomUUID().toString()
                               .substring(0, 8).toUpperCase();
        this.customerName = customerName;
        this.teaType      = teaType;
        this.placedAt     = LocalDateTime.now();
        this.status       = OrderStatus.QUEUED;  // born in queue
    }

    // Private — only for POISON_PILL
    private TeaOrder() {
        this.token = "POISON"; this.customerName = null;
        this.teaType = null;   this.placedAt = null;
    }

    // Public — anyone can read
    public String      getToken()        { return token; }
    public String      getCustomerName() { return customerName; }
    public TeaType     getTeaType()      { return teaType; }
    public OrderStatus getStatus()       { return status; }

    // Package-private — only TeaHut can change status
    void markPreparing() { this.status = OrderStatus.PREPARING; }

    void markReady() {
        this.status      = OrderStatus.READY;
        this.completedAt = LocalDateTime.now();
    }

    void markFailed(String reason) {
        this.status        = OrderStatus.FAILED;
        this.failureReason = reason;
        this.completedAt   = LocalDateTime.now();
    }
}