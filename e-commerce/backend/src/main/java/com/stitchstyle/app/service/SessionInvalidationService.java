package com.stitchstyle.app.service;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

@Service
public class SessionInvalidationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionInvalidationService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    public void invalidate(String email) {
        if (email == null) return;
        sessions.findByPrincipalName(email).keySet().forEach(sessions::deleteById);
    }
}
