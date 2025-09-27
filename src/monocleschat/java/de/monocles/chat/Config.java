package de.monocles.chat;

import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import de.monocles.chat.ProviderService;
import eu.siacs.conversations.BuildConfig;

public class Config {

    public static final String LOGTAG = BuildConfig.APP_NAME;

    public static final String PROVIDER_URL = "https://data.xmpp.net/providers/v2/providers-A.json";

    public static class DOMAIN {

        // use this fallback server if provider list can't be updated automatically
        public static final List<String> DOMAINS = Arrays.asList(
                "monocles.eu"
        );

        // don't use these servers in provider list
        public static final List<String> BLACKLISTED_DOMAINS = Arrays.asList(
        );

        // choose a random server for registration
        public static String getRandomServer() {
            try {
                new ProviderService().execute();
                final String domain = ProviderService.getProviders().get(new Random().nextInt(ProviderService.getProviders().size()));
                Log.d(LOGTAG, "MagicCreate account on domain: " + domain);
                return domain;
            } catch (Exception e) {
                Log.d(LOGTAG, "Error getting random server ", e);
            }
            return "monocles.eu";
        }
    }
}
