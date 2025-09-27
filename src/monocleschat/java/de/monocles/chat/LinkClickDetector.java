package de.monocles.chat;

import android.text.Layout;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.widget.TextView;

public class LinkClickDetector {

    public static boolean isLinkClicked(TextView textView, MotionEvent event) {
        if (textView == null || textView.getText() == null || !(textView.getText() instanceof Spannable)) {
            return false;
        }

        Spannable buffer = (Spannable) textView.getText();
        int action = event.getAction();

        if (action == MotionEvent.ACTION_UP) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= textView.getTotalPaddingLeft();
            y -= textView.getTotalPaddingTop();

            x += textView.getScrollX();
            y += textView.getScrollY();

            Layout layout = textView.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            ClickableSpan[] links = buffer.getSpans(off, off, ClickableSpan.class);
            return links.length > 0;
        }
        return false;
    }

    public static void setupLinkClickDetector(TextView textView) {
        textView.setMovementMethod(new CustomLinkMovementMethod());
    }

    private static class CustomLinkMovementMethod extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            // We don't need to handle the click here, just detect it
            return super.onTouchEvent(widget, buffer, event);
        }
    }
}