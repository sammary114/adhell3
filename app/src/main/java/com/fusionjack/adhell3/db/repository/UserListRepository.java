package com.fusionjack.adhell3.db.repository;

import androidx.lifecycle.LiveData;

import java.util.List;

import io.reactivex.rxjava3.core.Single;

public interface UserListRepository {

    LiveData<List<String>> getItems();
    Single<String> addItem(String item);
    Single<String> removeItem(String item);

    void addItemToDatabase(String item);
    void removeItemFromDatabase(String item);
}
