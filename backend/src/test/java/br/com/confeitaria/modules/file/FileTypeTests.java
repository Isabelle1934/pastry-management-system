package br.com.confeitaria.modules.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import br.com.confeitaria.modules.file.entity.FilePurpose;
import br.com.confeitaria.modules.file.entity.FileType;

class FileTypeTests {

    static byte[] bytes(int... values) {
        byte[] result = new byte[Math.max(values.length, 16)];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    @Test
    void detectsAllowedFormatsByContent() {
        assertThat(FileType.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0))).contains(FileType.JPEG);
        assertThat(FileType.detect(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A))).contains(FileType.PNG);
        assertThat(FileType.detect(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'))).contains(FileType.WEBP);
        assertThat(FileType.detect("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII))).contains(FileType.PDF);
    }

    @Test
    void rejectsUnknownOrDisguisedContent() {
        assertThat(FileType.detect("<html><script>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(FileType.detect(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'))).isEmpty();
        assertThat(FileType.detect(bytes('M', 'Z'))).as("executável Windows").isEmpty();
        assertThat(FileType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).as("cabeçalho incompleto").isEmpty();
    }

    @Test
    void eachPurposeAllowsOnlyItsTypes() {
        assertThat(FilePurpose.ORDER_REFERENCE.allows(FileType.WEBP)).isTrue();
        assertThat(FilePurpose.ORDER_REFERENCE.allows(FileType.PDF)).isFalse();
        assertThat(FilePurpose.PAYMENT_PROOF.allows(FileType.PDF)).isTrue();
        assertThat(FilePurpose.PAYMENT_PROOF.allows(FileType.WEBP)).isFalse();
    }
}
