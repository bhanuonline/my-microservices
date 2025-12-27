What is JWT?

    It’s an open standard (RFC 7519)
    JWT stands for JSON Web Token. It’s a compact way to securely transmit information between two parties (usually between a client and a server) as a JSON object.
    
    JWTs are commonly used for:JWTs are widely used in web applications for authentication and authorization.
    
    Authentication (logging in a user)
    Authorization (checking if a user can access a resource)
    Data Exchange (securely sending information)

JWT Structure

    A JWT has three parts seperated by dot (.):
    header.payload.signature
    Each part is Base64Url encoded.

Header

    Contains metadata:
    {
    "alg": "HS256", // Algorithm used to sign token
    "typ": "JWT" // type
    }
    Metadata about the token:
        alg: Algorithm used for signature (e.g., HS256, RS256)
        typ: Token type (usually "JWT")

Payload

    Contains claims (data) or the actual data being transmitted:
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

    ensures the token’s integrity
    Used to verify the token wasn’t altered.
    HMACSHA256(
    base64UrlEncode(header) + "." +base64UrlEncode(payload),secret_key)

How JWT Works

    Typical flow in authentication:
    1:Login — User sends credentials (username/password).
    2:Generate JWT — The server generates a JWT and sends it to the client. Server validates credentials, then encodes user info in JWT and signs it with a secret key.
    3:Send JWT to Client — Client stores it (usually in localStorage or a cookie).
    4:Use JWT for Requests — Client sends JWT in the Authorization header:
        Authorization: Bearer <token>
    5:Verify JWT — Server checks signature & expiry before processing.

Advantages of JWT

    Stateless authentication (no session storage needed)
    To give temporary access without storing session on the server
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

Key points

    JWT is stateless → server doesn’t need to store sessions.
    It contains encoded, not encrypted, data (you can decode it!).
    So never store passwords or sensitive data inside JWT.
    Always set an expiry time (exp claim).
        


**Point :**
It contains encoded, not encrypted, data (you can decode it!)” — Meaning

    1. Encoded ≠ Encrypted
       Encoded
    Data is converted into another format (Base64).
    Anyone can reverse it (decode it).
    No security, only formatting.
    
       2. Encrypted
       Data is locked using a secret key.
       Only the person with the key can read it.
       Provides security and privacy.
    
       3. JWT uses encoding, not encryption
       A JWT payload is Base64 encoded, like this:eyJ1c2VySWQiOjEyMywicm9sZSI6IlVTRVIifQ==
       Anyone can decode this and see:
          {
          "userId": 123,
          "role": "USER"
          }
          Data inside JWT is visible to anyone who has the token.
    
    There is no privacy, only integrity.
    
    4. So what protects JWT?
    JWT is protected by a signature, not encryption.
    
    Signature ensures:
    ✔ Data was not changed
    ✔ Token is from the real server

    Encoded = Change format, readable
    like 12345 → ONE TWO THREE FOUR FIVE
    Same meaning → just different format.

    4. Encrypted = Locked, cannot read
    Like putting a paper inside a locked box.
    No one can read it unless they have the key.

JWT is used for:

    ✔ Login authentication
    ✔ Securing APIs
    ✔ Mobile app login
    ✔ Microservices communication
    ✔ SSO
    ✔ Permissions/roles
    ✔ Stateless session management
