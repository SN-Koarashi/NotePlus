package com.snkms.noteplus;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.Toast;

public class ImageViewActivity extends AppCompatActivity {

    WebView wv;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_view);
        this.getSupportActionBar().setTitle("圖片檢視器");

        wv = findViewById(R.id.webView);

        byte[] b =  this.getIntent().getByteArrayExtra("bitmap");
        if(b != null) {
            String imgageBase64 = Base64.encodeToString(b, Base64.NO_PADDING);
            wv.loadData(imgageBase64, "image/jpeg", "base64"); // 載入圖片

            wv.getSettings().setBuiltInZoomControls(true); // 啟用內建縮放控制
            wv.getSettings().setSupportZoom(true); // 啟用縮放
            wv.getSettings().setDisplayZoomControls(false); // 隱藏縮放控制按鈕
            wv.getSettings().setUseWideViewPort(true); // 啟用 viewport tag
            wv.getSettings().setLoadWithOverviewMode(true); // 啟用自動縮放到最適大小
        }
        else{
            Toast.makeText(this, "找不到圖片資訊", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}