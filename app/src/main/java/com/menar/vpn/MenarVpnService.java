package com.menar.vpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

public class MenarVpnService extends VpnService {

    private ParcelFileDescriptor vpnInterface;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (vpnInterface == null) {
            Builder builder = new Builder();

            builder.setSession("Menar VPN");
            builder.addAddress("10.0.0.2", 24);
            builder.addRoute("0.0.0.0", 0);

            vpnInterface = builder.establish();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (Exception ignored) {
            }
            vpnInterface = null;
        }

        super.onDestroy();
    }
}
