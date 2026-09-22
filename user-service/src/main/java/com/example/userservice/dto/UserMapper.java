package com.example.userservice.dto;

import com.example.userservice.model.User;

// Static helper — no DI needed, no state. When mapping grows complex, swap for MapStruct.
public final class UserMapper {

    private UserMapper() {}

    public static User toEntity(CreateUserRequest req) {
        User u = new User();
        u.setName(req.name());
        u.setEmail(req.email());
        u.setPassword(req.password());   // TODO: hash before storing — see security TODO in service
        return u;
    }

    public static UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail());
    }
}
