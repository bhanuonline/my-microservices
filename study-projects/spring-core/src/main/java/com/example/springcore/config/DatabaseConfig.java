package com.example.springcore.config;

public class DatabaseConfig {

    private final String host;
    private final int port;
    private final String database;

    public DatabaseConfig(String host, int port, String database) {
        this.host = host;
        this.port = port;
        this.database = database;
    }

    @Override
    public String toString() {
        return host + ":" + port + "/" + database;
    }
}
