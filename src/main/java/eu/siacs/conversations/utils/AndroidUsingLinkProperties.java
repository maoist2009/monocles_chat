package eu.siacs.conversations.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.os.Build;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.minidns.dnsserverlookup.AbstractDnsServerLookupMechanism;
import org.minidns.dnsserverlookup.AndroidUsingExec;

public class AndroidUsingLinkProperties extends AbstractDnsServerLookupMechanism {

    private final Context context;

    AndroidUsingLinkProperties(Context context) {
        super(AndroidUsingLinkProperties.class.getSimpleName(), AndroidUsingExec.PRIORITY - 1);
        this.context = context;
    }

    @Override
    public boolean isAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    @Override
    @TargetApi(21)
    public List<String> getDnsServerAddresses() {
        final ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final Network[] networks = connectivityManager == null ? null : connectivityManager.getAllNetworks();
        if (networks == null) {
            return new ArrayList<>();
        }
        final Network activeNetwork = getActiveNetwork(connectivityManager);
        final List<String> networkServers = new ArrayList<>();
        final List<String> otherServers = new ArrayList<>();
        for(Network network : networks) {
            LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
            if (linkProperties == null) {
                continue;
            }
            final NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);
            final boolean isActiveNetwork = network.equals(activeNetwork);
            final boolean isVpn = networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_VPN;
            final List<String> servers = getIPv4First(linkProperties.getDnsServers());
            if (hasDefaultRoute(linkProperties) || isActiveNetwork || activeNetwork == null || isVpn) {
                if (isActiveNetwork) networkServers.addAll(0, servers);
                if (isVpn) networkServers.addAll(servers);
                otherServers.addAll(servers);
            }
        }
        return (networkServers.isEmpty() ? otherServers : networkServers).stream().distinct().collect(Collectors.toList());
    }

    @TargetApi(23)
    private static Network getActiveNetwork(ConnectivityManager cm) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? cm.getActiveNetwork() : null;
    }

    private static List<String> getIPv4First(List<InetAddress> in) {
        List<String> out = new ArrayList<>();
        for(InetAddress address : in) {
            if (address instanceof Inet4Address) {
                out.add(0, address.getHostAddress());
            } else {
                out.add(address.getHostAddress());
            }
        }
        return out;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static boolean hasDefaultRoute(LinkProperties linkProperties) {
        for(RouteInfo route: linkProperties.getRoutes()) {
            if (route.isDefaultRoute()) {
                return true;
            }
        }
        return false;
    }
}
