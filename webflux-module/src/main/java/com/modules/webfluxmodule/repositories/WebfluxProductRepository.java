package com.modules.webfluxmodule.repositories;

import com.modules.webfluxmodule.models.db.ProductR2dbc;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.Collection;
@Repository
public interface WebfluxProductRepository extends R2dbcRepository<ProductR2dbc, Long> {
    Flux<ProductR2dbc> findAllByIdAgencyAndDeleted(Long idAgency, Boolean deleted);

    Flux<ProductR2dbc> findAllByIdAgencyAndIdCategoryAndDeleted(Long idAgency, Long idCategory, Boolean deleted);
    Flux<ProductR2dbc> findAllByIdAgencyAndIdCategoryAndDeletedAndAvailable(Long idAgency, Long idCategory, Boolean deleted, Boolean available);

    Flux<ProductR2dbc> findAllByIdAgencyAndDeletedAndAvailable(Long idAgency, Boolean deleted, Boolean available);

    @Query("SELECT * FROM product WHERE id_category IN (:categoryIds) AND id_agency = :idAgency AND deleted = :deleted")
    Flux<ProductR2dbc> findAllByIdAgencyAndIdCategoryInAndDeleted(Long idAgency, Collection<Long> categoryIds, Boolean deleted);

}
