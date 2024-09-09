package com.firefly.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import net.kdt.pojavlaunch.R;

public class CustomDialog {
    private final AlertDialog dialog;

    private CustomDialog(Context context, String title, String message, View customView,
                         String confirmButtonText, String cancelButtonText,
                         OnCancelListener cancelListener, OnConfirmListener confirmListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.dialog_custom_layout, null);

        TextView titleTextView = view.findViewById(R.id.custom_dialog_title);
        TextView messageTextView = view.findViewById(R.id.custom_dialog_message);
        Button confirmButton = view.findViewById(R.id.custom_dialog_confirm_button);
        Button cancelButton = view.findViewById(R.id.custom_dialog_cancel_button);
        FrameLayout customContainer = view.findViewById(R.id.custom_view_container);

        if (title != null && !title.isEmpty()) {
            titleTextView.setText(title);
        } else {
            titleTextView.setVisibility(View.GONE);
        }

        if (message != null && !message.isEmpty()) {
            messageTextView.setText(message);
        } else {
            messageTextView.setVisibility(View.GONE);
        }

        if (customView != null && customContainer != null) {
            customContainer.addView(customView);
        }

        if (confirmButtonText != null) {
            confirmButton.setText(confirmButtonText);
        }
        if (cancelButtonText != null) {
            cancelButton.setText(cancelButtonText);
        }

        builder.setView(view);
        dialog = builder.create();
        dialog.setCancelable(false);

        confirmButton.setOnClickListener(v -> {
            boolean shouldDismiss = true;
            if (confirmListener != null) {
                shouldDismiss = confirmListener.onConfirm(customView);
            }
            if (shouldDismiss) {
                dialog.dismiss();
            }
        });

        cancelButton.setOnClickListener(v -> {
            boolean shouldDismiss = true;
            if (cancelListener != null) {
                shouldDismiss = cancelListener.onCancel();
            }
            if (shouldDismiss) {
                dialog.dismiss();
            }
        });

    }

    public void show() {
        dialog.show();
    }

    public interface OnConfirmListener {
        boolean onConfirm(View view);
    }

    public interface OnCancelListener {
        boolean onCancel();
    }

    public static class Builder {
        private final Context context;
        private String title;
        private String message;
        private View customView;
        private String confirmButtonText;
        private String cancelButtonText;
        private OnCancelListener cancelListener;
        private OnConfirmListener confirmListener;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder setCustomView(View customView) {
            this.customView = customView;
            return this;
        }

        public Builder setConfirmListener(int confirmButtonTextResId, OnConfirmListener confirmListener) {
            this.confirmButtonText = context.getString(confirmButtonTextResId);
            this.confirmListener = confirmListener;
            return this;
        }

        public Builder setCancelListener(int cancelButtonTextResId, OnCancelListener cancelListener) {
            this.cancelButtonText = context.getString(cancelButtonTextResId);
            this.cancelListener = cancelListener;
            return this;
        }

        public CustomDialog build() {
            return new CustomDialog(context, title, message, customView,
                    confirmButtonText, cancelButtonText, cancelListener, confirmListener);
        }
    }
}