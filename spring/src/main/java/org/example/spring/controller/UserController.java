package org.example.spring.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class UserController {

    @GetMapping("/me")
    public Map<String, Object> me(
            @AuthenticationPrincipal OidcUser user) {

        return user.getClaims();
    }
}
