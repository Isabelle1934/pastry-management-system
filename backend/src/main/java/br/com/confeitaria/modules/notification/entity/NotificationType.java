package br.com.confeitaria.modules.notification.entity;

/** Tipos de aviso e se cada um também gera e-mail para a proprietária. */
public enum NotificationType {
    NEW_ORDER("Nova encomenda", true),
    ORDER_EDITED_BY_CLIENT("Pedido alterado pela cliente", true),
    QUOTE_ACCEPTED("Orçamento aceito", true),
    QUOTE_DECLINED("Orçamento recusado", true),
    QUOTE_EXPIRING("Orçamento perto de vencer", true),
    QUOTE_EXPIRED("Orçamento vencido", true),
    PROOF_SUBMITTED("Comprovante de sinal enviado", true),
    DEPOSIT_DEADLINE_NEAR("Prazo do sinal perto de terminar", true),
    DEPOSIT_DEADLINE_PASSED("Prazo do sinal terminou", true);

    private final String title;
    private final boolean email;

    NotificationType(String title, boolean email) {
        this.title = title;
        this.email = email;
    }

    public String title() {
        return title;
    }

    public boolean sendsEmail() {
        return email;
    }
}
