# study-projects/

Side experiments, learning sandboxes, and old practice apps.
**Not part of the microservices reactor** — none of these build when you run
`mvn clean install` at the repo root.

## Why they're here

The repo root was getting crowded with ~13 extra Maven/Java projects that are
unrelated to the production microservices system. Moving them here keeps the
root `ls` focused on what actually matters: the 16 microservice modules plus
`angle-app`.

## Contents

| Dir | Type | Notes |
|---|---|---|
| `interview/` | Maven / Spring Boot | Interview prep snippets |
| `admin/` | Maven / Spring Boot | Admin UI experiment |
| `process/` | Maven / Spring Statemachine | State-machine playground |
| `service/` | Maven | Generic service sandbox |
| `prepration/` | Maven / Spring Boot | DSA / Spring prep |
| `food-delivery/` | Maven | Minimal domain-model stub |
| `client-app/` | Maven / Spring Boot | Standalone client experiments |
| `spring-security-apps/` | Maven / Spring Boot | Security demos |
| `jwtAuthApp/` | Maven / Spring Boot | JWT auth demo |
| `fast-api/` | Maven / Spring Boot (Java 21) | Fast-API-style app |
| `kite/` | Maven | Kite trading snippets |
| `algolia/` | Maven / Spring Boot | Algolia search demo |
| `trading/` | Markdown + images | Trading notes, no code |

## Building a single study project

Every Maven module here inherits from the root `my-microservices` parent pom
(`../../pom.xml`), so you can build any one directly:

```bash
cd study-projects/interview
mvn clean package
```

Or re-enable it in the reactor by uncommenting its line in the root `pom.xml`
under the `<!-- Study / practice projects ... -->` block.

## Removing a study project

These are entirely optional. If a directory stops being useful, delete it:

```bash
git rm -r study-projects/<name>
```

No other module depends on them.
