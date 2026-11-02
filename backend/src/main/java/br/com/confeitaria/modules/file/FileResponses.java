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
}
