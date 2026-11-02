package br.com.confeitaria.modules.auth.dto;

import br.com.confeitaria.modules.auth.entity.AdminUser;

public record AdminMeResponse(Long id, String name, String email, boolean mustChangePassword) {

    public static AdminMeResponse from(AdminUser user) {
        return new AdminMeResponse(user.getId(), user.getName(), user.getEmail(), user.isMustChangePassword());
    }
}
