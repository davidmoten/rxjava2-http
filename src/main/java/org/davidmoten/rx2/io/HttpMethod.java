package org.davidmoten.rx2.io;

public enum HttpMethod {

    GET("GET"), POST("POST");

    private final String method;

    private HttpMethod(String method) {
        this.method = method;
    }

    public String method() {
        return method;
    }
}
