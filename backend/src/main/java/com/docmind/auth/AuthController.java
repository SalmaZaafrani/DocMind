package com.docmind.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docmind.common.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(
            @NotBlank(message = "Le nom est obligatoire") String fullName,
            @NotBlank(message = "L'email est obligatoire") @Email(message = "Email invalide") String email,
            @NotBlank @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères") String password) { }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) { }

    public record UserDto(Long id, String email, String fullName) {
        static UserDto of(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getFullName());
        }
    }

    public record AuthResponse(String token, UserDto user) { }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Un compte existe déjà avec cet email");
        }
        User user = users.save(new User(req.email().trim().toLowerCase(), req.fullName().trim(), encoder.encode(req.password())));
        return new AuthResponse(jwt.createToken(user), UserDto.of(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect"));
        return new AuthResponse(jwt.createToken(user), UserDto.of(user));
    }

    @GetMapping("/me")
    public UserDto me() {
        return UserDto.of(CurrentUser.get());
    }
}
