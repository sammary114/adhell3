package com.fusionjack.adhell3.utils.rx;

import android.app.ProgressDialog;
import android.content.Context;

import com.fusionjack.adhell3.R;

import java.lang.ref.WeakReference;
import java.util.Optional;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Scheduler;

public class RxCompletableBuilder {

    private static final Runnable EMPTY_RUNNABLE = () -> {};

    private final Scheduler scheduler;

    private String dialogMessage;
    private boolean showDialog;

    private WeakReference<Context> weakReference;

    RxCompletableBuilder(Scheduler scheduler) {
        this.scheduler = scheduler;
        this.showDialog = false;
    }

    public RxCompletableBuilder showErrorAlert(Context context) {
        if (weakReference == null) {
            weakReference = new WeakReference<>(context);
        }
        return this;
    }

    public RxCompletableBuilder setShowDialog(String dialogMessage, Context context) {
        if (weakReference == null) {
            weakReference = new WeakReference<>(context);
        }
        this.showDialog = true;
        this.dialogMessage = dialogMessage;
        return this;
    }

    public void async(Completable observable) {
        async(observable, EMPTY_RUNNABLE, EMPTY_RUNNABLE, EMPTY_RUNNABLE);
    }

    public void async(Completable observable, Runnable onCompletableCallback) {
        async(observable, EMPTY_RUNNABLE, onCompletableCallback, EMPTY_RUNNABLE);
    }

    public void async(Completable observable, Runnable onSubscribeCallback, Runnable onCompletableCallback, Runnable onErrorCallback) {
        Context context = Optional.ofNullable(weakReference).map(WeakReference::get).orElse(null);
        if (showDialog) {
            Optional.ofNullable(context).map(ctx -> new ProgressDialog(ctx, R.style.DialogStyle)).ifPresent(dialog -> {
                Runnable onSubscribe = () -> {
                    dialog.setMessage(dialogMessage);
                    dialog.setCancelable(false);
                    dialog.show();
                    onSubscribeCallback.run();
                };
                Runnable onComplete = () -> {
                    if (dialog.isShowing()) dialog.dismiss();
                    onCompletableCallback.run();
                };
                Runnable onError = () -> {
                    if (dialog.isShowing()) dialog.dismiss();
                    onErrorCallback.run();
                };
                RxFactory.async(observable, scheduler, onSubscribe, onComplete, onError, context);
            });
        } else {
            RxFactory.async(observable, scheduler, onSubscribeCallback, onCompletableCallback, onErrorCallback, context);
        }
    }

}
