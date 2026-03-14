package com.monkmode.ledger.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String getLoginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam("username") String username, HttpSession session) {
        // Clean the username and save it to the server session
        String cleanUsername = username.trim().toLowerCase().replaceAll("\\s+", "-");
        session.setAttribute("USER_ID", cleanUsername);

        return "redirect:/"; // Send them to the dashboard
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate(); // Destroy the session
        return "redirect:/login";
    }
}