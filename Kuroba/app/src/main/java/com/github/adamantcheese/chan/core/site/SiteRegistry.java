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
package com.github.adamantcheese.chan.core.site;

import android.util.SparseArray;

import com.github.adamantcheese.chan.core.site.sites.wired7.Wired7;
import com.github.adamantcheese.chan.core.site.sites.arisuchan.Arisuchan;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.core.site.sites.chan8.Chan8;
import com.github.adamantcheese.chan.core.site.sites.dvach.Dvach;
import com.github.adamantcheese.chan.core.site.sites.lainchan.Lainchan;
import com.github.adamantcheese.chan.core.site.sites.sushichan.Sushichan;
import com.github.adamantcheese.chan.core.site.sites.yuki.Yuki;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of all sites and url handler we have.
 */
public class SiteRegistry {
    public static final List<SiteUrlHandler> URL_HANDLERS = new ArrayList<>();
    public static final SparseArray<Class<? extends Site>> SITE_CLASSES = new SparseArray<>();

    static {
        URL_HANDLERS.add(Chan4.URL_HANDLER);
        URL_HANDLERS.add(Chan8.URL_HANDLER);
        URL_HANDLERS.add(Lainchan.URL_HANDLER);
        URL_HANDLERS.add(Arisuchan.URL_HANDLER);
        URL_HANDLERS.add(Sushichan.URL_HANDLER);
        URL_HANDLERS.add(Dvach.URL_HANDLER);
        URL_HANDLERS.add(Wired7.URL_HANDLER);
        URL_HANDLERS.add(Yuki.URL_HANDLER);
    }

    static {
        // This id-siteclass mapping is used to look up the correct site class at deserialization.
        // This differs from the Site.id() id, that id is used for site instance linking, this is just to
        // find the correct class to use.
        SITE_CLASSES.put(0, Chan4.class);

        SITE_CLASSES.put(1, Chan8.class);
        SITE_CLASSES.put(2, Lainchan.class);
        SITE_CLASSES.put(3, Arisuchan.class);
        SITE_CLASSES.put(4, Sushichan.class);
        SITE_CLASSES.put(5, Dvach.class);
        SITE_CLASSES.put(6, Wired7.class);
        SITE_CLASSES.put(7, Yuki.class);
    }
}
