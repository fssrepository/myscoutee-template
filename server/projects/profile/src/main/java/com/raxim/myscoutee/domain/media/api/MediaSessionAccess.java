package com.raxim.myscoutee.domain.media.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;

/** In-process HTTP security adapter; remote media storage still requires a transport adapter. */
public interface MediaSessionAccess {
    Authentication authenticate(HttpServletRequest request);
    void issueForProfileRead(HttpServletRequest request, HttpServletResponse response, Authentication authentication);
    public static boolean isPrivateRead(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/media/private".equals(path) || "/api/media/private".equals(path);
    }

}
