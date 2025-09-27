package eu.siacs.conversations.ui;

import android.view.View;
import android.view.ViewGroup;
import android.util.TypedValue;

import de.monocles.chat.EmojiSearch;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Reaction;
import eu.siacs.conversations.utils.UIHelper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.function.Function;

public class BindingAdapters {
    public static void setReactionsOnReceived(
            final ChipGroup chipGroup,
            final Reaction.Aggregated reactions,
            final Consumer<Collection<String>> onModifiedReactions,
            final Function<Map.Entry<EmojiSearch.Emoji, Collection<Reaction>>, Boolean> onDetailsClicked,
            final Consumer<EmojiSearch.CustomEmoji> onCustomReaction,
            final Consumer<Reaction> onCustomReactionRemove,
            final Runnable addReaction) {
        setReactions(chipGroup, reactions, true, onModifiedReactions, onDetailsClicked, onCustomReaction, onCustomReactionRemove, addReaction);
    }

    public static void setReactionsOnSent(
            final ChipGroup chipGroup,
            final Reaction.Aggregated reactions,
            final Consumer<Collection<String>> onModifiedReactions,
            final Function<Map.Entry<EmojiSearch.Emoji, Collection<Reaction>>, Boolean> onDetailsClicked) {
        setReactions(chipGroup, reactions, false, onModifiedReactions, onDetailsClicked, null, null, null);
    }

    private static void setReactions(
            final ChipGroup chipGroup,
            final Reaction.Aggregated aggregated,
            final boolean onReceived,
            final Consumer<Collection<String>> onModifiedReactions,
            final Function<Map.Entry<EmojiSearch.Emoji, Collection<Reaction>>, Boolean> onDetailsClicked,
            final Consumer<EmojiSearch.CustomEmoji> onCustomReaction,
            final Consumer<Reaction> onCustomReactionRemove,
            final Runnable addReaction) {
        final var context = chipGroup.getContext();
        final var size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 35, context.getResources().getDisplayMetrics());
        final var corner = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 35, context.getResources().getDisplayMetrics());
        final var layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, size);
        final List<Map.Entry<EmojiSearch.Emoji, Collection<Reaction>>> reactions = aggregated.reactions;
        final AppSettings appSettings = new AppSettings(context);
        if (reactions == null || reactions.isEmpty()) {
            chipGroup.setVisibility(View.GONE);
        } else {
            chipGroup.removeAllViews();
            chipGroup.setVisibility(View.VISIBLE);
            for (final var reaction : reactions) {
                final var emoji = reaction.getKey();
                final var count = reaction.getValue().size();
                final Chip chip = new Chip(chipGroup.getContext());
                //chip.setEnsureMinTouchTargetSize(false);
                if (appSettings.isLargeFont()) {
                    chip.setTextAppearance(
                            com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                } else {
                    chip.setTextAppearance(
                            com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
                }
                chip.setChipMinHeight(size-22.0f);
                chip.ensureAccessibleTouchTarget(size);
                chip.setLayoutParams(layoutParams);
                chip.setChipCornerRadius(corner);
                emoji.setupChip(chip, count);
                final var oneOfOurs = reaction.getValue().stream().filter(r -> !r.received).findFirst();
                // received = surface; sent = surface high matches bubbles
                if (oneOfOurs.isPresent()) {
                    chip.setChipBackgroundColor(
                            MaterialColors.getColorStateListOrNull(
                                    context,
                                    com.google.android.material.R.attr
                                            .colorSurfaceContainerHighest));
                } else {
                    chip.setChipBackgroundColor(
                            MaterialColors.getColorStateListOrNull(
                                    context,
                                    com.google.android.material.R.attr.colorSurfaceContainerLow));
                }
                chip.setTextEndPadding(0.0f);
                chip.setTextStartPadding(0.0f);
                chip.setOnClickListener(
                        v -> {
                            if (oneOfOurs.isPresent()) {
                                if (emoji instanceof EmojiSearch.CustomEmoji) {
                                    onCustomReactionRemove.accept(oneOfOurs.get());
                                } else {
                                    onModifiedReactions.accept(
                                        ImmutableSet.copyOf(
                                                Collections2.filter(
                                                        aggregated.ourReactions,
                                                        r -> !r.equals(emoji.toString()))));
                                }
                            } else {
                                if (emoji instanceof EmojiSearch.CustomEmoji) {
                                    onCustomReaction.accept((EmojiSearch.CustomEmoji) emoji);
                                } else {
                                    onModifiedReactions.accept(
                                        new ImmutableSet.Builder<String>()
                                                .addAll(aggregated.ourReactions)
                                                .add(emoji.toString())
                                                .build());
                                }
                            }
                        });
                chip.setOnLongClickListener(v -> onDetailsClicked.apply(reaction));
                chipGroup.addView(chip);
            }
            if (addReaction != null) {
                final Chip chip = new Chip(chipGroup.getContext());
                if (appSettings.isLargeFont()) {
                    chip.setTextAppearance(
                            com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                } else {
                    chip.setTextAppearance(
                            com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
                }
                chip.setChipMinHeight(size-22.0f);
                chip.ensureAccessibleTouchTarget(size);
                chip.setLayoutParams(layoutParams);
                chip.setChipCornerRadius(corner);
                chip.setChipIconResource(R.drawable.ic_add_reaction_24dp);
                //chip.setChipStrokeColor(
                //        MaterialColors.getColorStateListOrNull(
                //                chipGroup.getContext(),
                //                com.google.android.material.R.attr.colorTertiary));
                chip.setChipBackgroundColor(
                        MaterialColors.getColorStateListOrNull(
                                context,
                                com.google.android.material.R.attr.colorSurfaceContainerLow));
                chip.setChipIconTint(
                        MaterialColors.getColorStateListOrNull(
                                context,
                                com.google.android.material.R.attr.colorOnSurface));
                //chip.setEnsureMinTouchTargetSize(false);
                chip.setTextEndPadding(0.0f);
                chip.setTextStartPadding(0.0f);
                chip.setOnClickListener(v -> addReaction.run());
                chipGroup.addView(chip);
            }
        }
    }
}
