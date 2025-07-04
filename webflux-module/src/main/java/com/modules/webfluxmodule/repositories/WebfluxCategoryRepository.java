package com.modules.webfluxmodule.repositories;

import com.modules.common.model.db.CategoriesWithProduct;
import com.modules.webfluxmodule.models.db.CategoryR2dbc;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface WebfluxCategoryRepository extends R2dbcRepository<CategoryR2dbc, Long> {
    Flux<CategoryR2dbc> findByIdAgencyAndDeleted(Long idAgency, Boolean deleted);

    Flux<CategoryR2dbc> findByIdAgencyAndDeletedAndAvailable(Long idAgency, Boolean deleted, Boolean available);

    @Query("""
        SELECT 
            c.id AS category_id,
            c.name AS category_name,
            c.description AS category_description,
            c.image AS image,
            c.available AS available,
            COALESCE(
                        '|' || STRING_AGG(p.id::TEXT || ':' || p.position_progressive::TEXT, '|') || '|',
                        '|'
                    ) AS products
        FROM 
            categories c
        LEFT JOIN 
            products p ON p.idCategory = c.id
        WHERE c.id_agency = :id_agency and c.deleted = :deleted
        GROUP BY 
            c.id, c.name, c.description, c.image, c.available
    """)
    Flux<CategoriesWithProduct> findCategoriesWithProductsByIdAgencyAndDeleted(Long idAgency, boolean deleted);
}
