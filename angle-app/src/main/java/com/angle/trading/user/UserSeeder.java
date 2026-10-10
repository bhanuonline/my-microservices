package com.angle.trading.user;

import com.angle.trading.persistence.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * On first boot (empty app_user table), seed the two legacy users so the app
 * is immediately usable with the same creds as before:
 *
 *   admin / admin123   ROLE_ADMIN
 *   alex  / demo123    ROLE_USER
 *
 * After the first successful run this component becomes a no-op. The admin
 * can then change passwords / add users via /admin/users.
 *
 * To skip seeding (e.g. restoring from a backup), set
 * {@code user-seed.enabled=false} in application.properties.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSeeder implements CommandLineRunner {

    private final UserService userService;
    private final AppUserRepository repo;

    @Override
    public void run(String... args) {
        if (repo.count() > 0) {
            log.info("UserSeeder: {} users already present — skipping seed", repo.count());
            return;
        }
        log.info("UserSeeder: empty app_user table — seeding default admin + alex");
        userService.create("admin", "admin123", null, "ROLE_ADMIN", true);
        userService.create("alex",  "demo123",  null, "ROLE_USER",  true);
        log.warn("⚠️  Default passwords are seeded. CHANGE THEM via /admin/users after first login.");
    }
}
