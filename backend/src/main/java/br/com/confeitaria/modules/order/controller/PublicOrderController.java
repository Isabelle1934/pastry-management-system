package br.com.confeitaria.modules.order.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.confeitaria.modules.order.dto.PublicOrderDtos.OrderCreatedResponse;
import br.com.confeitaria.modules.order.dto.PublicOrderDtos.OrderRequest;
import br.com.confeitaria.modules.order.service.OrderCreationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
public class PublicOrderController {

    private final OrderCreationService creation;

    public PublicOrderController(OrderCreationService creation) {
        this.creation = creation;
    }

    /**
     * Multipart: parte {@code order} (JSON), até 3 {@code referencePhotos} e 1 {@code topperPhoto} opcionais.
     */
    @PostMapping(path = "/api/public/orders", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public OrderCreatedResponse create(@Valid @RequestPart("order") OrderRequest order,
            @RequestPart(name = "referencePhotos", required = false) List<MultipartFile> referencePhotos,
            @RequestPart(name = "topperPhoto", required = false) MultipartFile topperPhoto,
            HttpServletRequest request) {
        return creation.create(order, referencePhotos, topperPhoto, request.getRemoteAddr());
    }
}
