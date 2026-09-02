package br.com.fiap.historico.exception;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Traduz excecoes em classificacoes GraphQL.
 *
 * <p>GraphQL responde HTTP 200 mesmo em erro, entao a distincao que o REST faz por status code
 * precisa aparecer em {@code errors[].extensions.classification} - e o que permite ao cliente
 * (e a collection do Postman) diferenciar "sem token" de "sem permissao".</p>
 */
@Component
public class GraphQLExceptionHandler extends DataFetcherExceptionResolverAdapter {

    public GraphQLExceptionHandler() {
        // Necessario para enxergar o SecurityContext, que e propagado por ThreadLocal.
        setThreadLocalContextAware(true);
    }

    @Override
    protected GraphQLError resolveToSingleError(Throwable excecao, DataFetchingEnvironment ambiente) {
        final ErrorType tipo = switch (excecao) {
            case AccessDeniedException ignored ->
                    estaAutenticado() ? ErrorType.FORBIDDEN : ErrorType.UNAUTHORIZED;
            case RecursoNaoEncontradoException ignored -> ErrorType.NOT_FOUND;
            case FiltroInvalidoException ignored -> ErrorType.BAD_REQUEST;
            case ConstraintViolationException ignored -> ErrorType.BAD_REQUEST;
            case IllegalArgumentException ignored -> ErrorType.BAD_REQUEST;
            default -> null;
        };

        if (tipo == null) {
            // Devolver null deixa o Spring tratar como INTERNAL_ERROR, sem vazar a stack trace.
            return null;
        }

        return GraphqlErrorBuilder.newError(ambiente)
                .errorType(tipo)
                .message(excecao.getMessage())
                .build();
    }

    private static boolean estaAutenticado() {
        final Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        return autenticacao != null && autenticacao.isAuthenticated();
    }
}
