package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, Long id);
    List<Category> findAllByOrderByDepthAscDisplayOrderAscIdAsc();
    List<Category> findByParentIsNullAndActiveTrueAndShowOnHomeTrueOrderByDisplayOrderAsc();
    List<Category> findByParentIsNullAndActiveTrueAndShowInNavbarTrueOrderByDisplayOrderAsc();
    List<Category> findByParentIdOrderByDisplayOrderAsc(Long parentId);
    List<Category> findByParentIsNullOrderByDisplayOrderAsc();
    long countByParentId(Long parentId);
    long countByImageMediaId(Long imageMediaId);
    boolean existsByParentId(Long parentId);
    List<Category> findByActiveTrueOrderByDepthAscDisplayOrderAsc();
}
