### Key Concepts:
- Inversion of Control (IoC) - Instead of your code controlling the creation and management of objects, Spring does it for you. You tell Spring what you need, and it provides it.
- Dependency Injection (DI) - This is how IoC is implemented. Spring "injects" the dependencies your classes need rather than having your classes create them. This can happen through constructors, setters, or fields.
- The IoC Container - This is Spring's core component that manages your application objects (called "beans"). The two main types are BeanFactory (basic) and ApplicationContext (more feature-rich and commonly used).
- Beans - These are simply objects that Spring manages. You define them through configuration (XML, annotations, or Java config), and Spring creates and manages their lifecycle.

## Configuration - You tell Spring about your beans through:
XML configuration files
Java-based configuration using @Configuration and @Bean
Annotation-based configuration using @Component, @Service, @Repository, etc.

1️⃣ @Configuration
→ Use with @Bean
→ Prevents multiple objects (singleton safe)

2️⃣ Without @Configuration
→ @Bean works
→ Inter-bean calls create new objects

3️⃣ @Configuration(proxyBeanMethods=false)
→ Faster
→ Use only when beans don’t call each other

4️⃣ @Import
→ Load beans from another class/package
→ Best for few specific beans

5️⃣ @ComponentScan
→ Scan whole package
→ Use when many beans exist

2. Bean Scopes
   Scopes determine how Spring creates and manages bean instances:
   Singleton (Default)
   @Component
   @Scope("singleton") // or just omit it, as it's default
   public class UserService {
   }
One instance per Spring container
Same instance returned every time it's requested
Good for stateless services

3. Prototype
   @Component
   @Scope("prototype")
   public class ShoppingCart {
   }

New instance created every time it's requested
Spring doesn't manage the complete lifecycle (no destruction callbacks)
Good for stateful objects

4. Request (Web applications only)
@Component
@Scope("request")
public class LoginAction {
}

One instance per HTTP request
Instance destroyed when request completes
5. Session (Web applications only)
   @Component
   @Scope("session")
   public class UserPreferences {
   }

One instance per HTTP session
Application
One instance per ServletContext

6. Custom Scopes - You can even define your own scopes.

## Container Lifecycle
   Understanding how Spring manages beans from creation to destruction:
   Startup Phase:

Load Configuration - Spring reads your configuration (XML, annotations, Java config)
Bean Definitions Created - Spring creates metadata about each bean (class, scope, dependencies, etc.)
BeanFactoryPostProcessors Execute - Modify bean definitions before beans are created
Beans Instantiated - Spring creates bean instances based on definitions
Dependencies Injected - Spring injects dependencies into beans
Bean Initialization:

@PostConstruct methods called
InitializingBean.afterPropertiesSet() called
Custom init methods called

Container Started
      ↓
Load Bean Definitions
      ↓
Instantiate Beans
      ↓
Populate Properties (Dependency Injection)
      ↓
BeanNameAware's setBeanName()
      ↓
BeanClassLoaderAware's setBeanClassLoader()
      ↓
BeanFactoryAware's setBeanFactory()
      ↓  
ApplicationContextAware's setApplicationContext()
      ↓
BeanPostProcessor's postProcessBeforeInitialization()
      ↓
@PostConstruct annotated methods
      ↓
InitializingBean's afterPropertiesSet()
      ↓
Custom init-method
      ↓
BeanPostProcessor's postProcessAfterInitialization()
      ↓
Bean Ready to Use
      ↓
Container Shutdown Triggered
      ↓
@PreDestroy annotated methods
      ↓
DisposableBean's destroy()
      ↓
Custom destroy-method
      ↓
Bean Destroyed

Usage Phase:

Beans are ready and available for use

Shutdown Phase:

@PreDestroy methods called
DisposableBean.destroy() called
Custom destroy methods called


# Difference: BeanFactoryPostProcessor vs BeanPostProcessor

| Aspect      | BeanFactoryPostProcessor    | BeanPostProcessor      |
| ----------- | --------------------------- | ---------------------- |
| Runs        | Before beans are created    | After bean creation    |
| Works on    | BeanDefinition              | Bean instance          |
| Can change  | Metadata                    | Object behavior        |
| Typical use | Property override, profiles | Proxy, AOP, validation |

Special note (very important 💡)
BeanFactoryPostProcessor is itself a bean
But Spring creates it early
Order can be controlled using:
PriorityOrdered
Ordered
BeanDefinitionRegistryPostProcessor — what & why What it is
A stronger version of BeanFactoryPostProcessor that can ADD / REMOVE bean definitions

| Feature          | BeanFactoryPostProcessor | BeanPostProcessor | BeanDefinitionRegistryPostProcessor |
| ---------------- | ------------------------ | ----------------- | ----------------------------------- |
| Runs on          | BeanDefinition           | Bean instance     | BeanDefinition                      |
| Timing           | Before creation          | Before/after init | **Before all**                      |
| Can create beans | ❌                        | ❌                 | ✅                                   |
| Used for         | Modify props             | Proxy, AOP        | Auto config                         |
| Boot uses?       | Yes                      | Yes               | **Heavy**                           |

Note: This is how Spring implements many features like use of BeanPostProcessor:

@Transactional (creates transaction proxies)
@Async (creates async proxies)
@Cacheable (creates caching proxies)

Phase 3: Bean Instantiation
Spring creates the actual bean instance using:

Constructor (default or the one marked with @Autowired)
Factory method
FactoryBean


3. Lazy Initialization:
   @Component
   @Lazy
   public class LazyBean {
   // Lifecycle starts only when first requested
   }
4. Depends-On:
   @Component
   @DependsOn("someOtherBean")
   public class MyBean {
   // Ensures someOtherBean is created first
   }

# How to Handle Cleanup for Prototype Beans

Option 1: Manual Cleanup
// Usage
DatabaseConnection conn = context.getBean(DatabaseConnection.class);
try {
// use connection
} finally {
conn.close(); // YOU must call this
}

Option 2: Custom BeanPostProcessor
Option 3: Use a Singleton Manager
Option 4: Try-with-Resources (Java 7+) Make your prototype bean AutoCloseable:

The issue: Singleton is created once at startup, gets one prototype instance, and never gets a new one.
Solution 1: Method Injection (@Lookup)
Solution 2: ApplicationContext Injection
Solution 3: ObjectProvider (Best Practice)
Solution 4: Provider (JSR-330)

## What Problem Do Aware Interfaces Solve?
Spring manages your beans in a container, but sometimes your beans need to interact with the Spring container itself or access Spring infrastructure. Aware interfaces give your beans access to Spring's internals.
Summary
Why we need Aware interfaces:

Access Spring infrastructure (context, bean factory, etc.)
Dynamic behavior (runtime bean lookup)
Event publishing (loose coupling)
Environment access (properties, profiles)
Self-awareness (know your own bean name)

Modern approach:

Prefer @Autowired injection over Aware interfaces
Use Aware only when necessary (early lifecycle, framework development)
Makes code less coupled to Spring

1. Dependency Injection in Detail

# Constructor vs Setter vs Field Injection
A. Constructor Injection (Recommended)
Advantages:

✅ Immutability - can use final fields
✅ Required dependencies - ensures all dependencies are provided
✅ Testability - easy to test without Spring
✅ Thread-safe - immutable after construction
✅ Clear dependencies - all dependencies visible in constructor

When to use: Almost always! This is the recommended approach.
B. Setter Injection
Advantages:

✅ Optional dependencies - can have partial configuration
✅ Reconfiguration - can change dependencies after creation
✅ Circular dependencies - can help resolve some circular dependency issues

Disadvantages:

❌ Cannot use final
❌ Dependencies might be null
❌ Harder to test

When to use: Optional dependencies or legacy code

@Autowired, @Inject, @Resource differences
Circular dependency problems and solutions
Optional dependencies
Collections injection (List, Map, Set)
@Qualifier and @Primary

2. Bean Definition and Registration

@Component vs @Bean
BeanDefinition and BeanDefinitionRegistry
Programmatic bean registration
Conditional bean registration (@Conditional)
Bean aliases
FactoryBean
    
    @Component vs @Bean
    Characteristics:@Component
    Applied to class level
    Used for your own classes that you control
    Spring automatically detects via component scanning
    Bean name = class name with first letter lowercase (e.g., userService)
    Cannot customize bean creation logic

    Characteristics:@Bean
    Applied to method level inside @Configuration classes
    Used for third-party classes or complex initialization
    Bean name = method name (e.g., dataSource)
    Full control over bean creation
    Can have complex initialization logic
    
    Stereotype annotations (specializations of @Component):
    @Controller   // Web layer
    @Service      // Business logic layer
    @Repository   // Data access layer
    @Configuration // Configuration classes
    
    @Component + @Bean Together wil work But better to separate

3. Profiles and Environment

@Profile for environment-specific beans
Property sources and property placeholders
@PropertySource
Environment abstraction
Active profiles

4. Expression Language (SpEL)

Spring Expression Language basics
Using SpEL in @Value
Bean references in SpEL
Method invocation in SpEL

5. Events and Event Listeners

ApplicationEvent and ApplicationListener
@EventListener annotation
Async events
Custom events
Event ordering

6. Resource Management

Resource interface
Loading resources (classpath, file system, URL)
ResourceLoader
@Value with Resource

7. Validation

JSR-303/JSR-380 Bean Validation
Spring Validator interface
@Validated vs @Valid
Custom validators
Validation groups

8. Type Conversion and Formatting

PropertyEditor
Converter interface
Formatter interface
ConversionService
@DateTimeFormat, @NumberFormat

9. AOP (Aspect-Oriented Programming)

What is AOP and why use it
Aspects, Join Points, Pointcuts, Advice
@Aspect, @Before, @After, @Around
Proxy mechanisms (JDK vs CGLIB)
AOP use cases (logging, transactions, security)

10. Internationalization (i18n)

MessageSource
ResourceBundle
Locale resolution
Message formatting

11. Task Execution and Scheduling

@Async for asynchronous execution
TaskExecutor
@Scheduled for scheduled tasks
Cron expressions
@EnableAsync, @EnableScheduling

12. Advanced Bean Configuration

@Import and @ImportResource
@DependsOn
@Lazy initialization
Bean inheritance
Method injection (@Lookup)

13. ApplicationContext Features

Different ApplicationContext implementations
Context hierarchy
Context lifecycle callbacks
Refreshing context

14. Container Extension Points

BeanPostProcessor in detail
BeanFactoryPostProcessor
Custom scopes
InstantiationAwareBeanPostProcessor

15. Spring Testing

@SpringBootTest, @WebMvcTest
@MockBean, @SpyBean
Test configuration
Test slices
TestContext framework



🔥 Full working project for dynamic routing
🔥 Multi-tenant production example
🔥 How Spring Transaction works internally
🔥 How Hibernate connects to connection pool
🔥 How HikariCP works internally
🔥 How transaction propagation affects routing
🔥 What happens if replica is down
🔥 How Netflix/Zomato scale DB layer
🔥 Performance tuning with HikariCP
Resource Management
Spring event