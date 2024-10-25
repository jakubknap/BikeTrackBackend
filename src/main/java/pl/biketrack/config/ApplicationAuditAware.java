package pl.biketrack.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.biketrack.user.User;

import java.util.Optional;

import static java.util.Objects.isNull;

public class ApplicationAuditAware implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext()
                                                             .getAuthentication();
        if (isNull(authentication) || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        User user = (User) authentication.getPrincipal();
        return Optional.ofNullable(user.getId());
    }
}
