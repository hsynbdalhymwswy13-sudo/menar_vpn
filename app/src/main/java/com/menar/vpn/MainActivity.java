package com.menar.vpn;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.net.VpnService;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends Activity {

    private static final int VPN_REQUEST = 100;
    private static final int FILE_REQUEST = 200;

    private TextView status;
    private TextView configName;

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
        subtitle.setText("WireGuard VPN");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(18);
        subtitle.setGravity(Gravity.CENTER);

        status = new TextView(this);
        status.setText("وضعیت: قطع");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(20);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 35, 0, 20);

        configName = new TextView(this);
        configName.setText("کانفیگی انتخاب نشده");
        configName.setTextColor(Color.WHITE);
        configName.setTextSize(17);
        configName.setGravity(Gravity.CENTER);
        configName.setPadding(0, 10, 0, 20);

        Button importButton = new Button(this);
        importButton.setText("📥 وارد کردن کانفیگ .conf");
        importButton.setTextSize(17);

        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, FILE_REQUEST);
            }
        });

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
        layout.addView(configName);
        layout.addView(importButton);
        layout.addView(connect);

        setContentView(layout);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();

            if (uri != null) {
                readConfig(uri);
            }

        } else if (requestCode == VPN_REQUEST) {

            if (resultCode == RESULT_OK) {
                startVpn();
            } else {
                status.setText("وضعیت: اجازه VPN داده نشد");
            }
        }
    }

    private void readConfig(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(inputStream));

            String line;
            StringBuilder config = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                config.append(line).append("\n");
            }

            reader.close();

            String fileName = uri.getLastPathSegment();

            configName.setText(
                    "کانفیگ انتخاب شد\n" + fileName
            );

            status.setText("وضعیت: کانفیگ آماده است");

        } catch (Exception e) {
            status.setText("خطا در خواندن کانفیگ");
        }
    }

    private void startVpn() {
        Intent serviceIntent = new Intent(this, MenarVpnService.class);
        startService(serviceIntent);
        status.setText("وضعیت: متصل");
    }
}
