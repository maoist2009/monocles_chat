package eu.siacs.conversations.xml;

import androidx.annotation.NonNull;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import eu.siacs.conversations.utils.XmlHelper;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Collectors;

import eu.siacs.conversations.utils.XmlHelper;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.stanza.Message;

public class Element implements Node {
	private final String name;
	private Hashtable<String, String> attributes = new Hashtable<>();
	protected List<Element> children = new ArrayList<>();
	private List<Node> childNodes = new ArrayList<>();

	public Element(String name) {
		this.name = name;
	}

	public Element(String name, String xmlns) {
		this.name = name;
		this.setAttribute("xmlns", xmlns);
	}

	public Node prependChild(Node child) {
		childNodes.add(0, child);
		if (child instanceof Element) children.add(0, (Element) child);
		return child;
	}

	public Node addChild(Node child) {
		childNodes.add(child);
		if (child instanceof Element) children.add((Element) child);
		return child;
	}

	public Element addChild(String name) {
		Element child = new Element(name);
		childNodes.add(child);
		children.add(child);
		return child;
	}

	public Element addChild(String name, String xmlns) {
		Element child = new Element(name);
		child.setAttribute("xmlns", xmlns);
		childNodes.add(child);
		children.add(child);
		return child;
	}

	public void addChildren(final Collection<? extends Node> children) {
		if (children == null) return;

		this.childNodes.addAll(children);
		for (Node node : children) {
			if (node instanceof Element) {
				this.children.add((Element) node);
			}
		}
	}

	public void removeChild(Node child) {
		if (child == null) return;

		this.childNodes.remove(child);
		if (child instanceof Element) this.children.remove(child);
	}

	public Element setContent(String content) {
		clearChildren();
		if (content != null) this.childNodes.add(new TextNode(content));
		return this;
	}

	public Element findChild(String name) {
		for (Element child : this.children) {
			if (child.getName().equals(name)) {
				return child;
			}
		}
		return null;
	}

	public String findChildContent(String name) {
		Element element = findChild(name);
		return element == null ? null : element.getContent();
	}

	public LocalizedContent findInternationalizedChildContentInDefaultNamespace(String name) {
		return LocalizedContent.get(this, name);
	}

	public Element findChild(String name, String xmlns) {
		for (Element child : getChildren()) {
			if (name.equals(child.getName()) && xmlns.equals(child.getAttribute("xmlns"))) {
				return child;
			}
		}
		return null;
	}

	public Element findChildEnsureSingle(String name, String xmlns) {
		final List<Element> results = new ArrayList<>();
		for (Element child : getChildren()) {
			if (name.equals(child.getName()) && xmlns.equals(child.getAttribute("xmlns"))) {
				results.add(child);
			}
		}
		if (results.size() == 1) {
			return results.get(0);
		}
		return null;
	}

	public String findChildContent(String name, String xmlns) {
		Element element = findChild(name,xmlns);
		return element == null ? null : element.getContent();
	}

	public boolean hasChild(final String name) {
		return findChild(name) != null;
	}

	public boolean hasChild(final String name, final String xmlns) {
		return findChild(name, xmlns) != null;
	}

	public final List<Element> getChildren() {
		return ImmutableList.copyOf(this.children);
	}

	public void setAttribute(final String name, final boolean value) {
		this.setAttribute(name, value ? "1" : "0");
	}

	// Deprecated: you probably want bindTo or replaceChildren
	public Element setChildren(List<Element> children) {
		this.childNodes = new ArrayList(children);
		this.children = new ArrayList(children);
		return this;
	}

	public void replaceChildren(List<Element> children) {
		this.childNodes.clear();
		this.childNodes.addAll(children);
		this.children.clear();
		this.children.addAll(children);
	}

	public void bindTo(Element original) {
		this.attributes = original.attributes;
		this.childNodes = original.childNodes;
		this.children = original.children;
	}

	public final String getContent() {
		return this.childNodes.stream().map(Node::getContent).filter(c -> c != null).collect(Collectors.joining());
	}

	public long getLongAttribute(final String name) {
		final var value = Longs.tryParse(Strings.nullToEmpty(this.attributes.get(name)));
		return value == null ? 0 : value;
	}

	public Optional<Integer> getOptionalIntAttribute(final String name) {
		final String value = getAttribute(name);
		if (value == null) {
			return Optional.absent();
		}
		return Optional.fromNullable(Ints.tryParse(value));
	}

	public Jid getAttributeAsJid(String name) {
		final String jid = this.getAttribute(name);
		if (jid != null && !jid.isEmpty()) {
			try {
				return Jid.of(jid);
			} catch (final IllegalArgumentException e) {
				return Jid.ofOrInvalid(jid, this instanceof Message);
			}
		}
		return null;
	}

	public Element setAttribute(String name, String value) {
		if (name != null && value != null) {
			this.attributes.put(name, value);
		}
		return this;
	}

	public Element setAttribute(String name, Jid value) {
		if (name != null && value != null) {
			this.attributes.put(name, value.toString());
		}
		return this;
	}

	public String toString() {
		return toString(ImmutableMap.of());
	}

	public void appendToBuilder(final Map<String, String> parentNS, final StringBuilder elementOutput, final int skipEnd) {
		final var mutns = new CopyOnWriteMap<>(parentNS);
		if (childNodes.size() == 0) {
			final var attr = getSerializableAttributes(mutns);
			Tag emptyTag = Tag.empty(name);
			emptyTag.setAttributes(attr);
			emptyTag.appendToBuilder(elementOutput);
		} else {
			final var startTag = startTag(mutns);
			startTag.appendToBuilder(elementOutput);
			for (Node child : ImmutableList.copyOf(childNodes)) {
				child.appendToBuilder(mutns.toMap(), elementOutput, Math.max(0, skipEnd - 1));
			}
			if (skipEnd < 1) endTag().appendToBuilder(elementOutput);
		}
	}

	public String toString(final ImmutableMap<String, String> parentNS) {
		final StringBuilder elementOutput = new StringBuilder();
		appendToBuilder(parentNS, elementOutput, 0);
		return elementOutput.toString();
	}

	public Tag startTag() {
		return startTag(new CopyOnWriteMap<>(new Hashtable<>()));
	}

	public Tag startTag(final CopyOnWriteMap<String, String> mutns) {
		final var attr = getSerializableAttributes(mutns);
		final var startTag = Tag.start(name);
		startTag.setAttributes(attr);
		return startTag;
	}

	public Tag endTag() {
		return Tag.end(name);
	}

	protected Hashtable<String, String> getSerializableAttributes(CopyOnWriteMap<String, String> ns) {
		final var result = new Hashtable<String, String>(attributes.size());
		for (final var attr : attributes.entrySet()) {
			if (attr.getKey().charAt(0) == '{') {
				final var uriIdx = attr.getKey().indexOf('}');
				final var uri = attr.getKey().substring(1, uriIdx - 1);
				if (!ns.containsKey(uri)) {
					result.put("xmlns:ns" + ns.size(), uri);
					ns.put(uri, "ns" + ns.size());
				}
				result.put(ns.get(uri) + ":" + attr.getKey().substring(uriIdx + 1), attr.getValue());
			} else {
				result.put(attr.getKey(), attr.getValue());
			}
		}

		return result;
	}

	public Element removeAttribute(final String name) {
		this.attributes.remove(name);
		return this;
	}

	public Element setAttributes(Hashtable<String, String> attributes) {
		this.attributes = attributes;
		return this;
	}

	public String getAttribute(String name) {
		if (this.attributes.containsKey(name)) {
			return this.attributes.get(name);
		} else {
			return null;
		}
	}

	public Hashtable<String, String> getAttributes() {
		return this.attributes;
	}

	public final String getName() {
		return name;
	}

	public void clearChildren() {
		this.children.clear();
		this.childNodes.clear();
	}

	public void setAttribute(String name, long value) {
		this.setAttribute(name, Long.toString(value));
	}

	public void setAttribute(String name, int value) {
		this.setAttribute(name, Integer.toString(value));
	}

	public boolean getAttributeAsBoolean(String name) {
		String attr = getAttribute(name);
		return (attr != null && (attr.equalsIgnoreCase("true") || attr.equalsIgnoreCase("1")));
	}

	public String getNamespace() {
		return getAttribute("xmlns");
	}

	static class CopyOnWriteMap<K,V> {
		protected final Map<K,V> original;
		protected Hashtable<K,V> mut = null;

		public CopyOnWriteMap(Map<K,V> original) {
			this.original = original;
		}

		public int size() {
			return mut == null ? original.size() : mut.size();
		}

		public boolean containsKey(K k) {
			return mut == null ? original.containsKey(k) : mut.containsKey(k);
		}

		public V get(K k) {
			return mut == null ? original.get(k) : mut.get(k);
		}

		public void put(K k, V v) {
			if (mut == null) {
				mut = new Hashtable<>(original);
			}
			mut.put(k, v);
		}

		public Map<K, V> toMap() {
			return mut == null ? original : mut;
		}
	}
}
