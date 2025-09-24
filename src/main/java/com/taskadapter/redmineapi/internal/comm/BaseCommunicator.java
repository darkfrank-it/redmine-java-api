package com.taskadapter.redmineapi.internal.comm;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineFormatException;
import com.taskadapter.redmineapi.RedmineTransportException;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BaseCommunicator implements Communicator<HttpResponse> {
	private final Logger logger = LoggerFactory.getLogger(BaseCommunicator.class);

	private final HttpClient client;

    public BaseCommunicator(HttpClient client) {
        this.client = client;
    }

	// TODO lots of usages process 404 code themselves, but some don't.
	// check if we can process 404 code in this method instead of forcing
	// clients to deal with it.

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.taskadapter.redmineapi.internal.comm.Communicator#sendRequest(org.apache.http
	 * .HttpRequest)
	 */
	@Override
	public <R> R sendRequest(ClassicHttpRequest request,
			ContentHandler<HttpResponse, R> handler) throws RedmineException {
		// logger.debug(request.getRequestLine().toString());
        // logger.debug("Sending request: {}", request.getRequestLine());

		 request.addHeader("Accept-Encoding", "gzip");
        //   .addResponseInterceptorLast(new ResponseContentEncoding())
		try {
//			final HttpResponse httpResponse = client
//					.execute((HttpUriRequest) request);
//			try {
//				return handler.processContent(httpResponse);
//			} finally {
//				EntityUtils.consume(httpResponse.getEntity());
//			}
        try (ClassicHttpResponse httpResponse = client.executeOpen(null, request, HttpClientContext.create())) {
            return handler.processContent(httpResponse);
        }

    } catch (ClientProtocolException e1) {
			throw new RedmineFormatException(e1);
		} catch (IOException e1) {
			throw new RedmineTransportException("Cannot fetch data from "
					+ getMessageURI(request) + " : "
							+ e1, e1);
		}
	}

	private String getMessageURI(ClassicHttpRequest request) {
		final String uri = request.getRequestUri(); //request.getRequestLine().getUri();
		final int paramsIndex = uri.indexOf('?');
		if (paramsIndex >= 0)
			return uri.substring(0, paramsIndex);
		return uri;
	}
}
