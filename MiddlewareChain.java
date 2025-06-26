package middleware;

import graphql.schema.DataFetchingEnvironment;

public interface MiddlewareChain {
    void next(DataFetchingEnvironment env);
}
