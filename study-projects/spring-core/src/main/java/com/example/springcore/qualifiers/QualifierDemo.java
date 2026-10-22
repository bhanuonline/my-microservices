package com.example.springcore.qualifiers;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class QualifierDemo {

    // No qualifier → Spring picks the @Primary bean (UpiPayment)
    private final PaymentService defaultPayment;

    // @Qualifier("cardPayment") → Spring picks CardPayment by bean name
    private final PaymentService cardPayment;

    public QualifierDemo(
            PaymentService defaultPayment,
            @Qualifier("cardPayment") PaymentService cardPayment
    ) {
        this.defaultPayment = defaultPayment;
        this.cardPayment = cardPayment;
    }

    public void demonstrate() {
        System.out.println("\n=== TOPIC 4: @Primary & @Qualifier ===");
        System.out.println("[@Primary default] " + defaultPayment.pay(500));
        System.out.println("[@Qualifier card]  " + cardPayment.pay(500));
    }
}
