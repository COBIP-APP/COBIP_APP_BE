package com.cobip.domain.catalog;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "관리자 분류", description = "관리자 웹에서 언어와 학습 주제를 추가·수정합니다.")
class AdminCatalogController {
    private final NamedParameterJdbcTemplate jdbc;

    AdminCatalogController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/languages")
    @ResponseStatus(HttpStatus.CREATED)
    IdResponse createLanguage(@AuthenticationPrincipal Jwt jwt,
                              @Valid @RequestBody LanguageSaveRequest request) {
        requireAdmin(jwt);
        String sql = """
                INSERT INTO languages (code, name, display_order, is_active)
                VALUES (:code, :name, :displayOrder, :active)
                RETURNING language_id
                """;
        return new IdResponse(jdbc.queryForObject(sql, params(request), Long.class));
    }

    @PutMapping("/languages/{languageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateLanguage(@AuthenticationPrincipal Jwt jwt, @PathVariable Long languageId,
                        @Valid @RequestBody LanguageSaveRequest request) {
        requireAdmin(jwt);
        String sql = """
                UPDATE languages SET code = :code, name = :name,
                    display_order = :displayOrder, is_active = :active
                WHERE language_id = :id
                """;
        if (jdbc.update(sql, params(request).addValue("id", languageId)) == 0) notFound();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    IdResponse createCategory(@AuthenticationPrincipal Jwt jwt,
                              @Valid @RequestBody CategorySaveRequest request) {
        requireAdmin(jwt);
        String sql = """
                INSERT INTO categories (parent_category_id, code, name, display_order, is_active)
                VALUES (:parentId, :code, :name, :displayOrder, :active)
                RETURNING category_id
                """;
        return new IdResponse(jdbc.queryForObject(sql, params(request), Long.class));
    }

    @PutMapping("/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long categoryId,
                        @Valid @RequestBody CategoryUpdateRequest request) {
        requireAdmin(jwt);
        String sql = """
                UPDATE categories SET code = :code,
                    name = :name, display_order = :displayOrder, is_active = :active
                WHERE category_id = :id
                """;
        if (jdbc.update(sql, params(request).addValue("id", categoryId)) == 0) notFound();
    }

    private MapSqlParameterSource params(LanguageSaveRequest request) {
        return new MapSqlParameterSource("code", request.code()).addValue("name", request.name())
                .addValue("displayOrder", request.displayOrder()).addValue("active", request.active());
    }

    private MapSqlParameterSource params(CategorySaveRequest request) {
        return new MapSqlParameterSource("parentId", request.parentId())
                .addValue("code", request.code()).addValue("name", request.name())
                .addValue("displayOrder", request.displayOrder()).addValue("active", request.active());
    }

    private MapSqlParameterSource params(CategoryUpdateRequest request) {
        return new MapSqlParameterSource("code", request.code()).addValue("name", request.name())
                .addValue("displayOrder", request.displayOrder()).addValue("active", request.active());
    }

    private void requireAdmin(Jwt jwt) {
        if (!"ADMIN".equals(jwt.getClaimAsString("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required.");
        }
    }

    private void notFound() {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Catalog item not found.");
    }

    record IdResponse(Long id) {}

    record LanguageSaveRequest(
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 50) String name,
            @NotNull @Min(1) Integer displayOrder,
            @NotNull Boolean active
    ) {}

    record CategorySaveRequest(
            Long parentId,
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 50) String name,
            @NotNull @Min(1) Integer displayOrder,
            @NotNull Boolean active
    ) {}

    record CategoryUpdateRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 50) String name,
            @NotNull @Min(1) Integer displayOrder,
            @NotNull Boolean active
    ) {}
}
