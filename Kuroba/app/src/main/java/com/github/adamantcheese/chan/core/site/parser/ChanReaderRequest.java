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
package com.github.adamantcheese.chan.core.site.parser;

import android.util.JsonReader;

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.JsonReaderRequest;
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderRequestParams;
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.Chan.inject;

/**
 * Process a typical imageboard json response.<br>
 * This class is highly multithreaded, take good care to not access models that are to be only
 * changed on the main thread.
 */
public class ChanReaderRequest extends JsonReaderRequest<ChanLoaderResponse> {
    private static final int THREAD_COUNT;
    private static final ExecutorService EXECUTOR;

    static {
        THREAD_COUNT = Runtime.getRuntime().availableProcessors();
        EXECUTOR = Executors.newFixedThreadPool(THREAD_COUNT);
    }

    @Inject
    DatabaseManager databaseManager;

    @Inject
    FilterEngine filterEngine;

    private Loadable loadable;
    private List<Post> cached;
    private ChanReader reader;
    private DatabaseSavedReplyManager databaseSavedReplyManager;

    private List<Filter> filters;

    public ChanReaderRequest(ChanLoaderRequestParams request) {
        super(getChanUrl(request.loadable).toString(), request.listener, request.errorListener);
        inject(this);

        // Copy the loadable and cached list. The cached array may changed/cleared by other threads.
        loadable = request.loadable.clone();
        cached = new ArrayList<>(request.cached);
        reader = request.chanReader;

        filters = new ArrayList<>();
        List<Filter> enabledFilters = filterEngine.getEnabledFilters();
        for (int i = 0; i < enabledFilters.size(); i++) {
            Filter filter = enabledFilters.get(i);

            if (filterEngine.matchesBoard(filter, loadable.board)) {
                // copy the filter because it will get used on other threads
                filters.add(filter.clone());
            }
        }

        databaseSavedReplyManager = databaseManager.getDatabaseSavedReplyManager();
    }

    private static HttpUrl getChanUrl(Loadable loadable) {
        HttpUrl url;

        if (loadable.site == null) {
            throw new NullPointerException("Loadable.site == null");
        }

        if (loadable.board == null) {
            throw new NullPointerException("Loadable.board == null");
        }

        if (loadable.isThreadMode()) {
            url = loadable.site.endpoints().thread(loadable.board, loadable);
        } else if (loadable.isCatalogMode()) {
            url = loadable.site.endpoints().catalog(loadable.board);
        } else {
            throw new IllegalArgumentException("Unknown mode");
        }
        return url;
    }

    @Override
    public Priority getPriority() {
        return Priority.HIGH;
    }

    @Override
    public ChanLoaderResponse readJson(JsonReader reader) throws Exception {
        ChanReaderProcessingQueue processing = new ChanReaderProcessingQueue(cached, loadable);

        if (loadable.isThreadMode()) {
            this.reader.loadThread(reader, processing);
        } else if (loadable.isCatalogMode()) {
            this.reader.loadCatalog(reader, processing);
        } else {
            throw new IllegalArgumentException("Unknown mode");
        }

        List<Post> list = parsePosts(processing);
        return processPosts(processing.getOp(), list);
    }

    // Concurrently parses the new posts with an executor
    private List<Post> parsePosts(ChanReaderProcessingQueue queue) throws InterruptedException, ExecutionException {
        List<Post> cached = queue.getToReuse();
        List<Post> total = new ArrayList<>(cached);

        List<Post.Builder> toParse = queue.getToParse();

        // A list of all ids in the thread. Used for checking if a quote if for the current
        // thread or externally.
        Set<Integer> internalIds = new HashSet<>();
        // All ids of cached posts.
        for (int i = 0; i < cached.size(); i++) {
            internalIds.add(cached.get(i).no);
        }
        // And ids for posts to parse, from the builder.
        for (int i = 0; i < toParse.size(); i++) {
            internalIds.add(toParse.get(i).id);
        }
        // Do not modify internalIds after this point.
        internalIds = Collections.unmodifiableSet(internalIds);

        List<Callable<Post>> tasks = new ArrayList<>(toParse.size());
        for (Post.Builder post : toParse) {
            tasks.add(new PostParseCallable(filterEngine,
                    filters,
                    databaseSavedReplyManager,
                    post,
                    reader,
                    internalIds));
        }

        if (!tasks.isEmpty()) {
            List<Future<Post>> futures = EXECUTOR.invokeAll(tasks);
            for (int i = 0; i < futures.size(); i++) {
                Future<Post> future = futures.get(i);
                Post parsedPost = future.get();
                if (parsedPost != null) {
                    total.add(parsedPost);
                }
            }
        }

        return total;
    }

    private ChanLoaderResponse processPosts(Post.Builder op, List<Post> allPost) {
        ChanLoaderResponse response = new ChanLoaderResponse(op, new ArrayList<>(allPost.size()));

        List<Post> cachedPosts = new ArrayList<>();
        List<Post> newPosts = new ArrayList<>();
        if (cached.size() > 0) {
            // Add all posts that were parsed before
            cachedPosts.addAll(cached);

            Map<Integer, Post> cachedPostsByNo = new HashMap<>();
            for (int i = 0; i < cachedPosts.size(); i++) {
                Post post = cachedPosts.get(i);
                cachedPostsByNo.put(post.no, post);
            }

            Map<Integer, Post> serverPostsByNo = new HashMap<>();
            for (int i = 0; i < allPost.size(); i++) {
                Post post = allPost.get(i);
                serverPostsByNo.put(post.no, post);
            }

            // If there's a cached post but it's not in the list received from the server, mark it as deleted
            if (loadable.isThreadMode()) {
                for (int i = 0; i < cachedPosts.size(); i++) {
                    Post cachedPost = cachedPosts.get(i);
                    cachedPost.deleted.set(!serverPostsByNo.containsKey(cachedPost.no));
                }
            }

            // If there's a post in the list from the server, that's not in the cached list, add it.
            for (int i = 0; i < allPost.size(); i++) {
                Post serverPost = allPost.get(i);
                if (!cachedPostsByNo.containsKey(serverPost.no)) {
                    newPosts.add(serverPost);
                }
            }
        } else {
            newPosts.addAll(allPost);
        }

        List<Post> allPosts = new ArrayList<>(cachedPosts.size() + newPosts.size());
        allPosts.addAll(cachedPosts);
        allPosts.addAll(newPosts);

        if (loadable.isThreadMode()) {
            Map<Integer, Post> postsByNo = new HashMap<>();
            for (int i = 0; i < allPosts.size(); i++) {
                Post post = allPosts.get(i);
                postsByNo.put(post.no, post);
            }

            // Maps post no's to a list of no's that that post received replies from
            Map<Integer, List<Integer>> replies = new HashMap<>();

            for (int i = 0; i < allPosts.size(); i++) {
                Post sourcePost = allPosts.get(i);

                for (int replyTo : sourcePost.repliesTo) {
                    List<Integer> value = replies.get(replyTo);
                    if (value == null) {
                        value = new ArrayList<>(3);
                        replies.put(replyTo, value);
                    }
                    value.add(sourcePost.no);
                }
            }

            for (Map.Entry<Integer, List<Integer>> entry : replies.entrySet()) {
                int key = entry.getKey();
                List<Integer> value = entry.getValue();

                Post subject = postsByNo.get(key);
                // Sometimes a post replies to a ghost, a post that doesn't exist.
                if (subject != null) {
                    synchronized (subject.repliesFrom) {
                        subject.repliesFrom.clear();
                        subject.repliesFrom.addAll(value);
                    }
                }
            }
        }

        response.posts.addAll(allPosts);

        return response;
    }
}
