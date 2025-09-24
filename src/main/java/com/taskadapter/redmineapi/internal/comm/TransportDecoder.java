package com.taskadapter.redmineapi.internal.comm;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineTransportException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Transport encoding decoder.
 * 
 * @author maxkar
 * 
 */
final class TransportDecoder implements
		ContentHandler<ClassicHttpResponse, BasicHttpResponse> {

	@Override
	public BasicHttpResponse processContent(ClassicHttpResponse response)
			throws RedmineException {
		final HttpEntity entity = response.getEntity();
		if (entity == null) {
			return new BasicHttpResponse(response.getCode(),
					InputStream.nullInputStream(),
					StandardCharsets.UTF_8.name());
		}
		String charset = entity.getContentEncoding(); //HttpUtil.getCharset(entity);
		if(charset == null) {charset = StandardCharsets.UTF_8.name();}
        final String encoding = HttpUtil.getEntityEncoding(entity);
		try {
			final InputStream initialStream = entity.getContent();
			return new BasicHttpResponse(response
					.getCode(), decodeStream(encoding, initialStream),
					charset);
		} catch (IOException e) {
			throw new RedmineTransportException(e);
		}
	}

	/**
	 * Decodes a transport stream.
	 * 
	 * @param encoding
	 *            stream encoding.
	 * @param initialStream
	 *            initial stream.
	 * @return decoding stream.
	 * @throws IOException
	 */
	private InputStream decodeStream(String encoding, InputStream initialStream)
			throws IOException {
		if (encoding == null)
			return initialStream;
		if ("gzip".equals(encoding))
			return new GZIPInputStream(initialStream);
		if ("deflate".equals(encoding))
			return new InflaterInputStream(initialStream);
		throw new IOException("Unsupported transport encoding " + encoding);
	}
}
