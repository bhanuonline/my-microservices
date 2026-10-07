package com.statemachine.config;

import com.statemachine.MyOrderStates;
import com.statemachine.OrderEvents;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachine;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

@Configuration
@EnableStateMachine
public class OrderStateMachineConfig
        extends StateMachineConfigurerAdapter<MyOrderStates, OrderEvents> {

    @Override
    public void configure(StateMachineStateConfigurer<MyOrderStates, OrderEvents> states) throws Exception {
        states
            .withStates()
            .initial(MyOrderStates.NEW)
            .state(MyOrderStates.VALIDATE_PAYMENT)
            .state(MyOrderStates.RESERVE_STOCK)
            .end(MyOrderStates.SHIPPED);
    }
    @Override
    public void configure(StateMachineConfigurationConfigurer<MyOrderStates, OrderEvents> config)
            throws Exception {
        config.withConfiguration().autoStartup(true);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<MyOrderStates, OrderEvents> transitions) throws Exception {
        transitions
            .withExternal().source(MyOrderStates.NEW).target(MyOrderStates.VALIDATE_PAYMENT).event(OrderEvents.START)
            .and()
            .withExternal().source(MyOrderStates.VALIDATE_PAYMENT).target(MyOrderStates.RESERVE_STOCK).event(OrderEvents.PAYMENT_OK)
            .and()
            .withExternal().source(MyOrderStates.RESERVE_STOCK).target(MyOrderStates.SHIPPED).event(OrderEvents.STOCK_RESERVED);
    }
}