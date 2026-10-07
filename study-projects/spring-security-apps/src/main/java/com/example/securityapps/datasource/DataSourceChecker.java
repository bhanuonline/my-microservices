package com.example.securityapps.datasource;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class DataSourceChecker {

    public DataSourceChecker(DataSource ds) {
        System.out.println("✅ DataSource injected: " + ds);
    }
}