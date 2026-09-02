package br.com.fiap.notificacao.exception;

/**
 * Evento que nunca vai processar, por mais que se tente (campo obrigatorio ausente, data
 * ilegivel). E classificada como nao-retryavel no tratador de erros do consumidor, para ir
 * direto ao dead letter topic em vez de ocupar o consumidor em retentativas inuteis.
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensagem) {
        super(mensagem);
    }

    public EventoInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
