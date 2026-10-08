package com.cobip.domain.catalog;

import java.util.List;

record LanguageResponse(
        Long id,
        String code,
        String name,
        Integer displayOrder,
        Boolean active
) {
}

record CategoryResponse(
        Long id,
        Long parentId,
        String code,
        String name,
        Integer displayOrder,
        Boolean active,
        List<CategoryResponse> children
) {
}
