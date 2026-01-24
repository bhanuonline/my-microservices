package com.example.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name="oauth_client")
@Data
public class OAuthClient {

    @Id
    private String id;

    private String clientId;
    private String clientSecret;
    private String scopes;
    private String grantTypes;
}
