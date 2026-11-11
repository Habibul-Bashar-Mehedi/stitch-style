package com.stitchstyle.app.security;

import com.stitchstyle.app.model.entity.Role;
import com.stitchstyle.app.model.entity.User;
import com.stitchstyle.app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_SUB_LENGTH = 255;
    private static final int MAX_PICTURE_LENGTH = 1024;

    private final OidcUserService delegate = new OidcUserService();
    private final UserRepository userRepository;
    private final List<String> bootstrapAdminEmails;

    public CustomOidcUserService(UserRepository userRepository,
                                 @Value("${app.security.bootstrap-admin-emails:}") String bootstrapEmails) {
        this.userRepository = userRepository;
        this.bootstrapAdminEmails = Arrays.stream(bootstrapEmails.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        var claims = oidcUser.getClaims();

        Object emailVerified = claims.get("email_verified");
        if (!Boolean.TRUE.equals(emailVerified) && !"true".equalsIgnoreCase(String.valueOf(emailVerified))) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_unverified",
                    "Google has not verified this email address.", null));
        }

        String emailClaim = cleanClaim(stringClaim(claims, "email"), MAX_EMAIL_LENGTH);
        if (emailClaim == null || !emailClaim.contains("@")) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_missing",
                    "Google did not return a valid email address.", null));
        }
        String email = emailClaim.toLowerCase(Locale.ROOT);

        String sub = cleanClaim(oidcUser.getSubject(), MAX_SUB_LENGTH);
        if (sub == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error("subject_missing",
                    "Google did not return a subject identifier.", null));
        }

        String nameClaim = cleanClaim(stringClaim(claims, "name"), MAX_NAME_LENGTH);
        String name = nameClaim == null ? email : nameClaim;
        String picture = cleanClaim(stringClaim(claims, "picture"), MAX_PICTURE_LENGTH);

        User user = userRepository.findByGoogleSub(sub)
                .or(() -> userRepository.findByEmailIgnoreCase(email))
                .orElseGet(() -> User.builder()
                        .email(email)
                        .fullName(name)
                        .role(Role.CUSTOMER)
                        .active(true)
                        .build());

        if (!user.isActive()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_disabled",
                    "This account has been disabled.", null));
        }
        if (user.getGoogleSub() == null) {
            user.setGoogleSub(sub);
        } else if (!user.getGoogleSub().equals(sub)) {
            // The email is already linked to a different Google identity. Do not
            // silently re-bind it, otherwise an email match could take over an account.
            throw new OAuth2AuthenticationException(new OAuth2Error("account_conflict",
                    "This email is already linked to a different Google account.", null));
        }
        user.setEmail(email);
        user.setFullName(name);
        user.setPictureUrl(picture);
        user.setLastLoginAt(Instant.now());
        if (user.getRole() == Role.CUSTOMER && bootstrapAdminEmails.contains(email)) {
            user.setRole(Role.ADMIN);
        }
        userRepository.save(user);

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        // The application looks users up by email (CurrentUserService,
        // SessionInvalidationService, audit). Using Google's numeric "sub" here
        // would make authentication.getName() return the subject instead of the
        // email, breaking /api/v1/me and session invalidation. Google's sub is
        // still available through getSubject()/getClaims().
        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), "email");
    }

    private static String stringClaim(java.util.Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value instanceof String string ? string : null;
    }

    /**
     * Removes NUL and other control characters and truncates to the column size.
     * PostgreSQL rejects NUL in text/varchar/JSONB values, so no external claim
     * should ever reach the database unfiltered.
     */
    private static String cleanClaim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(value.length());
        value.codePoints()
                .filter(codePoint -> codePoint >= 0x20 && codePoint != 0x7f)
                .forEach(builder::appendCodePoint);
        String cleaned = builder.toString().trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.codePointCount(0, cleaned.length()) <= maxLength) {
            return cleaned;
        }
        return cleaned.substring(0, cleaned.offsetByCodePoints(0, maxLength));
    }
}
