package com.example.common.featureflag;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class FeatureFlagAspect {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagAspect.class);

    private final FeatureFlagService svc;

    public FeatureFlagAspect(FeatureFlagService svc) {
        this.svc = svc;
    }

    @Around("@annotation(com.example.common.featureflag.FeatureEnabled)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method m = ((MethodSignature) pjp.getSignature()).getMethod();
        FeatureEnabled annotation = m.getAnnotation(FeatureEnabled.class);
        String key = annotation.value();
        if (!svc.isEnabled(key)) {
            log.debug("flag {} disabled → skipping {}.{}", key, m.getDeclaringClass().getSimpleName(), m.getName());
            return defaultReturn(m);
        }
        return pjp.proceed();
    }

    private static Object defaultReturn(Method m) {
        Class<?> t = m.getReturnType();
        if (t == void.class || t == Void.class) return null;
        if (t == boolean.class) return false;
        if (t.isPrimitive()) return 0;
        return null;
    }
}
