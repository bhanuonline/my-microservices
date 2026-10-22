package com.example.springcore;

import com.example.springcore.aop.AopDemo;
import com.example.springcore.config.ValueDemo;
import com.example.springcore.di.DiDemo;
import com.example.springcore.di.notification.NotificationDemo;
import com.example.springcore.events.EventsDemo;
import com.example.springcore.lifecycle.LifecycleDemo;
import com.example.springcore.profiles.ProfilesDemo;
import com.example.springcore.qualifiers.QualifierDemo;
import com.example.springcore.scopes.ScopesDemo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringCoreApp {

    public static void main(String[] args) {
        SpringApplication.run(SpringCoreApp.class, args);
    }

    @Bean
    CommandLineRunner runAllDemos(
            DiDemo diDemo,
            ScopesDemo scopesDemo,
            LifecycleDemo lifecycleDemo,
            QualifierDemo qualifierDemo,
            ValueDemo valueDemo,
            ProfilesDemo profilesDemo,
            EventsDemo eventsDemo,
            AopDemo aopDemo,
            NotificationDemo notificationDemo

    ) {
        return args -> {
            //notificationDemo.demonstrate();
//            diDemo.demonstrate();
            scopesDemo.demonstrate();
//            lifecycleDemo.demonstrate();
//            qualifierDemo.demonstrate();
//            valueDemo.demonstrate();
//            profilesDemo.demonstrate();
//            eventsDemo.demonstrate();
//            aopDemo.demonstrate();
        };
    }
}
