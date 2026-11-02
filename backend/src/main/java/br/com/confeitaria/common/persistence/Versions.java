package br.com.confeitaria.common.persistence;

import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.exception.ErrorCode;

/**
 * Edições enviam a versão que a tela carregou. Se o registro mudou depois disso
 * (outra aba, outro aparelho), a edição é recusada em vez de sobrescrever em silêncio.
 */
public final class Versions {

    private Versions() {
    }

    public static void require(long currentVersion, long expectedVersion) {
        if (currentVersion != expectedVersion) {
            throw new ConflictException(ErrorCode.CONCURRENT_MODIFICATION,
                    "Este registro foi alterado em outra tela. Recarregue a página e tente novamente.");
        }
    }
}
