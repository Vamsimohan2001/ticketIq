package com.ticketiq.app.user;

import com.ticketiq.core.model.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserIdIgnoreCase(String userId);

    boolean existsByUserIdIgnoreCase(String userId);

    boolean existsByEmployeeId(String employeeId);

    List<User> findByRoleIn(Collection<Role> roles);
}
