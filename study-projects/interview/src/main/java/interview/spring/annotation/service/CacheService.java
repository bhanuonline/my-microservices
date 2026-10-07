package interview.spring.annotation.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CacheService {

   public void load(){
       log.info("Created Cache!!");
   }
}
