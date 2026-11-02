package br.com.confeitaria.modules.file.entity;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Tipos aceitos, identificados pelo conteúdo (assinatura binária) — nunca pelo Content-Type
 * informado pelo navegador. A extensão do nome original também precisa corresponder.
 */
public enum FileType {
    JPEG("image/jpeg", Set.of("jpg", "jpeg")),
    PNG("image/png", Set.of("png")),
    WEBP("image/webp", Set.of("webp")),
    PDF("application/pdf", Set.of("pdf"));

    /** Bytes necessários para reconhecer qualquer um dos formatos. */
    public static final int HEADER_LENGTH = 12;

    private static final int[] JPEG_SIGNATURE = {0xFF, 0xD8, 0xFF};
    private static final int[] PNG_SIGNATURE = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final int[] RIFF_SIGNATURE = {0x52, 0x49, 0x46, 0x46};
    private static final int[] WEBP_MARKER = {0x57, 0x45, 0x42, 0x50};
    private static final int[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46, 0x2D};

    private final String contentType;
    private final Set<String> extensions;

    FileType(String contentType, Set<String> extensions) {
        this.contentType = contentType;
        this.extensions = extensions;
    }

    public String contentType() {
        return contentType;
    }

    public boolean acceptsExtension(String extension) {
        return extensions.contains(extension);
    }

    public static Optional<FileType> detect(byte[] header) {
        if (matches(header, 0, JPEG_SIGNATURE)) {
            return Optional.of(JPEG);
        }
        if (matches(header, 0, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (matches(header, 0, RIFF_SIGNATURE) && matches(header, 8, WEBP_MARKER)) {
            return Optional.of(WEBP);
        }
        if (matches(header, 0, PDF_SIGNATURE)) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    public static Optional<FileType> fromContentType(String contentType) {
        return Arrays.stream(values()).filter(t -> t.contentType.equals(contentType)).findFirst();
    }

    private static boolean matches(byte[] data, int offset, int[] expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
