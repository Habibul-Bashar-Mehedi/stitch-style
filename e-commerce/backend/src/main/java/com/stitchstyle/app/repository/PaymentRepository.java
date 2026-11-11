package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByProviderAndProviderTxnId(String provider, String providerTxnId);
    Optional<Payment> findByOrderId(Long orderId);
}
