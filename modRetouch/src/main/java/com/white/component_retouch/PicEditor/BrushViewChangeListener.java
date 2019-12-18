package com.white.component_retouch.PicEditor;

/**
 * Created on 1/17/2018.
 * @author <a href="https://github.com/burhanrashid52">Burhanuddin Rashid</a>
 * <p></p>
 */

interface BrushViewChangeListener {
    void onViewAdd(BrushDrawingView brushDrawingView);

    void onViewRemoved(BrushDrawingView brushDrawingView);

    void onStartDrawing();

    void onStopDrawing();
}
