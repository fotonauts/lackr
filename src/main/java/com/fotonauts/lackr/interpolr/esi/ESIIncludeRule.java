package com.fotonauts.lackr.interpolr.esi;

import com.fotonauts.lackr.interpolr.InterpolrScope;
import com.fotonauts.lackr.interpolr.plugins.MarkupDetectingRule;
import com.fotonauts.lackr.interpolr.plugins.Rule;
import com.fotonauts.lackr.interpolr.rope.Chunk;
import com.fotonauts.lackr.interpolr.rope.ConstantChunk;

abstract public class ESIIncludeRule extends MarkupDetectingRule implements Rule {

    protected static ConstantChunk NULL_CHUNK = new ConstantChunk("null".getBytes());

    public ESIIncludeRule(String markup) {
        super(markup);
    }

    protected String makeUrl(byte[] buffer, int start, int stop) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < stop; i++) {
            byte b = buffer[i];
            if (b < 0) {
                builder.append('%');
                builder.append(Integer.toHexString(b + 256).toUpperCase());
            } else if (b < 32) {
                builder.append('%');
                builder.append(Integer.toHexString(b).toUpperCase());
            } else {
                builder.append((char) b);
            }
        }
        return builder.toString();
    }

    @Override
    public Chunk substitute(byte[] buffer, int start, int[] boundPairs, int stop, InterpolrScope scope) {
        String url = makeUrl(buffer, boundPairs[0], boundPairs[1]);
        InterpolrScope sub;
        sub = scope.getInterpolrContext().getOrCreateSubScope(url, getSyntaxIdentifier(), scope);
        return new RequestChunk(sub, this);
    }

    public abstract String getSyntaxIdentifier();

    public abstract Chunk filterDocumentAsChunk(InterpolrScope scope);

    public abstract void check(InterpolrScope scope);
}
