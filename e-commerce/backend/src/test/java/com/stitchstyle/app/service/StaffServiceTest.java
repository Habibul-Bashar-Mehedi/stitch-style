package com.stitchstyle.app.service;

import com.stitchstyle.app.dto.request.StaffRequest;
import com.stitchstyle.app.dto.response.StaffResponse;
import com.stitchstyle.app.exception.DuplicateResourceException;
import com.stitchstyle.app.exception.InvalidStatusTransitionException;
import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffServiceTest {

    private UserRepository userRepository;
    private SessionInvalidationService sessionInvalidationService;
    private StaffService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        sessionInvalidationService = mock(SessionInvalidationService.class);
        service = new StaffService(userRepository, sessionInvalidationService, mock(AuditService.class));
    }

    @Test
    void listContainsOnlyAdminsAndManagers() {
        when(userRepository.findAll()).thenReturn(List.of(
                User.builder().id(1L).email("a@x.com").fullName("Admin").role(Role.ADMIN).active(true).build(),
                User.builder().id(2L).email("m@x.com").fullName("Manager").role(Role.MANAGER).active(true).build(),
                User.builder().id(3L).email("c@x.com").fullName("Customer").role(Role.CUSTOMER).active(true).build()));

        assertThat(service.list()).extracting(StaffResponse::email)
                .containsExactly("a@x.com", "m@x.com");
    }

    @Test
    void createPromotesAnExistingCustomerInsteadOfDuplicating() {
        User existing = User.builder().id(4L).email("new@x.com").fullName("Existing")
                .role(Role.CUSTOMER).active(true).build();
        when(userRepository.findByEmailIgnoreCase("new@x.com")).thenReturn(Optional.of(existing));

        StaffResponse response = service.create(new StaffRequest(" NEW@x.com ", "New Staff", Role.MANAGER, true));

        assertThat(response.id()).isEqualTo(4L);
        assertThat(response.role()).isEqualTo(Role.MANAGER);
        assertThat(existing.getFullName()).isEqualTo("New Staff");
        verify(userRepository).save(existing);
    }

    @Test
    void createRejectsAnAlreadyPrivilegedAccount() {
        User admin = User.builder().id(4L).email("admin@x.com").fullName("Admin")
                .role(Role.ADMIN).active(true).build();
        when(userRepository.findByEmailIgnoreCase("admin@x.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.create(new StaffRequest("admin@x.com", "Admin", Role.ADMIN, true)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void lastActiveAdminCannotBeDemotedOrDisabled() {
        User admin = User.builder().id(1L).email("admin@x.com").fullName("Admin")
                .role(Role.ADMIN).active(true).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(1L, new StaffRequest("admin@x.com", "Admin", Role.MANAGER, true)))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("last active Admin");

        assertThatThrownBy(() -> service.setActive(1L, false))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void roleOrActiveChangeInvalidatesSessions() {
        User manager = User.builder().id(2L).email("m@x.com").fullName("Manager")
                .role(Role.MANAGER).active(true).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));

        service.update(2L, new StaffRequest("m@x.com", "Manager", Role.ADMIN, true));

        assertThat(manager.getRole()).isEqualTo(Role.ADMIN);
        verify(sessionInvalidationService).invalidate("m@x.com");
    }

    @Test
    void noInvalidationWhenNothingChanges() {
        User manager = User.builder().id(2L).email("m@x.com").fullName("Manager")
                .role(Role.MANAGER).active(true).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));

        service.update(2L, new StaffRequest("m@x.com", "Manager", Role.MANAGER, true));

        verify(sessionInvalidationService, never()).invalidate(any());
    }

    @Test
    void setActiveDisablesAndInvalidates() {
        User manager = User.builder().id(3L).email("m@x.com").fullName("Manager")
                .role(Role.MANAGER).active(true).build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(manager));

        StaffResponse response = service.setActive(3L, false);

        assertThat(response.active()).isFalse();
        verify(sessionInvalidationService).invalidate("m@x.com");
    }

    @Test
    void updateAndSetActiveFailForUnknownStaff() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, new StaffRequest("x@y.z", "X", Role.MANAGER, true)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.setActive(404L, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void blankFullNameFallsBackToEmailOnCreate() {
        when(userRepository.findByEmailIgnoreCase("x@y.z")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(9L);
            return user;
        });

        StaffResponse response = service.create(new StaffRequest("x@y.z", " ", Role.MANAGER, null));

        assertThat(response.fullName()).isEqualTo("x@y.z");
        assertThat(response.active()).isTrue();
    }
}
