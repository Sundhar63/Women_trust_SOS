package com.foxsite.womentrust.app_utills;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.foxsite.womentrust.R;

public class SegmentedProgressBar extends View {

    private Paint mildPaint;       // For rating < 5 (Mild)
    private Paint mildYellowPaint; // For rating 5..9 (Mild Yellow)
    private Paint severeRedPaint;  // For rating >= 10 (Severe)
    private Paint backgroundPaint;

    private int progressValue = 0; // Range: 0 to 17
    private int maxRating = 17;

    public SegmentedProgressBar(Context context) {
        super(context);
        init(context);
    }

    public SegmentedProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SegmentedProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mildPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mildPaint.setColor(0xFF4CAF50); // Mild (Green)

        mildYellowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mildYellowPaint.setColor(0xFFFBC02D); // Mild Yellow (Moderate)

        severeRedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        severeRedPaint.setColor(0xFFF44336); // Severe (Red)

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(0xFFE0E0E0);
    }

    public void setProgress(int value) {
        this.progressValue = Math.max(0, Math.min(value, maxRating));
        invalidate();
    }

    public int getProgress() {
        return progressValue;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float unitWidth = width / (float) maxRating;

        // Draw background
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        if (progressValue <= 0) return;

        float start = 0;

        // Segment 1: below 5 (units 1 to 4) -> Mild (Green)
        int mildUnits = Math.min(progressValue, 4);
        if (mildUnits > 0) {
            canvas.drawRect(start, 0, start + mildUnits * unitWidth, height, mildPaint);
            start += mildUnits * unitWidth;
        }

        // Segment 2: below 10 (units 5 to 9) -> Mild Yellow
        if (progressValue >= 5) {
            int yellowUnits = Math.min(progressValue, 9) - 4;
            canvas.drawRect(start, 0, start + yellowUnits * unitWidth, height, mildYellowPaint);
            start += yellowUnits * unitWidth;
        }

        // Segment 3: 10 and above (units 10 to 17) -> Severe (Red)
        if (progressValue >= 10) {
            int redUnits = progressValue - 9;
            canvas.drawRect(start, 0, start + redUnits * unitWidth, height, severeRedPaint);
        }
    }
}
