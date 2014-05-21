package com.xebialabs.deployit.ci.server;

import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreemptiveAuthenticationInterceptor implements HttpRequestInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreemptiveAuthenticationInterceptor.class);

    public void process(final HttpRequest request, final HttpContext context) throws HttpException, IOException {
        if(request.getFirstHeader("Authorization") == null) {
            LOGGER.trace("No 'Authorization' header found for request: {}", request.getRequestLine());
            HttpHost targetHost = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
            CredentialsProvider credentialsProvider = (CredentialsProvider) context.getAttribute(ClientContext.CREDS_PROVIDER);
            if(credentialsProvider != null) {
                Credentials credentials = credentialsProvider.getCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()));
                if (credentials != null) {
                    request.setHeader(new BasicScheme().authenticate(credentials, request, context));
                    LOGGER.trace("Set 'Authorization' header {} for request: {}", credentials.getUserPrincipal(), request.getRequestLine());
                }
            }
        }
    }

}