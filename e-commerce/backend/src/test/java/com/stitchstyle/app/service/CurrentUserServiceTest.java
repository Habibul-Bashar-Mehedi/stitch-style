package com.stitchstyle.app.service;

import com.stitchstyle.app.exception.ResourceNotFoundException;
import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CurrentUserService service = new CurrentUserService(userRepository);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void optionalIsEmptyWithoutAuthentication() {
        assertThat(service.optional()).isEmpty();
        assertThat(service.email()).isNull();
    }

    @Test
    void optionalIgnoresAnonymousAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(service.optional()).isEmpty();
    }

    @Test
    void optionalResolvesTheDatabaseUserByEmail() {
        User user = User.builder().id(1L).email("a@x.com").fullName("A").role(Role.CUSTOMER).active(true).build();
        when(userRepository.findByEmailIgnoreCase("a@x.com")).thenReturn(Optional.of(user));
        authenticate("a@x.com");

        assertThat(service.optional()).contains(user);
        assertThat(service.require()).isSameAs(user);
        assertThat(service.email()).isEqualTo("a@x.com");
    }

    @Test
    void requireFailsWhenThePrincipalHasNoUserRow() {
        when(userRepository.findByEmailIgnoreCase("ghost@x.com")).thenReturn(Optional.empty());
        authenticate("ghost@x.com");

        assertThatThrownBy(service::require).isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void requireByIdFailsForUnknownUser() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireById(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unauthenticatedAuthenticationIsTreatedAsAnonymous() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("a@x.com", null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(service.optional()).isEmpty();
    }

    private static void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(email, null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }
}
