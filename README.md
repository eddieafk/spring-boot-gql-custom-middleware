# GraphQL Middleware System

A flexible middleware system for GraphQL resolvers using Spring AOP and annotations. This system allows you to apply cross-cutting concerns like authentication, logging, validation, and caching to your GraphQL resolvers in a declarative way.

## Overview

The middleware system consists of:
- `@UseMiddleware` annotation to mark resolver methods
- `Middleware` interface for implementing middleware logic
- `MiddlewareChain` for controlling execution flow
- `MiddlewareAspect` that handles the AOP integration

## Quick Start

### 1. Implement Your Middleware

Create middleware classes by implementing the `Middleware` interface:

```java
@Component
public class AuthenticationMiddleware implements Middleware {
    @Override
    public void apply(DataFetchingEnvironment env, MiddlewareChain chain) {
        // Get authentication token from context
        String token = env.getContext().get("Authorization");
        
        if (token == null || !isValidToken(token)) {
            throw new GqlException("Authentication required");
        }
        
        // Continue to next middleware or resolver
        chain.next(env);
    }
    
    private boolean isValidToken(String token) {
        // Your token validation logic here
        return token.startsWith("Bearer ") && token.length() > 20;
    }
}
```

### 2. Apply Middleware to Resolvers

Use the `@UseMiddleware` annotation on your GraphQL resolver methods:

```java
@Component
public class UserResolver {
    
    @UseMiddleware({AuthenticationMiddleware.class, LoggingMiddleware.class})
    @QueryMapping
    public User getUser(String id, DataFetchingEnvironment env) {
        // Your resolver logic here
        return userService.findById(id);
    }
    
    @UseMiddleware(ValidationMiddleware.class)
    @MutationMapping
    public User createUser(CreateUserInput input, DataFetchingEnvironment env) {
        // Your resolver logic here
        return userService.create(input);
    }
}
```

## Complete Example

Here's a complete example showing multiple middleware types:

### Authentication Middleware

```java
@Component
public class AuthenticationMiddleware implements Middleware {
    @Override
    public void apply(DataFetchingEnvironment env, MiddlewareChain chain) {
        String token = env.getGraphQLContext().get("Authorization");
        
        if (token == null) {
            throw new GqlException("Authentication token required");
        }
        
        // Validate token and extract user info
        User user = validateTokenAndGetUser(token);
        
        // Add user to context for downstream use
        env.getGraphQLContext().put("currentUser", user);
        
        chain.next(env);
    }
    
    private User validateTokenAndGetUser(String token) {
        // Your authentication logic
        if (!token.startsWith("Bearer ")) {
            throw new GqlException("Invalid token format");
        }
        // ... token validation logic
        return new User("user123", "john@example.com");
    }
}
```

### Logging Middleware

```java
@Component
public class LoggingMiddleware implements Middleware {
    private static final Logger logger = LoggerFactory.getLogger(LoggingMiddleware.class);
    
    @Override
    public void apply(DataFetchingEnvironment env, MiddlewareChain chain) {
        String fieldName = env.getField().getName();
        Map<String, Object> arguments = env.getArguments();
        
        logger.info("Executing resolver: {} with args: {}", fieldName, arguments);
        
        long startTime = System.currentTimeMillis();
        
        try {
            chain.next(env);
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Resolver {} completed in {}ms", fieldName, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Resolver {} failed after {}ms", fieldName, duration, e);
            throw e;
        }
    }
}
```

### Validation Middleware

```java
@Component
public class ValidationMiddleware implements Middleware {
    @Override
    public void apply(DataFetchingEnvironment env, MiddlewareChain chain) {
        String fieldName = env.getField().getName();
        Map<String, Object> args = env.getArguments();
        
        // Example validation for createUser operation
        if ("createUser".equals(fieldName)) {
            validateCreateUserInput(args);
        }
        
        chain.next(env);
    }
    
    private void validateCreateUserInput(Map<String, Object> args) {
        Object input = args.get("input");
        if (input == null) {
            throw new GqlException("Input is required");
        }
        
        if (input instanceof Map) {
            Map<String, Object> inputMap = (Map<String, Object>) input;
            String email = (String) inputMap.get("email");
            
            if (email == null || !email.contains("@")) {
                throw new GqlException("Valid email is required");
            }
        }
    }
}
```

### Rate Limiting Middleware

```java
@Component
public class RateLimitMiddleware implements Middleware {
    private final Map<String, List<Long>> requestTimes = new ConcurrentHashMap<>();
    private final int maxRequestsPerMinute = 60;
    
    @Override
    public void apply(DataFetchingEnvironment env, MiddlewareChain chain) {
        String clientId = getClientId(env);
        
        if (isRateLimited(clientId)) {
            throw new GqlException("Rate limit exceeded. Try again later.");
        }
        
        recordRequest(clientId);
        chain.next(env);
    }
    
    private String getClientId(DataFetchingEnvironment env) {
        // Extract client ID from context (could be user ID, IP, etc.)
        User user = env.getGraphQLContext().get("currentUser");
        return user != null ? user.getId() : "anonymous";
    }
    
    private boolean isRateLimited(String clientId) {
        List<Long> times = requestTimes.getOrDefault(clientId, new ArrayList<>());
        long oneMinuteAgo = System.currentTimeMillis() - 60000;
        
        // Remove old requests
        times.removeIf(time -> time < oneMinuteAgo);
        
        return times.size() >= maxRequestsPerMinute;
    }
    
    private void recordRequest(String clientId) {
        requestTimes.computeIfAbsent(clientId, k -> new ArrayList<>())
                   .add(System.currentTimeMillis());
    }
}
```

### Complete Resolver Example

```java
@Component
public class BookResolver {
    
    @Autowired
    private BookService bookService;
    
    // Public endpoint - no authentication required
    @UseMiddleware({LoggingMiddleware.class, RateLimitMiddleware.class})
    public List<Book> getBooks(DataFetchingEnvironment env) {
        return bookService.findAll();
    }
    
    // Protected endpoint - requires authentication
    @UseMiddleware({
        AuthenticationMiddleware.class, 
        LoggingMiddleware.class, 
        RateLimitMiddleware.class
    })
    public Book getBook(String id, DataFetchingEnvironment env) {
        return bookService.findById(id);
    }
    
    // Admin endpoint - requires authentication and validation
    @UseMiddleware({
        AuthenticationMiddleware.class,
        ValidationMiddleware.class,
        LoggingMiddleware.class
    })
    public Book createBook(CreateBookInput input, DataFetchingEnvironment env) {
        // Middleware has already validated the user and input
        User currentUser = env.getGraphQLContext().get("currentUser");
        
        if (!"ADMIN".equals(currentUser.getRole())) {
            throw new GqlException("Admin access required");
        }
        
        return bookService.create(input);
    }
}
```

## Key Features

### Middleware Ordering
Middleware executes in the order specified in the annotation:
```java
@UseMiddleware({FirstMiddleware.class, SecondMiddleware.class, ThirdMiddleware.class})
```

### Context Modification
Middleware can modify the `DataFetchingEnvironment` and pass it down:
```java
@Override
public void apply(DataFetchingEnvironment env, MiddlewareChain chain) {
    // Add data to context
    env.getGraphQLContext().put("timestamp", System.currentTimeMillis());
    
    // Pass modified environment to next middleware
    chain.next(env);
}
```

### Error Handling
The system handles both expected (`GqlException`) and unexpected errors:
- `GqlException` instances are passed through cleanly
- Other exceptions are wrapped for better error messages

### Conditional Execution
Middleware can conditionally execute based on the request:
```java
@Override
public void apply(DataFetchingEnvironment env, MiddlewareChain chain) {
    if (shouldApplyMiddleware(env)) {
        // Apply middleware logic
        doSomething(env);
    }
    
    // Always continue the chain
    chain.next(env);
}
```

## Best Practices

1. **Always call `chain.next(env)`** unless you want to short-circuit the execution
2. **Keep middleware focused** on a single concern (authentication, logging, etc.)
3. **Use meaningful error messages** when throwing `GqlException`
4. **Consider performance impact** especially for middleware that runs on every request
5. **Test middleware independently** and in combination with others
6. **Document middleware behavior** especially when it modifies the context

## Configuration

Make sure your Spring configuration includes:
- Component scanning for your middleware classes
- AspectJ support enabled
- The `MiddlewareAspect` component registered

```java
@Configuration
@EnableAspectJAutoProxy
@ComponentScan(basePackages = {"your.middleware.package"})
public class MiddlewareConfig {
    // Configuration if needed
}
```
