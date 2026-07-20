package com.fatcatkill.controller;

import com.fatcatkill.entity.User;
import com.fatcatkill.repository.UserRepository;
import com.fatcatkill.model.MessagePayload;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_KEY_LENGTH = 256;
    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null) return usernamePasswordRequired();
        String username = normalized(payload.get("username"));
        String password = stringValue(payload.get("password"));
        MessagePayload validation = validateCredentials(username, password);
        if (validation != null) return ControllerResponses.badRequest(validation);
        String usernameKey = usernameKey(username);
        if (userRepository.existsByUsernameKey(usernameKey) || userRepository.existsByUsernameIgnoreCase(username)) {
            return usernameTaken();
        }

        User user = new User();
        user.setUsername(username);
        user.setUsernameKey(usernameKey);
        user.setPassword(hashPassword(password));
        user.setSessionToken(newSessionToken());
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            return usernameTaken();
        }
        return ResponseEntity.ok(publicUser(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null) return usernamePasswordRequired();
        String username = normalized(payload.get("username"));
        String password = stringValue(payload.get("password"));
        if (username.isBlank() || password.isBlank()) {
            return usernamePasswordRequired();
        }

        return findUserForLogin(username)
                .filter(user -> verifyPassword(password, user.getPassword()))
                .<ResponseEntity<?>>map(user -> {
                    if (needsPasswordRehash(user.getPassword())) {
                        user.setPassword(hashPassword(password));
                    }
                    if (user.getUsernameKey() == null || user.getUsernameKey().isBlank()) {
                        user.setUsernameKey(usernameKey(user.getUsername()));
                    }
                    user.setSessionToken(newSessionToken());
                    userRepository.save(user);
                    return ResponseEntity.ok(publicUser(user));
                })
                .orElseGet(() -> ControllerResponses.status(HttpStatus.UNAUTHORIZED, MessagePayload.of("backend.auth.invalidCredentials", "Invalid username or password.")));
    }

    private ResponseEntity<?> usernamePasswordRequired() {
        return ControllerResponses.badRequest(MessagePayload.of("backend.auth.usernamePasswordRequired", "Username and password are required."));
    }

    private Map<String, Object> publicUser(User user) {
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "gamesPlayed", user.getGamesPlayed() == null ? 0 : user.getGamesPlayed(),
                "gamesWon", user.getGamesWon() == null ? 0 : user.getGamesWon(),
                "sessionToken", user.getSessionToken()
        );
    }

    private MessagePayload validateCredentials(String username, String password) {
        if (username.length() < 2 || username.length() > 50) return MessagePayload.of("backend.auth.usernameLength", "Username must be 2 to 50 characters.");
        if (password.length() < 4 || password.length() > 100) return MessagePayload.of("backend.auth.passwordLength", "Password must be 4 to 100 characters.");
        return null;
    }

    private Optional<User> findUserForLogin(String username) {
        String key = usernameKey(username);
        return userRepository.findByUsernameKey(key)
                .or(() -> userRepository.findByUsernameIgnoreCase(username));
    }

    private ResponseEntity<?> usernameTaken() {
        return ControllerResponses.status(HttpStatus.CONFLICT, MessagePayload.of("backend.auth.usernameTaken", "Username is already registered."));
    }

    private String usernameKey(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
    private String normalized(Object value) {
        return stringValue(value).trim();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String newSessionToken() {
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        return String.format("%064x", new BigInteger(1, token));
    }

    private boolean needsPasswordRehash(String storedPassword) {
        return storedPassword == null || !storedPassword.startsWith("pbkdf2$" + PBKDF2_ITERATIONS + "$");
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(salt, password, PBKDF2_ITERATIONS);
        return "pbkdf2$" + PBKDF2_ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
    }

    private boolean verifyPassword(String password, String storedPassword) {
        if (storedPassword == null) return false;
        try {
            if (storedPassword.startsWith("pbkdf2$")) {
                String[] parts = storedPassword.split("\\$", 4);
                if (parts.length != 4) return false;
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                return MessageDigest.isEqual(expected, pbkdf2(salt, password, iterations));
            }
            if (storedPassword.startsWith("sha256$")) {
                String[] parts = storedPassword.split("\\$", 3);
                if (parts.length != 3) return false;
                byte[] salt = Base64.getDecoder().decode(parts[1]);
                byte[] expected = Base64.getDecoder().decode(parts[2]);
                return MessageDigest.isEqual(expected, digest(salt, password));
            }
            return false;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] pbkdf2(byte[] salt, String password, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, PBKDF2_KEY_LENGTH);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is not available.", e);
        }
    }

    private byte[] digest(byte[] salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return digest.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
