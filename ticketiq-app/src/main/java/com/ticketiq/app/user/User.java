package com.ticketiq.app.user;

import com.ticketiq.core.model.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A person who can sign in. The legacy email column stores the account's user ID. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String userId;

    @Column(unique = true, length = 30)
    private String employeeId;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected User() {
        // for JPA
    }

    public User(String userId, String passwordHash, String fullName, Role role) {
        this(userId, passwordHash, fullName, role, null);
    }

    public User(String userId, String passwordHash, String fullName, Role role, String employeeId) {
        this.userId = userId.trim().toLowerCase();
        this.employeeId = employeeId == null ? null : employeeId.trim();
        this.passwordHash = passwordHash;
        this.fullName = fullName.trim();
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
