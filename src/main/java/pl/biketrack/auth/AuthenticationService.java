package pl.biketrack.auth;

import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pl.biketrack.email.EmailService;
import pl.biketrack.role.Role;
import pl.biketrack.role.RoleRepository;
import pl.biketrack.security.JwtService;
import pl.biketrack.user.Token;
import pl.biketrack.user.TokenRepository;
import pl.biketrack.user.User;
import pl.biketrack.user.UserRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static pl.biketrack.email.EmailTemplateName.ACTIVATE_ACCOUNT;

@Service
public class AuthenticationService {

    private static final String ROLE_USER = "USER";

    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final EmailService emailService;
    private final String activationUrl;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthenticationService(RoleRepository roleRepository, PasswordEncoder passwordEncoder, UserRepository userRepository, TokenRepository tokenRepository,
                                 EmailService emailService, @Value("${application.mailing.frontend.activation-url}") String activationUrl,
                                 AuthenticationManager authenticationManager, JwtService jwtService) {
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.activationUrl = activationUrl;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public void register(RegistrationRequest request) throws MessagingException {
        Role userRole = roleRepository.findByName(ROLE_USER)
                                      .orElseThrow(() -> new IllegalArgumentException("ROLE USER was not initialized"));

        User user = User.builder()
                        .firstname(request.getFirstname())
                        .email(request.getEmail())
                        .password(passwordEncoder.encode(request.getPassword()))
                        .accountLocked(false)
                        .enabled(false)
                        .roles(List.of(userRole))
                        .build();

        userRepository.save(user);

        sendValidationEmail(user);
    }

    private void sendValidationEmail(User user) throws MessagingException {
        String newToken = generateAndSaveActivationToken(user);
        emailService.sendEmail(user.getEmail(), user.getFirstname(), ACTIVATE_ACCOUNT, activationUrl, newToken, "Account activation");
    }

    private String generateAndSaveActivationToken(User user) {
        String generatedToken = generateActivationCode(6);
        Token token = Token.builder()
                           .token(generatedToken)
                           .createdAt(LocalDateTime.now())
                           .expiresAt(LocalDateTime.now()
                                                   .plusMinutes(15))
                           .user(user)
                           .build();

        tokenRepository.save(token);
        return generatedToken;
    }

    private String generateActivationCode(int length) {
        String characters = "0123456789";
        StringBuilder codeBuilder = new StringBuilder();
        SecureRandom secureRandom = new SecureRandom();

        for (int i = 0; i < length; i++) {
            int randomIndex = secureRandom.nextInt(characters.length());
            codeBuilder.append(characters.charAt(randomIndex));
        }

        return codeBuilder.toString();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        var auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        var claims = new HashMap<String, Object>();
        var user = ((User) auth.getPrincipal());
        claims.put("fullName", user.getFirstname());

        var jwtToken = jwtService.generateToken(claims, user);

        return AuthenticationResponse.builder()
                                     .token(jwtToken)
                                     .build();
    }

    public void activateAccount(String token) throws MessagingException {
        Token savedToken = tokenRepository.findByToken(token)
                                          .orElseThrow(() -> new RuntimeException("Invalid token"));

        if (LocalDateTime.now()
                         .isAfter(savedToken.getExpiresAt())) {
            sendValidationEmail(savedToken.getUser());
            throw new RuntimeException("Activation token has expired. A new token has been sent to the same email address");
        }

        var user = userRepository.findById(savedToken.getUser()
                                                     .getId())
                                 .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        user.setEnabled(true);
        userRepository.save(user);
        savedToken.setValidatedAt(LocalDateTime.now());
        tokenRepository.save(savedToken);
    }
}
