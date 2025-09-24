package com.taskadapter.redmineapi.internal.comm;

import com.taskadapter.redmineapi.RedmineException;
import org.apache.hc.core5.http.ClassicHttpRequest;

/**
 * FMap communicator.
 * 
 * @author maxkar
 * 
 */
final class FmapCommunicator<K, I> implements Communicator<K> {

	private final ContentHandler<I, K> handler;
	private final Communicator<I> peer;

	public FmapCommunicator(ContentHandler<I, K> handler, Communicator<I> peer) {
		super();
		this.handler = handler;
		this.peer = peer;
	}

	@Override
	public <R> R sendRequest(ClassicHttpRequest request,
			ContentHandler<K, R> contentHandler) throws RedmineException {
		return peer.sendRequest(request,
				Communicators.compose(contentHandler, handler));
	}

}
