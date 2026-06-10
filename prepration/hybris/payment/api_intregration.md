# API Integration Guide

## Payment Integration Overview

Payment integration = API integration + payment-specific business logic (authorize, capture, refund, callback, reconciliation, etc.)

---

## Steps to Integrate an API

### Step 2 — Read API Documentation

The API provider will give:

- Endpoint URL
- Request Method
- Headers
- Authentication
- Request Body
- Response Format
- Error Codes

### Step 5 — Call API

**Using RestTemplate:**

```java
ResponseEntity<PaymentResponse> response =
    restTemplate.postForEntity(
        url,
        request,
        PaymentResponse.class
    );
```

**Using WebClient:**

```java
webClient.post()
    .uri(url)
    .bodyValue(request)
    .retrieve()
    .bodyToMono(PaymentResponse.class);
```

### Step 6 — Parse Response

```java
PaymentResponse paymentResponse = response.getBody();
```

---

## API Calling Methods in Java / Spring Boot

### What is API Integration?

API Integration means your application communicates with another application/service using APIs.

---

### 1. RestTemplate

**Purpose:** Traditional Spring API client — Synchronous (Blocking)

```java
restTemplate.getForEntity(...)
restTemplate.postForEntity(...)
```

| | |
|---|---|
| **Use Cases** | Legacy Spring applications, existing projects, simple API integrations |
| **Pros** | Easy to use, widely used in older projects |
| **Cons** | Blocking calls, not recommended for new applications |

---

### 2. WebClient

**Purpose:** Modern Spring API client — Reactive / Non-Blocking

```java
webClient.get()
    .uri(url)
    .retrieve()
```

| | |
|---|---|
| **Use Cases** | New Spring Boot applications, high-performance systems, calling multiple APIs concurrently |
| **Pros** | Non-blocking, better scalability, supports async programming |
| **Cons** | Slightly more complex than RestTemplate |

---

### 3. Feign Client

**Purpose:** Declarative REST client — commonly used in microservices

```java
@FeignClient(name="payment-service")
public interface PaymentClient
```

| | |
|---|---|
| **Use Cases** | Service-to-service communication, microservices architecture, clean and maintainable code |
| **Pros** | Less boilerplate, easy to read, easy maintenance |
| **Cons** | Requires Spring Cloud setup |

---

### 4. Java HttpClient (Java 11+)

**Purpose:** Built-in Java HTTP client

```java
HttpClient.newHttpClient()
```

| | |
|---|---|
| **Use Cases** | Pure Java applications, projects without Spring Framework |
| **Pros** | No external dependency, modern Java standard |
| **Cons** | More code than Feign |

---

### 5. HttpURLConnection

**Purpose:** Old Java HTTP client

```java
HttpURLConnection connection
```

| | |
|---|---|
| **Use Cases** | Legacy Java applications, rarely used in modern projects |
| **Pros** | Available in all Java versions |
| **Cons** | Verbose code, hard to maintain |

---

### Selection Guide

| Scenario | Recommended Client |
|---|---|
| Legacy Spring Project | RestTemplate |
| New Spring Boot Project | WebClient |
| Microservices | Feign Client |
| Pure Java Application | Java HttpClient |
| Old Legacy Java | HttpURLConnection |

---

## API Response Parsing Checklist

### 1. Check HTTP Status Code

Verify the API returned the expected status.

| Code | Meaning |
|---|---|
| `200` | OK |
| `201` | Created |
| `400` | Bad Request |
| `401` | Unauthorized |
| `500` | Internal Server Error |

```java
if (responseEntity.getStatusCode().is2xxSuccessful()) {
    // proceed
}
```

---

### 2. Check Response Body is Not Null

Never assume the response body exists.

```java
if (response != null) {
    // process response
}
```

---

### 3. Validate Mandatory Fields

Ensure required fields are present (e.g., `paymentId`, `status`, `orderId`).

```java
if (response.getPaymentId() == null) {
    throw new RuntimeException("Payment ID Missing");
}
```

---

### 4. Validate Business Status

HTTP 200 does not always mean success.

```json
{ "status": "FAILED" }
```

```java
if ("SUCCESS".equals(response.getStatus())) {
    // process
}
```

---

### 5. Handle Unknown Fields

The API provider may add new fields in the future.

```json
{
  "paymentId": "123",
  "status": "SUCCESS",
  "newField": "ABC"
}
```

Configure Jackson to ignore unknown properties:

```java
FAIL_ON_UNKNOWN_PROPERTIES = false
```

---

### 6. Check Data Type Consistency

Expected:
```json
{ "amount": 1000 }
```

Received:
```json
{ "amount": "1000" }
```

Type mismatches can cause parsing exceptions — verify the API contract carefully.

---

### 7. Handle Empty Collections

```json
{ "orders": [] }
```

```java
response.getOrders() != null && !response.getOrders().isEmpty()
```

---

### 8. Handle Error Response Structure

Success response:
```json
{ "status": "SUCCESS" }
```

Error response:
```json
{ "errorCode": "401", "message": "Unauthorized" }
```

Support both response formats.

---

### 9. Validate Date Formats

| | Format |
|---|---|
| Expected | `2026-06-02` |
| Received | `02/06/2026` |

Incorrect formats may cause deserialization failures.

---

### 10. Logging

```java
log.info("API Response {}", response);
log.error("API Failure {}", exception);
```

---

### 11. Exception Handling

Handle all of the following:

- `TimeoutException`
- `JsonMappingException`
- `JsonProcessingException`
- `HttpClientErrorException`
- `HttpServerErrorException`
- `ResourceAccessException`

---

### 12. Defensive Programming

Never assume:

- ✓ Response exists
- ✓ Field exists
- ✓ Status is success
- ✓ Data type is correct

Always validate before processing.

---

## Production Checklist

- [ ] HTTP Status Verified
- [ ] Response Body Not Null
- [ ] Mandatory Fields Present
- [ ] Business Status Checked
- [ ] Data Types Valid
- [ ] Unknown Fields Handled
- [ ] Error Response Supported
- [ ] Date Format Validated
- [ ] Logging Added
- [ ] Exceptions Handled
- [ ] Database Updated Safely