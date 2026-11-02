package br.com.confeitaria.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.confeitaria.modules.auth.entity.AdminUser;
import br.com.confeitaria.modules.auth.repository.AdminUserRepository;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository repository;

    public AdminUserDetailsService(AdminUserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return repository.findByEmail(AdminUser.normalizeEmail(email))
                .map(AdminPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
    }
}
