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

    @Column(length = 100)
    private String team;

    private Boolean enabled = true;
    private Boolean approvalPending = false;

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
        this(userId, passwordHash, fullName, role, employeeId, null, true);
    }

    public User(String userId, String passwordHash, String fullName, Role role, String employeeId,
                String team, boolean enabled) {
        this.userId = userId.trim().toLowerCase();
        this.employeeId = employeeId == null ? null : employeeId.trim();
        this.passwordHash = passwordHash;
        this.fullName = fullName.trim();
        this.role = role;
        this.team = team == null ? null : team.trim();
        this.enabled = enabled;
        this.approvalPending = !enabled;
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

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId == null || employeeId.isBlank() ? null : employeeId.trim();
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName.trim();
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team == null || team.isBlank() ? null : team.trim();
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public boolean isApprovalPending() {
        return Boolean.TRUE.equals(approvalPending);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setApprovalPending(boolean approvalPending) {
        this.approvalPending = approvalPending;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
