package com.taskadapter.redmineapi.internal.comm.redmine;

import java.io.UnsupportedEncodingException;

import com.taskadapter.redmineapi.internal.comm.Communicator;
import com.taskadapter.redmineapi.internal.comm.ContentHandler;
import java.util.Base64;
import org.apache.hc.core5.http.ClassicHttpRequest;
import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineInternalError;

public class RedmineAuthenticator<K> implements Communicator<K> {
	/**
	 * Header value.
	 */
	private String authKey;

	/**
	 * Used charset.
	 */
	private final String charset;

	/**
	 * Peer communicator.
	 */
	private final Communicator<K> peer;

	public RedmineAuthenticator(Communicator<K> peer, String charset) {
		this.peer = peer;
		this.charset = charset;
	}

	public void setCredentials(String login, String password) {
		if (login == null) {
			authKey = null;
			return;
		}
		try {
			authKey = "Basic "
					+ Base64.getEncoder().encodeToString(
							(login + ':' + password).getBytes(charset)).trim();
		} catch (UnsupportedEncodingException e) {
			throw new RedmineInternalError(e);
		}
	}

	@Override
	public <R> R sendRequest(ClassicHttpRequest request, ContentHandler<K, R> handler)
			throws RedmineException {
		if (authKey != null)
			request.addHeader("Authorization", authKey);
		return peer.sendRequest(request, handler);
	}

}
