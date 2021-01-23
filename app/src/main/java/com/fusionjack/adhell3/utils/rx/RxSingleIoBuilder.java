package com.fusionjack.adhell3.utils.rx;

import io.reactivex.schedulers.Schedulers;

public final class RxSingleIoBuilder extends RxSingleBuilder {

    public RxSingleIoBuilder() {
        super(Schedulers.io());
    }

}
