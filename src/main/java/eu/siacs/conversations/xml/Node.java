package eu.siacs.conversations.xml;

import java.util.Map;
import com.google.common.collect.ImmutableMap;

public interface Node {
	public String getContent();
	public String toString(final ImmutableMap<String, String> ns);
	public void appendToBuilder(final Map<String, String> ns, final StringBuilder elementOutput, final int skipEnd);
}
