package com.swh.mvcframework.annotation;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SWHRequestParam {
    String value() default "";
}
