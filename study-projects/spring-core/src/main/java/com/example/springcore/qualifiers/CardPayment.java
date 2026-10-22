package com.example.springcore.qualifiers;

import org.springframework.stereotype.Service;

@Service
public class CardPayment implements PaymentService {

    @Override
    public String pay(double amount) {
        return "Card payment of ₹" + amount;
    }
}
