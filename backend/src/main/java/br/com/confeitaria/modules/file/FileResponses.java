package br.com.confeitaria.modules.file;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import br.com.confeitaria.modules.file.entity.StoredFile;
import br.com.confeitaria.modules.file.service.FileStorageService;

/**
 * Resposta HTTP de download: tipo detectado no envio (nunca o informado pelo navegador), sem cache,
 * imagens exibidas na tela e PDFs sempre como anexo. O header nosniff vem da configuração de segurança.
 */
public final class FileResponses {

    private FileResponses() {
    }

    public static ResponseEntity<InputStreamResource> download(FileStorageService storage, StoredFile file) {
        boolean image = file.getContentType().startsWith("image/");
        ContentDisposition disposition = (image ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSizeBytes())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("Content-Disposition", disposition.toString())
                .body(new InputStreamResource(storage.open(file)));
    }

    /**
     * Foto pública do catálogo: o conteúdo de um id nunca muda (trocar a foto gera outro arquivo),
     * então pode ficar em cache no navegador.
     */
    public static ResponseEntity<InputStreamResource> publicImage(FileStorageService storage, StoredFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSizeBytes())
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7)).cachePublic())
                .header("Content-Disposition", ContentDisposition.inline().build().toString())
                .body(new InputStreamResource(storage.open(file)));
    }
}
