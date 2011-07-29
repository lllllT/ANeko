package org.tamanegi.aneko;

import java.io.IOException;
import java.util.HashMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Xml;

// todo: change name: MotionState
public class MotionParams
{
    private static final String TAG_MOTION_PARAMS = "motion-params";
    private static final String TAG_MOTION = "motion";
    private static final String TAG_ITEM = "item";
    private static final String TAG_REPEAT_ITEM = "repeat-item";

    private static final float DEF_ACCELERATION = 160f; // dp per sec^2
    private static final float DEF_DEACCELERATE_DISTANCE = 100; // dp
    private static final float DEF_MAX_VELOCITY = 100f; // dp per sec

    private float acceleration;
    private float deacceleration_distance;
    private float max_velocity;

    private HashMap<String, Motion> motions;

    private static class Motion
    {
        private String name;
        private String next_state = null;
        private long duration = -1;

        private boolean check_wall = false;
        private boolean check_move = false;

        private MotionDrawable items = null;
    }

    public MotionParams(Context context, Resources res, int resid)
    {
        float density = context.getResources().getDisplayMetrics().density;
        acceleration = density * DEF_ACCELERATION;
        deacceleration_distance = density * DEF_DEACCELERATE_DISTANCE;
        max_velocity = density * DEF_MAX_VELOCITY;

        XmlPullParser xml = res.getXml(resid);
        AttributeSet attrs = Xml.asAttributeSet(xml);
        try {
            parseXml(xml, attrs);
        }
        catch(XmlPullParserException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch(IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void parseXml(XmlPullParser xml, AttributeSet attrs)
        throws XmlPullParserException, IOException
    {
        // todo:
        int depth = xml.getDepth();
        while(true) {
            int type = xml.next();
            if(type == XmlPullParser.END_DOCUMENT ||
               (type == XmlPullParser.END_TAG && depth >= xml.getDepth())) {
                break;
            }
            if(type != XmlPullParser.START_TAG) {
                continue;
            }

            String name = xml.getName();
            if(TAG_MOTION_PARAMS.equals(name)) {
                // todo: parse motion-params
            }
            else {
                // todo: unknown tag
            }
        }
    }
}
