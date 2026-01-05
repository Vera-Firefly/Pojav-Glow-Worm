package com.firefly.ui.dialog;

import androidx.annotation.Nullable;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.movtery.ui.dialog.DraggableDialog;

import net.kdt.pojavlaunch.firefly.R;

public class ListViewDialog implements DraggableDialog.DialogInitializationListener {
    private final AlertDialog dialog;
    private final String[] items;
    private final OnItemClickListener itemClickListener;

    private ListViewDialog(Context context, String title, String message,
                         String confirmButtonText, String cancelButtonText,
                         OnButtonClickListener cancelListener, OnButtonClickListener confirmListener,
                         String button1Text, String button2Text, String button3Text, String button4Text,
                         OnButtonClickListener button1Listener, OnButtonClickListener button2Listener,
                         OnButtonClickListener button3Listener, OnButtonClickListener button4Listener,
                         String[] items, OnItemClickListener itemClickListener,
                         boolean cancelable, boolean draggable) {

        this.items = items;
        this.itemClickListener = itemClickListener;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        // Main View
        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 20, 50, 20);

        // Button View
        LinearLayout buttonLayout = new LinearLayout(context);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(android.view.Gravity.CENTER);

        // Button Params
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);

        Button button1 = new Button(context);
        Button button2 = new Button(context);
        Button button3 = new Button(context);
        Button button4 = new Button(context);
        Button confirmButton = new Button(context);
        Button cancelButton = new Button(context);
        ListView listView = new ListView(context);

        if (title != null && !title.isEmpty()) {
            builder.setTitle(title);
        }

        if (message != null && !message.isEmpty()) {
            builder.setMessage(message);
        }

        if (items != null && items.length > 0) {
            mainLayout.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        }

        if (cancelListener != null) {
            if (cancelButtonText != null) cancelButton.setText(cancelButtonText);
            buttonLayout.addView(cancelButton, buttonParams);
        }

        if (button1Listener != null) {
            if (button1Text != null) button1.setText(button1Text);
            buttonLayout.addView(button1, buttonParams);
        }

        if (button2Listener != null) {
            if (button2Text != null) button2.setText(button2Text);
            buttonLayout.addView(button2, buttonParams);
        }

        if (button3Listener != null) {
            if (button3Text != null) button3.setText(button3Text);
            buttonLayout.addView(button3, buttonParams);
        }

        if (button4Listener != null) {
            if (button4Text != null) button4.setText(button4Text);
            buttonLayout.addView(button4, buttonParams);
        }

        if (confirmListener != null) {
            if (confirmButtonText != null) confirmButton.setText(confirmButtonText);
            buttonLayout.addView(confirmButton, buttonParams);
        }

        boolean onSetButton = (button1Listener != null
                               || button2Listener != null
                               || button3Listener != null
                               || button4Listener != null
                               || cancelListener != null
                               || confirmListener != null);

        if (itemClickListener != null) {
            if (onSetButton) {
                mainLayout.addView(buttonLayout);
            }
            builder.setView(mainLayout);
        }

        if (onSetButton && itemClickListener == null) {
            builder.setView(buttonLayout);
        }

        dialog = builder.create();

        if (draggable) dialog.setOnShowListener(dialogInterface -> DraggableDialog.initDialog(this));

        if (!cancelable) dialog.setCancelable(false);

        if (button1Listener != null) {
            button1.setOnClickListener(v -> {
                boolean shouldDismiss = button1Listener.onClick(v);
                if (shouldDismiss) dialog.dismiss();
            });
        }

        if (button2Listener != null) {
            button2.setOnClickListener(v -> {
                boolean shouldDismiss = button2Listener.onClick(v);
                if (shouldDismiss) dialog.dismiss();
            });
        }

        if (button3Listener != null) {
            button3.setOnClickListener(v -> {
                boolean shouldDismiss = button3Listener.onClick(v);
                if (shouldDismiss) dialog.dismiss();
            });
        }

        if (button4Listener != null) {
            button4.setOnClickListener(v -> {
                boolean shouldDismiss = button4Listener.onClick(v);
                if (shouldDismiss) dialog.dismiss();
            });
        }

        if (cancelListener != null) {
            cancelButton.setOnClickListener(v -> {
                boolean shouldDismiss = cancelListener.onClick(v);
                if (shouldDismiss) dialog.dismiss();
            });
        }

        if (confirmListener != null) {
            confirmButton.setOnClickListener(v -> {
                boolean shouldDismiss = confirmListener.onClick(v);
                if (shouldDismiss) dialog.dismiss();
            });
        }

        if (itemClickListener != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, items);
            listView.setAdapter(adapter);
            listView.setOnItemClickListener((parent, view1, position, id) -> {
                String item = items[position];
                itemClickListener.onItemClick(item, position);
                dialog.dismiss();
            });
        }
    }

    public void show() {
        dialog.show();
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    @Override
    public Window onInit() {
        return dialog.getWindow();
    }

    public interface OnButtonClickListener {
        boolean onClick(View view);
    }

    public interface OnItemClickListener {
        void onItemClick(String item, @Nullable Integer index);
    }

    public static class Builder {
        private final Context context;
        private String title;
        private String message;
        private String button1Text;
        private String button2Text;
        private String button3Text;
        private String button4Text;
        private String confirmButtonText;
        private String cancelButtonText;
        private OnButtonClickListener button1Listener;
        private OnButtonClickListener button2Listener;
        private OnButtonClickListener button3Listener;
        private OnButtonClickListener button4Listener;
        private OnButtonClickListener cancelListener;
        private OnButtonClickListener confirmListener;
        private String[] items;
        private OnItemClickListener itemClickListener;
        private boolean cancelable = true;
        private boolean draggable = false;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(int title) {
            this.title = context.getString(title);
            return this;
        }

        public Builder setMessage(int message) {
            this.message = context.getString(message);
            return this;
        }

        public Builder setItems(String[] items, OnItemClickListener listener) {
            this.items = items;
            this.itemClickListener = listener;
            return this;
        }

        public Builder setButton1Listener(int buttonText, OnButtonClickListener listener) {
            this.button1Text = context.getString(buttonText);
            this.button1Listener = listener;
            return this;
        }

        public Builder setButton2Listener(int buttonText, OnButtonClickListener listener) {
            this.button2Text = context.getString(buttonText);
            this.button2Listener = listener;
            return this;
        }

        public Builder setButton3Listener(int buttonText, OnButtonClickListener listener) {
            this.button3Text = context.getString(buttonText);
            this.button3Listener = listener;
            return this;
        }

        public Builder setButton4Listener(int buttonText, OnButtonClickListener listener) {
            this.button4Text = context.getString(buttonText);
            this.button4Listener = listener;
            return this;
        }

        public Builder setConfirmListener(int buttonText, OnButtonClickListener listener) {
            this.confirmButtonText = context.getString(buttonText);
            this.confirmListener = listener;
            return this;
        }

        public Builder setCancelListener(int buttonText, OnButtonClickListener listener) {
            this.cancelButtonText = context.getString(buttonText);
            this.cancelListener = listener;
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public Builder setDraggable(boolean draggable) {
            this.draggable = draggable;
            return this;
        }

        public ListViewDialog build() {
            return new ListViewDialog(context, title, message,
                    confirmButtonText, cancelButtonText, cancelListener, confirmListener,
                    button1Text, button2Text, button3Text, button4Text,
                    button1Listener, button2Listener, button3Listener, button4Listener,
                    items, itemClickListener, cancelable, draggable);
        }
    }
}