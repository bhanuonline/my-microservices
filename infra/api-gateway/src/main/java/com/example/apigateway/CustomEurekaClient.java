package com.example.apigateway;

import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Applications;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;

public class CustomEurekaClient {
    @Autowired
    private EurekaClient eurekaClient;

    @PostConstruct
    public void init() {
        Applications apps = eurekaClient.getApplications();
        System.out.println(apps);
    }
}
