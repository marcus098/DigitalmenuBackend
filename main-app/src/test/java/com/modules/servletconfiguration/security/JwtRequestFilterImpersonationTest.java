package com.modules.servletconfiguration.security;

import com.modules.common.dto.UserDto;
import com.modules.common.finders.UserUtils;
import com.modules.servletconfiguration.model.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JwtRequestFilterImpersonationTest {

    private JwtService jwt;
    private UserUtils users;
    private ImpersonationHooks hooks;
    private JwtRequestFilter filter;
    private UserDto admin;

    @BeforeEach
    void setUp() {
        jwt = SuperadminTestSupport.jwtService();
        users = mock(UserUtils.class);
        hooks = mock(ImpersonationHooks.class);
        filter = new JwtRequestFilter();
        ReflectionTestUtils.setField(filter, "jwtService", jwt);
        ReflectionTestUtils.setField(filter, "userService", users);
        ReflectionTestUtils.setField(filter, "impersonationHooks", hooks);
        admin = SuperadminTestSupport.user(42, "pizzeria", "owner@pizzeria.it", "ROLE_ADMIN", 7);
        when(users.loadUserByEmail("owner@pizzeria.it")).thenReturn(admin);
        when(hooks.isActiveSuperadmin(1L)).thenReturn(true);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String method, String path, String token) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.addHeader("Authorization", "Bearer " + token);
        req.setRemoteAddr("10.0.0.5");
        return req;
    }

    @Test
    void impersonationTokenSetsDetailsAndAttribute() throws Exception {
        String token = jwt.generateImpersonationToken(admin, 1L, "boss@platform.it");
        MockHttpServletRequest req = request("GET", "/api/categories/getAll", token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNotNull(chain.getRequest(), "la richiesta deve proseguire");
        assertEquals(1L, req.getAttribute(ImpersonationPolicy.REQUEST_ATTR_IMPERSONATED_BY));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails details = (CustomUserDetails) auth.getPrincipal();
        assertTrue(details.isImpersonated());
        assertEquals(1L, details.getImpersonatedBy());
        assertEquals("boss@platform.it", details.getImpersonatedByEmail());
        assertEquals(42L, details.getId());
        assertEquals("ROLE_ADMIN", auth.getAuthorities().iterator().next().getAuthority());
        verify(hooks, never()).recordImpersonatedRequest(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void mutatingRequestIsAudited() throws Exception {
        String token = jwt.generateImpersonationToken(admin, 1L, "boss@platform.it");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("POST", "/api/categories/addCategory", token), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
        verify(hooks).recordImpersonatedRequest(1L, "boss@platform.it", 7L, "POST", "/api/categories/addCategory", "10.0.0.5");
    }

    @Test
    void blockedPathReturns403WithMessage() throws Exception {
        String token = jwt.generateImpersonationToken(admin, 1L, "boss@platform.it");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("PUT", "/api/payments/provider/stripe", token), res, chain);

        assertNull(chain.getRequest(), "la richiesta non deve arrivare al controller");
        assertEquals(403, res.getStatus());
        assertTrue(res.getContentAsString().contains(ImpersonationPolicy.BLOCKED_MESSAGE));
        verify(hooks, never()).recordImpersonatedRequest(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void refundBlocked() throws Exception {
        String token = jwt.generateImpersonationToken(admin, 1L, "boss@platform.it");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/payments/9/refund", token), res, new MockFilterChain());
        assertEquals(403, res.getStatus());
    }

    @Test
    void impByNotSuperadminReturns401() throws Exception {
        when(hooks.isActiveSuperadmin(1L)).thenReturn(false);
        String token = jwt.generateImpersonationToken(admin, 1L, "boss@platform.it");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("GET", "/api/categories/getAll", token), res, chain);

        assertEquals(401, res.getStatus());
        assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void normalTokenUnaffectedByPolicy() throws Exception {
        String token = jwt.generateToken(admin);
        MockHttpServletRequest req = request("POST", "/api/users/changePassword", token);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
        assertNull(req.getAttribute(ImpersonationPolicy.REQUEST_ATTR_IMPERSONATED_BY));
        CustomUserDetails details = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertFalse(details.isImpersonated());
        verifyNoInteractions(hooks);
    }
}
