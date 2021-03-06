package com.fotonauts.lackr.interpolr;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.util.component.AbstractLifeCycle;

import com.fotonauts.lackr.MimeType;
import com.fotonauts.lackr.interpolr.plugins.AdvancedPlugin;
import com.fotonauts.lackr.interpolr.plugins.Plugin;
import com.fotonauts.lackr.interpolr.plugins.Rule;
import com.fotonauts.lackr.interpolr.rope.Chunk;
import com.fotonauts.lackr.interpolr.rope.DataChunk;
import com.fotonauts.lackr.interpolr.rope.Document;

public class Interpolr extends AbstractLifeCycle {

    private List<Plugin> plugins = Collections.emptyList();

    @Override
    protected void doStart() throws Exception {
        for (Plugin p : plugins) {
            if (p instanceof AdvancedPlugin) {
                ((AdvancedPlugin) p).setInterpolr(this);
                ((AdvancedPlugin) p).start();
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        for (Plugin p : plugins) {
            if (p instanceof AdvancedPlugin) {
                ((AdvancedPlugin) p).stop();
            }
        }
    }

    public boolean shouldProcess(InterpolrScope scope) {
        String mimeType = scope.getResultMimeType();
        return (MimeType.isML(mimeType) || MimeType.isJS(mimeType));
    }

    public void processResult(InterpolrScope scope) {
        if (shouldProcess(scope))
            scope.setParsedDocument(parse(scope.getBodyBytes(), scope));
        else
            scope.setParsedDocument(new Document(new DataChunk(scope.getBodyBytes())));
    }

    public Document parse(byte[] buffer, int start, int stop, InterpolrScope scope) {
        Document chunks = new Document();
        chunks.add(new DataChunk(buffer, start, stop));
        for (Plugin plugin : getPlugins())
            for (Rule rule : plugin.getRules()) {
                chunks = parse(rule, chunks, scope);
            }
        return chunks;
    }

    public List<Plugin> getPlugins() {
        return plugins;
    }

    public Document parse(byte[] data, InterpolrScope scope) {
        return parse(data, 0, data.length, scope);
    }

    public Document parse(Rule rule, Document input, InterpolrScope scope) {
        Document result = new Document();
        for (Chunk chunk : input.getChunks()) {
            if (chunk instanceof DataChunk) {
                List<Chunk> replacements = rule.parse((DataChunk) chunk, scope);
                result.addAll(replacements);
            } else
                result.add(chunk);
        }
        return result;
    }

    public void preflightCheck(InterpolrContext context) {
        for (Plugin plugin : getPlugins())
            plugin.preflightCheck(context);
        if (context.getRootScope().getParsedDocument() != null) {
            context.getRootScope().getParsedDocument().check();
        }
    }

    public void setPlugins(Plugin[] plugins) {
        this.plugins = Arrays.asList(plugins);
    }
}