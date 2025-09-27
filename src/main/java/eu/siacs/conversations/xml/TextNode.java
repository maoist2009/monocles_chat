package eu.siacs.conversations.xml;

import java.util.Map;
import com.google.common.collect.ImmutableMap;

import eu.siacs.conversations.utils.XmlHelper;

public class TextNode implements Node {
	protected String content;

	public TextNode(final String content) {
		if (content == null) throw new IllegalArgumentException("null TextNode is not allowed");
		this.content = content;
	}

	public String getContent() {
		return content;
	}

	public void appendToBuilder(final Map<String, String> parentNS, final StringBuilder elementOutput, final int skipEnd) {
		XmlHelper.appendEncodedEntities(content, elementOutput);
	}

	public String toString() {
		return XmlHelper.encodeEntities(content);
	}

	public String toString(final ImmutableMap<String, String> ns) {
		return toString();
	}
}
