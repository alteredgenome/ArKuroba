/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.captcha;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.utils.AndroidUtils;

public class GenericWebViewAuthenticationLayout extends WebView implements AuthenticationLayoutInterface {
    public static final int CHECK_INTERVAL = 500;

    private final Handler handler = new Handler();
    private boolean attachedToWindow = false;

    private AuthenticationLayoutCallback callback;
    private SiteAuthentication authentication;
    private boolean resettingFromFoundText = false;

    public GenericWebViewAuthenticationLayout(Context context) {
        this(context, null);
    }

    public GenericWebViewAuthenticationLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GenericWebViewAuthenticationLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setFocusableInTouchMode(true);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public void initialize(Site site, AuthenticationLayoutCallback callback) {
        this.callback = callback;

        authentication = site.actions().postAuthenticate();

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        addJavascriptInterface(new WebInterface(this), "WebInterface");
    }

    @Override
    public void reset() {
        loadUrl(authentication.url);
    }

    @Override
    public void hardReset() {
    }

    private void checkText() {
        loadUrl("javascript:WebInterface.onAllText(document.documentElement.textContent)");
    }

    private void onAllText(String text) {
        boolean retry = text.contains(authentication.retryText);
        boolean success = text.contains(authentication.successText);

        if (retry) {
            if (!resettingFromFoundText) {
                resettingFromFoundText = true;
                postDelayed(() -> {
                    resettingFromFoundText = false;
                    reset();
                }, 1000);
            }
        } else if (success) {
            callback.onAuthenticationComplete(this, "", "");
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        attachedToWindow = true;
        handler.postDelayed(checkTextRunnable, CHECK_INTERVAL);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        attachedToWindow = false;
        handler.removeCallbacks(checkTextRunnable);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        handler.removeCallbacks(checkTextRunnable);

        if (hasWindowFocus) {
            handler.postDelayed(checkTextRunnable, CHECK_INTERVAL);
        }
    }

    private final Runnable checkTextRunnable = new Runnable() {
        @Override
        public void run() {
            checkText();
            reschedule();
        }

        private void reschedule() {
            handler.removeCallbacks(checkTextRunnable);
            if (attachedToWindow && hasWindowFocus()) {
                handler.postDelayed(checkTextRunnable, CHECK_INTERVAL);
            }
        }
    };

    public static class WebInterface {
        private final GenericWebViewAuthenticationLayout layout;

        public WebInterface(GenericWebViewAuthenticationLayout layout) {
            this.layout = layout;
        }

        @JavascriptInterface
        public void onAllText(String text) {
            AndroidUtils.runOnUiThread(() -> layout.onAllText(text));
        }
    }
}
