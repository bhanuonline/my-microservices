# Parent POM — Centralized Plugin Management

The root `pom.xml` now owns plugin configuration for `spring-boot-maven-plugin`
and `git-commit-id-maven-plugin` via `<pluginManagement>`. Child modules
declare which plugins to activate; version + executions + configuration are
inherited.

Prior state: each child pom re-declared the full `<executions>` and
`<configuration>` blocks. 6 modules × ~35 lines each ≈ 210 lines of
copy-pasted YAML-XML. Bumping the git-commit-id plugin version meant 6
separate edits. Now: 1 edit in the parent + inherited everywhere.

---

## 1. Before / after

```
Before — every service pom had:                    After — every service pom has:

<build>                                             <build>
    <plugins>                                           <plugins>
        <plugin>                                            <plugin>
            <groupId>org.springframework.boot</groupId>         <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>   <artifactId>spring-boot-maven-plugin</artifactId>
            <executions>                                    </plugin>
                <execution>                                 <plugin>
                    <id>build-info</id>                         <groupId>io.github.git-commit-id</groupId>
                    <goals>                                     <artifactId>git-commit-id-maven-plugin</artifactId>
                        <goal>build-info</goal>             </plugin>
                    </goals>                            </plugins>
                </execution>                        </build>
            </executions>
        </plugin>
        <plugin>                                    ~10 lines. Reads clean.
            <groupId>io.github.git-commit-id</groupId>
            <artifactId>git-commit-id-maven-plugin</artifactId>
            <version>7.0.0</version>
            <executions>
                <execution>
                    <id>get-the-git-infos</id>
                    <phase>initialize</phase>
                    <goals>
                        <goal>revision</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <failOnNoGitDirectory>false</failOnNoGitDirectory>
                <generateGitPropertiesFile>true</generateGitPropertiesFile>
                <includeOnlyProperties>
                    <includeOnlyProperty>^git.branch$</includeOnlyProperty>
                    ... 5 more lines ...
                </includeOnlyProperties>
            </configuration>
        </plugin>
    </plugins>
</build>

~40 lines per service. 6 services × copy-paste.
```

---

## 2. How the inheritance works

Parent has `<pluginManagement>` — not `<plugins>`. Big distinction:

```
<pluginManagement>        ← declarative "if you use this plugin, here's the config"
    <plugins>...</plugins>
</pluginManagement>

<plugins>                 ← "activate this plugin during my build"
    ...
</plugins>
```

A child that declares:
```xml
<plugin>
    <groupId>io.github.git-commit-id</groupId>
    <artifactId>git-commit-id-maven-plugin</artifactId>
</plugin>
```
inherits `<version>`, `<executions>`, `<configuration>` from the parent's
`<pluginManagement>`. Any field the child DOES declare overrides the parent.

A child that DOESN'T declare the plugin at all is unaffected — the parent
config just sits there dormant. Perfect for modules like `angle-app`
(frontend, no build-info) that shouldn't run these plugins.

---

## 3. Where plugin config now lives

```
pom.xml (parent)
    <properties>
        <git-commit-id-plugin.version>7.0.0</git-commit-id-plugin.version>
    </properties>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <executions>
                        <execution>
                            <id>build-info</id>
                            <goals>
                                <goal>build-info</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>

                <plugin>
                    <groupId>io.github.git-commit-id</groupId>
                    <artifactId>git-commit-id-maven-plugin</artifactId>
                    <version>${git-commit-id-plugin.version}</version>
                    <executions>
                        <execution>
                            <id>get-the-git-infos</id>
                            <phase>initialize</phase>
                            <goals>
                                <goal>revision</goal>
                            </goals>
                        </execution>
                    </executions>
                    <configuration>
                        <failOnNoGitDirectory>false</failOnNoGitDirectory>
                        <generateGitPropertiesFile>true</generateGitPropertiesFile>
                        <includeOnlyProperties>
                            <includeOnlyProperty>^git.branch$</includeOnlyProperty>
                            <includeOnlyProperty>^git.commit.id$</includeOnlyProperty>
                            <includeOnlyProperty>^git.commit.id.abbrev$</includeOnlyProperty>
                            <includeOnlyProperty>^git.commit.time$</includeOnlyProperty>
                            <includeOnlyProperty>^git.commit.message.short$</includeOnlyProperty>
                            <includeOnlyProperty>^git.dirty$</includeOnlyProperty>
                        </includeOnlyProperties>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
```

Every child that wants build-info + git-info now just needs:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        <plugin>
            <groupId>io.github.git-commit-id</groupId>
            <artifactId>git-commit-id-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

---

## 4. Files touched

```
pom.xml                    (+ git-commit-id-plugin.version property, expanded pluginManagement)

api-gateway/pom.xml        (~50 lines removed, replaced with 10-line block)
auth-server/pom.xml        (~40 lines removed)
user-service/pom.xml       (~40 lines removed)
product-service/pom.xml    (~40 lines removed)
order-service/pom.xml      (~40 lines removed)
eureka-server/pom.xml      (~35 lines removed)

Total net change: ~245 lines deleted, ~65 lines added
                 = ~180 line reduction across the reactor
```

---

## 5. How to override for a single service

Say eureka-server wants a shorter `includeOnlyProperties` list. Override:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.git-commit-id</groupId>
            <artifactId>git-commit-id-maven-plugin</artifactId>
            <configuration>
                <!-- Overrides parent's includeOnlyProperties.
                     Merges via <combine.self="override"> if needed. -->
                <includeOnlyProperties>
                    <includeOnlyProperty>^git.branch$</includeOnlyProperty>
                    <includeOnlyProperty>^git.commit.id.abbrev$</includeOnlyProperty>
                </includeOnlyProperties>
            </configuration>
        </plugin>
    </plugins>
</build>
```

If you don't specify `<combine.self>`, Maven merges lists by appending. To
replace: add `<includeOnlyProperties combine.self="override">`.

---

## 6. Verification

```bash
cd /Users/bhanupratap/My/my-microservices

# Rebuild every affected service in one shot
mvn -pl api-gateway,auth-server,user-service,product-service,order-service,eureka-server clean install -DskipTests

# Verify build-info + git.properties still get generated
for m in api-gateway auth-server user-service product-service order-service eureka-server; do
  echo "=== $m ==="
  ls $m/target/classes/META-INF/ 2>/dev/null | grep -E "build-info|git.properties"
done

# Boot any service and check /actuator/info still populates
mvn -pl api-gateway spring-boot:run &
sleep 30
curl -s http://localhost:8080/actuator/info | jq '{build: .build.version, git: .git.commit.id.abbrev}'
# → both non-null
```

---

## 7. Interview cheat-sheet

| Question | Answer |
|---|---|
| `<pluginManagement>` vs `<plugins>`? | `<pluginManagement>` is "if you use this plugin, use these settings". `<plugins>` is "run this plugin during my build". Config-only vs active. |
| Why not just put plugins in parent `<build><plugins>`? | Then every child inherits and ACTIVATES the plugin. Frontend modules, IaC modules, etc. would run build-info too. Wrong default. |
| Same for dependencies — `<dependencyManagement>` vs `<dependencies>`? | Same pattern. `<dependencyManagement>` = version + scope defaults; `<dependencies>` = actually declared. Best practice: version in management, opt-in per module. |
| How does a child override a parent plugin config? | Redeclare the same plugin with the field you want to override. Optional `<combine.self="override">` on list fields prevents merge. |
| What if a plugin version is only in parent's pluginManagement — is it required in the child? | No. Child inherits version. Only need to declare the plugin (groupId+artifactId) to activate it. |
| Circular version bump story? | Change one property in parent. All 6 services get the new version on next rebuild. Zero copy-paste. |
| Why extract this NOW and not earlier? | Optimization. Ship the feature first (build-info per service), then DRY it up once the pattern is proven and won't change. |
| Overriding INCLUSIONS in list config? | Maven's default is to append lists. Use `<combine.children="override">` on the parent element to force replacement. |
| Property indirection (`${git-commit-id-plugin.version}`) — why? | Bumping via property lets a service temporarily override via `-D` command line: `mvn -Dgit-commit-id-plugin.version=8.0.0 install`. |
| BOMs (`<dependencyManagement><scope>import</scope>`) vs parent pom? | BOM: opinionated version lists (Spring Boot, Spring Cloud). Parent pom: shared config for YOUR modules. Different tools; use both. |
| Alternative: Maven Enforcer plugin? | Enforces version consistency at build time. Complementary — pluginManagement standardizes, Enforcer verifies. |
| Convention question — what belongs in root pom? | Anything used by 2+ modules. Below that: keep local (avoid inheritance surprises). |

---

## 8. Common pitfalls (interview probes)

1. **Putting plugin in root `<build><plugins>` instead of `<pluginManagement>`** — every child inherits activation. Frontend modules run build-info and choke.
2. **Missing `<version>` in root pluginManagement** — child inherits nothing usable. Some plugins version-default from Boot BOM (like spring-boot-maven-plugin); most don't.
3. **Property indirection typos** — `${git-commit-id.version}` vs `${git-commit-id-plugin.version}` (we chose the latter to disambiguate from potential dep versions).
4. **Merging lists unexpectedly** — child adds an entry to `<includeOnlyProperties>` expecting to REPLACE; ends up appending. Use `combine.self="override"`.
5. **Bumping version in child pom** — works but silently ignores the parent's config for that plugin. Confusion when parent adds a new execution.
6. **Nested inheritance** — if a service has its own parent-of-parent chain, all levels of pluginManagement merge. Watch for surprising defaults.
7. **`.mvn/maven.config`** — command-line args as a file. Not the same as pluginManagement but interacts with property indirection.
8. **`spring-boot-starter-parent` provides pluginManagement too** — for many common plugins. Our custom pluginManagement in the intermediate parent adds to that. Chain: root parent → spring-boot-starter-parent → our root pom → child pom.

---

## 9. Related patterns not applied here (parked)

- **Version property indirection for ALL dep versions** — currently only Spring Cloud + git-commit-id are property-driven. Extension: extract more (`resilience4j.version`, `logstash-encoder.version`).
- **Maven Enforcer** — plugin that fails build if property versions drift. Locks the invariant.
- **`<pluginRepository>`** — pin custom plugin sources. Only needed for internal plugins.
- **Multi-module Maven site** — `mvn site` generates per-module docs. Not wired.
- **Shared code quality plugins** — SpotBugs, Checkstyle, PMD via pluginManagement so every module gets the same rules by activating.
- **Test coverage report aggregation** — JaCoCo aggregator module that pulls coverage from all children.
- **Release plugin config** — `maven-release-plugin` with `useReleaseProfile=false` etc. Centralize once, use everywhere.
