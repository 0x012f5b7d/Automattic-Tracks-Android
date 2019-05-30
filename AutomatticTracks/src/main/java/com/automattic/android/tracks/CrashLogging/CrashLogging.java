package com.automattic.android.tracks.CrashLogging;

import android.content.Context;
import android.util.Log;

import com.automattic.android.tracks.BuildConfig;
import com.automattic.android.tracks.TracksUser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

import io.sentry.Sentry;
import io.sentry.SentryClient;
import io.sentry.android.AndroidSentryClientFactory;
import io.sentry.connection.EventSendCallback;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.UserBuilder;
import io.sentry.event.helper.ShouldSendEventCallback;
import io.sentry.event.interfaces.ExceptionInterface;

public class CrashLogging {

    private static CrashLoggingDataProvider mDataProvider;
    private static SentryClient sentry;

    private static Boolean isStarted = false;

    public static void start(Context context, CrashLoggingDataProvider dataProvider) {

        synchronized (isStarted) {
            if (isStarted) {
                return;
            }

            isStarted = true;
        }

        sentry = Sentry.init(
                dataProvider.sentryDSN(),
                new AndroidSentryClientFactory(context)
        );

        // Apply Sentry Tags
        sentry.setEnvironment(dataProvider.buildType());
        sentry.setRelease(dataProvider.releaseName());
        sentry.addTag("locale", getCurrentLanguage(dataProvider.locale()));

        sentry.addShouldSendEventCallback(new ShouldSendEventCallback() {
            @Override
            public boolean shouldSend(Event event) {

                if (BuildConfig.DEBUG) {
                    return false;
                }

                return !mDataProvider.getUserHasOptedOut();
            }
        });

        sentry.addEventSendCallback(new EventSendCallback() {
            @Override
            public void onFailure(Event event, Exception exception) {
                Log.println(Log.WARN, "SENTRY", exception.getLocalizedMessage());
            }

            @Override
            public void onSuccess(Event event) {
                Log.println(Log.INFO, "SENTRY", event.getMessage());
            }
        });

        mDataProvider = dataProvider;
        applyUserTrackingPreferences();
    }

    public static void crash() {
        throw new UnsupportedOperationException("This is a sample crash");
    }

    public static boolean getUserHasOptedOut() {
        return mDataProvider.getUserHasOptedOut();
    }

    public static void setUserHasOptedOut(boolean userHasOptedOut) {
        mDataProvider.setUserHasOptedOut(userHasOptedOut);
        applyUserTrackingPreferences();
    }

    private static void applyUserTrackingPreferences() {
        if(!mDataProvider.getUserHasOptedOut()) {
            enableUserTracking();
        }
        else{
            disableUserTracking();
        }
    }

    private static void enableUserTracking() {
        TracksUser tracksUser = mDataProvider.currentUser();

        sentry.getContext().setUser(new UserBuilder()
                .setEmail(tracksUser.getEmail())
                .setUsername(tracksUser.getUsername())
                .withData("userID", tracksUser.getUserID())
                .build()
        );
    }

    private static void disableUserTracking() {
        sentry.clearContext();
    }

    public static void setContext(Map<String, Object> context) {
        sentry.setExtra(context);
    }

    // Locale Helpers
    private static String getCurrentLanguage(@Nullable Locale locale) {
        if (locale == null) {
            return "unknown";
        }

        return locale.getLanguage();
    }

    // Logging Helpers
    public static void log(Throwable e) {
        sentry.sendException(e);
    }

    public static void log(@NotNull Throwable e, Map<String, String> data) {

        Event event = new EventBuilder().withMessage(e.getMessage())
                .withLevel(Event.Level.ERROR)
                .withSentryInterface(new ExceptionInterface(e))
                .getEvent();

        sentry.sendEvent(event);
    }

    public static void log(String message) {
        sentry.sendMessage(message);
    }
}
