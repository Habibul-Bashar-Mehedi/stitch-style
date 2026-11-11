package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartIdOrderByIdAsc(Long cartId);
    Optional<CartItem> findByCartIdAndVariantId(Long cartId, Long variantId);
    void deleteByCartIdAndVariantIdIn(Long cartId, Collection<Long> variantIds);
    void deleteByCartId(Long cartId);
    void deleteByVariantIdIn(Collection<Long> variantIds);
}
