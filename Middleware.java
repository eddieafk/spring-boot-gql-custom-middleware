package middleware;

import graphql.schema.DataFetchingEnvironment;

public interface Middleware {
    void apply(DataFetchingEnvironment env, MiddlewareChain chain);
}
