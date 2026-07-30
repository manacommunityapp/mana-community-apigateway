package com.manacommunity.gateway.authorization;

import com.manacommunity.gateway.constants.GatewayConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.Collections;
import java.util.List;

/**
 * Evaluates authorization policies for the current request against
 * the user's roles and permissions stored in exchange attributes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyAuthorization {

    private final PermissionEvaluator permissionEvaluator;

    /**
     * Evaluates whether the current user (from exchange attributes) satisfies
     * the required roles and permissions for accessing the requested resource.
     *
     * @param exchange            the current server web exchange
     * @param requiredRoles       the roles required for the endpoint (may be empty)
     * @param requiredPermissions the permissions required for the endpoint (may be empty)
     * @return true if the user is authorized
     */
    @SuppressWarnings("unchecked")
    public boolean evaluate(ServerWebExchange exchange, List<String> requiredRoles, List<String> requiredPermissions) {
        // If no specific roles or permissions are required, allow access
        boolean noRolesRequired = requiredRoles == null || requiredRoles.isEmpty();
        boolean noPermissionsRequired = requiredPermissions == null || requiredPermissions.isEmpty();
        if (noRolesRequired && noPermissionsRequired) {
            return true;
        }

        List<String> userRoles = exchange.getAttribute(GatewayConstants.ROLES_ATTR);
        List<String> userPermissions = exchange.getAttribute(GatewayConstants.PERMISSIONS_ATTR);

        userRoles = userRoles != null ? userRoles : Collections.emptyList();
        userPermissions = userPermissions != null ? userPermissions : Collections.emptyList();

        String userId = exchange.getAttribute(GatewayConstants.USER_ID_ATTR);
        String path = exchange.getRequest().getURI().getPath();

        boolean rolesSatisfied = noRolesRequired || permissionEvaluator.hasAnyRole(userRoles, requiredRoles);
        boolean permissionsSatisfied = noPermissionsRequired || permissionEvaluator.hasAnyPermission(userPermissions, requiredPermissions);

        boolean authorized = rolesSatisfied && permissionsSatisfied;

        if (!authorized) {
            log.warn("Authorization denied for user [{}] on path [{}]. " +
                            "Required roles: {}, user roles: {}. Required permissions: {}, user permissions: {}",
                    userId, path, requiredRoles, userRoles, requiredPermissions, userPermissions);
        } else {
            log.debug("Authorization granted for user [{}] on path [{}]", userId, path);
        }

        return authorized;
    }
}
