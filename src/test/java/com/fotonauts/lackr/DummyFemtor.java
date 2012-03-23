package com.fotonauts.lackr;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DummyFemtor implements Filter {

	@Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

	@Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest hr = (HttpServletRequest) request;
		if(hr.getPathInfo().startsWith("/femtor/crash")) {
			throw new ServletException("catch me or you're dead.");
		} else if(hr.getPathInfo().startsWith("/femtor")) {
			response.getWriter().println("Hi from dummy femtor");
			response.flushBuffer();
		} else {
			((HttpServletResponse) response).sendError(501);
		}
    }

	@Override
    public void destroy() {
    }

}