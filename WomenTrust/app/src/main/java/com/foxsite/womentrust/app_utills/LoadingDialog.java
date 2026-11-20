package com.foxsite.womentrust.app_utills;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.ProgressBar;

import com.foxsite.womentrust.R;


public class LoadingDialog extends Dialog {

    public LoadingDialog(Context context) {
        super(context);
    }

    @Override
    public void show() {
        // Disabled full-screen freezing modal anywhere in the app
    }

    @Override
    public void cancel() {
        // Safe no-op
    }

    @Override
    public void dismiss() {
        // Safe no-op
    }
}
