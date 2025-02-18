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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.ui.view.PostImageThumbnailView;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDimen;

public class AlbumViewCell extends FrameLayout {
    private PostImage postImage;
    private PostImageThumbnailView thumbnailView;
    private TextView text;

    public AlbumViewCell(Context context) {
        this(context, null);
    }

    public AlbumViewCell(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AlbumViewCell(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        thumbnailView = findViewById(R.id.thumbnail_view);
        text = findViewById(R.id.text);
    }

    public void setPostImage(PostImage postImage) {
        this.postImage = postImage;
        // Keep this the same as the normal thumbnails to improve performance
        int thumbnailSize = getDimen(getContext(), R.dimen.cell_post_thumbnail_size);
        thumbnailView.setPostImage(postImage, thumbnailSize, thumbnailSize);

        String details = postImage.extension.toUpperCase() + " " + postImage.imageWidth + "x" + postImage.imageHeight +
                " " + AndroidUtils.getReadableFileSize(postImage.size, false);
        text.setText(details);
    }

    public PostImage getPostImage() {
        return postImage;
    }

    public ThumbnailView getThumbnailView() {
        return thumbnailView;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY && (heightMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.AT_MOST)) {
            int width = MeasureSpec.getSize(widthMeasureSpec);

            int height = width + dp(32);

            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
