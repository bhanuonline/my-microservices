package interview.topic.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
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
                .claim("abc", 123) // custom claim
                .setIssuedAt(now)     // 'iat' claim
                .setExpiration(expiry) // 'exp' claim
                .signWith(key)        // sign with key
                .compact();

        System.out.println("JWT Token: " + jwt);

        // Parse and verify JWT
        var claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(jwt)
                .getBody();

        System.out.println("\nDecoded token data:");
        System.out.println("Subject: " + claims.getSubject());
        System.out.println("Role: " + claims.get("abc"));
        System.out.println("Expiration: " + claims.getExpiration());


        String[] parts = jwt.split("\\.");

        System.out.println(new String(Base64.getUrlDecoder().decode(parts[0])));
        System.out.println(new String(Base64.getUrlDecoder().decode(parts[1])));
    }
}