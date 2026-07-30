package com.manacommunity.gateway.constants;

public final class GatewayConstants {

    private GatewayConstants() {}

    public static final String TENANT_ID_ATTR = "tenantId";
    public static final String TENANT_CODE_ATTR = "tenantCode";
    public static final String COMMUNITY_ID_ATTR = "communityId";
    public static final String USER_ID_ATTR = "userId";
    public static final String USERNAME_ATTR = "username";
    public static final String ROLES_ATTR = "roles";
    public static final String PERMISSIONS_ATTR = "permissions";
    public static final String EMAIL_ATTR = "email";
    public static final String MOBILE_ATTR = "mobile";
    public static final String DEVICE_ID_ATTR = "deviceId";
    public static final String SESSION_ID_ATTR = "sessionId";
    public static final String CORRELATION_ID_ATTR = "correlationId";
    public static final String REQUEST_ID_ATTR = "requestId";
    public static final String REQUEST_START_TIME_ATTR = "requestStartTime";
    public static final String API_KEY_ATTR = "apiKey";

    public static final long MAX_REQUEST_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    public static final String API_VERSION_PREFIX = "/api/v";
    public static final int DEFAULT_API_VERSION = 1;
    public static final int MAX_API_VERSION = 3;
}
