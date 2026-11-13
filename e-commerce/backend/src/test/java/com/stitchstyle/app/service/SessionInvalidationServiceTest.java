package com.stitchstyle.app.service;

import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionInvalidationServiceTest {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void invalidatingANullEmailIsANoOp() {
        FindByIndexNameSessionRepository repository = mock(FindByIndexNameSessionRepository.class);
        new SessionInvalidationService(repository).invalidate(null);
        verify(repository, never()).findByPrincipalName(org.mockito.ArgumentMatchers.anyString());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void everySessionForThePrincipalIsDeleted() {
        FindByIndexNameSessionRepository repository = mock(FindByIndexNameSessionRepository.class);
        Session first = mock(Session.class);
        Session second = mock(Session.class);
        when(repository.findByPrincipalName("a@x.com"))
                .thenReturn(Map.of("s1", first, "s2", second));

        new SessionInvalidationService(repository).invalidate("a@x.com");

        verify(repository).deleteById("s1");
        verify(repository).deleteById("s2");
    }
}
