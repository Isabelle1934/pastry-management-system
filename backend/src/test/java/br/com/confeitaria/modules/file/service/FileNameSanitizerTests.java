package br.com.confeitaria.modules.file.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileNameSanitizerTests {

    @Test
    void keepsOnlyTheBaseNameWithoutDangerousCharacters() {
        assertThat(FileStorageService.sanitizeName("bolo de aniversário.jpg")).isEqualTo("bolo de aniversário.jpg");
        assertThat(FileStorageService.sanitizeName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(FileStorageService.sanitizeName("C:\\Users\\ana\\foto.png")).isEqualTo("foto.png");
        assertThat(FileStorageService.sanitizeName("a\u0000b<script>.jpg")).isEqualTo("abscript.jpg");
        assertThat(FileStorageService.sanitizeName("..")).isEqualTo("arquivo");
        assertThat(FileStorageService.sanitizeName(null)).isEqualTo("arquivo");
        assertThat(FileStorageService.sanitizeName("x".repeat(200) + ".jpg")).hasSize(120).endsWith(".jpg");
    }
}
