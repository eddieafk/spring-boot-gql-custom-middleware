package middleware.gql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

public class GqlException extends RuntimeException {

    public GqlException(String message) {
        super(message);
    }
    public GqlException(String message, Throwable cause) {
        super(message, cause);
    }

    public GraphQLError toGraphQlError(DataFetchingEnvironment environment) {
        return GraphqlErrorBuilder.newError()
                .message(buildErrorMessage())
                .path(environment.getExecutionStepInfo().getPath())
                .location(environment.getField().getSourceLocation())
                .build();
    }

    private String buildErrorMessage() {
        if(getCause() != null) {
            return getMessage() + getCause();
        } else {
            return getMessage();
        }
    }
}
