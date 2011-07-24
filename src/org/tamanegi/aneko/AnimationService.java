package org.tamanegi.aneko;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.FloatMath;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

public class AnimationService extends Service
{
    public static final String ACTION_START = "org.tamanegi.aneko.action.START";
    public static final String ACTION_STOP = "org.tamanegi.aneko.action.STOP";
    public static final String ACTION_TOGGLE =
        "org.tamanegi.aneko.action.TOGGLE";

    private static final String PREF_KEY_ENABLE = "motion.enable";

    private static final int NOTIF_ID = 1;

    private static final int MSG_ANIMATE = 1;

    private static final long ANIMATION_INTERVAL = 125; // msec

    private static final float FORCE_FACTOR = 20f;
    private static final float DEACCELERATE_LENGTH = 100; // dp
    private static final float PROXIMITY_LENGTH = 10;     // dp
    private static final float VELOCITY_MAX = 100f;       // dp per sec

    private boolean is_started;
    private SharedPreferences prefs;
    private PreferenceChangeListener pref_listener;

    private Handler handler;
    private MotionState motion_state;
    private View touch_view = null;
    private ImageView image_view = null;

    @Override
    public void onCreate()
    {
        android.util.Log.d("neko", "dbg: onCreate");
        is_started = false;
        handler = new Handler(new Handler.Callback() {
                @Override public boolean handleMessage(Message msg) {
                    return onHandleMessage(msg);
                }
            });
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onStart(Intent intent, int startId)
    {
        onStartCommand(intent, 0, startId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        android.util.Log.d("neko", "dbg: onStartCommand: " + intent);
        if(! is_started &&
           (intent == null || ACTION_START.equals(intent.getAction()))) {
            startAnimation();
            setForegroundNotification(true);
            is_started = true;
        }
        else if(ACTION_TOGGLE.equals(intent.getAction())) {
            toggleAnimation();
        }
        else if(is_started &&
                ACTION_STOP.equals(intent.getAction())) {
            stopAnimation();
            stopSelfResult(startId);
            setForegroundNotification(false);
            is_started = false;
        }

        return START_REDELIVER_INTENT;
    }

    private void startAnimation()
    {
        pref_listener = new PreferenceChangeListener();
        prefs.registerOnSharedPreferenceChangeListener(pref_listener);

        if(! checkPrefEnable()) {
            return;
        }

        WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);

        motion_state = new MotionState();
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        motion_state.setDensity(metrics.density);

        touch_view = new View(this);
        touch_view.setOnTouchListener(new TouchListener());
        WindowManager.LayoutParams touch_params =
            new WindowManager.LayoutParams(
                0, 0,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        touch_params.gravity = Gravity.LEFT | Gravity.TOP;
        wm.addView(touch_view, touch_params);

        image_view = new ImageView(this);
        image_view.setScaleType(ImageView.ScaleType.MATRIX);
        image_view.setImageResource(R.drawable.neko_wait);
        WindowManager.LayoutParams image_params =
            new WindowManager.LayoutParams(
                WindowManager.LayoutParams.FILL_PARENT,
                WindowManager.LayoutParams.FILL_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        wm.addView(image_view, image_params);

        motion_state.setCurrentPosition(-100, 100);
        motion_state.setTargetPosition(metrics.widthPixels / 2,
                                       metrics.heightPixels / 2);
        requestAnimate();
    }

    private void stopAnimation()
    {
        prefs.unregisterOnSharedPreferenceChangeListener(pref_listener);

        // todo:
        WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        if(touch_view != null) {
            wm.removeView(touch_view);
        }
        if(image_view != null) {
            wm.removeView(image_view);
        }

        touch_view = null;
        image_view = null;

        handler.removeMessages(MSG_ANIMATE);
    }

    private void toggleAnimation()
    {
        boolean enable = prefs.getBoolean(PREF_KEY_ENABLE, true);

        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(PREF_KEY_ENABLE, ! enable);
        edit.commit();

        startService(new Intent(this, AnimationService.class)
                     .setAction(ACTION_START));
    }

    private void setForegroundNotification(boolean start)
    {
        PendingIntent intent = PendingIntent.getService(
            this, 0,
            new Intent(this, AnimationService.class).setAction(ACTION_TOGGLE),
            0);

        Notification notif = new Notification(
            (start ? R.drawable.mati1 : R.drawable.sleep1), null, 0);
        notif.setLatestEventInfo(
            this,
            getString(R.string.app_name),
            getString(start ?
                      R.string.notification_enable :
                      R.string.notification_disable),
            intent);
        notif.flags = Notification.FLAG_ONGOING_EVENT;

        android.util.Log.d("neko", "dbg: setForegroundNotification: " + start);
        if(start) {
            startForeground(NOTIF_ID, notif);
        }
        else {
            stopForeground(true);

            NotificationManager nm = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIF_ID, notif);
        }
    }

    private void requestAnimate()
    {
        if(! handler.hasMessages(MSG_ANIMATE)) {
            handler.sendEmptyMessage(MSG_ANIMATE);
        }
    }

    private boolean onHandleMessage(Message msg)
    {
        switch(msg.what) {
          case MSG_ANIMATE:
              handler.removeMessages(MSG_ANIMATE);
              // todo:
              if(motion_state.updateState()) {
                  Drawable drawable = image_view.getDrawable();
                  if(drawable instanceof Animatable) {
                      ((Animatable)drawable).start();
                  }
                  motion_state.setBounds(drawable.getBounds());
                  image_view.setImageMatrix(motion_state.getMatrix());
                  handler.sendEmptyMessageDelayed(
                      MSG_ANIMATE, ANIMATION_INTERVAL);
              }
              return true;
        }

        return false;
    }

    private boolean checkPrefEnable()
    {
        boolean enable = prefs.getBoolean(PREF_KEY_ENABLE, true);
        if(! enable) {
            startService(new Intent(this, AnimationService.class)
                         .setAction(ACTION_STOP));
            return false;
        }
        else {
            return true;
        }
    }

    private class PreferenceChangeListener
        implements SharedPreferences.OnSharedPreferenceChangeListener
    {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs,
                                              String key)
        {
            checkPrefEnable();
        }
    }

    private class TouchListener implements View.OnTouchListener
    {
        public boolean onTouch(View v, MotionEvent ev)
        {
            if(ev.getAction() == MotionEvent.ACTION_OUTSIDE) {
                motion_state.setTargetPosition(ev.getX(), ev.getY());
                requestAnimate();
            }
            else if(ev.getAction() == MotionEvent.ACTION_CANCEL) {
                motion_state.forceStop();
                requestAnimate();
            }

            return false;
        }
    }

    private class MotionState
    {
        private float cur_x = 0;
        private float cur_y = 0;
        private float target_x = 0;
        private float target_y = 0;
        private float vx = 0;                   // pixels per sec
        private float vy = 0;                   // pixels per sec

        private int width = 0;
        private int height = 0;

        private float deaccelerate_length = DEACCELERATE_LENGTH;
        private float proxmity_length = PROXIMITY_LENGTH;
        private float velocity_max = VELOCITY_MAX;

        private boolean updateState()
        {
            float dx = target_x - cur_x;
            float dy = target_y - cur_y;
            float len = FloatMath.sqrt(dx * dx + dy * dy);
            if(len <= proxmity_length) {
                vx = 0;
                vy = 0;
                return false;
            }

            vx += dx * FORCE_FACTOR / len;
            vy += dy * FORCE_FACTOR / len;
            float vec = FloatMath.sqrt(vx * vx + vy * vy);
            float vmax = velocity_max * Math.min(len / deaccelerate_length, 1);
            if(vec > vmax) {
                float vr = vmax / vec;
                vx *= vr;
                vy *= vr;
            }

            cur_x += vx * ANIMATION_INTERVAL / 1000;
            cur_y += vy * ANIMATION_INTERVAL / 1000;
            return true;
        }

        private void setDensity(float density)
        {
            deaccelerate_length = DEACCELERATE_LENGTH * density;
            proxmity_length = PROXIMITY_LENGTH * density;
            velocity_max = VELOCITY_MAX * density;
        }

        private void setBounds(Rect rect)
        {
            width = rect.width();
            height = rect.height();
        }

        private void setCurrentPosition(float x, float y)
        {
            cur_x = x;
            cur_y = y;
        }

        private void setTargetPosition(float x, float y)
        {
            target_x = x;
            target_y = y;
        }

        private void forceStop()
        {
            setTargetPosition(cur_x, cur_y);
        }

        private Matrix getMatrix()
        {
            Matrix mat = new Matrix();
            mat.setTranslate(cur_x - width / 2, cur_y - height / 2);
            return mat;
        }
    }
}
