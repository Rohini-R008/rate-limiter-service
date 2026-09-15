package com.portfolio.ratelimiter.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DummyController {

    @GetMapping("/products")
    public List<Map<String, Object>> listProducts() {
        return List.of(
            Map.of("id", 1, "name", "Widget"),
            Map.of("id", 2, "name", "Gadget")
        );
    }

    @GetMapping("/products/{id}")
    public Map<String, Object> getProduct(@PathVariable int id) {
        return Map.of("id", id, "name", "Product " + id);
    }

    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestBody(required = false) Map<String, Object> body) {
        return Map.of("orderId", UUID.randomUUID().toString(), "status", "created");
    }

        // Added in Phase 3 to give the credential-stuffing rule real 401s to observe.
    // Accepts one hardcoded correct credential; everything else is a 401.
    @PostMapping("/login")
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> login(
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String user = body == null ? null : body.get("username");
        String pass = body == null ? null : body.get("password");
        if ("demo".equals(user) && "correct-password".equals(pass)) {
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("status", "authenticated"));
        }
        return org.springframework.http.ResponseEntity.status(401)
                .body(java.util.Map.of("error", "invalid credentials"));
    }
}