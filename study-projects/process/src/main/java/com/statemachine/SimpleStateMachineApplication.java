package com.statemachine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.statemachine.StateMachine;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootApplication
public class SimpleStateMachineApplication{

    @Autowired
    private StateMachine<MyOrderStates, OrderEvents> stateMachine;

    public static void main(String[] args) {
        SpringApplication.run(SimpleStateMachineApplication.class, args);
    }


}