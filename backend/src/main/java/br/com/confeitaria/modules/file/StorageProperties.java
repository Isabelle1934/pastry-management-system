package br.com.confeitaria.modules.file;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param uploadDir   diretório persistente e privado (fora de pastas públicas e do código)
 * @param maxFileSize tamanho máximo de cada arquivo
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(Path uploadDir, @DefaultValue("5MB") DataSize maxFileSize) {
}
