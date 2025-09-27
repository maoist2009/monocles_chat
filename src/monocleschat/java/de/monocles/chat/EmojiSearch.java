package de.monocles.chat;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;

import io.ipfs.cid.Cid;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Comparable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.BoundExtractedResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.EmojiSearchRowBinding;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.utils.ReplacingSerialSingleThreadExecutor;

public class EmojiSearch {
	protected final Set<Emoji> emoji = new TreeSet<>();

	public EmojiSearch(Context context) {
		try {
			final JSONArray data = new JSONArray(CharStreams.toString(new InputStreamReader(context.getResources().openRawResource(R.raw.emoji), "UTF-8")));
			for (int i = 0; i < data.length(); i++) {
				emoji.add(new Emoji(data.getJSONObject(i)));
			}
		} catch (final JSONException | IOException e) {
			throw new IllegalStateException("emoji.json invalid: " + e);
		}
	}

	public synchronized void addEmoji(final Emoji one) {
		emoji.add(one);
	}

	public synchronized List<Emoji> find(final String q) {
		final ResultPQ pq = new ResultPQ();
		for (Emoji e : emoji) {
			if (e.emoticonMatch(q)) {
				pq.addTopK(e, 999999, 10);
			}
			int shortcodeScore = e.shortcodes.isEmpty() ? 0 : Collections.max(Lists.transform(e.shortcodes, (shortcode) -> FuzzySearch.ratio(q, shortcode)));
			int tagScore = e.tags.isEmpty() ? 0 : Collections.max(Lists.transform(e.tags, (tag) -> FuzzySearch.ratio(q, tag))) - 2;
			pq.addTopK(e, Math.max(shortcodeScore, tagScore), 10);
		}

		for (BoundExtractedResult<Emoji> r : new ArrayList<>(pq)) {
			for (Emoji e : emoji) {
				if (e.shortcodeMatch(r.getReferent().uniquePart())) {
					// hack see https://stackoverflow.com/questions/76880072/imagespan-with-emojicompat
					e.shortcodes.clear();
					e.shortcodes.addAll(r.getReferent().shortcodes);

					pq.addTopK(e, r.getScore() - 1, 10);
				}
			}
		}

		List<Emoji> result = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			BoundExtractedResult<Emoji> e = pq.poll();
			if (e != null) result.add(e.getReferent());
		}
		Collections.reverse(result);
		return result;
	}

	public EmojiSearchAdapter makeAdapter(Consumer<Emoji> callback) {
		return new EmojiSearchAdapter(callback);
	}

	public static class ResultPQ extends PriorityQueue<BoundExtractedResult<Emoji>> {
		public void addTopK(Emoji e, int score, int k) {
			BoundExtractedResult r = new BoundExtractedResult(e, null, score, 0);
			if (size() < k) {
				add(r);
			} else if (r.compareTo(peek()) > 0) {
				poll();
				add(r);
			}
		}
	}

	public static class Emoji implements Comparable<Emoji> {
		protected final String unicode;
		protected final int order;
		protected final List<String> tags = new ArrayList<>();
		protected final List<String> emoticon = new ArrayList<>();
		protected final List<String> shortcodes = new ArrayList<>();

		public Emoji(final String unicode, final int order) {
			this.unicode = unicode;
			this.order = order;
		}

		public Emoji(JSONObject o) throws JSONException {
			unicode = o.getString("unicode");
			order = o.getInt("order");
			final JSONArray rawTags = o.getJSONArray("tags");
			for (int i = 0; i < rawTags.length(); i++) {
				tags.add(rawTags.getString(i));
			}
			final JSONArray rawEmoticon = o.getJSONArray("emoticon");
			for (int i = 0; i < rawEmoticon.length(); i++) {
				emoticon.add(rawEmoticon.getString(i));
			}
			final JSONArray rawShortcodes = o.getJSONArray("shortcodes");
			for (int i = 0; i < rawShortcodes.length(); i++) {
				shortcodes.add(rawShortcodes.getString(i));
			}
		}

		public boolean emoticonMatch(final String q) {
			for (final String emote : emoticon) {
				if (emote.equals(q) || emote.equals(":" + q)) return true;
			}

			return false;
		}

		public boolean shortcodeMatch(final String q) {
			for (final String shortcode : shortcodes) {
				if (shortcode.equals(q)) return true;
			}

			return false;
		}

		public SpannableStringBuilder toInsert() {
			return new SpannableStringBuilder(unicode);
		}

		public void setupChip(Chip chip, int count) {
			if (count < 2) {
				chip.setText(unicode);
			} else {
				chip.setText(String.format(Locale.ENGLISH, "%s %d", unicode, count));
			}
		}

		@Override
		public String toString() {
			return unicode;
		}

		public String uniquePart() {
			return unicode;
		}

		@Override
		public int compareTo(Emoji o) {
			if (equals(o)) return 0;
			if (order == o.order) return uniquePart().compareTo(o.uniquePart());
			return order - o.order;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Emoji)) return false;

			return uniquePart().equals(((Emoji) o).uniquePart());
		}

		@Override
		public int hashCode() {
			return uniquePart().hashCode();
		}
	}

	public static class CustomEmoji extends Emoji {
		public final String source;
		protected final Drawable icon;

		public CustomEmoji(final String shortcode, final String source, final Drawable icon, final String tag) {
			super(null, 10);
			shortcodes.add(shortcode);
			if (tag != null) tags.add(tag);
			this.source = source;
			this.icon = icon;
		}

		public SpannableStringBuilder toInsert() {
			SpannableStringBuilder builder = new SpannableStringBuilder(toString());
			builder.setSpan(new InlineImageSpan(icon == null ? new android.graphics.drawable.ColorDrawable(0) : icon, source), 0, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			return builder;
		}

		public void setupChip(Chip chip, int count) {
			if (icon == null) {
				chip.setChipIconResource(R.drawable.ic_photo_24dp);
				chip.setChipIconTint(
						MaterialColors.getColorStateListOrNull(
								chip.getContext(),
								com.google.android.material.R.attr.colorOnSurface));
			} else {
				SpannableStringBuilder builder = new SpannableStringBuilder("😇"); // needs to be same size as an emoji
				if (icon != null) builder.setSpan(new InlineImageSpan(icon, source), 0, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				chip.setText(builder); // We cannot use icon because it is a hardware bitmap
			}
			if (count > 1) {
				chip.append(String.format(Locale.ENGLISH, " %d", count));
			}
		}

		@Override
		public String uniquePart() {
			return source;
		}

		@Override
		public String toString() {
			return ":" + shortcodes.get(0) + ":";
		}
	}

	public class EmojiSearchAdapter extends ListAdapter<Emoji, EmojiSearchAdapter.ViewHolder> {
		ReplacingSerialSingleThreadExecutor executor = new ReplacingSerialSingleThreadExecutor("EmojiSearchAdapter");

		static final DiffUtil.ItemCallback<Emoji> DIFF = new DiffUtil.ItemCallback<Emoji>() {
			@Override
			public boolean areItemsTheSame(Emoji a, Emoji b) {
				return a.equals(b);
			}

			@Override
			public boolean areContentsTheSame(Emoji a, Emoji b) {
				return a.equals(b);
			}
		};
		final Consumer<Emoji> callback;
		protected Semaphore doingUpdate = new Semaphore(1);

		public EmojiSearchAdapter(final Consumer<Emoji> callback) {
			super(DIFF);
			this.callback = callback;
		}

		@Override
		public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int position) {
			return new ViewHolder(DataBindingUtil.inflate(LayoutInflater.from(viewGroup.getContext()), R.layout.emoji_search_row, viewGroup, false));
		}

		@Override
		public void onBindViewHolder(ViewHolder viewHolder, int position) {
			final var binding = viewHolder.binding;
			final var item = getItem(position);
			if (item instanceof CustomEmoji) {
				binding.nonunicode.setText(item.toInsert());
				binding.nonunicode.setVisibility(View.VISIBLE);
				binding.unicode.setVisibility(View.GONE);
			} else {
				binding.unicode.setText(item.toInsert());
				binding.unicode.setVisibility(View.VISIBLE);
				binding.nonunicode.setVisibility(View.GONE);
			}
			binding.shortcode.setText(item.shortcodes.get(0));
			binding.getRoot().setOnClickListener(v -> {
				callback.accept(item);
			});
		}

		public void search(final Activity activity, final RecyclerView view, final String q) {
			executor.execute(() -> {
				final List<Emoji> results = find(q);
				try {
					// Acquire outside so to not block UI thread
					doingUpdate.acquire();
					activity.runOnUiThread(() -> {
						submitList(results, () -> {
							activity.runOnUiThread(() -> doingUpdate.release());
						});
					});
				} catch (final InterruptedException e) { }
			});
		}

		public static class ViewHolder extends RecyclerView.ViewHolder {
			public final EmojiSearchRowBinding binding;

			private ViewHolder(EmojiSearchRowBinding binding) {
				super(binding.getRoot());
				this.binding = binding;
			}
		}
	}
}
