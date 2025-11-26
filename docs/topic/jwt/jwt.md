What is JWT?

    JWT stands for JSON Web Token. It’s a compact way to securely transmit information between two parties (usually between a client and a server) as a JSON object.
    
    JWTs are commonly used for:
    
    Authentication (logging in a user)
    Authorization (checking if a user can access a resource)
    Data Exchange (securely sending information)

JWT Structure

    A JWT has three parts:
    header.payload.signature
    Each part is Base64Url encoded.

Header

    Contains metadata:
    {
    "alg": "HS256", // Algorithm used to sign token
    "typ": "JWT"
    }
    Metadata about the token:
        alg: Algorithm used for signature (e.g., HS256, RS256)
        typ: Token type (usually "JWT")

Payload

    Contains claims (data):
    {
    "userId": 123,
    "name": "Alice",
    "exp": 1700000000 // expiration time in UNIX seconds
    }
    Contains claims (statements about the user and token):
        Registered claims: iss (issuer), exp (expiration), sub (subject), etc.
        iss → Issuer
        sub → Subject
        aud → Audience
        exp → Expiration time
        nbf → Not before
        iat → Issued at

        Public claims: custom fields you define (e.g., userId, role)
    This part is not encrypted by default — it can be read by anyone with the token.


Signature

    Used to verify the token wasn’t altered.
    HMACSHA256(
    base64UrlEncode(header) + "." +base64UrlEncode(payload),secret_key)

How JWT Works

    Typical flow in authentication:
    1:Login — User sends credentials (username/password).
    2:Generate JWT — Server validates credentials, then encodes user info in JWT and signs it with a secret key.
    3:Send JWT to Client — Client stores it (usually in localStorage or a cookie).
    4:Use JWT for Requests — Client sends JWT in the Authorization header:
        Authorization: Bearer <token>
    5:Verify JWT — Server checks signature & expiry before processing.

Advantages of JWT

    Stateless authentication (no session storage needed)
    Compact and easy to transmit (Base64 encoded)
    Can carry custom user data

JWT in System Architecture

    JWT is usually used in stateless authentication
    [Client] --(username/pass)--> [Server]
    |
    v
    [Server] --validate credentials-->
    |
    v
    Generate JWT (header.payload.signature)
    |
    v
    Send JWT to client
    |
    v
    [Client stores JWT] (localStorage / cookie)
    
    Next request:
    [Client] --Authorization: Bearer <JWT>--> [Server]
    |
    v
    [Server verifies signature & expiry]
    |
    v
    If valid --> process request
    If invalid --> reject
    
