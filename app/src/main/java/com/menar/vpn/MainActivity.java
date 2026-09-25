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
import android.widget.ScrollView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

public class MainActivity extends Activity {

    private static final int VPN_REQUEST = 100;
    private static final int FILE_REQUEST = 200;

    private TextView status;
    private TextView configName;
    private TextView serverInfo;
    private TextView trafficInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(35, 35, 35, 35);
        layout.setBackgroundColor(Color.rgb(18, 18, 18));

        TextView logo = new TextView(this);
        logo.setText("𐩱");
        logo.setTextSize(55);
        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("منار VPN");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("VPN • WireGuard");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(17);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 5, 0, 25);

        status = new TextView(this);
        status.setText("🔴 وضعیت: قطع");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(21);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 15, 0, 15);

        Button connect = new Button(this);
        connect.setText("🔘 اتصال به VPN");
        connect.setTextSize(19);
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

        Button importButton = new Button(this);
        importButton.setText("🔑 وارد کردن کانفیگ .conf");
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

        configName = new TextView(this);
        configName.setText("📄 کانفیگی انتخاب نشده");
        configName.setTextColor(Color.WHITE);
        configName.setTextSize(17);
        configName.setGravity(Gravity.CENTER);
        configName.setPadding(0, 10, 0, 20);

        Button serverButton = new Button(this);
        serverButton.setText("🌐 انتخاب سرور");
        serverButton.setTextSize(17);
        serverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(
                        MainActivity.this,
                        "برای انتخاب سرور، ابتدا کانفیگ وارد کنید",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        serverInfo = new TextView(this);
        serverInfo.setText("🌐 سرور: انتخاب نشده");
        serverInfo.setTextColor(Color.LTGRAY);
        serverInfo.setTextSize(16);
        serverInfo.setGravity(Gravity.CENTER);
        serverInfo.setPadding(0, 5, 0, 15);

        trafficInfo = new TextView(this);
        trafficInfo.setText("📡 ترافیک: 0 MB\n⚡ سرعت: 0 KB/s");
        trafficInfo.setTextColor(Color.LTGRAY);
        trafficInfo.setTextSize(16);
        trafficInfo.setGravity(Gravity.CENTER);
        trafficInfo.setPadding(0, 5, 0, 15);

        Button settingsButton = new Button(this);
        settingsButton.setText("⚙️ تنظیمات");
        settingsButton.setTextSize(17);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(
                        MainActivity.this,
                        "تنظیمات منار VPN",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        Button configsButton = new Button(this);
        configsButton.setText("📋 مدیریت کانفیگ‌ها");
        configsButton.setTextSize(17);
        configsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(
                        MainActivity.this,
                        "مدیریت کانفیگ‌ها به‌زودی فعال می‌شود",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        layout.addView(logo);
        layout.addView(title);
        layout.addView(subtitle);
        layout.addView(status);
        layout.addView(connect);
        layout.addView(configName);
        layout.addView(importButton);
        layout.addView(serverButton);
        layout.addView(serverInfo);
        layout.addView(trafficInfo);
        layout.addView(configsButton);
        layout.addView(settingsButton);

        scroll.addView(layout);
        setContentView(scroll);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_REQUEST
                && resultCode == RESULT_OK
                && data != null) {

            Uri uri = data.getData();

            if (uri != null) {
                readConfig(uri);
            }

        } else if (requestCode == VPN_REQUEST) {

            if (resultCode == RESULT_OK) {
                startVpn();
            } else {
                status.setText("🔴 وضعیت: اجازه VPN داده نشد");
            }
        }
    }

    private void readConfig(Uri uri) {
        try {
            String fileName = uri.getLastPathSegment();

            InputStream input = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;

            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }

            input.close();

            String configText = output.toString("UTF-8");

            getSharedPreferences("menar_vpn", MODE_PRIVATE)
                    .edit()
                    .putString("config_name", fileName)
                    .putString("config_text", configText)
                    .apply();

            configName.setText(
                    "📄 کانفیگ انتخاب شد\n" + fileName
            );

            status.setText("🟡 وضعیت: کانفیگ آماده است");

            Toast.makeText(
                    this,
                    "کانفیگ انتخاب شد",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {
            status.setText("🔴 خطا در خواندن کانفیگ");
        }
    }

    private void startVpn() {
        Intent serviceIntent =
                new Intent(this, MenarVpnService.class);

        startService(serviceIntent);

        status.setText("🟢 وضعیت: VPN فعال");
    }
}
