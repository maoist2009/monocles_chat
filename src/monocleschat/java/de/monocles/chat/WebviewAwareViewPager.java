package de.monocles.chat;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;

public class WebviewAwareViewPager extends androidx.viewpager.widget.ViewPager {
	public WebviewAwareViewPager(Context context) {
		super(context);
	}

	public WebviewAwareViewPager(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
		return true;
	}
}
