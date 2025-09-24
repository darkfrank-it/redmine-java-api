package com.taskadapter.redmineapi.internal.comm;

import com.taskadapter.redmineapi.RedmineException;
import org.apache.hc.core5.http.ClassicHttpRequest;

public interface Communicator<K> {

	/**
	 * Performs a request.
	 * 
	 * @return the response body.
	 */
	<R> R sendRequest(ClassicHttpRequest request, ContentHandler<K, R> contentHandler) throws RedmineException;

}