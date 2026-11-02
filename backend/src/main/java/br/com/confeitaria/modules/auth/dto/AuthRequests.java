package br.com.confeitaria.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpos de requisição do módulo de autenticação. Regras de senha ficam em PasswordPolicy. */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record Login(
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254) String email,
            @NotBlank(message = "Informe a senha.") @Size(max = 128, message = "Senha inválida.") String password) {
    }

    public record ForgotPassword(
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 254) String email) {
    }

    public record ResetPassword(
            @NotBlank(message = "Link inválido.") @Size(max = 128, message = "Link inválido.") String token,
            @NotBlank(message = "Informe a nova senha.") @Size(max = 128) String newPassword) {
    }

    public record ChangePassword(
            @NotBlank(message = "Informe a senha atual.") @Size(max = 128) String currentPassword,
            @NotBlank(message = "Informe a nova senha.") @Size(max = 128) String newPassword) {
    }
}
