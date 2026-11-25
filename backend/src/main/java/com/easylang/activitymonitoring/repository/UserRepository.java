package com.easylang.activitymonitoring.repository;

import com.easylang.activitymonitoring.model.User;
import java.util.Optional;
import java.util.List;
import com.easylang.activitymonitoring.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    List<User> findByRoleAndActiveTrueOrderByFullNameAsc(Role role);
}
