package com.fotonauts.lackr.backend.inprocess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fotonauts.lackr.backend.LackrBackendExchange;
import com.fotonauts.lackr.backend.LackrBackendRequest;

public class FemtorExchange extends LackrBackendExchange {

    static Logger log = LoggerFactory.getLogger(FemtorExchange.class);
    
	private FemtorRequest request;
	private FemtorResponse response;
	private InProcessFemtor inProcessFemtor;

	public FemtorExchange(InProcessFemtor inProcessFemtor, LackrBackendRequest spec) {
		super(inProcessFemtor, spec);
		this.request = new FemtorRequest(spec.getFrontendRequest().getRequest(), spec);
		this.response = new FemtorResponse(this);
		this.inProcessFemtor = inProcessFemtor;
	}

	@Override
	protected void doStart() throws Exception {
        inProcessFemtor.filter.doFilter(request, response, null);
        onComplete();
	}

	public FemtorResponse getResponse() {
		return response;
	}

}