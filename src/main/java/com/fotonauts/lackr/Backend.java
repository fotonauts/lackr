package com.fotonauts.lackr;

import org.eclipse.jetty.util.component.LifeCycle;

import com.fotonauts.lackr.backend.hashring.HashRingBackend.NotAvailableException;

/**
 * Represents a backend server or any other upstream system lackr will try to delegate a {@link LackrBackendRequest} too. 
 *  
 * @author kali
 *
 */
public interface Backend extends LifeCycle {

    /**
     * The "interesting" method of the backend.
     * 
     * @param request the request specification to process
     * @return a {@link LackrBackendExchange} materialising the transaction with the Backend.
     * @throws NotAvailableException if the Backend is not available.
     */
	public LackrBackendExchange createExchange(LackrBackendRequest request) throws NotAvailableException;

	/**
	 * For debugging and logging purposes.
	 */
	public String getName();
	
	public boolean probe();
}
