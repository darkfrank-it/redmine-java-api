package com.taskadapter.redmineapi.internal.comm;

import com.taskadapter.redmineapi.RedmineException;
import org.apache.hc.core5.http.ClassicHttpRequest;

public interface SimpleCommunicator<T> {
	/**
	 * Performs a request.
	 * 
	 * @return the response body.
	 */
	T sendRequest(ClassicHttpRequest request) throws RedmineException;

}
