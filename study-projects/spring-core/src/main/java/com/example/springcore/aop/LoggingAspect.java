package com.example.springcore.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    // pointcut: every method in BankService
    private static final String BANK = "execution(* com.example.springcore.aop.BankService.*(..))";

    @Before(BANK)
    public void logBefore(JoinPoint jp) {
        System.out.println("[AOP @Before]  → " + jp.getSignature().getName());
    }

    @After(BANK)
    public void logAfter(JoinPoint jp) {
        System.out.println("[AOP @After]   ← " + jp.getSignature().getName());
    }

    // @Around wraps the method — you control when it executes via pjp.proceed()
    @Around("execution(* com.example.springcore.aop.BankService.transfer(..))")
    public Object measureTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = pjp.proceed();
        System.out.println("[AOP @Around]  transfer took " + (System.currentTimeMillis() - start) + "ms");
        return result;
    }
}
