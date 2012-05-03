package com.fotonauts.lackr.client;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.io.ByteArrayBuffer;

import com.fotonauts.lackr.BackendRequest;
import com.fotonauts.lackr.HttpDirectorInterface;
import com.fotonauts.lackr.HttpHost;
import com.fotonauts.lackr.LackrBackendExchange;
import com.fotonauts.lackr.Gateway;
import com.fotonauts.lackr.hashring.HashRing.NotAvailableException;

public class JettyLackrBackendExchange extends LackrBackendExchange {

	static class JettyContentExchange extends ContentExchange {
		
		JettyLackrBackendExchange exchange;

		public JettyContentExchange(JettyLackrBackendExchange exchange) {
			super(true);
			setMethod(exchange.getBackendRequest().getMethod());
			this.exchange = exchange;

			if (exchange.getBackendRequest().getBody() != null) {
				setRequestContent(new ByteArrayBuffer(exchange.getBackendRequest()
						.getBody()));
				setRequestHeader("Content-Length",
						Integer.toString(exchange.getBackendRequest().getBody().length));
			}

		}

		@Override
		protected void onResponseComplete() throws IOException {
			super.onResponseComplete();
			exchange.onResponseComplete(false);
		}

		@Override
		protected void onConnectionFailed(Throwable x) {
			super.onConnectionFailed(x);
			exchange.getBackendRequest().getFrontendRequest().addBackendExceptions(x);
		}

		@Override
		protected void onException(Throwable x) {
			super.onException(x);
			exchange.getBackendRequest().getFrontendRequest().addBackendExceptions(x);
		}

	}

	ContentExchange jettyContentExchange;
	private HttpClient jettyClient;
	private HttpDirectorInterface director;
	private HttpHost upstream;

	@Override
	public Gateway getUpstream() throws NotAvailableException {
	    if(upstream == null)
	        upstream = director.getHostFor(getBackendRequest());
	    return upstream;
	}
	
	public JettyLackrBackendExchange(HttpClient jettyClient, HttpDirectorInterface director, BackendRequest spec) {
		super(spec);
		this.director = director;
		this.jettyClient = jettyClient;
		jettyContentExchange = new JettyContentExchange(this);
	}

	@Override
	protected int getResponseStatus() {
		return jettyContentExchange.getResponseStatus();
	}

	@Override
	protected byte[] getResponseContentBytes() {
		return jettyContentExchange.getResponseContentBytes();
	}

	@Override
	protected String getResponseHeader(String name) {
		return jettyContentExchange.getResponseFields().getStringField(name);
	}

	@Override
	public void addRequestHeader(String name, String value) {
		jettyContentExchange.addRequestHeader(name, value);
	}

	@Override
	protected List<String> getResponseHeaderNames() {
		return Collections.list(jettyContentExchange.getResponseFields().getFieldNames());
	}

	@Override
	public List<String> getResponseHeaderValues(String name) {
		return Collections.list(jettyContentExchange.getResponseFields().getValues(name));
	}

	@Override
	protected void doStart() throws IOException, NotAvailableException {
		jettyContentExchange.setURL(upstream.getHostname() + getBackendRequest().getQuery());
		jettyClient.send(jettyContentExchange);
	}

}
