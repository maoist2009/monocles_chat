/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.ui.util;

import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.lang.IndexOutOfBoundsException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.Roster;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.text.FixedURLSpan;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.Patterns;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.Jid;

public class MyLinkify {

    private static final Linkify.TransformFilter WEBURL_TRANSFORM_FILTER = (matcher, url) -> {
        if (url == null) {
            return null;
        }
        final String lcUrl = url.toLowerCase(Locale.US);
        if (lcUrl.startsWith("http://") || lcUrl.startsWith("https://")) {
            return removeTrailingBracket(url);
        } else {
            return "http://" + removeTrailingBracket(url);
        }
    };

    private static String removeTrailingBracket(final String url) {
        int numOpenBrackets = 0;
        for (char c : url.toCharArray()) {
            if (c == '(') {
                ++numOpenBrackets;
            } else if (c == ')') {
                --numOpenBrackets;
            }
        }
        if (numOpenBrackets != 0 && url.charAt(url.length() - 1) == ')') {
            return url.substring(0, url.length() - 1);
        } else {
            return url;
        }
    }

    private static final Linkify.MatchFilter WEBURL_MATCH_FILTER = (cs, start, end) -> {
        if (start > 0) {
            if (cs.charAt(start - 1) == '@' || cs.charAt(start - 1) == '.'
                    || cs.subSequence(Math.max(0, start - 3), start).equals("://")) {
                return false;
            }
        }

        if (end < cs.length()) {
            // Reject strings that were probably matched only because they contain a dot followed by
            // by some known TLD (see also comment for WORD_BOUNDARY in Patterns.java)
            return !isAlphabetic(cs.charAt(end - 1)) || !isAlphabetic(cs.charAt(end));
        }

        return true;
    };

    private static final Linkify.MatchFilter XMPPURI_MATCH_FILTER = (s, start, end) -> {
        XmppUri uri = new XmppUri(s.subSequence(start, end).toString());
        return uri.isValidJid();
    };

    private static boolean isAlphabetic(final int code) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return Character.isAlphabetic(code);
        }

        switch (Character.getType(code)) {
            case Character.UPPERCASE_LETTER:
            case Character.LOWERCASE_LETTER:
            case Character.TITLECASE_LETTER:
            case Character.MODIFIER_LETTER:
            case Character.OTHER_LETTER:
            case Character.LETTER_NUMBER:
                return true;
            default:
                return false;
        }
    }

    public static void addLinks(Editable body, boolean includeGeo) {
        Linkify.addLinks(body, Patterns.XMPP_PATTERN, "xmpp", XMPPURI_MATCH_FILTER, null);
        Linkify.addLinks(body, Patterns.TEL_URI, "tel");
        Linkify.addLinks(body, Patterns.SMS_URI, "sms");
        Linkify.addLinks(body, Patterns.BITCOIN_URI, "bitcoin");
        Linkify.addLinks(body, Patterns.BITCOINCASH_URI, "bitcoincash");
        Linkify.addLinks(body, Patterns.ETHEREUM_URI, "ethereum");
        Linkify.addLinks(body, Patterns.MONERO_URI, "monero");
        Linkify.addLinks(body, Patterns.WOWNERO_URI, "wownero");
        Linkify.addLinks(body, Patterns.URI_TALER, "taler");
        Linkify.addLinks(body, Patterns.AUTOLINK_WEB_URL, "http", WEBURL_MATCH_FILTER, WEBURL_TRANSFORM_FILTER);
        if (includeGeo) {
            Linkify.addLinks(body, GeoHelper.GEO_URI, "geo");
        }
        FixedURLSpan.fix(body);
    }

    public static void addLinks(Editable body, Account account, Jid context, final XmppConnectionService service) {
        addLinks(body, true);
        Roster roster = account.getRoster();
        urlspan:
        for (final URLSpan urlspan : body.getSpans(0, body.length() - 1, URLSpan.class)) {
            final var start = body.getSpanStart(urlspan);
            if (start < 0) continue;
            for (final var span : body.getSpans(start, start, Object.class))  {
                // instanceof TypefaceSpan is to block in XHTML code blocks. Probably a bit heavy-handed but works for now
                if ((body.getSpanFlags(span) & Spanned.SPAN_USER) >> Spanned.SPAN_USER_SHIFT == StylingHelper.NOLINKIFY || span instanceof TypefaceSpan) {
                    body.removeSpan(urlspan);
                    continue urlspan;
                }
            }
            Uri uri = Uri.parse(urlspan.getURL());
            if ("xmpp".equals(uri.getScheme())) {
                try {
                    if (!body.subSequence(body.getSpanStart(urlspan), body.getSpanEnd(urlspan)).toString().startsWith("xmpp:")) {
                        // Already customized
                        continue;
                    }

                    XmppUri xmppUri = new XmppUri(uri);
                    Jid jid = xmppUri.getJid();
                    String display = xmppUri.toString();

                    if (service.getBooleanPreference("plain_text_links", R.bool.plain_text_links)) {
                        display = xmppUri.toString();
                    } else if (jid.asBareJid().equals(context) && xmppUri.isAction("message") && xmppUri.getBody() != null) {
                        display = xmppUri.getBody();
                    } else if (jid.asBareJid().equals(context) && xmppUri.parameterString().length() > 0) {
                        display = xmppUri.parameterString();
                    } else {
                        ListItem item = account.getBookmark(jid);
                        if (item == null) item = roster.getContact(jid);
                        display = item.getDisplayName() + xmppUri.displayParameterString();
                    }
                    body.replace(
                            body.getSpanStart(urlspan),
                            body.getSpanEnd(urlspan),
                            display
                    );
                } catch (final IllegalArgumentException | IndexOutOfBoundsException e) { /* bad JID or span gone */ }
            }
        }
    }

    public static List<String> extractLinks(final Editable body) {
        MyLinkify.addLinks(body, false);
        final Collection<URLSpan> spans =
                Arrays.asList(body.getSpans(0, body.length() - 1, URLSpan.class));
        final Collection<UrlWrapper> urlWrappers =
                Collections2.filter(
                        Collections2.transform(
                                spans,
                                s ->
                                        s == null
                                                ? null
                                                : new UrlWrapper(body.getSpanStart(s), s.getURL())),
                        uw -> uw != null);
        List<UrlWrapper> sorted = ImmutableList.sortedCopyOf(
                (a, b) -> Integer.compare(a.position, b.position), urlWrappers);
        return Lists.transform(sorted, uw -> uw.url);

    }

    private static class UrlWrapper {
        private final int position;
        private final String url;

        private UrlWrapper(int position, String url) {
            this.position = position;
            this.url = url;
        }
    }
}
