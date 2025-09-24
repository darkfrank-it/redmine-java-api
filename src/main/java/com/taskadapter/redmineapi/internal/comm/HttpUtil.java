package com.taskadapter.redmineapi.internal.comm;

import org.apache.hc.core5.http.HttpEntity;

class HttpUtil {

	/**
	 * Returns entity encoding.
	 * 
	 * @param entity
	 *            entitity to get encoding.
	 * @return entity encoding string.
	 */
	public static String getEntityEncoding(HttpEntity entity) {
		return entity.getContentEncoding();
//		if (header == null)
//			return null;
//		return header.getValue();
	}

	/* *
	 * Returns entity charset to use.
	 * 
	 * @param entity
	 *            entity to check.
	 * @return entity charset to use in decoding.
	 */
//	public static String getCharset(HttpEntity entity) {
//		final String guess = EntityUtils.getContentCharSet(entity);
//		return guess == null ? StandardCharsets.UTF_8.name() : guess;
//	}
}
