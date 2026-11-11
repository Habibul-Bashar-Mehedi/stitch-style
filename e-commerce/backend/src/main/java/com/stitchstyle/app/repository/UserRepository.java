package com.stitchstyle.app.repository;

import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByGoogleSub(String googleSub);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countByRoleAndActiveTrue(Role role);
    java.util.List<User> findByRoleAndActiveTrue(Role role);
}
