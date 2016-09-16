/**
 * Copyright 2016 Cohorte Technologies (ex. isandlaTech)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cohorte.herald.http.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.ServiceController;
import org.apache.felix.ipojo.annotations.Validate;
import org.cohorte.herald.http.IHttpConstants;
import org.cohorte.herald.http.IHttpServiceAvailabilityChecker;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.service.log.LogService;

/**
 * 
 * @author Bassem Debbabi
 *
 */
@Component(name = IHttpConstants.FACTORY_HTTPSERVICE_AVAILABILIY_CHECKER)
@Provides(specifications = IHttpServiceAvailabilityChecker.class)
@Instantiate(name = IHttpConstants.INSTANC_HTTPSERVICE_AVAILABILIY_CHECKER)
public class CHttpServiceAvailabilityChecker implements IHttpServiceAvailabilityChecker {		

	/** name of servie property holding valid Http Service endpoints */
	// TODO check on framework osgi api
    private static final String HTTP_SERVICE_ENDPOINTS = "osgi.http.service.endpoints";
    
	/** HTTP service port */
    private int pHttpPort;
    
	/** HTTP service, injected by iPOJO */
    @Requires
    private HttpService pHttpService;    
    
	/** Service controller */
    @ServiceController(value = false)
	boolean pController;
	
    /** The log service */
    @Requires(optional = true)
    private LogService pLogger;
    
    /** OSGi Bundle Context */
    private BundleContext pBundleContext;
    
    /** Timer used to check if endpoint's port is set */
    private Timer pTimer;
    
    /**
     * Constructor.
     * @param aBundleContext
     */
    public CHttpServiceAvailabilityChecker(BundleContext aBundleContext) {
		pBundleContext = aBundleContext;
	}
    
    /*
     * (non-Javadoc)
     * @see org.cohorte.isolates.discovery.local.IHttpServiceAvailabilityChecker#getPort()
     */
	@Override
	public int getPort() {		
		return pHttpPort;
	}
    
	@Validate
	public void validate() {		
		// set service listener to check if endpoint port is changed
		String wFilter =  "(objectclass=" + HttpService.class.getName() + ")";
		ServiceListener wListener = new ServiceListener() {			
			@Override
			public void serviceChanged(ServiceEvent aServiceEvent) {
				if (aServiceEvent.getType() == ServiceEvent.MODIFIED) {
					pLogger.log(LogService.LOG_INFO, "Properties modified! should update Http service port..");
					//checkEndpoints(aServiceEvent.getServiceReference().getProperty(HTTP_SERVICE_ENDPOINTS));
					// to garanty the synchronization of tests, we call instead the synchronized method:
					checkRegisteredHttpService();
				}
			}				
		};
		try {								
			pBundleContext.addServiceListener(wListener, wFilter);						
		} catch (InvalidSyntaxException e) {			
			pLogger.log(LogService.LOG_ERROR, "Invalid syntax while registering a service listener: " 
							+ e.getMessage());
			pController = false;
			pHttpPort = -1;
		}
		// set a timer to check periodically if endpoint port is set the first time!
		// when the port is set, the timer is canceled and we do use only service listener
		// to check if port change again.
		pTimer = new Timer();
		pTimer.scheduleAtFixedRate(new TimerTask() {
			
			@Override
			public void run() {
				checkRegisteredHttpService();
				if (pHttpPort > 0) {							
					this.cancel();
				}
			}
		}, 1000, 1000);						
	}
	
	@Invalidate
	public void invalidate() {
		pController = false;
		pHttpPort = -1;
		if (pTimer != null) {
			pTimer.cancel();
			pTimer = null;
		}
	}
	
	/**
	 * Checks if the Http Service has a service property "osgi.http.service.endpoints" 
	 * that has valid urls with a port > 0.
	 */
	private synchronized void checkRegisteredHttpService() {
		ServiceReference<?> wHttpServiceRef = pBundleContext.getServiceReference(HttpService.class);
		if (wHttpServiceRef != null) {
			Object wEndpointsObj = wHttpServiceRef.getProperty(HTTP_SERVICE_ENDPOINTS);									
			checkEndpoints(wEndpointsObj);			
		}
	}

	/**
	 * Checks the list of provided endpoints if the port is set.
	 * 
	 * @param aEndpoints String array of endpoints
	 * @return
	 */
	private boolean checkEndpoints(Object aEndpoints) {
		if (aEndpoints == null) {
			pController = false;
			pHttpPort = -1;
			return false;
		}		
		String[] wEndpoints = (String[])aEndpoints;
		pLogger.log(LogService.LOG_INFO, "Checking endpoints: " + Arrays.toString(wEndpoints));
		for (int i=0; i< wEndpoints.length; i++) {
			int wPort = getPort(wEndpoints[i]); 
			if (wPort > 0) {
				pLogger.log(LogService.LOG_INFO, "Check Ok. Port= " + wPort);
				pHttpPort = wPort;
				pController = true;
				return true;
			}
		}
		return false;
	}	
	
	/**
	 * Extracts the port from an endpoint url.
	 * 
	 * @param aEndpoint
	 * @return
	 */
	private int getPort(String aEndpoint) {
		try {
			URL wUrl = new URL(aEndpoint);
			return wUrl.getPort();
		} catch (MalformedURLException e) {
			pLogger.log(LogService.LOG_ERROR, 
					"Cannot retrieve Http service port from malformed endpoint url exception! " 
					+ e.getMessage());
			return -1;
		}		
	}


	
}
