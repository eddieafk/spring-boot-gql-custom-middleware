package middleware;

import graphql.schema.DataFetchingEnvironment;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import middleware.gql.GqlException;
import java.util.ArrayList;
import java.util.List;

@Aspect
@Component
public class MiddlewareAspect {

    @Autowired
    private ApplicationContext context;

    @Around("@annotation(middleware.UseMiddleware)")
    public Object applyMiddlewares(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        UseMiddleware annotation = signature.getMethod().getAnnotation(UseMiddleware.class);
        Object[] args = joinPoint.getArgs();

        // Find DataFetchingEnvironment in the arguments
        DataFetchingEnvironment env = null;
        int envIndex = -1;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof DataFetchingEnvironment) {
                env = (DataFetchingEnvironment) args[i];
                envIndex = i;
                break;
            }
        }

        if (env == null) {
            throw new RuntimeException("DataFetchingEnvironment not found in resolver method");
        }

        // Create a list of middleware instances
        List<Middleware> middlewares = new ArrayList<>();
        for (Class<? extends Middleware> middlewareClass : annotation.value()) {
            Middleware middleware = context.getBean(middlewareClass);
            middlewares.add(middleware);
        }

        // Execute middleware chain
        if (middlewares.isEmpty()) {
            return joinPoint.proceed();
        }

        // Create a middleware chain and execute it
        return executeMiddlewareChain(middlewares, env, joinPoint, envIndex);
    }

    private Object executeMiddlewareChain(List<Middleware> middlewares,
                                          DataFetchingEnvironment env,
                                          ProceedingJoinPoint joinPoint,
                                          int envIndex) throws Throwable {
        try {
            MiddlewareChainImpl chain = new MiddlewareChainImpl(middlewares, env, joinPoint, envIndex);
            chain.proceed();
            return chain.getResult();
        } catch (GqlException ex) {
            // Pass through GqlExceptions cleanly - these are expected errors
            throw ex;
        } catch (Exception ex) {
            // Wrap other exceptions with a more useful message
            throw new GqlException("Middleware execution error", ex);
        }
    }

    private static class MiddlewareChainImpl implements MiddlewareChain {
        private final List<Middleware> middlewares;
        private final ProceedingJoinPoint joinPoint;
        private int index = 0;
        private DataFetchingEnvironment env;
        private final int envIndex;
        private Object result;
        private boolean hasExecuted = false;

        MiddlewareChainImpl(List<Middleware> middlewares, DataFetchingEnvironment env,
                            ProceedingJoinPoint joinPoint, int envIndex) {
            this.middlewares = middlewares;
            this.env = env;
            this.joinPoint = joinPoint;
            this.envIndex = envIndex;
        }

        public Object proceed() throws Throwable {
            if (middlewares.isEmpty()) {
                result = joinPoint.proceed();
                hasExecuted = true;
                return result;
            }

            // Start the middleware chain
            middlewares.get(0).apply(env, this);
            if (!hasExecuted) {
                // If no middleware called next() all the way to the end,
                // we need to execute the target method
                executeTarget();
            }
            return result;
        }

        @Override
        public void next(DataFetchingEnvironment env) {
            // Update the environment in case it was modified by the middleware
            this.env = env;

            try {
                if (index < middlewares.size() - 1) {
                    // Process next middleware in the chain
                    index++;
                    middlewares.get(index).apply(env, this);
                } else {
                    // We've reached the end of the middleware chain, execute the target method
                    executeTarget();
                }
            } catch (Throwable e) {
                // Don't wrap GqlException - let it propagate naturally
                if (e instanceof GqlException) {
                    throw (GqlException) e;
                }
                throw new RuntimeException("Middleware chain error", e);
            }
        }

        private void executeTarget() throws Throwable {
            // Update the environment in the arguments if it was modified
            if (envIndex >= 0) {
                Object[] args = joinPoint.getArgs();
                args[envIndex] = env;
                result = joinPoint.proceed(args);
            } else {
                result = joinPoint.proceed();
            }
            hasExecuted = true;
        }

        public Object getResult() {
            return result;
        }
    }
}