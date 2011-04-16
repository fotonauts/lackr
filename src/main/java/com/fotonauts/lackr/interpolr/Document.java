package com.fotonauts.lackr.interpolr;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import com.fotonauts.lackr.interpolr.Rule.InterpolrException;

public class Document implements Chunk {

	List<Chunk> chunks;
	
	public Document() {
		
	}
	
	public Document(DataChunk dataChunk) {
		getChunks().add(dataChunk);
    }

	public String toDebugString() {
		StringBuilder builder = new StringBuilder();
		for(Chunk chunk: chunks) {
			builder.append(chunk.toDebugString());
		}
		return builder.toString();
    }

	public int length() {
		int l = 0;
		for (Chunk chunk : chunks)
			l += chunk.length();
		return l;
	}

	public  void writeTo(OutputStream stream) throws IOException {
		for (Chunk chunk : chunks) {
			chunk.writeTo(stream);
		}
	}
	
	public void check(List<InterpolrException> exceptions) {
		for (Chunk chunk : chunks) {
			chunk.check(exceptions);
		}		
	}

	public void addAll(List<Chunk> added) {
		getChunks().addAll(added);
    }

	public void add(Chunk added) {
		getChunks().add(added);
    }

	public List<Chunk> getChunks() {
		if(chunks == null)
			chunks = new LinkedList<Chunk>();
		return chunks;
    }
}