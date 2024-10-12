package com.example.cameratest.utils;

import android.app.Activity;
import android.content.Context;
import android.view.OrientationEventListener;

import androidx.activity.ComponentActivity;
import androidx.annotation.MainThread;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import java.util.HashSet;
import java.util.Set;


public class OrientationService implements LifecycleObserver {

    public interface OrientationDegreeChangedListener {
        @MainThread
        void onOrientationDegreeChanged(int degree);
    }

    public interface LayoutOrientationChangedListener {
        @MainThread
        void onLayoutOrientationChanged(LayoutOrientation changed);
    }

    public enum LayoutOrientation {
        Unknown,
        Portrait,
        Landscape,
        ReversePortrait,
        ReverseLandscape;

        public boolean isPortrait() {
            return this == Unknown || this == Portrait || this == ReversePortrait;
        }
    }

    private final Activity mActivity;

    private final Lifecycle mLifecycle;

    private final Set<LayoutOrientationChangedListener> mLayoutOrientationChangedListenerSet =
            new HashSet<>();

    private final Set<OrientationDegreeChangedListener> mOrientationDegreeChangedListenerSet =
            new HashSet<>();

    private OrientationEventListener mOrientationEventListener;

    private LayoutOrientation mLastDetectedOrientation = LayoutOrientation.Unknown;

    private int mLastOrientationDegree = OrientationEventListener.ORIENTATION_UNKNOWN;

    private int mLastDeterminedOrientationDegree = OrientationEventListener.ORIENTATION_UNKNOWN;

    public OrientationService(ComponentActivity activity) {
        mActivity = activity;
        mLifecycle = activity.getLifecycle();
        mLifecycle.addObserver(this);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    public void onCreate() {
        // Start orientation listener.
        enableOrientation();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void onResume() {
        // Start orientation listener.
        enableOrientation();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void onPause() {
        // ORIENTATION
        disableOrientation();

        mLastDetectedOrientation = LayoutOrientation.Unknown;
        mLastOrientationDegree = OrientationEventListener.ORIENTATION_UNKNOWN;
        mLastDeterminedOrientationDegree = OrientationEventListener.ORIENTATION_UNKNOWN;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void onDestroy() {
        // ORIENTATION
        mLayoutOrientationChangedListenerSet.clear();
        mOrientationDegreeChangedListenerSet.clear();
    }

    public LayoutOrientation getLayoutOrientation() {
        int orientation = mLastOrientationDegree;
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            orientation = mLastDeterminedOrientationDegree;
        }
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return LayoutOrientation.Unknown;
        }

        orientation += 90;
        orientation %= 360;

        boolean nowPortrait = (
                mLastDetectedOrientation == LayoutOrientation.Portrait ||
                        mLastDetectedOrientation == LayoutOrientation.ReversePortrait);
        int margin = nowPortrait ? 60 : 30;

        if (in(orientation, 90 - margin, 90 + margin)) {
            return LayoutOrientation.Portrait;

        } else if (in(orientation, 90 + margin, 270 - margin)){
            return LayoutOrientation.ReverseLandscape;

        } else if (in(orientation, 270 - margin, 270 + margin)) {
            return LayoutOrientation.ReversePortrait;

        } else {
            return LayoutOrientation.Landscape;
        }
    }

    private void enableOrientation() {
        if (mOrientationEventListener == null) {
            mOrientationEventListener = new ExtendedOrientationEventListener(mActivity);
            mOrientationEventListener.enable();
        }
    }

    private void disableOrientation() {
        if (mOrientationEventListener != null) {
            mOrientationEventListener.disable();
            mOrientationEventListener = null;
        }
    }

    private void notifyOrientationDegreeChanged(int degree) {
        if (degree == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return;
        }
        for (OrientationDegreeChangedListener listener
                : mOrientationDegreeChangedListenerSet) {
            listener.onOrientationDegreeChanged(degree);
        }
    }

    private void notifyLayoutOrientationChanged(LayoutOrientation orientation) {
        if (orientation == mLastDetectedOrientation) {
            return;
        } else if (orientation == LayoutOrientation.Unknown) {
            return;
        }
        mLastDetectedOrientation = orientation;
        setLayoutOrientation(mLastDetectedOrientation);
        for (LayoutOrientationChangedListener listener : mLayoutOrientationChangedListenerSet) {
            listener.onLayoutOrientationChanged(mLastDetectedOrientation);
        }
    }

    private void setLayoutOrientation(LayoutOrientation orientation) {
        int orientationDegree = getOrientationDegree(orientation);
        mLastOrientationDegree = orientationDegree;
        mLastDeterminedOrientationDegree = orientationDegree;
    }

    public int getOrientationDegree(LayoutOrientation fixed) {

        int degree;
        switch(fixed) {
            case Unknown:
                // fall-through.
            case Landscape:
                degree = 270;
                break;
            case Portrait:
                degree = 0;
                break;
            case ReverseLandscape:
                degree = 90;
                break;
            case ReversePortrait:
                degree = 180;
                break;
            default:
                degree = OrientationEventListener.ORIENTATION_UNKNOWN;
                return degree;
        }

        return degree;
    }

    private static boolean in(int testee, int lower, int upper) {
        return (testee >= lower && testee < upper);
    }

    private class ExtendedOrientationEventListener extends OrientationEventListener {
        ExtendedOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(final int orientation) {
            notifyOrientationDegreeChanged(orientation);

            if (orientation == mLastOrientationDegree) {
                return;
            }
            mLastOrientationDegree = orientation;

            if (mLastOrientationDegree != OrientationEventListener.ORIENTATION_UNKNOWN) {
                mLastDeterminedOrientationDegree = mLastOrientationDegree;
            }

            notifyLayoutOrientationChanged(getLayoutOrientation());
        }
    }
}
