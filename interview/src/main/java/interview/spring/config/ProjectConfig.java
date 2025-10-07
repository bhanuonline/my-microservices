package interview.spring.config;

import interview.spring.config.bean.Parrot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


// NoUniqueBeanDefinitionException
@Configuration
public class ProjectConfig {
 
  @Bean
  Parrot parrot1() {
    var p = new Parrot();
    p.setName("Koko");
    return p;
  }
 
  @Bean(name="Miki")
  Parrot parrot2() {
    var p = new Parrot();
    p.setName("Miki");
    return p;
  }
 
  @Bean
  //@Primary
  Parrot parrot3() {
    var p = new Parrot();
    p.setName("Riki");
    return p;
  }
}