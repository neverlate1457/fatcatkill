package com.fatcatkill;

import com.fatcatkill.controller.AuthController;
import com.fatcatkill.entity.User;
import com.fatcatkill.model.MessagePayload;
import com.fatcatkill.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuthControllerTests {

    @Autowired
    private AuthController authController;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registerStoresPbkdf2HashAndLoginVerifiesPassword() {
        String username = "auth-user-" + System.nanoTime();
        ResponseEntity<?> registerResponse = authController.register(Map.of(
                "username", username,
                "password", "correct-password"
        ));

        assertThat(registerResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(registerResponse.getBody()).isInstanceOf(Map.class);
        Map<?, ?> registerBody = (Map<?, ?>) registerResponse.getBody();
        assertThat(registerBody.containsKey("password")).isFalse();
        assertThat(registerBody.get("sessionToken")).isInstanceOf(String.class);

        User savedUser = userRepository.findByUsernameIgnoreCase(username).orElseThrow();
        assertThat(savedUser.getPassword()).startsWith("pbkdf2$");
        assertThat(savedUser.getUsernameKey()).isEqualTo(username.toLowerCase(Locale.ROOT));
        assertThat(savedUser.getPassword()).doesNotContain("correct-password");

        ResponseEntity<?> loginResponse = authController.login(Map.of(
                "username", username.toUpperCase(),
                "password", "correct-password"
        ));
        assertThat(loginResponse.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> loginBody = (Map<?, ?>) loginResponse.getBody();
        assertThat(loginBody.get("username")).isEqualTo(username);
        assertThat(loginBody.get("sessionToken")).isInstanceOf(String.class);

        ResponseEntity<?> wrongPasswordResponse = authController.login(Map.of(
                "username", username,
                "password", "wrong-password"
        ));
        assertThat(wrongPasswordResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authEndpointsReturnMessagePayloadForMissingBody() {
        ResponseEntity<?> registerResponse = authController.register(null);
        ResponseEntity<?> loginResponse = authController.login(null);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(messageKey(registerResponse)).isEqualTo("backend.auth.usernamePasswordRequired");
        assertThat(messageKey(loginResponse)).isEqualTo("backend.auth.usernamePasswordRequired");
    }

    @Test
    void registerRejectsCaseInsensitiveDuplicateUsername() {
        String username = "CaseUser" + System.nanoTime();
        ResponseEntity<?> firstResponse = authController.register(Map.of(
                "username", username,
                "password", "case-password"
        ));
        ResponseEntity<?> duplicateResponse = authController.register(Map.of(
                "username", username.toUpperCase(Locale.ROOT),
                "password", "case-password"
        ));

        assertThat(firstResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void loginMigratesLegacySha256PasswordToPbkdf2() throws Exception {
        String username = "legacy-auth-user-" + System.nanoTime();
        User user = new User();
        user.setUsername(username);
        user.setPassword(legacySha256Password("legacy-password"));
        user.setSessionToken("old-token");
        userRepository.save(user);

        ResponseEntity<?> loginResponse = authController.login(Map.of(
                "username", username,
                "password", "legacy-password"
        ));

        assertThat(loginResponse.getStatusCode().is2xxSuccessful()).isTrue();
        User migrated = userRepository.findByUsernameIgnoreCase(username).orElseThrow();
        assertThat(migrated.getPassword()).startsWith("pbkdf2$");
        assertThat(migrated.getUsernameKey()).isEqualTo(username.toLowerCase(Locale.ROOT));
        assertThat(migrated.getPassword()).doesNotContain("legacy-password");
        assertThat(migrated.getSessionToken()).isNotEqualTo("old-token");
    }

    private String messageKey(ResponseEntity<?> response) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        return ((MessagePayload) body.get("message")).getKey();
    }

    private String legacySha256Password(String password) throws Exception {
        byte[] salt = "legacy-salt".getBytes(StandardCharsets.UTF_8);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
        return "sha256$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
    }
}
