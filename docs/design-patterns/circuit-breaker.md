Circuit Breaker Pattern protects your system from repeatedly calling a failing service.
Instead of waiting for timeouts again and again, it cuts the connection temporarily, keeping your system fast and stable.


Why Circuit Breaker is needed
Without it:
Service A → Service B (DOWN)
→ waits for timeout
→ thread blocked
→ system crashes


With Circuit Breaker
Service B fails repeatedly
↓
Circuit opens
↓
Service A stops calling B
↓
Fallback response returned
↓
System stays healthy


Circuit Breaker States

| State         | Meaning                         |
| ------------- | ------------------------------- |
| **CLOSED**    | Everything normal               |
| **OPEN**      | Too many failures → block calls |
| **HALF-OPEN** | Test few requests               |

State Flow

CLOSED → failures → OPEN → wait time → HALF-OPEN
HALF-OPEN success → CLOSED
HALF-OPEN failure → OPEN
