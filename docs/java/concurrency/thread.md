1. What is a Thread?
   A thread is the smallest unit of execution within a process. Java runs in a JVM process, and that process can have multiple threads running concurrently — sharing the same heap memory but each having its own stack, program counter, and local variables.
   JVM Process
   ├── Main Thread
   ├── Thread-1  ──┐
   ├── Thread-2    ├── Share: Heap, Static fields, Open files
   └── Thread-N  ──┘
   Each has its own: Stack, PC Register, Local vars

NEW ──→ RUNNABLE ──→ RUNNING
             │
┌────────────┼────────────┐
▼            ▼            ▼
BLOCKED      WAITING    TIMED_WAITING
│            │            │
└────────────┴────────────┘
             │
        TERMINATED

NEWThread created but start() not called
RUNNABLEReady to run or currently running
BLOCKEDWaiting to acquire a synchronized lock
WAITINGCalled wait(), join() with no timeout
TIMED_WAITINGCalled sleep(ms), wait(ms), join(ms)
TERMINATED         run() completed or exception thrown

# ExecutorService is used to manage threads using a thread pool.
Runnable :No return value
Callable :Returns value