package interview.topic.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.security.Key;

public class JwtVerifyExample {
    public static void main(String[] args) {
        // Use the same key used when generating the token
        Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);

        // Step 1: Create a token first (in real apps, you get it from client request)
        String jwt = Jwts.builder()
                .setSubject("alice")
                .claim("userId", 123)
                .signWith(key)
                .compact();

        // Step 2: Verify the token
        try {
            Jws<Claims> jws = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(jwt);

            System.out.println("Subject: " + jws.getBody().getSubject());
            System.out.println("User ID: " + jws.getBody().get("userId"));
            System.out.println("Expiration: " + jws.getBody().getExpiration());
        } catch (Exception e) {
            System.out.println("Invalid or expired token");
        }
    }
}