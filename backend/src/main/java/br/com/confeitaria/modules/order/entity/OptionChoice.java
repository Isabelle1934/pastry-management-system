package br.com.confeitaria.modules.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Massa ou recheio escolhido. O nome é copiado no momento da encomenda para o pedido
 * continuar legível mesmo que o item seja renomeado ou desativado depois.
 */
@Embeddable
public class OptionChoice {

    @Column(name = "name_snapshot", nullable = false, length = 80)
    private String nameSnapshot;

    @Column(nullable = false)
    private short position;

    protected OptionChoice() {
    }

    public OptionChoice(String nameSnapshot, int position) {
        this.nameSnapshot = nameSnapshot;
        this.position = (short) position;
    }

    public String getNameSnapshot() {
        return nameSnapshot;
    }

    public int getPosition() {
        return position;
    }
}
