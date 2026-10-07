package interview.project.teahut;

public enum OrderStatus {
    QUEUED,      // order placed, sitting in queue
    PREPARING,   // barista picked it up, brewing
    READY,       // tea done, customer can collect
    FAILED       // ingredient shortage, order cancelled
}