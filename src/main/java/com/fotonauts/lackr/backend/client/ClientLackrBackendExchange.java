package com.fotonauts.lackr.backend.client;

import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fotonauts.lackr.BaseGatewayMetrics;
import com.fotonauts.lackr.HttpDirectorInterface;
import com.fotonauts.lackr.HttpHost;
import com.fotonauts.lackr.backend.LackrBackendExchange;
import com.fotonauts.lackr.backend.LackrBackendRequest;
import com.fotonauts.lackr.backend.LackrBackendResponse;
import com.fotonauts.lackr.backend.hashring.HashRing.NotAvailableException;

public class ClientLackrBackendExchange extends LackrBackendExchange {

    static Logger log = LoggerFactory.getLogger(ClientLackrBackendExchange.class);

    //	ContentExchange jettyContentExchange;
    private HttpDirectorInterface director;
    private HttpHost upstream;
    private Request request;
    protected Result result;
    private LackrBackendResponse response;
    private byte[] responseBody;

    @Override
    public BaseGatewayMetrics getUpstream() throws NotAvailableException {
        if (upstream == null)
            upstream = director.getHostFor(getBackendRequest());
        return upstream;
    }

    public ClientLackrBackendExchange(ClientBackend backend, HttpClient jettyClient, HttpDirectorInterface director,
            LackrBackendRequest spec) throws NotAvailableException {
        super(backend, spec);
        this.director = director;
        String url = director.getHostFor(spec).getHostname() + getBackendRequest().getQuery();
        request = jettyClient.newRequest(url);
        request.method(HttpMethod.fromString(spec.getMethod()));
        if (spec.getBody() != null) {
            request.header(HttpHeader.CONTENT_TYPE.asString(), spec.getFrontendRequest().getRequest().getHeader("Content-Type"));
            request.content(new BytesContentProvider(spec.getBody()));
        }
        log.debug("Created {}", this);
    }

    @Override
    public LackrBackendResponse getResponse() {
        return response;
    }

    @Override
    public void addRequestHeader(String name, String value) {
        request.getHeaders().add(name, value);
    }

    public class ResponseAdapter extends LackrBackendResponse {

        public ResponseAdapter(LackrBackendExchange exchange) {
            super(exchange);
        }

        @Override
        public int getStatus() {
            return result.getResponse().getStatus();
        }

        @Override
        public byte[] getBodyBytes() {
            return responseBody;
        }

        @Override
        public String getHeader(String name) {
            return result.getResponse().getHeaders().getStringField(name);
        }

        @Override
        public List<String> getHeaderNames() {
            return Collections.list(result.getResponse().getHeaders().getFieldNames());
        }

        @Override
        public List<String> getHeaderValues(String name) {
            return Collections.list(result.getResponse().getHeaders().getValues(name));
        }

    }

    @Override
    protected void doStart() {
        final ClientLackrBackendExchange lackrExchange = this;
        request.send(new BufferingResponseListener(100 * 1024 * 1024) {

            @Override
            public void onComplete(Result r) {
                if (r.isSucceeded()) {
                    lackrExchange.result = r;
                    lackrExchange.responseBody = getContent();
                    response = new ResponseAdapter(lackrExchange);
                    lackrExchange.onComplete();
                } else {
                    lackrExchange.getBackendRequest().getFrontendRequest().addBackendExceptions(r.getFailure());
                }
            }

            @Override
            public void onFailure(Response arg0, Throwable x) {
                lackrExchange.getBackendRequest().getFrontendRequest().addBackendExceptions(x);
            }
        });
    }

}
