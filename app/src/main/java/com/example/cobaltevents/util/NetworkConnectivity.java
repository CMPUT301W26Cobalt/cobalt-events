package com.example.cobaltevents.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * Shared connectivity checks for Firestore / cloud actions.
 * <p>
 * Prefer {@link #hasValidatedInternet(Context)} over only checking Wi‑Fi/cellular transports:
 * a device can be “connected” to an access point while having no usable internet.
 */
public final class NetworkConnectivity {

    private NetworkConnectivity() {}

    /**
     * @return true if the active default network has been validated for internet access
     */
    public static boolean hasValidatedInternet(Context context) {
        if (context == null) return false;
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return false;
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return false;
        }
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
