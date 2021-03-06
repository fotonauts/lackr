package com.fotonauts.lackr.interpolr.handlebars;

import java.io.UnsupportedEncodingException;

import com.fotonauts.lackr.interpolr.InterpolrScope;
import com.fotonauts.lackr.interpolr.plugins.MarkupDetectingRule;
import com.fotonauts.lackr.interpolr.plugins.Plugin;
import com.fotonauts.lackr.interpolr.rope.Chunk;
import com.fotonauts.lackr.interpolr.rope.ConstantChunk;
import com.fotonauts.lackr.interpolr.rope.Document;

public class TemplateRule extends MarkupDetectingRule {

    protected static ConstantChunk EMPTY_CHUNK = new ConstantChunk("".getBytes());

    private Plugin plugin;

    public TemplateRule(HandlebarsPlugin plugin) {
        super("<!-- " + plugin.getPrefix() + ":template name=\"*\" -->*<!-- /" + plugin.getPrefix() + ":template -->");
        this.plugin = plugin;
    }

    @Override
    public Chunk substitute(byte[] buffer, int start, int[] boundPairs, int stop, InterpolrScope scope) {
        String name = null;
        Document template = null;
        try {
            name = new String(buffer, boundPairs[0], boundPairs[1] - boundPairs[0], "UTF-8");
            template = scope.getInterpolr().parse(buffer, boundPairs[2], boundPairs[3], scope);
            ((HandlebarsContext) scope.getInterpolrContext().getPluginData(plugin)).registerTemplate(name, template);
            // Someday, we will not want lackr to delete parsed templates on the fly
            // return new DataChunk(buffer, boundPairs[2], boundPairs[3]);
            return EMPTY_CHUNK;
        } catch (UnsupportedEncodingException e) {
            // no way :)
            return EMPTY_CHUNK;
        }
    }

}
