package com.raxim.myscoutee.domain.administration.dto;

import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}

    public record AdminUserDto(
            String id,
            String name,
            String initials,
            String email,
            String headline,
            String about,
            List<String> images) {}
}
