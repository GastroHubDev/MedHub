package br.com.fiap.historico.exception;

/**
 * Evento que nunca vai processar por mais que se tente. Classificada como nao-retryavel no
 * tratador de erros do consumidor, indo direto ao dead letter topic.
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensagem) {
        super(mensagem);
    }

    public EventoInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
