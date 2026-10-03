package com.example.common.featureflag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Guard a method behind a feature flag. When the flag is OFF the method
 * short-circuits and returns null / default. When ON, the method runs
 * normally.
 *
 * For richer "fall back to legacy impl" behaviour, prefer an explicit
 * if/else in the caller — annotations hide control flow.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FeatureEnabled {
    /** The flag key to check in {@code feature_flags.flag_key}. */
    String value();
}
