package br.com.confeitaria.modules.file.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.common.exception.NotFoundException;
import br.com.confeitaria.modules.file.StorageProperties;
import br.com.confeitaria.modules.file.entity.FilePurpose;
import br.com.confeitaria.modules.file.entity.FileType;
import br.com.confeitaria.modules.file.entity.StoredFile;
import br.com.confeitaria.modules.file.repository.StoredFileRepository;
import jakarta.annotation.PostConstruct;

/**
 * Armazenamento local de arquivos enviados (ADR-006).
 * <ul>
 *   <li>Tipo verificado pelo conteúdo e pela extensão; o Content-Type do navegador é ignorado.</li>
 *   <li>Nome interno aleatório (UUID), em diretório privado, sem extensão executável.</li>
 *   <li>Se a transação que criou o registro for desfeita, o arquivo em disco é apagado.</li>
 * </ul>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /** Arquivo validado, ainda não gravado. */
    public record ValidatedFile(MultipartFile source, FileType type, String originalName) {
    }

    private final StorageProperties properties;
    private final StoredFileRepository repository;
    private final Clock clock;
    private Path root;

    public FileStorageService(StorageProperties properties, StoredFileRepository repository, Clock clock) {
        this.properties = properties;
        this.repository = repository;
        this.clock = clock;
    }

    @PostConstruct
    void init() throws IOException {
        if (properties.uploadDir() == null) {
            throw new IllegalStateException("Configure app.storage.upload-dir (APP_UPLOAD_DIR).");
        }
        root = properties.uploadDir().toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("Armazenamento de arquivos em {}", root);
    }

    /**
     * Valida tamanho, extensão e conteúdo real. Não grava nada.
     *
     * @param field nome do campo para a mensagem de erro
     */
    public ValidatedFile validate(MultipartFile file, FilePurpose purpose, String field) {
        if (file == null || file.isEmpty()) {
            throw new FieldValidationException(field, "O arquivo está vazio.");
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new FieldValidationException(field,
                    "Cada arquivo pode ter no máximo " + properties.maxFileSize().toMegabytes() + " MB.");
        }
        String originalName = sanitizeName(file.getOriginalFilename());
        FileType type = detect(file).filter(purpose::allows)
                .orElseThrow(() -> new FieldValidationException(field, purpose.allowedMessage()));
        if (!type.acceptsExtension(extensionOf(originalName))) {
            throw new FieldValidationException(field, "A extensão do arquivo não corresponde ao seu conteúdo. "
                    + purpose.allowedMessage());
        }
        return new ValidatedFile(file, type, originalName);
    }

    /** Grava o arquivo e registra os metadados. Exige a transação de quem chama. */
    @Transactional(propagation = Propagation.MANDATORY)
    public StoredFile store(ValidatedFile file, Long orderId, FilePurpose purpose, ActorType uploadedBy) {
        String key = newStorageKey();
        Path target = resolve(key);
        String sha256;
        long size;
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
            try (DigestInputStream in = new DigestInputStream(file.source().getInputStream(), sha256Digest())) {
                size = Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                sha256 = HexFormat.of().formatHex(in.getMessageDigest().digest());
                moveAtomically(temp, target);
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(temp);
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar arquivo enviado", e);
        }
        deleteOnRollback(target);

        StoredFile stored = new StoredFile(key, orderId, purpose, file.originalName(), file.type(), size, sha256,
                uploadedBy, clock.instant());
        return repository.save(stored);
    }

    /** Arquivo de um pedido, para download autorizado. Nunca devolve arquivo de outro pedido. */
    @Transactional(readOnly = true)
    public StoredFile findForOrder(Long orderId, Long fileId) {
        return repository.findById(fileId)
                .filter(f -> f.getOrderId().equals(orderId) && f.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("Arquivo não encontrado."));
    }

    public InputStream open(StoredFile file) {
        try {
            return Files.newInputStream(resolve(file.getStorageKey()));
        } catch (IOException e) {
            throw new NotFoundException("Arquivo não encontrado.");
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Chave de armazenamento inválida");
        }
        return path;
    }

    /** Ex.: "3f/3f2a9c…-….bin" — subpasta pelos 2 primeiros caracteres para não acumular tudo em uma pasta. */
    private static String newStorageKey() {
        String id = UUID.randomUUID().toString();
        return id.substring(0, 2) + "/" + id + ".bin";
    }

    private static java.util.Optional<FileType> detect(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return FileType.detect(in.readNBytes(FileType.HEADER_LENGTH));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler arquivo enviado", e);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private static void deleteOnRollback(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        log.warn("Não foi possível apagar arquivo de transação desfeita: {}", path.getFileName());
                    }
                }
            }
        });
    }

    /** Mantém só o nome-base, sem caminhos nem caracteres de controle (usado apenas para exibição). */
    static String sanitizeName(String name) {
        if (name == null) {
            return "arquivo";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").strip();
        if (base.isEmpty() || base.equals(".") || base.equals("..")) {
            return "arquivo";
        }
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
