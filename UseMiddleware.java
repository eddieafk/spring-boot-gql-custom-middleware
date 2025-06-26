package middleware;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UseMiddleware {
    Class<? extends Middleware>[] value();

}
