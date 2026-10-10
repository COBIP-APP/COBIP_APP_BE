package com.cobip.domain.catalog;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "분류", description = "프로그래밍 언어와 학습 주제 조회")
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
