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
package com.github.adamantcheese.chan.ui.cell;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;

public class ThreadStatusCell extends LinearLayout implements View.OnClickListener {
    private static final int UPDATE_INTERVAL = 1000;
    private static final int MESSAGE_INVALIDATE = 1;

    private Callback callback;

    private boolean running = false;

    private TextView text;
    private String error;
    private Handler handler = new Handler(msg -> {
        if (msg.what == MESSAGE_INVALIDATE) {
            if (running && update()) {
                schedule();
            }

            return true;
        } else {
            return false;
        }
    });

    public ThreadStatusCell(Context context, AttributeSet attrs) {
        super(context, attrs);

        setBackgroundResource(R.drawable.item_background);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        text = findViewById(R.id.text);
        text.setTypeface(ThemeHelper.getTheme().mainFont);

        setOnClickListener(this);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setError(String error) {
        this.error = error;
        if (error == null) {
            schedule();
        }
    }

    @SuppressLint("SetTextI18n")
    public boolean update() {
        if (error != null) {
            text.setText(error + "\n" + getContext()
                    .getString(R.string.thread_refresh_bar_inactive));
            return false;
        } else {
            ChanThread chanThread = callback.getChanThread();
            if (chanThread == null) {
                // Recyclerview not clearing immediately or view didn't receive
                // onDetachedFromWindow.
                return false;
            }

            boolean update = false;

            SpannableStringBuilder builder = new SpannableStringBuilder();

            if (chanThread.archived) {
                builder.append(getContext().getString(R.string.thread_archived));
            } else if (chanThread.closed) {
                builder.append(getContext().getString(R.string.thread_closed));
            }

            if (!chanThread.archived && !chanThread.closed) {
                long time = callback.getTimeUntilLoadMore() / 1000L;
                if (!callback.isWatching()) {
                    builder.append(getContext().getString(R.string.thread_refresh_bar_inactive));
                } else if (time <= 0) {
                    builder.append(getContext().getString(R.string.thread_refresh_now));
                } else {
                    builder.append(getContext().getString(R.string.thread_refresh_countdown, time));
                }
                update = true;
            }

            Post op = chanThread.op;
            Board board = op.board;
            if (board != null) {
                boolean hasReplies = op.getReplies() >= 0;
                boolean hasImages = op.getImagesCount() >= 0;
                if (hasReplies && hasImages) {
                    boolean hasBumpLimit = board.bumpLimit > 0;
                    boolean hasImageLimit = board.imageLimit > 0;

                    SpannableString replies = new SpannableString(op.getReplies() + "R");
                    if (hasBumpLimit && op.getReplies() >= board.bumpLimit) {
                        replies.setSpan(new StyleSpan(Typeface.ITALIC), 0, replies.length(), 0);
                    }

                    SpannableString images = new SpannableString(op.getImagesCount() + "I");
                    if (hasImageLimit && op.getImagesCount() >= board.imageLimit) {
                        images.setSpan(new StyleSpan(Typeface.ITALIC), 0, images.length(), 0);
                    }

                    builder.append('\n').append(replies).append(" / ").append(images);

                    if (op.getUniqueIps() >= 0) {
                        String ips = op.getUniqueIps() + "P";
                        builder.append(" / ").append(ips);
                    }

                    builder.append(" / ").append(getContext().getString(R.string.thread_page_no)).append(' ');

                    Chan4PagesRequest.Page p = callback.getPage(op);
                    if (p != null) {
                        SpannableString page = new SpannableString(String.valueOf(p.page));
                        if (p.page >= board.pages) {
                            page.setSpan(new StyleSpan(Typeface.ITALIC), 0, page.length(), 0);
                        }
                        builder.append(page);
                    } else {
                        builder.append('?');
                    }
                }
            }

            text.setText(builder);

            return update;
        }
    }

    private void schedule() {
        running = true;
        if (!handler.hasMessages(MESSAGE_INVALIDATE)) {
            handler.sendMessageDelayed(handler.obtainMessage(MESSAGE_INVALIDATE), UPDATE_INTERVAL);
        }
    }

    private void unschedule() {
        running = false;
        handler.removeMessages(MESSAGE_INVALIDATE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        schedule();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unschedule();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            if (update()) {
                schedule();
            }
        } else {
            unschedule();
        }
    }

    @Override
    public void onClick(View v) {
        error = null;
        callback.onListStatusClicked();
        update();
    }

    public interface Callback {
        long getTimeUntilLoadMore();

        boolean isWatching();

        ChanThread getChanThread();

        Chan4PagesRequest.Page getPage(Post op);

        void onListStatusClicked();
    }
}
