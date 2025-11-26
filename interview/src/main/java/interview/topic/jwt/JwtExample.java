package interview.topic.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;

public class JwtExample {
    public static void main(String[] args) {
        // Create a secure random key (for HS256)
        Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);

        // Set expiration (1 hour from now)
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600_000); // 1h

        // Build token
        String jwt = Jwts.builder()
                .setSubject("alice") // 'sub' claim
                .claim("userId", 123) // custom claim
                .setIssuedAt(now)     // 'iat' claim
                .setExpiration(expiry) // 'exp' claim
                .signWith(key)        // sign with key
                .compact();

        System.out.println("JWT Token: " + jwt);
    }
}