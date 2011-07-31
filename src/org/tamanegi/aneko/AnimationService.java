package org.tamanegi.aneko;

import java.util.Random;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
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
    private static final int MSG_MOTION_END = 2;

    private static final long ANIMATION_INTERVAL = 125; // msec

    private boolean is_started;
    private SharedPreferences prefs;
    private PreferenceChangeListener pref_listener;

    private Handler handler;
    private MotionState motion_state;
    private View touch_view = null;
    private ImageView image_view = null;
    private WindowManager.LayoutParams touch_params = null;
    private WindowManager.LayoutParams image_params = null;

    @Override
    public void onCreate()
    {
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

    @Override
    public void onConfigurationChanged(Configuration conf)
    {
        WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        int dw = wm.getDefaultDisplay().getWidth();
        int dh = wm.getDefaultDisplay().getHeight();
        motion_state.setDisplaySize(dw, dh);
    }

    private void startAnimation()
    {
        pref_listener = new PreferenceChangeListener();
        prefs.registerOnSharedPreferenceChangeListener(pref_listener);

        if(! checkPrefEnable()) {
            return;
        }

        WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        int dw = wm.getDefaultDisplay().getWidth();
        int dh = wm.getDefaultDisplay().getHeight();
        int cx, cy;
        {
            int pos = new Random().nextInt(400);
            int ratio = pos % 100;
            if(pos / 200 == 0) {
                cx = (dw + 200) * ratio / 100 - 100;
                cy = ((pos / 100) % 2 == 0 ? -100 : dh + 100);
            }
            else {
                cx = ((pos / 100) % 2 == 0 ? -100 : dw + 100);
                cy = (dh + 200) * ratio / 100 - 100;
            }
        }

        motion_state = new MotionState();
        motion_state.setParams(
            new MotionParams(this, getResources(), R.xml.neko));
        motion_state.setDisplaySize(dw, dh);
        motion_state.setCurrentPosition(cx, cy);
        motion_state.setTargetPosition(dw / 2, dh / 2);

        touch_view = new View(this);
        touch_view.setOnTouchListener(new TouchListener());
        touch_params = new WindowManager.LayoutParams(
            0, 0,
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT);
        touch_params.gravity = Gravity.LEFT | Gravity.TOP;
        wm.addView(touch_view, touch_params);

        image_view = new ImageView(this);
        image_params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        image_params.gravity = Gravity.LEFT | Gravity.TOP;
        wm.addView(image_view, image_params);

        requestAnimate();
    }

    private void stopAnimation()
    {
        prefs.unregisterOnSharedPreferenceChangeListener(pref_listener);

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

    private void updateDrawable()
    {
        MotionDrawable drawable = motion_state.getCurrentDrawable();
        image_view.setImageDrawable(drawable);
        drawable.stop();
        drawable.start();

        int duration = motion_state.getCurrentDuration();
        handler.removeMessages(MSG_MOTION_END);
        if(duration >= 0) {
            handler.sendEmptyMessageDelayed(MSG_MOTION_END, duration);
        }
    }

    private void updatePosition()
    {
        Point pt = motion_state.getPosition();
        image_params.x = pt.x;
        image_params.y = pt.y;

        WindowManager wm =
            (WindowManager)getSystemService(WINDOW_SERVICE);
        wm.updateViewLayout(image_view, image_params);
    }

    private void updateToNext()
    {
        if(motion_state.checkWall() ||
           motion_state.updateMovingState() ||
           motion_state.changeToNextState()) {
            updateDrawable();
            updatePosition();
            requestAnimate();
        }
    }

    private boolean onHandleMessage(Message msg)
    {
        switch(msg.what) {
          case MSG_ANIMATE:
              handler.removeMessages(MSG_ANIMATE);

              motion_state.updateState();
              if(motion_state.isStateChanged() ||
                 motion_state.isPositionMoved()) {
                  if(motion_state.isStateChanged()) {
                      updateDrawable();
                  }

                  updatePosition();

                  handler.sendEmptyMessageDelayed(
                      MSG_ANIMATE, ANIMATION_INTERVAL);
              }
              break;

          case MSG_MOTION_END:
              updateToNext();
              break;

          default:
              return false;
        }

        return true;
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

        private int display_width = 1;
        private int display_height = 1;

        private MotionParams params;
        private String cur_state = null;

        private boolean moving_state = false;
        private boolean state_changed = false;
        private boolean position_moved = false;

        private MotionEndListener on_motion_end = new MotionEndListener();

        private void updateState()
        {
            state_changed = false;
            position_moved = false;

            if(cur_state == null) {
                changeState(params.getInitialState());
            }

            float dx = target_x - cur_x;
            float dy = target_y - cur_y;
            float len = FloatMath.sqrt(dx * dx + dy * dy);
            if(len <= params.getProximityDistance()) {
                if(moving_state) {
                    vx = 0;
                    vy = 0;
                    changeState(params.getInitialState());
                }
                return;
            }

            if(! moving_state) {
                changeState(params.getAwakeState());
                return;
            }

            float interval = ANIMATION_INTERVAL / 1000f;

            float acceleration = params.getAcceleration();
            float max_velocity = params.getMaxVelocity();
            float deaccelerate_distance = params.getDeaccelerationDistance();

            vx += acceleration * interval * dx / len;
            vy += acceleration * interval * dy / len;
            float vec = FloatMath.sqrt(vx * vx + vy * vy);
            float vmax = max_velocity *
                Math.min((len + 1) / (deaccelerate_distance + 1), 1);
            if(vec > vmax) {
                float vr = vmax / vec;
                vx *= vr;
                vy *= vr;
            }

            cur_x += vx * interval;
            cur_y += vy * interval;
            position_moved = true;

            changeToMovingState();
            return;
        }

        private boolean checkWall()
        {
            if(! params.needCheckWall(cur_state)) {
                return false;
            }

            MotionDrawable drawable = getCurrentDrawable();
            int dw = drawable.getIntrinsicWidth();
            int dh = drawable.getIntrinsicHeight();
            float proximity_length = params.getProximityDistance();

            MotionParams.WallDirection dir;
            float nx = cur_x;
            float ny = cur_y;
            if(cur_x >= 0 && cur_x < dw + proximity_length) {
                nx = dw / 2f;
                dir = MotionParams.WallDirection.LEFT;
            }
            else if(cur_x <= display_width &&
                    cur_x > display_width - dw - proximity_length) {
                nx = display_width - dw / 2f;
                dir = MotionParams.WallDirection.RIGHT;
            }
            else if(cur_y >= 0 && cur_y < dh + proximity_length) {
                ny = dh / 2f;
                dir = MotionParams.WallDirection.UP;
            }
            else if(cur_y <= display_height &&
                    cur_y > display_height - dh - proximity_length) {
                ny = display_height - dh / 2f;
                dir = MotionParams.WallDirection.DOWN;
            }
            else {
                return false;
            }

            String nstate = params.getWallState(dir);
            if(nstate == null) {
                return false;
            }

            cur_x = target_x = nx;
            cur_y = target_y = ny;
            changeState(nstate);

            return true;
        }

        private boolean updateMovingState()
        {
            if(! params.needCheckMove(cur_state)) {
                return false;
            }

            float dx = target_x - cur_x;
            float dy = target_y - cur_y;
            float len = FloatMath.sqrt(dx * dx + dy * dy);
            if(len <= params.getProximityDistance()) {
                return false;
            }

            changeToMovingState();
            return true;
        }

        private void setParams(MotionParams _params)
        {
            params = _params;
        }

        private void changeState(String state)
        {
            if(state.equals(cur_state)) {
                return;
            }

            cur_state = state;
            state_changed = true;
            moving_state = false;
            getCurrentDrawable().setOnMotionEndListener(on_motion_end);
        }

        private boolean changeToNextState()
        {
            String next_state = params.getNextState(motion_state.cur_state);
            if(next_state == null) {
                return false;
            }

            changeState(next_state);
            return true;
        }

        private void changeToMovingState()
        {
            int dir = (int)(Math.atan2(vy, vx) * 4 / Math.PI + 8.5) % 8;
            MotionParams.MoveDirection dirs[] = {
                MotionParams.MoveDirection.RIGHT,
                MotionParams.MoveDirection.DOWN_RIGHT,
                MotionParams.MoveDirection.DOWN,
                MotionParams.MoveDirection.DOWN_LEFT,
                MotionParams.MoveDirection.LEFT,
                MotionParams.MoveDirection.UP_LEFT,
                MotionParams.MoveDirection.UP,
                MotionParams.MoveDirection.UP_RIGHT
            };

            String nstate = params.getMoveState(dirs[dir]);
            if(nstate == null) {
                return;
            }

            changeState(nstate);
            moving_state = true;
        }

        private void setDisplaySize(int w, int h)
        {
            display_width = w;
            display_height = h;
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
            vx = 0;
            vy = 0;
        }

        private boolean isStateChanged()
        {
            return state_changed;
        }

        private boolean isPositionMoved()
        {
            return position_moved;
        }

        private MotionDrawable getCurrentDrawable()
        {
            return params.getDrawable(cur_state);
        }

        private int getCurrentDuration()
        {
            return params.getDuration(cur_state);
        }

        private Point getPosition()
        {
            MotionDrawable drawable = getCurrentDrawable();
            return new Point((int)(cur_x - drawable.getIntrinsicWidth() / 2f),
                             (int)(cur_y - drawable.getIntrinsicHeight() / 2f));
        }
    }

    private class MotionEndListener
        implements MotionDrawable.OnMotionEndListener
    {
        @Override
        public void onMotionEnd(MotionDrawable drawable)
        {
            if(drawable == motion_state.getCurrentDrawable()) {
                updateToNext();
            }
        }
    }
}
