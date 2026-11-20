package com.foxsite.womentrust.abuse;


import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.foxsite.womentrust.R;

public class FullScreenImageActivity extends AppCompatActivity {

    ImageView fullImageView, btnClose;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen_image);

        fullImageView = findViewById(R.id.fullImageView);
        btnClose = findViewById(R.id.btnClose);

        String imageUrl = getIntent().getStringExtra("image_url");

        Glide.with(this)
                .load(imageUrl)
                .fitCenter()
                .into(fullImageView);

        btnClose.setOnClickListener(v -> finish());
    }
}