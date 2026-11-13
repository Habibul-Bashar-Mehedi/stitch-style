package com.stitchstyle.app.service;

import com.stitchstyle.app.model.entity.AuditAction;
import com.stitchstyle.app.model.entity.AuditLog;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.AuditLogRepository;
import com.stitchstyle.app.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuditService service = new AuditService(repository, userRepository);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordCapturesTheAuthenticatedActor() {
        User actor = User.builder().id(1L).email("admin@x.com").fullName("Admin")
                .role(Role.ADMIN).active(true).build();
        when(userRepository.findByEmailIgnoreCase("admin@x.com")).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("admin@x.com", null, List.of()));

        service.record(AuditAction.UPDATE, "ORDER", 9L, Map.of("status", "PENDING"),
                Map.of("status", "SHIPPED"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getActorEmail()).isEqualTo("admin@x.com");
        assertThat(captor.getValue().getActorUser()).isSameAs(actor);
        assertThat(captor.getValue().getEntityType()).isEqualTo("ORDER");
        assertThat(captor.getValue().getAfterState()).containsEntry("status", "SHIPPED");
    }

    @Test
    void anonymousMutationsAreRecordedWithoutAnActor() {
        service.record(AuditAction.CREATE, "COMPLAINT", 1L, null, Map.of("x", "y"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getActorEmail()).isNull();
        assertThat(captor.getValue().getActorUser()).isNull();
    }

    @Test
    void searchClampsPagingAndMapsRows() {
        AuditLog log = AuditLog.builder().id(1L).action(AuditAction.UPDATE).entityType("ORDER")
                .entityId(1L).actorEmail("a@x.com").build();
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        var response = service.search("a", "order", null, null, -1, 500);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).entityType()).isEqualTo("ORDER");
    }
}
