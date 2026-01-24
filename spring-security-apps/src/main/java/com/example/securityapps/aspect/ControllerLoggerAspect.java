package com.example.securityapps.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class ControllerLoggerAspect {

    @Before("within(@org.springframework.stereotype.Controller *)")
    public void logBeforeController(JoinPoint joinPoint) {
        log.info("🎯 CONTROLLER HIT → {}", joinPoint.getSignature());
    }
}
