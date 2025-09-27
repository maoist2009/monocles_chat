package eu.siacs.conversations.entities;

import android.util.Log;
import androidx.annotation.NonNull;

import de.monocles.chat.EmojiSearch;
import de.monocles.chat.GetThumbnailForCid;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.ipfs.cid.Cid;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.Emoticons;
import eu.siacs.conversations.xmpp.Jid;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class Reaction {

    public static final List<String> SUGGESTIONS =
            Arrays.asList(
                    "\u2764\uFE0F",
                    "\uD83D\uDC4D",
                    "\uD83D\uDC4E",
                    "\uD83D\uDE02",
                    "\uD83D\uDE2E",
                    "\uD83D\uDE22");

    private static final Gson GSON;

    static {
        GSON = new GsonBuilder().registerTypeAdapter(Jid.class, new JidTypeAdapter()).registerTypeAdapter(Cid.class, new CidTypeAdapter()).create();
    }

    public final String reaction;
    public final boolean received;
    public final Jid from;
    public final Jid trueJid;
    public final String occupantId;
    public final Cid cid;
    public final String envelopeId;

    public Reaction(
            final String reaction,
            final Cid cid,
            boolean received,
            final Jid from,
            final Jid trueJid,
            final String occupantId,
            final String envelopeId) {
        this.reaction = reaction;
        this.cid = cid;
        this.received = received;
        this.from = from;
        this.trueJid = trueJid;
        this.occupantId = occupantId;
        this.envelopeId = envelopeId;
    }

    public String normalizedReaction() {
        return Emoticons.normalizeToVS16(this.reaction);
    }

    public static String toString(final Collection<Reaction> reactions) {
        return (reactions == null || reactions.isEmpty()) ? null : GSON.toJson(reactions);
    }

    public static Collection<Reaction> fromString(final String asString) {
        if (Strings.isNullOrEmpty(asString)) {
            return Collections.emptyList();
        }
        try {
            return GSON.fromJson(asString, new TypeToken<List<Reaction>>() {}.getType());
        } catch (final IllegalArgumentException | JsonSyntaxException e) {
            Log.e(Config.LOGTAG, "could not restore reactions", e);
            return Collections.emptyList();
        }
    }

    public static Collection<Reaction> withMine(
            final Collection<Reaction> existing,
            final Collection<String> reactions,
            final boolean received,
            final Jid from,
            final Jid trueJid,
            final String occupantId,
            final String envelopeId) {
        final ImmutableSet.Builder<Reaction> builder = new ImmutableSet.Builder<>();
        builder.addAll(Collections2.filter(existing, e -> e.received));
        builder.addAll(
                Collections2.transform(
                        reactions, r -> new Reaction(r, null, received, from, trueJid, occupantId, envelopeId)));
        return builder.build();
    }

    public static Collection<Reaction> withOccupantId(
            final Collection<Reaction> existing,
            final Collection<String> reactions,
            final boolean received,
            final Jid from,
            final Jid trueJid,
            final String occupantId,
            final String envelopeId) {
        final ImmutableSet.Builder<Reaction> builder = new ImmutableSet.Builder<>();
        builder.addAll(Collections2.filter(existing, e -> !occupantId.equals(e.occupantId)));
        builder.addAll(
                Collections2.transform(
                        reactions, r -> new Reaction(r, null, received, from, trueJid, occupantId, envelopeId)));
        return builder.build();
    }

    @NonNull
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("reaction", cid == null ? reaction : null)
                .add("cid", cid)
                .add("received", received)
                .add("from", from)
                .add("trueJid", trueJid)
                .add("occupantId", occupantId)
                .toString();
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof Reaction)) return false;
        return toString().equals(o.toString());
    }

    public static Collection<Reaction> withFrom(
            final Collection<Reaction> existing,
            final Collection<String> reactions,
            final boolean received,
            final Jid from,
            final String envelopeId) {
        final ImmutableSet.Builder<Reaction> builder = new ImmutableSet.Builder<>();
        builder.addAll(
                Collections2.filter(existing, e -> !from.asBareJid().equals(e.from.asBareJid())));
        builder.addAll(
                Collections2.transform(
                        reactions, r -> new Reaction(r, null, received, from, null, null, envelopeId)));
        return builder.build();
    }

    private static class JidTypeAdapter extends TypeAdapter<Jid> {
        @Override
        public void write(final JsonWriter out, final Jid value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Jid read(final JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            } else if (in.peek() == JsonToken.STRING) {
                final String value = in.nextString();
                return Jid.of(value);
            }
            throw new IOException("Unexpected token");
        }
    }

    private static class CidTypeAdapter extends TypeAdapter<Cid> {
        @Override
        public void write(final JsonWriter out, final Cid value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Cid read(final JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            } else if (in.peek() == JsonToken.STRING) {
                final String value = in.nextString();
                return Cid.decode(value);
            }
            throw new IOException("Unexpected token");
        }
    }

    public static Aggregated aggregated(final Collection<Reaction> reactions) {
        return aggregated(reactions, (r) -> null);
    }

    public static Aggregated aggregated(final Collection<Reaction> reactions, Function<Reaction, GetThumbnailForCid> thumbnailer) {
        final Map<EmojiSearch.Emoji, Collection<Reaction>> aggregatedReactions =
                Multimaps.index(reactions, r -> r.cid == null ? new EmojiSearch.Emoji(r.reaction, 0) : new EmojiSearch.CustomEmoji(r.reaction, r.cid.toString(), thumbnailer.apply(r).getThumbnail(r.cid), null)).asMap();
        final List<Map.Entry<EmojiSearch.Emoji, Collection<Reaction>>> sortedList =
                Ordering.from(
                                Comparator.comparingInt(
                                        (Map.Entry<EmojiSearch.Emoji, Collection<Reaction>> o) -> o.getValue().size()))
                        .reverse()
                        .immutableSortedCopy(aggregatedReactions.entrySet());
        return new Aggregated(
                sortedList,
                ImmutableSet.copyOf(
                        Collections2.transform(
                                Collections2.filter(reactions, r -> r.cid == null && !r.received),
                                Reaction::normalizedReaction)));
    }

    public static final class Aggregated {

        public final List<Map.Entry<EmojiSearch.Emoji, Collection<Reaction>>> reactions;
        public final Set<String> ourReactions;

        private Aggregated(
                final List<Map.Entry<EmojiSearch.Emoji, Collection<Reaction>>> reactions, Set<String> ourReactions) {
            this.reactions = reactions;
            this.ourReactions = ourReactions;
        }
    }
}
