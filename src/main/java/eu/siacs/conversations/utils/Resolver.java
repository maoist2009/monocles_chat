package eu.siacs.conversations.utils;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.net.InetAddresses;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.xmpp.Jid;

import org.minidns.dnsmessage.Question;
import org.minidns.dnsname.DnsName;
import org.minidns.dnsname.InvalidDnsNameException;
import org.minidns.dnsqueryresult.DnsQueryResult;
import org.minidns.record.A;
import org.minidns.record.AAAA;
import org.minidns.record.CNAME;
import org.minidns.record.Data;
import org.minidns.record.InternetAddressRR;
import org.minidns.record.Record;
import org.minidns.record.SRV;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.R;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;

import org.minidns.AbstractDnsClient;
import org.minidns.DnsCache;
import org.minidns.DnsClient;
import org.minidns.cache.LruCache;
import org.minidns.dnsmessage.Question;
import org.minidns.dnsname.DnsName;
import org.minidns.dnsname.InvalidDnsNameException;
import org.minidns.dnssec.DnssecResultNotAuthenticException;
import org.minidns.dnssec.DnssecValidationFailedException;
import org.minidns.dnsserverlookup.AndroidUsingExec;
import org.minidns.hla.DnssecResolverApi;
import org.minidns.hla.ResolverApi;
import org.minidns.hla.ResolverResult;
import org.minidns.iterative.ReliableDnsClient;
import org.minidns.record.A;
import org.minidns.record.AAAA;
import org.minidns.record.CNAME;
import org.minidns.record.Data;
import org.minidns.record.InternetAddressRR;
import org.minidns.record.Record;
import org.minidns.record.SRV;

public class Resolver {

    private static final Comparator<Result> RESULT_COMPARATOR =
            (left, right) -> {
                if (left.priority == right.priority) {
                    if (left.directTls == right.directTls) {
                        if (left.ip == null && right.ip == null) {
                            return 0;
                        } else if (left.ip != null && right.ip != null) {
                            final var appSettings = new AppSettings(Conversations.getContext());
                            if (appSettings.preferIPv6()) {
                                if (left.ip instanceof Inet6Address
                                        && right.ip instanceof Inet6Address) {
                                    return 0;
                                } else {
                                    return left.ip instanceof Inet6Address ? -1 : 1;
                                }
                            } else {
                                if (left.ip instanceof Inet4Address
                                        && right.ip instanceof Inet4Address) {
                                    return 0;
                                } else {
                                    return left.ip instanceof Inet4Address ? -1 : 1;
                                }
                            }
                        } else {
                            return left.ip != null ? -1 : 1;
                        }
                    } else {
                        return left.directTls ? 1 : -1;
                    }
                } else {
                    return left.priority - right.priority;
                }
            };

    private static final ExecutorService DNS_QUERY_EXECUTOR = Executors.newFixedThreadPool(12);

    public static final int XMPP_PORT_STARTTLS = 5222;
    private static final int XMPP_PORT_DIRECT_TLS = 5223;

    private static final String DIRECT_TLS_SERVICE = "_xmpps-client";
    private static final String STARTTLS_SERVICE = "_xmpp-client";

    private static XmppConnectionService SERVICE = null;

    private static List<String> DNSSECLESS_TLDS = Arrays.asList(
        "ae",
        "aero",
        "ai",
        "al",
        "ao",
        "aq",
        "as",
        "ba",
        "bb",
        "bd",
        "bf",
        "bi",
        "bj",
        "bn",
        "bo",
        "bs",
        "bw",
        "cd",
        "cf",
        "cg",
        "ci",
        "ck",
        "cm",
        "cu",
        "cv",
        "cw",
        "dj",
        "dm",
        "do",
        "ec",
        "eg",
        "eh",
        "er",
        "et",
        "fj",
        "fk",
        "ga",
        "ge",
        "gf",
        "gh",
        "gm",
        "gp",
        "gq",
        "gt",
        "gu",
        "hm",
        "ht",
        "im",
        "ir",
        "je",
        "jm",
        "jo",
        "ke",
        "kh",
        "km",
        "kn",
        "kp",
        "kz",
        "ls",
        "mg",
        "mh",
        "mk",
        "ml",
        "mm",
        "mo",
        "mp",
        "mq",
        "ms",
        "mt",
        "mu",
        "mv",
        "mw",
        "mz",
        "ne",
        "ng",
        "ni",
        "np",
        "nr",
        "om",
        "pa",
        "pf",
        "pg",
        "pk",
        "pn",
        "ps",
        "py",
        "qa",
        "rw",
        "sd",
        "sl",
        "sm",
        "so",
        "sr",
        "sv",
        "sy",
        "sz",
        "tc",
        "td",
        "tg",
        "tj",
        "to",
        "tr",
        "va",
        "vg",
        "vi",
        "ye",
        "zm",
        "zw"
    );

    protected static final Map<String, String> knownSRV = ImmutableMap.of(
        "_xmpp-client._tcp.yax.im", "xmpp.yaxim.org",
        "_xmpps-client._tcp.yax.im", "xmpp.yaxim.org",
        "_xmpp-server._tcp.yax.im", "xmpp.yaxim.org"
    );

    public static void init(XmppConnectionService service) {
        Resolver.SERVICE = service;
        DnsClient.removeDNSServerLookupMechanism(AndroidUsingExec.INSTANCE);
        DnsClient.addDnsServerLookupMechanism(AndroidUsingExecLowPriority.INSTANCE);
        DnsClient.addDnsServerLookupMechanism(new AndroidUsingLinkProperties(service));
        final AbstractDnsClient client = ResolverApi.INSTANCE.getClient();
        if (client instanceof ReliableDnsClient) {
            ((ReliableDnsClient) client).setUseHardcodedDnsServers(false);
        }
        final AbstractDnsClient dnssecclient = DnssecResolverApi.INSTANCE.getClient();
        if (dnssecclient instanceof ReliableDnsClient) {
            ((ReliableDnsClient) dnssecclient).setUseHardcodedDnsServers(false);
        }
    }

    public static List<Result> fromHardCoded(final String hostname, final int port) {
        final Result result = new Result();
        result.hostname = DnsName.from(hostname);
        result.port = port;
        result.directTls = useDirectTls(port);
        result.authenticated = true;
        return Collections.singletonList(result);
    }

    public static void checkDomain(final Jid jid) {
        DnsName.from(jid.getDomain());
    }

    public static boolean invalidHostname(final String hostname) {
        try {
            DnsName.from(hostname);
            return false;
        } catch (final InvalidDnsNameException | IllegalArgumentException e) {
            return true;
        }
    }

    public static void clearCache() {
        final AbstractDnsClient client = ResolverApi.INSTANCE.getClient();
        final DnsCache dnsCache = client.getCache();
        if (dnsCache instanceof LruCache) {
            Log.d(Config.LOGTAG,"clearing DNS cache");
            ((LruCache) dnsCache).clear();
        }

        final AbstractDnsClient clientSec = DnssecResolverApi.INSTANCE.getClient();
        final DnsCache dnsCacheSec = clientSec.getCache();
        if (dnsCacheSec instanceof LruCache) {
            Log.d(Config.LOGTAG,"clearing DNSSEC cache");
            ((LruCache) dnsCacheSec).clear();
        }
    }

    public static boolean useDirectTls(final int port) {
        return port == 443 || port == XMPP_PORT_DIRECT_TLS;
    }

    public static List<Result> resolve(final String domain) {
        final List<Result> ipResults = fromIpAddress(domain);
        if (!ipResults.isEmpty()) {
            return ipResults;
        }

        final var startTls = resolveSrvAsFuture(domain, false);
        final var directTls = resolveSrvAsFuture(domain, true);

        final var combined = merge(ImmutableList.of(startTls, directTls));

        final var combinedWithFallback =
                Futures.transformAsync(
                        combined,
                        results -> {
                            if (results.isEmpty()) {
                                return resolveNoSrvAsFuture(DnsName.from(domain), true);
                            } else {
                                return Futures.immediateFuture(results);
                            }
                        },
                        MoreExecutors.directExecutor());
        final var orderedFuture =
                Futures.transform(
                        combinedWithFallback,
                        all -> Ordering.from(RESULT_COMPARATOR).immutableSortedCopy(all),
                        MoreExecutors.directExecutor());
        try {
            final var ordered = orderedFuture.get();
            Log.d(Config.LOGTAG, "Resolver (" + ordered.size() + "): " + ordered);
            return ordered;
        } catch (final ExecutionException e) {
            Log.d(Config.LOGTAG, "error resolving DNS", e);
            return Collections.emptyList();
        } catch (final InterruptedException e) {
            Log.d(Config.LOGTAG, "DNS resolution interrupted");
            return Collections.emptyList();
        }
    }

    private static List<Result> fromIpAddress(final String domain) {
        if (IP.matches(domain)) {
            final InetAddress inetAddress;
            try {
                inetAddress = InetAddresses.forString(domain);
            } catch (final IllegalArgumentException e) {
                return Collections.emptyList();
            }
            return Result.createWithDefaultPorts(null, inetAddress);
        } else {
            return Collections.emptyList();
        }
    }

    private static ListenableFuture<List<Result>> resolveSrvAsFuture(
            final String domain, final boolean directTls) {
        final DnsName dnsName =
                DnsName.from(
                        (directTls ? DIRECT_TLS_SERVICE : STARTTLS_SERVICE) + "._tcp." + domain);
        final var resultFuture = resolveAsFuture(dnsName, SRV.class);
        return Futures.transformAsync(
                resultFuture,
                result -> resolveIpsAsFuture(result, directTls),
                MoreExecutors.directExecutor());
    }

    @NonNull
    private static ListenableFuture<List<Result>> resolveIpsAsFuture(
            final ResolverResult<SRV> srvResolverResult, final boolean directTls) {
        final ImmutableList.Builder<ListenableFuture<List<Result>>> futuresBuilder =
                new ImmutableList.Builder<>();
        for (final SRV record : srvResolverResult.getAnswersOrEmptySet()) {
            if (record.target.length() == 0 && record.priority == 0) {
                continue;
            }
            final var ipv4sRaw =
                    resolveIpsAsFuture(
                            record, A.class, srvResolverResult.isAuthenticData(), directTls);
            final var ipv4s =
                    Futures.transform(
                            ipv4sRaw,
                            results -> {
                                if (results.isEmpty()) {
                                    final Result resolverResult =
                                            Result.fromRecord(record, directTls);
                                    resolverResult.authenticated =
                                            srvResolverResult.isAuthenticData();
                                    return Collections.singletonList(resolverResult);
                                } else {
                                    return results;
                                }
                            },
                            MoreExecutors.directExecutor());
            final var ipv6s =
                    resolveIpsAsFuture(
                            record, AAAA.class, srvResolverResult.isAuthenticData(), directTls);
            futuresBuilder.add(ipv4s);
            futuresBuilder.add(ipv6s);
        }
        final ImmutableList<ListenableFuture<List<Result>>> futures = futuresBuilder.build();
        return merge(futures);
    }

    private static ListenableFuture<List<Result>> merge(
            final Collection<ListenableFuture<List<Result>>> futures) {
        return Futures.transform(
                Futures.successfulAsList(futures),
                lists -> {
                    final var builder = new ImmutableList.Builder<Result>();
                    for (final Collection<Result> list : lists) {
                        if (list == null) {
                            continue;
                        }
                        builder.addAll(list);
                    }
                    return builder.build();
                },
                MoreExecutors.directExecutor());
    }

    private static <D extends InternetAddressRR<?>>
            ListenableFuture<List<Result>> resolveIpsAsFuture(
                    final SRV srv, Class<D> type, boolean authenticated, boolean directTls) {
        final var resultFuture = resolveAsFuture(srv.target, type);
        return Futures.transform(
                resultFuture,
                result -> {
                    final var builder = new ImmutableList.Builder<Result>();
                    for (D record : result.getAnswersOrEmptySet()) {
                        Result resolverResult = Result.fromRecord(srv, directTls);
                        resolverResult.authenticated =
                                result.isAuthenticData()
                                        && authenticated; // TODO technically it does not matter if
                        // the IP
                        // was authenticated
                        resolverResult.ip = record.getInetAddress();
                        builder.add(resolverResult);
                    }
                    return builder.build();
                },
                MoreExecutors.directExecutor());
    }

    private static ListenableFuture<List<Result>> resolveNoSrvAsFuture(
            final DnsName dnsName, boolean cName) {
        final ImmutableList.Builder<ListenableFuture<List<Result>>> futuresBuilder =
                new ImmutableList.Builder<>();
        ListenableFuture<List<Result>> aRecordResults =
                Futures.transform(
                        resolveAsFuture(dnsName, A.class),
                        result ->
                                Lists.transform(
                                        ImmutableList.copyOf(result.getAnswersOrEmptySet()),
                                        a -> Result.createDefault(dnsName, a.getInetAddress(), result.isAuthenticData())),
                        MoreExecutors.directExecutor());
        futuresBuilder.add(aRecordResults);
        ListenableFuture<List<Result>> aaaaRecordResults =
                Futures.transform(
                        resolveAsFuture(dnsName, AAAA.class),
                        result ->
                                Lists.transform(
                                        ImmutableList.copyOf(result.getAnswersOrEmptySet()),
                                        aaaa ->
                                                Result.createDefault(
                                                        dnsName, aaaa.getInetAddress(), result.isAuthenticData())),
                        MoreExecutors.directExecutor());
        futuresBuilder.add(aaaaRecordResults);
        if (cName) {
            ListenableFuture<List<Result>> cNameRecordResults =
                    Futures.transformAsync(
                            resolveAsFuture(dnsName, CNAME.class),
                            result -> {
                                Collection<ListenableFuture<List<Result>>> test =
                                        Lists.transform(
                                                ImmutableList.copyOf(result.getAnswersOrEmptySet()),
                                                cname -> resolveNoSrvAsFuture(cname.target, false));
                                return merge(test);
                            },
                            MoreExecutors.directExecutor());
            futuresBuilder.add(cNameRecordResults);
        }
        final ImmutableList<ListenableFuture<List<Result>>> futures = futuresBuilder.build();
        final var noSrvFallbacks = merge(futures);
        return Futures.transform(
                noSrvFallbacks,
                results -> {
                    if (results.isEmpty()) {
                        return Result.createDefaults(dnsName);
                    } else {
                        return results;
                    }
                },
                MoreExecutors.directExecutor());
    }

    private static <D extends Data> ListenableFuture<ResolverResult<D>> resolveAsFuture(
            final DnsName dnsName, final Class<D> type) {
        final var start = System.currentTimeMillis();
        return Futures.submit(
                () -> {
                    final Question question = new Question(dnsName, Record.TYPE.getType(type));
                    if (!DNSSECLESS_TLDS.contains(dnsName.getLabels()[0].toString())) {
                        for (int i = 0; i < 5; i++) {
                            if (System.currentTimeMillis() - start > 5000) break;
                            try {
                                ResolverResult<D> result = DnssecResolverApi.INSTANCE.resolve(question);
                                if (result.wasSuccessful() && !result.isAuthenticData()) {
                                    Log.d(Config.LOGTAG, "DNSSEC validation failed for " + type.getSimpleName() + " : " + result.getUnverifiedReasons());
                                }
                                return result;
                            } catch (DnssecValidationFailedException e) {
                                Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": error resolving " + type.getSimpleName() + " with DNSSEC. Trying DNS instead.", e);
                                // Try again, may be transient DNSSEC failure https://github.com/MiniDNS/minidns/issues/132
                            } catch (Throwable throwable) {
                                Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": error resolving " + type.getSimpleName() + " with DNSSEC. Trying DNS instead.", throwable);
                                break;
                            }
                        }
                    }
                    return ResolverApi.INSTANCE.resolve(question);
                },
                DNS_QUERY_EXECUTOR);
    }

    public static class Result {
        public static final String DOMAIN = "domain";
        public static final String IP = "ip";
        public static final String HOSTNAME = "hostname";
        public static final String PORT = "port";
        public static final String PRIORITY = "priority";
        public static final String DIRECT_TLS = "directTls";
        public static final String AUTHENTICATED = "authenticated";
        private InetAddress ip;
        private DnsName hostname;
        private int port = XMPP_PORT_STARTTLS;
        private boolean directTls = false;
        private boolean authenticated = false;
        private int priority;

        static Result fromRecord(final SRV srv, final boolean directTls) {
            final Result result = new Result();
            result.port = srv.port;
            result.hostname = srv.target;
            result.directTls = directTls;
            result.priority = srv.priority;
            return result;
        }

        static List<Result> createWithDefaultPorts(final DnsName hostname, final InetAddress ip) {
            return Lists.transform(
                    Arrays.asList(XMPP_PORT_STARTTLS),
                    p -> createDefault(hostname, ip, p, false));
        }

        static Result createDefault(final DnsName hostname, final InetAddress ip, final int port, final boolean authenticated) {
            Result result = new Result();
            result.port = port;
            result.hostname = hostname;
            result.ip = ip;
            result.authenticated = authenticated;
            return result;
        }

        static Result createDefault(final DnsName hostname, final InetAddress ip, final boolean authenticated) {
            return createDefault(hostname, ip, XMPP_PORT_STARTTLS, authenticated);
        }

        static Result createDefault(final DnsName hostname) {
            return createDefault(hostname, null, XMPP_PORT_STARTTLS, false);
        }

        static List<Result> createDefaults(
                final DnsName hostname, final Collection<InetAddress> inetAddresses) {
            final ImmutableList.Builder<Result> builder = new ImmutableList.Builder<>();
            for (final InetAddress inetAddress : inetAddresses) {
                builder.addAll(createWithDefaultPorts(hostname, inetAddress));
            }
            return builder.build();
        }

        static List<Result> createDefaults(final DnsName hostname) {
            return createWithDefaultPorts(hostname, null);
        }

        public static Result fromCursor(final Cursor cursor) {
            final Result result = new Result();
            try {
                result.ip =
                        InetAddress.getByAddress(cursor.getBlob(cursor.getColumnIndexOrThrow(IP)));
            } catch (final UnknownHostException e) {
                result.ip = null;
            }
            final String hostname = cursor.getString(cursor.getColumnIndexOrThrow(HOSTNAME));
            result.hostname = hostname == null ? null : DnsName.from(hostname);
            result.port = cursor.getInt(cursor.getColumnIndexOrThrow(PORT));
            result.priority = cursor.getInt(cursor.getColumnIndexOrThrow(PRIORITY));
            result.authenticated = cursor.getInt(cursor.getColumnIndexOrThrow(AUTHENTICATED)) > 0;
            result.directTls = cursor.getInt(cursor.getColumnIndexOrThrow(DIRECT_TLS)) > 0;
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Result result = (Result) o;
            return port == result.port
                    && directTls == result.directTls
                    && authenticated == result.authenticated
                    && priority == result.priority
                    && Objects.equal(ip, result.ip)
                    && Objects.equal(hostname, result.hostname);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(ip, hostname, port, directTls, authenticated, priority);
        }

        public InetAddress getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        public DnsName getHostname() {
            return hostname;
        }

        public boolean isDirectTls() {
            return directTls;
        }

        public boolean isAuthenticated() {
            return authenticated;
        }

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("ip", ip)
                    .add("hostname", hostname)
                    .add("port", port)
                    .add("directTls", directTls)
                    .add("authenticated", authenticated)
                    .add("priority", priority)
                    .toString();
        }

        public String asDestination() {
            return ip != null ? InetAddresses.toAddrString(ip) : hostname.toString();
        }

        public ContentValues toContentValues() {
            final ContentValues contentValues = new ContentValues();
            contentValues.put(IP, ip == null ? null : ip.getAddress());
            contentValues.put(HOSTNAME, hostname == null ? null : hostname.toString());
            contentValues.put(PORT, port);
            contentValues.put(PRIORITY, priority);
            contentValues.put(DIRECT_TLS, directTls ? 1 : 0);
            contentValues.put(AUTHENTICATED, authenticated ? 1 : 0);
            return contentValues;
        }

        public Result seeOtherHost(final String seeOtherHost) {
            final String hostname = seeOtherHost.trim();
            if (hostname.isEmpty()) {
                return null;
            }
            final Result result = new Result();
            result.directTls = this.directTls;
            final int portSegmentStart = hostname.lastIndexOf(':');
            if (hostname.charAt(hostname.length() - 1) != ']'
                    && portSegmentStart >= 0
                    && hostname.length() >= portSegmentStart + 1) {
                final String hostPart = hostname.substring(0, portSegmentStart);
                final String portPart = hostname.substring(portSegmentStart + 1);
                final Integer port = Ints.tryParse(portPart);
                if (port == null || Strings.isNullOrEmpty(hostPart)) {
                    return null;
                }
                final String host = eu.siacs.conversations.utils.IP.unwrapIPv6(hostPart);
                result.port = port;
                if (InetAddresses.isInetAddress(host)) {
                    final InetAddress inetAddress;
                    try {
                        inetAddress = InetAddresses.forString(host);
                    } catch (final IllegalArgumentException e) {
                        return null;
                    }
                    result.ip = inetAddress;
                } else {
                    if (hostPart.trim().isEmpty()) {
                        return null;
                    }
                    try {
                        result.hostname = DnsName.from(hostPart.trim());
                    } catch (final Exception e) {
                        return null;
                    }
                }
            } else {
                final String host = eu.siacs.conversations.utils.IP.unwrapIPv6(hostname);
                if (InetAddresses.isInetAddress(host)) {
                    final InetAddress inetAddress;
                    try {
                        inetAddress = InetAddresses.forString(host);
                    } catch (final IllegalArgumentException e) {
                        return null;
                    }
                    result.ip = inetAddress;
                } else {
                    try {
                        result.hostname = DnsName.from(hostname);
                    } catch (final Exception e) {
                        return null;
                    }
                }
                result.port = port;
            }
            return result;
        }
    }
}
