package com.menar.vpn;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.VpnService;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

public class MainActivity extends Activity {

    private static final int VPN_REQUEST = 100;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(40, 40, 40, 40);
        layout.setBackgroundColor(Color.rgb(18, 18, 18));

        TextView title = new TextView(this);
        title.setText("منار VPN");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("اتصال امن و سریع");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(18);
        subtitle.setGravity(Gravity.CENTER);

        status = new TextView(this);
        status.setText("وضعیت: قطع");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(20);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 50, 0, 30);

        Button connect = new Button(this);
        connect.setText("اتصال");
        connect.setTextSize(20);

        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = VpnService.prepare(MainActivity.this);

                if (intent != null) {
                    startActivityForResult(intent, VPN_REQUEST);
                } else {
                    startVpn();
                }
            }
        });

        layout.addView(title);
        layout.addView(subtitle);
        layout.addView(status);
        layout.addView(connect);

        setContentView(layout);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VPN_REQUEST && resultCode == RESULT_OK) {
            startVpn();
        } else if (requestCode == VPN_REQUEST) {
            status.setText("وضعیت: اجازه داده نشد");
        }
    }

    private void startVpn() {
        Intent serviceIntent = new Intent(this, MenarVpnService.class);
        startService(serviceIntent);
        status.setText("وضعیت: متصل");
    }
}
