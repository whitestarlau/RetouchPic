package com.white.component_retouch.PicEditor;

import android.graphics.Paint;
import android.graphics.Path;

class LinePath {
    private final Paint mDrawPaint;
    private final Path mDrawPath;

    LinePath(final Path drawPath, final Paint drawPaints) {
        mDrawPaint = new Paint(drawPaints);
        mDrawPath = new Path(drawPath);
    }

    Paint getDrawPaint() {
        return mDrawPaint;
    }

    Path getDrawPath() {
        return mDrawPath;
    }
}