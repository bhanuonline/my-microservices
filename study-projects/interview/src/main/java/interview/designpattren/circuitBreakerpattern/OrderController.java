package interview.designpattren.circuitBreakerpattern;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private PaymentService paymentService;

    @GetMapping("/pay")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "fallbackPay")
    public String makePayment() {
        return paymentService.pay();
    }

    // Fallback Method
    public String fallbackPay(Exception ex) {
        return "Payment Service is temporarily unavailable. Please try later.";
    }
}
