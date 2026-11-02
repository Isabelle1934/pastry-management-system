package br.com.confeitaria.modules.auth.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.modules.auth.dto.AdminMeResponse;
import br.com.confeitaria.modules.auth.dto.AuthRequests;
import br.com.confeitaria.modules.auth.service.AuthService;
import br.com.confeitaria.modules.auth.service.PasswordService;
import br.com.confeitaria.security.AdminPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Login, sessão atual e senha. O logout é tratado pelo filtro do Spring Security (POST /logout). */
@RestController
@RequestMapping("/api/admin/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordService passwordService;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(AuthService authService, PasswordService passwordService,
            SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.passwordService = passwordService;
        this.sessionStrategy = sessionStrategy;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/login")
    public AdminMeResponse login(@Valid @RequestBody AuthRequests.Login body, HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = authService.authenticate(body.email(), body.password(), request.getRemoteAddr());

        sessionStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return authService.me(((AdminPrincipal) authentication.getPrincipal()).getId());
    }

    @GetMapping("/me")
    public AdminMeResponse me(@AuthenticationPrincipal AdminPrincipal principal) {
        return authService.me(principal.getId());
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> forgotPassword(@Valid @RequestBody AuthRequests.ForgotPassword body,
            HttpServletRequest request) {
        passwordService.requestReset(body.email(), request.getRemoteAddr());
        return Map.of("message",
                "Se o e-mail estiver cadastrado, você receberá em instantes um link para criar uma nova senha.");
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody AuthRequests.ResetPassword body, HttpServletRequest request) {
        passwordService.resetPassword(body.token(), body.newPassword(), request.getRemoteAddr());
    }

    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AdminPrincipal principal,
            @Valid @RequestBody AuthRequests.ChangePassword body, HttpServletRequest request) {
        passwordService.changePassword(principal.getId(), body.currentPassword(), body.newPassword(),
                request.getSession().getId());
    }
}
