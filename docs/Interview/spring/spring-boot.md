1. What is auto-configuration in Spring Boot
        
        How it Works
        Spring Boot’s auto-configuration mechanism is powered by:
        
        1: @EnableAutoConfiguration annotation (included in @SpringBootApplication)
        2: spring.factories files packaged in Spring Boot’s auto-configuration modules
        3: Conditional annotations such as:
            @ConditionalOnClass — configures beans only if certain classes are on the classpath
            @ConditionalOnMissingBean — configures only if a bean of that type isn’t already defined
            @ConditionalOnProperty — configures based on specific property values
        When the application starts:
        
        1:Spring Boot checks the dependencies in the classpath.
        2:Reads the META-INF/spring.factories file for available auto-configuration classes.
        3:Loads these classes as configuration, but applies them conditionally to avoid interfering with custom configurations.

        @SpringBootApplication — wraps:
            @Configuration (Java-based config)
            @EnableAutoConfiguration (turns on auto-configuration)
            @ComponentScan (scans for Spring components)

        What is Classpath Content in Java/Spring?
        In Java, the classpath is the list of locations (directories, JARs) where the JVM looks for classes and resources.

        When you declare dependencies in Maven or Gradle, their JAR files are added to the classpath at runtime, meaning all classes in those JARs become available to your application
        EX:
        <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
       </dependency>
        This brings in:
        Spring MVC
        Jackson JSON
        Embedded Tomcat server
        These classes are now present in the classpath during application startup.


                Conditional Annotations Used
                Annotation	                                                    Purpose
                @ConditionalOnClass	                                            Load config only if class exists in classpath
                @ConditionalOnMissingBean	                                    Don’t override user beans
                @ConditionalOnBean	                                            Apply config only if specific bean exists
                @ConditionalOnProperty	                                        Toggle based on property value
                @ConditionalOnWebApplication / @ConditionalOnNotWebApplication	For web/non-web apps
                @ConditionalOnResource	                                        Require a file/resource in classpath

2. How Overriding Auto‑Configured Beans?

         @Configuration
         public class MyConfig {

         @Bean
         public RestTemplate restTemplate(RestTemplateBuilder builder) {
         return builder
         .setConnectTimeout(Duration.ofSeconds(3))
         .setReadTimeout(Duration.ofSeconds(5))
         .build();
         }
         }
         Here Boot won't create its own RestTemplate because you provided one.

3. Using Profiles for Environment-Specific Config

          Different configs by profile for multiple environments
          Spring profiles let you load different sets of properties depending on the environment (dev, prod, test).

          Files:
   
         application-dev.properties
         application-prod.properties
         spring.profiles.active=dev
         java -jar app.jar --spring.profiles.active=prod


Q :How to bind properties to Java objects using @ConfigurationProperties

      It tells Spring to map a group of related properties into a strongly-typed Java object automatically.
      Instead of manually calling env.getProperty("..."), you just inject your config bean.

      Restriction
      A map's contents are bound only from the active profile and defaults.
      If no profile is active, Spring loads only application.properties

      Works only on beans managed by Spring (@Component or registered with @EnableConfigurationProperties).

      Spring Boot normalizes property keys to lowercase when binding from .properties.
      Spring Boot ignores case, treats dots as hierarchy, and converts hyphens/underscores to camelCase when binding to Java fields

      When multiple active profiles exist:
         Spring loads application.yml first (default values).
            Then loads application-staging.yml.
            Then loads application-test.yml.
            If the same key appears in multiple profiles, the last one wins.

   
   