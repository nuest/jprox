package com.github.matthesrieke.jprox;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

public class JProxViaParameterServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3762618215346245527L;
	private String parameterKey = "targetUrl";
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		if (config != null) {
			String parameterKey = config.getInitParameter("parameterKey");
			if (parameterKey != null) {
				this.parameterKey = parameterKey;
			}
		}
		
	}
	
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		executeRequest(req, resp);
	}
	
	private void executeRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String target = resolveTargetUrl(req);
		HttpClient client = new DefaultHttpClient();
		HttpResponse response = client.execute(prepareRequest(req, target));
		try {
			String contentType = response.getEntity().getContentType().getValue();
			
			resp.setContentLength((int) response.getEntity().getContentLength());
			resp.setStatus(response.getStatusLine().getStatusCode());
			resp.setContentType(contentType);
			
			copyStream(response.getEntity().getContent(), resp.getOutputStream());
		} finally {
			EntityUtils.consume(response.getEntity());
		}
	}

	private HttpUriRequest prepareRequest(HttpServletRequest req, String target) throws IOException {
		String method = req.getMethod();
		if (method.equals(HttpGet.METHOD_NAME)) {
			return new HttpGet(target);
		}
		else if (method.equals(HttpPost.METHOD_NAME)) {
			HttpPost post = new HttpPost(target);
			post.setEntity(new InputStreamEntity(req.getInputStream(), req.getContentLength()));
		}
		throw new UnsupportedOperationException("Only GET and POST are supported by this proxy.");
	}

	private String resolveTargetUrl(HttpServletRequest req) {
		String[] values = req.getParameterValues(this.parameterKey);
		if (values != null && values.length > 0) {
			try {
				return URLDecoder.decode(values[0], "UTF-8");
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}
		throw new RuntimeException("ParameterKey not specified!");
	}
	
	private void copyStream(InputStream content,
			ServletOutputStream outputStream) throws IOException {
		ReadableByteChannel ci = Channels.newChannel(content);
		WritableByteChannel co = Channels.newChannel(outputStream);

		copyChannel(ci, co);
	}

	private void copyChannel(final ReadableByteChannel src,
			final WritableByteChannel dest) throws IOException {
		final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
		
		while (src.read(buffer) != -1) {
			buffer.flip();
			dest.write(buffer);
			buffer.compact();
		}
		
		buffer.flip();
		
		while (buffer.hasRemaining()) {
			dest.write(buffer);
		}
	}

}
