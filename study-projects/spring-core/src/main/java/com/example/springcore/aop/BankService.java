package com.example.springcore.aop;

import org.springframework.stereotype.Service;

@Service
public class BankService {

    public void transfer(String from, String to, double amount) {
        System.out.println("[BankService] Transferred ₹" + amount + " from " + from + " to " + to);
    }

    public double getBalance(String account) {
        System.out.println("[BankService] Fetching balance for: " + account);
        return 5000.00;
    }
}
