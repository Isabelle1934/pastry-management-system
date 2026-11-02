package br.com.confeitaria.security;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Encerra sessões de uma usuária (após troca ou redefinição de senha). */
@Component
public class SessionRevoker {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /**
     * @param email            identificador da usuária (nome do principal nas sessões)
     * @param keepSessionId    sessão a preservar (a atual), ou {@code null} para encerrar todas
     * @return quantidade de sessões encerradas
     */
    public int revokeAll(String email, String keepSessionId) {
        var ids = sessions.findByPrincipalName(email).keySet().stream()
                .filter(id -> !id.equals(keepSessionId))
                .toList();
        ids.forEach(sessions::deleteById);
        return ids.size();
    }
}
