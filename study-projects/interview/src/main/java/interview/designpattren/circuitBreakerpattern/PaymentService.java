package interview.designpattren.circuitBreakerpattern;

import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    public String pay() {
        if (Math.random() > 0.3) {
            throw new RuntimeException("Payment Gateway DOWN");
        }
        return "Payment Success";
    }
}
