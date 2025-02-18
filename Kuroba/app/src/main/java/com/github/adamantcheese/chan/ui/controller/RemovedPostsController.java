package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.ui.helper.RemovedPostsHelper;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

public class RemovedPostsController extends BaseFloatingController implements View.OnClickListener {
    private static final String TAG = "RemovedPostsController";

    @Inject
    ImageLoader imageLoader;

    private RemovedPostsHelper removedPostsHelper;

    private ConstraintLayout viewHolder;
    private ListView postsListView;
    private AppCompatButton restorePostsButton;
    private AppCompatButton selectAllButton;

    @Nullable
    private RemovedPostAdapter adapter;

    public RemovedPostsController(
            Context context,
            RemovedPostsHelper removedPostsHelper) {
        super(context);
        this.removedPostsHelper = removedPostsHelper;

        inject(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        viewHolder = view.findViewById(R.id.removed_posts_view_holder);
        restorePostsButton = view.findViewById(R.id.removed_posts_restore_posts);
        selectAllButton = view.findViewById(R.id.removed_posts_select_all);
        postsListView = view.findViewById(R.id.removed_posts_posts_list);

        viewHolder.setOnClickListener(this);
        restorePostsButton.setOnClickListener(this);
        selectAllButton.setOnClickListener(this);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.layout_removed_posts;
    }

    @Override
    public boolean onBack() {
        removedPostsHelper.pop();
        return true;
    }

    public void showRemovePosts(List<Post> removedPosts) {
        if (!BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Must be executed on the main thread!");
        }

        RemovedPost[] removedPostsArray = new RemovedPost[removedPosts.size()];

        for (int i = 0, removedPostsSize = removedPosts.size(); i < removedPostsSize; i++) {
            Post post = removedPosts.get(i);
            removedPostsArray[i] = new RemovedPost(post.images, post.no, post.comment.toString(), false);
        }

        if (adapter == null) {
            adapter = new RemovedPostAdapter(
                    context,
                    imageLoader,
                    R.layout.layout_removed_posts);

            postsListView.setAdapter(adapter);
        }

        adapter.setRemovedPosts(removedPostsArray);
    }

    @Override
    public void onClick(View v) {
        if (v == viewHolder) {
            removedPostsHelper.pop();
        } else if (v == restorePostsButton) {
            onRestoreClicked();
        } else if (v == selectAllButton) {
            if (adapter != null) {
                adapter.selectAll();
            }
        }
    }

    private void onRestoreClicked() {
        if (adapter == null) {
            return;
        }

        List<Integer> selectedPosts = adapter.getSelectedPostNoList();
        if (selectedPosts.isEmpty()) {
            return;
        }

        removedPostsHelper.onRestoreClicked(selectedPosts);
    }

    public static class RemovedPost {
        private List<PostImage> images;
        private int postNo;
        private String comment;
        private boolean checked;

        public RemovedPost(List<PostImage> images, int postNo, String comment, boolean checked) {
            this.images = images;
            this.postNo = postNo;
            this.comment = comment;
            this.checked = checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
        }

        public List<PostImage> getImages() {
            return images;
        }

        public int getPostNo() {
            return postNo;
        }

        public String getComment() {
            return comment;
        }

        public boolean isChecked() {
            return checked;
        }
    }

    public static class RemovedPostAdapter extends ArrayAdapter<RemovedPost> {
        private ImageLoader imageLoader;
        private List<RemovedPost> removedPostsCopy = new ArrayList<>();

        public RemovedPostAdapter(@NonNull Context context, ImageLoader imageLoader, int resource) {
            super(context, resource);
            this.imageLoader = imageLoader;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            RemovedPost removedPost = getItem(position);

            if (removedPost == null) {
                throw new RuntimeException("removedPost is null! position = " +
                        position + ", items count = " + getCount());
            }

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(
                        R.layout.layout_removed_post,
                        parent,
                        false);
            }

            LinearLayout viewHolder = convertView.findViewById(R.id.removed_post_view_holder);
            AppCompatTextView postNo = convertView.findViewById(R.id.removed_post_no);
            AppCompatTextView postComment = convertView.findViewById(R.id.removed_post_comment);
            AppCompatCheckBox checkbox = convertView.findViewById(R.id.removed_post_checkbox);
            AppCompatImageView postImage = convertView.findViewById(R.id.post_image);

            postNo.setText(String.format("No. %d", removedPost.postNo));
            postComment.setText(removedPost.comment);
            checkbox.setChecked(removedPost.isChecked());

            if (removedPost.images.size() > 0) {
                // load only the first image
                PostImage image = removedPost.getImages().get(0);
                postImage.setVisibility(View.VISIBLE);

                imageLoader.get(image.getThumbnailUrl().toString(), new ImageLoader.ImageListener() {
                    @Override
                    public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                        postImage.setImageBitmap(response.getBitmap());
                    }

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Logger.e(TAG, "Error while trying to download post image", error);
                        postImage.setVisibility(View.GONE);
                    }
                }, postImage.getWidth(), postImage.getHeight());
            } else {
                postImage.setVisibility(View.GONE);
            }

            checkbox.setOnClickListener((v) -> onItemClick(position));
            viewHolder.setOnClickListener((v) -> onItemClick(position));

            return convertView;
        }

        public void onItemClick(int position) {
            RemovedPost rp = getItem(position);
            if (rp == null) {
                return;
            }

            rp.setChecked(!rp.isChecked());
            removedPostsCopy.get(position).setChecked(rp.isChecked());

            notifyDataSetChanged();
        }

        public void setRemovedPosts(RemovedPost[] removedPostsArray) {
            removedPostsCopy.clear();
            removedPostsCopy.addAll(Arrays.asList(removedPostsArray));

            clear();
            addAll(removedPostsCopy);
            notifyDataSetChanged();
        }

        public List<Integer> getSelectedPostNoList() {
            List<Integer> selectedPosts = new ArrayList<>();

            for (int i = 0; i < removedPostsCopy.size(); ++i) {
                RemovedPost removedPost = removedPostsCopy.get(i);
                if (removedPost == null) {
                    continue;
                }

                if (removedPost.isChecked()) {
                    selectedPosts.add(removedPost.getPostNo());
                }
            }

            return selectedPosts;
        }

        public void selectAll() {
            if (removedPostsCopy.isEmpty()) {
                return;
            }

            // If first item is selected - unselect all other items
            // If it's not selected - select all other items
            boolean select = !removedPostsCopy.get(0).isChecked();

            for (int i = 0; i < removedPostsCopy.size(); ++i) {
                RemovedPost rp = getItem(i);
                if (rp == null) {
                    return;
                }

                rp.setChecked(select);
                removedPostsCopy.get(i).setChecked(select);
            }

            notifyDataSetChanged();
        }
    }
}
