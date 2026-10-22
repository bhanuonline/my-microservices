package com.example.springcore.qualifiers;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary // Spring picks this when multiple PaymentService beans exist and no @Qualifier is given
public class UpiPayment implements PaymentService {

    @Override
    public String pay(double amount) {
        return "UPI payment of ₹" + amount;
    }
}
