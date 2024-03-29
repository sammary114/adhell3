package com.fusionjack.adhell3.tasks;

import android.app.ProgressDialog;
import android.content.Context;

import com.fusionjack.adhell3.R;
import com.fusionjack.adhell3.utils.AdhellFactory;
import com.fusionjack.adhell3.utils.AppDatabaseFactory;
import com.fusionjack.adhell3.utils.LogUtils;
import com.fusionjack.adhell3.utils.dialog.DialogBuilder;

import java.lang.ref.WeakReference;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class RestoreDatabaseRxTask implements Runnable {
    private final WeakReference<Context> contextWeakReference;
    private ProgressDialog dialog;

    public RestoreDatabaseRxTask(Context context) {
        this.contextWeakReference = new WeakReference<>(context);
        createDialog();
    }

    private void createDialog() {
        Context context = contextWeakReference.get();
        if (context != null) {
            this.dialog = new ProgressDialog(context, R.style.DialogStyle);
            dialog.setCancelable(false);
        }
    }

    @Override
    public void run() {
        Context context = contextWeakReference.get();
        if (context == null) {
            return;
        }

        boolean hasInternetAccess = AdhellFactory.getInstance().hasInternetAccess(context);
        Observable.create((ObservableOnSubscribe<String>) emitter -> {
            try {
                AppDatabaseFactory.restoreDatabase(emitter, hasInternetAccess);
            } catch (Exception e) {
                emitter.onError(e);
            }
            emitter.onComplete();
        })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<String>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        dialog.setMessage("Restoring database, please wait ...");
                        dialog.show();
                    }

                    @Override
                    public void onNext(@NonNull String message) {
                        dialog.setMessage(message);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        dialog.dismiss();
                        LogUtils.error(e.getMessage(), e);
                        DialogBuilder.showDialog(R.string.error, e.getMessage(), context);
                    }

                    @Override
                    public void onComplete() {
                        dialog.dismiss();
                        DialogBuilder.showDialog(R.string.info, R.string.restore_database_finished, context);
                    }
                });
    }
}
