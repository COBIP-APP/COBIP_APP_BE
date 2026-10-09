package com.cobip.domain.catalog;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api")
class CatalogController {

    private final CatalogRepository catalogRepository;

    CatalogController(CatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    @GetMapping("/languages")
    List<LanguageResponse> languages(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return catalogRepository.findLanguages(activeOnly);
    }

    @GetMapping("/categories")
    List<CategoryResponse> categories(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return catalogRepository.findCategoryTree(activeOnly);
    }
}
