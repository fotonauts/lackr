package com.fotonauts.lackr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.api.Request.HeadersListener;
import org.eclipse.jetty.http.HttpHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fotonauts.lackr.backend.LackrBackendExchange;
import com.fotonauts.lackr.backend.LackrBackendRequest;
import com.fotonauts.lackr.backend.LackrBackendRequest.Listener;
import com.fotonauts.lackr.backend.hashring.HashRing.NotAvailableException;
import com.fotonauts.lackr.interpolr.Document;
import com.fotonauts.lackr.interpolr.Interpolr;
import com.fotonauts.lackr.interpolr.InterpolrContext;
import com.fotonauts.lackr.interpolr.InterpolrScope;
import com.fotonauts.lackr.interpolr.SimpleInterpolrScope;
import com.fotonauts.lackr.mustache.MustacheContext;
import com.mongodb.BasicDBObject;

public class InterpolrFrontendRequest extends BaseFrontendRequest implements InterpolrContext {

    static Logger log = LoggerFactory.getLogger(InterpolrFrontendRequest.class);

    private AtomicInteger pendingCount;

    protected InterpolrProxy service;

    private List<LackrPresentableError> backendExceptions = Collections.synchronizedList(new ArrayList<LackrPresentableError>(5));

    private MustacheContext mustacheContext;

    private ConcurrentHashMap<String, InterpolrScope> backendRequestCache = new ConcurrentHashMap<String, InterpolrScope>();

    private ProxyInterpolrScope rootScope;

    InterpolrFrontendRequest(final InterpolrProxy baseProxy, HttpServletRequest request) {
        super(baseProxy, request);
        this.service = baseProxy;
        this.pendingCount = new AtomicInteger(0);
        this.mustacheContext = new MustacheContext(this);
    }

    public InterpolrScope getSubBackendExchange(String url, String format, InterpolrScope dad) {
        log.debug("{} requires {} (as {})", dad, url, format);
        String key = format + "::" + url;
        InterpolrScope ex = backendRequestCache.get(key);
        if (ex != null)
            return ex;

        final ProxyInterpolrScope newBorn = new ProxyInterpolrScope(this);
        backendRequestCache.put(key, newBorn);
        LackrBackendRequest dadRequest = ((ProxyInterpolrScope) dad).getRequest();
        LackrBackendRequest req = new LackrBackendRequest(this, "GET", url, dadRequest.getQuery(), dad.hashCode(), format, null,
                dadRequest.getFields(), new Listener() {

                    @Override
                    public void fail(Throwable t) {
                        addBackendExceptions(t);
                        log.debug("Failure for {}", newBorn.toString());
                        log.debug("with: ", t);
                        if (pendingCount.decrementAndGet() == 0)
                            yieldRootRequestProcessing();
                    }

                    @Override
                    public void complete() {
                        try {
                            log.debug("Request completion for {}", newBorn.toString());
                            getInterpolr().processResult(newBorn);
                            log.debug("Interpolation done for {}", newBorn.toString());
                        } finally {
                            if (pendingCount.decrementAndGet() == 0)
                                yieldRootRequestProcessing();
                        }
                    }
                });
        newBorn.setRequest(req);
        pendingCount.incrementAndGet();
        scheduleUpstreamRequest(req);
        return newBorn;
    }

    protected void yieldRootRequestProcessing() {
        log.debug("Yield root request.");
        super.onBackendRequestComplete();
    }

    @Override
    protected void preflightCheck() {
        log.debug("Entering preflight check for {}", this);
        try {
            getMustacheContext().checkAndCompileAll();
            if (rootScope.getParsedDocument() != null) {
                rootScope.getParsedDocument().check();
            }
        } catch (Throwable e) {
            backendExceptions.add(LackrPresentableError.fromThrowable(e));
        }
    }

    public void writeResponse(HttpServletResponse response) throws IOException {
        if (pendingCount.get() > 0)
            backendExceptions.add(new LackrPresentableError("There is unfinished business with backends..."));

        super.writeResponse(response);
    }

    public MustacheContext getMustacheContext() {
        return mustacheContext;
    }

    public Interpolr getInterpolr() {
        return service.getInterpolr();
    }

    @Override
    public void onBackendRequestComplete() {
        log.debug("Request completion for root: {}", getPathAndQuery(request));
        rootScope = new ProxyInterpolrScope(this);
        rootScope.setRequest(rootRequest);
        getInterpolr().processResult(rootScope);
        log.debug("Interpolation done for root: {}", getPathAndQuery(request));
        if (pendingCount.get() == 0) {
            log.debug("No ESI found for {}.", getPathAndQuery(request));
            yieldRootRequestProcessing();
        }
    }

    protected void writeContentLengthHeaderAndBody(HttpServletResponse response) throws IOException {
        if (rootScope.getParsedDocument().length() > 0) {
            response.setContentLength(rootScope.getParsedDocument().length());
            if (request.getMethod() != "HEAD")
                rootScope.getParsedDocument().writeTo(response.getOutputStream());
        }
    }

    @Override
    public String toString() {
        return rootRequest.toString();
    }
}
