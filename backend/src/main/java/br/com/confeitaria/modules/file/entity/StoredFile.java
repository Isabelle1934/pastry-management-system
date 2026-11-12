package br.com.confeitaria.modules.file.entity;

import java.time.Instant;

import br.com.confeitaria.common.audit.ActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Metadados de um arquivo enviado. O conteúdo fica em disco, fora de qualquer pasta pública. */
@Entity
@Table(name = "stored_file")
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Caminho relativo e aleatório no armazenamento (sem relação com o nome original). */
    @Column(name = "storage_key", nullable = false, length = 80, updatable = false)
    private String storageKey;

    /** Dono do arquivo: um pedido OU um produto (fotos do catálogo). */
    @Column(name = "order_id", updatable = false)
    private Long orderId;

    @Column(name = "product_id", updatable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private FilePurpose purpose;

    @Column(name = "original_name", nullable = false, length = 120, updatable = false)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 50, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "uploaded_by", nullable = false, length = 20, updatable = false)
    private ActorType uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected StoredFile() {
    }

    public StoredFile(String storageKey, Long orderId, FilePurpose purpose, String originalName, FileType type,
            long sizeBytes, String sha256, ActorType uploadedBy, Instant createdAt) {
        this.storageKey = storageKey;
        if (purpose == FilePurpose.PRODUCT_PHOTO) {
            this.productId = orderId;
        } else {
            this.orderId = orderId;
        }
        this.purpose = purpose;
        this.originalName = originalName;
        this.contentType = type.contentType();
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getProductId() {
        return productId;
    }

    /** Exclusão lógica (o arquivo em disco é mantido para auditoria). */
    public void markDeleted(Instant now) {
        this.deletedAt = now;
    }

    public FilePurpose getPurpose() {
        return purpose;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public ActorType getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
