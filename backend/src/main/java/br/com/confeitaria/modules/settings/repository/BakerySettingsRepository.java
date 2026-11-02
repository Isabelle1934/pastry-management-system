package br.com.confeitaria.modules.settings.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.confeitaria.modules.settings.entity.BakerySettings;

public interface BakerySettingsRepository extends JpaRepository<BakerySettings, Short> {
}
