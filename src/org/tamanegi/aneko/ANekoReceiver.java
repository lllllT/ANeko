package org.tamanegi.aneko;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ANekoReceiver extends BroadcastReceiver
{
    @Override
    public void onReceive(Context context, Intent intent)
    {
        android.util.Log.d("neko", "dbg: onReceive: " + intent);
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            context.startService(new Intent(context, AnimationService.class)
                                 .setAction(AnimationService.ACTION_START));
        }
    }
}
