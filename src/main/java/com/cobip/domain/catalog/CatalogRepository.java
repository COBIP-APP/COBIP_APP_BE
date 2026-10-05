package com.cobip.domain.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogRepository {

    private final JdbcClient jdbcClient;

    CatalogRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    List<LanguageResponse> findLanguages(boolean activeOnly) {
        String sql = """
                SELECT language_id, code, name, display_order, is_active
                FROM languages
                WHERE (:activeOnly = false OR is_active = true)
                ORDER BY display_order
                """;

        return jdbcClient.sql(sql)
                .param("activeOnly", activeOnly)
                .query((rs, rowNum) -> new LanguageResponse(
                        rs.getLong("language_id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getInt("display_order"),
                        rs.getBoolean("is_active")))
                .list();
    }

    List<CategoryResponse> findCategoryTree(boolean activeOnly) {
        String sql = """
                SELECT category_id, parent_category_id, code, name, display_order, is_active
                FROM categories
                WHERE (:activeOnly = false OR is_active = true)
                ORDER BY parent_category_id NULLS FIRST, display_order
                """;

        List<CategoryRow> rows = jdbcClient.sql(sql)
                .param("activeOnly", activeOnly)
                .query((rs, rowNum) -> new CategoryRow(
                        rs.getLong("category_id"),
                        rs.getObject("parent_category_id", Long.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getInt("display_order"),
                        rs.getBoolean("is_active")))
                .list();

        Map<Long, List<CategoryRow>> childrenByParent = new LinkedHashMap<>();
        for (CategoryRow row : rows) {
            childrenByParent
                    .computeIfAbsent(row.parentId(), ignored -> new ArrayList<>())
                    .add(row);
        }

        return childrenByParent.getOrDefault(null, List.of()).stream()
                .sorted(Comparator.comparing(CategoryRow::displayOrder))
                .map(row -> toResponse(row, childrenByParent))
                .toList();
    }

    private CategoryResponse toResponse(CategoryRow row, Map<Long, List<CategoryRow>> childrenByParent) {
        List<CategoryResponse> children = childrenByParent.getOrDefault(row.id(), List.of()).stream()
                .sorted(Comparator.comparing(CategoryRow::displayOrder))
                .map(child -> toResponse(child, childrenByParent))
                .toList();

        return new CategoryResponse(
                row.id(),
                row.parentId(),
                row.code(),
                row.name(),
                row.displayOrder(),
                row.active(),
                children);
    }

    private record CategoryRow(
            Long id,
            Long parentId,
            String code,
            String name,
            Integer displayOrder,
            Boolean active
    ) {
    }
}
