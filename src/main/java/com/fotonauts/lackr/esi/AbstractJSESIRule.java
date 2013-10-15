package com.fotonauts.lackr.esi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;

import com.fotonauts.lackr.LackrPresentableError;
import com.fotonauts.lackr.MimeType;
import com.fotonauts.lackr.backend.LackrBackendExchange;
import com.fotonauts.lackr.backend.LackrBackendRequest;
import com.fotonauts.lackr.esi.filters.JsonQuotingChunk;
import com.fotonauts.lackr.interpolr.Chunk;

public class AbstractJSESIRule extends ESIIncludeRule {

	public AbstractJSESIRule(String pattern) {
		super(pattern);
	}

	@Override
	public Chunk filterDocumentAsChunk(LackrBackendRequest exchange) {
		String mimeType = getMimeType(exchange.getExchange());
		if (MimeType.isJS(mimeType))
			return exchange.getParsedDocument();
		else if (MimeType.isML(mimeType)) {
			if (exchange.getParsedDocument() == null || exchange.getParsedDocument().length() == 0)
				return NULL_CHUNK;
			else
				return new JsonQuotingChunk(exchange.getParsedDocument(), true);
		} else if (MimeType.isTextPlain(mimeType)) {
			return new JsonQuotingChunk(exchange.getParsedDocument(), true);
		}
		return NULL_CHUNK;
	}

	@Override
	public String getSyntaxIdentifier() {
		return "JS";
	}

	@Override
	public void check(LackrBackendRequest request) {
		LackrBackendExchange exchange = request.getExchange();
		// FIXME this is here to find a bug. it is probably unecessary as it
		// will be parsed later
		if(!MimeType.isJS(exchange.getResponse().getResponseHeaderValue("Content-Type")))
			return;
		ObjectMapper mapper = exchange.getBackendRequest().getFrontendRequest().getService().getJacksonObjectMapper();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			baos.write("{ \"placeholder\" : ".getBytes());
			request.getParsedDocument().writeTo(baos);
			baos.write("}".getBytes());
			mapper.readValue(baos.toByteArray(), Map.class);
		} catch (JsonParseException e) {
			StringBuilder builder = new StringBuilder();
			builder.append("JsonParseException: a fragment supposed to be a json value does not parse:\n");
			builder.append("url: " + exchange.getBackendRequest().getQuery() + "\n");
			builder.append(e.getMessage() + "\n");
			builder.append("\n");
			try {
				builder.append(baos.toString("UTF-8"));
			} catch (UnsupportedEncodingException e2) {
				// no way
			} 
			builder.append("\n\n");
			exchange.getBackendRequest().getFrontendRequest().addBackendExceptions(new LackrPresentableError(builder.toString()));
		} catch (IOException e) {
			exchange.getBackendRequest().getFrontendRequest().addBackendExceptions(LackrPresentableError.fromThrowable(e));
		}
	}

}
