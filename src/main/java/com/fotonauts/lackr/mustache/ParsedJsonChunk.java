package com.fotonauts.lackr.mustache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import com.fotonauts.lackr.BackendRequest;
import com.fotonauts.lackr.LackrPresentableError;
import com.fotonauts.lackr.interpolr.Chunk;
import com.fotonauts.lackr.interpolr.Document;

public abstract class ParsedJsonChunk implements Chunk {

    protected Document inner;
    protected BackendRequest request;

    @SuppressWarnings("unchecked")
    public static void inlineWrapperJsonEvaluation(Object data) {
        if (data instanceof List<?>) {
            List<Serializable> dataAsList = (List<Serializable>) data;
            for (Serializable s : dataAsList)
                inlineWrapperJsonEvaluation(s);

        } else if (data instanceof Map<?, ?>) {
            Map<String, Serializable> dataAsMap = (Map<String, Serializable>) data;
            List<String> keysToRemove = new LinkedList<>();
            Map<String, Serializable> stuffToInline = new HashMap<>();
            for (Entry<String, Serializable> pair : dataAsMap.entrySet()) {
                if (pair.getValue() instanceof Map<?, ?>) {
                    Map<String, Serializable> valueAsMap = (Map<String, Serializable>) pair.getValue();
                    if (valueAsMap.size() == 1 && valueAsMap.containsKey("$$inline_wrapper")) {
                        if (valueAsMap.get("$$inline_wrapper") instanceof Map<?, ?>) {
                            stuffToInline
                                    .putAll((Map<? extends String, ? extends Serializable>) valueAsMap.get("$$inline_wrapper"));
                            keysToRemove.add(pair.getKey());
                        }
                    }
                }
            }
            for (String k : keysToRemove)
                dataAsMap.remove(k);
            dataAsMap.putAll(stuffToInline);
            for (Serializable value : dataAsMap.values())
                inlineWrapperJsonEvaluation(value);
        }
    }

    public ParsedJsonChunk(byte[] buffer, int start, int stop, BackendRequest request) {
        super();
        this.request = request;
        inner = request.getFrontendRequest().getService().getInterpolr().parse(buffer, start, stop, request);
    }

    protected String contentAsDebugString(int lineNumber, int columnNumber) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            inner.writeTo(baos);
        } catch (IOException e) {
            /* ignore this, we're already debugging anyway */
        }
        StringBuilder builder = new StringBuilder();
        String lines[] = baos.toString().split("\n");
        for (int i = 0; i < lines.length; i++) {
            builder.append(String.format("% 3d %s\n", i + 1, lines[i]));
            if (i + 1 == lineNumber) {
                builder.append("    ");
                for (int j = 0; j < columnNumber - 2; j++)
                    builder.append("-");
                builder.append("^\n");
            }
        }
        return builder.toString();
    }

    protected String prettyPrint(Map<String, Object> data) {
        ObjectMapper mapper = request.getFrontendRequest().getService().getJacksonObjectMapper();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            mapper.defaultPrettyPrintingWriter().writeValue(baos, data);
        } catch (JsonGenerationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JsonMappingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return baos.toString();
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> parse() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectMapper mapper = request.getFrontendRequest().getService().getJacksonObjectMapper();
        try {
            Map<String, Object> data = null;
            inner.writeTo(baos);
            data = mapper.readValue(baos.toByteArray(), Map.class);
            return data;
        } catch (JsonParseException e) {
            StringBuilder builder = new StringBuilder();
            builder.append("JsonParseException\n");
            builder.append("url: " + request.getQuery() + "\n");
            builder.append(e.getMessage() + "\n");
            builder.append("where: " + toDebugString() + "\n");
            builder.append(contentAsDebugString(e.getLocation().getLineNr(), e.getLocation().getColumnNr()));
            builder.append("\n");
            request.getFrontendRequest().addBackendExceptions(new LackrPresentableError(builder.toString()));
        } catch (IOException e) {
            StringBuilder builder = new StringBuilder();
            builder.append("IOException\n");
            builder.append("url: " + request.getQuery() + "\n");
            builder.append(e.getMessage() + "\n");
            builder.append("where: " + toDebugString() + "\n");
            request.getFrontendRequest().addBackendExceptions(new LackrPresentableError(builder.toString()));
        }
        return new HashMap<String,Object>();
    }
}