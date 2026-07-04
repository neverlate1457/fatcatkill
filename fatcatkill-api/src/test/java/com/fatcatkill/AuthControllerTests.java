package com.fatcatkill;

import com.fatcatkill.controller.AuthController;
import com.fatcatkill.entity.User;
import com.fatcatkill.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
}