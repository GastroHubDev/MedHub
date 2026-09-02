package br.com.fiap.notificacao.service;

/**
 * Porta de saida para envio de e-mail. Manter a interface aqui permite trocar SMTP por outro
 * provedor sem tocar nas regras de notificacao - e testa-las sem rede.
 */
public interface EmailSender {

    void enviar(String destinatario, String assunto, String mensagem);
}
