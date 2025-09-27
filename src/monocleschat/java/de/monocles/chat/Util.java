package de.monocles.chat;

import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ListAdapter;

public class Util {
	public static void justifyListViewHeightBasedOnChildren(ListView listView) {
		justifyListViewHeightBasedOnChildren(listView, 0, false);
	}

	public static void justifyListViewHeightBasedOnChildren(ListView listView, int offset, boolean andWidth) {
		ListAdapter adapter = listView.getAdapter();

		if (adapter == null) {
			return;
		}
		ViewGroup vg = listView;
		int totalHeight = 0;
		int maxWidth = 0;
		final var displayWidth = listView.getContext().getResources().getDisplayMetrics().widthPixels;
		final int width = !andWidth && listView.getWidth() > 0 ? listView.getWidth() : (displayWidth - offset);
		final int widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
		for (int i = 0; i < adapter.getCount(); i++) {
			View listItem = adapter.getView(i, null, vg);
			listItem.measure(widthSpec, 0);
			totalHeight += listItem.getMeasuredHeight();
			maxWidth = Math.max(maxWidth, listItem.getMeasuredWidth());
		}

		ViewGroup.LayoutParams par = listView.getLayoutParams();
		par.height = totalHeight + (listView.getDividerHeight() * Math.max(0, adapter.getCount() - 1));
		if (andWidth) {
			if (maxWidth <= (displayWidth - offset) && maxWidth > offset*2) {
				par.width = maxWidth;
			} else {
				par.width = ViewGroup.LayoutParams.MATCH_PARENT;
			}
		}
		listView.setLayoutParams(par);
		listView.requestLayout();
	}
}
