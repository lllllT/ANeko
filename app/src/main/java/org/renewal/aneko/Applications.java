package org.renewal.aneko;

import android.app.Application;

import com.kieronquinn.monetcompat.core.MonetCompat;

public class Applications extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MonetCompat.enablePaletteCompat();
    }
}
