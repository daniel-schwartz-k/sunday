package com.schwartzinc.sunday;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class SundayWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new SundayViewsFactory(this.getApplicationContext());
    }

    static class SundayViewsFactory implements RemoteViewsFactory {
        private static final int VIEW_TYPE_HEADER = 0;
        private static final int VIEW_TYPE_TASK = 1;
        private static final int VIEW_TYPE_EMPTY = 2;

        private final Context context;
        private final List<RowItem> rows = new ArrayList<>();
        private WidgetTheme theme = WidgetTheme.get("dark");

        static class TagInfo {
            final String name;
            final int color;
            TagInfo(String name, int color) {
                this.name = name;
                this.color = color;
            }
        }

        static class RowItem {
            final int type;
            final String columnId;
            final String headerTitle;
            final String taskId;
            final String taskName;
            final int subtaskCount;
            final List<TagInfo> tags;

            private RowItem(int type, String columnId, String headerTitle,
                            String taskId, String taskName, int subtaskCount, List<TagInfo> tags) {
                this.type = type;
                this.columnId = columnId;
                this.headerTitle = headerTitle;
                this.taskId = taskId;
                this.taskName = taskName;
                this.subtaskCount = subtaskCount;
                this.tags = tags != null ? tags : new ArrayList<>();
            }

            static RowItem header(String columnId, String title) {
                return new RowItem(VIEW_TYPE_HEADER, columnId, title, null, null, 0, null);
            }

            static RowItem task(String columnId, String id, String name, int subtaskCount, List<TagInfo> tags) {
                return new RowItem(VIEW_TYPE_TASK, columnId, null, id, name, subtaskCount, tags);
            }

            static RowItem empty(String columnId) {
                return new RowItem(VIEW_TYPE_EMPTY, columnId, null, null, null, 0, null);
            }
        }

        SundayViewsFactory(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            rows.clear();
            SharedPreferences prefs = context.getSharedPreferences(WidgetBridgePlugin.PREFS_NAME, Context.MODE_PRIVATE);
            String snapshotStr = prefs.getString(WidgetBridgePlugin.KEY_SNAPSHOT, null);
            if (snapshotStr == null) {
                theme = WidgetTheme.get("dark");
                return;
            }

            try {
                JSONObject snapshot = new JSONObject(snapshotStr);
                theme = WidgetTheme.get(snapshot.optString("theme", "dark"));

                Map<String, TagInfo> tagMap = new HashMap<>();
                JSONObject tagsObj = snapshot.optJSONObject("tags");
                if (tagsObj != null) {
                    JSONArray tagKeys = tagsObj.names();
                    if (tagKeys != null) {
                        for (int i = 0; i < tagKeys.length(); i++) {
                            String key = tagKeys.getString(i);
                            JSONObject t = tagsObj.optJSONObject(key);
                            if (t != null) {
                                String name = t.optString("name", key);
                                String colorStr = t.optString("color", "#ff6b2b");
                                int color;
                                try {
                                    color = Color.parseColor(colorStr);
                                } catch (Exception e) {
                                    color = Color.parseColor("#ff6b2b");
                                }
                                tagMap.put(key, new TagInfo(name, color));
                            }
                        }
                    }
                }

                JSONObject listsObj = snapshot.optJSONObject("lists");
                String[][] columns = new String[][]{
                    {"on-it", "🚀 On It"},
                    {"next-up", "🕗 Next Up"},
                    {"back-log", "📋 Back Log"}
                };

                for (String[] col : columns) {
                    String colId = col[0];
                    String colTitle = col[1];
                    rows.add(RowItem.header(colId, colTitle));

                    JSONArray tasks = listsObj != null ? listsObj.optJSONArray(colId) : null;
                    if (tasks == null || tasks.length() == 0) {
                        rows.add(RowItem.empty(colId));
                    } else {
                        for (int i = 0; i < tasks.length(); i++) {
                            JSONObject taskObj = tasks.getJSONObject(i);
                            String id = taskObj.optString("id", "");
                            String name = taskObj.optString("name", "Untitled task");
                            int subtaskCount = taskObj.optInt("subtaskCount", 0);

                            List<TagInfo> taskTags = new ArrayList<>();
                            JSONArray tagKeysArr = taskObj.optJSONArray("tags");
                            if (tagKeysArr != null) {
                                for (int k = 0; k < tagKeysArr.length(); k++) {
                                    String tagKey = tagKeysArr.getString(k);
                                    TagInfo info = tagMap.get(tagKey);
                                    if (info != null) {
                                        taskTags.add(info);
                                    }
                                }
                            }
                            rows.add(RowItem.task(colId, id, name, subtaskCount, taskTags));
                        }
                    }
                }
            } catch (Exception e) {
                rows.clear();
            }
        }

        @Override
        public void onDestroy() {
            rows.clear();
        }

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= rows.size()) return null;
            RowItem item = rows.get(position);

            switch (item.type) {
                case VIEW_TYPE_HEADER:
                    return buildHeaderRow(item);
                case VIEW_TYPE_TASK:
                    return buildTaskRow(item);
                case VIEW_TYPE_EMPTY:
                default:
                    return buildEmptyRow(item);
            }
        }

        private RemoteViews buildHeaderRow(RowItem item) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_row_header);
            views.setTextViewText(R.id.header_title, item.headerTitle);
            views.setTextColor(R.id.header_title, theme.accent);
            views.setTextColor(R.id.header_add_btn, theme.accent);

            // Fill-in intent for + button: addTask for this column
            Intent fillIn = new Intent();
            fillIn.putExtra("widgetAction", "addTask");
            fillIn.putExtra("columnId", item.columnId);
            views.setOnClickFillInIntent(R.id.header_add_btn, fillIn);

            return views;
        }

        private RemoteViews buildEmptyRow(RowItem item) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_row_empty);
            views.setTextColor(R.id.empty_row_text, theme.textSecondary);
            return views;
        }

        private RemoteViews buildTaskRow(RowItem item) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_row_task);

            // Card title and text color
            views.setTextViewText(R.id.task_title, item.taskName);
            views.setTextColor(R.id.task_title, theme.textPrimary);

            // Subtask badge
            if (item.subtaskCount > 0) {
                views.setViewVisibility(R.id.task_subtask_badge, View.VISIBLE);
                views.setTextViewText(R.id.task_subtask_badge, "↳ " + item.subtaskCount);
                views.setTextColor(R.id.task_subtask_badge, theme.textSecondary);
            } else {
                views.setViewVisibility(R.id.task_subtask_badge, View.GONE);
            }

            // Tags: populate up to 3 pills, +N for the rest
            int[] pillIds = new int[]{R.id.task_tag_1, R.id.task_tag_2, R.id.task_tag_3};
            int tagCount = item.tags.size();
            int displayedTags = Math.min(tagCount, 3);

            for (int i = 0; i < 3; i++) {
                int pillId = pillIds[i];
                if (i < displayedTags) {
                    TagInfo t = item.tags.get(i);
                    views.setViewVisibility(pillId, View.VISIBLE);
                    views.setTextViewText(pillId, "#" + t.name);
                    views.setTextColor(pillId, t.color);
                } else {
                    views.setViewVisibility(pillId, View.GONE);
                }
            }

            if (tagCount > 3) {
                views.setViewVisibility(R.id.task_tag_more, View.VISIBLE);
                views.setTextViewText(R.id.task_tag_more, "+" + (tagCount - 3));
                views.setTextColor(R.id.task_tag_more, theme.textSecondary);
            } else {
                views.setViewVisibility(R.id.task_tag_more, View.GONE);
            }

            // Fill-in intent: openTask for this task & column
            Intent fillIn = new Intent();
            fillIn.putExtra("widgetAction", "openTask");
            fillIn.putExtra("taskId", item.taskId);
            fillIn.putExtra("columnId", item.columnId);
            views.setOnClickFillInIntent(R.id.task_row_root, fillIn);

            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }
    }
}
