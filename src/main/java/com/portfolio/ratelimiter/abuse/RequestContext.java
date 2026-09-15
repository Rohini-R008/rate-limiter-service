package com.portfolio.ratelimiter.abuse;

/**
 * Everything a rule might need about one request. responseStatus is null
 * during the PRE_REQUEST phase and set during POST_RESPONSE.
 */
public class RequestContext {

    private final String apiKey;
    private final String ip;
    private final String method;
    private final String path;
    private final String resourceId;   // e.g. the {id} in /api/products/{id}; null if none
    private final long timestampMs;
    private Integer responseStatus;     // null until the response is known

    public RequestContext(String apiKey, String ip, String method, String path,
                          String resourceId, long timestampMs) {
        this.apiKey = apiKey;
        this.ip = ip;
        this.method = method;
        this.path = path;
        this.resourceId = resourceId;
        this.timestampMs = timestampMs;
    }

    public String apiKey()       { return apiKey; }
    public String ip()           { return ip; }
    public String method()       { return method; }
    public String path()         { return path; }
    public String resourceId()   { return resourceId; }
    public long timestampMs()    { return timestampMs; }
    public Integer responseStatus()            { return responseStatus; }
    public void setResponseStatus(Integer s)   { this.responseStatus = s; }
}
