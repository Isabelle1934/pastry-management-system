package br.com.confeitaria.modules.file.entity;

import java.util.Set;

/** Finalidade do arquivo e os tipos aceitos para cada uma. */
public enum FilePurpose {
    ORDER_REFERENCE(Set.of(FileType.JPEG, FileType.PNG, FileType.WEBP), "Envie fotos em JPG, PNG ou WEBP."),
    TOPPER_REFERENCE(Set.of(FileType.JPEG, FileType.PNG, FileType.WEBP), "Envie fotos em JPG, PNG ou WEBP."),
    PAYMENT_PROOF(Set.of(FileType.JPEG, FileType.PNG, FileType.PDF), "Envie o comprovante em JPG, PNG ou PDF."),
    PRODUCT_PHOTO(Set.of(FileType.JPEG, FileType.PNG, FileType.WEBP), "Envie fotos em JPG, PNG ou WEBP.");

    private final Set<FileType> allowedTypes;
    private final String allowedMessage;

    FilePurpose(Set<FileType> allowedTypes, String allowedMessage) {
        this.allowedTypes = allowedTypes;
        this.allowedMessage = allowedMessage;
    }

    public boolean allows(FileType type) {
        return allowedTypes.contains(type);
    }

    public String allowedMessage() {
        return allowedMessage;
    }
}
