# Setup — Angle App

## How to run

From project root (`angle-app/`):

```bash
# 1. Make sure Java 17 is active (Spring Boot 3.x requires it)
java -version
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# 2. Compile
mvn clean compile

# 3. Run the app
mvn spring-boot:run
```

App boots on `http://localhost:9010`. Look for `Started AngleAppApplication` in the logs.

**Package as a jar and run without Maven:**
```bash
mvn clean package
java -jar target/angle-app-*.jar
```

---

## Maven coordinates — this project

Every Maven project is identified by three values in `pom.xml`:

| Field        | Meaning                         | This project           |
|--------------|---------------------------------|------------------------|
| `groupId`    | WHO owns it (reverse domain)    | inherited from parent  |
| `artifactId` | WHAT the project is called      | `angle-app`            |
| `version`    | Which release                   | inherited from parent  |

- **groupId** — reverse-domain style, keeps projects globally unique.
- **artifactId** — the project's own name, kebab-case. Becomes the jar filename: `angle-app-<version>.jar`.
- **version** — the release number of this project.

The jar produced by `mvn package` lands at `target/angle-app-<version>.jar`, named from these coordinates.

Generate the Maven Wrapper (Recommended)
mvn wrapper:wrapper

get static ip for app setup angle eone
https://ifconfig.me/
https://whatismyipaddress.com
