package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, Long id);
    List<Product> findTop12ByActiveTrueOrderByCreatedAtDesc();
    long countByCategoryId(Long categoryId);
    long countByCategoryIdAndActiveTrue(Long categoryId);
    List<Product> findByCategoryId(Long categoryId);

    @Query("select p from Product p where p.active = true and p.featured = true order by p.createdAt desc")
    List<Product> findFeatured();
}
