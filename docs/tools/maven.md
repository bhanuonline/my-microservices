# Maven — Multi-Module Projects

## GAV Coordinates

Every Maven project is identified by three fields together called **GAV**:

```xml
<groupId>com.example</groupId>        <!-- company / org -->
<artifactId>test-service</artifactId> <!-- project / module name -->
<version>1.0.0-SNAPSHOT</version>     <!-- version -->
```

These three together uniquely identify a module across the entire Maven ecosystem.

---

## artifactId

`artifactId` is the unique name Maven uses to identify your module.

**Where it is used:**

| Usage | Example |
|---|---|
| Built JAR name | `test-service-1.0.0-SNAPSHOT.jar` |
| Dependency reference | `<artifactId>test-service</artifactId>` |
| Local Maven repo path | `.m2/repository/com/example/test-service/` |
| IntelliJ module name | shown in bold or in brackets |

Example — how other services depend on `common-lib` using its artifactId:
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>common-lib</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## Folder Name vs artifactId

- **Folder name** = directory on disk (`services/test-service/`)
- **artifactId** = Maven identity declared inside `pom.xml`

IntelliJ shows the bracket when they don't match:

```
Folder name       artifactId        What IntelliJ shows
─────────────────────────────────────────────────────────
test-service  ==  test-service  →   test-service        (bold, no bracket)
tsh-service   !=  test-service  →   tsh-service [test-service]
```

Best practice: keep folder name and artifactId the same to avoid confusion.

---

## When to Register a Module in Root pom.xml

```xml
<!-- root pom.xml -->
<modules>
    <module>services/test-service</module>
</modules>
```

### Register when:
- You want `mvn install` from root to build it automatically
- The module inherits parent pom (Spring Boot version, Java version, dependency versions)
- It depends on `common-lib` or any sibling module
- You want IntelliJ to recognise it as a Maven module (bold, imports resolve)
- CI/CD should include it in the build

### Skip registration when:
- It is a totally standalone / throwaway experiment
- It uses a different tech stack (Python, Node.js, etc.)
- You explicitly don't want it included in the root build

### In this project:
`test-service` is registered because it uses the parent pom for Spring Boot version
and will eventually use `common-lib`.

---

## Running a Module

From root (builds all modules first, then runs):
```bash
mvn spring-boot:run -pl services/test-service
```

Standalone (from inside the module directory):
```bash
cd services/test-service
mvn spring-boot:run
```
